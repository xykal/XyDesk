// XyDesk untuk Windows — panel kontrol native C++ (Win32 murni, tanpa WebView).
//
// Jendela ini digambar sendiri seluruhnya: tanpa caption Windows, tanpa border
// bawaan, tanpa kontrol sistem. Sudut membulat, bayangan, dan tepi yang
// dihaluskan dihitung di `layout.h` lalu dirasterkan ke permukaan jendela
// berlapis (WS_EX_LAYERED + UpdateLayeredWindow) supaya bentuknya sama persis
// di Windows 10 dan Windows 11 — tidak bergantung pada pembulatan bawaan DWM
// yang hanya ada di Windows 11 dan radiusnya tidak bisa diatur.
//
// Pembagian tugas tidak berubah: EXE ini launcher + panel, engine streaming
// tetap `xydesk-host.exe` (proses terpisah, diawasi Job Object).

#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif
#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0A00
#endif
// Berkas ini UTF-8 dan memuat tanda baca non-ASCII di teks panel (·, —, …).
// MSVC membacanya sebagai codepage sistem tanpa flag ini, dan hasilnya mojibake
// di jendela pengguna; karena itu setiap pemanggilan cl.exe di workflow memakai
// /utf-8, dan tool/check_panel_text.py menjaga hasilnya.
#ifndef _WIN32_IE
#define _WIN32_IE 0x0A00
#endif
// Windows.h mendefinisikan makro min/max yang mematahkan std::min/std::max di
// MSVC (error C2589 "illegal token on right side of '::'"). Layout dan gambar
// memakai std::min/std::max/std::clamp, jadi makro itu dinonaktifkan di sini.
#ifndef NOMINMAX
#define NOMINMAX
#endif

#pragma comment(linker, "\"/manifestdependency:type='win32' name='Microsoft.Windows.Common-Controls' version='6.0.0.0' processorArchitecture='*' publicKeyToken='6595b64144ccf1df' language='*'\"")
#include <winsock2.h>
#include <windows.h>
#include <cmath>
#include <algorithm>
#include <functional>
#include <vector>
#include <shellapi.h>
#include <commdlg.h>

#include "resource.h"
#include "layout.h"
#include "gate_layout.h"
#include "onboarding.h"
#include "email_login.h"
#include "engine_json.h"
#include "control_client.h"
#include "account_auth.h"
#include "update_fetch.h"
#include "session_view.h"
#include "vendor/qrcodegen/qrcodegen.hpp"
// Build workflows compile one panel translation unit; retain upstream implementation.
#include "vendor/qrcodegen/qrcodegen.cpp"
#include <limits>

#include <algorithm>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>
#include <fstream>
#include <future>
#include <chrono>

#if defined(_MSC_VER)
#pragma comment(lib, "user32.lib")
#pragma comment(lib, "gdi32.lib")
#pragma comment(lib, "shell32.lib")
#pragma comment(lib, "advapi32.lib")
#pragma comment(lib, "comdlg32.lib")
#endif

namespace {

using xydesk::panel::Page;
using xydesk::panel::PanelLayout;
using xydesk::panel::Rect;
using xydesk::panel::Target;

constexpr wchar_t kClassName[] = L"XyDeskNativeControlPanel";
constexpr wchar_t kWindowTitle[] = L"XyDesk Control Panel";
constexpr wchar_t kWebUrl[] = L"https://remote.xydesk.my.id/devices";
constexpr wchar_t kEngineName[] = L"xydesk-host.exe";

constexpr UINT kTrayMessage = WM_APP + 11;
constexpr UINT kAutoStartMessage = WM_APP + 12;
constexpr UINT kTrayId = 1;
constexpr UINT_PTR kTimer = 7;
constexpr UINT_PTR kAnimTimer = 8; // tick morphing 16ms, hidup hanya saat animasi
constexpr UINT kFlashDurationMs = 2600;
#ifndef WM_DPICHANGED
constexpr UINT WM_DPICHANGED = 0x02E0;
#endif

// ── Palet XyDesk — satu sumber dengan aplikasi Android
// (`android-native/.../ui/kit/Theme.kt`, objek `Xy`). Panel sempat memakai
// aksen abu-abu gelap #30353C sementara APK, web, dan ikonnya memakai ungu
// #7C3AED: satu produk dengan dua warna utama terlihat seperti dua program
// yang kebetulan senama. Nilai di bawah disalin apa adanya dari `Xy` supaya
// perbedaan berikutnya ketahuan sebagai perbedaan angka, bukan selera.
constexpr COLORREF kBackground = RGB(255, 255, 255);  // Xy.bg
constexpr COLORREF kSurface = RGB(255, 255, 255);     // Xy.raised
constexpr COLORREF kSurface2 = RGB(244, 244, 247);    // Xy.overlay #F4F4F7
constexpr COLORREF kSurface3 = RGB(236, 236, 242);    // overlay hover
constexpr COLORREF kSurfacePressed = RGB(226, 226, 234);
constexpr COLORREF kEdge = RGB(232, 232, 237);        // Xy.line #E8E8ED
constexpr COLORREF kText = RGB(24, 24, 27);           // Xy.textHi #18181B
constexpr COLORREF kMuted = RGB(107, 107, 118);       // Xy.textMid #6B6B76
constexpr COLORREF kDisabled = RGB(154, 154, 162);    // Xy.textLow #9A9AA2
constexpr COLORREF kOnAccent = RGB(255, 255, 255);    // teks di atas ungu
constexpr COLORREF kAccent = RGB(124, 58, 237);       // Xy.accent #7C3AED
constexpr COLORREF kAccentHover = RGB(139, 92, 246);  // #8B5CF6
constexpr COLORREF kAccentPressed = RGB(109, 40, 217);// Xy.accentDeep #6D28D9
constexpr COLORREF kAccentSoft = RGB(243, 238, 255);  // latar lembut aksen
constexpr COLORREF kGood = RGB(22, 115, 71);          // Xy.success #167347
constexpr COLORREF kWarn = RGB(133, 84, 0);           // Xy.warning #855400
constexpr COLORREF kBad = RGB(180, 35, 50);           // Xy.danger #B42332

constexpr int kTrayOpen = 1010;
constexpr int kTrayStart = 1011;
constexpr int kTrayStop = 1012;
constexpr int kTrayWeb = 1013;
constexpr int kTrayQuit = 1014;
constexpr int kTrayRestart = 1015;
constexpr int kTrayUpdate = 1016;

// Versi host diisi compiler dari berkas VERSION (lihat build.yml). Build
// lokal tanpa definisi itu memakai nilai kosong: pemeriksa pembaruan lebih
// baik mengaku tidak tahu versinya sendiri daripada menebak, lalu menawarkan
// pemasangan ulang yang tidak perlu.
#ifndef XYDESK_VERSION
#define XYDESK_VERSION ""
#endif
constexpr char kHostVersion[] = XYDESK_VERSION;
constexpr UINT kUpdateCheckedMessage = WM_APP + 13;
constexpr UINT kUpdateInstalledMessage = WM_APP + 14;
constexpr UINT kGateSubmitMessage = WM_APP + 15; // Enter di kotak isian gerbang
constexpr UINT kGateBackMessage = WM_APP + 16;   // Esc di kotak isian gerbang
constexpr int kGateEditId = 1301;

// Keadaan pembaruan ditaruh di luar AppState karena diisi dari thread lain:
// hasilnya baru dibaca thread UI setelah pesan jendela sampai.
struct UpdateState {
    std::future<void> worker;
    std::atomic_bool busy{false};
    std::atomic_bool cancelled{false};
    xydesk::updater::Decision decision = xydesk::updater::Decision::Unreadable;
    xydesk::updater::Release release;
    xydesk::updater::InstallResult install = xydesk::updater::InstallResult::NetworkFailed;
};
UpdateState updateState;

COLORREF mixColor(COLORREF from, COLORREF to, float t) {
    t = std::clamp(t, 0.0f, 1.0f);
    const auto lerp = [t](int a, int b) {
        return static_cast<int>(a + (b - a) * t + 0.5f);
    };
    return RGB(lerp(GetRValue(from), GetRValue(to)),
        lerp(GetGValue(from), GetGValue(to)),
        lerp(GetBValue(from), GetBValue(to)));
}

std::uint32_t packPremultiplied(COLORREF color, float alpha) {
    const float a = std::clamp(alpha, 0.0f, 1.0f);
    const auto channel = [a](BYTE value) {
        return static_cast<std::uint32_t>(static_cast<float>(value) * a + 0.5f);
    };
    const std::uint32_t sa = static_cast<std::uint32_t>(a * 255.0f + 0.5f);
    return (sa << 24) | (channel(GetRValue(color)) << 16) | (channel(GetGValue(color)) << 8) | channel(GetBValue(color));
}

struct Surface {
    HDC dc = nullptr;
    HBITMAP bitmap = nullptr;
    HGDIOBJ previous = nullptr;
    std::uint32_t* pixels = nullptr;
    int width = 0;
    int height = 0;

    [[nodiscard]] bool valid() const { return dc && bitmap && pixels; }
};

struct AppState {
    HWND window = nullptr;
    HFONT fontIcons = nullptr;
    bool sidebarCollapsed = false;
    int panelBitrate = -1;
    HFONT fontHeading = nullptr;
    HFONT fontTitle = nullptr;
    HFONT fontBody = nullptr;
    HFONT fontSmall = nullptr;
    HFONT fontMono = nullptr;
    HFONT fontLogo = nullptr;
    HFONT fontSemi = nullptr;  // 14 semibold: teks status utama
    HFONT fontCaps = nullptr;  // 11 semibold: judul kartu & label sidebar
    HFONT fontValue = nullptr; // 26 semibold mono: ID & kode pairing
    Surface surface;
    PanelLayout layout;
    Target hot = Target::None;
    Target pressed = Target::None;
    Target focused = Target::None;
    Page page = Page::Status;
    // Morphing UI: pill sidebar meluncur antar item dan halaman meluncur
    // saat berpindah — animasi 60fps hanya selama transisi berjalan.
    Page pageFrom = Page::Status;
    float pageT = 1.0f;
    float pillY = -1.0f;
    bool animOn = false;
    bool trackingMouse = false;
    bool layered = false;
    // Kesehatan capture dari engine (heartbeat publik per PID): buat kartu Status
    // jujur soal layar hitam / sesi berbeda.
    std::wstring captureBackend;
    std::wstring captureNote;
    std::wstring captureNote2;
    bool captureWarn = false;
    bool captureSeen = false;
    bool sessionMismatch = false;
    std::wstring procUser;
    std::wstring activeUser;
    int procSession = -1;
    int activeSession = -1;
    // Perbesar = panel dizoom proporsional (bukan maximized Win32, karena
    // jendela ini WS_POPUP berlapis). zoomPct dikalikan ke DPI efektif.
    int zoomPct = 100;
    // Ukuran panel dalam satuan 96-DPI; pengguna bisa menarik tepi jendela
    // (kiri/kanan/atas/bawah) untuk mengubahnya.
    int unitsW = xydesk::panel::kPanelWidth;
    int unitsH = xydesk::panel::kPanelHeight;
    bool maximized = false;
    RECT normalRect{};
    bool haveNormalRect = false;
    std::wstring statusText = L"Menyiapkan host…";
    COLORREF statusColor = kMuted;
    std::wstring flashText;
    ULONGLONG flashUntil = 0;
    std::wstring logPath;
    std::wstring deviceId;
    std::wstring pairingCode;
    bool showPairingCode = false; // nilai pairing disembunyikan sampai mata ditekan
    std::wstring lastError;
    HANDLE process = nullptr;
    HANDLE job = nullptr;
    HANDLE logFile = nullptr;
    bool running = false;
    bool startRequested = false;

    // ── Gerbang masuk: sambutan → login → panel ──
    xydesk::onboarding::Gate gate;
    xydesk::onboarding::Clock gateMotion;   // kartu muncul / berpindah layar
    xydesk::panel::GateLayout gateLayout{};
    int cursorX = -1, cursorY = -1;
    int gateHot = 0;      // 0 tidak ada, 1 utama, 2 kedua, 3 keluar
    int gatePressed = 0;
    std::future<xydesk::account::Result> gateRestore;
    std::future<xydesk::account::Result> gateLogin;
    std::atomic_bool gateCancelled{false};
    std::wstring gateAccount;
    // Formulir masuk-email di dalam kartu gerbang. `gateEmail` menyala saat
    // pengguna memilih jalur email; `gateEditBox` adalah satu-satunya kontrol
    // Windows di layar gerbang (kotak isian), sisanya tetap digambar sendiri.
    bool gateEmail = false;
    xydesk::emaillogin::Form gateForm;
    HWND gateEditBox = nullptr;
    WNDPROC gateEditBase = nullptr;
    std::future<xydesk::account::OtpResult> gateOtp;
    bool gateOtpVerifying = false;
};

AppState g;
xydesk::panel_control::Channel controlChannel;
UINT g_taskbarCreated = 0;

void removeTrayIcon();
void showTrayMenu(HWND hwnd);
bool startHost();
void stopHost();
void renderPanel();
void setStatus(const std::wstring& text, COLORREF color);

// ── Berkas mesin: ± sama seperti sebelumnya -------------------------------

std::wstring moduleDirectory() {
    wchar_t path[MAX_PATH]{};
    const DWORD n = GetModuleFileNameW(nullptr, path, MAX_PATH);
    if (!n || n >= MAX_PATH) return L".";
    std::wstring result(path, n);
    const auto slash = result.find_last_of(L"\\/");
    return slash == std::wstring::npos ? L"." : result.substr(0, slash);
}

std::wstring enginePath() {
    return moduleDirectory() + L"\\" + kEngineName;
}

std::wstring hostLogPath() {
    wchar_t localAppData[MAX_PATH]{};
    const DWORD n = GetEnvironmentVariableW(L"LOCALAPPDATA", localAppData, ARRAYSIZE(localAppData));
    const std::wstring base = n && n < ARRAYSIZE(localAppData)
        ? std::wstring(localAppData, n)
        : moduleDirectory();
    const auto directory = base + L"\\XyDesk";
    CreateDirectoryW(directory.c_str(), nullptr);
    return directory + L"\\host.log";
}

std::wstring quote(const std::wstring& value) {
    std::wstring out = L"\"";
    for (const wchar_t c : value) {
        if (c == L'"') out += L'\\';
        out += c;
    }
    out += L"\"";
    return out;
}

std::wstring jsonString(const std::string& json, const char* key) {
    const auto object = xydesk::engine_json::parse(json);
    if (!object) return L"";
    const auto found = object->find(key);
    if (found == object->end()) return L"";
    const auto value = std::get_if<std::string>(&found->second);
    if (!value || value->empty()) return L"";
    const int size = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, value->data(), static_cast<int>(value->size()), nullptr, 0);
    if (!size) return L"";
    std::wstring result(size, L'\0');
    if (MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, value->data(), static_cast<int>(value->size()), result.data(), size) != size) return L"";
    return result;
}

struct IdentityResult { std::wstring id; std::wstring password; };
std::future<IdentityResult> identityFuture;

IdentityResult readIdentityBounded() {
    SECURITY_ATTRIBUTES sa{sizeof(SECURITY_ATTRIBUTES), nullptr, TRUE};
    HANDLE readPipe = nullptr;
    HANDLE writePipe = nullptr;
    if (!CreatePipe(&readPipe, &writePipe, &sa, 0)) return {};
    SetHandleInformation(readPipe, HANDLE_FLAG_INHERIT, 0);

    const std::wstring command = quote(enginePath()) + L" --identity-json";
    std::vector<wchar_t> commandLine(command.begin(), command.end());
    commandLine.push_back(L'\0');
    STARTUPINFOW si{};
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESTDHANDLES;
    si.hStdOutput = writePipe;
    si.hStdError = writePipe;
    PROCESS_INFORMATION pi{};
    const BOOL started = CreateProcessW(nullptr, commandLine.data(), nullptr, nullptr, TRUE,
        CREATE_NO_WINDOW, nullptr, moduleDirectory().c_str(), &si, &pi);
    CloseHandle(writePipe);
    if (!started) {
        CloseHandle(readPipe);
        return {};
    }

    std::string output;
    char buffer[1024];
    DWORD got = 0;
    const ULONGLONG deadline = GetTickCount64() + 5000;
    bool completed = false;
    while (GetTickCount64() < deadline && output.size() <= 8192) {
        DWORD available = 0;
        if (!PeekNamedPipe(readPipe, nullptr, 0, nullptr, &available, nullptr)) {
            completed = WaitForSingleObject(pi.hProcess, 0) == WAIT_OBJECT_0;
            break;
        }
        if (available) {
            const DWORD want = std::min<DWORD>(available, sizeof(buffer));
            if (!ReadFile(readPipe, buffer, want, &got, nullptr)) break;
            output.append(buffer, buffer + got);
        } else if (WaitForSingleObject(pi.hProcess, 0) == WAIT_OBJECT_0) {
            completed = true;
            break;
        } else {
            Sleep(10); // worker saja; thread UI tidak menunggu pipe
        }
    }
    CloseHandle(readPipe);
    if (!completed || output.size() > 8192) {
        TerminateProcess(pi.hProcess, 1);
        WaitForSingleObject(pi.hProcess, 1000);
    }
    DWORD exitCode = 1;
    GetExitCodeProcess(pi.hProcess, &exitCode);
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
    if (!completed || exitCode != 0 || output.size() > 8192) return {};
    IdentityResult result{jsonString(output, "deviceId"), jsonString(output, "password")};
    return result;
}

bool jsonFlag(const std::string& json, const char* key) {
    const auto object = xydesk::engine_json::parse(json);
    if (!object) return false;
    const auto found = object->find(key);
    if (found == object->end()) return false;
    const auto value = std::get_if<bool>(&found->second);
    return value && *value;
}

int jsonNumber(const std::string& json, const char* key) {
    const auto object = xydesk::engine_json::parse(json);
    if (!object) return -1;
    const auto found = object->find(key);
    if (found == object->end()) return -1;
    const auto value = std::get_if<std::int64_t>(&found->second);
    if (!value || *value < 0 || *value > std::numeric_limits<int>::max()) return -1;
    return static_cast<int>(*value);
}

// Heartbeat publik per PID; wajib cocok dengan umur proses dan segar <=5s.
// File lama/tidak lengkap tidak boleh membuat UI mengaku host siap.
void readCaptureStatus() {
    auto unavailable = [](const std::wstring& note, bool warn, bool updateStatus) {
        const bool changed = g.captureSeen || !g.captureBackend.empty() || g.captureWarn != warn ||
            g.captureNote != note || !g.captureNote2.empty() || g.sessionMismatch ||
            !g.procUser.empty() || !g.activeUser.empty() || g.procSession != -1 || g.activeSession != -1;
        g.captureSeen = false;
        g.captureBackend.clear();
        g.captureNote = note;
        g.captureNote2.clear();
        g.captureWarn = warn;
        g.sessionMismatch = false;
        g.procUser.clear();
        g.activeUser.clear();
        g.procSession = g.activeSession = -1;
        if (updateStatus && g.running) setStatus(note, warn ? kWarn : kMuted);
        else if (changed) renderPanel();
    };
    auto stale = [&] {
        unavailable(L"Status engine belum tersedia atau sudah kedaluwarsa.", true, true);
    };
    if (!g.process || !g.running) {
        unavailable(L"Capture belum aktif — mulai host untuk menerima koneksi.", false, false);
        return;
    }
    const DWORD pid = GetProcessId(g.process);
    // Sama dengan config_dir() engine: XYDESK_HOME dulu, lalu USERPROFILE\.xydesk.
    const std::wstring path = [&] {
        wchar_t home[MAX_PATH]{};
        DWORD n = GetEnvironmentVariableW(L"XYDESK_HOME", home, ARRAYSIZE(home));
        if (n && n < ARRAYSIZE(home)) return std::wstring(home, n) + L"\\runtime-" + std::to_wstring(pid) + L".json";
        n = GetEnvironmentVariableW(L"USERPROFILE", home, ARRAYSIZE(home));
        const std::wstring base = n && n < ARRAYSIZE(home)
            ? std::wstring(home, n)
            : moduleDirectory();
        return base + L"\\.xydesk\\runtime-" + std::to_wstring(pid) + L".json";
    }();
    HANDLE file = CreateFileW(path.c_str(), GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
        nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE) { stale(); return; }
    FILETIME written{}, now{}, born{}, exited{}, kernel{}, user{};
    LARGE_INTEGER size{};
    GetSystemTimeAsFileTime(&now);
    auto ticks = [](FILETIME t) { return (static_cast<ULONGLONG>(t.dwHighDateTime) << 32) | t.dwLowDateTime; };
    const bool fresh = GetFileTime(file, nullptr, nullptr, &written) && GetFileSizeEx(file, &size)
        && size.QuadPart > 0 && size.QuadPart <= 16384
        && GetProcessTimes(g.process, &born, &exited, &kernel, &user)
        && ticks(written) >= ticks(born) && ticks(written) <= ticks(now)
        && ticks(now) - ticks(written) <= 5ULL * 10000000;
    if (!fresh) { CloseHandle(file); stale(); return; }
    std::string json;
    char buffer[1024];
    DWORD got = 0;
    while (json.size() <= 16384 && ReadFile(file, buffer, sizeof(buffer), &got, nullptr) && got) json.append(buffer, buffer + got);
    CloseHandle(file);
    if (json.empty() || json.size() > 16384 || json.back() != '}' || jsonNumber(json, "pid") != static_cast<int>(pid)) { stale(); return; }
    const std::wstring state = jsonString(json, "state");
    if (state == L"ready") setStatus(L"Host siap menerima pairing", kGood);
    else if (state == L"streaming") setStatus(L"Sesi remote aktif", kGood);
    else if (state == L"standby") setStatus(L"Standby — sesi lain akun ini memegang host", kWarn);
    else if (state == L"connecting") setStatus(L"Menghubungkan signaling…", kMuted);
    else if (state == L"starting") setStatus(L"Engine sedang memulai…", kMuted);
    else { stale(); return; }

    const std::wstring backend = jsonString(json, "backend");
    const bool mismatch = jsonFlag(json, "session_mismatch");
    const bool warn = jsonFlag(json, "black_frames") || mismatch;
    const std::wstring procUser = jsonString(json, "proc_user");
    const std::wstring activeUser = jsonString(json, "active_user");
    const int procSession = jsonNumber(json, "proc_session");
    const int activeSession = jsonNumber(json, "active_session");
    std::wstring note;
    std::wstring note2;
    if (mismatch) {
        note = L"Host ada di sesi " + std::to_wstring(procSession) +
            L"; sesi aktif akun ini adalah " + std::to_wstring(activeSession) +
            L". Menunggu perpindahan leadership; tidak menangkap layar lintas sesi.";
        note2 = L"Koordinasi hanya untuk sesi milik akun Windows yang sama.";
    } else if (jsonFlag(json, "black_frames")) {
        note = L"Capture menghasilkan frame hitam — layar mungkin terkunci atau di secure desktop. Buka kunci PC host.";
    }
    if (backend != g.captureBackend || warn != g.captureWarn || note != g.captureNote ||
        note2 != g.captureNote2 ||
        mismatch != g.sessionMismatch || procUser != g.procUser || activeUser != g.activeUser) {
        g.captureBackend = backend;
        g.captureWarn = warn;
        g.captureNote = note;
        g.captureNote2 = note2;
        g.sessionMismatch = mismatch;
        g.procUser = procUser;
        g.activeUser = activeUser;
        g.procSession = procSession;
        g.activeSession = activeSession;
        g.captureSeen = true;
        renderPanel();
    } else if (!backend.empty()) {
        g.captureSeen = true;
    }
}

void setStatus(const std::wstring& text, COLORREF color) {
    g.statusText = text;
    g.statusColor = color;
    if (g.flashText.empty()) renderPanel();
}

void setFlash(const std::wstring& text, COLORREF color) {
    g.flashText = text;
    g.statusColor = color;
    g.flashUntil = GetTickCount64() + kFlashDurationMs;
    renderPanel();
}

// ── Permukaan gambar -------------------------------------------------------

void destroySurface(Surface& surface) {
    if (surface.dc) {
        if (surface.previous) SelectObject(surface.dc, surface.previous);
        DeleteDC(surface.dc);
        surface.dc = nullptr;
        surface.previous = nullptr;
    }
    if (surface.bitmap) {
        DeleteObject(surface.bitmap);
        surface.bitmap = nullptr;
    }
    surface.pixels = nullptr;
    surface.width = 0;
    surface.height = 0;
}

bool ensureSurface(Surface& surface, int width, int height) {
    if (surface.valid() && surface.width == width && surface.height == height) return true;
    destroySurface(surface);
    if (width <= 0 || height <= 0) return false;

    BITMAPINFO info{};
    info.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
    info.bmiHeader.biWidth = width;
    info.bmiHeader.biHeight = -height; // negatif: baris teratas lebih dulu
    info.bmiHeader.biPlanes = 1;
    info.bmiHeader.biBitCount = 32;
    info.bmiHeader.biCompression = BI_RGB;

    void* bits = nullptr;
    surface.bitmap = CreateDIBSection(nullptr, &info, DIB_RGB_COLORS, &bits, nullptr, 0);
    if (!surface.bitmap || !bits) {
        destroySurface(surface);
        return false;
    }
    surface.dc = CreateCompatibleDC(nullptr);
    if (!surface.dc) {
        destroySurface(surface);
        return false;
    }
    surface.previous = SelectObject(surface.dc, surface.bitmap);
    surface.pixels = static_cast<std::uint32_t*>(bits);
    surface.width = width;
    surface.height = height;
    return true;
}

void clearSurface(Surface& surface) {
    if (!surface.valid()) return;
    std::memset(surface.pixels, 0, static_cast<size_t>(surface.width) * surface.height * sizeof(std::uint32_t));
}

void fillRectOpaque(Surface& surface, const Rect& rect, COLORREF color) {
    if (!surface.valid()) return;
    const int left = std::max(0, rect.x);
    const int top = std::max(0, rect.y);
    const int right = std::min(surface.width, rect.right());
    const int bottom = std::min(surface.height, rect.bottom());
    const std::uint32_t pixel = packPremultiplied(color, 1.0f);
    for (int y = top; y < bottom; ++y) {
        std::uint32_t* row = surface.pixels + static_cast<size_t>(y) * surface.width;
        for (int x = left; x < right; ++x) row[x] = pixel;
    }
}

// Persegi membulat dengan tepi terhalus. `alpha` < 1 dipakai untuk lapisan
// lembut (mis. halo titik status) tanpa perlu mesin blend terpisah.
void fillRoundedOpaque(Surface& surface, const Rect& rect, int radius, COLORREF color, float alpha = 1.0f) {
    if (!surface.valid()) return;
    const float r = static_cast<float>(std::max(radius, 0));
    const int left = std::max(0, rect.x - 2);
    const int top = std::max(0, rect.y - 2);
    const int right = std::min(surface.width, rect.right() + 2);
    const int bottom = std::min(surface.height, rect.bottom() + 2);
    const std::uint32_t pixel = packPremultiplied(color, alpha);
    for (int y = top; y < bottom; ++y) {
        std::uint32_t* row = surface.pixels + static_cast<size_t>(y) * surface.width;
        for (int x = left; x < right; ++x) {
            const float coverage = xydesk::panel::roundedRectCoverage(
                static_cast<float>(x) + 0.5f, static_cast<float>(y) + 0.5f, rect, r);
            if (coverage <= 0.0f) continue;
            if (coverage >= 0.996f && alpha >= 1.0f) {
                row[x] = pixel;
            } else {
                const std::uint32_t existing = row[x];
                if (alpha >= 1.0f) {
                    // Tepi: campur warna bentuk dengan apa pun yang sudah ada.
                    const float keep = 1.0f - coverage;
                    const auto blend = [coverage, keep](std::uint32_t oldChannel, std::uint32_t newChannel) {
                        return static_cast<std::uint32_t>(oldChannel * keep + newChannel * coverage + 0.5f);
                    };
                    row[x] = blend(existing & 0xFF000000u, pixel & 0xFF000000u)
                        | (blend((existing >> 16) & 0xFF, (pixel >> 16) & 0xFF) << 16)
                        | (blend((existing >> 8) & 0xFF, (pixel >> 8) & 0xFF) << 8)
                        | blend(existing & 0xFF, pixel & 0xFF);
                } else {
                    const float t = coverage * alpha;
                    const auto blend = [t](std::uint32_t oldChannel, std::uint32_t newChannel) {
                        return static_cast<std::uint32_t>(oldChannel * (1.0f - t) + newChannel + 0.5f);
                    };
                    row[x] = blend(existing & 0xFF000000u, pixel & 0xFF000000u)
                        | (blend((existing >> 16) & 0xFF, (pixel >> 16) & 0xFF) << 16)
                        | (blend((existing >> 8) & 0xFF, (pixel >> 8) & 0xFF) << 8)
                        | blend(existing & 0xFF, pixel & 0xFF);
                }
            }
        }
    }
}

void fillCircleOpaque(Surface& surface, const Rect& rect, COLORREF color, float alpha = 1.0f) {
    fillRoundedOpaque(surface, rect, rect.w, color, alpha);
}

void strokeRounded(Surface& surface, const Rect& rect, int radius, COLORREF color, int lineWidth) {
    if (!surface.valid()) return;
    const float r = static_cast<float>(std::max(radius, 0));
    const float half = static_cast<float>(lineWidth) * 0.5f;
    const int left = std::max(0, rect.x - lineWidth - 2);
    const int top = std::max(0, rect.y - lineWidth - 2);
    const int right = std::min(surface.width, rect.right() + lineWidth + 2);
    const int bottom = std::min(surface.height, rect.bottom() + lineWidth + 2);
    const std::uint32_t pixel = packPremultiplied(color, 1.0f);
    for (int y = top; y < bottom; ++y) {
        std::uint32_t* row = surface.pixels + static_cast<size_t>(y) * surface.width;
        for (int x = left; x < right; ++x) {
            const float distance = xydesk::panel::roundedRectDistance(
                static_cast<float>(x) + 0.5f, static_cast<float>(y) + 0.5f, rect, r);
            const float coverage = std::clamp(half + 0.5f - std::fabs(distance), 0.0f, 1.0f);
            if (coverage <= 0.0f) continue;
            const std::uint32_t existing = row[x];
            const auto blend = [coverage](std::uint32_t oldChannel, std::uint32_t newChannel) {
                return static_cast<std::uint32_t>(oldChannel * (1.0f - coverage) + newChannel * coverage + 0.5f);
            };
            row[x] = blend(existing & 0xFF000000u, pixel & 0xFF000000u)
                | (blend((existing >> 16) & 0xFF, (pixel >> 16) & 0xFF) << 16)
                | (blend((existing >> 8) & 0xFF, (pixel >> 8) & 0xFF) << 8)
                | blend(existing & 0xFF, pixel & 0xFF);
        }
    }
}

// Sudut jendela + bayangan: satu lintasan terakhir yang memberi alpha pada
// setiap piksel, dijalankan SETELAH semua isi digambar.
//
// Di luar bentuk panel isinya bayangan hitam; di dalamnya, isi panel apa
// adanya. Piksel yang hanya sebagian tertutup bentuk (tepi busur) menerima
// keduanya: alpha = cakupan + bayangan*(1-cakupan), sedangkan warnanya
// menyumbang cakupan saja — bayangan tidak menambah warna, ia hanya
// kegelapan. Inilah yang membuat tepi busur terlihat rata, bukan bergelombang
// atau menggelap seperti potongan kotak.
void applyWindowShape(Surface& surface, const PanelLayout& layout) {
    if (!surface.valid()) return;
    const float radius = static_cast<float>(layout.radiusPanel);
    const float spread = static_cast<float>(xydesk::panel::scaled(xydesk::panel::kShadowSpread, layout.scalePct));
    const float strength = static_cast<float>(xydesk::panel::kShadowStrength) / 255.0f;

    for (int y = 0; y < surface.height; ++y) {
        std::uint32_t* row = surface.pixels + static_cast<size_t>(y) * surface.width;
        for (int x = 0; x < surface.width; ++x) {
            const float px = static_cast<float>(x) + 0.5f;
            const float py = static_cast<float>(y) + 0.5f;
            const float coverage = xydesk::panel::roundedRectCoverage(px, py, layout.panel, radius);
            if (coverage >= 0.996f) {
                row[x] = (0xFFu << 24) | (row[x] & 0x00FFFFFFu);
                continue;
            }
            // Tepi dalam dicerahkan tipis: transisi tepi-ke-isi terlihat halus
            // dan rapi, bukan memotong tajam ke latar.
            if (coverage >= 0.6f) {
                const std::uint32_t edge = row[x];
                const auto pre = [coverage](std::uint32_t ch) {
                    const std::uint32_t lifted = ch + 12 > 255 ? 255 : ch + 12;
                    return static_cast<std::uint32_t>(static_cast<float>(lifted) * coverage + 0.5f);
                };
                row[x] = (static_cast<std::uint32_t>(coverage * 255.0f + 0.5f) << 24)
                    | (pre((edge >> 16) & 0xFF) << 16)
                    | (pre((edge >> 8) & 0xFF) << 8)
                    | pre(edge & 0xFF);
                continue;
            }
            const float shadow = xydesk::panel::roundedRectShadow(px, py, layout.panel, radius, spread, strength);
            if (coverage <= 0.0f) {
                row[x] = shadow <= 0.003f ? 0
                                          : (static_cast<std::uint32_t>(shadow * 255.0f + 0.5f) << 24);
                continue;
            }
            const std::uint32_t pixel = row[x];
            const float alpha = coverage + shadow * (1.0f - coverage);
            const auto premultiply = [coverage](std::uint32_t channel) {
                return static_cast<std::uint32_t>(static_cast<float>(channel) * coverage + 0.5f);
            };
            row[x] = (static_cast<std::uint32_t>(alpha * 255.0f + 0.5f) << 24)
                | (premultiply((pixel >> 16) & 0xFF) << 16)
                | (premultiply((pixel >> 8) & 0xFF) << 8)
                | premultiply(pixel & 0xFF);
        }
    }
}

// ── Teks ---------------------------------------------------------------

void drawTextLine(HDC dc, const std::wstring& text, const Rect& rect, HFONT font, COLORREF color, UINT flags) {
    if (!dc || !font || text.empty() || !rect.valid()) return;
    RECT box{rect.x, rect.y, rect.right(), rect.bottom()};
    const HGDIOBJ previous = SelectObject(dc, font);
    SetBkMode(dc, TRANSPARENT);
    SetTextColor(dc, color);
    DrawTextW(dc, text.c_str(), -1, &box, flags);
    SelectObject(dc, previous);
}

void drawTextCentered(HDC dc, const std::wstring& text, const Rect& rect, HFONT font, COLORREF color) {
    drawTextLine(dc, text, rect, font, color, DT_CENTER | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);
}

// ── Gaya kontrol -------------------------------------------------------

struct ButtonPalette {
    COLORREF fill;
    COLORREF label;
    int radius;
    bool outlined;
};

ButtonPalette buttonPalette(Target target, bool enabled, bool hot, bool pressed, const PanelLayout& layout) {
    const int radius = layout.radiusControl;
    if (!enabled) return ButtonPalette{kSurface, kDisabled, radius, false};
    if (target == Target::Start || target == Target::RunHost) {
        if (pressed) return ButtonPalette{kAccentPressed, kOnAccent, radius, false};
        if (hot) return ButtonPalette{kAccentHover, kOnAccent, radius, false};
        return ButtonPalette{kAccent, kOnAccent, radius, false};
    }
    if (target == Target::StopSession) {
        if (pressed) return ButtonPalette{mixColor(kSurfacePressed, kBad, 0.22f), kBad, radius, true};
        if (hot) return ButtonPalette{mixColor(kSurface3, kBad, 0.16f), kBad, radius, true};
        return ButtonPalette{mixColor(kSurface2, kBad, 0.10f), kBad, radius, true};
    }
    if (pressed) return ButtonPalette{kSurfacePressed, kText, radius, false};
    if (hot) return ButtonPalette{kSurface3, kText, radius, true};
    return ButtonPalette{kSurface2, kText, radius, false};
}

bool workspaceProbe=false;
xydesk::session_view::Snapshot sessionView;
std::future<xydesk::session_view::Snapshot> sessionViewPending;
std::future<std::string> sessionActionPending;
std::future<std::string> fileSendPending;
std::wstring fileSendName;
/// Jawaban "Terima"/"Tolak" yang sedang dikirim ke host.
std::future<std::string> fileConsentPending;
bool fileConsentAllow=false;
/// Perubahan kebijakan / pelupaan perangkat yang sedang dikirim ke host.
std::future<std::string> filePolicyPending;
std::wstring filePolicyNote;
unsigned sessionViewPid=0;
ULONGLONG sessionViewNext=0;
void pollSessionAction(){
    if(!sessionActionPending.valid()||sessionActionPending.wait_for(std::chrono::milliseconds(0))!=std::future_status::ready)return;
    std::string response;
    try{response=sessionActionPending.get();}catch(...){response.clear();}
    if(response.empty()){
        setFlash(L"Gagal memutus sesi lewat kanal privat host.",kBad);
    }else if(jsonFlag(response,"stopped")){
        sessionView={};sessionViewNext=0;
        setFlash(L"Sesi aktif diputus dari host.",kGood);
    }else{
        sessionView={};sessionViewNext=0;
        setFlash(L"Tidak ada sesi aktif yang perlu diputus.",kWarn);
    }
}

// Jawaban `file-send`. Host hanya menjawab apakah kirimannya **diterima
// antrean**, bukan apakah berkasnya sampai: perjalanannya bisa menit-menit,
// dan panel yang menunggu sampai selesai akan terlihat menggantung.
void pollFileSend(){
    if(!fileSendPending.valid()||fileSendPending.wait_for(std::chrono::milliseconds(0))!=std::future_status::ready)return;
    std::string response;
    try{response=fileSendPending.get();}catch(...){response.clear();}
    if(response.empty()){
        setFlash(L"Gagal menghubungi host untuk mengirim berkas.",kBad);
        return;
    }
    if(jsonFlag(response,"ok")){
        setFlash(fileSendName.empty()?L"Berkas dikirim ke perangkat.":(L"Mengirim " + fileSendName + L" ke perangkat…"),kAccent);
        return;
    }
    // Alasan dari host ditampilkan apa adanya: ia sudah ditulis sebagai
    // kalimat untuk manusia (\"berkas kosong\", \"tidak ada perangkat yang
    // tersambung\"), dan menerjemahkannya ulang di sini hanya membuat dua
    // sumber kebenaran.
    const auto reason=jsonString(response,"error");
    setFlash(reason.empty()?L"Host menolak kiriman berkas.":(L"Kiriman ditolak: "+reason),kWarn);
}

// Jawaban atas tombol Terima/Tolak berkas masuk.
void pollFileConsent(){
    if(!fileConsentPending.valid()||fileConsentPending.wait_for(std::chrono::milliseconds(0))!=std::future_status::ready)return;
    std::string response;
    try{response=fileConsentPending.get();}catch(...){response.clear();}
    if(response.empty()){setFlash(L"Gagal menghubungi host untuk menjawab tawaran berkas.",kBad);return;}
    if(jsonFlag(response,"ok")){
        setFlash(fileConsentAllow?L"Berkas masuk disetujui.":L"Berkas masuk ditolak.",fileConsentAllow?kAccent:kMuted);
        return;
    }
    const auto reason=jsonString(response,"error");
    setFlash(reason.empty()?L"Jawaban tidak diterima host.":reason,kWarn);
}

// Jawaban atas tombol kebijakan dan "lupakan perangkat tepercaya".
void pollFilePolicy(){
    if(!filePolicyPending.valid()||filePolicyPending.wait_for(std::chrono::milliseconds(0))!=std::future_status::ready)return;
    std::string response;
    try{response=filePolicyPending.get();}catch(...){response.clear();}
    if(response.empty()){setFlash(L"Gagal menghubungi host.",kBad);return;}
    if(jsonFlag(response,"ok")){setFlash(filePolicyNote,kAccent);return;}
    const auto reason=jsonString(response,"error");
    setFlash(reason.empty()?L"Host menolak perubahan itu.":reason,kWarn);
}

void pollSessionView(){
    if(sessionViewPending.valid()&&sessionViewPending.wait_for(std::chrono::milliseconds(0))==std::future_status::ready){
        try{auto value=sessionViewPending.get();if(g.process&&GetProcessId(g.process)==sessionViewPid)sessionView=std::move(value);else sessionView={};}catch(...){sessionView={};}
        renderPanel();
    }
    if(!g.process){sessionView={};return;}
    if((g.page!=Page::Status&&g.page!=Page::Connections)||!IsWindowVisible(g.window)||sessionViewPending.valid()||GetTickCount64()<sessionViewNext||!controlChannel.endpoint)return;
    const auto endpoint=*controlChannel.endpoint;sessionViewPid=endpoint.pid;sessionViewNext=GetTickCount64()+3000;
    try{sessionViewPending=std::async(std::launch::async,[endpoint]{return xydesk::session_view::read(endpoint);});}catch(...){sessionView={};}
}

bool targetEnabled(Target target) {
    switch (target) {
    case Target::TogglePairingVisibility:
        return !g.pairingCode.empty();
    case Target::CustomizePassword:
        return true;
    case Target::CopyLink:
    case Target::ConnectionQr:
    case Target::DeviceLink: return !xydesk::panel_control::deviceLink(g.deviceId).empty();
    case Target::Start:
        return !g.running;
    case Target::Stop:
        return g.running;
    case Target::StopSession:
        return g.running && controlChannel.endpoint.has_value() && sessionView.known && sessionView.active && !sessionActionPending.valid();
    case Target::SendFile:
        // Tanpa perangkat tersambung tidak ada tujuan kiriman; tombol yang
        // bisa ditekan tetapi selalu gagal lebih buruk daripada tombol mati.
        return g.running && controlChannel.endpoint.has_value() && sessionView.known && sessionView.active && !fileSendPending.valid();
    case Target::AcceptFile:
    case Target::TrustFile:
    case Target::RejectFile:
        // Hanya hidup selama ada tawaran yang benar-benar menunggu.
        return controlChannel.endpoint.has_value() && sessionView.pendingId != 0 && !fileConsentPending.valid();
    case Target::FilePolicy:
        return controlChannel.endpoint.has_value() && !sessionView.filePolicy.empty() && !filePolicyPending.valid();
    case Target::ForgetTrusted:
        // Tombol yang tidak punya apa pun untuk dilupakan adalah tombol mati.
        return controlChannel.endpoint.has_value() && sessionView.trustedDevices > 0 && !filePolicyPending.valid();
    case Target::UnattendedPolicy:
        // Host lama tidak melaporkan kebijakan kehadiran sama sekali; tombol
        // yang mengubah sesuatu yang tidak ada hanya akan menipu.
        return controlChannel.endpoint.has_value() && !sessionView.unattendedPolicy.empty() && !filePolicyPending.valid();
    case Target::UnattendedGrant:
        // Izin sementara hanya berarti pada kebijakan "hanya saat saya ada":
        // pada "kapan saja" ia tidak menambah apa pun, dan pada "tolak semua"
        // ia memang tidak boleh menambah apa pun.
        return controlChannel.endpoint.has_value() && sessionView.unattendedPolicy == L"watched" && !filePolicyPending.valid();
    case Target::CopyId:
        return !g.deviceId.empty();
    case Target::CopyPassword:
        return !g.pairingCode.empty();
    default:
        return true;
    }
}

/// "Berkas masuk: Tanya" — kata kerja yang dipakai, bukan nilai mentahnya.
std::wstring filePolicyLabel() {
    const auto& value=sessionView.filePolicy;
    if(value==L"always")return L"Berkas masuk: Terima otomatis";
    if(value==L"never")return L"Berkas masuk: Tolak semua";
    return L"Berkas masuk: Tanya dulu";
}

std::wstring forgetTrustedLabel() {
    return L"Lupakan "+std::to_wstring(sessionView.trustedDevices)+L" perangkat tepercaya";
}

/// Kebijakan kehadiran dalam kalimat pemilik PC, bukan nama varian.
std::wstring unattendedPolicyLabel() {
    const auto& value=sessionView.unattendedPolicy;
    if(value==L"watched")return L"Akses: hanya saat saya ada";
    if(value==L"off")return L"Akses: tolak semua koneksi";
    return L"Akses: kapan saja";
}

/// Sisa izin dibulatkan ke atas, seperti di sisi host: angka nol di sebelah
/// izin yang masih berlaku akan dibaca sebagai sudah habis.
std::wstring unattendedGrantLabel() {
    const auto ms=sessionView.unattendedGrantMs;
    if(ms==0)return L"Izinkan 8 jam tanpa saya";
    const unsigned long long minutes=(ms+59999)/60000;
    if(minutes<60)return L"Cabut izin ("+std::to_wstring(minutes)+L" menit)";
    const unsigned long long hours=(minutes+59)/60;
    return L"Cabut izin ("+std::to_wstring(hours)+L" jam)";
}

std::wstring targetLabel(Target target) {
    switch (target) {
    case Target::Start: return L"Mulai host";
    case Target::Stop: return L"Hentikan";
    case Target::Restart: return L"Restart";
    case Target::Web: return L"Buka XyDesk Web";
    case Target::PageClient: return L"Buka mode klien";
    case Target::ClientConnect: return L"Sambung ke perangkat lain";
    case Target::ClientLast: return L"Sambung ke kode terakhir";
    case Target::OpenLog: return L"Buka log host";
    case Target::RunHost: return L"Ambil alih sesi ini";
    case Target::StopSession: return L"Putus sesi";
    case Target::SendFile: return L"Kirim berkas…";
    case Target::AcceptFile: return L"Terima";
    case Target::TrustFile: return L"Terima & ingat perangkat";
    case Target::RejectFile: return L"Tolak";
    case Target::FilePolicy: return filePolicyLabel();
    case Target::ForgetTrusted: return forgetTrustedLabel();
    case Target::UnattendedPolicy: return unattendedPolicyLabel();
    case Target::UnattendedGrant: return unattendedGrantLabel();
    case Target::CopyId:
    case Target::CopyPassword: return L"Salin";
    case Target::TogglePairingVisibility: return L"";
    case Target::CustomizePassword: return L"Customize password";
    case Target::PagePairing:return L"Buka akses host";
    case Target::PageConnections:return L"Lihat koneksi";
    case Target::CopyLink: return L"Salin link";
    case Target::ConnectionQr: return L"QR koneksi";
    default: return L"";
    }
}

// ── Menggambar panel ---------------------------------------------------

void paintCard(Surface& surface, const Rect& card, int radius) {
    Rect shadow=card;shadow.y+=3;
    fillRoundedOpaque(surface,shadow,radius,kAccent,0.045f);
    shadow.y-=1;
    fillRoundedOpaque(surface,shadow,radius,kAccent,0.025f);
    fillRoundedOpaque(surface,card,radius,kSurface);
}

void paintStatusCard(Surface& surface, const PanelLayout& layout, HDC dc) {
    const Rect& card = layout.statusCard;
    paintCard(surface,card,layout.radiusCard);

    const COLORREF dotColor = g.statusColor;
    fillCircleOpaque(surface, Rect{layout.statusDot.x - 4, layout.statusDot.y - 4, layout.statusDot.w + 8, layout.statusDot.h + 8},
        mixColor(kSurface2, dotColor, 0.22f));
    fillCircleOpaque(surface, layout.statusDot, dotColor);

    const std::wstring line1 = g.flashText.empty() ? g.statusText : g.flashText;
    drawTextLine(dc, line1, layout.statusLine1, g.fontSemi, kText,
        DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);

    const std::wstring logLine = g.logPath.empty() ? std::wstring(L"Log host belum dibuat") : (L"Log: " + g.logPath);
    drawTextLine(dc, logLine, layout.statusLine2, g.fontSmall, kMuted,
        DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_PATH_ELLIPSIS);
}

void paintIdentityCard(Surface& surface, const PanelLayout& layout, HDC dc, const Rect& card, const Rect& label,
    const Rect& value, const Rect& copy, const wchar_t* labelText, const std::wstring& valueText, Target copyTarget) {
    paintCard(surface,card,layout.radiusCard);
    drawTextLine(dc, labelText, label, g.fontCaps, kMuted, DT_LEFT | DT_VCENTER | DT_SINGLELINE);

    const bool hasValue = !valueText.empty();
    drawTextLine(dc, hasValue ? valueText : std::wstring(L"Belum tersedia"), value, g.fontValue,
        hasValue ? kText : kDisabled, DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);

    const bool enabled = targetEnabled(copyTarget);
    const bool hot = g.hot == copyTarget && enabled;
    const bool pressed = g.pressed == copyTarget && enabled;
    const ButtonPalette palette = buttonPalette(copyTarget, enabled, hot, pressed, layout);
    const COLORREF fill = enabled ? (pressed ? kAccentPressed : (hot ? kAccentHover : mixColor(kSurface2, kAccent, 0.35f)))
                                  : kSurface2;
    fillRoundedOpaque(surface, copy, palette.radius, fill);
    if (enabled && g.focused == copyTarget) {
        strokeRounded(surface, copy, palette.radius, kAccent, xydesk::panel::scaled(2, layout.scalePct));
    }
    drawTextCentered(dc, L"Salin", copy, g.fontSmall, enabled ? kText : kDisabled);
}


void paintButton(Surface& surface, const PanelLayout& layout, HDC dc, Target target, const Rect& rect);

// ── Ikon mata (semat/sembunyikan nilai sensitif) ────────────────────────────
// Digambar dari primitif GDI supaya tidak bergantung pada glif font ikon yang
// berbeda antar versi Windows.
void drawEyeGlyph(HDC dc, const Rect& box, bool open, COLORREF color) {
    if (box.w < 8 || box.h < 8) return;
    const int cx = box.x + box.w / 2, cy = box.y + box.h / 2;
    HPEN pen = CreatePen(PS_SOLID, xydesk::panel::scaled(2, g.layout.scalePct), color);
    HGDIOBJ oldPen = SelectObject(dc, pen);
    HGDIOBJ oldBrush = SelectObject(dc, GetStockObject(NULL_BRUSH));
    const int rx = box.w * 34 / 100;
    const int ry = open ? box.h * 24 / 100 : box.h * 12 / 100;
    Ellipse(dc, cx - rx, cy - ry, cx + rx + 1, cy + ry + 1);
    if (open) {
        HBRUSH pupil = CreateSolidBrush(color);
        HGDIOBJ prev = SelectObject(dc, pupil);
        const int r = (std::max)(2, box.h * 12 / 100);
        Ellipse(dc, cx - r, cy - r, cx + r + 1, cy + r + 1);
        SelectObject(dc, prev);
        DeleteObject(pupil);
    } else {
        const int lash = box.h * 26 / 100;
        MoveToEx(dc, cx - rx / 2, cy + lash, nullptr); LineTo(dc, cx - rx / 2 - 2, cy + lash + 4);
        MoveToEx(dc, cx, cy + lash + 1, nullptr); LineTo(dc, cx, cy + lash + 5);
        MoveToEx(dc, cx + rx / 2, cy + lash, nullptr); LineTo(dc, cx + rx / 2 + 2, cy + lash + 4);
    }
    SelectObject(dc, oldPen);
    SelectObject(dc, oldBrush);
    DeleteObject(pen);
}

// Kartu "Kode pairing": nilai disembunyikan bila tidak disematkan, dengan aksi
// mata + Customize password tepat di bawahnya — semua langsung di Akses host,
// bukan di Pengaturan (permintaan pemilik 2026-11-03).
void paintPairingPasswordCard(Surface& surface, const PanelLayout& layout, HDC dc) {
    const Rect& card = layout.passwordCard;
    paintCard(surface, card, layout.radiusCard);
    drawTextLine(dc, L"Kode pairing", layout.passwordLabel, g.fontCaps, kMuted,
        DT_LEFT | DT_VCENTER | DT_SINGLELINE);

    const bool hasValue = !g.pairingCode.empty();
    const std::wstring shown = !hasValue ? L"Belum tersedia"
        : (g.showPairingCode ? g.pairingCode : std::wstring(g.pairingCode.size(), L'\u2022'));
    drawTextLine(dc, shown, layout.passwordValue, g.fontValue,
        hasValue ? kText : kDisabled, DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);

    {
        const Target copyTarget = Target::CopyPassword;
        const bool enabled = targetEnabled(copyTarget);
        const bool hot = g.hot == copyTarget && enabled;
        const bool pressed = g.pressed == copyTarget && enabled;
        const ButtonPalette palette = buttonPalette(copyTarget, enabled, hot, pressed, layout);
        const COLORREF fill = enabled ? (pressed ? kAccentPressed : (hot ? kAccentHover : mixColor(kSurface2, kAccent, 0.35f)))
                                      : kSurface2;
        fillRoundedOpaque(surface, layout.passwordCopy, palette.radius, fill);
        if (enabled && g.focused == copyTarget) {
            strokeRounded(surface, layout.passwordCopy, palette.radius, kAccent, xydesk::panel::scaled(2, layout.scalePct));
        }
        drawTextCentered(dc, L"Salin", layout.passwordCopy, g.fontSmall, enabled ? kText : kDisabled);
    }

    {
        const Target eye = Target::TogglePairingVisibility;
        const bool enabled = targetEnabled(eye);
        const bool hot = g.hot == eye && enabled;
        const bool pressed = g.pressed == eye && enabled;
        fillRoundedOpaque(surface, layout.passwordEye, layout.radiusControl,
            !enabled ? kSurface2 : (pressed ? kAccentPressed : (hot ? mixColor(kSurface2, kAccent, 0.25f) : kSurface2)));
        if (enabled && g.focused == eye) {
            strokeRounded(surface, layout.passwordEye, layout.radiusControl, kAccent, xydesk::panel::scaled(2, layout.scalePct));
        }
        const Rect glyph{layout.passwordEye.x + layout.passwordEye.w / 2 - xydesk::panel::scaled(11, layout.scalePct),
            layout.passwordEye.y + layout.passwordEye.h / 2 - xydesk::panel::scaled(9, layout.scalePct),
            xydesk::panel::scaled(22, layout.scalePct), xydesk::panel::scaled(18, layout.scalePct)};
        drawEyeGlyph(dc, glyph, g.showPairingCode, enabled ? kText : kDisabled);
    }

    paintButton(surface, layout, dc, Target::CustomizePassword, layout.passwordCustomize);
}

// ── Modal custom: pengganti MessageBox/Dialog bawaan Windows ────────────────
// Semua prompt panel lewat sini supaya tampilannya konsisten (kartu membulat,
// tombol pil, isian custom) dan tidak ada lagi jendela gaya Windows klasik.
struct PanelModalRequest {
    std::wstring title;
    std::wstring text;
    std::vector<std::pair<std::wstring, int>> buttons; // label -> kode hasil
    bool input = false;
    std::wstring inputLabel;
    std::wstring inputInitial;
    bool inputMasked = true;
    std::wstring status;
    // Dipanggil saat tombol aksi (kode > 0) ditekan, sinkron ke engine lokal.
    // true = selesai dan modal ditutup; false = tetap terbuka + status galat.
    std::function<bool(int, const std::wstring&, std::wstring&)> onAction;
};

struct PanelModalState {
    PanelModalRequest request;
    std::wstring input;
    bool inputVisible = false;
    bool caretOn = true;
    int hoverButton = -1;
    int pressedButton = -1;
    int result = 0;
    bool busy = false;
    HWND owner = nullptr;
    HWND hwnd = nullptr;
    Rect inputRect{}, inputEyeRect{}, closeRect{};
    std::vector<Rect> buttonRects;
};

static PanelModalState* g_modal = nullptr;

int modalHitButton(PanelModalState* state, int x, int y) {
    for (size_t i = 0; i < state->buttonRects.size(); ++i) {
        const Rect& r = state->buttonRects[i];
        if (x >= r.x && x < r.right() && y >= r.y && y < r.bottom()) return static_cast<int>(i);
    }
    return -1;
}

void fillRoundedHdc(HDC dc, const Rect& r, int radius, COLORREF color) {
    HBRUSH brush = CreateSolidBrush(color);
    HPEN pen = CreatePen(PS_SOLID, 1, color);
    HGDIOBJ oldBrush = SelectObject(dc, brush);
    HGDIOBJ oldPen = SelectObject(dc, pen);
    RoundRect(dc, r.x, r.y, r.right(), r.bottom(), radius * 2, radius * 2);
    SelectObject(dc, oldBrush);
    SelectObject(dc, oldPen);
    DeleteObject(brush);
    DeleteObject(pen);
}

void strokeRoundedHdc(HDC dc, const Rect& r, int radius, COLORREF color, int width) {
    HGDIOBJ oldBrush = SelectObject(dc, GetStockObject(NULL_BRUSH));
    HPEN pen = CreatePen(PS_SOLID, width, color);
    HGDIOBJ oldPen = SelectObject(dc, pen);
    RoundRect(dc, r.x, r.y, r.right(), r.bottom(), radius * 2, radius * 2);
    SelectObject(dc, oldPen);
    SelectObject(dc, oldBrush);
    DeleteObject(pen);
}

void paintPanelModal(HWND hwnd, PanelModalState* state) {
    PAINTSTRUCT ps{};
    HDC dc = BeginPaint(hwnd, &ps);
    RECT rc{}; GetClientRect(hwnd, &rc);
    const int w = rc.right, h = rc.bottom;
    HDC mem = CreateCompatibleDC(dc);
    HBITMAP bmp = CreateCompatibleBitmap(dc, w, h);
    HGDIOBJ oldBmp = SelectObject(mem, bmp);
    RECT full{0, 0, w, h};
    FillRect(mem, &full, static_cast<HBRUSH>(GetStockObject(WHITE_BRUSH)));
    const auto px = [&](int n) { return xydesk::panel::scaled(n, g.layout.scalePct); };

    drawTextLine(mem, state->request.title, Rect{px(24), px(18), w - px(96), px(28)}, g.fontTitle, kText,
        DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);
    // Tombol tutup (silang) di kanan-atas.
    {
        const Rect c = state->closeRect;
        HPEN pen = CreatePen(PS_SOLID, px(2), state->hoverButton == -2 ? kText : kMuted);
        HGDIOBJ oldPen = SelectObject(mem, pen);
        MoveToEx(mem, c.x + px(6), c.y + px(6), nullptr); LineTo(mem, c.right() - px(6), c.bottom() - px(6));
        MoveToEx(mem, c.right() - px(6), c.y + px(6), nullptr); LineTo(mem, c.x + px(6), c.bottom() - px(6));
        SelectObject(mem, oldPen); DeleteObject(pen);
    }

    drawTextLine(mem, state->request.text, Rect{px(24), px(56), w - px(24), h - px(120)}, g.fontBody, kText,
        DT_LEFT | DT_TOP | DT_WORDBREAK | DT_END_ELLIPSIS);

    int bottomCursor = h - px(24) - px(40);
    // Baris tombol dihitung dulu supaya isian/status tidak menabrak.
    state->buttonRects.clear();
    {
        int totalW = 0;
        for (const auto& b : state->request.buttons) totalW += px(120) + px(10);
        int x = w - px(24) - totalW + px(10);
        for (size_t i = 0; i < state->request.buttons.size(); ++i) {
            const bool primary = i == 0;
            Rect r{x, bottomCursor, px(120), px(40)};
            state->buttonRects.push_back(r);
            x += px(120) + px(10);
            const bool hot = state->hoverButton == static_cast<int>(i) && !state->busy;
            const bool pressed = state->pressedButton == static_cast<int>(i) && !state->busy;
            const COLORREF fill = state->busy ? kSurface2
                : primary ? (pressed ? kAccentPressed : (hot ? kAccentHover : kAccent))
                          : (pressed ? mixColor(kSurface2, kText, 0.18f) : (hot ? mixColor(kSurface2, kText, 0.10f) : kSurface2));
            fillRoundedHdc(mem, r, px(20), fill);
            drawTextCentered(mem, state->request.buttons[i].first, r, g.fontSemi,
                state->busy ? kDisabled : (primary ? kOnAccent : kText));
        }
    }

    int contentBottom = bottomCursor - px(12);
    if (!state->request.status.empty()) {
        Rect s{px(24), contentBottom - px(22), w - px(48), px(22)};
        drawTextLine(mem, state->request.status, s, g.fontSmall, kMuted,
            DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);
        contentBottom -= px(28);
    }

    if (state->request.input) {
        const Rect field{px(24), contentBottom - px(46), w - px(48), px(46)};
        state->inputRect = field;
        state->inputEyeRect = Rect{field.right() - px(42), field.y + px(5), px(36), px(36)};
        drawTextLine(mem, state->request.inputLabel, Rect{px(24), field.y - px(24), w - px(48), px(18)},
            g.fontCaps, kMuted, DT_LEFT | DT_VCENTER | DT_SINGLELINE);
        fillRoundedHdc(mem, field, px(10), kSurface2);
        strokeRoundedHdc(mem, field, px(10), kAccent, px(2));
        const std::wstring shown = (!state->request.inputMasked || state->inputVisible)
            ? state->input
            : std::wstring(state->input.size(), L'\u2022');
        drawTextLine(mem, shown, Rect{field.x + px(14), field.y + px(2), field.w - px(64), field.h - px(4)},
            g.fontSemi, kText, DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);
        if (state->caretOn && !state->busy) {
            SIZE size{};
            HGDIOBJ oldFont = SelectObject(mem, g.fontSemi);
            GetTextExtentPoint32W(mem, shown.c_str(), static_cast<int>(shown.size()), &size);
            SelectObject(mem, oldFont);
            const int cx = (std::min)(static_cast<int>(field.x + px(14) + size.cx + px(2)), field.right() - px(52));
            HPEN pen = CreatePen(PS_SOLID, px(2), kAccent);
            HGDIOBJ oldPen = SelectObject(mem, pen);
            MoveToEx(mem, cx, field.y + px(12), nullptr); LineTo(mem, cx, field.bottom() - px(12));
            SelectObject(mem, oldPen); DeleteObject(pen);
        }
        drawEyeGlyph(mem, Rect{state->inputEyeRect.x + px(7), state->inputEyeRect.y + px(9), px(22), px(18)},
            state->inputVisible, kMuted);
        contentBottom = field.y - px(28);
    }

    BitBlt(dc, 0, 0, w, h, mem, 0, 0, SRCCOPY);
    SelectObject(mem, oldBmp); DeleteObject(bmp); DeleteDC(mem);
    EndPaint(hwnd, &ps);
}

void modalActivateButton(HWND hwnd, PanelModalState* state, int hit) {
    if (!state || state->busy || hit < 0
        || static_cast<size_t>(hit) >= state->request.buttons.size()) return;
    state->pressedButton = -1;
    const int code = state->request.buttons[static_cast<size_t>(hit)].second;
    if (code > 0 && state->request.onAction) {
        state->busy = true;
        state->request.status = L"Menerapkan melalui kanal privat...";
        InvalidateRect(hwnd, nullptr, FALSE);
        UpdateWindow(hwnd);
        std::wstring status;
        const bool ok = state->request.onAction(code, state->input, status);
        state->busy = false;
        if (ok) {
            state->result = code;
            DestroyWindow(hwnd);
        } else {
            state->request.status = status;
            InvalidateRect(hwnd, nullptr, FALSE);
        }
    } else {
        state->result = code;
        DestroyWindow(hwnd);
    }
}

LRESULT CALLBACK panelModalProc(HWND hwnd, UINT message, WPARAM wParam, LPARAM lParam) {
    PanelModalState* state = g_modal;
    switch (message) {
    case WM_PAINT:
        if (state) paintPanelModal(hwnd, state);
        return 0;
    case WM_ERASEBKGND:
        return 1;
    case WM_TIMER:
        if (state) { state->caretOn = !state->caretOn; InvalidateRect(hwnd, nullptr, FALSE); }
        return 0;
    case WM_MOUSEMOVE:
        if (state) {
            const int x = static_cast<short>(LOWORD(lParam)), y = static_cast<short>(HIWORD(lParam));
            const int hit = modalHitButton(state, x, y);
            int hover = hit;
            if (hover < 0 && x >= state->closeRect.x && x < state->closeRect.right()
                && y >= state->closeRect.y && y < state->closeRect.bottom()) hover = -2;
            if (hover != state->hoverButton) { state->hoverButton = hover; InvalidateRect(hwnd, nullptr, FALSE); }
            SetCursor(LoadCursorW(nullptr, hover >= 0 || hover == -2 ? IDC_HAND : IDC_IBEAM));
        }
        return 0;
    case WM_LBUTTONDOWN: {
        if (!state) return 0;
        const int x = static_cast<short>(LOWORD(lParam)), y = static_cast<short>(HIWORD(lParam));
        if (state->busy) return 0;
        const int hit = modalHitButton(state, x, y);
        if (hit >= 0) { state->pressedButton = hit; InvalidateRect(hwnd, nullptr, FALSE); return 0; }
        if (x >= state->closeRect.x && x < state->closeRect.right()
            && y >= state->closeRect.y && y < state->closeRect.bottom()) {
            state->result = 0; DestroyWindow(hwnd); return 0;
        }
        if (state->request.input && x >= state->inputEyeRect.x && x < state->inputEyeRect.right()
            && y >= state->inputEyeRect.y && y < state->inputEyeRect.bottom()) {
            state->inputVisible = !state->inputVisible; InvalidateRect(hwnd, nullptr, FALSE); return 0;
        }
        return 0;
    }
    case WM_LBUTTONUP: {
        if (!state || state->busy) return 0;
        const int x = static_cast<short>(LOWORD(lParam)), y = static_cast<short>(HIWORD(lParam));
        const int hit = modalHitButton(state, x, y);
        if (hit >= 0 && hit == state->pressedButton) {
            modalActivateButton(hwnd, state, hit);
            return 0;
        }
        state->pressedButton = -1;
        InvalidateRect(hwnd, nullptr, FALSE);
        return 0;
    }
    case WM_CHAR:
        if (!state || state->busy || !state->request.input) return 0;
        if (wParam == 8) { if (!state->input.empty()) state->input.pop_back(); }
        else if (wParam == 27) { state->result = 0; DestroyWindow(hwnd); return 0; }
        else if (wParam == 13) { modalActivateButton(hwnd, state, 0); return 0; }
        else if (wParam == 22) { // Ctrl+V: tempel dari papan klip
            if (OpenClipboard(hwnd)) {
                HANDLE data = GetClipboardData(CF_UNICODETEXT);
                if (data) {
                    const wchar_t* text = static_cast<const wchar_t*>(GlobalLock(data));
                    if (text) {
                        for (const wchar_t* p = text; *p; ++p) {
                            if (*p >= 32 && *p != 127 && state->input.size() < 128) state->input.push_back(*p);
                        }
                        GlobalUnlock(data);
                    }
                }
                CloseClipboard();
            }
        }
        else if (wParam >= 32 && state->input.size() < 128) state->input.push_back(static_cast<wchar_t>(wParam));
        InvalidateRect(hwnd, nullptr, FALSE);
        return 0;
    case WM_KEYDOWN:
        if (state && wParam == VK_ESCAPE && !state->busy) { state->result = 0; DestroyWindow(hwnd); }
        return 0;
    case WM_CLOSE:
        if (state) state->result = 0;
        DestroyWindow(hwnd);
        return 0;
    case WM_DESTROY:
        KillTimer(hwnd, 1);
        return 0;
    }
    return DefWindowProcW(hwnd, message, wParam, lParam);
}

// === Mode Klien: sambung ke perangkat lain dari aplikasi yang sama ===
std::wstring clientDevicePath() {
    wchar_t localAppData[MAX_PATH]{};
    const DWORD n = GetEnvironmentVariableW(L"LOCALAPPDATA", localAppData, ARRAYSIZE(localAppData));
    const std::wstring base = n && n < ARRAYSIZE(localAppData) ? std::wstring(localAppData, n) : moduleDirectory();
    const auto directory = base + L"\\" + L"XyDesk";
    CreateDirectoryW(directory.c_str(), nullptr);
    return directory + L"\\" + L"client-device.txt";
}

std::wstring readLastClientDevice() {
    std::wifstream in(clientDevicePath());
    std::wstring line;
    if (in) std::getline(in, line);
    std::wstring digits;
    for (const wchar_t c : line) if (c >= L'0' && c <= L'9') digits.push_back(c);
    return digits.size() == 9 ? digits : L"";
}

void saveLastClientDevice(const std::wstring& digits) {
    std::wofstream out(clientDevicePath(), std::ios::trunc);
    out << digits;
}

void openClientSession(HWND hwnd, const std::wstring& digits) {
    const auto url = xydesk::panel_control::deviceLink(digits);
    if (!url.empty()) ShellExecuteW(hwnd, L"open", url.c_str(), nullptr, nullptr, SW_SHOWNORMAL);
}

int showPanelModal(HWND owner, PanelModalRequest request) {
    static bool registered = false;
    if (!registered) {
        WNDCLASSEXW wc{};
        wc.cbSize = sizeof(wc);
        wc.lpfnWndProc = panelModalProc;
        wc.hInstance = GetModuleHandleW(nullptr);
        wc.hCursor = LoadCursorW(nullptr, IDC_ARROW);
        wc.hbrBackground = nullptr;
        wc.lpszClassName = L"XyDeskPanelModal";
        registered = RegisterClassExW(&wc) != 0;
    }
    PanelModalState state;
    state.request = std::move(request);
    state.input = state.request.inputInitial;
    state.owner = owner;
    g_modal = &state;

    const auto px = [&](int n) { return xydesk::panel::scaled(n, g.layout.scalePct); };
    const int width = px(460);
    // Tinggi diukur dari teks supaya prompt panjang tidak terpotong.
    int textH = px(96);
    {
        HDC screen = GetDC(nullptr);
        if (screen) {
            HGDIOBJ oldFont = SelectObject(screen, g.fontBody);
            RECT measure{0, 0, width - px(48), 0};
            DrawTextW(screen, state.request.text.c_str(), -1, &measure, DT_WORDBREAK | DT_CALCRECT);
            textH = (std::max)(px(64), static_cast<int>(measure.bottom + px(12)));
            SelectObject(screen, oldFont);
            ReleaseDC(nullptr, screen);
        }
    }
    const int height = px(56) + textH + (state.request.input ? px(112) : px(28))
        + (state.request.status.empty() ? 0 : px(28)) + px(88);

    RECT workArea{};
    if (!SystemParametersInfoW(SPI_GETWORKAREA, 0, &workArea, 0)) {
        workArea = RECT{0, 0, GetSystemMetrics(SM_CXSCREEN), GetSystemMetrics(SM_CYSCREEN)};
    }
    const int x = workArea.left + ((workArea.right - workArea.left) - width) / 2;
    const int y = workArea.top + ((workArea.bottom - workArea.top) - height) / 2;

    HWND modal = CreateWindowExW(WS_EX_TOOLWINDOW, L"XyDeskPanelModal", L"", WS_POPUP,
        x, y, width, height, owner, nullptr, GetModuleHandleW(nullptr), nullptr);
    if (!modal) { g_modal = nullptr; return 0; }
    state.hwnd = modal;
    state.closeRect = Rect{width - px(46), px(10), px(36), px(36)};
    HRGN region = CreateRoundRectRgn(0, 0, width + 1, height + 1, px(24), px(24));
    SetWindowRgn(modal, region, TRUE);
    SetTimer(modal, 1, 530, nullptr);

    if (owner) EnableWindow(owner, FALSE);
    ShowWindow(modal, SW_SHOW);
    UpdateWindow(modal);
    SetForegroundWindow(modal);

    MSG msg{};
    while (IsWindow(modal) && GetMessageW(&msg, nullptr, 0, 0) > 0) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }
    if (owner) {
        EnableWindow(owner, TRUE);
        SetForegroundWindow(owner);
    }
    const int result = state.result;
    g_modal = nullptr;
    return result;
}

int showPanelDialog(HWND owner, const std::wstring& title, const std::wstring& text,
    std::vector<std::pair<std::wstring, int>> buttons) {
    PanelModalRequest request;
    request.title = title;
    request.text = text;
    request.buttons = std::move(buttons);
    return showPanelModal(owner, std::move(request));
}


void paintButton(Surface& surface, const PanelLayout& layout, HDC dc, Target target, const Rect& rect) {
    const bool enabled = targetEnabled(target);
    const bool hot = g.hot == target && enabled;
    const bool pressed = g.pressed == target && enabled;
    const ButtonPalette palette = buttonPalette(target, enabled, hot, pressed, layout);
    fillRoundedOpaque(surface, rect, palette.radius, palette.fill);
    if (!enabled && target != Target::Start) {
        strokeRounded(surface, rect, palette.radius, kEdge, 1);
    }
    if (enabled && g.focused == target) {
        strokeRounded(surface, rect, palette.radius, kAccent, xydesk::panel::scaled(2, layout.scalePct));
    }
    drawTextCentered(dc, targetLabel(target), rect, g.fontBody, palette.label);
}

// Garis-garis glyph caption digambar lewat satu pembantu: pena dibuat,
// dipakai untuk semua segmen, lalu dipulihkan — tidak ada pena bocor.
void drawGlyphSegments(HDC dc, COLORREF color, int thickness,
    const std::initializer_list<std::pair<POINT, POINT>>& segments) {
    const HGDIOBJ previousPen = SelectObject(dc,
        CreatePen(PS_SOLID | PS_JOIN_ROUND | PS_ENDCAP_ROUND, thickness, color));
    for (const auto& segment : segments) {
        MoveToEx(dc, segment.first.x, segment.first.y, nullptr);
        LineTo(dc, segment.second.x, segment.second.y);
    }
    if (previousPen) DeleteObject(SelectObject(dc, previousPen));
}

// Tiga tombol caption di kanan: perkecil, perbesar/pulihkan, tutup.
//
// Yang diambil dari macOS adalah gayanya — kotak membulat tanpa bingkai,
// latar yang baru muncul saat disentuh, glyph tipis 2 px — bukan tiga
// lingkaran merah/kuning/hijau di pojok kiri. Di jendela Windows lingkaran
// itu hanya membuat orang mencari tombol tutup di tempat yang salah, dan
// tetap tidak membuat panel ini terasa seperti aplikasi macOS. Hanya tombol
// tutup yang memerah saat disentuh, supaya makna destruktifnya jelas tanpa
// menambah warna mencolok lain ke caption.
void paintCaptionButtons(Surface& surface, const PanelLayout& layout, HDC dc) {
    const int thickness = std::max(1, xydesk::panel::scaled(2, layout.scalePct));
    const int radius = xydesk::panel::scaled(9, layout.scalePct);
    const bool active = GetForegroundWindow() == g.window;

    const struct { Target target; const Rect& hit; } buttons[] = {
        {Target::Minimize, layout.minimizeButton},
        {Target::Maximize, layout.maximizeButton},
        {Target::Close, layout.closeButton},
    };

    for (const auto& button : buttons) {
        const bool hot = g.hot == button.target;
        const bool pressed = g.pressed == button.target;
        COLORREF glyph = active ? kMuted : kDisabled;
        if (button.target == Target::Close && (hot || pressed)) {
            fillRoundedOpaque(surface, button.hit, radius, kBad, pressed ? 1.0f : 0.92f);
            glyph = kOnAccent;
        } else if (hot || pressed) {
            fillRoundedOpaque(surface, button.hit, radius,
                pressed ? kSurfacePressed : kSurface2);
            glyph = kText;
        }

        const int cx = xydesk::panel::centerX(button.hit);
        const int cy = xydesk::panel::centerY(button.hit);
        const int arm = std::max(3, xydesk::panel::scaled(5, layout.scalePct));
        if (button.target == Target::Close) {
            drawGlyphSegments(dc, glyph, thickness,
                {{{cx - arm, cy - arm}, {cx + arm + 1, cy + arm + 1}},
                 {{cx + arm, cy - arm}, {cx - arm - 1, cy + arm + 1}}});
        } else if (button.target == Target::Minimize) {
            drawGlyphSegments(dc, glyph, thickness,
                {{{cx - arm, cy}, {cx + arm + 1, cy}}});
        } else if (!g.maximized) {
            drawGlyphSegments(dc, glyph, thickness,
                {{{cx - arm, cy - arm}, {cx + arm, cy - arm}},
                 {{cx - arm, cy - arm}, {cx - arm, cy + arm}},
                 {{cx + arm, cy + arm}, {cx - arm, cy + arm}},
                 {{cx + arm, cy + arm}, {cx + arm, cy - arm}}});
        } else {
            // Sudah dizoom: dua kotak bertumpuk = "pulihkan ukuran".
            const int shrunk = arm - 1;
            drawGlyphSegments(dc, glyph, thickness,
                {{{cx - arm, cy - shrunk}, {cx + shrunk, cy - shrunk}},
                 {{cx - arm, cy - shrunk}, {cx - arm, cy + arm}},
                 {{cx + shrunk, cy + arm}, {cx - arm, cy + arm}},
                 {{cx + shrunk, cy + arm}, {cx + shrunk, cy - shrunk}},
                 {{cx - shrunk, cy - arm}, {cx + arm, cy - arm}},
                 {{cx + arm, cy - arm}, {cx + arm, cy + shrunk}}});
        }
    }
}

void paintXyDeskLogoMark(Surface& surface, HDC dc, const Rect& box, bool roundedBg = true) {
    const int size = std::min(box.w, box.h);
    if (size < 12) return;
    const Rect centerBox{box.x + (box.w - size) / 2, box.y + (box.h - size) / 2, size, size};
    if (roundedBg) {
        const int radius = std::max(4, size * 26 / 100);
        fillRoundedOpaque(surface, centerBox, radius, kAccent);
    }
    const int stroke = std::max(2, size * 18 / 100);
    HPEN penRibbon = CreatePen(PS_SOLID, stroke, RGB(255, 255, 255));
    HPEN penBack = CreatePen(PS_SOLID, stroke, RGB(221, 214, 254)); // lavender #DDD6FE
    HPEN oldPen = static_cast<HPEN>(SelectObject(dc, penBack));

    // Lengan silang belakang (kiri-atas ke kanan-bawah)
    const int x1 = centerBox.x + size * 22 / 100;
    const int y1 = centerBox.y + size * 22 / 100;
    const int x2 = centerBox.x + size * 78 / 100;
    const int y2 = centerBox.y + size * 78 / 100;
    MoveToEx(dc, x1, y1, nullptr);
    LineTo(dc, x2, y2);

    // Pita utama depan (kanan-atas ke kiri-bawah)
    SelectObject(dc, penRibbon);
    const int x3 = centerBox.x + size * 78 / 100;
    const int y3 = centerBox.y + size * 22 / 100;
    const int x4 = centerBox.x + size * 22 / 100;
    const int y4 = centerBox.y + size * 78 / 100;
    MoveToEx(dc, x3, y3, nullptr);
    LineTo(dc, x4, y4);

    // Glint bintang tengah putih
    const int cx = centerBox.x + size / 2;
    const int cy = centerBox.y + size / 2;
    const int dotR = std::max(2, stroke * 35 / 100);
    HBRUSH brushWhite = CreateSolidBrush(RGB(255, 255, 255));
    HBRUSH oldBrush = static_cast<HBRUSH>(SelectObject(dc, brushWhite));
    Ellipse(dc, cx - dotR, cy - dotR, cx + dotR + 1, cy + dotR + 1);

    SelectObject(dc, oldPen);
    SelectObject(dc, oldBrush);
    DeleteObject(penRibbon);
    DeleteObject(penBack);
    DeleteObject(brushWhite);
}

void paintLogo(Surface& surface, const PanelLayout& layout, HDC dc) {
    HICON icon = static_cast<HICON>(LoadImageW(GetModuleHandleW(nullptr), MAKEINTRESOURCEW(IDI_XYDESK), IMAGE_ICON, layout.logo.w, layout.logo.h, LR_SHARED));
    if (icon) {
        DrawIconEx(dc, layout.logo.x, layout.logo.y, icon, layout.logo.w, layout.logo.h, 0, nullptr, DI_NORMAL);
    } else {
        paintXyDeskLogoMark(surface, dc, layout.logo, true);
    }
}

// ── Sidebar ────────────────────────────────────────────────────────────
// Ikon digambar dari garis/busur sederhana (stroke 2px) supaya panel tidak
// "semua teks": bentuknya yang bicara, label kecil hanya penegas.
void paintSidebarIcon(HDC dc, Page page, const Rect& icon, COLORREF color) {
    // One icon family, one box and one weight for every navigation item.
    const wchar_t* glyph=L"\uE80F";
    switch(page){case Page::Status:glyph=L"\uE80F";break;case Page::Connections:glyph=L"\uE968";break;
    case Page::Pairing:glyph=L"\uE72E";break;case Page::Control:glyph=L"\uE7F4";break;
    case Page::Settings:glyph=L"\uE713";break;case Page::Account:glyph=L"\uE77B";break;case Page::Help:glyph=L"\uE897";break;case Page::Client:glyph=L"\uE71B";break;}
    drawTextCentered(dc,glyph,icon,g.fontIcons,color);
}

float sidebarItemY(const PanelLayout& layout, Page page) {
    switch (page) {
    case Page::Connections: return static_cast<float>(layout.sideConnections.y);
    case Page::Settings: return -1.0f;
    case Page::Account: return -1.0f;
    case Page::Help: return -1.0f;
    case Page::Pairing: return static_cast<float>(layout.sidePairing.y);
    case Page::Control: return static_cast<float>(layout.sideControl.y);
    case Page::Client: return static_cast<float>(layout.sideClient.y);
    case Page::Status:
    default: return static_cast<float>(layout.sideStatus.y);
    }
}

void startAnim(HWND hwnd) {
    if (!g.animOn) {
        g.animOn = true;
        SetTimer(hwnd, kAnimTimer, 16, nullptr);
    }
}

// Ganti halaman dengan transisi luncur; pill sidebar ikut meluncur.
void syncEmbeddedPage();
void goPage(HWND hwnd, Page target) {
    if (target == g.page) return;
    g.pageFrom = target;g.page=target;g.pageT=1.0f;
    g.pillY=sidebarItemY(g.layout,target);
    KillTimer(hwnd,kAnimTimer);g.animOn=false;syncEmbeddedPage();renderPanel();
}

void pollGate(HWND hwnd); // definisi ada di blok gerbang, di bawah.

void tickAnimation(HWND hwnd) {
    bool more = false;
    pollGate(hwnd);
    if (g.gateMotion.running) more = true;
    if (g.gateLogin.valid() || g.gateOtp.valid() || g.gateRestore.valid()) more = true;
    if (g.pageT < 1.0f) {
        g.pageT = std::min(1.0f, g.pageT + 0.10f); // ±160ms
        more = g.pageT < 1.0f;
    }
    const float targetY = sidebarItemY(g.layout, g.page);
    if (g.pillY < 0.0f) g.pillY = targetY;
    const float dy = targetY - g.pillY;
    if (std::fabs(dy) > 0.5f) {
        g.pillY += dy * 0.30f; // ease-out sederhana
        more = true;
    } else {
        g.pillY = targetY;
    }
    renderPanel();
    if (!more) {
        g.animOn = false;
        KillTimer(hwnd, kAnimTimer);
    }
}

void paintSidebar(Surface& surface, const PanelLayout& layout, HDC dc) {
    // Kolom sidebar bernada overlay (token web) + garis pemisah tipis, biar
    // navigasi terbaca sebagai wilayah sendiri di atas latar putih. Kolom
    // sengaja mulai setelah padding kiri (x=12) supaya bingkai putih panel
    // tetap terlihat utuh di sekelilingnya.
    const Rect column=layout.sidebarShell;
    const auto px=[&](int n){return xydesk::panel::scaled(n,layout.scalePct);};
    fillRoundedOpaque(surface,column,layout.radiusPanel,kSurface2);
    if(!g.sidebarCollapsed){
        drawTextLine(dc,L"WORKSPACE",{column.x+px(20),column.y+px(18),column.w-px(40),px(18)},g.fontCaps,kMuted,DT_LEFT|DT_SINGLELINE);
        Rect footer{column.x+px(10),column.bottom()-px(96),column.w-px(20),px(80)};
        fillRoundedOpaque(surface,footer,layout.radiusControl,kSurface);
        drawTextLine(dc,L"HOST WINDOWS",{footer.x+px(12),footer.y+px(14),footer.w-px(24),px(18)},g.fontCaps,kMuted,DT_LEFT|DT_SINGLELINE);
        drawTextLine(dc,g.running?L"Host berjalan":L"Host belum aktif",{footer.x+px(12),footer.y+px(39),footer.w-px(24),px(20)},g.fontSmall,kText,DT_LEFT|DT_SINGLELINE);
    }

    const struct {
        Page page;
        Target target;
        const Rect& item;
        const Rect& icon;
        const Rect& label;
        const wchar_t* text;
    } items[] = {
        {Page::Status, Target::PageStatus, layout.sideStatus, layout.sideStatusIcon, layout.sideStatusLabel, L"Beranda"},
        {Page::Pairing, Target::PagePairing, layout.sidePairing, layout.sidePairingIcon, layout.sidePairingLabel, L"Akses host"},
        {Page::Control, Target::PageControl, layout.sideControl, layout.sideControlIcon, layout.sideControlLabel, L"Kontrol host"},
        {Page::Connections, Target::PageConnections, layout.sideConnections, layout.sideConnectionsIcon, layout.sideConnectionsLabel, L"Koneksi"},
        {Page::Client, Target::PageClient, layout.sideClient, layout.sideClientIcon, layout.sideClientLabel, L"Klien"},
    };

    // Pill aktif meluncur (morphing) antar item; tingginya sama dengan item.
    if (g.pillY < 0.0f) g.pillY = sidebarItemY(layout, g.page);
    const Rect pill{layout.sideStatus.x, static_cast<int>(g.pillY + 0.5f),
        layout.sideStatus.w, layout.sideStatus.h};
    if (g.pillY >= 0.0f) {
    fillRoundedOpaque(surface, pill, layout.radiusControl, kSurface);
    strokeRounded(surface, pill, layout.radiusControl, kSurface, 1);
    }

    for (const auto& entry : items) {
        const bool active = g.page == entry.page;
        const bool hot = g.hot == entry.target;
        const bool pressed = g.pressed == entry.target;
        if (!active && (hot || pressed)) {
            fillRoundedOpaque(surface, entry.item, layout.radiusControl, pressed ? kSurfacePressed : kSurface2);
        }
        if (g.focused == entry.target) {
            strokeRounded(surface, entry.item, layout.radiusControl, kAccent, xydesk::panel::scaled(2, layout.scalePct));
        }
        paintSidebarIcon(dc, entry.page, entry.icon, active ? kText : (hot ? kText : kMuted));
        drawTextLine(dc, entry.text, entry.label, active?g.fontSemi:g.fontBody, active ? kText : kMuted, DT_LEFT|DT_VCENTER|DT_SINGLELINE);
    }
}

// Kartu kesehatan capture: jujur soal backend dan layar hitam/sesi berbeda.
void paintCaptureCard(Surface& surface, const PanelLayout& layout, HDC dc) {
    paintCard(surface,layout.captureCard,layout.radiusCard);
    drawTextLine(dc, L"CAPTURE", layout.captureTitle, g.fontCaps, kMuted,
        DT_LEFT | DT_VCENTER | DT_SINGLELINE);
    const std::wstring line1 = g.captureBackend.empty()
        ? std::wstring(L"Engine belum melaporkan capture")
        : (L"Backend: " + g.captureBackend);
    drawTextLine(dc, line1, layout.captureLine1, g.fontSmall, kText,
        DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);
    const std::wstring line2 = !g.captureNote.empty() ? g.captureNote
        : std::wstring(L"Tidak ada masalah terdeteksi — frame langsung dari sesi aktif.");
    drawTextLine(dc, line2, layout.captureLine2, g.fontSmall, g.captureWarn ? kWarn : kMuted,
        DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);
    if (g.sessionMismatch) {
        drawTextLine(dc, g.captureNote2, layout.captureLine3, g.fontSmall, kWarn,
            DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);
        paintButton(surface, layout, dc, Target::RunHost, layout.runHost);
    }
}

void workspaceText(HDC dc,const std::wstring& text,Rect rect,HFONT font,COLORREF color=kText){
    drawTextLine(dc,text,rect,font,color,DT_LEFT|DT_WORDBREAK|DT_NOPREFIX);
}
void paintConnection(Surface& surface,const PanelLayout& layout,HDC dc,Rect card,bool detailed){
    const auto px=[&](int n){return xydesk::panel::scaled(n,layout.scalePct);};
    paintCard(surface,card,layout.radiusCard);
    const int pad=px(20);int x=card.x+pad,y=card.y+pad;int width=card.w-2*pad;
    workspaceText(dc,L"PERANGKAT TERHUBUNG",{x,y,width,px(20)},g.fontCaps,kMuted);y+=px(28);
    const bool known=sessionView.known;
    if(!(sessionView.active&&known)){
        workspaceText(dc,!known?L"Menunggu status engine…":L"Belum ada HP/browser terhubung.",{x,y,width,px(40)},g.fontBody,kMuted);
        if(detailed) workspaceText(dc,L"Remote HP dari PC adalah fitur Premium — belum aktif di sesi ini.",{x,y+px(48),width,px(40)},g.fontSmall,kMuted);
        return;
    }
    // Baris ringkas: ikon HP · nama/model · status · durasi. Bukan foto produk besar.
    Rect row{x,y,width,px(56)};
    fillRoundedOpaque(surface,row,px(12),kSurface3);
    drawTextCentered(dc,L"\uE8EA",{x,y,px(48),px(56)},g.fontIcons,kText);
    const std::wstring title=sessionView.name.empty()?L"Perangkat tanpa nama":sessionView.name;
    workspaceText(dc,title,{x+px(52),y+px(8),width-px(160),px(24)},g.fontSemi);
    const std::wstring plat=sessionView.platform.empty()?L"klien":sessionView.platform;
    workspaceText(dc,plat+L" · streaming",{x+px(52),y+px(30),width-px(160),px(20)},g.fontSmall,kMuted);
    const std::wstring dur=std::to_wstring(sessionView.seconds/60)+L"m "+std::to_wstring(sessionView.seconds%60)+L"s";
    workspaceText(dc,dur,{x+width-px(96),y+px(16),px(88),px(24)},g.fontSmall,kMuted);
    y+=px(68);
    if(detailed){
        paintButton(surface,layout,dc,Target::StopSession,layout.stopSession);
        if(layout.sendFile.valid())paintButton(surface,layout,dc,Target::SendFile,layout.sendFile);
        int below=layout.acceptFile.bottom();
        if(sessionView.pendingId!=0){
            // Nama berkas boleh dipilih lawan, jadi ia digambar satu baris
            // dengan ellipsis dan tidak pernah melebar melewati kartu.
            const std::wstring judul=L"Berkas masuk: "+(sessionView.pendingName.empty()?std::wstring(L"tanpa nama"):sessionView.pendingName)+
                L"  ·  "+xydesk::account::wide(xydesk::updater::humanBytes(static_cast<std::int64_t>(sessionView.pendingSize)));
            drawTextLine(dc,judul,{x,layout.stopSession.bottom()+px(6),width,px(20)},g.fontSmall,kText,
                DT_LEFT|DT_VCENTER|DT_SINGLELINE|DT_END_ELLIPSIS|DT_NOPREFIX);
            paintButton(surface,layout,dc,Target::AcceptFile,layout.acceptFile);
            if(layout.trustFile.valid())paintButton(surface,layout,dc,Target::TrustFile,layout.trustFile);
            if(layout.rejectFile.valid())paintButton(surface,layout,dc,Target::RejectFile,layout.rejectFile);
            const std::wstring sisa=sessionView.pendingRisky
                ?(L"Berkas ini langsung dijalankan Windows. Ditolak sendiri dalam "+std::to_wstring(sessionView.pendingSeconds)+L" detik bila didiamkan.")
                :(L"Ditolak sendiri dalam "+std::to_wstring(sessionView.pendingSeconds)+L" detik bila didiamkan.");
            drawTextLine(dc,sisa,{x,layout.acceptFile.bottom()+px(4),width,px(20)},g.fontSmall,sessionView.pendingRisky?kWarn:kMuted,
                DT_LEFT|DT_VCENTER|DT_SINGLELINE|DT_END_ELLIPSIS|DT_NOPREFIX);
            below=layout.acceptFile.bottom()+px(24);
        }
        if(!sessionView.filePolicy.empty()&&layout.filePolicy.valid()){
            paintButton(surface,layout,dc,Target::FilePolicy,layout.filePolicy);
            if(sessionView.trustedDevices>0&&layout.forgetTrusted.valid())
                paintButton(surface,layout,dc,Target::ForgetTrusted,layout.forgetTrusted);
            below=layout.filePolicy.bottom();
        }
        if(!sessionView.unattendedPolicy.empty()&&layout.unattendedPolicy.valid()){
            paintButton(surface,layout,dc,Target::UnattendedPolicy,layout.unattendedPolicy);
            if(sessionView.unattendedPolicy==L"watched"&&layout.unattendedGrant.valid())
                paintButton(surface,layout,dc,Target::UnattendedGrant,layout.unattendedGrant);
            below=layout.unattendedPolicy.bottom();
        }
        workspaceText(dc,L"Remote HP dari PC  ·  Premium",{x,below+px(12),width,px(22)},g.fontSmall,kMuted);
        workspaceText(dc,L"Arah HP → PC sudah aktif. Arah PC → HP dikunci sampai langganan Premium.",{x,below+px(36),width,px(40)},g.fontSmall,kMuted);
    }
}
void paintAccessGuide(Surface& surface,const PanelLayout& l,HDC dc){
    const auto px=[&](int n){return xydesk::panel::scaled(n,l.scalePct);};
    const int x=l.idCard.right()+px(16),width=l.statusCard.right()-x;
    const auto link=xydesk::panel_control::deviceLink(g.deviceId);
    if(width>=px(140)&&!link.empty()){
        Rect card{x,l.idCard.y,width,px(288)};paintCard(surface,card,l.radiusCard);
        workspaceText(dc,L"SCAN UNTUK TERHUBUNG",{x+px(16),card.y+px(18),width-px(32),px(40)},g.fontCaps,kMuted);
        static std::wstring cachedLink;static std::optional<qrcodegen::QrCode> qr;
        if(cachedLink!=link){try{const std::string text(link.begin(),link.end());qr=qrcodegen::QrCode::encodeText(text.c_str(),qrcodegen::QrCode::Ecc::MEDIUM);cachedLink=link;}catch(...){qr.reset();}}
        if(qr){const int size=qr->getSize()+8,cell=std::max(1,std::min(width-px(32),px(180))/size),side=cell*size;
            const int left=x+(width-side)/2,top=card.y+px(64);RECT white{left,top,left+side,top+side};FillRect(dc,&white,static_cast<HBRUSH>(GetStockObject(WHITE_BRUSH)));
            for(int y=0;y<qr->getSize();++y)for(int xx=0;xx<qr->getSize();++xx)if(qr->getModule(xx,y)){RECT module{left+(xx+4)*cell,top+(y+4)*cell,left+(xx+5)*cell,top+(y+5)*cell};FillRect(dc,&module,static_cast<HBRUSH>(GetStockObject(BLACK_BRUSH)));}
        }
        workspaceText(dc,L"ID saja. Password tidak ada di QR.",{x+px(16),card.bottom()-px(44),width-px(32),px(40)},g.fontSmall,kMuted);
    }
    const int y=l.connectionQr.bottom()+px(24);Rect note{l.statusCard.x,y,l.statusCard.w,l.hint.y-y-px(16)};
    if(note.h>=px(144)){paintCard(surface,note,l.radiusCard);
        workspaceText(dc,L"Akses yang tetap di bawah kendali lu",{note.x+px(24),y+px(24),note.w-px(48),px(32)},g.fontSemi);
        workspaceText(dc,L"1. Bagikan link atau QR perangkat ini.\n2. Browser memakai izin tersimpan yang valid, atau meminta password pairing.\n3. Ubah password lewat Customize password di kartu Kode pairing untuk mencabut izin browser lama.",{note.x+px(24),y+px(66),note.w-px(48),note.h-px(80)},g.fontBody,kMuted);
    }
}
Rect embeddedCard(const PanelLayout& l){
    const auto px=[&](int n){return xydesk::panel::scaled(n,l.scalePct);};
    const int w=l.statusCard.w>=px(826)?px(640):l.statusCard.w;
    return {l.statusCard.x,l.statusCard.y,w,l.hint.y-l.statusCard.y-px(18)};
}
void paintScreenAside(Surface& surface,const PanelLayout& l,HDC dc,Page page){
    const auto px=[&](int n){return xydesk::panel::scaled(n,l.scalePct);};const Rect form=embeddedCard(l);paintCard(surface,form,l.radiusCard);const int x=form.right()+px(16),width=l.statusCard.right()-x;if(width<px(170))return;
    Rect card{x,l.statusCard.y,width,px(300)};paintCard(surface,card,l.radiusCard);
    const wchar_t* title=page==Page::Settings?L"Tentang pengaturan":page==Page::Account?L"Privasi akun":L"Catatan penting";
    const wchar_t* body=page==Page::Settings?L"Password baru mencabut izin browser lama.\n\nTidak perlu mengubah bitrate yang sudah nyaman.\n\nStatus penerapan tampil di halaman ini.":page==Page::Account?L"Login dibuka di browser sistem.\n\nSesi disimpan oleh Windows Credential Manager.\n\nKeluar dari aplikasi tidak menghapus cookie Google di browser.":L"X menyembunyikan aplikasi ke tray.\n\nHentikan mematikan host.\n\nStatus koneksi dan perangkat ada di halaman Koneksi.";
    workspaceText(dc,title,{x+px(20),card.y+px(24),width-px(40),px(48)},g.fontSemi);
    workspaceText(dc,body,{x+px(20),card.y+px(80),width-px(40),px(204)},g.fontBody,kMuted);
}

void paintWorkspaceSummary(Surface& surface,const PanelLayout& l,HDC dc){
    const auto px=[&](int n){return xydesk::panel::scaled(n,l.scalePct);};
    const int y=l.captureCard.bottom()+px(16),height=std::min(px(224),l.hint.y-y-px(14));if(height<px(136))return;
    const int half=(l.statusCard.w-px(16))/2;
    Rect left{l.statusCard.x,y,half,height},right{left.right()+px(16),y,l.statusCard.w-half-px(16),height};
    paintCard(surface,left,l.radiusCard);paintCard(surface,right,l.radiusCard);
    workspaceText(dc,L"AKSES KE PC INI",{left.x+px(24),y+px(22),half-px(48),px(20)},g.fontCaps,kMuted);
    workspaceText(dc,g.deviceId.empty()?L"ID belum tersedia":g.deviceId,{left.x+px(24),y+px(50),half-px(48),px(34)},g.fontValue);
    if(height>=px(200))workspaceText(dc,L"Link, QR dan password tersedia di Akses host. Kartu perangkat memakai wallpaper Windows, bukan frame video sesi.",{left.x+px(24),y+px(96),half-px(48),px(56)},g.fontBody,kMuted);
    workspaceText(dc,L"KONEKSI",{right.x+px(24),y+px(22),right.w-px(48),px(20)},g.fontCaps,kMuted);
    const std::wstring label=!sessionView.known?L"Menunggu status":sessionView.active?L"1 perangkat terhubung":L"Tidak ada sesi aktif";
    workspaceText(dc,label,{right.x+px(24),y+px(50),right.w-px(48),px(42)},g.fontSemi);
    if(height>=px(200))workspaceText(dc,sessionView.active?(sessionView.name.empty()?L"Nama perangkat tidak dilaporkan":sessionView.name):L"Detail nama, platform dan durasi ada di halaman Koneksi.",{right.x+px(24),y+px(98),right.w-px(48),px(48)},g.fontBody,kMuted);
    paintButton(surface,l,dc,Target::PagePairing,l.homeAccess);paintButton(surface,l,dc,Target::PageConnections,l.homeConnections);
}

// Menggambar panel ke permukaan. Tidak menyentuh jendela sama sekali, jadi
// jalur yang sama dipakai `--panel-snapshot` untuk memeriksa hasil gambar
// tanpa membuka jendela (dipakai CI).
void paintPageHeading(HDC dc, const PanelLayout& layout, Page page){
    const wchar_t* title=L"Beranda host";
    const wchar_t* detail=L"Status PC, akses, daftar HP, dan catatan preview wallpaper — ringkas.";
    switch(page){
    case Page::Connections:title=L"Koneksi perangkat";detail=L"Daftar ringkas: model, status, durasi. Foto produk tidak dipakai.";break;
    case Page::Pairing:title=L"Akses ke PC ini";detail=L"Bagikan link atau QR. Password tetap di bawah kendali lu.";break;
    case Page::Control:title=L"Kontrol host";detail=L"Kelola proses host tanpa keluar dari workspace.";break;
    case Page::Settings:title=L"Pengaturan host";detail=L"Password, bitrate, dan keamanan akses — satu halaman, tanpa pengaturan tersembunyi.";break;
    case Page::Account:title=L"Profil & akun";detail=L"Akun XyDesk terpisah dari login Windows. Keluar tidak menghapus cookie Google.";break;
    case Page::Help:title=L"Bantuan";detail=L"Langkah singkat untuk mulai terhubung dengan aman.";break;
    case Page::Client:title=L"Mode Klien";detail=L"Kendali PC lain dari aplikasi yang sama — satu aplikasi untuk mengendalikan dan diendalikan.";break;
    default:break;
    }
    drawTextLine(dc,title,layout.pageHeading,g.fontHeading,kText,DT_LEFT|DT_SINGLELINE|DT_END_ELLIPSIS);
    drawTextLine(dc,detail,layout.pageDescription,g.fontBody,kMuted,DT_LEFT|DT_SINGLELINE|DT_END_ELLIPSIS);
}

void paintPage(Surface& surface, const PanelLayout& layout, HDC dc, Page page) {
    switch (page) {
    case Page::Status:
        paintStatusCard(surface, layout, dc);
        paintCaptureCard(surface, layout, dc);
        paintWorkspaceSummary(surface,layout,dc);
        break;
    case Page::Pairing:
        paintIdentityCard(surface, layout, dc, layout.idCard, layout.idLabel, layout.idValue, layout.idCopy,
            L"Device ID", g.deviceId, Target::CopyId);
        paintPairingPasswordCard(surface, layout, dc);
        drawTextLine(dc,xydesk::panel_control::deviceLink(g.deviceId),layout.deviceLink,g.fontSmall,kText,DT_LEFT|DT_VCENTER|DT_SINGLELINE|DT_END_ELLIPSIS);
        paintButton(surface,layout,dc,Target::CopyLink,layout.copyLink);
        paintButton(surface,layout,dc,Target::ConnectionQr,layout.connectionQr);
        paintAccessGuide(surface,layout,dc);
        break;
    case Page::Connections: {
        const int px=xydesk::panel::scaled(1,layout.scalePct);
        Rect card{layout.statusCard.x,layout.statusCard.y,layout.statusCard.w,std::min(layout.hint.y-layout.statusCard.y-84*px,360*px)};
        paintConnection(surface,layout,dc,card,true);
        workspaceText(dc,L"Nama dan model dilaporkan oleh klien, bukan verifikasi identitas hardware. Hanya sesi aktif yang dilaporkan engine ditampilkan.",{card.x,card.bottom()+16*px,card.w,48*px},g.fontBody,kMuted);
        break;
    }
    case Page::Settings:case Page::Account:case Page::Help:paintScreenAside(surface,layout,dc,page);break;
    case Page::Control: {
        const auto px=[&](int n){return xydesk::panel::scaled(n,layout.scalePct);};
        Rect actions{layout.statusCard.x,layout.statusCard.y,layout.statusCard.w,layout.openLog.bottom()-layout.statusCard.y+px(20)};
        paintCard(surface,actions,layout.radiusCard);
        Rect note{actions.x,actions.bottom()+px(16),actions.w,std::min(px(228),layout.hint.y-actions.bottom()-px(32))};
        paintCard(surface,note,layout.radiusCard);
        paintButton(surface, layout, dc, Target::Start, layout.start);
        paintButton(surface, layout, dc, Target::Stop, layout.stop);
        paintButton(surface, layout, dc, Target::Restart, layout.restart);
        paintButton(surface, layout, dc, Target::Web, layout.web);
        paintButton(surface, layout, dc, Target::OpenLog, layout.openLog);
        workspaceText(dc,L"Kontrol proses host\n\nHentikan memutus layanan host. Restart menyalakan ulang proses host. Tombol X hanya menyembunyikan aplikasi ke tray.\n\nPengaturan video ada di halaman Pengaturan; password pairing diubah langsung dari halaman Akses host. Akun aplikasi terpisah dari identitas host Windows.", {note.x+px(24),note.y+px(24),note.w-px(48),note.h-px(48)},g.fontBody,kMuted);
        break;
    }
    case Page::Client: {
        const auto px=[&](int n){return xydesk::panel::scaled(n,layout.scalePct);};
        Rect actions{layout.statusCard.x,layout.statusCard.y,layout.statusCard.w,layout.clientWeb.bottom()-layout.statusCard.y+px(20)};
        paintCard(surface,actions,layout.radiusCard);
        Rect note{actions.x,actions.bottom()+px(16),actions.w,std::min(px(228),layout.hint.y-actions.bottom()-px(32))};
        paintCard(surface,note,layout.radiusCard);
        paintButton(surface, layout, dc, Target::ClientConnect, layout.clientConnect);
        paintButton(surface, layout, dc, Target::ClientLast, layout.clientLast);
        paintButton(surface, layout, dc, Target::Web, layout.clientWeb);
        workspaceText(dc,L"Kendali dari PC ini\n\nMasukkan kode perangkat untuk mengendalikan PC lain dari aplikasi yang sama. Sesi terbuka di XyDesk Web dengan akun yang sama — satu aplikasi untuk dua arah.\n\nKode terakhir tersimpan otomatis untuk sambung cepat.", {note.x+px(24),note.y+px(24),note.w-px(48),note.h-px(48)},g.fontBody,kMuted);
        break;
    }
    }
}

// Salinan tata letak dengan seluruh rect konten digeser — dipakai transisi
// luncur antar halaman. Fill menulis piksel langsung (bukan lewat GDI), jadi
// menggeser tata letak adalah satu-satunya cara menggeser isi secara utuh.
PanelLayout shiftedContent(const PanelLayout& l, int dx) {
    PanelLayout s = l;
    const auto shift = [dx](Rect& r) { r.x += dx; };
    shift(s.statusCard); shift(s.statusDot); shift(s.statusLine1); shift(s.statusLine2);
    shift(s.captureCard); shift(s.captureTitle); shift(s.captureLine1); shift(s.captureLine2);
    shift(s.captureLine3); shift(s.runHost); shift(s.stopSession);
    shift(s.idCard); shift(s.idLabel); shift(s.idValue); shift(s.idCopy);
    shift(s.passwordCard); shift(s.passwordLabel); shift(s.passwordValue); shift(s.passwordCopy);
    shift(s.passwordEye); shift(s.passwordCustomize);
    shift(s.start); shift(s.stop); shift(s.restart); shift(s.web); shift(s.openLog);
    return s;
}

// ── Gerbang masuk ───────────────────────────────────────────────────────────
// Panel host tidak lagi terbuka begitu saja: sambutan untuk pemasangan baru,
// lalu masuk akun. Aturan tahapnya ada di onboarding.h (teruji tanpa Windows);
// di sini hanya penyimpanan, menggambar, dan menyalakan animasi.

constexpr wchar_t kGateKey[] = L"Software\\XyDesk\\Panel";

bool welcomeSeenStored() {
    DWORD value = 0, size = sizeof(value), type = REG_DWORD;
    if (RegGetValueW(HKEY_CURRENT_USER, kGateKey, L"WelcomeSeen", RRF_RT_REG_DWORD, &type,
            &value, &size) != ERROR_SUCCESS) {
        return false;
    }
    return value != 0;
}

void storeWelcomeSeen() {
    HKEY key = nullptr;
    if (RegCreateKeyExW(HKEY_CURRENT_USER, kGateKey, 0, nullptr, 0, KEY_SET_VALUE, nullptr, &key,
            nullptr) != ERROR_SUCCESS) {
        return;
    }
    const DWORD value = 1;
    RegSetValueExW(key, L"WelcomeSeen", 0, REG_DWORD, reinterpret_cast<const BYTE*>(&value),
        sizeof(value));
    RegCloseKey(key);
}

std::uint64_t nowMs() { return GetTickCount64(); }

void beginGateMotion(HWND hwnd, std::uint32_t duration) {
    g.gateMotion.begin(nowMs(), duration);
    startAnim(hwnd);
}

/** Mulai memulihkan sesi tersimpan tanpa memblokir jendela. */
void probeAccount(HWND hwnd) {
    g.gate.start(/*hasSession=*/false, welcomeSeenStored());
    beginGateMotion(hwnd, xydesk::onboarding::kCardMs);
    try {
        g.gateRestore = std::async(std::launch::async, xydesk::account::restore);
    } catch (...) {
        // Tanpa thread, gerbang tetap bisa dipakai secara manual.
    }
    startAnim(hwnd);
}

void beginSignIn(HWND hwnd) {
    if (g.gate.busy()) return;
    g.gate.attempt(xydesk::onboarding::Method::Google);
    g.gateCancelled = false;
    try {
        g.gateLogin = std::async(std::launch::async, [] {
            return xydesk::account::login(g.gateCancelled);
        });
    } catch (...) {
        g.gate.fail("Proses login tidak dapat dimulai.");
    }
    beginGateMotion(hwnd, xydesk::onboarding::kPressMs);
    startAnim(hwnd);
}

std::string narrow(const std::wstring& text) {
    if (text.empty()) return {};
    const int n = WideCharToMultiByte(CP_UTF8, 0, text.data(), static_cast<int>(text.size()),
        nullptr, 0, nullptr, nullptr);
    if (n <= 0) return {};
    std::string out(static_cast<size_t>(n), 0);
    WideCharToMultiByte(CP_UTF8, 0, text.data(), static_cast<int>(text.size()), out.data(), n,
        nullptr, nullptr);
    return out;
}

// ── Formulir masuk lewat email ──────────────────────────────────────────────
//
// Satu-satunya kontrol Windows di layar gerbang adalah kotak isian EDIT.
// Alasannya praktis: mengetik alamat email butuh seleksi, tempel, IME, dan
// tombol Home/End yang benar — menuliskannya sendiri di atas GDI berarti
// menulis ulang editor teks dan tetap kalah. Sisa kartu tetap digambar
// manual supaya bentuk dan animasinya tidak berubah.

void beginGateMotion(HWND hwnd, std::uint32_t duration);

LRESULT CALLBACK gateEditProc(HWND hwnd, UINT message, WPARAM wParam, LPARAM lParam) {
    switch (message) {
    case WM_CTLCOLOREDIT: {
        // Isian gerbang: teks mengambang di atas kulit membulat yang digambar
        // paintGate — tanpa latar kotak putih bawaan EDIT.
        HDC editDc = reinterpret_cast<HDC>(wParam);
        SetTextColor(editDc, kText);
        SetBkMode(editDc, TRANSPARENT);
        return reinterpret_cast<LRESULT>(GetStockObject(NULL_BRUSH));
    }
    case WM_CHAR:
        // Enter dan Esc di EDIT satu baris berbunyi "beep" kalau diteruskan;
        // keduanya sudah ditangani di WM_KEYDOWN.
        if (wParam == VK_RETURN || wParam == VK_ESCAPE) return 0;
        break;
    case WM_KEYDOWN:
        if (wParam == VK_RETURN) {
            PostMessageW(GetParent(hwnd), kGateSubmitMessage, 0, 0);
            return 0;
        }
        if (wParam == VK_ESCAPE) {
            PostMessageW(GetParent(hwnd), kGateBackMessage, 0, 0);
            return 0;
        }
        break;
    default:
        break;
    }
    return CallWindowProcW(g.gateEditBase, hwnd, message, wParam, lParam);
}

/** Salin isi kotak ke formulir (dipanggil tiap EN_CHANGE). */
void readGateEdit() {
    if (!g.gateEditBox) return;
    wchar_t buffer[320] = {};
    GetWindowTextW(g.gateEditBox, buffer, static_cast<int>(std::size(buffer)));
    g.gateForm.typed = narrow(buffer);
}

/** Tempatkan, isi, atau sembunyikan kotak sesuai keadaan gerbang. */
void syncGateEdit() {
    using xydesk::emaillogin::Step;
    const bool wanted = g.gateEmail && !g.gate.panelVisible()
        && g.gate.stage != xydesk::onboarding::Stage::Welcome
        && g.gateForm.step != Step::Done;
    if (!wanted) {
        if (g.gateEditBox) ShowWindow(g.gateEditBox, SW_HIDE);
        return;
    }
    const auto& field = g.gateLayout.field;
    if (field.h <= 0) return;
    if (!g.gateEditBox) {
        g.gateEditBox = CreateWindowExW(0, L"EDIT", L"",
            WS_CHILD | WS_TABSTOP | ES_AUTOHSCROLL, field.x, field.y, field.w, field.h,
            g.window, reinterpret_cast<HMENU>(static_cast<INT_PTR>(kGateEditId)),
            GetModuleHandleW(nullptr), nullptr);
        if (!g.gateEditBox) return;
        g.gateEditBase = reinterpret_cast<WNDPROC>(SetWindowLongPtrW(g.gateEditBox, GWLP_WNDPROC,
            reinterpret_cast<LONG_PTR>(gateEditProc)));
        SendMessageW(g.gateEditBox, EM_SETMARGINS, EC_LEFTMARGIN | EC_RIGHTMARGIN,
            MAKELPARAM(xydesk::panel::scaled(14, g.layout.scalePct),
                xydesk::panel::scaled(14, g.layout.scalePct)));
    }
    SendMessageW(g.gateEditBox, WM_SETFONT, reinterpret_cast<WPARAM>(g.fontBody), TRUE);
    const bool code = g.gateForm.step == Step::Code;
    SendMessageW(g.gateEditBox, EM_SETLIMITTEXT, code ? 16 : 254, 0);
    // Kotak kode hanya menampung enam digit, tapi batasnya dilonggarkan supaya
    // tempelan "123 456" tetap masuk; penyaringan dilakukan di email_login.h.
    const int inset = xydesk::panel::scaled(1, g.layout.scalePct);
    SetWindowPos(g.gateEditBox, HWND_TOP, field.x + inset, field.y + inset,
        field.w - 2 * inset, field.h - 2 * inset, SWP_NOACTIVATE);
    EnableWindow(g.gateEditBox, g.gateForm.busy ? FALSE : TRUE);
    if (!IsWindowVisible(g.gateEditBox)) {
        ShowWindow(g.gateEditBox, SW_SHOW);
        SetFocus(g.gateEditBox);
    }
    wchar_t current[320] = {};
    GetWindowTextW(g.gateEditBox, current, static_cast<int>(std::size(current)));
    const auto wanted_text = xydesk::account::wide(g.gateForm.typed);
    if (wanted_text != current) {
        SetWindowTextW(g.gateEditBox, wanted_text.c_str());
        SendMessageW(g.gateEditBox, EM_SETSEL, static_cast<WPARAM>(wanted_text.size()),
            static_cast<LPARAM>(wanted_text.size()));
    }
}

/** Mulai permintaan kode (`/auth/request-otp`) di utas latar. */
void beginOtpRequest(HWND hwnd) {
    const auto body = g.gateForm.beginRequest(nowMs());
    if (body.empty()) {
        beginGateMotion(hwnd, xydesk::onboarding::kPressMs);
        return;
    }
    const auto email = g.gateForm.email;
    g.gateOtpVerifying = false;
    try {
        g.gateOtp = std::async(std::launch::async, [email, body] {
            return xydesk::account::sendOtp(email, body);
        });
    } catch (...) {
        g.gateForm.onOffline();
    }
    beginGateMotion(hwnd, xydesk::onboarding::kPressMs);
    startAnim(hwnd);
}

/** Tukar enam digit jadi sesi (`/auth/verify-otp`) di utas latar. */
void beginOtpVerify(HWND hwnd) {
    const auto body = g.gateForm.beginVerify();
    if (body.empty()) {
        beginGateMotion(hwnd, xydesk::onboarding::kPressMs);
        return;
    }
    g.gateOtpVerifying = true;
    try {
        g.gateOtp = std::async(std::launch::async, [body] {
            return xydesk::account::verifyOtp(body);
        });
    } catch (...) {
        g.gateForm.onOffline();
    }
    beginGateMotion(hwnd, xydesk::onboarding::kPressMs);
    startAnim(hwnd);
}

/** Tombol utama formulir: kirim kode atau masuk, tergantung langkah. */
void submitGateForm(HWND hwnd) {
    using xydesk::emaillogin::Step;
    if (!g.gateEmail || g.gateForm.busy) return;
    readGateEdit();
    if (g.gateForm.step == Step::Code) beginOtpVerify(hwnd);
    else beginOtpRequest(hwnd);
}

/** Esc / tombol kedua: mundur satu langkah, bukan menutup aplikasi. */
void gateFormBack(HWND hwnd) {
    using xydesk::emaillogin::Step;
    if (g.gateForm.busy) return;
    if (g.gateForm.step == Step::Code) {
        g.gateForm.backToEmail();
    } else {
        g.gateEmail = false;
        g.gateForm.reset();
    }
    syncGateEdit();
    beginGateMotion(hwnd, xydesk::onboarding::kStageMs);
}

/** Panen hasil request/verify OTP; dipanggil dari pollGate tiap tick. */
void pollGateOtp(HWND hwnd) {
    if (!g.gateOtp.valid()
        || g.gateOtp.wait_for(std::chrono::seconds(0)) != std::future_status::ready) {
        return;
    }
    const auto result = g.gateOtp.get();
    const auto now = nowMs();
    if (g.gateOtpVerifying) {
        if (result.ok) {
            g.gateForm.onVerified();
            g.gateAccount = result.user.email.empty() ? result.user.name : result.user.email;
            g.gate.succeed(narrow(g.gateAccount));
            g.gateEmail = false;
            storeWelcomeSeen();
            if (!g.running) PostMessageW(hwnd, kAutoStartMessage, 0, 0);
        } else if (result.offline) {
            g.gateForm.onOffline();
        } else {
            g.gateForm.onVerifyFailed(now, result.error);
        }
    } else {
        if (result.ok) g.gateForm.onCodeSent(now);
        else if (result.offline) g.gateForm.onOffline();
        else g.gateForm.onRequestFailed(now, result.error, result.retryIn);
    }
    syncGateEdit();
    beginGateMotion(hwnd, xydesk::onboarding::kStageMs);
}

/** Nama berkas saja, untuk ditampilkan di bilah status panel. */
std::wstring fileNameOf(const std::wstring& path) {
    const auto cut = path.find_last_of(L"\\/");
    return cut == std::wstring::npos ? path : path.substr(cut + 1);
}

// ── Pemilih berkas custom ───────────────────────────────────────────────────
// Menggantikan dialog "Buka" bawaan Windows: daftar folder/berkas digambar
// sendiri dengan gaya panel (kartu membulat, pil tombol), jendela tanpa
// caption Windows dan bisa digeser dari bar judulnya.
namespace filepicker {

struct Entry { std::wstring name; bool dir = false; };

struct State {
    std::wstring folder;
    std::vector<Entry> rows;
    int selected = -1;
    int scroll = 0;
    std::wstring result;
    Rect listRect{}, upRect{}, cancelRect{}, okRect{}, titleRect{};
};
State g_state;

std::wstring join(const std::wstring& folder, const std::wstring& name) {
    if (folder.empty()) return name;
    return folder + (folder.back() == L'\\' ? L"" : L"\\") + name;
}

void load(State& s) {
    s.rows.clear();
    s.selected = -1;
    s.scroll = 0;
    WIN32_FIND_DATAW fd{};
    const std::wstring pattern = join(s.folder, L"*");
    HANDLE h = FindFirstFileW(pattern.c_str(), &fd);
    if (h != INVALID_HANDLE_VALUE) {
        do {
            if (wcscmp(fd.cFileName, L".") == 0 || wcscmp(fd.cFileName, L"..") == 0) continue;
            if ((fd.dwFileAttributes & FILE_ATTRIBUTE_HIDDEN) != 0) continue;
            s.rows.push_back({fd.cFileName, (fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) != 0});
        } while (FindNextFileW(h, &fd));
        FindClose(h);
    }
    std::sort(s.rows.begin(), s.rows.end(), [](const Entry& a, const Entry& b) {
        if (a.dir != b.dir) return a.dir;
        return _wcsicmp(a.name.c_str(), b.name.c_str()) < 0;
    });
}

void up(State& s) {
    const auto cut = s.folder.find_last_of(L"\\/");
    if (cut == std::wstring::npos || cut < 2) return;
    s.folder = s.folder.substr(0, cut);
    load(s);
}

constexpr int kRowH = 32;

int rowAt(const State& s, int y) {
    if (y < s.listRect.y || y >= s.listRect.bottom()) return -1;
    return s.scroll + (y - s.listRect.y) / kRowH;
}

void paint(HWND hwnd) {
    State& s = g_state;
    const auto px = [](int n) { return xydesk::panel::scaled(n, g.layout.scalePct); };
    PAINTSTRUCT ps{};
    HDC windowDc = BeginPaint(hwnd, &ps);
    RECT rc{}; GetClientRect(hwnd, &rc);
    HDC dc = CreateCompatibleDC(windowDc);
    HBITMAP bitmap = CreateCompatibleBitmap(windowDc, rc.right, rc.bottom);
    HGDIOBJ oldBitmap = SelectObject(dc, bitmap);
    fillRoundedHdc(dc, Rect{0, 0, rc.right, rc.bottom}, px(18), kBackground);
    drawTextLine(dc, L"Pilih berkas untuk dikirim ke perangkat", s.titleRect,
        g.fontSemi, kText, DT_LEFT | DT_VCENTER | DT_SINGLELINE);
    drawTextLine(dc, s.folder, Rect{s.listRect.x, s.titleRect.bottom() + px(4), s.listRect.w, px(22)},
        g.fontSmall, kMuted, DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_PATH_ELLIPSIS);
    fillRoundedHdc(dc, s.listRect, px(12), kSurface);
    strokeRoundedHdc(dc, s.listRect, px(12), kEdge, 1);
    const int visible = s.listRect.h / kRowH;
    for (int i = 0; i < visible; ++i) {
        const int index = s.scroll + i;
        if (index >= static_cast<int>(s.rows.size())) break;
        const Rect row{s.listRect.x + px(4), s.listRect.y + i * kRowH,
            s.listRect.w - px(8), kRowH};
        const bool sel = index == s.selected;
        if (sel) fillRoundedHdc(dc, row, px(8), kSurface2);
        // Ikon mini: folder = persegi bertab, berkas = persegi bersudut lipat.
        const Rect icon{row.x + px(10), row.y + (row.h - px(16)) / 2, px(18), px(16)};
        if (s.rows[static_cast<size_t>(index)].dir) {
            fillRoundedHdc(dc, icon, px(3), sel ? kAccent : kMuted);
            fillRoundedHdc(dc, Rect{icon.x, icon.y, px(9), px(5)}, px(2), sel ? kAccent : kMuted);
        } else {
            fillRoundedHdc(dc, icon, px(2), kSurface2);
            strokeRoundedHdc(dc, icon, px(2), sel ? kAccent : kMuted, 1);
        }
        drawTextLine(dc, s.rows[static_cast<size_t>(index)].name,
            Rect{row.x + px(38), row.y, row.w - px(46), row.h},
            s.rows[static_cast<size_t>(index)].dir ? g.fontSemi : g.fontBody,
            sel ? kText : kText, DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_PATH_ELLIPSIS);
    }
    if (s.rows.empty()) {
        drawTextCentered(dc, L"Folder ini kosong.", s.listRect, g.fontBody, kDisabled);
    }
    const auto pill = [&](const Rect& r, const wchar_t* label, bool accent) {
        fillRoundedHdc(dc, r, r.h / 2, accent ? kAccent : kSurface);
        strokeRoundedHdc(dc, r, r.h / 2, accent ? kAccent : kEdge, 1);
        drawTextCentered(dc, label, r, g.fontSemi, accent ? kOnAccent : kText);
    };
    pill(s.upRect, L"Naik", false);
    pill(s.cancelRect, L"Batal", false);
    pill(s.okRect, L"Pilih", true);
    BitBlt(windowDc, 0, 0, rc.right, rc.bottom, dc, 0, 0, SRCCOPY);
    SelectObject(dc, oldBitmap);
    DeleteObject(bitmap);
    DeleteDC(dc);
    EndPaint(hwnd, &ps);
}

void layout(State& s, HWND hwnd) {
    const auto px = [](int n) { return xydesk::panel::scaled(n, g.layout.scalePct); };
    RECT rc{}; GetClientRect(hwnd, &rc);
    s.titleRect = Rect{px(20), px(8), rc.right - px(40), px(34)};
    s.listRect = Rect{px(16), px(72), rc.right - px(32), rc.bottom - px(72) - px(68)};
    s.upRect = Rect{px(16), rc.bottom - px(52), px(96), px(36)};
    s.okRect = Rect{rc.right - px(16) - px(120), rc.bottom - px(52), px(120), px(36)};
    s.cancelRect = Rect{s.okRect.x - px(10) - px(96), rc.bottom - px(52), px(96), px(36)};
}

int hitButton(const State& s, int x, int y) {
    const Rect p{x, y, 1, 1};
    if (p.x >= s.upRect.x && p.x < s.upRect.right() && p.y >= s.upRect.y && p.y < s.upRect.bottom()) return 1;
    if (p.x >= s.cancelRect.x && p.x < s.cancelRect.right() && p.y >= s.cancelRect.y && p.y < s.cancelRect.bottom()) return 2;
    if (p.x >= s.okRect.x && p.x < s.okRect.right() && p.y >= s.okRect.y && p.y < s.okRect.bottom()) return 3;
    return 0;
}

void openSelection(HWND hwnd) {
    State& s = g_state;
    if (s.selected < 0 || s.selected >= static_cast<int>(s.rows.size())) return;
    const auto& e = s.rows[static_cast<size_t>(s.selected)];
    if (e.dir) {
        s.folder = join(s.folder, e.name);
        load(s);
        InvalidateRect(hwnd, nullptr, FALSE);
    } else {
        s.result = join(s.folder, e.name);
        DestroyWindow(hwnd);
    }
}

LRESULT CALLBACK pickerProc(HWND hwnd, UINT message, WPARAM wParam, LPARAM lParam) {
    State& s = g_state;
    switch (message) {
    case WM_PAINT: paint(hwnd); return 0;
    case WM_ERASEBKGND: return 1;
    case WM_NCHITTEST: {
        POINT pt{static_cast<short>(LOWORD(lParam)), static_cast<short>(HIWORD(lParam))};
        ScreenToClient(hwnd, &pt);
        if (pt.y >= 0 && pt.y < s.titleRect.y + s.titleRect.h
            && hitButton(s, pt.x, pt.y) == 0 && rowAt(s, pt.y) < 0) {
            return HTCAPTION;
        }
        break;
    }
    case WM_MOUSEWHEEL: {
        const int delta = -GET_WHEEL_DELTA_WPARAM(wParam) / WHEEL_DELTA;
        const int visible = s.listRect.h / kRowH;
        const int maxScroll = (std::max)(0, static_cast<int>(s.rows.size()) - visible);
        s.scroll = (std::min)(maxScroll, (std::max)(0, s.scroll + delta * 3));
        InvalidateRect(hwnd, nullptr, FALSE);
        return 0;
    }
    case WM_LBUTTONDOWN: {
        const int x = static_cast<short>(LOWORD(lParam)), y = static_cast<short>(HIWORD(lParam));
        const int button = hitButton(s, x, y);
        if (button == 1) { up(s); InvalidateRect(hwnd, nullptr, FALSE); return 0; }
        if (button == 2) { DestroyWindow(hwnd); return 0; }
        if (button == 3) { openSelection(hwnd); return 0; }
        const int row = rowAt(s, y);
        if (row >= 0 && row < static_cast<int>(s.rows.size())) {
            s.selected = row;
            InvalidateRect(hwnd, nullptr, FALSE);
        }
        return 0;
    }
    case WM_LBUTTONDBLCLK: {
        const int x = static_cast<short>(LOWORD(lParam)), y = static_cast<short>(HIWORD(lParam));
        const int row = rowAt(s, y);
        if (row >= 0 && row < static_cast<int>(s.rows.size())) {
            s.selected = row;
            openSelection(hwnd);
        }
        return 0;
    }
    case WM_KEYDOWN: {
        const int visible = s.listRect.h / kRowH;
        if (wParam == VK_ESCAPE) { DestroyWindow(hwnd); return 0; }
        if (wParam == VK_RETURN) { openSelection(hwnd); return 0; }
        if (wParam == VK_UP || wParam == VK_DOWN) {
            const int delta = wParam == VK_DOWN ? 1 : -1;
            const int count = static_cast<int>(s.rows.size());
            if (count > 0) {
                s.selected = s.selected < 0 ? 0
                    : (std::min)(count - 1, (std::max)(0, s.selected + delta));
                if (s.selected < s.scroll) s.scroll = s.selected;
                if (s.selected >= s.scroll + visible) s.scroll = s.selected - visible + 1;
                InvalidateRect(hwnd, nullptr, FALSE);
            }
            return 0;
        }
        if (wParam == VK_BACK) { up(s); InvalidateRect(hwnd, nullptr, FALSE); return 0; }
        break;
    }
    case WM_CLOSE:
        DestroyWindow(hwnd);
        return 0;
    }
    return DefWindowProcW(hwnd, message, wParam, lParam);
}

} // namespace filepicker

/** Pemilih berkas custom panel. Kosong = pengguna membatalkan. */
std::wstring pickFileToSend(HWND owner) {
    using namespace filepicker;
    State& s = g_state;
    s = State{};
    wchar_t home[MAX_PATH]{};
    GetEnvironmentVariableW(L"USERPROFILE", home, static_cast<DWORD>(std::size(home)));
    s.folder = join(home, L"Documents");
    if (GetFileAttributesW(s.folder.c_str()) == INVALID_FILE_ATTRIBUTES) s.folder = home;
    load(s);

    WNDCLASSEXW wc{};
    wc.cbSize = sizeof(wc);
    wc.lpfnWndProc = pickerProc;
    wc.hInstance = GetModuleHandleW(nullptr);
    wc.style = CS_DBLCLKS;
    wc.hCursor = LoadCursorW(nullptr, IDC_ARROW);
    wc.lpszClassName = L"XyDeskPanelFilePicker";
    RegisterClassExW(&wc); // boleh gagal bila kelas sudah terdaftar

    const auto px = [](int n) { return xydesk::panel::scaled(n, g.layout.scalePct); };
    const int w = px(600), h = px(480);
    RECT workArea{};
    if (!SystemParametersInfoW(SPI_GETWORKAREA, 0, &workArea, 0)) {
        workArea = RECT{0, 0, GetSystemMetrics(SM_CXSCREEN), GetSystemMetrics(SM_CYSCREEN)};
    }
    const int x = workArea.left + ((workArea.right - workArea.left) - w) / 2;
    const int y = workArea.top + ((workArea.bottom - workArea.top) - h) / 2;
    HWND hwnd = CreateWindowExW(WS_EX_TOOLWINDOW, L"XyDeskPanelFilePicker",
        L"Pilih berkas", WS_POPUP, x, y, w, h, owner, nullptr,
        GetModuleHandleW(nullptr), nullptr);
    if (!hwnd) return {};
    layout(s, hwnd);
    SetWindowRgn(hwnd, CreateRoundRectRgn(0, 0, w + 1, h + 1, px(18), px(18)), TRUE);
    EnableWindow(owner, FALSE);
    ShowWindow(hwnd, SW_SHOW);
    UpdateWindow(hwnd);
    MSG msg;
    while (IsWindow(hwnd) && GetMessageW(&msg, nullptr, 0, 0) > 0) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }
    EnableWindow(owner, TRUE);
    SetForegroundWindow(owner);
    return s.result;
}

/** Dipanggil tiap tick animasi: panen hasil restore/login yang sudah siap. */
void pollGate(HWND hwnd) {
    pollGateOtp(hwnd);
    const auto ready = [](std::future<xydesk::account::Result>& f) {
        return f.valid() && f.wait_for(std::chrono::seconds(0)) == std::future_status::ready;
    };
    if (ready(g.gateRestore)) {
        const auto result = g.gateRestore.get();
        if (result.ok) {
            g.gateAccount = result.email.empty() ? result.name : result.email;
            g.gate.succeed(narrow(g.gateAccount));
            storeWelcomeSeen();
            if (!g.running) PostMessageW(hwnd, kAutoStartMessage, 0, 0);
        }
        beginGateMotion(hwnd, xydesk::onboarding::kStageMs);
    }
    if (ready(g.gateLogin)) {
        const auto result = g.gateLogin.get();
        if (result.ok) {
            g.gateAccount = result.email.empty() ? result.name : result.email;
            g.gate.succeed(narrow(g.gateAccount));
            storeWelcomeSeen();
            if (!g.running) PostMessageW(hwnd, kAutoStartMessage, 0, 0);
        } else {
            g.gate.fail(narrow(result.message));
        }
        beginGateMotion(hwnd, xydesk::onboarding::kStageMs);
    }
}

/**
 * 0 = tidak ada, 1 = tombol utama, 2 = tombol kedua, 3 = keluar aplikasi.
 * Di formulir email artinya: 1 = kirim kode / masuk, 2 = kirim ulang atau
 * kembali, 3 tetap keluar.
 */
int gateTargetAt(int x, int y) {
    if (g.gate.panelVisible()) return 0;
    const auto& l = g.gateLayout;
    if (l.quit.contains(x, y)) return 3;
    if (l.primary.contains(x, y)) return 1;
    if (l.secondary.h > 0 && l.secondary.contains(x, y)) return 2;
    return 0;
}

void activateGate(HWND hwnd, int target) {
    using xydesk::onboarding::Stage;
    switch (target) {
    case 1:
        if (g.gateEmail && g.gate.stage == Stage::Login) {
            submitGateForm(hwnd);
        } else if (g.gate.stage == Stage::Welcome) {
            g.gate.beginLogin();
            storeWelcomeSeen();
            beginGateMotion(hwnd, xydesk::onboarding::kStageMs);
        } else {
            beginSignIn(hwnd);
        }
        break;
    case 2:
        // Masuk lewat email terjadi di dalam aplikasi: kode sekali pakai ke
        // alamat yang diketik di kartu ini. Versi lama hanya membuka
        // xydesk.my.id/masuk di browser — sesinya tertinggal di browser dan
        // panel tetap meminta login, jadi tombolnya praktis jalan buntu.
        if (g.gate.stage == Stage::Login) {
            if (!g.gateEmail) {
                g.gateEmail = true;
                g.gateForm.reset();
                g.gate.attempt(xydesk::onboarding::Method::Email);
                g.gate.cancel(); // tidak ada yang ditunggu; tombol tetap hidup
                syncGateEdit();
                beginGateMotion(hwnd, xydesk::onboarding::kStageMs);
            } else if (g.gateForm.step == xydesk::emaillogin::Step::Code
                && g.gateForm.canResend(nowMs())) {
                beginOtpRequest(hwnd);
            } else {
                gateFormBack(hwnd);
            }
        }
        break;
    case 3:
        g.gateCancelled = true;
        PostMessageW(hwnd, WM_CLOSE, 0, 0);
        break;
    default:
        break;
    }
}

/**
 * Logo "G" Google, digambar dengan GDI pada empat warna resminya
 * (#4285F4 biru, #34A853 hijau, #FBBC05 kuning, #EA4335 merah).
 *
 * Kenapa digambar, bukan ditempel sebagai PNG: panel menggambar seluruh
 * permukaannya sendiri ke DIB 32-bit premultiplied, jadi sebuah bitmap juga
 * harus melewati jalur yang sama, dan ikon statis akan buram di 125%/150%.
 * Busur mengikuti skala, jadi tajam di DPI berapa pun.
 *
 * Bentuknya: cincin yang dipotong menjadi empat busur berwarna, ditambah
 * batang biru horizontal ke tengah — susunan yang sama dengan lambang resmi.
 */
void paintGoogleMark(HDC dc, const Rect& box) {
    const int size = std::min(box.w, box.h);
    if (size < 8) return;
    const int cx = xydesk::panel::centerX(box);
    const int cy = xydesk::panel::centerY(box);
    const int radius = size / 2;
    const int stroke = std::max(2, size * 22 / 100);
    const int inner = radius - stroke;
    if (inner <= 1) return;

    const RECT ring{cx - radius, cy - radius, cx + radius, cy + radius};
    const HGDIOBJ noBrush = SelectObject(dc, GetStockObject(NULL_BRUSH));
    const struct { COLORREF color; double from; double to; } arcs[] = {
        {RGB(234, 67, 53), 45.0, 135.0},    // merah: atas
        {RGB(251, 188, 5), 135.0, 215.0},   // kuning: kiri
        {RGB(52, 168, 83), 215.0, 315.0},   // hijau: bawah
        {RGB(66, 133, 244), 315.0, 405.0},  // biru: kanan
    };
    constexpr double pi = 3.14159265358979323846;
    for (const auto& arc : arcs) {
        const HPEN pen = CreatePen(PS_SOLID, stroke, arc.color);
        const HGDIOBJ previous = SelectObject(dc, pen);
        const double a0 = arc.from * pi / 180.0;
        const double a1 = arc.to * pi / 180.0;
        const double mid = (radius + inner) / 2.0;
        Arc(dc, ring.left, ring.top, ring.right, ring.bottom,
            cx + static_cast<int>(std::lround(std::cos(a0) * mid)),
            cy - static_cast<int>(std::lround(std::sin(a0) * mid)),
            cx + static_cast<int>(std::lround(std::cos(a1) * mid)),
            cy - static_cast<int>(std::lround(std::sin(a1) * mid)));
        SelectObject(dc, previous);
        DeleteObject(pen);
    }
    SelectObject(dc, noBrush);

    // Batang biru: dari tengah lingkaran ke tepi kanan, setinggi stroke.
    const HBRUSH bar = CreateSolidBrush(RGB(66, 133, 244));
    RECT barRect{cx, cy - stroke / 2, cx + radius, cy + stroke - stroke / 2};
    FillRect(dc, &barRect, bar);
    DeleteObject(bar);
}

void paintGate(Surface& surface, HDC dc) {
    using namespace xydesk::onboarding;
    const bool login = g.gate.stage != Stage::Welcome;
    const bool form = g.gateEmail && login;
    g.gateLayout = xydesk::panel::computeGateLayout(g.layout.panel, g.layout.scalePct, login, form);
    const auto& l = g.gateLayout;

    const float raw = g.gateMotion.progress(nowMs());
    const int lift = xydesk::panel::scaled(18, g.layout.scalePct);

    // Latar: warna latar panel yang sama seperti biasa — bukan abu-abu —
    // supaya padding dan sudut jendela tetap lolos pemeriksa bentuk. Fokus
    // datang dari kartu yang lebih terang di atasnya, bukan dari latar gelap.
    fillRectOpaque(surface, g.layout.panel, kBackground);

    const float cardT = easeOutExpo(staggered(raw, 0, xydesk::panel::GateLayout::kSteps));
    const Rect card{l.card.x, l.card.y + mixInt(lift, 0, cardT), l.card.w, l.card.h};
    // Kartu terang di atas bidang redup: kontrasnya datang dari pasangan ini,
    // sementara tepi panel tetap putih bersih.
    const Rect well = card.inset(-xydesk::panel::scaled(10, g.layout.scalePct));
    fillRoundedOpaque(surface, well, l.radiusCard + xydesk::panel::scaled(6, g.layout.scalePct),
        kSurface2, cardT);
    fillRoundedOpaque(surface, card, l.radiusCard, kSurface, cardT);
    strokeRounded(surface, card, l.radiusCard, kEdge, 1);

    const int dy = card.y - l.card.y;
    const auto shifted = [&](const Rect& r, int index) {
        const float t = easeOutExpo(staggered(raw, index, xydesk::panel::GateLayout::kSteps));
        return Rect{r.x, r.y + dy + mixInt(lift, 0, t), r.w, r.h};
    };

    // Tanda XyDesk: logo resmi pita geometris di atas piringan gradien lembut,
    // mengembang halus (easeOutBack). Piringan memberi bobot visual — hero
    // gerbang tidak lagi terasa "tempelan kecil".
    {
        const float t = easeOutBack(staggered(raw, 1, xydesk::panel::GateLayout::kSteps));
        const auto px = [&](int n) { return xydesk::panel::scaled(n, g.layout.scalePct); };
        const Rect base = shifted(l.mark, 1);
        const int size = mixInt(base.w * 7 / 10, base.w, t);
        const Rect mark{base.x + (base.w - size) / 2, base.y + (base.h - size) / 2, size, size};
        const Rect plate = mark.inset(-px(10));
        fillRoundedOpaque(surface, plate, plate.w / 2, kSurface2, t);
        strokeRounded(surface, plate, plate.w / 2, kEdge, 1);
        paintXyDeskLogoMark(surface, dc, mark, true);
    }

    using xydesk::emaillogin::Step;
    const bool codeStep = form && g.gateForm.step == Step::Code;
    const std::wstring emailWide = xydesk::account::wide(g.gateForm.email);
    const wchar_t* title = !login ? L"Selamat datang di XyDesk"
        : form ? (codeStep ? L"Cek email kamu" : L"Masuk dengan email")
               : L"Masuk ke XyDesk";
    const std::wstring subtitleText = !login
        ? L"Kendalikan PC ini dari HP. Siapkan sekali, pakai dari mana saja."
        : form ? (codeStep
            ? L"Kami kirim enam digit ke " + emailWide + L". Masukkan di bawah."
            : std::wstring(L"Masukkan alamat email kamu. Kami kirim kode sekali pakai."))
        : std::wstring(L"Akun dipakai untuk mengenali PC ini dan menyimpan perangkat tepercaya.");
    const wchar_t* subtitle = subtitleText.c_str();
    drawTextLine(dc, title, shifted(l.title, 2), g.fontHeading, kText,
        DT_CENTER | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);
    drawTextLine(dc, subtitle, shifted(l.subtitle, 2), g.fontSmall, kMuted,
        DT_CENTER | DT_WORDBREAK);

    // Kotak isian formulir email. Teksnya digambar oleh kontrol EDIT asli
    // Windows (lihat syncGateEdit); di sini hanya kulitnya.
    if (form && l.field.h > 0) {
        const Rect rect = shifted(l.field, 3);
        fillRoundedOpaque(surface, rect, l.radiusControl, kSurface2);
        const bool focused = g.gateEditBox && GetFocus() == g.gateEditBox;
        strokeRounded(surface, rect, l.radiusControl, focused ? kAccent
            : g.gateForm.error ? kBad : kEdge, focused ? 2 : 1);
        if (g.gateForm.typed.empty() && !focused) {
            drawTextCentered(dc, codeStep ? L"6 digit dari email" : L"nama@email.com",
                rect, g.fontBody, kDisabled);
        }
    }

    // Tombol utama.
    {
        const Rect rect = shifted(l.primary, 3);
        const bool hot = g.gateHot == 1;
        const bool pressed = g.gatePressed == 1;
        const bool ready = !form
            || (codeStep ? g.gateForm.canSubmitCode() : g.gateForm.canSubmitEmail());
        const bool busy = form ? g.gateForm.busy : g.gate.busy();
        const COLORREF fill = (busy || !ready) ? kDisabled
            : pressed ? kAccentPressed : hot ? kAccentHover : kAccent;
        fillRoundedOpaque(surface, rect, l.radiusControl, fill);
        const std::wstring label = !login ? L"Mulai"
            : form ? xydesk::account::wide(g.gateForm.actionLabel())
            : g.gate.busy() ? std::wstring(L"Menunggu browser…")
                            : std::wstring(L"Lanjutkan dengan Google");
        if (!login || form) {
            drawTextCentered(dc, label, rect, g.fontSemi, kOnAccent);
        } else {
            // Tombol Google memakai lambang resminya di dalam keping putih,
            // lalu teks di sisa ruang. Tanpa lambang, tombol itu cuma kalimat
            // dan orang ragu apakah benar menuju Google.
            const int markBox = xydesk::panel::scaled(28, g.layout.scalePct);
            const Rect chip{rect.x + xydesk::panel::scaled(8, g.layout.scalePct),
                rect.y + (rect.h - markBox) / 2, markBox, markBox};
            fillRoundedOpaque(surface, chip, markBox / 2, kSurface);
            paintGoogleMark(dc, chip.inset(xydesk::panel::scaled(5, g.layout.scalePct)));
            const Rect text{chip.right(), rect.y, rect.right() - chip.right()
                - xydesk::panel::scaled(8, g.layout.scalePct), rect.h};
            drawTextCentered(dc, label, text, g.fontSemi, kOnAccent);
        }
    }

    // Tombol kedua (hanya layar masuk).
    if (l.secondary.h > 0) {
        const Rect rect = shifted(l.secondary, 3);
        const bool pressed = g.gatePressed == 2;
        if (g.gateHot == 2 || pressed) {
            fillRoundedOpaque(surface, rect, l.radiusControl, pressed ? kSurfacePressed : kSurface3);
        }
        strokeRounded(surface, rect, l.radiusControl, kEdge, 1);
        const int wait = form ? g.gateForm.resendInSec(nowMs()) : 0;
        const std::wstring label = !form ? std::wstring(L"Masuk dengan email")
            : !codeStep ? std::wstring(L"Kembali ke pilihan masuk")
            : wait > 0 ? L"Kirim ulang kode (" + std::to_wstring(wait) + L" dtk)"
                       : std::wstring(L"Kirim ulang kode");
        const bool dim = form ? (g.gateForm.busy || (codeStep && wait > 0)) : g.gate.busy();
        drawTextCentered(dc, label, rect, g.fontSemi, dim ? kDisabled : kText);
    }

    // Catatan / pesan gagal.
    {
        const Rect rect = shifted(l.note, 4);
        if (form) {
            const std::wstring hint = xydesk::account::wide(g.gateForm.hint(nowMs()));
            drawTextLine(dc, hint, rect, g.fontSmall, g.gateForm.error ? kBad : kMuted,
                DT_CENTER | DT_WORDBREAK);
        } else if (!g.gate.message.empty()) {
            const std::wstring message = xydesk::account::wide(g.gate.message);
            drawTextLine(dc, message, rect, g.fontSmall, kBad, DT_CENTER | DT_WORDBREAK);
        } else {
            drawTextLine(dc,
                login ? L"Login dibuka di browser sistem. Jangan bagikan URL callback."
                      : L"Perlu akun XyDesk. Membuatnya gratis dan satu menit.",
                rect, g.fontSmall, kMuted, DT_CENTER | DT_WORDBREAK);
        }
    }

    // Dua titik langkah: sambutan → masuk.
    {
        const Rect rect = shifted(l.dots, 4);
        const int dot = xydesk::panel::scaled(6, g.layout.scalePct);
        const int gap = xydesk::panel::scaled(8, g.layout.scalePct);
        const int totalW = dot * 2 + gap;
        int x = rect.x + (rect.w - totalW) / 2;
        for (int index = 0; index < 2; ++index) {
            const bool active = (index == 0) == (!login);
            fillCircleOpaque(surface, Rect{x, rect.y, dot, dot}, active ? kAccent : kSurface3);
            x += dot + gap;
        }
    }

    // Keluar aplikasi dari gerbang: tanpa ini pengguna yang belum punya akun
    // terjebak di jendela tanpa jalan keluar selain Task Manager.
    {
        const Rect rect = shifted(l.quit, 0);
        if (g.gateHot == 3) fillRoundedOpaque(surface, rect, l.radiusControl, kSurface3);
        drawTextCentered(dc, L"\u2715", rect, g.fontSmall, kMuted);
    }

    syncGateEdit();
    if (g.gateMotion.running) startAnim(g.window);
}

bool drawPanelToSurface() {
    if (!ensureSurface(g.surface, g.layout.window.w, g.layout.window.h)) return false;
    Surface& surface = g.surface;
    clearSurface(surface);
    GdiFlush();

    // Panel: isi penuh dulu (termasuk sudut), lalu garis tepi tipis supaya
    // tepi panel tetap terbaca walau dinding desktop gelap.
    fillRectOpaque(surface, g.layout.panel, kBackground);

    strokeRounded(surface, g.layout.panel.inset(1), g.layout.radiusPanel - 1, kEdge, 1);

    HDC dc = surface.dc;
    if (!dc) return false;

    HFONT previousFont = static_cast<HFONT>(SelectObject(dc, g.fontBody));

    // Sebelum masuk akun, panel tidak digambar sama sekali — bukan sekadar
    // ditutupi. Tidak ada ID, kode pairing, atau status mesin yang bocor ke
    // layar bagi orang yang menemukan PC ini menyala.
    if (!g.gate.panelVisible()) {
        paintGate(surface, dc);
        paintCaptionButtons(surface, g.layout, dc);
        SelectObject(dc, previousFont);
        GdiFlush();
        // Ritual penutup yang sama dengan panel: tanpa ini sudut jendela tetap
        // kotak dan opak, dan pemeriksa bentuk di CI menangkapnya.
        applyWindowShape(surface, g.layout);
        return true;
    }

    fillRoundedOpaque(surface,g.layout.workspaceShell,g.layout.radiusPanel,kSurface2);
    paintPageHeading(dc,g.layout,g.page);

    // Konten halaman (dengan transisi luncur saat morphing), lalu chrome
    // (sidebar + caption) digambar di atas supaya isi yang meluncur tidak
    // pernah menimpa navigasi.
    if (g.pageT < 1.0f && g.pageFrom != g.page) {
        const float e = xydesk::panel::smoothstep01(g.pageT);
        const int slide = xydesk::panel::scaled(28, g.layout.scalePct);
        const PanelLayout layoutFrom = shiftedContent(g.layout, -static_cast<int>(e * slide));
        const PanelLayout layoutTo = shiftedContent(g.layout, static_cast<int>((1.0f - e) * slide));
        paintPage(surface, layoutFrom, dc, g.pageFrom);
        paintPage(surface, layoutTo, dc, g.page);
    } else {
        paintPage(surface, g.layout, dc, g.page);
    }

    drawTextLine(dc, L"X menyembunyikan ke tray · Tab untuk navigasi · Enter untuk memilih",
        g.layout.hint, g.fontSmall, kMuted, DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);

    paintSidebar(surface, g.layout, dc);
    paintLogo(surface, g.layout, dc);
    drawTextLine(dc, L"XyDesk", g.layout.title, g.fontTitle, kText,
        DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);
    drawTextLine(dc, L"Host workspace", g.layout.subtitle, g.fontSmall, kMuted,
        DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);
    paintCaptionButtons(surface, g.layout, dc);
    const struct {Target target;Rect rect;const wchar_t* glyph;} tools[]={
        {Target::ToggleSidebar,g.layout.toggleSidebar,L"\uE700"},{Target::Settings,g.layout.settings,L"\uE713"},
        {Target::Profile,g.layout.profile,L"\uE77B"},{Target::Help,g.layout.help,L"\uE897"}};
    for(const auto& tool:tools){const bool selected=(tool.target==Target::Settings&&g.page==Page::Settings)||(tool.target==Target::Profile&&g.page==Page::Account)||(tool.target==Target::Help&&g.page==Page::Help);if(selected||g.hot==tool.target||g.focused==tool.target)fillRoundedOpaque(surface,tool.rect,g.layout.radiusControl,kSurface3);drawTextCentered(dc,tool.glyph,tool.rect,g.fontIcons,kText);}

    SelectObject(dc, previousFont);
    GdiFlush();
    applyWindowShape(surface, g.layout);
    return true;
}

void renderPanel() {
    if (!g.window) return;
    if (!drawPanelToSurface()) return;
    Surface& surface = g.surface;
    if(!g.layered){
        static int regionW=0,regionH=0;
        if(regionW!=surface.width||regionH!=surface.height){regionW=surface.width;regionH=surface.height;SetWindowRgn(g.window,CreateRoundRectRgn(0,0,regionW+1,regionH+1,g.layout.radiusPanel*2,g.layout.radiusPanel*2),TRUE);}
        syncEmbeddedPage();InvalidateRect(g.window,nullptr,FALSE);return;
    }

    // Panel di koordinat layar: jendela sudah diposisikan sekali di awal, jadi
    // cukup menempelkan permukaan pada posisi jendela itu.
    RECT windowRect{};
    GetWindowRect(g.window, &windowRect);
    POINT destination{windowRect.left, windowRect.top};
    POINT source{0, 0};
    SIZE size{g.layout.window.w, g.layout.window.h};
    BLENDFUNCTION blend{AC_SRC_OVER, 0, 255, AC_SRC_ALPHA};
    HDC screen = GetDC(nullptr);
    const BOOL applied = UpdateLayeredWindow(g.window, screen, &destination, &size, surface.dc,
        &source, 0, &blend, ULW_ALPHA);
    ReleaseDC(nullptr, screen);
    if (!applied && g.layered) {
        // Lingkungan tanpa dukungan jendela berlapis: jatuh ke jalur kedua —
        // region membulat + BitBlt di WM_PAINT — supaya panel tetap tampil.
        g.layered = false;
        SetWindowLongPtrW(g.window, GWL_EXSTYLE, GetWindowLongPtrW(g.window, GWL_EXSTYLE) & ~WS_EX_LAYERED);
        const HRGN region = CreateRoundRectRgn(g.layout.panel.x, g.layout.panel.y,
            g.layout.panel.right() + 1, g.layout.panel.bottom() + 1,
            g.layout.radiusPanel * 2, g.layout.radiusPanel * 2);
        SetWindowRgn(g.window, region, TRUE);
        InvalidateRect(g.window, nullptr, TRUE);
    }
}

void createFonts() {
    const int s = g.layout.scalePct;
    const auto make = [](int height, int weight, const wchar_t* face) {
        return CreateFontW(height, 0, 0, 0, weight, FALSE, FALSE, FALSE, DEFAULT_CHARSET,
            OUT_TT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_DONTCARE, face);
    };
    if (g.fontHeading) DeleteObject(g.fontHeading);
    if (g.fontTitle) DeleteObject(g.fontTitle);
    if (g.fontBody) DeleteObject(g.fontBody);
    if (g.fontSmall) DeleteObject(g.fontSmall);
    if (g.fontMono) DeleteObject(g.fontMono);
    if (g.fontLogo) DeleteObject(g.fontLogo);
    if (g.fontSemi) DeleteObject(g.fontSemi);
    if (g.fontCaps) DeleteObject(g.fontCaps);
    if (g.fontValue) DeleteObject(g.fontValue);
    // Hirarki tipografi mengikuti web: judul tebal, status semibold, label
    // kecil kapital, angka pairing besar monospasi.
    if(g.fontIcons)DeleteObject(g.fontIcons);
    g.fontIcons = make(-xydesk::panel::scaled(20, s), FW_NORMAL, L"Segoe MDL2 Assets");
    g.fontHeading = make(-xydesk::panel::scaled(30, s), FW_SEMIBOLD, L"Segoe UI");
    g.fontTitle = make(-xydesk::panel::scaled(20, s), FW_BOLD, L"Segoe UI");
    g.fontBody = make(-xydesk::panel::scaled(14, s), FW_NORMAL, L"Segoe UI");
    g.fontSmall = make(-xydesk::panel::scaled(12, s), FW_NORMAL, L"Segoe UI");
    g.fontSemi = make(-xydesk::panel::scaled(15, s), FW_SEMIBOLD, L"Segoe UI");
    g.fontCaps = make(-xydesk::panel::scaled(11, s), FW_SEMIBOLD, L"Segoe UI");
    g.fontValue = make(-xydesk::panel::scaled(22, s), FW_SEMIBOLD, L"Consolas");
    g.fontMono = make(-xydesk::panel::scaled(20, s), FW_SEMIBOLD, L"Consolas");
    g.fontLogo = make(-xydesk::panel::scaled(17, s), FW_BOLD, L"Segoe UI");
}

UINT windowDpi(HWND hwnd) {
    using GetDpiForWindowFn = UINT(WINAPI*)(HWND);
    static const auto fn = reinterpret_cast<GetDpiForWindowFn>(
        reinterpret_cast<void*>(GetProcAddress(GetModuleHandleW(L"user32.dll"), "GetDpiForWindow")));
    if (fn) {
        const UINT dpi = fn(hwnd);
        if (dpi) return dpi;
    }
    HDC screen = GetDC(nullptr);
    const UINT fallback = screen ? static_cast<UINT>(GetDeviceCaps(screen, LOGPIXELSX)) : 96;
    if (screen) ReleaseDC(nullptr, screen);
    return fallback ? fallback : 96;
}

void centerWindow(HWND hwnd) {
    RECT workArea{};
    MONITORINFO monitor{sizeof(MONITORINFO)};
    if (GetMonitorInfoW(MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST), &monitor)) {
        workArea = monitor.rcWork;
    } else if (!SystemParametersInfoW(SPI_GETWORKAREA, 0, &workArea, 0)) {
        workArea = RECT{0, 0, GetSystemMetrics(SM_CXSCREEN), GetSystemMetrics(SM_CYSCREEN)};
    }
    const int x = workArea.left + ((workArea.right - workArea.left) - g.layout.window.w) / 2;
    const int y = workArea.top + ((workArea.bottom - workArea.top) - g.layout.window.h) / 2;
    SetWindowPos(hwnd, nullptr, x, y, g.layout.window.w, g.layout.window.h,
        SWP_NOZORDER | SWP_NOACTIVATE);
}

void applyDpi(HWND hwnd, UINT dpi, bool remeasure) {
    // Zoom perbesar ikut dikalikan ke DPI efektif sehingga seluruh tata
    // letak (termasuk font) membesar proporsional lewat jalur skala yang
    // sudah teruji; tidak ada gambar yang perlu digambar ulang khusus.
    UINT effective = static_cast<UINT>(static_cast<unsigned long long>(dpi) * g.zoomPct / 100);
    MONITORINFO monitor{sizeof(MONITORINFO)};
    if (GetMonitorInfoW(MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST), &monitor)) {
        const int availableW = std::max(1, static_cast<int>(monitor.rcWork.right - monitor.rcWork.left) - 24);
        const int availableH = std::max(1, static_cast<int>(monitor.rcWork.bottom - monitor.rcWork.top) - 24);
        const int fitDpi = std::max(72, std::min(availableW * 96 / xydesk::panel::kPanelMinWidth, availableH * 96 / xydesk::panel::kPanelMinHeight));
        effective = std::min(effective, static_cast<UINT>(fitDpi));
        const int scale = xydesk::panel::scalePctFromDpi(static_cast<int>(effective));
        g.unitsW = std::min(g.unitsW, std::max(xydesk::panel::kPanelMinWidth, availableW * 100 / scale));
        g.unitsH = std::min(g.unitsH, std::max(xydesk::panel::kPanelMinHeight, availableH * 100 / scale));
    }
    g.layout = xydesk::panel::computeLayout(static_cast<int>(effective), g.unitsW, g.unitsH, g.sidebarCollapsed);
    g.pillY = sidebarItemY(g.layout, g.page); // posisi instan saat DPI/zoom
    createFonts();
    if (remeasure) {
        centerWindow(hwnd);
    } else {
        SetWindowPos(hwnd, nullptr, 0, 0, g.layout.window.w, g.layout.window.h,
            SWP_NOZORDER | SWP_NOACTIVATE | SWP_NOMOVE);
    }
    renderPanel();
}

// Zoom agar panel (dengan margin bayangan) pas di area kerja monitor utama.
// Dihitung dari ukuran jendela yang SEDANG tampil supaya benar pada DPI
// berapa pun; dibatasi 100–400 mengikuti batas skala tata letak.
int workAreaZoom() {
    RECT work{};
    if (!SystemParametersInfoW(SPI_GETWORKAREA, 0, &work, 0)) {
        work = RECT{0, 0, GetSystemMetrics(SM_CXSCREEN), GetSystemMetrics(SM_CYSCREEN)};
    }
    const int availW = work.right - work.left;
    const int availH = work.bottom - work.top;
    if (availW <= 0 || availH <= 0 || g.layout.window.w <= 0 || g.layout.window.h <= 0) return 100;
    const int zoom = std::min(availW * 100 / g.layout.window.w, availH * 100 / g.layout.window.h);
    return std::clamp(zoom, 100, 400);
}

// Perbesar = zoom penuh area kerja; klik lagi = kembali ke ukuran dan
// posisi semula. Posisi normal disimpan sebelum zoom pertama.
void toggleMaximize(HWND hwnd) {
    if(!g.maximized){
        g.haveNormalRect=GetWindowRect(hwnd,&g.normalRect)!=FALSE;
        MONITORINFO monitor{sizeof(MONITORINFO)};
        if(!GetMonitorInfoW(MonitorFromWindow(hwnd,MONITOR_DEFAULTTONEAREST),&monitor))return;
        g.maximized=true;g.zoomPct=100;
        const auto& r=monitor.rcWork;
        SetWindowPos(hwnd,nullptr,r.left,r.top,r.right-r.left,r.bottom-r.top,SWP_NOZORDER|SWP_NOACTIVATE);
    }else{
        g.maximized=false;
        if(g.haveNormalRect)SetWindowPos(hwnd,nullptr,g.normalRect.left,g.normalRect.top,g.normalRect.right-g.normalRect.left,g.normalRect.bottom-g.normalRect.top,SWP_NOZORDER|SWP_NOACTIVATE);
    }
    renderPanel();
}

// ── Mesin host: start/stop/restart --------------------------------------

void closeHostHandles() {
    controlChannel.reset();
    g.panelBitrate = -1;
    if (g.process) {
        CloseHandle(g.process);
        g.process = nullptr;
    }
    if (g.logFile) {
        CloseHandle(g.logFile);
        g.logFile = nullptr;
    }
    if (g.job) {
        CloseHandle(g.job);
        g.job = nullptr;
    }
    g.running = false;
}

bool createJob() {
    g.job = CreateJobObjectW(nullptr, nullptr);
    if (!g.job) return false;
    JOBOBJECT_EXTENDED_LIMIT_INFORMATION info{};
    info.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
    if (!SetInformationJobObject(g.job, JobObjectExtendedLimitInformation, &info, sizeof(info))) {
        CloseHandle(g.job);
        g.job = nullptr;
        return false;
    }
    return true;
}

void stopHost() {
    g.startRequested = false;
    g.captureSeen = false;
    g.captureBackend.clear();
    g.captureNote = L"Capture belum aktif — mulai host untuk menerima koneksi.";
    g.captureNote2.clear();
    g.captureWarn = false;
    g.sessionMismatch = false;
    g.procUser.clear();
    g.activeUser.clear();
    g.procSession = g.activeSession = -1;
    if (g.job) TerminateJobObject(g.job, 0);
    closeHostHandles();
    setStatus(L"Host berhenti", kMuted);
}

bool restartHost() {
    stopHost();
    return startHost();
}

bool startHost() {
    if (g.running) return true;
    if (GetFileAttributesW(enginePath().c_str()) == INVALID_FILE_ATTRIBUTES) {
        g.lastError = L"xydesk-host.exe tidak ditemukan di folder aplikasi.";
        setStatus(g.lastError, kBad);
        return false;
    }
    if (g.deviceId.empty() || g.pairingCode.empty()) {
        g.startRequested = true;
        if (!identityFuture.valid()) {
            try { identityFuture = std::async(std::launch::async, readIdentityBounded); }
            catch (...) { g.startRequested = false; setStatus(L"Pembaca identitas tidak dapat dimulai.", kBad); return false; }
        }
        setStatus(L"Membaca identitas host…", kMuted);
        return true;
    }
    g.startRequested = false;
    if (!createJob()) {
        g.lastError = L"Windows tidak mengizinkan pengawasan proses host.";
        setStatus(g.lastError, kBad);
        return false;
    }

    g.logPath = hostLogPath();
    SECURITY_ATTRIBUTES logSecurity{sizeof(SECURITY_ATTRIBUTES), nullptr, TRUE};
    g.logFile = CreateFileW(g.logPath.c_str(), FILE_APPEND_DATA,
        FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE, &logSecurity, OPEN_ALWAYS,
        FILE_ATTRIBUTE_NORMAL, nullptr);
    if (g.logFile == INVALID_HANDLE_VALUE) {
        g.logFile = nullptr;
        closeHostHandles();
        g.lastError = L"Log host tidak dapat dibuka: " + g.logPath;
        setStatus(g.lastError, kBad);
        return false;
    }

    std::wstring command = quote(enginePath()) + L" --url \"wss://signal.xydesk.my.id/ws\" --managed-auth";
    PROCESS_INFORMATION pi{};
    const BOOL started = controlChannel.launch(command, moduleDirectory(), g.logFile, pi);
    if (!started) {
        const DWORD error = GetLastError();
        closeHostHandles();
        g.lastError = L"Host tidak dapat dimulai (Windows error " + std::to_wstring(error) + L").";
        setStatus(g.lastError, kBad);
        return false;
    }
    g.process = pi.hProcess;
    if (!AssignProcessToJobObject(g.job, g.process)) {
        TerminateProcess(g.process, 1);
        CloseHandle(pi.hThread);
        closeHostHandles();
        g.lastError = L"Windows gagal mengikat host ke pengawas proses.";
        setStatus(g.lastError, kBad);
        return false;
    }
    if (ResumeThread(pi.hThread) == static_cast<DWORD>(-1)) {
        CloseHandle(pi.hThread);TerminateProcess(g.process,1);closeHostHandles();
        setStatus(L"Engine tidak dapat dilanjutkan.",kBad);return false;
    }
    CloseHandle(pi.hThread);
    g.running = true;
    setStatus(L"Proses host dimulai — menunggu status engine", kMuted);
    return true;
}

// ── Clipboard, tray, dan aksi UI ---------------------------------------

void copyToClipboard(HWND owner, const std::wstring& text) {
    if (text.empty() || !OpenClipboard(owner)) return;
    EmptyClipboard();
    const SIZE_T bytes = (text.size() + 1) * sizeof(wchar_t);
    HGLOBAL memory = GlobalAlloc(GMEM_MOVEABLE, bytes);
    if (memory) {
        void* destination = GlobalLock(memory);
        if (destination) {
            memcpy(destination, text.c_str(), bytes);
            GlobalUnlock(memory);
            SetClipboardData(CF_UNICODETEXT, memory);
        } else {
            GlobalFree(memory);
        }
    }
    CloseClipboard();
}

void addTrayIcon(HWND hwnd) {
    NOTIFYICONDATAW data{};
    data.cbSize = sizeof(data);
    data.hWnd = hwnd;
    data.uID = kTrayId;
    data.uFlags = NIF_MESSAGE | NIF_ICON | NIF_TIP;
    data.uCallbackMessage = kTrayMessage;
    // Ikon tray memakai resource XyDesk yang sama dengan jendela.
    data.hIcon = LoadIconW(GetModuleHandleW(nullptr), MAKEINTRESOURCEW(IDI_XYDESK));
    if (!data.hIcon) data.hIcon = LoadIconW(nullptr, IDI_APPLICATION);
    lstrcpynW(data.szTip, L"XyDesk Host — klik kanan untuk kontrol", ARRAYSIZE(data.szTip));
    if (Shell_NotifyIconW(NIM_ADD, &data)) {
        data.uVersion = NOTIFYICON_VERSION_4;
        Shell_NotifyIconW(NIM_SETVERSION, &data);
    }
}

void removeTrayIcon() {
    if (!g.window) return;
    NOTIFYICONDATAW data{};
    data.cbSize = sizeof(data);
    data.hWnd = g.window;
    data.uID = kTrayId;
    Shell_NotifyIconW(NIM_DELETE, &data);
}

void openPanel(HWND hwnd) {
    ShowWindow(hwnd, SW_SHOW);
    ShowWindow(hwnd, SW_RESTORE);
    SetForegroundWindow(hwnd);
    renderPanel();
}

void hidePanel(HWND hwnd) {
    ShowWindow(hwnd, SW_HIDE);
}

// ── Menu tray custom ─────────────────────────────────────────────────────────
// Menggantikan TrackPopupMenu bawaan Windows: daftar aksi digambar sendiri
// dengan gaya panel (kartu membulat, sorot baris), hilang saat kehilangan fokus.
namespace traymenu {
struct Row { UINT id; const wchar_t* label; bool enabled = true; bool strong = false; };
struct State {
    HWND owner = nullptr;
    std::vector<Row> rows;
    int hover = -1;
    Rect itemRect(int i, const Rect& box) const {
        return Rect{box.x, box.y + 8 + i * 34, box.w, 34};
    }
};
State g_state;

void paint(HWND hwnd) {
    State& s = g_state;
    PAINTSTRUCT ps{};
    HDC dc = BeginPaint(hwnd, &ps);
    RECT rc{}; GetClientRect(hwnd, &rc);
    const Rect box{0, 0, rc.right, rc.bottom};
    fillRoundedHdc(dc, box, xydesk::panel::scaled(14, g.layout.scalePct), kSurface);
    strokeRoundedHdc(dc, box, xydesk::panel::scaled(14, g.layout.scalePct), kEdge, 1);
    for (int i = 0; i < static_cast<int>(s.rows.size()); ++i) {
        const Row& row = s.rows[static_cast<size_t>(i)];
        if (row.id == 0) {
            const Rect line{box.x + 12, s.itemRect(i, box).y + 17, box.w - 24, 1};
            fillRoundedHdc(dc, line, 1, kEdge);
            continue;
        }
        const Rect r = s.itemRect(i, box).inset(4);
        if (i == s.hover && row.enabled) {
            fillRoundedHdc(dc, r, xydesk::panel::scaled(8, g.layout.scalePct), kSurface2);
        }
        drawTextLine(dc, row.label, r.inset(10), row.strong ? g.fontSemi : g.fontBody,
            row.enabled ? (row.strong ? kAccent : kText) : kDisabled,
            DT_LEFT | DT_VCENTER | DT_SINGLELINE);
    }
    EndPaint(hwnd, &ps);
}

int hitRow(HWND hwnd, LPARAM lParam) {
    State& s = g_state;
    POINT pt{static_cast<short>(LOWORD(lParam)), static_cast<short>(HIWORD(lParam))};
    RECT rc{}; GetClientRect(hwnd, &rc);
    const Rect box{0, 0, rc.right, rc.bottom};
    for (int i = 0; i < static_cast<int>(s.rows.size()); ++i) {
        if (s.rows[static_cast<size_t>(i)].id == 0) continue;
        const Rect r = s.itemRect(i, box);
        if (pt.y >= r.y && pt.y < r.bottom()) return i;
    }
    return -1;
}

LRESULT CALLBACK menuProc(HWND hwnd, UINT message, WPARAM wParam, LPARAM lParam) {
    State& s = g_state;
    switch (message) {
    case WM_PAINT: paint(hwnd); return 0;
    case WM_ERASEBKGND: return 1;
    case WM_MOUSEMOVE: {
        const int hit = hitRow(hwnd, lParam);
        if (hit != s.hover) {
            s.hover = hit;
            InvalidateRect(hwnd, nullptr, FALSE);
        }
        return 0;
    }
    case WM_LBUTTONDOWN: {
        const int hit = hitRow(hwnd, lParam);
        if (hit >= 0 && s.rows[static_cast<size_t>(hit)].enabled) {
            const UINT id = s.rows[static_cast<size_t>(hit)].id;
            DestroyWindow(hwnd);
            if (s.owner) PostMessageW(s.owner, WM_COMMAND, id, 0);
        } else {
            DestroyWindow(hwnd);
        }
        return 0;
    }
    case WM_KEYDOWN:
        if (wParam == VK_ESCAPE) { DestroyWindow(hwnd); return 0; }
        if (wParam == VK_RETURN) {
            if (s.hover >= 0 && s.rows[static_cast<size_t>(s.hover)].enabled) {
                const UINT id = s.rows[static_cast<size_t>(s.hover)].id;
                DestroyWindow(hwnd);
                if (s.owner) PostMessageW(s.owner, WM_COMMAND, id, 0);
            }
            return 0;
        }
        if (wParam == VK_UP || wParam == VK_DOWN) {
            const int delta = wParam == VK_DOWN ? 1 : -1;
            int next = s.hover;
            for (int step = 0; step < 8; ++step) {
                next += delta;
                if (next < 0 || next >= static_cast<int>(s.rows.size())) break;
                if (s.rows[static_cast<size_t>(next)].id != 0) { s.hover = next; break; }
            }
            InvalidateRect(hwnd, nullptr, FALSE);
            return 0;
        }
        break;
    case WM_ACTIVATE:
        if (LOWORD(wParam) == WA_INACTIVE) DestroyWindow(hwnd);
        return 0;
    case WM_CLOSE:
        DestroyWindow(hwnd);
        return 0;
    }
    return DefWindowProcW(hwnd, message, wParam, lParam);
}
} // namespace traymenu

void showTrayMenu(HWND hwnd) {
    using namespace traymenu;
    State& s = g_state;
    s = State{};
    s.owner = hwnd;
    s.rows = {
        {kTrayOpen, L"Buka Control Panel", true, true},
        {kTrayStart, L"Mulai host", !g.running},
        {kTrayStop, L"Hentikan host", g.running},
        {kTrayRestart, L"Restart host"},
        {kTrayWeb, L"Buka XyDesk Web"},
        {kTrayUpdate, updateState.busy.load() ? L"Memeriksa pembaruan\u2026" : L"Cek pembaruan",
            !updateState.busy.load()},
        {0, nullptr},
        {kTrayQuit, L"Keluar XyDesk"},
    };

    WNDCLASSEXW wc{};
    wc.cbSize = sizeof(wc);
    wc.lpfnWndProc = menuProc;
    wc.hInstance = GetModuleHandleW(nullptr);
    wc.hCursor = LoadCursorW(nullptr, IDC_ARROW);
    wc.lpszClassName = L"XyDeskTrayMenu";
    RegisterClassExW(&wc); // boleh gagal bila kelas sudah terdaftar

    const int rowH = 34;
    const int w = xydesk::panel::scaled(250, g.layout.scalePct);
    const int h = 16 + rowH * static_cast<int>(s.rows.size());
    POINT point{};
    if (!GetCursorPos(&point)) point = POINT{0, 0};
    HWND popup = CreateWindowExW(WS_EX_TOOLWINDOW | WS_EX_TOPMOST, L"XyDeskTrayMenu", L"",
        WS_POPUP, point.x, point.y, w, h, nullptr, nullptr,
        GetModuleHandleW(nullptr), nullptr);
    if (!popup) return;
    SetWindowRgn(popup, CreateRoundRectRgn(0, 0, w + 1, h + 1,
        xydesk::panel::scaled(14, g.layout.scalePct),
        xydesk::panel::scaled(14, g.layout.scalePct)), TRUE);
    SetForegroundWindow(hwnd);
    ShowWindow(popup, SW_SHOWNOACTIVATE);
    UpdateWindow(popup);
    MSG msg;
    while (IsWindow(popup) && GetMessageW(&msg, nullptr, 0, 0) > 0) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }
    PostMessageW(hwnd, WM_NULL, 0, 0);
}

struct SettingsState {
    bool readyShown=false;
    std::future<std::string> pending;
    unsigned pid=0;
    int requestedBitrate=-1;
};

// Keep native button semantics/commands; only replace their painting.
bool paintEmbeddedButton(LPARAM parameter){
    const auto item=reinterpret_cast<DRAWITEMSTRUCT*>(parameter);
    if(!item||item->CtlType!=ODT_BUTTON)return false;
    const bool disabled=(item->itemState&ODS_DISABLED)!=0,pressed=(item->itemState&ODS_SELECTED)!=0;
    const bool primary=item->CtlID==IDC_APPLY_BITRATE||item->CtlID==IDC_ACCOUNT_LOGIN;
    const COLORREF fill=disabled?kSurface2:primary?(pressed?kAccentPressed:kAccent):(pressed?kSurfacePressed:kSurface2);
    const auto dc=item->hDC;RECT rect=item->rcItem;FillRect(dc,&rect,static_cast<HBRUSH>(GetStockObject(WHITE_BRUSH)));
    HBRUSH brush=CreateSolidBrush(fill);HPEN pen=CreatePen(PS_SOLID,1,(item->itemState&ODS_FOCUS)?kAccent:fill);
    const auto oldBrush=SelectObject(dc,brush),oldPen=SelectObject(dc,pen);
    const int radius=xydesk::panel::scaled(16,g.layout.scalePct);
    RoundRect(dc,rect.left,rect.top,rect.right,rect.bottom,radius,radius);
    SelectObject(dc,oldBrush);SelectObject(dc,oldPen);DeleteObject(brush);DeleteObject(pen);
    wchar_t text[256]{};GetWindowTextW(item->hwndItem,text,256);
    const auto oldFont=SelectObject(dc,reinterpret_cast<HFONT>(SendMessageW(item->hwndItem,WM_GETFONT,0,0)));
    SetBkMode(dc,TRANSPARENT);SetTextColor(dc,disabled?kDisabled:primary?kOnAccent:kText);
    InflateRect(&rect,-6,-2);DrawTextW(dc,text,-1,&rect,DT_CENTER|DT_VCENTER|DT_SINGLELINE|DT_END_ELLIPSIS);
    SelectObject(dc,oldFont);return true;
}

// ── Chrome dialog tanpa bingkai Windows ──────────────────────────────────────
// Dialog resource dipakai TANPA caption bawaan: bar judul + tombol tutup
// digambar sendiri, sudut membulat, area judul bisa digeser. Isi dialog tetap
// dari resource (tombol sudah owner-drawn) — hanya kerangka Windows-nya yang
// dihapus. Dialog yang dipasang sebagai halaman panel (WS_CHILD) tidak diubah.
namespace dialogchrome {
constexpr int kTitle = 34;

RECT closeRect(HWND hwnd) {
    const auto px = [](int n) { return xydesk::panel::scaled(n, g.layout.scalePct); };
    RECT rc{}; GetClientRect(hwnd, &rc);
    return RECT{rc.right - px(46), px(4), rc.right - px(10), px(4) + px(26)};
}

bool chromeOn(HWND hwnd) {
    return GetPropW(hwnd, L"XyDeskDialogChrome") != nullptr
        && (GetWindowLongPtrW(hwnd, GWL_STYLE) & WS_CHILD) == 0;
}

void apply(HWND hwnd, const wchar_t* title) {
    if (GetWindowLongPtrW(hwnd, GWL_STYLE) & WS_CHILD) return;
    const auto px = [](int n) { return xydesk::panel::scaled(n, g.layout.scalePct); };
    RECT rc{}; GetWindowRect(hwnd, &rc);
    const int strip = px(kTitle);
    SetWindowPos(hwnd, nullptr, 0, 0, rc.right - rc.left, rc.bottom - rc.top + strip,
        SWP_NOMOVE | SWP_NOZORDER | SWP_NOACTIVATE);
    for (HWND child = GetWindow(hwnd, GW_CHILD); child; child = GetWindow(child, GW_HWNDNEXT)) {
        RECT cr{}; GetWindowRect(child, &cr);
        MapWindowPoints(nullptr, hwnd, reinterpret_cast<POINT*>(&cr), 2);
        SetWindowPos(child, nullptr, cr.left, cr.top + strip, 0, 0,
            SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE);
    }
    RECT rc2{}; GetClientRect(hwnd, &rc2);
    SetWindowRgn(hwnd, CreateRoundRectRgn(0, 0, rc2.right + 1, rc2.bottom + 1, px(18), px(18)), TRUE);
    SetPropW(hwnd, L"XyDeskDialogChrome", reinterpret_cast<HANDLE>(const_cast<wchar_t*>(title)));
    RedrawWindow(hwnd, nullptr, nullptr, RDW_INVALIDATE);
}

bool paintTitle(HWND hwnd) {
    if (!chromeOn(hwnd)) return false;
    const auto px = [](int n) { return xydesk::panel::scaled(n, g.layout.scalePct); };
    PAINTSTRUCT ps{};
    HDC dc = BeginPaint(hwnd, &ps);
    RECT rc{}; GetClientRect(hwnd, &rc);
    const wchar_t* title = static_cast<const wchar_t*>(GetPropW(hwnd, L"XyDeskDialogChrome"));
    drawTextLine(dc, title ? title : L"", Rect{px(18), 0, rc.right - px(80), px(kTitle)},
        g.fontSemi, kText, DT_LEFT | DT_VCENTER | DT_SINGLELINE);
    const RECT cr = closeRect(hwnd);
    const Rect pill{cr.left, cr.top, cr.right - cr.left, cr.bottom - cr.top};
    fillRoundedHdc(dc, pill, pill.h / 2, kSurface2);
    strokeRoundedHdc(dc, pill, pill.h / 2, kEdge, 1);
    drawTextCentered(dc, L"\uE711", pill, g.fontIcons, kMuted);
    EndPaint(hwnd, &ps);
    return true;
}

bool hitClose(HWND hwnd, LPARAM lParam) {
    if (!chromeOn(hwnd)) return false;
    POINT pt{static_cast<short>(LOWORD(lParam)), static_cast<short>(HIWORD(lParam))};
    ScreenToClient(hwnd, &pt);
    const RECT cr = closeRect(hwnd);
    return PtInRect(&cr, pt) == TRUE;
}

bool hitTitleBar(HWND hwnd, LPARAM lParam) {
    if (!chromeOn(hwnd)) return false;
    POINT pt{static_cast<short>(LOWORD(lParam)), static_cast<short>(HIWORD(lParam))};
    ScreenToClient(hwnd, &pt);
    RECT rc{}; GetClientRect(hwnd, &rc);
    return pt.y >= 0 && pt.y < xydesk::panel::scaled(kTitle, g.layout.scalePct) && !hitClose(hwnd, lParam);
}
} // namespace dialogchrome

INT_PTR CALLBACK settingsDialog(HWND hwnd,UINT message,WPARAM wParam,LPARAM lParam){
    if(message==WM_PAINT&&dialogchrome::paintTitle(hwnd))return TRUE;
    if(message==WM_NCHITTEST){
        if(dialogchrome::hitTitleBar(hwnd,lParam)){SetWindowLongPtrW(hwnd,DWLP_MSGRESULT,HTCAPTION);return TRUE;}
        return FALSE;
    }
    if(message==WM_LBUTTONDOWN&&dialogchrome::hitClose(hwnd,lParam)){EndDialog(hwnd,0);return TRUE;}
    if(message==WM_DRAWITEM){
        auto di=reinterpret_cast<DRAWITEMSTRUCT*>(lParam);
        if(di->CtlID>=IDC_RATE_0&&di->CtlID<=IDC_RATE_7){
            // Pil bitrate: dipilih menonjol aksen, sisanya tenang. Klik langsung
            // menerapkan — tanpa kotak turun bawaan Windows.
            constexpr int rateVals[]={0,1,2,4,8,15,25,50};
            const bool disabled=(di->itemState&ODS_DISABLED)!=0;
            const bool sel=!disabled&&g.panelBitrate==rateVals[di->CtlID-IDC_RATE_0];
            const Rect r{di->rcItem.left,di->rcItem.top,
                di->rcItem.right-di->rcItem.left,di->rcItem.bottom-di->rcItem.top};
            fillRoundedHdc(di->hDC,r,r.h/2,sel?kAccent:(disabled?kDisabled:kSurface2));
            strokeRoundedHdc(di->hDC,r,r.h/2,sel?kAccent:kEdge,1);
            wchar_t label[16]{};GetWindowTextW(di->hwndItem,label,16);
            drawTextCentered(di->hDC,label,r,g.fontSemi,sel?kOnAccent:(disabled?kDisabled:kText));
            return TRUE;
        }
    }
    if(message==WM_DRAWITEM&&paintEmbeddedButton(lParam))return TRUE;
    if(message==WM_CTLCOLORDLG)return reinterpret_cast<INT_PTR>(GetStockObject(WHITE_BRUSH));
    if(message==WM_CTLCOLORSTATIC){SetBkColor(reinterpret_cast<HDC>(wParam),RGB(255,255,255));SetTextColor(reinterpret_cast<HDC>(wParam),RGB(32,35,40));return reinterpret_cast<INT_PTR>(GetStockObject(WHITE_BRUSH));}

    auto state=reinterpret_cast<SettingsState*>(GetWindowLongPtrW(hwnd,DWLP_USER));
    if(message==WM_INITDIALOG){
        state=reinterpret_cast<SettingsState*>(lParam);SetWindowLongPtrW(hwnd,DWLP_USER,lParam);
        dialogchrome::apply(hwnd,L"Pengaturan host");
        SetTimer(hwnd,1,100,nullptr);return TRUE;
    }
    if(!state)return FALSE;
    if(message==WM_TIMER){
        controlChannel.poll(g.process);
        if(state->pending.valid()&&state->pending.wait_for(std::chrono::milliseconds(0))==std::future_status::ready){
            std::string response;try{response=state->pending.get();}catch(...){}
            const auto object=xydesk::engine_json::parse(response);
            bool ok=false;
            if(object){auto it=object->find("ok");if(it!=object->end()){auto value=std::get_if<bool>(&it->second);ok=value&&*value;}}
            ok=ok&&g.process&&GetProcessId(g.process)==state->pid;
            if(ok){
                auto password=jsonString(response,"password");
                if(!password.empty()){g.pairingCode=std::move(password);renderPanel();}
                if(state->requestedBitrate>=0)g.panelBitrate=state->requestedBitrate;
            }
            if(!response.empty())SecureZeroMemory(response.data(),response.size());
            SetDlgItemTextW(hwnd,IDC_SETTING_STATUS,ok?L"Bitrate diterapkan. Klien dapat mengubah target ini.":L"Gagal atau timeout. Periksa host sebelum mencoba lagi.");
            state->requestedBitrate=-1;
        }
        const bool ready=controlChannel.endpoint.has_value()&&g.running;
        const bool busy=state->pending.valid();
        for(int id=IDC_RATE_0;id<=IDC_RATE_7;++id)EnableWindow(GetDlgItem(hwnd,id),ready&&!busy);
        if(!ready&&!busy){state->readyShown=false;SetDlgItemTextW(hwnd,IDC_SETTING_STATUS,L"Kontrol privat belum tersedia. Mulai/restart engine dari panel ini.");}
        else if(ready&&!busy&&!state->readyShown){state->readyShown=true;SetDlgItemTextW(hwnd,IDC_SETTING_STATUS,L"Kanal privat siap. Pilih target bitrate yang diinginkan.");}
        return TRUE;
    }
    if(message==WM_CLOSE||(message==WM_COMMAND&&LOWORD(wParam)==IDCANCEL)){
        if(GetWindowLongPtrW(hwnd,GWL_STYLE)&WS_CHILD){goPage(g.window,Page::Status);return TRUE;}
        if(state->pending.valid()){SetDlgItemTextW(hwnd,IDC_SETTING_STATUS,L"Tunggu hasil permintaan sebelum menutup.");return TRUE;}
        KillTimer(hwnd,1);EndDialog(hwnd,0);return TRUE;
    }
    if(message!=WM_COMMAND||state->pending.valid()||!controlChannel.endpoint)return FALSE;
    const int id=LOWORD(wParam);std::string body;
    state->requestedBitrate=-1;
    if(id>=IDC_RATE_0&&id<=IDC_RATE_7){
        constexpr int rateVals[]={0,1,2,4,8,15,25,50};
        state->requestedBitrate=rateVals[id-IDC_RATE_0];
        body="{\"action\":\"video-bitrate\",\"bitrate_mbps\":"+std::to_string(state->requestedBitrate)+"}";
        InvalidateRect(hwnd,nullptr,TRUE);
    }else return FALSE;
    const auto endpoint=*controlChannel.endpoint;state->pid=endpoint.pid;state->readyShown=true;
    try{state->pending=std::async(std::launch::async,[endpoint,body=std::move(body)]()mutable{return xydesk::panel_control::action(endpoint,std::move(body));});SetDlgItemTextW(hwnd,IDC_SETTING_STATUS,L"Menerapkan melalui kanal privat...");}
    catch(...){SetDlgItemTextW(hwnd,IDC_SETTING_STATUS,L"Worker pengaturan tidak dapat dimulai.");}
    return TRUE;
}

struct AccountDialogState {
    std::atomic_bool cancelled{false};
    std::future<xydesk::account::Result> pending;
    xydesk::account::Result profile;
    bool closing=false;
};
INT_PTR CALLBACK accountDialog(HWND hwnd,UINT message,WPARAM wParam,LPARAM lParam){
    if(message==WM_PAINT&&dialogchrome::paintTitle(hwnd))return TRUE;
    if(message==WM_NCHITTEST){
        if(dialogchrome::hitTitleBar(hwnd,lParam)){SetWindowLongPtrW(hwnd,DWLP_MSGRESULT,HTCAPTION);return TRUE;}
        return FALSE;
    }
    if(message==WM_LBUTTONDOWN&&dialogchrome::hitClose(hwnd,lParam)){EndDialog(hwnd,0);return TRUE;}
    if(message==WM_DRAWITEM&&paintEmbeddedButton(lParam))return TRUE;
    if(message==WM_CTLCOLORDLG)return reinterpret_cast<INT_PTR>(GetStockObject(WHITE_BRUSH));
    if(message==WM_CTLCOLORSTATIC){SetBkColor(reinterpret_cast<HDC>(wParam),RGB(255,255,255));SetTextColor(reinterpret_cast<HDC>(wParam),RGB(32,35,40));return reinterpret_cast<INT_PTR>(GetStockObject(WHITE_BRUSH));}

    auto state=reinterpret_cast<AccountDialogState*>(GetWindowLongPtrW(hwnd,DWLP_USER));
    if(message==WM_INITDIALOG){
        state=reinterpret_cast<AccountDialogState*>(lParam);SetWindowLongPtrW(hwnd,DWLP_USER,lParam);
        dialogchrome::apply(hwnd,L"Profil dan akun");
        SendDlgItemMessageW(hwnd,IDC_ACCOUNT_NAME,WM_SETFONT,reinterpret_cast<WPARAM>(g.fontTitle),TRUE);
        wchar_t user[256]{};DWORD size=256;GetUserNameW(user,&size);
        const auto local=L"Windows: "+std::wstring(user)+L"\nDevice ID: "+g.deviceId;
        SetDlgItemTextW(hwnd,IDC_ACCOUNT_LOCAL,local.c_str());
        try{if(!workspaceProbe)state->pending=std::async(std::launch::async,xydesk::account::restore);else{SetDlgItemTextW(hwnd,IDC_ACCOUNT_NAME,L"Belum masuk akun");SetDlgItemTextW(hwnd,IDC_ACCOUNT_STATUS,L"Pratinjau UI offline. Tidak ada sesi akun yang dibaca.");}}catch(...){SetDlgItemTextW(hwnd,IDC_ACCOUNT_STATUS,L"Pemeriksa sesi tidak tersedia.");}
        SetTimer(hwnd,1,100,nullptr);return TRUE;
    }
    if(!state)return FALSE;
    if(message==WM_TIMER){
        if(state->pending.valid()&&state->pending.wait_for(std::chrono::milliseconds(0))==std::future_status::ready){
            try{state->profile=state->pending.get();}catch(...){state->profile={false,{},{},L"Login gagal. Periksa koneksi lalu coba lagi."};}
            if(state->closing){KillTimer(hwnd,1);EndDialog(hwnd,0);return TRUE;}
            SetDlgItemTextW(hwnd,IDC_ACCOUNT_NAME,state->profile.ok?state->profile.name.c_str():L"Belum masuk akun");
            SetDlgItemTextW(hwnd,IDC_ACCOUNT_EMAIL,state->profile.email.c_str());
            SetDlgItemTextW(hwnd,IDC_ACCOUNT_STATUS,state->profile.message.c_str());
        }
        const bool busy=state->pending.valid();
        EnableWindow(GetDlgItem(hwnd,IDC_ACCOUNT_LOGIN),!busy);EnableWindow(GetDlgItem(hwnd,IDC_ACCOUNT_LOGOUT),!busy);
        return TRUE;
    }
    if(message==WM_CLOSE||(message==WM_COMMAND&&LOWORD(wParam)==IDCANCEL)){
        if(GetWindowLongPtrW(hwnd,GWL_STYLE)&WS_CHILD){state->cancelled=true;goPage(g.window,Page::Status);return TRUE;}
        if(state->pending.valid()){state->cancelled=true;state->closing=true;SetDlgItemTextW(hwnd,IDC_ACCOUNT_STATUS,L"Membatalkan login dengan aman...");return TRUE;}
        KillTimer(hwnd,1);EndDialog(hwnd,0);return TRUE;
    }
    if(message==WM_COMMAND&&!state->pending.valid()){
        if(LOWORD(wParam)==IDC_ACCOUNT_LOGOUT){
            if(!xydesk::account::signOut()){SetDlgItemTextW(hwnd,IDC_ACCOUNT_STATUS,L"Sesi belum berhasil dihapus dari penyimpanan Windows.");return TRUE;}state->profile={};
            // Keluar akun menutup panel kembali ke layar masuk: tanpa ini
            // ID dan kode pairing tetap terbaca setelah sesi dihapus.
            g.gateAccount.clear();g.gateEmail=false;g.gateForm.reset();syncGateEdit();g.gate.signOut();if(g.window){beginGateMotion(g.window,xydesk::onboarding::kStageMs);renderPanel();}
            SetDlgItemTextW(hwnd,IDC_ACCOUNT_NAME,L"Belum masuk akun");SetDlgItemTextW(hwnd,IDC_ACCOUNT_EMAIL,L"");
            SetDlgItemTextW(hwnd,IDC_ACCOUNT_STATUS,L"Sesi aplikasi dihapus. Login browser tidak ikut dikeluarkan.");return TRUE;
        }
        if(LOWORD(wParam)==IDC_ACCOUNT_LOGIN){
            state->cancelled=false;
            try{state->pending=std::async(std::launch::async,[state]{return xydesk::account::login(state->cancelled);});SetDlgItemTextW(hwnd,IDC_ACCOUNT_STATUS,L"Selesaikan login di browser. Jangan bagikan kode atau URL callback.");}
            catch(...){SetDlgItemTextW(hwnd,IDC_ACCOUNT_STATUS,L"Login belum dapat dimulai.");}return TRUE;
        }
    }
    return FALSE;
}
INT_PTR CALLBACK guideDialog(HWND hwnd,UINT message,WPARAM wParam,LPARAM lParam){
    if(message==WM_PAINT&&dialogchrome::paintTitle(hwnd))return TRUE;
    if(message==WM_NCHITTEST){
        if(dialogchrome::hitTitleBar(hwnd,lParam)){SetWindowLongPtrW(hwnd,DWLP_MSGRESULT,HTCAPTION);return TRUE;}
        return FALSE;
    }
    if(message==WM_LBUTTONDOWN&&dialogchrome::hitClose(hwnd,lParam)){EndDialog(hwnd,0);return TRUE;}
    if(message==WM_INITDIALOG){dialogchrome::apply(hwnd,L"Panduan XyDesk");return TRUE;}
    if(message==WM_DRAWITEM&&paintEmbeddedButton(lParam))return TRUE;
    if(message==WM_CTLCOLORDLG)return reinterpret_cast<INT_PTR>(GetStockObject(WHITE_BRUSH));
    if(message==WM_CTLCOLORSTATIC){SetBkColor(reinterpret_cast<HDC>(wParam),RGB(255,255,255));SetTextColor(reinterpret_cast<HDC>(wParam),RGB(32,35,40));return reinterpret_cast<INT_PTR>(GetStockObject(WHITE_BRUSH));}

    if(message==WM_CLOSE||(message==WM_COMMAND&&LOWORD(wParam)==IDCANCEL)){if(GetWindowLongPtrW(hwnd,GWL_STYLE)&WS_CHILD)goPage(g.window,Page::Status);else EndDialog(hwnd,0);return TRUE;}return FALSE;
}
struct QrDialogState {qrcodegen::QrCode code;std::wstring link;};
INT_PTR CALLBACK qrDialog(HWND hwnd,UINT message,WPARAM wParam,LPARAM lParam){
    auto state=reinterpret_cast<QrDialogState*>(GetWindowLongPtrW(hwnd,DWLP_USER));
    if(message==WM_PAINT&&dialogchrome::paintTitle(hwnd))return TRUE;
    if(message==WM_NCHITTEST){
        if(dialogchrome::hitTitleBar(hwnd,lParam)){SetWindowLongPtrW(hwnd,DWLP_MSGRESULT,HTCAPTION);return TRUE;}
        return FALSE;
    }
    if(message==WM_LBUTTONDOWN&&dialogchrome::hitClose(hwnd,lParam)){EndDialog(hwnd,0);return TRUE;}
    if(message==WM_INITDIALOG){state=reinterpret_cast<QrDialogState*>(lParam);SetWindowLongPtrW(hwnd,DWLP_USER,lParam);dialogchrome::apply(hwnd,L"QR koneksi");SetDlgItemTextW(hwnd,IDC_CONNECTION_LINK,state->link.c_str());return TRUE;}
    if(message==WM_DRAWITEM&&wParam==IDC_CONNECTION_QR&&state){
        auto draw=reinterpret_cast<DRAWITEMSTRUCT*>(lParam);FillRect(draw->hDC,&draw->rcItem,static_cast<HBRUSH>(GetStockObject(WHITE_BRUSH)));
        const int modules=state->code.getSize(),size=modules+8;
        const int cell=std::max(1,static_cast<int>(std::min(draw->rcItem.right-draw->rcItem.left,draw->rcItem.bottom-draw->rcItem.top))/size);
        const int left=draw->rcItem.left+((draw->rcItem.right-draw->rcItem.left)-size*cell)/2;
        const int top=draw->rcItem.top+((draw->rcItem.bottom-draw->rcItem.top)-size*cell)/2;
        for(int y=0;y<modules;++y)for(int x=0;x<modules;++x)if(state->code.getModule(x,y)){RECT rect{left+(x+4)*cell,top+(y+4)*cell,left+(x+5)*cell,top+(y+5)*cell};FillRect(draw->hDC,&rect,static_cast<HBRUSH>(GetStockObject(BLACK_BRUSH)));}return TRUE;
    }
    if(message==WM_CLOSE||(message==WM_COMMAND&&LOWORD(wParam)==IDCANCEL)){EndDialog(hwnd,0);return TRUE;}return FALSE;
}
SettingsState embeddedSettings;
AccountDialogState embeddedAccount;
std::map<Page,HWND> embeddedPages;
struct EmbeddedChildLayout {
    HWND window=nullptr;
    RECT base{};
    LOGFONTW font{};
    HFONT ownedFont=nullptr;
    bool combo=false;
};
struct EmbeddedLayout {
    int width=0,height=0;
    std::vector<EmbeddedChildLayout> children;
};
std::map<HWND,EmbeddedLayout> embeddedLayouts;
void layoutEmbeddedChildren(HWND page,int width,int height){
    auto& layout=embeddedLayouts[page];
    if(layout.children.empty()){
        RECT base{};GetClientRect(page,&base);layout.width=std::max(1,static_cast<int>(base.right));
        for(HWND child=GetWindow(page,GW_CHILD);child;child=GetWindow(child,GW_HWNDNEXT)){
            if(GetDlgCtrlID(child)==IDCANCEL)continue;
            EmbeddedChildLayout entry;entry.window=child;GetWindowRect(child,&entry.base);
            MapWindowPoints(nullptr,page,reinterpret_cast<POINT*>(&entry.base),2);
            layout.height=std::max(layout.height,static_cast<int>(entry.base.bottom)+8);
            auto font=reinterpret_cast<HFONT>(SendMessageW(child,WM_GETFONT,0,0));
            if(!font)font=reinterpret_cast<HFONT>(SendMessageW(page,WM_GETFONT,0,0));
            GetObjectW(font,sizeof(entry.font),&entry.font);
            wchar_t kind[32]{};GetClassNameW(child,kind,32);entry.combo=_wcsicmp(kind,L"ComboBox")==0;
            if(_wcsicmp(kind,L"Button")==0){
                const auto style=GetWindowLongPtrW(child,GWL_STYLE),type=style&BS_TYPEMASK;
                if(type==BS_PUSHBUTTON||type==BS_DEFPUSHBUTTON)SetWindowLongPtrW(child,GWL_STYLE,(style&~BS_TYPEMASK)|BS_OWNERDRAW);
            }
            layout.children.push_back(entry);
        }
    }
    const double factor=std::min(static_cast<double>(width)/layout.width,static_cast<double>(height)/std::max(1,layout.height));
    const auto scale=[factor](int n){return static_cast<int>(std::lround(n*factor));};
    for(auto& entry:layout.children){
        const auto& rect=entry.base;
        int controlHeight=scale(rect.bottom-rect.top);
        if(entry.combo){RECT dropdown{0,0,0,94};MapDialogRect(page,&dropdown);controlHeight=std::max(controlHeight,scale(dropdown.bottom));}
        SetWindowPos(entry.window,nullptr,scale(rect.left),scale(rect.top),std::max(1,scale(rect.right-rect.left)),std::max(1,controlHeight),SWP_NOZORDER|SWP_NOACTIVATE);
        auto font=entry.font;font.lfHeight=scale(entry.font.lfHeight);
        if(GetDlgCtrlID(entry.window)==IDC_ACCOUNT_NAME)font.lfWeight=FW_SEMIBOLD;
        const auto replacement=CreateFontIndirectW(&font);
        if(replacement){SendMessageW(entry.window,WM_SETFONT,reinterpret_cast<WPARAM>(replacement),TRUE);if(entry.ownedFont)DeleteObject(entry.ownedFont);entry.ownedFont=replacement;}
    }
}

void syncEmbeddedPage(){
    if(!g.window)return;
    for(const auto& entry:embeddedPages)if(entry.first!=g.page)ShowWindow(entry.second,SW_HIDE);
    int resource=0;DLGPROC proc=nullptr;LPARAM state=0;
    switch(g.page){
    case Page::Settings:resource=IDD_HOST_SETTINGS;proc=settingsDialog;state=reinterpret_cast<LPARAM>(&embeddedSettings);break;
    case Page::Account:resource=IDD_ACCOUNT;proc=accountDialog;state=reinterpret_cast<LPARAM>(&embeddedAccount);break;
    case Page::Help:resource=IDD_GUIDE;proc=guideDialog;break;
    default:return;
    }
    auto& page=embeddedPages[g.page];
    if(!page){
        page=CreateDialogParamW(GetModuleHandleW(nullptr),MAKEINTRESOURCEW(resource),g.window,proc,state);if(!page)return;
        ShowWindow(page,SW_HIDE);SetParent(page,g.window);
        SetWindowLongPtrW(page,GWL_STYLE,WS_CHILD|WS_CLIPSIBLINGS|DS_CONTROL|DS_SETFONT);
        SetWindowLongPtrW(page,GWL_EXSTYLE,WS_EX_CONTROLPARENT);
        ShowWindow(GetDlgItem(page,IDCANCEL),SW_HIDE);
    }
    const Rect card=embeddedCard(g.layout);const int pad=xydesk::panel::scaled(16,g.layout.scalePct);
    const int width=card.w-2*pad,height=card.h-2*pad;
    layoutEmbeddedChildren(page,width,height);
    SetWindowPos(page,nullptr,card.x+pad,card.y+pad,width,height,SWP_NOZORDER|SWP_FRAMECHANGED|SWP_SHOWWINDOW);
}
bool routeEmbeddedMessage(MSG& message){
    auto it=embeddedPages.find(g.page);return it!=embeddedPages.end()&&IsWindowVisible(it->second)&&IsDialogMessageW(it->second,&message);
}

void showProfile(HWND hwnd){
    AccountDialogState state;DialogBoxParamW(GetModuleHandleW(nullptr),MAKEINTRESOURCEW(IDD_ACCOUNT),hwnd,accountDialog,reinterpret_cast<LPARAM>(&state));
}

void activateTarget(HWND hwnd, Target target) {
    switch (target) {
    case Target::ToggleSidebar:
        g.sidebarCollapsed=!g.sidebarCollapsed;
        g.layout=xydesk::panel::computeLayout(g.layout.scalePct*96/100,g.unitsW,g.unitsH,g.sidebarCollapsed);
        g.pillY=sidebarItemY(g.layout,g.page);syncEmbeddedPage();renderPanel();break;
    case Target::Settings: {
        goPage(hwnd,Page::Settings);break;
    }
    case Target::Profile: goPage(hwnd,Page::Account);break;
    case Target::Help:
        goPage(hwnd,Page::Help);break;
    case Target::ConnectionQr: {
        const auto link=xydesk::panel_control::deviceLink(g.deviceId);if(link.empty())break;
        try{const std::string text(link.begin(),link.end());QrDialogState state{qrcodegen::QrCode::encodeText(text.c_str(),qrcodegen::QrCode::Ecc::MEDIUM),link};DialogBoxParamW(GetModuleHandleW(nullptr),MAKEINTRESOURCEW(IDD_CONNECTION_QR),hwnd,qrDialog,reinterpret_cast<LPARAM>(&state));}
        catch(...){setFlash(L"QR belum dapat dibuat. Gunakan Salin link.",kWarn);}break;
    }
    case Target::DeviceLink: {
        const auto link=xydesk::panel_control::deviceLink(g.deviceId);if(!link.empty())ShellExecuteW(hwnd,L"open",link.c_str(),nullptr,nullptr,SW_SHOWNORMAL);break;
    }
    case Target::CopyLink:
        copyToClipboard(hwnd,xydesk::panel_control::deviceLink(g.deviceId));setFlash(L"Link ID-only disalin; password tidak disertakan.",kGood);break;
    case Target::CopyId:
        copyToClipboard(hwnd, g.deviceId);
        setFlash(L"Device ID disalin ke clipboard", kGood);
        break;
    case Target::CopyPassword:
        copyToClipboard(hwnd, g.pairingCode);
        setFlash(L"Kode pairing disalin ke clipboard", kGood);
        break;
    case Target::Start:
        startHost();
        break;
    case Target::Stop:
        stopHost();
        break;
    case Target::Restart:
        restartHost();
        break;
    case Target::Web: {
        const auto link=xydesk::panel_control::deviceLink(g.deviceId);
        ShellExecuteW(hwnd,L"open",link.empty()?kWebUrl:link.c_str(),nullptr,nullptr,SW_SHOWNORMAL);break;
    }
    case Target::OpenLog: {
        const std::wstring log = g.logPath.empty() ? hostLogPath() : g.logPath;
        ShellExecuteW(hwnd, L"open", log.c_str(), nullptr, nullptr, SW_SHOWNORMAL);
        break;
    }
    case Target::TogglePairingVisibility:
        g.showPairingCode = !g.showPairingCode;
        renderPanel();
        break;
    case Target::CustomizePassword: {
        if (!controlChannel.endpoint) {
            setFlash(L"Kanal kontrol host belum siap. Mulai atau restart engine dari panel Kontrol.", kWarn);
            break;
        }
        const auto endpoint = *controlChannel.endpoint;
        PanelModalRequest req;
        req.title = L"Customize password";
        req.text =
            L"Password pairing dipakai perangkat lain saat menyambung ke PC ini. "
            L"Minimal 6 karakter. Mengganti password mencabut izin browser yang tersimpan.";
        req.input = true;
        req.inputLabel = L"Password baru";
        req.inputMasked = true;
        req.buttons = {{L"Simpan password", 1}, {L"Buat acak", 2}, {L"Batal", 0}};
        req.onAction = [endpoint](int code, const std::wstring& input, std::wstring& status) {
            std::string body;
            std::wstring typed;
            if (code == 1) {
                if (input.size() < 6) {
                    status = L"Isi password minimal 6 karakter; engine memvalidasi aturannya.";
                    return false;
                }
                const int size = WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS, input.c_str(), -1,
                    nullptr, 0, nullptr, nullptr);
                if (size <= 1) { status = L"Password tidak valid."; return false; }
                std::string text(static_cast<size_t>(size), '\0');
                WideCharToMultiByte(CP_UTF8, WC_ERR_INVALID_CHARS, input.c_str(), -1, text.data(), size,
                    nullptr, nullptr);
                text.resize(static_cast<size_t>(size - 1));
                body = "{\"action\":\"set-password\",\"password\":" + xydesk::panel_control::quoteJson(text) + "}";
                SecureZeroMemory(text.data(), text.size());
                typed = input;
            } else {
                body = "{\"action\":\"new-password\"}";
            }
            try {
                std::string response = xydesk::panel_control::action(endpoint, body);
                const auto object = xydesk::engine_json::parse(response);
                bool ok = false;
                if (object) {
                    auto it = object->find("ok");
                    if (it != object->end()) {
                        auto value = std::get_if<bool>(&it->second);
                        ok = value && *value;
                    }
                }
                auto password = jsonString(response, "password");
                if (!response.empty()) SecureZeroMemory(response.data(), response.size());
                if (!ok) {
                    status = L"Gagal atau timeout. Periksa host sebelum mencoba lagi.";
                    return false;
                }
                if (!password.empty()) g.pairingCode = std::move(password);
                else if (!typed.empty()) g.pairingCode = typed;
                g.showPairingCode = true;
                status = L"Password tersimpan. Akses browser lama dicabut.";
                renderPanel();
                return true;
            } catch (...) {
                status = L"Permintaan ke engine gagal.";
                return false;
            }
        };
        const int result = showPanelModal(hwnd, std::move(req));
        if (result > 0) setFlash(L"Password pairing diperbarui.", kAccent);
        break;
    }
    case Target::ClientConnect: {
        PanelModalRequest req;
        req.title = L"Sambung ke perangkat lain";
        req.text = L"Masukkan kode perangkat 9 digit dari layar host tujuan.";
        req.input = true;
        req.inputLabel = L"Kode perangkat";
        req.inputMasked = false;
        req.inputInitial = readLastClientDevice();
        req.buttons = {{L"Batal", 0}, {L"Sambung", 1}};
        req.onAction = [&](int, const std::wstring& typed, std::wstring& status) {
            std::wstring digits;
            for (const wchar_t c : typed) if (c >= L'0' && c <= L'9') digits.push_back(c);
            if (digits.size() != 9) { status = L"Kode harus 9 digit angka."; return false; }
            saveLastClientDevice(digits);
            openClientSession(hwnd, digits);
            status = L"Menyambungkan lewat XyDesk Web…";
            return true;
        };
        showPanelModal(hwnd, std::move(req));
        break;
    }
    case Target::ClientLast: {
        const auto digits = readLastClientDevice();
        if (digits.empty()) { setFlash(L"Belum ada kode perangkat tersimpan.", kWarn); break; }
        openClientSession(hwnd, digits);
        setFlash(L"Membuka sesi ke perangkat terakhir…", kAccent);
        break;
    }
    case Target::StopSession: {
        if(!controlChannel.endpoint){setFlash(L"Kanal kontrol host belum siap.",kWarn);break;}
        if(sessionActionPending.valid()){setFlash(L"Permintaan putus sesi masih diproses.",kWarn);break;}
        const auto endpoint=*controlChannel.endpoint;
        try{
            sessionActionPending=std::async(std::launch::async,[endpoint]{return xydesk::panel_control::action(endpoint,"{\"action\":\"stop-session\"}");});
            setFlash(L"Memutus sesi aktif…",kAccent);
        }catch(...){setFlash(L"Gagal memulai permintaan putus sesi.",kBad);}
        break;
    }
    case Target::SendFile: {
        if(!controlChannel.endpoint){setFlash(L"Kanal kontrol host belum siap.",kWarn);break;}
        if(fileSendPending.valid()){setFlash(L"Masih ada kiriman yang diproses.",kWarn);break;}
        const auto chosen=pickFileToSend(hwnd);
        if(chosen.empty())break;  // Dibatalkan pengguna: bukan kesalahan.
        const auto endpoint=*controlChannel.endpoint;
        const auto body=std::string("{\"action\":\"file-send\",\"path\":")+
            xydesk::panel_control::quoteJson(narrow(chosen))+"}";
        fileSendName=fileNameOf(chosen);
        try{
            fileSendPending=std::async(std::launch::async,[endpoint,body]{return xydesk::panel_control::action(endpoint,body);});
            setFlash(L"Menitipkan berkas ke host…",kAccent);
        }catch(...){setFlash(L"Gagal memulai kiriman berkas.",kBad);}
        break;
    }
    case Target::FilePolicy: {
        if(!controlChannel.endpoint){setFlash(L"Kanal kontrol host belum siap.",kWarn);break;}
        if(filePolicyPending.valid())break;
        // Putaran tiga nilai; nilai berikutnya dihitung dari yang dilaporkan
        // host, bukan dari tebakan panel — dua panel yang terbuka bersamaan
        // tidak boleh saling menimpa dengan nilai usang.
        const std::wstring now=sessionView.filePolicy;
        const char* next = now==L"ask" ? "always" : (now==L"always" ? "never" : "ask");
        const auto endpoint=*controlChannel.endpoint;
        const auto body=std::string("{\"action\":\"file-policy\",\"value\":\"")+next+"\"}";
        filePolicyNote=L"Kebijakan berkas masuk diubah.";
        try{filePolicyPending=std::async(std::launch::async,[endpoint,body]{return xydesk::panel_control::action(endpoint,body);});}
        catch(...){setFlash(L"Gagal mengubah kebijakan.",kBad);}
        break;
    }
    case Target::ForgetTrusted: {
        if(!controlChannel.endpoint){setFlash(L"Kanal kontrol host belum siap.",kWarn);break;}
        if(filePolicyPending.valid())break;
        const auto endpoint=*controlChannel.endpoint;
        filePolicyNote=L"Semua perangkat tepercaya dilupakan.";
        try{filePolicyPending=std::async(std::launch::async,[endpoint]{return xydesk::panel_control::action(endpoint,"{\"action\":\"file-trust-clear\"}");});}
        catch(...){setFlash(L"Gagal melupakan perangkat.",kBad);}
        break;
    }
    case Target::UnattendedPolicy: {
        if(!controlChannel.endpoint){setFlash(L"Kanal kontrol host belum siap.",kWarn);break;}
        if(filePolicyPending.valid())break;
        // Putaran tiga nilai dengan aturan yang sama seperti kebijakan
        // berkas: nilai berikutnya dihitung dari laporan host, bukan dari
        // tebakan panel.
        const std::wstring now=sessionView.unattendedPolicy;
        const char* next = now==L"always" ? "watched" : (now==L"watched" ? "off" : "always");
        const auto endpoint=*controlChannel.endpoint;
        const auto body=std::string("{\"action\":\"unattended-policy\",\"value\":\"")+next+"\"}";
        filePolicyNote=L"Kebijakan akses diubah.";
        try{filePolicyPending=std::async(std::launch::async,[endpoint,body]{return xydesk::panel_control::action(endpoint,body);});}
        catch(...){setFlash(L"Gagal mengubah kebijakan akses.",kBad);}
        break;
    }
    case Target::UnattendedGrant: {
        if(!controlChannel.endpoint){setFlash(L"Kanal kontrol host belum siap.",kWarn);break;}
        if(filePolicyPending.valid())break;
        // Tombol yang sama memberi dan mencabut: saat izin sedang berjalan,
        // satu-satunya hal yang masuk akal untuk ditekan adalah mencabutnya.
        const bool mencabut=sessionView.unattendedGrantMs>0;
        const auto endpoint=*controlChannel.endpoint;
        const auto body=std::string("{\"action\":\"unattended-grant\",\"hours\":")+(mencabut?"0":"8")+"}";
        filePolicyNote=mencabut?L"Izin akses dicabut.":L"Izin akses 8 jam diberikan.";
        try{filePolicyPending=std::async(std::launch::async,[endpoint,body]{return xydesk::panel_control::action(endpoint,body);});}
        catch(...){setFlash(L"Gagal mengubah izin akses.",kBad);}
        break;
    }
    case Target::AcceptFile:
    case Target::TrustFile:
    case Target::RejectFile: {
        if(!controlChannel.endpoint){setFlash(L"Kanal kontrol host belum siap.",kWarn);break;}
        if(sessionView.pendingId==0){setFlash(L"Tidak ada tawaran berkas yang menunggu.",kWarn);break;}
        if(fileConsentPending.valid())break;
        const bool allow=target!=Target::RejectFile;
        const bool remember=target==Target::TrustFile;
        const auto endpoint=*controlChannel.endpoint;
        const auto body=std::string("{\"action\":\"file-consent\",\"id\":")+std::to_string(sessionView.pendingId)+
            ",\"allow\":"+(allow?"true":"false")+",\"remember\":"+(remember?"true":"false")+"}";
        fileConsentAllow=allow;
        try{
            fileConsentPending=std::async(std::launch::async,[endpoint,body]{return xydesk::panel_control::action(endpoint,body);});
        }catch(...){setFlash(L"Gagal mengirim jawaban ke host.",kBad);}
        break;
    }
    case Target::RunHost: {
        // Jalan pintas takeover: bila engine lama masih hidup di sesi lain,
        // restart dari panel ini supaya instance baru benar-benar lahir di
        // sesi Windows aktif. startHost() saja akan no-op saat g.running=true.
        const bool ok = g.running ? restartHost() : startHost();
        if (ok) {
            setFlash(L"Host dijalankan di sesi ini — menunggu takeover…", kAccent);
        } else {
            setStatus(g.lastError.empty() ? L"Gagal memulai host di sesi ini." : g.lastError, kBad);
        }
        break;
    }
    case Target::PageClient:goPage(hwnd,Page::Client);break;
    case Target::PageConnections:goPage(hwnd,Page::Connections);break;
    case Target::PageSettings:goPage(hwnd,Page::Settings);break;
    case Target::PageAccount:goPage(hwnd,Page::Account);break;
    case Target::PageHelp:goPage(hwnd,Page::Help);break;
    case Target::PageStatus:
        goPage(hwnd, Page::Status);
        break;
    case Target::PagePairing:
        goPage(hwnd, Page::Pairing);
        break;
    case Target::PageControl:
        goPage(hwnd, Page::Control);
        break;
    case Target::Minimize:
        // Perkecil sungguhan ke taskbar (panel tetap ada di taskbar karena
        // WS_EX_APPWINDOW). Sembunyi ke tray tetap jadi tugas tombol tutup.
        ShowWindow(hwnd, SW_MINIMIZE);
        break;
    case Target::Maximize:
        toggleMaximize(hwnd);
        break;
    case Target::Close:
        hidePanel(hwnd);
        break;
    default:
        break;
    }
}

// Urutan Tab mengikuti halaman yang terbuka: sidebar dulu, lalu isi halaman
// (yang terlihat saja), terakhir tombol caption.
std::vector<Target> focusOrder() {
    std::vector<Target> order = {Target::ToggleSidebar,Target::Settings,Target::Profile,Target::Help,Target::PageStatus, Target::PagePairing, Target::PageControl};
    switch (g.page) {
    case Page::Pairing:
        order.push_back(Target::CopyId);
        order.push_back(Target::CopyPassword);
        order.push_back(Target::DeviceLink);order.push_back(Target::CopyLink);order.push_back(Target::ConnectionQr);
        break;
    case Page::Control:
        order.push_back(Target::Start);
        order.push_back(Target::Stop);
        order.push_back(Target::Restart);
        order.push_back(Target::Web);
        order.push_back(Target::OpenLog);
        break;
    case Page::Status:
        if (g.sessionMismatch) order.push_back(Target::RunHost);
        break;
    case Page::Connections:
        order.push_back(Target::StopSession);
        order.push_back(Target::SendFile);
        order.push_back(Target::AcceptFile);
        order.push_back(Target::TrustFile);
        order.push_back(Target::RejectFile);
        order.push_back(Target::FilePolicy);
        order.push_back(Target::ForgetTrusted);
        break;
    }
    order.push_back(Target::Minimize);
    order.push_back(Target::PageConnections);
    order.push_back(Target::Maximize);
    order.push_back(Target::Close);
    return order;
}

void moveFocus(int step) {
    const std::vector<Target> order = focusOrder();
    const int count = static_cast<int>(order.size());
    int index = -1;
    for (int i = 0; i < count; ++i) {
        if (order[i] == g.focused) {
            index = i;
            break;
        }
    }
    for (int i = 0; i < count; ++i) {
        index = (index + step + count) % count;
        if (targetEnabled(order[index])) {
            g.focused = order[index];
            renderPanel();
            return;
        }
    }
}

int xFromLParam(LPARAM value) { return static_cast<int>(static_cast<short>(LOWORD(value))); }
int yFromLParam(LPARAM value) { return static_cast<int>(static_cast<short>(HIWORD(value))); }

void trackMouse(HWND hwnd) {
    if (g.trackingMouse) return;
    TRACKMOUSEEVENT event{sizeof(TRACKMOUSEEVENT), TME_LEAVE, hwnd, 0};
    if (TrackMouseEvent(&event)) g.trackingMouse = true;
}

void updateHover(int x, int y) {
    g.cursorX = x;
    g.cursorY = y;
    const Target target = xydesk::panel::targetAt(g.layout, g.page, x, y, g.sessionMismatch);
    const Target hot = targetEnabled(target) || target == Target::TitleBar ? target : Target::None;
    if (hot != g.hot) {
        g.hot = hot;
        renderPanel();
    }
}

LRESULT handleHitTest(HWND hwnd, LPARAM lParam) {
    POINT client{static_cast<LONG>(static_cast<short>(LOWORD(lParam))),
        static_cast<LONG>(static_cast<short>(HIWORD(lParam)))};
    ScreenToClient(hwnd, &client);
    // Tepi jendela = gagang ubah ukuran (kecuali sedang dizoom penuh).
    // Windows otomatis memberi kursor panah dua dari kode HT* ini.
    if (!g.maximized) {
        const int grip = xydesk::panel::scaled(8, g.layout.scalePct);
        const bool left = client.x < grip;
        const bool right = client.x >= g.layout.window.w - grip;
        const bool top = client.y < grip;
        const bool bottom = client.y >= g.layout.window.h - grip;
        if (top && left) return HTTOPLEFT;
        if (top && right) return HTTOPRIGHT;
        if (bottom && left) return HTBOTTOMLEFT;
        if (bottom && right) return HTBOTTOMRIGHT;
        if (left) return HTLEFT;
        if (right) return HTRIGHT;
        if (top) return HTTOP;
        if (bottom) return HTBOTTOM;
    }
    const Target target = xydesk::panel::targetAt(g.layout, g.page, client.x, client.y, g.sessionMismatch);
    switch (target) {
    case Target::ConnectionQr:
    case Target::ToggleSidebar:
    case Target::Settings:
    case Target::Profile:
    case Target::Help:
    case Target::DeviceLink:
    case Target::CopyLink:
    case Target::Minimize:
    case Target::Maximize:
    case Target::Close:
    case Target::PageConnections:
    case Target::PageSettings:
    case Target::PageAccount:
    case Target::PageHelp:
    case Target::PageStatus:
    case Target::PagePairing:
    case Target::PageControl:
    case Target::CopyId:
    case Target::CopyPassword:
    case Target::Start:
    case Target::Stop:
    case Target::Restart:
    case Target::Web:
    case Target::OpenLog:
    case Target::RunHost:
    case Target::StopSession:
    case Target::SendFile:
    case Target::AcceptFile:
    case Target::TrustFile:
    case Target::RejectFile:
    case Target::FilePolicy:
    case Target::ForgetTrusted:
        return HTCLIENT;
    case Target::TitleBar:
        return HTCAPTION; // geser jendela dari area judul
    default:
        // Di luar permukaan (hanya ada bayangan): biarkan klik lewat.
        return g.layout.panel.contains(client.x,client.y)?HTCLIENT:HTTRANSPARENT;
    }
}


// ── Pembaruan host ──
// Keputusan "boleh dipasang atau tidak" seluruhnya ada di updater.h yang
// diuji di Linux. Di sini hanya urutan tindakannya: periksa di thread lain,
// lapor lewat pesan jendela, lalu pasang kalau pengguna setuju.
void startUpdateCheck(HWND hwnd) {
    if (updateState.busy.exchange(true)) return;
    updateState.cancelled.store(false);
    updateState.worker = std::async(std::launch::async, [hwnd]() {
        xydesk::updater::Release release;
        const auto decision =
            xydesk::updater::check(kHostVersion, release, &updateState.cancelled);
        updateState.decision = decision;
        updateState.release = release;
        PostMessageW(hwnd, kUpdateCheckedMessage, 0, 0);
    });
}

void startUpdateInstall(HWND hwnd) {
    if (updateState.busy.exchange(true)) return;
    updateState.worker = std::async(std::launch::async, [hwnd]() {
        std::wstring path;
        updateState.install =
            xydesk::updater::downloadAndRun(updateState.release, path, &updateState.cancelled);
        PostMessageW(hwnd, kUpdateInstalledMessage, 0, 0);
    });
}

void onUpdateChecked(HWND hwnd) {
    updateState.busy.store(false);
    using xydesk::updater::Decision;
    if (updateState.decision == Decision::UpToDate) {
        const std::wstring text =
            L"XyDesk " + xydesk::account::wide(kHostVersion) + L" sudah versi terbaru.";
        showPanelDialog(hwnd, L"XyDesk", text, {{L"Mengerti", 1}});
        return;
    }
    if (updateState.decision != Decision::Available) {
        showPanelDialog(hwnd, L"XyDesk",
            L"Tidak bisa memeriksa pembaruan sekarang.\n\n"
            L"Periksa koneksi internet, lalu coba lagi. Unduhan manual selalu "
            L"tersedia di github.com/xykal/XyDesk/releases.",
            {{L"Mengerti", 1}});
        return;
    }
    const std::wstring text =
        xydesk::account::wide(xydesk::updater::offerText(updateState.release)) +
        L"\n\nXyDesk akan mengunduh installer resmi, memeriksa sidik SHA-256-nya, lalu "
        L"menjalankannya. Sesi yang sedang berjalan akan terputus.\n\nUnduh dan pasang "
        L"sekarang?";
    if (showPanelDialog(hwnd, L"Pembaruan tersedia", text,
            {{L"Ya, unduh dan pasang", 1}, {L"Nanti", 0}}) != 1) {
        return;
    }
    startUpdateInstall(hwnd);
}

void onUpdateInstalled(HWND hwnd) {
    updateState.busy.store(false);
    using xydesk::updater::InstallResult;
    switch (updateState.install) {
    case InstallResult::Started:
        // Installer sudah berjalan; panel menutup diri supaya berkasnya
        // tidak terkunci saat ditimpa.
        removeTrayIcon();
        DestroyWindow(hwnd);
        return;
    case InstallResult::Cancelled:
        return;
    case InstallResult::HashMismatch:
        showPanelDialog(hwnd, L"Pembaruan dibatalkan",
            L"Berkas yang terunduh tidak cocok dengan sidik SHA-256 di catatan "
            L"rilis, jadi tidak dijalankan dan sudah dihapus.\n\nCoba lagi nanti, "
            L"atau unduh manual dari github.com/xykal/XyDesk/releases.",
            {{L"Mengerti", 1}});
        return;
    case InstallResult::WriteFailed:
        showPanelDialog(hwnd, L"XyDesk", L"Tidak bisa menulis installer ke folder sementara.",
            {{L"Mengerti", 1}});
        return;
    case InstallResult::LaunchFailed:
        showPanelDialog(hwnd, L"XyDesk",
            L"Installer terunduh dan sah, tetapi Windows menolak menjalankannya.",
            {{L"Mengerti", 1}});
        return;
    case InstallResult::NetworkFailed:
    default:
        showPanelDialog(hwnd, L"XyDesk", L"Unduhan pembaruan gagal atau terputus.",
            {{L"Mengerti", 1}});
        return;
    }
}

LRESULT CALLBACK windowProc(HWND hwnd, UINT message, WPARAM wParam, LPARAM lParam) {
    // Explorer mengirim pesan ini saat taskbar/tray restart (Explorer crash):
    // ikon tray didaftarkan ulang supaya tidak hilang sampai sesi Windows mati.
    if (g_taskbarCreated && message == g_taskbarCreated) {
        addTrayIcon(hwnd);
        return 0;
    }

    switch (message) {
    case WM_CREATE:
        g.window = hwnd;
        g.layout = xydesk::panel::computeLayout(static_cast<int>(windowDpi(hwnd)));
        createFonts();
        g.logPath = hostLogPath();
        if(!workspaceProbe)addTrayIcon(hwnd);
        setStatus(L"Menyalakan host…", kMuted);
        if(!workspaceProbe)SetTimer(hwnd, kTimer, 1000, nullptr);
        // Panel adalah satu pintu, tapi pintunya kini berkunci: engine baru
        // dinyalakan setelah pemilik PC ini masuk akun. Menyalakannya lebih
        // awal berarti mesin siap menerima koneksi sementara layar masih
        // menampilkan layar masuk — persis yang ingin dicegah.
        if(!workspaceProbe)probeAccount(hwnd);
        return 0;

    case kAutoStartMessage:
        if (!g.running) startHost();
        return 0;

    case WM_DPICHANGED: {
        const UINT dpi = HIWORD(wParam);
        applyDpi(hwnd, dpi, true);
        return 0;
    }

    case WM_GETMINMAXINFO: {
        // Panel boleh ditarik (permintaan pemilik); batasnya dari layout.h.
        auto* info = reinterpret_cast<MINMAXINFO*>(lParam);
        const int s = g.layout.scalePct > 0 ? g.layout.scalePct : 100;
        info->ptMinTrackSize.x = xydesk::panel::scaled(xydesk::panel::kPanelMinWidth, s);
        info->ptMinTrackSize.y = xydesk::panel::scaled(xydesk::panel::kPanelMinHeight, s);
        info->ptMaxTrackSize.x = xydesk::panel::scaled(xydesk::panel::kPanelMaxWidth, s);
        info->ptMaxTrackSize.y = xydesk::panel::scaled(xydesk::panel::kPanelMaxHeight, s);
        return 0;
    }

    case WM_NCCALCSIZE:
        return 0; // Keep the resize style, but draw our own attached chrome.

    case WM_NCHITTEST:
        return handleHitTest(hwnd, lParam);

    case WM_MOUSEMOVE: {
        trackMouse(hwnd);
        const int mx = xFromLParam(lParam), my = yFromLParam(lParam);
        if (!g.gate.panelVisible()) {
            g.cursorX = mx; g.cursorY = my;
            const int hot = gateTargetAt(mx, my);
            if (hot != g.gateHot) { g.gateHot = hot; renderPanel(); }
            return 0;
        }
        updateHover(mx, my);
        return 0;
    }

    case WM_MOUSELEAVE:
        g.trackingMouse = false;
        if (g.hot != Target::None) {
            g.hot = Target::None;
            renderPanel();
        }
        return 0;

    case WM_LBUTTONDOWN: {
        if (!g.gate.panelVisible()) {
            const int hit = gateTargetAt(xFromLParam(lParam), yFromLParam(lParam));
            if (hit) { g.gatePressed = hit; SetCapture(hwnd); renderPanel(); }
            return 0;
        }
        const Target target = xydesk::panel::targetAt(g.layout, g.page, xFromLParam(lParam), yFromLParam(lParam), g.sessionMismatch);
        if (target != Target::None && target != Target::TitleBar && targetEnabled(target)) {
            g.pressed = target;
            SetCapture(hwnd);
            renderPanel();
        }
        return 0;
    }

    case WM_LBUTTONUP: {
        if (!g.gate.panelVisible()) {
            const int hit = gateTargetAt(xFromLParam(lParam), yFromLParam(lParam));
            const int pressed = g.gatePressed;
            g.gatePressed = 0;
            if (GetCapture() == hwnd) ReleaseCapture();
            if (pressed && pressed == hit) activateGate(hwnd, hit);
            renderPanel();
            return 0;
        }
        const Target target = xydesk::panel::targetAt(g.layout, g.page, xFromLParam(lParam), yFromLParam(lParam), g.sessionMismatch);
        const Target pressed = g.pressed;
        g.pressed = Target::None;
        if (GetCapture() == hwnd) ReleaseCapture();
        if (pressed != Target::None && pressed == target && targetEnabled(target)) {
            if (target != Target::CopyId && target != Target::CopyPassword) g.focused = target;
            activateTarget(hwnd, target);
        }
        renderPanel();
        return 0;
    }

    case WM_LBUTTONDBLCLK: {
        // Kebiasaan Windows: dua klik di area judul = perbesar/pulihkan.
        const Target target = xydesk::panel::targetAt(g.layout, g.page, xFromLParam(lParam), yFromLParam(lParam), g.sessionMismatch);
        if (target == Target::TitleBar) {
            toggleMaximize(hwnd);
            return 0;
        }
        break;
    }

    case WM_SETCURSOR: {
        if (LOWORD(lParam) == HTCLIENT) {
            const Target target = g.hot;
            if (target != Target::None && target != Target::TitleBar) {
                SetCursor(LoadCursorW(nullptr, IDC_HAND));
                return TRUE;
            }
        }
        break;
    }

    case WM_SETTINGCHANGE:
    case WM_DISPLAYCHANGE:
        renderPanel();
        return 0;

    case WM_KEYDOWN: {
        if (!g.gate.panelVisible()) {
            // Di gerbang keyboard sederhana saja: Enter menekan tombol utama,
            // Esc membatalkan percobaan masuk yang menggantung.
            if (wParam == VK_RETURN || wParam == VK_SPACE) { activateGate(hwnd, 1); return 0; }
            if (wParam == VK_ESCAPE) {
                g.gateCancelled = true;
                g.gate.cancel();
                renderPanel();
                return 0;
            }
            return 0;
        }
        if (wParam == VK_TAB) {
            moveFocus((GetKeyState(VK_SHIFT) & 0x8000) ? -1 : 1);
            return 0;
        }
        if (wParam == VK_ESCAPE) {
            hidePanel(hwnd);
            return 0;
        }
        if (wParam == VK_RETURN || wParam == VK_SPACE) {
            if (g.focused != Target::None && targetEnabled(g.focused)) activateTarget(hwnd, g.focused);
            return 0;
        }
        break;
    }

    case WM_SYSCOMMAND: {
        const WPARAM command = wParam & 0xFFF0;
        if (command == SC_MINIMIZE) {
            // Perkecil sungguhan ke taskbar; menyembunyikan panel ke tray
            // tetap jadi tugas tombol tutup dan menu tray.
            ShowWindow(hwnd, SW_MINIMIZE);
            return 0;
        }
        if (command == SC_MAXIMIZE) {
            if (!g.maximized) toggleMaximize(hwnd);
            return 0;
        }
        if (command == SC_RESTORE) {
            // Klik tombol taskbar atau Alt+Tab saat minimized: pulihkan.
            // Kalau panel sedang di-zoom, kembalikan ke ukuran normal.
            if (IsIconic(hwnd)) ShowWindow(hwnd, SW_RESTORE);
            else if (g.maximized) toggleMaximize(hwnd);
            return 0;
        }
        if (command == SC_KEYMENU) return 0;
        break;
    }

    case WM_CLOSE:
        // Menutup panel = sembunyi ke tray, bukan mematikan host. Kontrol
        // penuh tetap ada di menu tray; "Keluar XyDesk" yang menghentikan.
        hidePanel(hwnd);
        return 0;

    case WM_QUERYENDSESSION:
        return TRUE;

    case WM_SIZE: {
        // Pengguna menarik tepi: hitung ulang satuan ukuran lalu tata ulang.
        if (wParam == SIZE_MINIMIZED || g.layout.scalePct <= 0) return 0;
        const int cx = static_cast<int>(LOWORD(lParam));
        const int cy = static_cast<int>(HIWORD(lParam));
        if (cx <= 0 || cy <= 0) return 0;
        const int s = g.layout.scalePct;
        const int newW = (cx * 100 + s / 2) / s;
        const int newH = (cy * 100 + s / 2) / s;
        if (newW == g.unitsW && newH == g.unitsH) return 0;
        g.unitsW = newW;
        g.unitsH = newH;
        const UINT dpi = static_cast<UINT>(static_cast<unsigned long long>(windowDpi(hwnd)) * g.zoomPct / 100);
        g.layout = xydesk::panel::computeLayout(static_cast<int>(dpi), g.unitsW, g.unitsH, g.sidebarCollapsed);
        g.pillY = sidebarItemY(g.layout, g.page);
        renderPanel();
        return 0;
    }

    case WM_ENDSESSION:
        if (wParam) {
            removeTrayIcon();
            stopHost();
        }
        return 0;

    case WM_PRINTCLIENT:
        if(g.surface.valid())BitBlt(reinterpret_cast<HDC>(wParam),0,0,g.surface.width,g.surface.height,g.surface.dc,0,0,SRCCOPY);
        return 0;

    case WM_PAINT: {
        PAINTSTRUCT ps{};
        HDC dc = BeginPaint(hwnd, &ps);
        if (!g.layered && g.surface.valid()) {
            BitBlt(dc, 0, 0, g.surface.width, g.surface.height, g.surface.dc, 0, 0, SRCCOPY);
        }
        EndPaint(hwnd, &ps);
        return 0;
    }

    case WM_ERASEBKGND:
        return 1;

    case WM_TIMER:
        if (wParam == kAnimTimer) {
            tickAnimation(hwnd);
            return 0;
        }
        if (wParam == kTimer) {
            controlChannel.poll(g.process);
            if (identityFuture.valid() && identityFuture.wait_for(std::chrono::milliseconds(0)) == std::future_status::ready) {
                IdentityResult identity;
                try { identity = identityFuture.get(); } catch (...) { identity = {}; }
                g.deviceId = identity.id;
                g.pairingCode = identity.password;
                const bool start = g.startRequested;
                g.startRequested = false;
                if (g.deviceId.empty() || g.pairingCode.empty()) {
                    setStatus(L"Identitas gagal dibaca atau timeout. Coba Mulai host lagi.", kBad);
                } else if (start) { startHost(); }
            }
            pollSessionAction();
            pollFileSend();
            pollFileConsent();
            pollFilePolicy();
            pollSessionView();
            readCaptureStatus();
            if (!g.flashText.empty() && GetTickCount64() >= g.flashUntil) {
                g.flashText.clear();
                g.statusColor = kMuted;
                renderPanel();
            }
            if (g.process) {
                DWORD code = STILL_ACTIVE;
                if (GetExitCodeProcess(g.process, &code) && code != STILL_ACTIVE) {
                    closeHostHandles();
                    setStatus(L"Host berhenti (kode " + std::to_wstring(code) + L")", kWarn);
                }
            }
        }
        return 0;

    case WM_COMMAND:
        // Kotak isian gerbang memberi tahu tiap ketikan supaya tombol utama
        // hidup/mati mengikuti isi kotak.
        if (LOWORD(wParam) == kGateEditId && HIWORD(wParam) == EN_CHANGE) {
            readGateEdit();
            renderPanel();
            return 0;
        }
        switch (LOWORD(wParam)) {
        case kTrayOpen: openPanel(hwnd); return 0;
        case kTrayStart: startHost(); return 0;
        case kTrayStop: stopHost(); return 0;
        case kTrayRestart: restartHost(); return 0;
        case kTrayWeb: ShellExecuteW(hwnd, L"open", kWebUrl, nullptr, nullptr, SW_SHOWNORMAL); return 0;
        case kTrayUpdate: startUpdateCheck(hwnd); return 0;
        case kTrayQuit:
            removeTrayIcon();
            DestroyWindow(hwnd);
            return 0;
        default:
            break;
        }
        return 0;

    case kUpdateCheckedMessage:
        onUpdateChecked(hwnd);
        return 0;

    case kUpdateInstalledMessage:
        onUpdateInstalled(hwnd);
        return 0;

    case kGateSubmitMessage:
        submitGateForm(hwnd);
        return 0;

    case kGateBackMessage:
        gateFormBack(hwnd);
        return 0;

    case WM_CTLCOLOREDIT:
        // Kotak isian gerbang memakai warna kartu, bukan putih sistem, supaya
        // menyatu dengan kulit yang digambar di belakangnya.
        if (reinterpret_cast<HWND>(lParam) == g.gateEditBox) {
            auto* dc = reinterpret_cast<HDC>(wParam);
            SetTextColor(dc, kText);
            SetBkColor(dc, kSurface2);
            static HBRUSH field = nullptr;
            if (!field) field = CreateSolidBrush(kSurface2);
            return reinterpret_cast<LRESULT>(field);
        }
        break;

    case kTrayMessage: {
        // NOTIFYICON_VERSION_4 menaruh event di lParam; beberapa build Explorer
        // mengemasnya di LOWORD. Keduanya diterima.
        const UINT raw = static_cast<UINT>(lParam);
        const UINT event = LOWORD(raw);
        if (raw == WM_LBUTTONUP || raw == WM_LBUTTONDBLCLK || event == WM_LBUTTONUP ||
            event == WM_LBUTTONDBLCLK || raw == NIN_SELECT) {
            openPanel(hwnd);
        } else if (raw == WM_RBUTTONUP || raw == WM_RBUTTONDOWN || raw == WM_CONTEXTMENU ||
                   event == WM_RBUTTONUP || event == WM_RBUTTONDOWN || event == WM_CONTEXTMENU ||
                   raw == NIN_KEYSELECT) {
            showTrayMenu(hwnd);
        }
        return 0;
    }

    case WM_DESTROY:
        KillTimer(hwnd, kTimer);
        KillTimer(hwnd, kAnimTimer);
        g.animOn = false;
        removeTrayIcon();
        stopHost();
        for(auto& page:embeddedLayouts)for(auto& child:page.second.children)if(child.ownedFont)DeleteObject(child.ownedFont);
        embeddedLayouts.clear();
        if (g.fontIcons) DeleteObject(g.fontIcons);
        if (g.fontHeading) DeleteObject(g.fontHeading);
        if (g.fontTitle) DeleteObject(g.fontTitle);
        if (g.fontBody) DeleteObject(g.fontBody);
        if (g.fontSmall) DeleteObject(g.fontSmall);
        if (g.fontMono) DeleteObject(g.fontMono);
        if (g.fontLogo) DeleteObject(g.fontLogo);
        if (g.fontSemi) DeleteObject(g.fontSemi);
        if (g.fontCaps) DeleteObject(g.fontCaps);
        if (g.fontValue) DeleteObject(g.fontValue);
        destroySurface(g.surface);
        g.window = nullptr;
        PostQuitMessage(0);
        return 0;

    default:
        break;
    }
    return DefWindowProcW(hwnd, message, wParam, lParam);
}

// `--panel-probe <berkas>`: menulis tata letak panel sebagai JSON tanpa
// membuka jendela. Dipakai CI untuk memastikan EXE yang benar-benar
// dikompilasi masih menjalankan tata letak yang diharapkan.
int runPanelProbe(const std::wstring& path) {
    const PanelLayout layout = xydesk::panel::computeLayout(96);
    const auto at = [&](const Rect& r, Page page) {
        return xydesk::panel::targetAt(layout, page, xydesk::panel::centerX(r), xydesk::panel::centerY(r));
    };
    const Target samples[] = {
        at(layout.minimizeButton, Page::Status),
        at(layout.maximizeButton, Page::Status),
        at(layout.closeButton, Page::Status),
        at(layout.sideStatus, Page::Status),
        at(layout.sidePairing, Page::Status),
        at(layout.start, Page::Control),
        at(layout.idCopy, Page::Pairing),
        xydesk::panel::targetAt(layout, Page::Status, layout.panel.x + 2, layout.panel.bottom() - 2),
    };
    std::string json = "{\n";
    json += "  \"scalePct\": " + std::to_string(layout.scalePct) + ",\n";
    json += "  \"windowWidth\": " + std::to_string(layout.window.w) + ",\n";
    json += "  \"windowHeight\": " + std::to_string(layout.window.h) + ",\n";
    json += "  \"panelWidth\": " + std::to_string(layout.panel.w) + ",\n";
    json += "  \"panelHeight\": " + std::to_string(layout.panel.h) + ",\n";
    json += "  \"radiusPanel\": " + std::to_string(layout.radiusPanel) + ",\n";
    json += "  \"shadowMargin\": " + std::to_string(layout.panel.x) + ",\n";
    json += "  \"hits\": [";
    for (int i = 0; i < static_cast<int>(std::size(samples)); ++i) {
        if (i) json += ", ";
        json += std::string("\"") + xydesk::panel::targetName(samples[i]) + "\"";
    }
    json += "]\n}\n";
    HANDLE file = CreateFileW(path.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS,
        FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE) return 2;
    DWORD written = 0;
    const BOOL ok = WriteFile(file, json.data(), static_cast<DWORD>(json.size()), &written, nullptr);
    CloseHandle(file);
    return ok && written == json.size() ? 0 : 3;
}

// Resource/render evidence only: no engine, credentials, network or browser launch.
INT_PTR CALLBACK accountSnapshotDialog(HWND hwnd,UINT message,WPARAM,LPARAM){
    if(message==WM_INITDIALOG){
        SendDlgItemMessageW(hwnd,IDC_ACCOUNT_NAME,WM_SETFONT,reinterpret_cast<WPARAM>(g.fontTitle),TRUE);
        SetDlgItemTextW(hwnd,IDC_ACCOUNT_NAME,L"Belum masuk akun");
        SetDlgItemTextW(hwnd,IDC_ACCOUNT_STATUS,L"Masuk melalui browser untuk memakai akun XyDesk.");
        SetDlgItemTextW(hwnd,IDC_ACCOUNT_LOCAL,L"Windows: operator (fixture)\nDevice ID: 123456789");return TRUE;
    }return FALSE;
}
int runDialogSnapshots(const std::wstring& directory){
    SetProcessDPIAware();g.layout=xydesk::panel::computeLayout(96);createFonts();g.deviceId=L"123456789";
    const auto link=xydesk::panel_control::deviceLink(g.deviceId);const std::string text(link.begin(),link.end());
    QrDialogState qr{qrcodegen::QrCode::encodeText(text.c_str(),qrcodegen::QrCode::Ecc::MEDIUM),link};SettingsState settings;
    struct Entry{int id;DLGPROC proc;LPARAM state;const wchar_t* name;};
    const Entry entries[]={{IDD_HOST_SETTINGS,settingsDialog,reinterpret_cast<LPARAM>(&settings),L"settings"},{IDD_ACCOUNT,accountSnapshotDialog,0,L"profile"},{IDD_GUIDE,guideDialog,0,L"guide"},{IDD_CONNECTION_QR,qrDialog,reinterpret_cast<LPARAM>(&qr),L"qr"}};
    for(const auto& entry:entries){
        HWND hwnd=CreateDialogParamW(GetModuleHandleW(nullptr),MAKEINTRESOURCEW(entry.id),nullptr,entry.proc,entry.state);if(!hwnd)return 10;
        KillTimer(hwnd,1);ShowWindow(hwnd,SW_SHOWNOACTIVATE);UpdateWindow(hwnd);
        RECT bounds{};GetWindowRect(hwnd,&bounds);const int width=bounds.right-bounds.left,height=bounds.bottom-bounds.top;
        HDC dc=CreateCompatibleDC(nullptr);BITMAPINFO info{};info.bmiHeader.biSize=sizeof(BITMAPINFOHEADER);info.bmiHeader.biWidth=width;info.bmiHeader.biHeight=-height;info.bmiHeader.biPlanes=1;info.bmiHeader.biBitCount=32;info.bmiHeader.biCompression=BI_RGB;info.bmiHeader.biSizeImage=width*height*4;
        void* pixels=nullptr;HBITMAP bitmap=CreateDIBSection(dc,&info,DIB_RGB_COLORS,&pixels,nullptr,0);if(!bitmap)return 11;auto old=SelectObject(dc,bitmap);
        const BOOL rendered=PrintWindow(hwnd,dc,0);GdiFlush();
        BITMAPFILEHEADER header{};header.bfType=0x4D42;header.bfOffBits=sizeof(header)+sizeof(BITMAPINFOHEADER);header.bfSize=header.bfOffBits+info.bmiHeader.biSizeImage;
        const auto path=directory+L"\\"+entry.name+L".bmp";HANDLE file=CreateFileW(path.c_str(),GENERIC_WRITE,0,nullptr,CREATE_ALWAYS,FILE_ATTRIBUTE_NORMAL,nullptr);if(file==INVALID_HANDLE_VALUE)return 12;
        DWORD n=0;bool ok=WriteFile(file,&header,sizeof(header),&n,nullptr)&&WriteFile(file,&info.bmiHeader,sizeof(BITMAPINFOHEADER),&n,nullptr)&&WriteFile(file,pixels,info.bmiHeader.biSizeImage,&n,nullptr)&&n==info.bmiHeader.biSizeImage;
        CloseHandle(file);SelectObject(dc,old);DeleteObject(bitmap);DeleteDC(dc);DestroyWindow(hwnd);if(!ok||!rendered)return 13;
    }return 0;
}

bool saveWindowEvidence(HWND hwnd,const std::wstring& path){
    RECT bounds{};GetWindowRect(hwnd,&bounds);const int width=bounds.right-bounds.left,height=bounds.bottom-bounds.top;
    HDC dc=CreateCompatibleDC(nullptr);BITMAPINFO info{};info.bmiHeader.biSize=sizeof(BITMAPINFOHEADER);info.bmiHeader.biWidth=width;info.bmiHeader.biHeight=-height;info.bmiHeader.biPlanes=1;info.bmiHeader.biBitCount=32;info.bmiHeader.biCompression=BI_RGB;info.bmiHeader.biSizeImage=width*height*4;
    void* pixels=nullptr;HBITMAP bitmap=CreateDIBSection(dc,&info,DIB_RGB_COLORS,&pixels,nullptr,0);if(!bitmap){DeleteDC(dc);return false;}auto old=SelectObject(dc,bitmap);
    // Compose the actual window surface and real child controls through WM_PRINT.
    // CI desktop can be narrower than the test window; no screen clipping/readback.
    std::memset(pixels,0,info.bmiHeader.biSizeImage);
    SendMessageW(hwnd,WM_PRINTCLIENT,reinterpret_cast<WPARAM>(dc),PRF_CLIENT);
    RECT parent{};GetWindowRect(hwnd,&parent);
    for(const auto& entry:embeddedPages)if(IsWindowVisible(entry.second)){
        RECT child{};GetWindowRect(entry.second,&child);int saved=SaveDC(dc);
        SetViewportOrgEx(dc,child.left-parent.left,child.top-parent.top,nullptr);
        IntersectClipRect(dc,0,0,child.right-child.left,child.bottom-child.top);
        SendMessageW(entry.second,WM_PRINT,reinterpret_cast<WPARAM>(dc),PRF_CLIENT|PRF_CHILDREN|PRF_ERASEBKGND);
        RestoreDC(dc,saved);
    }
    const BOOL rendered=TRUE;GdiFlush();
    BITMAPFILEHEADER header{};header.bfType=0x4D42;header.bfOffBits=sizeof(header)+sizeof(BITMAPINFOHEADER);header.bfSize=header.bfOffBits+info.bmiHeader.biSizeImage;
    HANDLE file=CreateFileW(path.c_str(),GENERIC_WRITE,0,nullptr,CREATE_ALWAYS,FILE_ATTRIBUTE_NORMAL,nullptr);bool ok=false;
    if(file!=INVALID_HANDLE_VALUE){DWORD n=0;ok=WriteFile(file,&header,sizeof(header),&n,nullptr)&&WriteFile(file,&info.bmiHeader,sizeof(BITMAPINFOHEADER),&n,nullptr)&&WriteFile(file,pixels,info.bmiHeader.biSizeImage,&n,nullptr)&&n==info.bmiHeader.biSizeImage;CloseHandle(file);}
    SelectObject(dc,old);DeleteObject(bitmap);DeleteDC(dc);return ok&&rendered;
}
int runWorkspaceEvidence(HWND hwnd,const std::wstring& directory){
    g.deviceId=L"123456789";g.pairingCode=L"TESTONLY";g.statusText=L"Pratinjau offline · engine tidak dijalankan";
    g.captureBackend=L"Fixture UI, bukan sesi capture";g.captureWarn=true;g.captureNote=L"Tidak menjalankan capture dalam pratinjau offline.";
    sessionView.known=true;
    const auto style=GetWindowLongPtrW(hwnd,GWL_STYLE),extended=GetWindowLongPtrW(hwnd,GWL_EXSTYLE);
    if(!(style&WS_THICKFRAME)||(extended&WS_EX_LAYERED))return 30;
    SetWindowPos(hwnd,nullptr,20,20,1100,720,SWP_NOZORDER);
    RECT bounds{};GetWindowRect(hwnd,&bounds);
    struct Grip{int x,y;LRESULT code;};
    const int w=bounds.right-bounds.left,h=bounds.bottom-bounds.top;
    for(const auto& grip:std::initializer_list<Grip>{{2,2,HTTOPLEFT},{w-2,2,HTTOPRIGHT},{2,h-2,HTBOTTOMLEFT},{w-2,h-2,HTBOTTOMRIGHT},{2,h/2,HTLEFT},{w-2,h/2,HTRIGHT},{w/2,2,HTTOP},{w/2,h-2,HTBOTTOM}}){
        const auto hit=SendMessageW(hwnd,WM_NCHITTEST,0,MAKELPARAM(bounds.left+grip.x,bounds.top+grip.y));if(hit!=grip.code)return 31;
    }
    SetWindowPos(hwnd,nullptr,0,0,1000,680,SWP_NOMOVE|SWP_NOZORDER);
    if(g.layout.window.w!=1000||g.layout.window.h!=680)return 32;
    toggleMaximize(hwnd);GetWindowRect(hwnd,&bounds);MONITORINFO monitor{sizeof(MONITORINFO)};GetMonitorInfoW(MonitorFromWindow(hwnd,MONITOR_DEFAULTTONEAREST),&monitor);
    if(!EqualRect(&bounds,&monitor.rcWork))return 33;
    toggleMaximize(hwnd);GetWindowRect(hwnd,&bounds);if(bounds.right-bounds.left!=1000||bounds.bottom-bounds.top!=680)return 34;
    SetWindowPos(hwnd,nullptr,0,0,1100,720,SWP_NOMOVE|SWP_NOZORDER);
    // Bukti gerbang dulu (sambutan + layar masuk), baru halaman panel.
    g.gate.start(false,false);g.gateMotion.begin(nowMs(),1);renderPanel();UpdateWindow(hwnd);
    if(!saveWindowEvidence(hwnd,directory+L"\\gate-welcome.bmp"))return 37;
    g.gate.beginLogin();g.gateMotion.begin(nowMs(),1);renderPanel();UpdateWindow(hwnd);
    if(!saveWindowEvidence(hwnd,directory+L"\\gate-login.bmp"))return 37;
    // Sisa bukti memotret panel, jadi gerbang dibuka untuk mode pratinjau ini.
    g.gate.succeed("pratinjau@xydesk.my.id");
    const struct{Page page;const wchar_t* name;} pages[]={{Page::Status,L"home"},{Page::Connections,L"connections-empty"},{Page::Pairing,L"access"},{Page::Control,L"host-control"},{Page::Settings,L"settings-screen"},{Page::Account,L"account-screen"},{Page::Help,L"help-screen"}};
    for(const auto& entry:pages){
        goPage(hwnd,entry.page);renderPanel();UpdateWindow(hwnd);
        for(const auto& child:embeddedPages)if(IsWindowVisible(child.second)){
            if(GetParent(child.second)!=hwnd||(GetWindowLongPtrW(child.second,GWL_STYLE)&WS_POPUP))return 35;
            RECT rect{};GetWindowRect(child.second,&rect);RECT parent{};GetWindowRect(hwnd,&parent);if(rect.right>parent.right||rect.bottom>parent.bottom)return 36;
        }
        if(!saveWindowEvidence(hwnd,directory+L"\\"+entry.name+L".bmp"))return 37;
    }
    SetWindowPos(hwnd,nullptr,0,0,900,640,SWP_NOMOVE|SWP_NOZORDER);
    for(const auto& entry:pages){
        goPage(hwnd,entry.page);renderPanel();UpdateWindow(hwnd);
        for(const auto& page:embeddedPages)if(IsWindowVisible(page.second)){
            RECT client{};GetClientRect(page.second,&client);
            for(HWND child=GetWindow(page.second,GW_CHILD);child;child=GetWindow(child,GW_HWNDNEXT))if(IsWindowVisible(child)){
                RECT rect{};GetWindowRect(child,&rect);MapWindowPoints(nullptr,page.second,reinterpret_cast<POINT*>(&rect),2);
                if(rect.left<0||rect.top<0||rect.right>client.right+1||rect.bottom>client.bottom+1)return 39;
            }
        }
        if(!saveWindowEvidence(hwnd,directory+L"\\compact-"+entry.name+L".bmp"))return 40;
    }
    SetWindowPos(hwnd,nullptr,0,0,1100,720,SWP_NOMOVE|SWP_NOZORDER);
    sessionView={true,true,L"Redmi Note 12",L"android",L"fixture-client",L"streaming",125};goPage(hwnd,Page::Connections);renderPanel();UpdateWindow(hwnd);
    if(!saveWindowEvidence(hwnd,directory+L"\\connections-fixture.bmp"))return 38;
    DestroyWindow(hwnd);return 0;
}

// `--panel-snapshot <berkas.bmp>`: menggambar panel ke berkas (32-bit, alpha
// tidak dipremultiply) tanpa membuka jendela. Dipakai CI Windows untuk
// memeriksa bentuk panel dari piksel: sudut harus transparan, tepi harus
// separuh tembus (bukti penghalusan), dan bayangan harus memudar keluar.
int runPanelSnapshot(const std::wstring& path, Page page=Page::Status, bool collapsed=false) {
    g.layout = xydesk::panel::computeLayout(96,1100,720,collapsed);
    createFonts();
    g.statusText = L"Host aktif sebagai user Windows ini";
    g.statusColor = kGood;
    g.deviceId = L"123456789";
    g.pairingCode = L"TESTONLY";
    g.logPath = L"C:\\Users\\operator\\AppData\\Local\\XyDesk\\host.log";
    g.running = true;
    g.hot = xydesk::panel::Target::None;
    g.page = page;
    g.captureBackend = L"gdi-bitblt · sesi aktif";
    g.captureSeen = true;
    // Bila berkas kesehatan engine ada (mesin sungguhan / uji lapangan),
    // pakai isinya supaya snapshot mencerminkan keadaan nyata.
    readCaptureStatus();
    if (!drawPanelToSurface()) return 4;

    const Surface& surface = g.surface;
    const int width = surface.width;
    const int height = surface.height;
    std::vector<std::uint32_t> straight(static_cast<size_t>(width) * height);
    for (size_t i = 0; i < straight.size(); ++i) {
        const std::uint32_t pixel = surface.pixels[i];
        const std::uint32_t alpha = (pixel >> 24) & 0xFF;
        if (alpha == 0) {
            straight[i] = 0;
            continue;
        }
        // Kembalikan dari premultiplied ke straight supaya berkasnya bisa
        // dibaca alat lain (ImageMagick, System.Drawing) tanpa asumsi.
        const auto unpremultiply = [alpha](std::uint32_t channel) {
            const std::uint32_t value = (channel * 255u + alpha / 2) / alpha;
            return std::min(value, 255u);
        };
        straight[i] = (alpha << 24) | (unpremultiply((pixel >> 16) & 0xFF) << 16)
            | (unpremultiply((pixel >> 8) & 0xFF) << 8) | unpremultiply(pixel & 0xFF);
    }

    BITMAPFILEHEADER fileHeader{};
    fileHeader.bfType = 0x4D42; // 'BM'
    fileHeader.bfOffBits = sizeof(BITMAPFILEHEADER) + sizeof(BITMAPINFOHEADER);
    BITMAPINFOHEADER infoHeader{};
    infoHeader.biSize = sizeof(BITMAPINFOHEADER);
    infoHeader.biWidth = width;
    infoHeader.biHeight = -height; // negatif: baris teratas lebih dulu
    infoHeader.biPlanes = 1;
    infoHeader.biBitCount = 32;
    infoHeader.biCompression = BI_RGB;
    infoHeader.biSizeImage = static_cast<DWORD>(straight.size() * sizeof(std::uint32_t));
    fileHeader.bfSize = fileHeader.bfOffBits + infoHeader.biSizeImage;

    HANDLE file = CreateFileW(path.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS,
        FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE) return 2;
    DWORD written = 0;
    bool ok = WriteFile(file, &fileHeader, sizeof(fileHeader), &written, nullptr) != 0;
    ok = ok && WriteFile(file, &infoHeader, sizeof(infoHeader), &written, nullptr) != 0;
    ok = ok && WriteFile(file, straight.data(), infoHeader.biSizeImage, &written, nullptr) != 0;
    CloseHandle(file);
    return ok && written == infoHeader.biSizeImage ? 0 : 3;
}

std::wstring commandLineArgument(const wchar_t* name) {
    int count = 0;
    LPWSTR* args = CommandLineToArgvW(GetCommandLineW(), &count);
    std::wstring result;
    if (args) {
        for (int i = 1; i + 1 < count; ++i) {
            if (_wcsicmp(args[i], name) == 0) {
                result = args[i + 1];
                break;
            }
        }
        LocalFree(args);
    }
    return result;
}

bool hasArgument(const wchar_t* name) {
    int count = 0;
    LPWSTR* args = CommandLineToArgvW(GetCommandLineW(), &count);
    bool found = false;
    if (args) {
        for (int i = 1; i < count; ++i) {
            if (_wcsicmp(args[i], name) == 0) {
                found = true;
                break;
            }
        }
        LocalFree(args);
    }
    return found;
}

} // namespace

// ── Splash pembuka ──────────────────────────────────────────────────────────
// Jendela kecil bergradien dengan logo — muncul halus lalu menguap saat panel
// dimuat, memberi kesan "hidup" termasuk di cloud PC yang lambat membuka.

static const wchar_t kSplashClass[] = L"XyDeskPanelSplash";

// Logo mark versi HDC murni (saudara paintXyDeskLogoMark yang butuh Surface).
void drawLogoMarkHdc(HDC dc, const Rect& box, COLORREF bg) {
    const int size = (std::min)(box.w, box.h);
    if (size < 12) return;
    const Rect centerBox{box.x + (box.w - size) / 2, box.y + (box.h - size) / 2, size, size};
    {
        HBRUSH brush = CreateSolidBrush(bg);
        HGDIOBJ old = SelectObject(dc, brush);
        HPEN pen = CreatePen(PS_SOLID, 1, bg);
        HGDIOBJ oldPen = SelectObject(dc, pen);
        const int radius = (std::max)(4, size * 26 / 100);
        RoundRect(dc, centerBox.x, centerBox.y, centerBox.right(), centerBox.bottom(), radius, radius);
        SelectObject(dc, old); SelectObject(dc, oldPen);
        DeleteObject(brush); DeleteObject(pen);
    }
    const int stroke = (std::max)(2, size * 18 / 100);
    HPEN penRibbon = CreatePen(PS_SOLID, stroke, RGB(255, 255, 255));
    HPEN penBack = CreatePen(PS_SOLID, stroke, RGB(221, 214, 254));
    HPEN oldPen = static_cast<HPEN>(SelectObject(dc, penBack));
    const int x1 = centerBox.x + size * 22 / 100, y1 = centerBox.y + size * 22 / 100;
    const int x2 = centerBox.x + size * 78 / 100, y2 = centerBox.y + size * 78 / 100;
    MoveToEx(dc, x1, y1, nullptr); LineTo(dc, x2, y2);
    SelectObject(dc, penRibbon);
    const int x3 = centerBox.x + size * 78 / 100, y3 = centerBox.y + size * 22 / 100;
    const int x4 = centerBox.x + size * 22 / 100, y4 = centerBox.y + size * 78 / 100;
    MoveToEx(dc, x3, y3, nullptr); LineTo(dc, x4, y4);
    HBRUSH brushWhite = CreateSolidBrush(RGB(255, 255, 255));
    HGDIOBJ oldBrush = SelectObject(dc, brushWhite);
    const int cx = centerBox.x + size / 2, cy = centerBox.y + size / 2;
    const int dotR = (std::max)(2, stroke * 35 / 100);
    Ellipse(dc, cx - dotR, cy - dotR, cx + dotR + 1, cy + dotR + 1);
    SelectObject(dc, oldPen); SelectObject(dc, oldBrush);
    DeleteObject(penRibbon); DeleteObject(penBack); DeleteObject(brushWhite);
}

LRESULT CALLBACK splashProc(HWND hwnd, UINT message, WPARAM wParam, LPARAM lParam) {
    switch (message) {
    case WM_PAINT: {
        PAINTSTRUCT ps{};
        HDC dc = BeginPaint(hwnd, &ps);
        RECT rc{}; GetClientRect(hwnd, &rc);
        const int w = rc.right, h = rc.bottom;
        // Gradien lavender -> ungu, baris demi baris (sekali gambar saja).
        for (int y = 0; y < h; ++y) {
            const float t = static_cast<float>(y) / (std::max)(1, h - 1);
            const COLORREF c = mixColor(RGB(240, 237, 255), RGB(124, 92, 232), t * 0.9f);
            HBRUSH brush = CreateSolidBrush(c);
            RECT row{0, y, w, y + 1};
            FillRect(dc, &row, brush);
            DeleteObject(brush);
        }
        const auto px = [&](int n) { return xydesk::panel::scaled(n, g.layout.scalePct); };
        drawLogoMarkHdc(dc, Rect{w / 2 - px(34), px(52), px(68), px(68)}, RGB(109, 74, 224));
        RECT titleRc{px(24), px(132), w - px(24), px(170)};
        drawTextLine(dc, L"XyDesk", Rect{px(24), px(132), w - px(24), px(40)}, g.fontHeading, RGB(255, 255, 255),
            DT_CENTER | DT_VCENTER | DT_SINGLELINE);
        drawTextLine(dc, L"Remote Desktop untuk PC kamu", Rect{px(24), px(172), w - px(24), px(26)},
            g.fontBody, RGB(232, 228, 255), DT_CENTER | DT_VCENTER | DT_SINGLELINE);
        drawTextLine(dc, L"Memuat panel host\u2026", Rect{px(24), h - px(56), w - px(24), px(24)},
            g.fontSmall, RGB(224, 219, 255), DT_CENTER | DT_VCENTER | DT_SINGLELINE);
        EndPaint(hwnd, &ps);
        return 0;
    }
    case WM_ERASEBKGND:
        return 1;
    case WM_TIMER:
    case WM_LBUTTONDOWN:
    case WM_LBUTTONUP:
        AnimateWindow(hwnd, 380, AW_BLEND | AW_HIDE);
        DestroyWindow(hwnd);
        return 0;
    }
    return DefWindowProcW(hwnd, message, wParam, lParam);
}

HWND showSplash(HINSTANCE instance) {
    WNDCLASSEXW wc{};
    wc.cbSize = sizeof(wc);
    wc.lpfnWndProc = splashProc;
    wc.hInstance = instance;
    wc.hCursor = LoadCursorW(nullptr, IDC_ARROW);
    wc.lpszClassName = kSplashClass;
    if (!RegisterClassExW(&wc)) return nullptr;
    const auto px = [&](int n) { return xydesk::panel::scaled(n, g.layout.scalePct); };
    const int w = px(420), h = px(260);
    RECT workArea{};
    if (!SystemParametersInfoW(SPI_GETWORKAREA, 0, &workArea, 0)) {
        workArea = RECT{0, 0, GetSystemMetrics(SM_CXSCREEN), GetSystemMetrics(SM_CYSCREEN)};
    }
    const int x = workArea.left + ((workArea.right - workArea.left) - w) / 2;
    const int y = workArea.top + ((workArea.bottom - workArea.top) - h) / 2;
    HWND hwnd = CreateWindowExW(WS_EX_LAYERED | WS_EX_TOOLWINDOW | WS_EX_TOPMOST, kSplashClass, L"",
        WS_POPUP, x, y, w, h, nullptr, nullptr, instance, nullptr);
    if (!hwnd) return nullptr;
    SetLayeredWindowAttributes(hwnd, 0, 255, LWA_ALPHA);
    SetWindowRgn(hwnd, CreateRoundRectRgn(0, 0, w + 1, h + 1, px(28), px(28)), TRUE);
    ShowWindow(hwnd, SW_SHOWNOACTIVATE);
    AnimateWindow(hwnd, 320, AW_BLEND);
    SetTimer(hwnd, 1, 1500, nullptr);
    return hwnd;
}

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE, PWSTR, int show) {
    // Pemeriksa bentuk dan snapshot memotret PANEL, bukan gerbang: tanpa baris
    // ini semuanya akan memotret layar masuk dan melaporkan panel "hilang".
    // Bukti gerbang sendiri dibuat terpisah oleh --workspace-snapshots, yang
    // menyetel tahapnya sendiri sebelum memotret.
    if (hasArgument(L"--dialog-snapshots") || hasArgument(L"--panel-probe")
        || hasArgument(L"--panel-pairing-snapshot") || hasArgument(L"--panel-collapsed-snapshot")
        || hasArgument(L"--panel-snapshot")) {
        g.gate.succeed("probe@xydesk.my.id");
    }
    if (hasArgument(L"--dialog-snapshots"))return runDialogSnapshots(commandLineArgument(L"--dialog-snapshots"));
    if (hasArgument(L"--panel-probe")) {
        return runPanelProbe(commandLineArgument(L"--panel-probe"));
    }
    if (hasArgument(L"--panel-pairing-snapshot"))return runPanelSnapshot(commandLineArgument(L"--panel-pairing-snapshot"),Page::Pairing);
    if (hasArgument(L"--panel-collapsed-snapshot"))return runPanelSnapshot(commandLineArgument(L"--panel-collapsed-snapshot"),Page::Pairing,true);
    if (hasArgument(L"--panel-snapshot")) {
        return runPanelSnapshot(commandLineArgument(L"--panel-snapshot"));
    }

    workspaceProbe=hasArgument(L"--workspace-snapshots");
    // Proteksi pemuatan DLL: dependensi hanya dicari di folder aplikasi dan
    // System32 — menutup serangan "DLL planting" lewat folder kerja/unduhan.
    SetDefaultDllDirectories(LOAD_LIBRARY_SEARCH_SYSTEM32 | LOAD_LIBRARY_SEARCH_APPLICATION_DIR);
    SetProcessDPIAware();
    g_taskbarCreated = RegisterWindowMessageW(L"TaskbarCreated");

    WNDCLASSEXW wc{};
    wc.cbSize = sizeof(wc);
    wc.style = CS_DBLCLKS; // dua klik di judul = perbesar/pulihkan
    wc.hInstance = instance;
    wc.lpfnWndProc = windowProc;
    wc.lpszClassName = kClassName;
    wc.hIcon = LoadIconW(instance, MAKEINTRESOURCEW(IDI_XYDESK));
    wc.hIconSm = LoadIconW(instance, MAKEINTRESOURCEW(IDI_XYDESK));
    wc.hCursor = LoadCursorW(nullptr, IDC_ARROW);
    wc.hbrBackground = nullptr;
    if (!RegisterClassExW(&wc)) return 1;

    // Tanpa WS_CAPTION/WS_BORDER: judul, sudut, dan bayangan milik panel
    // sendiri. WS_SYSMENU tetap ada supaya Alt+F4 dan menu sistem bekerja.
    g.layout = xydesk::panel::computeLayout(96);
    RECT workArea{};
    if (!SystemParametersInfoW(SPI_GETWORKAREA, 0, &workArea, 0)) {
        workArea = RECT{0, 0, GetSystemMetrics(SM_CXSCREEN), GetSystemMetrics(SM_CYSCREEN)};
    }
    const int x = workArea.left + ((workArea.right - workArea.left) - g.layout.window.w) / 2;
    const int y = workArea.top + ((workArea.bottom - workArea.top) - g.layout.window.h) / 2;

    HWND window = CreateWindowExW(WS_EX_APPWINDOW, kClassName, kWindowTitle,
        WS_POPUP | WS_SYSMENU | WS_THICKFRAME | WS_CLIPCHILDREN, x, y, g.layout.window.w, g.layout.window.h,
        nullptr, nullptr, instance, nullptr);
    if (!window) return 1;
    // Tampilkan jendela secara normal di semua lingkungan (termasuk Cloud GPU VM seperti ThinkMay, Parsec, dan taskbar thumbnail DWM).
    SetWindowDisplayAffinity(window, 0);

    applyDpi(window, windowDpi(window), true);
    HWND splash = showSplash(instance);
    ShowWindow(window, (show == SW_SHOWMINNOACTIVE || show == SW_MINIMIZE || show == SW_SHOWMINIMIZED) ? show : SW_SHOWNORMAL);
    UpdateWindow(window);
    SetForegroundWindow(window);
    if (splash) {
        SetWindowPos(splash, HWND_TOPMOST, 0, 0, 0, 0,
            SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE);
    }

    if(workspaceProbe)return runWorkspaceEvidence(window,commandLineArgument(L"--workspace-snapshots"));
    MSG message{};
    while (GetMessageW(&message, nullptr, 0, 0) > 0) {
        if(routeEmbeddedMessage(message))continue;
        TranslateMessage(&message);
        DispatchMessageW(&message);
    }
    return static_cast<int>(message.wParam);
}
