#!/usr/bin/env bash
# Uji tata letak panel native XyDesk (murni angka, tidak butuh Windows).
#
# Dipakai CI Linux dan bisa dijalankan lokal: script ini mengompilasi
# packaging/tests/native-panel-layout-test.cpp dengan g++ lalu menjalankannya.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
out_dir="${TMPDIR:-/tmp}/xydesk-native-panel-test"
mkdir -p "$out_dir"
binary="$out_dir/native-panel-layout-test"

cxx="${CXX:-g++}"
"$cxx" -std=c++20 -O2 -Wall -Wextra -Werror \
    -I "$root/packaging/native-host" \
    "$root/packaging/tests/native-panel-layout-test.cpp" \
    -o "$binary"

"$binary"
