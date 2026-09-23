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
constexpr UINT kFlashDurationMs = 2600;
#ifndef WM_DPICHANGED
constexpr UINT WM_DPICHANGED = 0x02E0;
#endif

// ── Palet Quiet Surface (panel host) ──
constexpr COLORREF kBackground = RGB(14, 16, 22);
constexpr COLORREF kSurface = RGB(24, 27, 36);
constexpr COLORREF kSurface2 = RGB(31, 35, 46);
constexpr COLORREF kSurface3 = RGB(41, 46, 60);
constexpr COLORREF kSurfacePressed = RGB(23, 26, 34);
constexpr COLORREF kEdge = RGB(48, 53, 68);
constexpr COLORREF kText = RGB(244, 246, 250);
constexpr COLORREF kMuted = RGB(157, 166, 181);
constexpr COLORREF kDisabled = RGB(96, 104, 120);
constexpr COLORREF kAccent = RGB(125, 105, 238);
constexpr COLORREF kAccentHover = RGB(143, 126, 248);
constexpr COLORREF kAccentPressed = RGB(104, 86, 205);
constexpr COLORREF kGood = RGB(91, 202, 132);
constexpr COLORREF kWarn = RGB(245, 183, 77);
constexpr COLORREF kBad = RGB(238, 104, 115);

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
    bool trackingMouse = false;
    bool layered = true;
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
        if (pressed) return ButtonPalette{kAccentPressed, kText, radius, false};
        if (hot) return ButtonPalette{kAccentHover, kText, radius, false};
        return ButtonPalette{kAccent, kText, radius, false};
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
    fillRoundedOpaque(surface, card, layout.radiusCard, kSurface);

    const COLORREF dotColor = g.statusColor;
    fillCircleOpaque(surface, Rect{layout.statusDot.x - 4, layout.statusDot.y - 4, layout.statusDot.w + 8, layout.statusDot.h + 8},
        mixColor(kSurface, dotColor, 0.28f));
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
    fillRoundedOpaque(surface, card, layout.radiusCard, kSurface);
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

void paintCloseButton(Surface& surface, const PanelLayout& layout, HDC dc) {
    const Rect& rect = layout.closeButton;
    const bool hot = g.hot == Target::Close;
    const bool pressed = g.pressed == Target::Close;
    if (hot || pressed) {
        fillRoundedOpaque(surface, rect, xydesk::panel::scaled(10, layout.scalePct),
            pressed ? kSurfacePressed : kSurface2);
    }
    const COLORREF color = hot ? kBad : kMuted;
    const int glyph = xydesk::panel::scaled(13, layout.scalePct);
    const int centerX = xydesk::panel::centerX(rect);
    const int centerY = xydesk::panel::centerY(rect);
    // Silang digambar dari dua garis tebal; panjangnya dipilih supaya bobotnya
    // seimbang dengan judul di sebelahnya.
    const int arm = glyph / 2;
    const int thickness = std::max(1, xydesk::panel::scaled(2, layout.scalePct));
    const HGDIOBJ previousPen = SelectObject(dc, CreatePen(PS_SOLID, thickness, color));
    const HGDIOBJ previousBrush = SelectObject(dc, GetStockObject(NULL_BRUSH));
    MoveToEx(dc, centerX - arm, centerY - arm, nullptr);
    LineTo(dc, centerX + arm + 1, centerY + arm + 1);
    MoveToEx(dc, centerX + arm, centerY - arm, nullptr);
    LineTo(dc, centerX - arm - 1, centerY + arm + 1);
    if (previousPen) DeleteObject(SelectObject(dc, previousPen));
    if (previousBrush) SelectObject(dc, previousBrush);
}

void paintLogo(Surface& surface, const PanelLayout& layout, HDC dc) {
    fillRoundedOpaque(surface, layout.logo, xydesk::panel::scaled(9, layout.scalePct), kAccent);
    drawTextCentered(dc, L"X", layout.logo, g.fontLogo, kText);
}

// Menggambar panel ke permukaan. Tidak menyentuh jendela sama sekali, jadi
// jalur yang sama dipakai `--panel-snapshot` untuk memeriksa hasil gambar
// tanpa membuka jendela (dipakai CI).
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
    paintLogo(surface, g.layout, dc);
    drawTextLine(dc, L"XyDesk Control Panel", g.layout.title, g.fontTitle, kText,
        DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);
    drawTextLine(dc, L"Panel host Windows · tanpa terminal", g.layout.subtitle, g.fontSmall, kMuted,
        DT_LEFT | DT_VCENTER | DT_SINGLELINE | DT_END_ELLIPSIS);
    paintCloseButton(surface, g.layout, dc);

    paintStatusCard(surface, g.layout, dc);

    paintIdentityCard(surface, g.layout, dc, g.layout.idCard, g.layout.idLabel, g.layout.idValue, g.layout.idCopy,
        L"Device ID", g.deviceId, Target::CopyId);
    paintIdentityCard(surface, g.layout, dc, g.layout.passwordCard, g.layout.passwordLabel, g.layout.passwordValue,
        g.layout.passwordCopy, L"Kode pairing", g.pairingCode, Target::CopyPassword);

    paintButton(surface, g.layout, dc, Target::Start, g.layout.start);
    paintButton(surface, g.layout, dc, Target::Stop, g.layout.stop);
    paintButton(surface, g.layout, dc, Target::Restart, g.layout.restart);
    paintButton(surface, g.layout, dc, Target::Web, g.layout.web);
    paintButton(surface, g.layout, dc, Target::OpenLog, g.layout.openLog);

    drawTextLine(dc, L"Menutup panel menyembunyikan ke tray — host tetap jalan.\n"
                     L"Tab pindah tombol · Enter menjalankan · Esc menyembunyikan.",
        g.layout.hint, g.fontSmall, kMuted, DT_LEFT | DT_TOP | DT_WORDBREAK | DT_NOPREFIX);

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
    g.layout = xydesk::panel::computeLayout(static_cast<int>(dpi));
    createFonts();
    if (remeasure) {
        centerWindow(hwnd);
    } else {
        SetWindowPos(hwnd, nullptr, 0, 0, g.layout.window.w, g.layout.window.h,
            SWP_NOZORDER | SWP_NOACTIVATE | SWP_NOMOVE);
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
    case Target::Close:
        hidePanel(hwnd);
        break;
    default:
        break;
    }
}

// Urutan Tab: kartu identitas dulu (aksi paling sering), lalu tombol host,
// lalu tautan, terakhir tombol tutup.
constexpr Target kFocusOrder[] = {
    Target::CopyId, Target::CopyPassword, Target::Start, Target::Stop,
    Target::Restart, Target::Web, Target::OpenLog, Target::Close,
};

void moveFocus(int step) {
    const int count = static_cast<int>(std::size(kFocusOrder));
    int index = -1;
    for (int i = 0; i < count; ++i) {
        if (kFocusOrder[i] == g.focused) {
            index = i;
            break;
        }
    }
    for (int i = 0; i < count; ++i) {
        index = (index + step + count) % count;
        if (targetEnabled(kFocusOrder[index])) {
            g.focused = kFocusOrder[index];
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
    const Target target = xydesk::panel::targetAt(g.layout, x, y);
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
    const Target target = xydesk::panel::targetAt(g.layout, client.x, client.y);
    switch (target) {
    case Target::Close:
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
        auto* info = reinterpret_cast<MINMAXINFO*>(lParam);
        info->ptMinTrackSize.x = g.layout.window.w;
        info->ptMinTrackSize.y = g.layout.window.h;
        info->ptMaxTrackSize.x = g.layout.window.w;
        info->ptMaxTrackSize.y = g.layout.window.h;
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
        const Target target = xydesk::panel::targetAt(g.layout, xFromLParam(lParam), yFromLParam(lParam));
        if (target != Target::None && target != Target::TitleBar && targetEnabled(target)) {
            g.pressed = target;
            SetCapture(hwnd);
            renderPanel();
        }
        return 0;
    }

    case WM_LBUTTONUP: {
        const Target target = xydesk::panel::targetAt(g.layout, xFromLParam(lParam), yFromLParam(lParam));
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
            hidePanel(hwnd);
            return 0;
        }
        if (command == SC_MAXIMIZE || command == SC_RESTORE) {
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
        if (wParam == kTimer) {
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
    const Target samples[] = {
        xydesk::panel::targetAt(layout, xydesk::panel::centerX(layout.closeButton), xydesk::panel::centerY(layout.closeButton)),
        xydesk::panel::targetAt(layout, xydesk::panel::centerX(layout.start), xydesk::panel::centerY(layout.start)),
        xydesk::panel::targetAt(layout, xydesk::panel::centerX(layout.openLog), xydesk::panel::centerY(layout.openLog)),
        xydesk::panel::targetAt(layout, 0, 0),
        xydesk::panel::targetAt(layout, layout.panel.x + 4, layout.panel.y + 4),
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
