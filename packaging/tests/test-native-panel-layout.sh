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

json_binary="$out_dir/native-engine-json-test"
"$cxx" -std=c++20 -O2 -Wall -Wextra -Werror \
    -I "$root/packaging/native-host" \
    "$root/packaging/tests/native-engine-json-test.cpp" \
    -o "$json_binary"
"$json_binary"

onboarding_binary="$out_dir/native-onboarding-test"
"$cxx" -std=c++20 -O2 -Wall -Wextra -Werror \
    -I "$root/packaging/native-host" \
    "$root/packaging/tests/native-onboarding-test.cpp" -o "$onboarding_binary"
"$onboarding_binary"

updater_binary="$out_dir/native-updater-test"
"$cxx" -std=c++20 -O2 -Wall -Wextra -Werror \
    -I "$root/packaging/native-host" \
    "$root/packaging/tests/native-updater-test.cpp" -o "$updater_binary"
"$updater_binary"

contract_binary="$out_dir/native-control-contract-test"
"$cxx" -std=c++20 -O2 -Wall -Wextra -Werror \
    -I "$root/packaging/native-host" \
    "$root/packaging/tests/native-control-contract-test.cpp" -o "$contract_binary"
"$contract_binary"

# Pemeriksaan sintaks panel Win32 tanpa Windows.
#
# main.cpp hanya dikompilasi oleh job Windows CI, jadi kesalahan sepele
# (identifier belum dideklarasikan, namespace salah) baru ketahuan setelah
# antre di belakang build Rust yang panjang. MinGW mengurai header Win32 yang
# sama dan menangkap kelas kesalahan itu dalam hitungan detik. Ini BUKAN
# pengganti build MSVC — hanya penyaring cepat, dan dilewati bila MinGW tidak
# terpasang.
mingw="${MINGW_CXX:-x86_64-w64-mingw32-g++}"
if command -v "$mingw" >/dev/null 2>&1; then
    "$mingw" -fsyntax-only -std=c++20 -DUNICODE -D_UNICODE \
        -I "$root/packaging/native-host" "$root/packaging/native-host/main.cpp"
    echo "Sintaks panel Win32: lulus (MinGW)."
else
    echo "Lewati pemeriksaan sintaks Win32: $mingw tidak terpasang."
fi
