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

#include <windows.h>
#include <shellapi.h>

#include "resource.h"
#include "layout.h"

#include <algorithm>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

#if defined(_MSC_VER)
#pragma comment(lib, "user32.lib")
#pragma comment(lib, "gdi32.lib")
#pragma comment(lib, "shell32.lib")
#endif

namespace {

using xydesk::panel::Page;
using xydesk::panel::PanelLayout;
using xydesk::panel::Rect;
using xydesk::panel::Target;

constexpr wchar_t kClassName[] = L"XyDeskNativeControlPanel";
constexpr wchar_t kWindowTitle[] = L"XyDesk Control Panel";
constexpr wchar_t kWebUrl[] = L"https://app.xydesk.my.id";
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
constexpr COLORREF kSurface2 = RGB(245, 243, 255);   // --overlay
constexpr COLORREF kSurface3 = RGB(233, 229, 250);   // overlay ditekan/hover
constexpr COLORREF kSurfacePressed = RGB(237, 233, 254);
constexpr COLORREF kEdge = RGB(228, 228, 231);       // garis tepi zinc-200
constexpr COLORREF kText = RGB(24, 24, 27);          // --ink
constexpr COLORREF kMuted = RGB(82, 82, 91);         // --ink-soft
constexpr COLORREF kDisabled = RGB(154, 154, 162);   // --text-low
constexpr COLORREF kOnAccent = RGB(255, 255, 255);   // teks di atas ungu
constexpr COLORREF kAccent = RGB(124, 58, 237);      // --accent #7c3aed
constexpr COLORREF kAccentHover = RGB(139, 92, 246);
constexpr COLORREF kAccentPressed = RGB(91, 33, 182); // --accent-deep
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
    HFONT fontTitle = nullptr;
    HFONT fontBody = nullptr;
    HFONT fontSmall = nullptr;
    HFONT fontMono = nullptr;
    HFONT fontLogo = nullptr;
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
    bool layered = true;
    // Kesehatan capture dari engine (berkas capture.json): buat kartu Status
    // jujur soal layar hitam / sesi berbeda.
    std::wstring captureBackend;
    std::wstring captureNote;
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
};

AppState g;
UINT g_taskbarCreated = 0;

void removeTrayIcon();
void showTrayMenu(HWND hwnd);
bool startHost();
void stopHost();
void renderPanel();

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
    const std::string needle = std::string("\"") + key + "\":\"";
    const auto start = json.find(needle);
    if (start == std::string::npos) return L"";
    const auto valueStart = start + needle.size();
    const auto end = json.find('"', valueStart);
    if (end == std::string::npos) return L"";
    std::wstring result;
    for (size_t i = valueStart; i < end; ++i) {
        result.push_back(static_cast<wchar_t>(static_cast<unsigned char>(json[i])));
    }
    return result;
}

bool readIdentity() {
    SECURITY_ATTRIBUTES sa{sizeof(SECURITY_ATTRIBUTES), nullptr, TRUE};
    HANDLE readPipe = nullptr;
    HANDLE writePipe = nullptr;
    if (!CreatePipe(&readPipe, &writePipe, &sa, 0)) return false;
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
        return false;
    }

    std::string output;
    char buffer[1024];
    DWORD got = 0;
    while (ReadFile(readPipe, buffer, sizeof(buffer), &got, nullptr) && got) {
        output.append(buffer, buffer + got);
    }
    CloseHandle(readPipe);
    WaitForSingleObject(pi.hProcess, 5000);
    DWORD exitCode = 1;
    GetExitCodeProcess(pi.hProcess, &exitCode);
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
    if (exitCode != 0) return false;

    const std::wstring id = jsonString(output, "deviceId");
    const std::wstring password = jsonString(output, "password");
    if (id.empty() || password.empty()) return false;
    g.deviceId = id;
    g.pairingCode = password;
    return true;
}

bool jsonFlag(const std::string& json, const char* key) {
    const std::string needle = std::string("\"") + key + "\":true";
    return json.find(needle) != std::string::npos;
}

int jsonNumber(const std::string& json, const char* key) {
    const std::string needle = std::string("\"") + key + "\":";
    const auto start = json.find(needle);
    if (start == std::string::npos) return -1;
    return std::atoi(json.c_str() + start + needle.size());
}

// Engine menulis capture.json sekali setiap beberapa detik: backend capture,
// apakah frame hitam semua (layar terkunci / secure desktop), dan apakah
// proses hidup di sesi yang berbeda dari sesi yang memegang layar aktif
// (klasik RDP: BitBlt dari sesi lain selalu hitam). Panel menampilkan ini
// apa adanya supaya layar hitam di client tidak jadi misteri.
void readCaptureStatus() {
    // Sama dengan config_dir() engine: XYDESK_HOME dulu, lalu USERPROFILE\.xydesk.
    const std::wstring path = [&] {
        wchar_t home[MAX_PATH]{};
        DWORD n = GetEnvironmentVariableW(L"XYDESK_HOME", home, ARRAYSIZE(home));
        if (n && n < ARRAYSIZE(home)) return std::wstring(home, n) + L"\\capture.json";
        n = GetEnvironmentVariableW(L"USERPROFILE", home, ARRAYSIZE(home));
        const std::wstring base = n && n < ARRAYSIZE(home)
            ? std::wstring(home, n)
            : moduleDirectory();
        return base + L"\\.xydesk\\capture.json";
    }();
    HANDLE file = CreateFileW(path.c_str(), GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE,
        nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE) return;
    std::string json;
    char buffer[1024];
    DWORD got = 0;
    while (ReadFile(file, buffer, sizeof(buffer), &got, nullptr) && got) json.append(buffer, buffer + got);
    CloseHandle(file);

    const std::wstring backend = jsonString(json, "backend");
    const bool mismatch = jsonFlag(json, "session_mismatch");
    const bool warn = jsonFlag(json, "black_frames") || mismatch;
    const std::wstring procUser = jsonString(json, "proc_user");
    const std::wstring activeUser = jsonString(json, "active_user");
    const int procSession = jsonNumber(json, "proc_session");
    const int activeSession = jsonNumber(json, "active_session");
    std::wstring note;
    if (mismatch) {
        // Menunjuk persis: host hidup sebagai siapa di sesi mana, layar aktif
        // milik siapa — dan apa yang harus dilakukan (tanpa trik pindah sesi;
        // pemilik menolak, dan antar-user memang tidak mungkin user-mode).
        note = L"Host jalan sebagai " + (procUser.empty() ? L"?" : procUser) +
            L" (sesi " + std::to_wstring(procSession) + L"); layar aktif milik " +
            (activeUser.empty() ? L"?" : activeUser) + L" (sesi " +
            std::to_wstring(activeSession) +
            L"). Capture antar-sesi selalu hitam: tutup XyDesk di sesi lama, jalankan dari sesi " +
            (activeUser.empty() ? L"aktif" : activeUser) + L".";
    } else if (jsonFlag(json, "black_frames")) {
        note = L"Capture menghasilkan frame hitam — layar mungkin terkunci atau di secure desktop. Buka kunci PC host.";
    }
    if (backend != g.captureBackend || warn != g.captureWarn || note != g.captureNote ||
        mismatch != g.sessionMismatch || procUser != g.procUser || activeUser != g.activeUser) {
        g.captureBackend = backend;
        g.captureWarn = warn;
        g.captureNote = note;
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
    if (target == Target::Start) {
        if (pressed) return ButtonPalette{kAccentPressed, kOnAccent, radius, false};
        if (hot) return ButtonPalette{kAccentHover, kOnAccent, radius, false};
        return ButtonPalette{kAccent, kOnAccent, radius, false};
    }
    if (pressed) return ButtonPalette{kSurfacePressed, kText, radius, false};
    if (hot) return ButtonPalette{kSurface3, kText, radius, true};
    return ButtonPalette{kSurface2, kText, radius, false};
}

bool targetEnabled(Target target) {
    switch (target) {
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
    case Target::CopyId:
    case Target::CopyPassword: return L"Salin";
    default: return L"";
    }
}

// ── Menggambar panel ---------------------------------------------------

void paintStatusCard(Surface& surface, const PanelLayout& layout, HDC dc) {
    const Rect& card = layout.statusCard;
    fillRoundedOpaque(surface, card, layout.radiusCard, kSurface2);
    strokeRounded(surface, card, layout.radiusCard, kEdge, 1);

    const COLORREF dotColor = g.statusColor;
    fillCircleOpaque(surface, Rect{layout.statusDot.x - 4, layout.statusDot.y - 4, layout.statusDot.w + 8, layout.statusDot.h + 8},
        mixColor(kSurface2, dotColor, 0.22f));
    fillCircleOpaque(surface, layout.statusDot, dotColor);

    const std::wstring line1 = g.flashText.empty() ? g.statusText : g.flashText;
    drawTextLine(dc, line1, layout.statusLine1, g.fontBody, kText,
        DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);

    const std::wstring logLine = g.logPath.empty() ? std::wstring(L"Log host belum dibuat") : (L"Log: " + g.logPath);
    drawTextLine(dc, logLine, layout.statusLine2, g.fontSmall, kMuted,
        DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_PATH_ELLIPSIS);
}

void paintIdentityCard(Surface& surface, const PanelLayout& layout, HDC dc, const Rect& card, const Rect& label,
    const Rect& value, const Rect& copy, const wchar_t* labelText, const std::wstring& valueText, Target copyTarget) {
    fillRoundedOpaque(surface, card, layout.radiusCard, kSurface2);
    strokeRounded(surface, card, layout.radiusCard, kEdge, 1);
    drawTextLine(dc, labelText, label, g.fontSmall, kMuted, DT_LEFT | DT_VCENTER | DT_SINGLELINE);

    const bool hasValue = !valueText.empty();
    drawTextLine(dc, hasValue ? valueText : std::wstring(L"Belum tersedia"), value, g.fontMono,
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
    const HGDIOBJ previousPen = SelectObject(dc, CreatePen(PS_SOLID, thickness, color));
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
    fillRoundedOpaque(surface, layout.logo, xydesk::panel::scaled(9, layout.scalePct), kAccent);
    drawTextCentered(dc, L"X", layout.logo, g.fontLogo, kText);
}

// ── Sidebar ────────────────────────────────────────────────────────────
// Ikon digambar dari garis/busur sederhana (stroke 2px) supaya panel tidak
// "semua teks": bentuknya yang bicara, label kecil hanya penegas.
void paintSidebarIcon(HDC dc, Page page, const Rect& icon, COLORREF color) {
    const int s = g.layout.scalePct;
    const int thickness = std::max(1, xydesk::panel::scaled(2, s));
    const int x0 = icon.x + 1, y0 = icon.y + 1;
    const int x1 = icon.right() - 2, y1 = icon.bottom() - 2;
    const int cx = xydesk::panel::centerX(icon);
    const int cy = xydesk::panel::centerY(icon);
    switch (page) {
    case Page::Status: {
        // Denyut aktivitas: datar – puncak – lembah – datar.
        drawGlyphSegments(dc, color, thickness, {
            {{x0, cy}, {x0 + (x1 - x0) / 4, cy}},
            {{x0 + (x1 - x0) / 4, cy}, {cx, y0}},
            {{cx, y0}, {x0 + 3 * (x1 - x0) / 4, y1}},
            {{x0 + 3 * (x1 - x0) / 4, y1}, {x1, cy}}});
        break;
    }
    case Page::Pairing: {
        // Kunci: lingkaran kecil + gagang bergerigi.
        const int r = (y1 - y0) / 2;
        const int ringCx = x0 + r + 1;
        const HGDIOBJ pen = SelectObject(dc, CreatePen(PS_SOLID, thickness, color));
        Arc(dc, ringCx - r, y0, ringCx + r, y0 + 2 * r, ringCx - r, cy, ringCx - r, cy);
        MoveToEx(dc, ringCx + r - 1, cy, nullptr);
        LineTo(dc, x1, cy);
        MoveToEx(dc, x1 - thickness - 1, cy, nullptr);
        LineTo(dc, x1 - thickness - 1, y1);
        MoveToEx(dc, cx + r, cy, nullptr);
        LineTo(dc, cx + r, y1 - 2);
        if (pen) DeleteObject(SelectObject(dc, pen));
        break;
    }
    case Page::Control: {
        // Slider: tiga rel mendatar dengan knobs di posisi selang-seling.
        const int knob = std::max(3, xydesk::panel::scaled(5, s));
        const HGDIOBJ pen = SelectObject(dc, CreatePen(PS_SOLID, thickness, color));
        const HGDIOBJ brush = SelectObject(dc, CreateSolidBrush(color));
        const int rows[3] = {y0 + 1, cy, y1 - 1};
        const int knobs[3] = {x0 + (x1 - x0) / 3, x0 + 2 * (x1 - x0) / 3, x0 + (x1 - x0) / 2};
        for (int i = 0; i < 3; ++i) {
            MoveToEx(dc, x0, rows[i], nullptr);
            LineTo(dc, x1 + 1, rows[i]);
            Ellipse(dc, knobs[i] - knob / 2, rows[i] - knob / 2, knobs[i] + knob / 2 + 1, rows[i] + knob / 2 + 1);
        }
        if (pen) DeleteObject(SelectObject(dc, pen));
        if (brush) DeleteObject(SelectObject(dc, brush));
        break;
    }
    }
}

float sidebarItemY(const PanelLayout& layout, Page page) {
    switch (page) {
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
void goPage(HWND hwnd, Page target) {
    if (target == g.page) return;
    g.pageFrom = g.page;
    g.page = target;
    g.pageT = 0.0f;
    if (g.pillY < 0.0f) g.pillY = sidebarItemY(g.layout, target);
    startAnim(hwnd);
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
    const Rect column{layout.sideStatus.x, layout.titleBar.bottom(),
        layout.sideStatus.w + xydesk::panel::scaled(6, layout.scalePct),
        layout.panel.bottom() - layout.titleBar.bottom()};
    fillRectOpaque(surface, column, mixColor(kBackground, kSurface2, 0.55f));
    HGDIOBJ oldPen = SelectObject(surface.dc, CreatePen(PS_SOLID, 1, kEdge));
    MoveToEx(surface.dc, column.right(), column.y, nullptr);
    LineTo(surface.dc, column.right(), column.bottom());
    DeleteObject(SelectObject(surface.dc, oldPen));

    const struct {
        Page page;
        Target target;
        const Rect& item;
        const Rect& icon;
        const Rect& label;
        const wchar_t* text;
    } items[] = {
        {Page::Status, Target::PageStatus, layout.sideStatus, layout.sideStatusIcon, layout.sideStatusLabel, L"Status"},
        {Page::Pairing, Target::PagePairing, layout.sidePairing, layout.sidePairingIcon, layout.sidePairingLabel, L"Pairing"},
        {Page::Control, Target::PageControl, layout.sideControl, layout.sideControlIcon, layout.sideControlLabel, L"Kontrol"},
    };

    // Pill aktif meluncur (morphing) antar item; tingginya sama dengan item.
    if (g.pillY < 0.0f) g.pillY = sidebarItemY(layout, g.page);
    const Rect pill{layout.sideStatus.x, static_cast<int>(g.pillY + 0.5f),
        layout.sideStatus.w, layout.sideStatus.h};
    fillRoundedOpaque(surface, pill, layout.radiusControl, mixColor(kSurface2, kAccent, 0.30f));
    strokeRounded(surface, pill, layout.radiusControl, mixColor(kEdge, kAccent, 0.55f), 1);

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
        drawTextCentered(dc, entry.text, entry.label, g.fontSmall, active ? kText : kMuted);
    }
}

// Kartu kesehatan capture: jujur soal backend dan layar hitam/sesi berbeda.
void paintCaptureCard(Surface& surface, const PanelLayout& layout, HDC dc) {
    fillRoundedOpaque(surface, layout.captureCard, layout.radiusCard, kSurface2);
    strokeRounded(surface, layout.captureCard, layout.radiusCard, kEdge, 1);
    drawTextLine(dc, L"CAPTURE", layout.captureTitle, g.fontSmall, kMuted,
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
}

// Menggambar panel ke permukaan. Tidak menyentuh jendela sama sekali, jadi
// jalur yang sama dipakai `--panel-snapshot` untuk memeriksa hasil gambar
// tanpa membuka jendela (dipakai CI).
void paintPage(Surface& surface, const PanelLayout& layout, HDC dc, Page page) {
    switch (page) {
    case Page::Status:
        paintStatusCard(surface, layout, dc);
        paintCaptureCard(surface, layout, dc);
        break;
    case Page::Pairing:
        paintIdentityCard(surface, layout, dc, layout.idCard, layout.idLabel, layout.idValue, layout.idCopy,
            L"Device ID", g.deviceId, Target::CopyId);
        paintIdentityCard(surface, layout, dc, layout.passwordCard, layout.passwordLabel, layout.passwordValue,
            layout.passwordCopy, L"Kode pairing", g.pairingCode, Target::CopyPassword);
        break;
    case Page::Control:
        paintButton(surface, layout, dc, Target::Start, layout.start);
        paintButton(surface, layout, dc, Target::Stop, layout.stop);
        paintButton(surface, layout, dc, Target::Restart, layout.restart);
        paintButton(surface, layout, dc, Target::Web, layout.web);
        paintButton(surface, layout, dc, Target::OpenLog, layout.openLog);
        break;
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

    // Gradien vertikal halus: pendar lembut di sepertiga atas, biar permukaan
    // tidak datar seperti kotak — tetap tenang, bukan neon.
    {
        const Rect& p = g.layout.panel;
        const int glowH = xydesk::panel::scaled(150, g.layout.scalePct);
        const int bottom = std::min(p.bottom(), p.y + glowH);
        for (int y = p.y; y < bottom; ++y) {
            const float t = 1.0f - static_cast<float>(y - p.y) / static_cast<float>(glowH);
            // Bell 4t(1-t) dikuadratkan: pendar mulai dari NOL di tepi atas
            // (garis tepi panel tetap putih murni), memuncak di tengah, lalu
            // habis sebelum kartu pertama.
            const float bell = 4.0f * t * (1.0f - t);
            const float amount = 0.05f * bell * bell;
            std::uint32_t* row = surface.pixels + static_cast<size_t>(y) * surface.width;
            for (int x = p.x; x < p.right(); ++x) {
                const std::uint32_t px = row[x];
                // Campur ke ungu aksen (124,58,237), bukan naik ke putih.
                const auto tint = [&amount](std::uint32_t c, std::uint32_t target) {
                    const int v = static_cast<int>(c) +
                        static_cast<int>(amount * (static_cast<float>(static_cast<int>(target) - static_cast<int>(c))));
                    return static_cast<std::uint32_t>(v < 0 ? 0 : (v > 255 ? 255 : v));
                };
                row[x] = 0xFF000000u | (tint((px >> 16) & 0xFF, 124) << 16) |
                    (tint((px >> 8) & 0xFF, 58) << 8) | tint(px & 0xFF, 237);
            }
        }
    }
    strokeRounded(surface, g.layout.panel.inset(1), g.layout.radiusPanel - 1, kEdge, 1);

    HDC dc = surface.dc;
    if (!dc) return false;

    HFONT previousFont = static_cast<HFONT>(SelectObject(dc, g.fontBody));

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

    drawTextLine(dc, L"Tutup = sembunyi ke tray, host tetap jalan. Dobel-klik judul = perbesar. "
                     L"Tab pindah tombol · Enter menjalankan · Esc menyembunyikan.",
        g.layout.hint, g.fontSmall, kMuted, DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);

    paintSidebar(surface, g.layout, dc);
    paintLogo(surface, g.layout, dc);
    drawTextLine(dc, L"XyDesk Control Panel", g.layout.title, g.fontTitle, kText,
        DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);
    drawTextLine(dc, L"Panel host Windows · tanpa terminal", g.layout.subtitle, g.fontSmall, kMuted,
        DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);
    paintCaptionButtons(surface, g.layout, dc);

    SelectObject(dc, previousFont);
    GdiFlush();
    applyWindowShape(surface, g.layout);
    return true;
}

void renderPanel() {
    if (!g.window) return;
    if (!drawPanelToSurface()) return;
    Surface& surface = g.surface;

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
    if (g.fontTitle) DeleteObject(g.fontTitle);
    if (g.fontBody) DeleteObject(g.fontBody);
    if (g.fontSmall) DeleteObject(g.fontSmall);
    if (g.fontMono) DeleteObject(g.fontMono);
    if (g.fontLogo) DeleteObject(g.fontLogo);
    g.fontTitle = make(-xydesk::panel::scaled(20, s), FW_SEMIBOLD, L"Segoe UI");
    g.fontBody = make(-xydesk::panel::scaled(15, s), FW_NORMAL, L"Segoe UI");
    g.fontSmall = make(-xydesk::panel::scaled(13, s), FW_NORMAL, L"Segoe UI");
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
    if (!SystemParametersInfoW(SPI_GETWORKAREA, 0, &workArea, 0)) {
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
    const UINT effective = static_cast<UINT>(static_cast<unsigned long long>(dpi) * g.zoomPct / 100);
    g.layout = xydesk::panel::computeLayout(static_cast<int>(effective), g.unitsW, g.unitsH);
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
    if (!g.maximized) {
        if (GetWindowRect(hwnd, &g.normalRect)) g.haveNormalRect = true;
        g.zoomPct = workAreaZoom();
        g.maximized = true;
        applyDpi(hwnd, windowDpi(hwnd), true); // sekalian menengahkan
        return;
    }
    g.maximized = false;
    g.zoomPct = 100;
    applyDpi(hwnd, windowDpi(hwnd), false);
    if (g.haveNormalRect) {
        SetWindowPos(hwnd, nullptr, g.normalRect.left, g.normalRect.top,
            g.layout.window.w, g.layout.window.h, SWP_NOZORDER | SWP_NOACTIVATE);
    } else {
        centerWindow(hwnd);
    }
    renderPanel();
}

// ── Mesin host: start/stop/restart --------------------------------------

void closeHostHandles() {
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
    if ((g.deviceId.empty() || g.pairingCode.empty()) && !readIdentity()) {
        g.lastError = L"Identitas host tidak dapat dibaca.";
        setStatus(g.lastError, kBad);
        return false;
    }
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
    std::vector<wchar_t> commandLine(command.begin(), command.end());
    commandLine.push_back(L'\0');
    STARTUPINFOW si{};
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESTDHANDLES;
    si.hStdOutput = g.logFile;
    si.hStdError = g.logFile;
    si.hStdInput = GetStdHandle(STD_INPUT_HANDLE);
    PROCESS_INFORMATION pi{};
    const BOOL started = CreateProcessW(nullptr, commandLine.data(), nullptr, nullptr, TRUE,
        CREATE_NO_WINDOW | CREATE_UNICODE_ENVIRONMENT, nullptr, moduleDirectory().c_str(), &si, &pi);
    if (!started) {
        const DWORD error = GetLastError();
        closeHostHandles();
        g.lastError = L"Host tidak dapat dimulai (Windows error " + std::to_wstring(error) + L").";
        setStatus(g.lastError, kBad);
        return false;
    }
    CloseHandle(pi.hThread);
    g.process = pi.hProcess;
    if (!AssignProcessToJobObject(g.job, g.process)) {
        TerminateProcess(g.process, 1);
        closeHostHandles();
        g.lastError = L"Windows gagal mengikat host ke pengawas proses.";
        setStatus(g.lastError, kBad);
        return false;
    }
    g.running = true;
    setStatus(L"Host aktif sebagai user Windows ini", kGood);
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

void activateTarget(HWND hwnd, Target target) {
    switch (target) {
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
    case Target::Web:
        ShellExecuteW(hwnd, L"open", kWebUrl, nullptr, nullptr, SW_SHOWNORMAL);
        break;
    case Target::OpenLog: {
        const std::wstring log = g.logPath.empty() ? hostLogPath() : g.logPath;
        ShellExecuteW(hwnd, L"open", log.c_str(), nullptr, nullptr, SW_SHOWNORMAL);
        break;
    }
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
    std::vector<Target> order = {Target::PageStatus, Target::PagePairing, Target::PageControl};
    switch (g.page) {
    case Page::Pairing:
        order.push_back(Target::CopyId);
        order.push_back(Target::CopyPassword);
        break;
    case Page::Control:
        order.push_back(Target::Start);
        order.push_back(Target::Stop);
        order.push_back(Target::Restart);
        order.push_back(Target::Web);
        order.push_back(Target::OpenLog);
        break;
    case Page::Status:
        break;
    }
    order.push_back(Target::Minimize);
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
        const int grip = xydesk::panel::scaled(6, g.layout.scalePct);
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
    case Target::Minimize:
    case Target::Maximize:
    case Target::Close:
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
        return HTTRANSPARENT;
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
        readIdentity();
        addTrayIcon(hwnd);
        setStatus(L"Menyalakan host…", kMuted);
        SetTimer(hwnd, kTimer, 1000, nullptr);
        // Panel adalah satu pintu: dibuka berarti host hidup tanpa tombol
        // kedua. Post agar jendela selesai dibuat dulu.
        PostMessageW(hwnd, kAutoStartMessage, 0, 0);
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
        const Target target = xydesk::panel::targetAt(g.layout, g.page, xFromLParam(lParam), yFromLParam(lParam));
        if (target != Target::None && target != Target::TitleBar && targetEnabled(target)) {
            g.pressed = target;
            SetCapture(hwnd);
            renderPanel();
        }
        return 0;
    }

    case WM_LBUTTONUP: {
        const Target target = xydesk::panel::targetAt(g.layout, g.page, xFromLParam(lParam), yFromLParam(lParam));
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
        const Target target = xydesk::panel::targetAt(g.layout, g.page, xFromLParam(lParam), yFromLParam(lParam));
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
        g.layout = xydesk::panel::computeLayout(static_cast<int>(dpi), g.unitsW, g.unitsH);
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
            readCaptureStatus();
            if (!g.flashText.empty() && GetTickCount64() >= g.flashUntil) {
                g.flashText.clear();
                g.statusColor = g.running ? kGood : kMuted;
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
        if (g.fontTitle) DeleteObject(g.fontTitle);
        if (g.fontBody) DeleteObject(g.fontBody);
        if (g.fontSmall) DeleteObject(g.fontSmall);
        if (g.fontMono) DeleteObject(g.fontMono);
        if (g.fontLogo) DeleteObject(g.fontLogo);
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

// `--panel-snapshot <berkas.bmp>`: menggambar panel ke berkas (32-bit, alpha
// tidak dipremultiply) tanpa membuka jendela. Dipakai CI Windows untuk
// memeriksa bentuk panel dari piksel: sudut harus transparan, tepi harus
// separuh tembus (bukti penghalusan), dan bayangan harus memudar keluar.
int runPanelSnapshot(const std::wstring& path) {
    g.layout = xydesk::panel::computeLayout(96);
    createFonts();
    g.statusText = L"Host aktif sebagai user Windows ini";
    g.statusColor = kGood;
    g.deviceId = L"8412-7735-2094";
    g.pairingCode = L"4821";
    g.logPath = L"C:\\Users\\operator\\AppData\\Local\\XyDesk\\host.log";
    g.running = true;
    g.hot = xydesk::panel::Target::None;
    g.page = xydesk::panel::Page::Status;
    g.captureBackend = L"gdi-bitblt · sesi aktif";
    g.captureSeen = true;
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
    if (hasArgument(L"--panel-probe")) {
        return runPanelProbe(commandLineArgument(L"--panel-probe"));
    }
    if (hasArgument(L"--panel-snapshot")) {
        return runPanelSnapshot(commandLineArgument(L"--panel-snapshot"));
    }

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

    HWND window = CreateWindowExW(WS_EX_LAYERED | WS_EX_APPWINDOW, kClassName, kWindowTitle,
        WS_POPUP | WS_SYSMENU, x, y, g.layout.window.w, g.layout.window.h,
        nullptr, nullptr, instance, nullptr);
    if (!window) return 1;

    applyDpi(window, windowDpi(window), false);
    ShowWindow(window, show);
    UpdateWindow(window);
    SetForegroundWindow(window);

    MSG message{};
    while (GetMessageW(&message, nullptr, 0, 0) > 0) {
        TranslateMessage(&message);
        DispatchMessageW(&message);
    }
    return static_cast<int>(message.wParam);
}
