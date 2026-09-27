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
#include <shellapi.h>

#include "resource.h"
#include "layout.h"
#include "engine_json.h"
#include "control_client.h"
#include "account_auth.h"
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
#include <future>
#include <chrono>

#if defined(_MSC_VER)
#pragma comment(lib, "user32.lib")
#pragma comment(lib, "gdi32.lib")
#pragma comment(lib, "shell32.lib")
#pragma comment(lib, "advapi32.lib")
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

// ── Palet "Paper" — dicerminkan dari web/src/style.css (kanonik sejak
// unifikasi Sep 2026: web = acuan). Latar terang, aksen ungu #7c3aed, status
// memakai varian teks-terang tokens.dart supaya kontras di atas putih. ──
constexpr COLORREF kBackground = RGB(255, 255, 255); // --bg
constexpr COLORREF kSurface = RGB(255, 255, 255);    // kartu putih + garis tepi
constexpr COLORREF kSurface2 = RGB(244, 246, 248);   // --overlay
constexpr COLORREF kSurface3 = RGB(229, 232, 236);   // overlay ditekan/hover
constexpr COLORREF kSurfacePressed = RGB(222, 226, 231);
constexpr COLORREF kEdge = RGB(228, 228, 231);       // garis tepi zinc-200
constexpr COLORREF kText = RGB(24, 24, 27);          // --ink
constexpr COLORREF kMuted = RGB(82, 82, 91);         // --ink-soft
constexpr COLORREF kDisabled = RGB(154, 154, 162);   // --text-low
constexpr COLORREF kOnAccent = RGB(255, 255, 255);   // teks di atas ungu
constexpr COLORREF kAccent = RGB(48, 53, 60);      // --accent #7c3aed
constexpr COLORREF kAccentHover = RGB(64, 71, 80);
constexpr COLORREF kAccentPressed = RGB(34, 39, 45); // --accent-deep
constexpr COLORREF kGood = RGB(22, 115, 71);         // --success
constexpr COLORREF kWarn = RGB(133, 84, 0);          // --warning
constexpr COLORREF kBad = RGB(165, 42, 54);          // --danger

constexpr int kTrayOpen = 1010;
constexpr int kTrayStart = 1011;
constexpr int kTrayStop = 1012;
constexpr int kTrayWeb = 1013;
constexpr int kTrayQuit = 1014;
constexpr int kTrayRestart = 1015;

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
    std::wstring lastError;
    HANDLE process = nullptr;
    HANDLE job = nullptr;
    HANDLE logFile = nullptr;
    bool running = false;
    bool startRequested = false;
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
    auto stale = [] {
        g.captureSeen = false;
        g.captureBackend.clear();
        g.captureNote2.clear();
        g.sessionMismatch = false;
        g.procSession = g.activeSession = -1;
        g.captureWarn = true;
        g.captureNote = L"Status engine belum tersedia atau sudah kedaluwarsa.";
        if (g.running) setStatus(g.captureNote, kWarn);
    };
    if (!g.process || !g.running) { stale(); return; }
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
    if (pressed) return ButtonPalette{kSurfacePressed, kText, radius, false};
    if (hot) return ButtonPalette{kSurface3, kText, radius, true};
    return ButtonPalette{kSurface2, kText, radius, false};
}

bool workspaceProbe=false;
xydesk::session_view::Snapshot sessionView;
std::future<xydesk::session_view::Snapshot> sessionViewPending;
unsigned sessionViewPid=0;
ULONGLONG sessionViewNext=0;
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
    case Target::CopyLink:
    case Target::ConnectionQr:
    case Target::DeviceLink: return !xydesk::panel_control::deviceLink(g.deviceId).empty();
    case Target::Start:
        return !g.running;
    case Target::Stop:
        return g.running;
    case Target::CopyId:
        return !g.deviceId.empty();
    case Target::CopyPassword:
        return !g.pairingCode.empty();
    default:
        return true;
    }
}

std::wstring targetLabel(Target target) {
    switch (target) {
    case Target::Start: return L"Mulai host";
    case Target::Stop: return L"Hentikan";
    case Target::Restart: return L"Restart";
    case Target::Web: return L"Buka XyDesk Web";
    case Target::OpenLog: return L"Buka log host";
    case Target::RunHost: return L"Jalankan host di sesi ini";
    case Target::CopyId:
    case Target::CopyPassword: return L"Salin";
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

// Tiga tombol caption: perkecil, perbesar/pulihkan, tutup. Rasa hover-nya
// sama (latar bulat halus, glyph menerang); hanya tutup yang memerah supaya
// makna destruktifnya tetap jelas. Tanpa warna mencolok lain — panel harus
// tetap bersih.
void paintCaptionButtons(Surface& surface, const PanelLayout& layout, HDC dc) {
    const int radius = xydesk::panel::scaled(10, layout.scalePct);
    const int thickness = std::max(1, xydesk::panel::scaled(2, layout.scalePct));

    const auto hoverFill = [&](Target target, const Rect& rect) {
        if (g.hot == target || g.pressed == target) {
            fillRoundedOpaque(surface, rect, radius,
                g.pressed == target ? kSurfacePressed : kSurface2);
        }
    };
    const auto glyphColor = [&](Target target, COLORREF hotColor) {
        return g.hot == target ? hotColor : kMuted;
    };

    // Perkecil: satu garis mendatar, bobotnya sama dengan lengan silang.
    {
        const Rect& rect = layout.minimizeButton;
        hoverFill(Target::Minimize, rect);
        const int arm = xydesk::panel::scaled(13, layout.scalePct) / 2;
        const int cx = xydesk::panel::centerX(rect);
        const int cy = xydesk::panel::centerY(rect);
        drawGlyphSegments(dc, glyphColor(Target::Minimize, kText), thickness,
            {{{cx - arm, cy}, {cx + arm + 1, cy}}});
    }

    // Perbesar: kotak kosong. Saat sudah besar, glyph berubah jadi dua kotak
    // bertumpuk (pulihkan), mengikuti kebiasaan Windows.
    {
        const Rect& rect = layout.maximizeButton;
        hoverFill(Target::Maximize, rect);
        const COLORREF color = glyphColor(Target::Maximize, kText);
        const int cx = xydesk::panel::centerX(rect);
        const int cy = xydesk::panel::centerY(rect);
        if (!g.maximized) {
            const int half = xydesk::panel::scaled(12, layout.scalePct) / 2;
            const int x0 = cx - half, y0 = cy - half;
            const int x1 = cx + half, y1 = cy + half;
            drawGlyphSegments(dc, color, thickness,
                {{{x0, y0}, {x1, y0}}, {{x1, y0}, {x1, y1}},
                 {{x1, y1}, {x0, y1}}, {{x0, y1}, {x0, y0}}});
        } else {
            const int size = xydesk::panel::scaled(12, layout.scalePct);
            const int offset = xydesk::panel::scaled(3, layout.scalePct);
            const int back = size - offset;
            const int bx = cx - size / 2 + offset, by = cy - size / 2 - offset;
            const int fx = bx - offset, fy = by + offset;
            // Kotak belakang cukup dua garis (atas + kanan) supaya tidak
            // ramai di ukuran sekecil ini.
            drawGlyphSegments(dc, color, thickness,
                {{{bx, by}, {bx + back, by}}, {{bx + back, by}, {bx + back, by + back}},
                 {{fx, fy}, {fx + back, fy}}, {{fx + back, fy}, {fx + back, fy + back}},
                 {{fx + back, fy + back}, {fx, fy + back}}, {{fx, fy + back}, {fx, fy}}});
        }
    }

    // Tutup: silang dari dua garis tebal; hover memerah sebagai penegas.
    {
        const Rect& rect = layout.closeButton;
        hoverFill(Target::Close, rect);
        const int arm = xydesk::panel::scaled(13, layout.scalePct) / 2;
        const int cx = xydesk::panel::centerX(rect);
        const int cy = xydesk::panel::centerY(rect);
        drawGlyphSegments(dc, glyphColor(Target::Close, kBad), thickness,
            {{{cx - arm, cy - arm}, {cx + arm + 1, cy + arm + 1}},
             {{cx + arm, cy - arm}, {cx - arm - 1, cy + arm + 1}}});
    }
}

void paintLogo(Surface& surface, const PanelLayout& layout, HDC dc) {
    (void)surface;
    HICON icon=static_cast<HICON>(LoadImageW(GetModuleHandleW(nullptr),MAKEINTRESOURCEW(IDI_XYDESK),IMAGE_ICON,layout.logo.w,layout.logo.h,LR_SHARED));
    if(icon)DrawIconEx(dc,layout.logo.x,layout.logo.y,icon,layout.logo.w,layout.logo.h,0,nullptr,DI_NORMAL);
}

// ── Sidebar ────────────────────────────────────────────────────────────
// Ikon digambar dari garis/busur sederhana (stroke 2px) supaya panel tidak
// "semua teks": bentuknya yang bicara, label kecil hanya penegas.
void paintSidebarIcon(HDC dc, Page page, const Rect& icon, COLORREF color) {
    // One icon family, one box and one weight for every navigation item.
    const wchar_t* glyph=L"\uE80F";
    switch(page){case Page::Status:glyph=L"\uE80F";break;case Page::Connections:glyph=L"\uE968";break;
    case Page::Pairing:glyph=L"\uE72E";break;case Page::Control:glyph=L"\uE7F4";break;
    case Page::Settings:glyph=L"\uE713";break;case Page::Account:glyph=L"\uE77B";break;case Page::Help:glyph=L"\uE897";break;}
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

void tickAnimation(HWND hwnd) {
    bool more = false;
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
    const std::wstring line2 = g.captureWarn ? g.captureNote
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
    const int pad=px(24);int x=card.x+pad,y=card.y+pad;int width=card.w-2*pad;
    workspaceText(dc,L"KONEKSI AKTIF",{x,y,width,px(20)},g.fontCaps,kMuted);y+=px(34);
    const bool known=sessionView.known;
    const std::wstring title=!known?L"Status koneksi belum tersedia":sessionView.active?(sessionView.name.empty()?L"Perangkat tanpa nama":sessionView.name):L"Belum ada perangkat terhubung";
    int photoWidth=0;
    if(detailed&&sessionView.active&&xydesk::session_view::redmiNote12(sessionView.name)){
        static HBITMAP photo=LoadBitmapW(GetModuleHandleW(nullptr),MAKEINTRESOURCEW(IDB_REDMI_NOTE12));
        if(photo){BITMAP bitmap{};GetObjectW(photo,sizeof(bitmap),&bitmap);const int h=px(220),w=bitmap.bmWidth*h/bitmap.bmHeight;HDC source=CreateCompatibleDC(dc);auto old=SelectObject(source,photo);
            SetStretchBltMode(dc,HALFTONE);StretchBlt(dc,card.right()-pad-w,y,w,h,source,0,0,bitmap.bmWidth,bitmap.bmHeight,SRCCOPY);SelectObject(source,old);DeleteDC(source);photoWidth=w+pad;}
    }
    workspaceText(dc,title,{x,y,width-photoWidth,px(56)},g.fontTitle);y+=px(64);
    if(sessionView.active&&known){
        workspaceText(dc,L"Platform: "+(sessionView.platform.empty()?L"Tidak dilaporkan":sessionView.platform),{x,y,width-photoWidth,px(28)},g.fontBody);y+=px(32);
        workspaceText(dc,L"Durasi sesi: "+std::to_wstring(sessionView.seconds/60)+L" menit "+std::to_wstring(sessionView.seconds%60)+L" detik",{x,y,width-photoWidth,px(28)},g.fontBody);y+=px(32);
        if(detailed){workspaceText(dc,L"ID klien: "+sessionView.id,{x,y,width-photoWidth,px(44)},g.fontSmall,kMuted);y+=px(48);
            workspaceText(dc,photoWidth?L"Foto produk Xiaomi. Warna ilustratif; bukan warna HP yang terdeteksi.":L"Foto model belum tersedia. Nama atau jenis browser saja tidak menentukan model HP.",{x,y,width,px(48)},g.fontSmall,kMuted);}
    }else workspaceText(dc,known?L"Buka Akses host, bagikan link atau scan QR, lalu izinkan koneksi dengan password.":L"Menunggu status dari kanal privat engine. Ini bukan berarti tidak ada koneksi.",{x,y,width,px(60)},g.fontBody,kMuted);
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
        workspaceText(dc,L"1. Bagikan link atau QR perangkat ini.\n2. Browser memakai izin tersimpan yang valid, atau meminta password pairing.\n3. Ubah password lewat Pengaturan untuk mencabut izin browser lama.",{note.x+px(24),y+px(66),note.w-px(48),note.h-px(80)},g.fontBody,kMuted);
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
    if(height>=px(200))workspaceText(dc,L"Link, QR dan password tersedia di Akses host. Jangan bagikan password di ruang publik.",{left.x+px(24),y+px(96),half-px(48),px(48)},g.fontBody,kMuted);
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
    const wchar_t* title=L"Semua di satu tempat.";
    const wchar_t* detail=L"Akses perangkat, lihat koneksi, dan kelola host lu.";
    switch(page){
    case Page::Connections:title=L"Koneksi perangkat";detail=L"Perangkat yang sedang terhubung ke PC ini.";break;
    case Page::Pairing:title=L"Akses ke PC ini";detail=L"Bagikan link atau QR. Password tetap di bawah kendali lu.";break;
    case Page::Control:title=L"Kontrol host";detail=L"Kelola proses host tanpa keluar dari workspace.";break;
    case Page::Settings:title=L"Pengaturan host";detail=L"Preferensi host dan keamanan akses dalam satu halaman.";break;
    case Page::Account:title=L"Profil & akun";detail=L"Akun XyDesk tetap terpisah dari identitas host Windows.";break;
    case Page::Help:title=L"Bantuan";detail=L"Langkah singkat untuk mulai terhubung dengan aman.";break;
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
        paintIdentityCard(surface, layout, dc, layout.passwordCard, layout.passwordLabel, layout.passwordValue,
            layout.passwordCopy, L"Kode pairing", g.pairingCode, Target::CopyPassword);
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
        workspaceText(dc,L"Kontrol proses host\n\nHentikan memutus layanan host. Restart menyalakan ulang proses host. Tombol X hanya menyembunyikan aplikasi ke tray.\n\nPengaturan password dan video ada di halaman Pengaturan. Akun aplikasi terpisah dari identitas host Windows.", {note.x+px(24),note.y+px(24),note.w-px(48),note.h-px(48)},g.fontBody,kMuted);
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
    shift(s.captureLine3); shift(s.runHost);
    shift(s.idCard); shift(s.idLabel); shift(s.idValue); shift(s.idCopy);
    shift(s.passwordCard); shift(s.passwordLabel); shift(s.passwordValue); shift(s.passwordCopy);
    shift(s.start); shift(s.stop); shift(s.restart); shift(s.web); shift(s.openLog);
    return s;
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
    if (g.job) TerminateJobObject(g.job, 0);
    closeHostHandles();
    setStatus(L"Host berhenti", kMuted);
}

void restartHost() {
    stopHost();
    startHost();
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

void showTrayMenu(HWND hwnd) {
    HMENU menu = CreatePopupMenu();
    if (!menu) return;
    AppendMenuW(menu, MF_STRING, kTrayOpen, L"Buka Control Panel");
    AppendMenuW(menu, g.running ? MF_GRAYED : MF_STRING, kTrayStart, L"Mulai host");
    AppendMenuW(menu, g.running ? MF_STRING : MF_GRAYED, kTrayStop, L"Hentikan host");
    AppendMenuW(menu, MF_STRING, kTrayRestart, L"Restart host");
    AppendMenuW(menu, MF_STRING, kTrayWeb, L"Buka XyDesk Web");
    AppendMenuW(menu, MF_SEPARATOR, 0, nullptr);
    AppendMenuW(menu, MF_STRING, kTrayQuit, L"Keluar XyDesk");
    SetMenuDefaultItem(menu, kTrayOpen, FALSE);
    POINT point{};
    if (!GetCursorPos(&point)) {
        point.x = 0;
        point.y = 0;
    }
    SetForegroundWindow(hwnd);
    TrackPopupMenu(menu, TPM_RIGHTBUTTON | TPM_BOTTOMALIGN | TPM_LEFTALIGN | TPM_NOANIMATION,
        point.x, point.y, 0, hwnd, nullptr);
    DestroyMenu(menu);
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
    const bool primary=item->CtlID==IDC_APPLY_BITRATE||item->CtlID==IDC_APPLY_PASSWORD||item->CtlID==IDC_ACCOUNT_LOGIN;
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

INT_PTR CALLBACK settingsDialog(HWND hwnd,UINT message,WPARAM wParam,LPARAM lParam){
    if(message==WM_DRAWITEM&&paintEmbeddedButton(lParam))return TRUE;
    if(message==WM_CTLCOLORDLG)return reinterpret_cast<INT_PTR>(GetStockObject(WHITE_BRUSH));
    if(message==WM_CTLCOLORSTATIC){SetBkColor(reinterpret_cast<HDC>(wParam),RGB(255,255,255));SetTextColor(reinterpret_cast<HDC>(wParam),RGB(32,35,40));return reinterpret_cast<INT_PTR>(GetStockObject(WHITE_BRUSH));}

    auto state=reinterpret_cast<SettingsState*>(GetWindowLongPtrW(hwnd,DWLP_USER));
    constexpr int rates[]={0,1,2,4,8,15,25,50};
    if(message==WM_INITDIALOG){
        state=reinterpret_cast<SettingsState*>(lParam);SetWindowLongPtrW(hwnd,DWLP_USER,lParam);
        for(int rate:rates){const auto label=rate?std::to_wstring(rate)+L" Mbps":L"Default engine (8 Mbps, bukan adaptif)";SendDlgItemMessageW(hwnd,IDC_BITRATE,CB_ADDSTRING,0,reinterpret_cast<LPARAM>(label.c_str()));}
        for(int i=0;i<8;++i)if(g.panelBitrate==rates[i])SendDlgItemMessageW(hwnd,IDC_BITRATE,CB_SETCURSEL,i,0);
        SendDlgItemMessageW(hwnd,IDC_PASSWORD,EM_SETLIMITTEXT,128,0);
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
            SetDlgItemTextW(hwnd,IDC_SETTING_STATUS,ok?(state->requestedBitrate>=0?L"Bitrate diterapkan. Klien dapat mengubah target ini.":L"Password tersimpan. Akses browser lama dicabut; lihat Akses host."):L"Gagal atau timeout. Periksa host sebelum mencoba lagi.");
            state->requestedBitrate=-1;
        }
        const bool ready=controlChannel.endpoint.has_value()&&g.running;
        const bool busy=state->pending.valid();
        for(int id:{IDC_APPLY_BITRATE,IDC_APPLY_PASSWORD,IDC_NEW_PASSWORD,IDC_BITRATE,IDC_PASSWORD})EnableWindow(GetDlgItem(hwnd,id),ready&&!busy);
        if(!ready&&!busy){state->readyShown=false;SetDlgItemTextW(hwnd,IDC_SETTING_STATUS,L"Kontrol privat belum tersedia. Mulai/restart engine dari panel ini.");}
        else if(ready&&!busy&&!state->readyShown){state->readyShown=true;SetDlgItemTextW(hwnd,IDC_SETTING_STATUS,L"Kanal privat siap. Pilih bitrate atau isi password baru.");}
        return TRUE;
    }
    if(message==WM_CLOSE||(message==WM_COMMAND&&LOWORD(wParam)==IDCANCEL)){
        if(GetWindowLongPtrW(hwnd,GWL_STYLE)&WS_CHILD){goPage(g.window,Page::Status);return TRUE;}
        if(state->pending.valid()){SetDlgItemTextW(hwnd,IDC_SETTING_STATUS,L"Tunggu hasil permintaan sebelum menutup.");return TRUE;}
        KillTimer(hwnd,1);EndDialog(hwnd,0);return TRUE;
    }
    if(message==WM_COMMAND&&LOWORD(wParam)==IDC_PASSWORD_SHOW){SendDlgItemMessageW(hwnd,IDC_PASSWORD,EM_SETPASSWORDCHAR,IsDlgButtonChecked(hwnd,IDC_PASSWORD_SHOW)==BST_CHECKED?0:0x25CF,0);InvalidateRect(GetDlgItem(hwnd,IDC_PASSWORD),nullptr,TRUE);return TRUE;}
    if(message!=WM_COMMAND||state->pending.valid()||!controlChannel.endpoint)return FALSE;
    const int id=LOWORD(wParam);std::string body;
    state->requestedBitrate=-1;
    if(id==IDC_APPLY_BITRATE){
        const auto index=SendDlgItemMessageW(hwnd,IDC_BITRATE,CB_GETCURSEL,0,0);
        if(index<0||index>=8){SetDlgItemTextW(hwnd,IDC_SETTING_STATUS,L"Pilih target bitrate terlebih dahulu.");return TRUE;}
        state->requestedBitrate=rates[index];body="{\"action\":\"video-bitrate\",\"bitrate_mbps\":"+std::to_string(state->requestedBitrate)+"}";
    }else if(id==IDC_NEW_PASSWORD){
        if(MessageBoxW(hwnd,L"Ganti password pairing dengan password acak baru?",L"Konfirmasi",MB_YESNO|MB_ICONQUESTION)!=IDYES)return TRUE;
        body="{\"action\":\"new-password\"}";
    }else if(id==IDC_APPLY_PASSWORD){
        wchar_t password[129]{};GetDlgItemTextW(hwnd,IDC_PASSWORD,password,129);
        const int size=WideCharToMultiByte(CP_UTF8,WC_ERR_INVALID_CHARS,password,-1,nullptr,0,nullptr,nullptr);
        if(size<=1){SecureZeroMemory(password,sizeof(password));SetDlgItemTextW(hwnd,IDC_SETTING_STATUS,L"Isi password minimal 6 karakter; engine memvalidasi aturannya.");return TRUE;}
        std::string text(size,'\0');WideCharToMultiByte(CP_UTF8,WC_ERR_INVALID_CHARS,password,-1,text.data(),size,nullptr,nullptr);text.resize(size-1);
        SecureZeroMemory(password,sizeof(password));
        body="{\"action\":\"set-password\",\"password\":"+xydesk::panel_control::quoteJson(text)+"}";
        SecureZeroMemory(text.data(),text.size());SetDlgItemTextW(hwnd,IDC_PASSWORD,L"");
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
    if(message==WM_DRAWITEM&&paintEmbeddedButton(lParam))return TRUE;
    if(message==WM_CTLCOLORDLG)return reinterpret_cast<INT_PTR>(GetStockObject(WHITE_BRUSH));
    if(message==WM_CTLCOLORSTATIC){SetBkColor(reinterpret_cast<HDC>(wParam),RGB(255,255,255));SetTextColor(reinterpret_cast<HDC>(wParam),RGB(32,35,40));return reinterpret_cast<INT_PTR>(GetStockObject(WHITE_BRUSH));}

    auto state=reinterpret_cast<AccountDialogState*>(GetWindowLongPtrW(hwnd,DWLP_USER));
    if(message==WM_INITDIALOG){
        state=reinterpret_cast<AccountDialogState*>(lParam);SetWindowLongPtrW(hwnd,DWLP_USER,lParam);
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
    if(message==WM_DRAWITEM&&paintEmbeddedButton(lParam))return TRUE;
    if(message==WM_CTLCOLORDLG)return reinterpret_cast<INT_PTR>(GetStockObject(WHITE_BRUSH));
    if(message==WM_CTLCOLORSTATIC){SetBkColor(reinterpret_cast<HDC>(wParam),RGB(255,255,255));SetTextColor(reinterpret_cast<HDC>(wParam),RGB(32,35,40));return reinterpret_cast<INT_PTR>(GetStockObject(WHITE_BRUSH));}

    if(message==WM_CLOSE||(message==WM_COMMAND&&LOWORD(wParam)==IDCANCEL)){if(GetWindowLongPtrW(hwnd,GWL_STYLE)&WS_CHILD)goPage(g.window,Page::Status);else EndDialog(hwnd,0);return TRUE;}return FALSE;
}
struct QrDialogState {qrcodegen::QrCode code;std::wstring link;};
INT_PTR CALLBACK qrDialog(HWND hwnd,UINT message,WPARAM wParam,LPARAM lParam){
    auto state=reinterpret_cast<QrDialogState*>(GetWindowLongPtrW(hwnd,DWLP_USER));
    if(message==WM_INITDIALOG){state=reinterpret_cast<QrDialogState*>(lParam);SetWindowLongPtrW(hwnd,DWLP_USER,lParam);SetDlgItemTextW(hwnd,IDC_CONNECTION_LINK,state->link.c_str());return TRUE;}
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
    case Target::RunHost:
        // Jalan pintas takeover: mulai instance host di sesi panel ini
        // (peluncur resmi: job object + identitas). Instance tersebut akan
        // meminta leader lama turun lewat berkas stepdown dan mengambil alih.
        if (startHost()) {
            setFlash(L"Host dijalankan di sesi ini — menunggu takeover…", kAccent);
        } else {
            setStatus(g.lastError.empty() ? L"Gagal memulai host di sesi ini." : g.lastError, kBad);
        }
        break;
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
    const Target target = xydesk::panel::targetAt(g.layout, g.page, x, y);
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
    const Target target = xydesk::panel::targetAt(g.layout, g.page, client.x, client.y);
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
        return HTCLIENT;
    case Target::TitleBar:
        return HTCAPTION; // geser jendela dari area judul
    default:
        // Di luar permukaan (hanya ada bayangan): biarkan klik lewat.
        return g.layout.panel.contains(client.x,client.y)?HTCLIENT:HTTRANSPARENT;
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
        // Panel adalah satu pintu: dibuka berarti host hidup tanpa tombol
        // kedua. Post agar jendela selesai dibuat dulu.
        if(!workspaceProbe)PostMessageW(hwnd, kAutoStartMessage, 0, 0);
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

    case WM_MOUSEMOVE:
        trackMouse(hwnd);
        updateHover(xFromLParam(lParam), yFromLParam(lParam));
        return 0;

    case WM_MOUSELEAVE:
        g.trackingMouse = false;
        if (g.hot != Target::None) {
            g.hot = Target::None;
            renderPanel();
        }
        return 0;

    case WM_LBUTTONDOWN: {
        const Target target = xydesk::panel::targetAt(g.layout, g.page, xFromLParam(lParam), yFromLParam(lParam), g.sessionMismatch);
        if (target != Target::None && target != Target::TitleBar && targetEnabled(target)) {
            g.pressed = target;
            SetCapture(hwnd);
            renderPanel();
        }
        return 0;
    }

    case WM_LBUTTONUP: {
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
        switch (LOWORD(wParam)) {
        case kTrayOpen: openPanel(hwnd); return 0;
        case kTrayStart: startHost(); return 0;
        case kTrayStop: stopHost(); return 0;
        case kTrayRestart: restartHost(); return 0;
        case kTrayWeb: ShellExecuteW(hwnd, L"open", kWebUrl, nullptr, nullptr, SW_SHOWNORMAL); return 0;
        case kTrayQuit:
            removeTrayIcon();
            DestroyWindow(hwnd);
            return 0;
        default:
            break;
        }
        return 0;

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

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE, PWSTR, int show) {
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

    applyDpi(window, windowDpi(window), true);
    ShowWindow(window, show);
    UpdateWindow(window);
    SetForegroundWindow(window);

    if(workspaceProbe)return runWorkspaceEvidence(window,commandLineArgument(L"--workspace-snapshots"));
    MSG message{};
    while (GetMessageW(&message, nullptr, 0, 0) > 0) {
        if(routeEmbeddedMessage(message))continue;
        TranslateMessage(&message);
        DispatchMessageW(&message);
    }
    return static_cast<int>(message.wParam);
}
