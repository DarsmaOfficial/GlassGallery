#!/usr/bin/env bash

set -euo pipefail

apk_path=${1:-app/build/outputs/apk/release/app-release.apk}

if [[ ! -f "$apk_path" || ! -r "$apk_path" ]]; then
    printf 'ERROR: release APK is missing or unreadable: %s\n' "$apk_path" >&2
    exit 1
fi

sdk_roots=()
if [[ -n ${ANDROID_HOME:-} ]]; then
    sdk_roots+=("$ANDROID_HOME")
fi
if [[ -n ${ANDROID_SDK_ROOT:-} && ${ANDROID_SDK_ROOT:-} != "${ANDROID_HOME:-}" ]]; then
    sdk_roots+=("$ANDROID_SDK_ROOT")
fi

aapt2_path=
shopt -s nullglob
for sdk_root in "${sdk_roots[@]}"; do
    for candidate in "$sdk_root"/build-tools/*/aapt2; do
        if [[ -x "$candidate" ]]; then
            aapt2_path=$candidate
        fi
    done
done
shopt -u nullglob

if [[ -z "$aapt2_path" ]]; then
    printf 'ERROR: could not locate an executable aapt2 under ANDROID_HOME or ANDROID_SDK_ROOT build-tools directories.\n' >&2
    exit 1
fi

if permissions_output=$("$aapt2_path" dump permissions "$apk_path" 2>&1); then
    :
else
    dump_status=$?
    printf 'ERROR: aapt2 failed to dump permissions from %s (exit %d).\n' \
        "$apk_path" "$dump_status" >&2
    if [[ -n "$permissions_output" ]]; then
        printf '%s\n' "$permissions_output" >&2
    fi
    exit 1
fi

if [[ -z "$permissions_output" ]]; then
    printf 'ERROR: aapt2 returned empty permission output for %s; refusing to pass without evidence.\n' \
        "$apk_path" >&2
    exit 1
fi

permission_names=()
permission_entries=()
unparseable_permission_entries=()
while IFS= read -r line; do
    if [[ "$line" =~ ^[[:space:]]*uses-permission[^:]*: ]]; then
        if [[ "$line" =~ ^[[:space:]]*uses-permission[^:]*:[[:space:]]+name=\'([^\']+)\' ]]; then
            permission_names+=("${BASH_REMATCH[1]}")
            permission_entries+=("$line")
        else
            unparseable_permission_entries+=("$line")
        fi
    fi
done <<< "$permissions_output"

if (( ${#unparseable_permission_entries[@]} > 0 )); then
    printf 'ERROR: aapt2 returned unparseable permission entries for %s; refusing to ignore them.\n' \
        "$apk_path" >&2
    printf 'Unparseable permission entries:\n' >&2
    printf '  %s\n' "${unparseable_permission_entries[@]}" >&2
    printf 'Full aapt2 output:\n%s\n' "$permissions_output" >&2
    exit 1
fi

if (( ${#permission_names[@]} == 0 )); then
    printf 'ERROR: aapt2 output contained no parseable permission entries for %s; refusing to pass without evidence.\n' \
        "$apk_path" >&2
    printf 'Full aapt2 output:\n%s\n' "$permissions_output" >&2
    exit 1
fi

offending_entries=()
for index in "${!permission_names[@]}"; do
    case "${permission_names[$index]}" in
        android.permission.INTERNET|android.permission.ACCESS_NETWORK_STATE)
            offending_entries+=("${permission_entries[$index]}")
            ;;
    esac
done

if (( ${#offending_entries[@]} > 0 )); then
    printf 'ERROR: banned network permission present in the built APK.\n' >&2
    printf 'Offending manifest entry:\n' >&2
    printf '  %s\n' "${offending_entries[@]}" >&2
    printf 'Full permission list:\n' >&2
    printf '  %s\n' "${permission_names[@]}" >&2
    printf 'A transitive dependency is the likely source. Inspect it with: gradle :app:dependencies\n' >&2
    exit 1
fi

printf 'Permissions found in built APK:\n'
printf '  %s\n' "${permission_names[@]}"
printf 'No banned network permissions found.\n'
