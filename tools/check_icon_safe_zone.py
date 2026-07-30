#!/usr/bin/env python3

import itertools
import math
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


CENTER = (54.0, 54.0)
LIMIT = 33.0
EXPECTED_VIEWPORT = 108.0
MIN_COMPOSITING_GAP = 0.04
EXPECTED_MONOCHROME_GAP = 2.0
MONOCHROME_GAP_TOLERANCE = 0.01
FLATNESS = 1e-5
MAX_DEPTH = 24
ANDROID_NS = "http://schemas.android.com/apk/res/android"
TOKEN_RE = re.compile(
    r"[AaCcHhLlMmQqSsTtVvZz]|"
    r"[-+]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?"
)
ARITY = {
    "M": 2,
    "L": 2,
    "H": 1,
    "V": 1,
    "C": 6,
    "S": 4,
    "Q": 4,
    "T": 2,
    "A": 7,
    "Z": 0,
}


def midpoint(a, b):
    return ((a[0] + b[0]) / 2.0, (a[1] + b[1]) / 2.0)


def point_line_distance(point, start, end):
    dx = end[0] - start[0]
    dy = end[1] - start[1]
    if dx == 0.0 and dy == 0.0:
        return math.hypot(point[0] - start[0], point[1] - start[1])
    return abs(
        dy * point[0] - dx * point[1] + end[0] * start[1] - end[1] * start[0]
    ) / math.hypot(dx, dy)


def flatten_quadratic(start, control, end, points, depth=0):
    if (
        depth >= MAX_DEPTH
        or point_line_distance(control, start, end) <= FLATNESS
    ):
        points.append(end)
        return
    start_control = midpoint(start, control)
    control_end = midpoint(control, end)
    split = midpoint(start_control, control_end)
    flatten_quadratic(start, start_control, split, points, depth + 1)
    flatten_quadratic(split, control_end, end, points, depth + 1)


def flatten_cubic(start, control1, control2, end, points, depth=0):
    if depth >= MAX_DEPTH or max(
        point_line_distance(control1, start, end),
        point_line_distance(control2, start, end),
    ) <= FLATNESS:
        points.append(end)
        return
    start_control1 = midpoint(start, control1)
    control1_control2 = midpoint(control1, control2)
    control2_end = midpoint(control2, end)
    left_middle = midpoint(start_control1, control1_control2)
    right_middle = midpoint(control1_control2, control2_end)
    split = midpoint(left_middle, right_middle)
    flatten_cubic(
        start,
        start_control1,
        left_middle,
        split,
        points,
        depth + 1,
    )
    flatten_cubic(
        split,
        right_middle,
        control2_end,
        end,
        points,
        depth + 1,
    )


def vector_angle(ux, uy, vx, vy):
    return math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)


def flatten_arc(start, values, relative, points):
    rx, ry, rotation, large_arc, sweep, x, y = values
    if relative:
        x += start[0]
        y += start[1]
    end = (x, y)
    rx = abs(rx)
    ry = abs(ry)
    if start == end:
        return end
    if rx == 0.0 or ry == 0.0:
        points.append(end)
        return end

    phi = math.radians(rotation % 360.0)
    cos_phi = math.cos(phi)
    sin_phi = math.sin(phi)
    dx = (start[0] - end[0]) / 2.0
    dy = (start[1] - end[1]) / 2.0
    x_prime = cos_phi * dx + sin_phi * dy
    y_prime = -sin_phi * dx + cos_phi * dy

    scale = x_prime * x_prime / (rx * rx) + y_prime * y_prime / (ry * ry)
    if scale > 1.0:
        scale = math.sqrt(scale)
        rx *= scale
        ry *= scale

    numerator = max(
        0.0,
        rx * rx * ry * ry
        - rx * rx * y_prime * y_prime
        - ry * ry * x_prime * x_prime,
    )
    denominator = (
        rx * rx * y_prime * y_prime + ry * ry * x_prime * x_prime
    )
    coefficient = 0.0 if denominator == 0.0 else math.sqrt(numerator / denominator)
    if bool(large_arc) == bool(sweep):
        coefficient = -coefficient

    center_prime_x = coefficient * rx * y_prime / ry
    center_prime_y = -coefficient * ry * x_prime / rx
    center_x = (
        cos_phi * center_prime_x
        - sin_phi * center_prime_y
        + (start[0] + end[0]) / 2.0
    )
    center_y = (
        sin_phi * center_prime_x
        + cos_phi * center_prime_y
        + (start[1] + end[1]) / 2.0
    )

    start_vector = (
        (x_prime - center_prime_x) / rx,
        (y_prime - center_prime_y) / ry,
    )
    end_vector = (
        (-x_prime - center_prime_x) / rx,
        (-y_prime - center_prime_y) / ry,
    )
    theta = vector_angle(1.0, 0.0, *start_vector)
    delta = vector_angle(*start_vector, *end_vector)
    if not sweep and delta > 0.0:
        delta -= 2.0 * math.pi
    elif sweep and delta < 0.0:
        delta += 2.0 * math.pi

    radius = max(rx, ry)
    if radius <= FLATNESS:
        steps = 1
    else:
        max_step = 2.0 * math.acos(max(-1.0, 1.0 - FLATNESS / radius))
        steps = max(1, math.ceil(abs(delta) / max_step))
    for index in range(1, steps + 1):
        angle = theta + delta * index / steps
        arc_x = rx * math.cos(angle)
        arc_y = ry * math.sin(angle)
        points.append(
            (
                cos_phi * arc_x - sin_phi * arc_y + center_x,
                sin_phi * arc_x + cos_phi * arc_y + center_y,
            )
        )
    points[-1] = end
    return end


def tokenize(path_data):
    tokens = []
    end = 0
    for match in TOKEN_RE.finditer(path_data):
        if path_data[end : match.start()].strip(" ,\t\r\n"):
            raise ValueError(f"invalid pathData near {path_data[end:match.start()]!r}")
        token = match.group()
        tokens.append(token if token.isalpha() else float(token))
        end = match.end()
    if path_data[end:].strip(" ,\t\r\n"):
        raise ValueError(f"invalid pathData suffix {path_data[end:]!r}")
    return tokens


def flatten_path(path_data):
    tokens = tokenize(path_data)
    points = []
    current = (0.0, 0.0)
    subpath_start = current
    last_cubic_control = None
    last_quadratic_control = None
    command = None
    previous_command = None
    index = 0

    while index < len(tokens):
        if isinstance(tokens[index], str):
            command = tokens[index]
            index += 1
        elif command is None:
            raise ValueError("pathData must start with a command")

        upper = command.upper()
        relative = command.islower()
        if upper == "Z":
            if current != subpath_start:
                points.append(subpath_start)
            current = subpath_start
            last_cubic_control = None
            last_quadratic_control = None
            previous_command = command
            command = None
            continue

        arity = ARITY[upper]
        if index + arity > len(tokens) or any(
            isinstance(token, str) for token in tokens[index : index + arity]
        ):
            raise ValueError(f"not enough coordinates for {command}")
        values = tokens[index : index + arity]
        index += arity

        if upper == "M":
            x, y = values
            if relative:
                x += current[0]
                y += current[1]
            current = (x, y)
            subpath_start = current
            points.append(current)
            command = "l" if relative else "L"
        elif upper == "L":
            x, y = values
            if relative:
                x += current[0]
                y += current[1]
            current = (x, y)
            points.append(current)
        elif upper == "H":
            x = values[0] + (current[0] if relative else 0.0)
            current = (x, current[1])
            points.append(current)
        elif upper == "V":
            y = values[0] + (current[1] if relative else 0.0)
            current = (current[0], y)
            points.append(current)
        elif upper == "C":
            x1, y1, x2, y2, x, y = values
            if relative:
                x1 += current[0]
                y1 += current[1]
                x2 += current[0]
                y2 += current[1]
                x += current[0]
                y += current[1]
            control1 = (x1, y1)
            control2 = (x2, y2)
            end = (x, y)
            flatten_cubic(current, control1, control2, end, points)
            current = end
            last_cubic_control = control2
        elif upper == "S":
            x2, y2, x, y = values
            if relative:
                x2 += current[0]
                y2 += current[1]
                x += current[0]
                y += current[1]
            if previous_command and previous_command.upper() in {"C", "S"}:
                control1 = (
                    2.0 * current[0] - last_cubic_control[0],
                    2.0 * current[1] - last_cubic_control[1],
                )
            else:
                control1 = current
            control2 = (x2, y2)
            end = (x, y)
            flatten_cubic(current, control1, control2, end, points)
            current = end
            last_cubic_control = control2
        elif upper == "Q":
            x1, y1, x, y = values
            if relative:
                x1 += current[0]
                y1 += current[1]
                x += current[0]
                y += current[1]
            control = (x1, y1)
            end = (x, y)
            flatten_quadratic(current, control, end, points)
            current = end
            last_quadratic_control = control
        elif upper == "T":
            x, y = values
            if relative:
                x += current[0]
                y += current[1]
            if previous_command and previous_command.upper() in {"Q", "T"}:
                control = (
                    2.0 * current[0] - last_quadratic_control[0],
                    2.0 * current[1] - last_quadratic_control[1],
                )
            else:
                control = current
            end = (x, y)
            flatten_quadratic(current, control, end, points)
            current = end
            last_quadratic_control = control
        elif upper == "A":
            current = flatten_arc(current, values, relative, points)

        if upper not in {"C", "S"}:
            last_cubic_control = None
        if upper not in {"Q", "T"}:
            last_quadratic_control = None
        previous_command = command

    return points


def local_name(tag):
    return tag.rsplit("}", 1)[-1]


def parse_vector(path):
    root = ET.parse(path).getroot()
    unsupported = {"group", "clip-path"}
    for element in root.iter():
        name = local_name(element.tag)
        if name in unsupported:
            raise ValueError(f"unsupported <{name}> element")

    viewport_width = root.get(f"{{{ANDROID_NS}}}viewportWidth")
    viewport_height = root.get(f"{{{ANDROID_NS}}}viewportHeight")
    try:
        viewport = (float(viewport_width), float(viewport_height))
    except (TypeError, ValueError) as error:
        raise ValueError("viewportWidth and viewportHeight must be numeric") from error
    if viewport != (EXPECTED_VIEWPORT, EXPECTED_VIEWPORT):
        raise ValueError(
            "viewportWidth and viewportHeight must both equal "
            f"{EXPECTED_VIEWPORT:g}, got {viewport[0]:g}x{viewport[1]:g}"
        )

    path_attribute = f"{{{ANDROID_NS}}}pathData"
    paths = []
    for element in root.iter():
        path_data = element.get(path_attribute)
        if path_data:
            paths.append((element, flatten_path(path_data)))
    if not paths:
        raise ValueError("no pathData found")
    return paths


def measure_paths(paths):
    points = [
        point
        for _element, path_points in paths
        for point in path_points
    ]
    return max(
        math.hypot(point[0] - CENTER[0], point[1] - CENTER[1])
        for point in points
    )


def resolve_drawable(repository, reference):
    prefix = "@drawable/"
    if not reference.startswith(prefix):
        raise ValueError(f"unsupported layer reference {reference!r}")
    name = reference[len(prefix) :]
    return repository / "app/src/main/res/drawable" / f"{name}.xml"


def discover_layers(repository):
    icon_path = (
        repository / "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml"
    )
    root = ET.parse(icon_path).getroot()
    drawable_attribute = f"{{{ANDROID_NS}}}drawable"
    layers = {}
    for element in root:
        name = local_name(element.tag)
        if name not in {"foreground", "monochrome"}:
            continue
        reference = element.get(drawable_attribute)
        if reference is None:
            raise ValueError(f"<{name}> has no android:drawable")
        layers[name] = resolve_drawable(repository, reference)
    missing = {"foreground", "monochrome"} - layers.keys()
    if missing:
        raise ValueError(f"missing adaptive-icon layers: {', '.join(sorted(missing))}")
    return layers


def fill_alphas(paths):
    fill_alpha_attribute = f"{{{ANDROID_NS}}}fillAlpha"
    alphas = []
    for element, _points in paths:
        raw_alpha = element.get(fill_alpha_attribute, "1")
        try:
            alpha = float(raw_alpha)
        except ValueError as error:
            raise ValueError(f"fillAlpha must be numeric, got {raw_alpha!r}") from error
        if not 0.0 <= alpha <= 1.0:
            raise ValueError(f"fillAlpha must be in [0, 1], got {alpha:g}")
        alphas.append(alpha)
    return alphas


def compositing_gap(paths):
    alphas = fill_alphas(paths)
    coverages = []
    for count in range(1, len(alphas) + 1):
        for combination in itertools.combinations(alphas, count):
            coverages.append(
                1.0 - math.prod(1.0 - alpha for alpha in combination)
            )
    return min(
        abs(left - right)
        for left, right in itertools.combinations(coverages, 2)
    )


def point_segment_distance(point, start, end):
    dx = end[0] - start[0]
    dy = end[1] - start[1]
    squared_length = dx * dx + dy * dy
    if squared_length == 0.0:
        return math.hypot(point[0] - start[0], point[1] - start[1])
    projection = (
        (point[0] - start[0]) * dx + (point[1] - start[1]) * dy
    ) / squared_length
    projection = max(0.0, min(1.0, projection))
    closest = (
        start[0] + projection * dx,
        start[1] + projection * dy,
    )
    return math.hypot(point[0] - closest[0], point[1] - closest[1])


def polyline_gap(left, right):
    distances = []
    for points, segments in ((left, right), (right, left)):
        distances.extend(
            point_segment_distance(point, start, end)
            for point in points
            for start, end in zip(segments, segments[1:])
        )
    return min(distances)


def display_path(path, repository):
    try:
        return path.resolve().relative_to(repository)
    except ValueError:
        return path


def main():
    repository = Path(__file__).resolve().parent.parent
    try:
        layers = discover_layers(repository)
        parsed_layers = {
            name: parse_vector(path) for name, path in layers.items()
        }
    except (ET.ParseError, OSError, ValueError) as error:
        print(f"HARD FAIL: {error}", file=sys.stderr)
        return 1

    failed = False
    for name, path in layers.items():
        radius = measure_paths(parsed_layers[name])
        status = "PASS" if radius <= LIMIT else "FAIL"
        print(
            f"{display_path(path, repository)}: "
            f"max radius {radius:.6f} dp ({status})"
        )
        failed = failed or radius > LIMIT

    compositing = compositing_gap(parsed_layers["foreground"])
    compositing_status = (
        "PASS" if compositing >= MIN_COMPOSITING_GAP else "FAIL"
    )
    print(
        f"COMPOSITING: minimum coverage gap {compositing:.6f} "
        f"(limit {MIN_COMPOSITING_GAP:.2f}) ({compositing_status})"
    )
    failed = failed or compositing < MIN_COMPOSITING_GAP

    monochrome_paths = parsed_layers["monochrome"]
    if len(monochrome_paths) != 2:
        print(
            "MONOCHROME GAP: expected exactly two paths, "
            f"found {len(monochrome_paths)} (FAIL)"
        )
        failed = True
    else:
        gap = polyline_gap(
            monochrome_paths[0][1],
            monochrome_paths[1][1],
        )
        gap_status = (
            "PASS"
            if abs(gap - EXPECTED_MONOCHROME_GAP)
            <= MONOCHROME_GAP_TOLERANCE
            else "FAIL"
        )
        print(
            f"MONOCHROME GAP: minimum distance {gap:.6f} dp "
            f"(expected {EXPECTED_MONOCHROME_GAP:.2f} "
            f"+/- {MONOCHROME_GAP_TOLERANCE:.2f}) ({gap_status})"
        )
        failed = failed or gap_status == "FAIL"
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
