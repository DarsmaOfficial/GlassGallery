#!/usr/bin/env python3

"""Audit Kotlin sources for destructive media calls."""

from pathlib import Path
import re
import sys
from typing import Optional


REPO_ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = REPO_ROOT / "app/src/main/java"
PACKAGE_PREFIX = "com/darsma/glassgallery/"
REQUEST_ALLOWLIST = {"ui/trash/TrashScreen.kt"}
DIRECT_DELETE_ALLOWLIST = {"ui/trim/VideoTrimScreen.kt"}

REQUEST_PATTERN = re.compile(r"\b(create(?:Trash|Delete)Request)\s*\(")
DIRECT_DELETE_PATTERN = re.compile(
    r"\b(?:(?:contentResolver|resolver)\s*\.\s*delete"
    r"|(?:DocumentsContract\s*\.\s*)?deleteDocument)\s*\("
)
FILE_DELETE_PATTERN = re.compile(
    r"\b([A-Za-z_]\w*)\s*(?:\?\.|\.)\s*delete\s*\("
)


def package_path(relative_path: str) -> Optional[str]:
    if not relative_path.startswith(PACKAGE_PREFIX):
        return None
    return relative_path[len(PACKAGE_PREFIX) :]


def is_allowed(relative_path: str, allowlist: set[str]) -> bool:
    path = package_path(relative_path)
    return path is not None and path in allowlist


def mask_non_code(text: str) -> str:
    """Replace comments and literals with spaces while preserving offsets."""
    masked = list(text)
    index = 0
    state = "code"

    while index < len(text):
        if state == "code":
            if text.startswith("//", index):
                masked[index : index + 2] = "  "
                index += 2
                state = "line_comment"
            elif text.startswith("/*", index):
                masked[index : index + 2] = "  "
                index += 2
                state = "block_comment"
            elif text.startswith('"""', index):
                masked[index : index + 3] = "   "
                index += 3
                state = "triple_string"
            elif text[index] == '"':
                masked[index] = " "
                index += 1
                state = "string"
            elif text[index] == "'":
                masked[index] = " "
                index += 1
                state = "character"
            else:
                index += 1
        elif state == "line_comment":
            if text[index] == "\n":
                state = "code"
            else:
                masked[index] = " "
            index += 1
        elif state == "block_comment":
            if text.startswith("*/", index):
                masked[index : index + 2] = "  "
                index += 2
                state = "code"
            else:
                if text[index] != "\n":
                    masked[index] = " "
                index += 1
        elif state == "triple_string":
            if text.startswith('"""', index):
                masked[index : index + 3] = "   "
                index += 3
                state = "code"
            else:
                if text[index] != "\n":
                    masked[index] = " "
                index += 1
        else:
            if text[index] == "\\" and index + 1 < len(text):
                masked[index] = " "
                if text[index + 1] != "\n":
                    masked[index + 1] = " "
                index += 2
            elif (state == "string" and text[index] == '"') or (
                state == "character" and text[index] == "'"
            ):
                masked[index] = " "
                index += 1
                state = "code"
            else:
                if text[index] != "\n":
                    masked[index] = " "
                index += 1

    return "".join(masked)


def closing_parenthesis(text: str, opening: int) -> Optional[int]:
    depth = 0
    for index in range(opening, len(text)):
        if text[index] == "(":
            depth += 1
        elif text[index] == ")":
            depth -= 1
            if depth == 0:
                return index
    return None


def last_argument(arguments: str) -> str:
    depths = {"(": 0, "[": 0, "{": 0}
    last_comma = -1
    pairs = {")": "(", "]": "[", "}": "{"}

    for index, character in enumerate(arguments):
        if character in depths:
            depths[character] += 1
        elif character in pairs:
            opener = pairs[character]
            if depths[opener] > 0:
                depths[opener] -= 1
        elif character == "," and not any(depths.values()):
            last_comma = index

    return arguments[last_comma + 1 :].strip()


def line_number(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def source_line(text: str, number: int) -> str:
    return text.splitlines()[number - 1].strip()


def display_path(relative_path: str) -> str:
    return f"app/src/main/java/{relative_path}"


def print_inventory(
    heading: str, sites: list[tuple[str, int, str]]
) -> None:
    print(heading)
    if not sites:
        print("  (none)")
        return
    for relative_path, number, description in sites:
        print(f"{display_path(relative_path)}:{number}: {description}")


def main() -> int:
    resolved_source_root = SOURCE_ROOT.resolve()
    if not SOURCE_ROOT.is_dir():
        print("ERROR: source root is not a directory:")
        print(f"  {resolved_source_root}")
        print("Guard could not scan Kotlin sources.")
        return 2

    request_sites: list[tuple[str, int, str, str]] = []
    direct_delete_sites: list[tuple[str, int, str]] = []
    file_delete_sites: list[tuple[str, int, str]] = []

    for path in sorted(SOURCE_ROOT.rglob("*.kt")):
        relative_path = path.relative_to(SOURCE_ROOT).as_posix()
        text = path.read_text(encoding="utf-8")
        masked_text = mask_non_code(text)

        for match in DIRECT_DELETE_PATTERN.finditer(masked_text):
            number = line_number(masked_text, match.start())
            direct_delete_sites.append(
                (relative_path, number, source_line(text, number))
            )

        for match in FILE_DELETE_PATTERN.finditer(masked_text):
            if match.group(1) in {"contentResolver", "resolver"}:
                continue
            number = line_number(masked_text, match.start())
            file_delete_sites.append(
                (relative_path, number, source_line(text, number))
            )

        for match in REQUEST_PATTERN.finditer(masked_text):
            opening = match.end() - 1
            closing = closing_parenthesis(masked_text, opening)
            number = line_number(masked_text, match.start())
            arguments = (
                masked_text[opening + 1 : closing]
                if closing is not None
                else ""
            )
            request_sites.append(
                (
                    relative_path,
                    number,
                    match.group(1),
                    last_argument(arguments),
                )
            )

    if not request_sites:
        print("ERROR: no createTrashRequest/createDeleteRequest sites found under:")
        print(f"  {resolved_source_root}")
        print("Guard scan is broken; this repository must contain request sites.")
        return 2

    request_inventory = [
        (
            relative_path,
            number,
            f"{call_name} (last argument: {argument or '<unparsed>'})",
        )
        for relative_path, number, call_name, argument in request_sites
    ]
    print_inventory("MediaStore request inventory:", request_inventory)
    print_inventory(
        "Resolver/document deletion inventory:", direct_delete_sites
    )
    print_inventory(
        "File.delete() report-only inventory:", file_delete_sites
    )

    request_offenders = [
        site
        for site in request_sites
        if not is_allowed(site[0], REQUEST_ALLOWLIST)
        and (
            site[2] == "createDeleteRequest"
            or (site[2] == "createTrashRequest" and site[3] != "true")
        )
    ]
    direct_delete_offenders = [
        site
        for site in direct_delete_sites
        if not is_allowed(site[0], DIRECT_DELETE_ALLOWLIST)
    ]

    if request_offenders or direct_delete_offenders:
        print("ERROR: unapproved destructive media calls found:")
        for relative_path, number, call_name, argument in request_offenders:
            if call_name == "createDeleteRequest":
                reason = "permanent deletion is not allowlisted"
            else:
                reason = (
                    "createTrashRequest last argument must be true "
                    f"(found {argument or '<unparsed>'})"
                )
            print(
                f"{display_path(relative_path)}:{number}: "
                f"{call_name}: {reason}"
            )
        for relative_path, number, source in direct_delete_offenders:
            print(
                f"{display_path(relative_path)}:{number}: "
                f"direct deletion is not allowlisted: {source}"
            )
        return 1

    print("OK: destructive media calls are limited to the explicit allowlists.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
