#!/usr/bin/env bash

set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
tmp_root=$(mktemp -d "${TMPDIR:-/tmp}/manifest-permissions-test.XXXXXX")

cleanup() {
    if [[ -n "${tmp_root:-}" && -d "$tmp_root" ]]; then
        rm -rf -- "$tmp_root"
    fi
}
trap cleanup EXIT

cp -R "$script_dir" "$tmp_root/tools"

test_tools_dir=$tmp_root/tools
expected_permissions_path=$test_tools_dir/expected_permissions.txt
sdk_root=$tmp_root/sdk
aapt2_path=$sdk_root/build-tools/test/aapt2
apk_path=$tmp_root/app.apk

mkdir -p "$(dirname -- "$aapt2_path")"
printf '%s\n' \
    '#!/usr/bin/env bash' \
    'set -euo pipefail' \
    'printf '\''%s\n'\'' "${STUB_PERMISSION_OUTPUT:?}"' > "$aapt2_path"
chmod +x "$aapt2_path"
: > "$apk_path"

failures=0
last_status=0
last_output=

run_checker() {
    if last_output=$(
        ANDROID_HOME="$sdk_root" \
        ANDROID_SDK_ROOT="$sdk_root" \
        STUB_PERMISSION_OUTPUT="$1" \
        bash "$test_tools_dir/check_manifest_permissions.sh" "$apk_path" 2>&1
    ); then
        last_status=0
    else
        last_status=$?
    fi
}

report_result() {
    local name=$1
    local expected_status=$2
    local expected_message=$3

    if (( last_status == expected_status )) && [[ "$last_output" == *"$expected_message"* ]]; then
        printf 'PASS: %s (exit %d; matched "%s")\n' \
            "$name" "$last_status" "$expected_message"
        return
    fi

    printf 'FAIL: %s (expected exit %d and message "%s"; got exit %d)\n' \
        "$name" "$expected_status" "$expected_message" "$last_status" >&2
    printf '%s\n' "$last_output" >&2
    failures=$((failures + 1))
}

run_case() {
    local name=$1
    local expected_status=$2
    local expected_message=$3
    local expected_file_content=$4
    local stub_output=$5

    printf '%s' "$expected_file_content" > "$expected_permissions_path"
    run_checker "$stub_output"
    report_result "$name" "$expected_status" "$expected_message"
}

read_images_entry="uses-permission: name='android.permission.READ_MEDIA_IMAGES'"
internet_entry="uses-permission: name='android.permission.INTERNET'"
camera_entry="uses-permission: name='android.permission.CAMERA'"
ad_id_entry="uses-permission: name='com.google.android.gms.permission.AD_ID'"

run_case \
    "clean" \
    0 \
    "No banned permissions found." \
    $'android.permission.READ_MEDIA_IMAGES\n' \
    "$read_images_entry"

run_case \
    "unexpected" \
    1 \
    "UNEXPECTED: android.permission.CAMERA" \
    $'android.permission.READ_MEDIA_IMAGES\n' \
    "$read_images_entry"$'\n'"$camera_entry"

run_case \
    "missing" \
    1 \
    "MISSING: android.permission.READ_MEDIA_VIDEO" \
    $'android.permission.READ_MEDIA_IMAGES\nandroid.permission.READ_MEDIA_VIDEO\n' \
    "$read_images_entry"

run_case \
    "banned permission also present in expected file" \
    1 \
    "ERROR: banned permission present in the built APK." \
    $'com.google.android.gms.permission.AD_ID\n' \
    "$ad_id_entry"

mv "$expected_permissions_path" "$tmp_root/expected_permissions.saved"
run_checker "$read_images_entry"
report_result \
    "expected file missing" \
    2 \
    "ERROR: expected permission file is missing or unreadable:"

run_case \
    "expected file empty" \
    2 \
    "ERROR: expected permission file contains no permission entries:" \
    "" \
    "$read_images_entry"

run_case \
    "expected file comments only" \
    2 \
    "ERROR: expected permission file contains no permission entries:" \
    $'# first comment\n   # second comment\n\n' \
    "$read_images_entry"

run_case \
    "expected file duplicate entry" \
    0 \
    "No banned permissions found." \
    $'android.permission.READ_MEDIA_IMAGES\nandroid.permission.READ_MEDIA_IMAGES\n' \
    "$read_images_entry"

run_case \
    "expected file without trailing newline" \
    0 \
    "No banned permissions found." \
    "android.permission.READ_MEDIA_IMAGES" \
    "$read_images_entry"

run_case \
    "banned network plus missing" \
    1 \
    "ERROR: banned network permission present in the built APK." \
    $'android.permission.READ_MEDIA_VIDEO\n' \
    "$internet_entry"

if (( failures > 0 )); then
    printf '%d of 10 cases failed.\n' "$failures" >&2
    exit 1
fi

printf 'All 10 cases passed.\n'
