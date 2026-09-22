#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif
#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0A00
#endif

#include <windows.h>
#include <shellapi.h>
#include "resource.h"
#include <string>
#include <vector>
#include <algorithm>
#include <cstring>

#pragma comment(lib, "user32.lib")
#pragma comment(lib, "gdi32.lib")
#pragma comment(lib, "shell32.lib")

namespace {

constexpr wchar_t kClassName[] = L"XyDeskNativeControlPanel";
constexpr wchar_t kWindowTitle[] = L"XyDesk Control Panel";
constexpr int kStart = 1001;
constexpr int kStop = 1002;
constexpr int kCopyId = 1003;
constexpr int kCopyPassword = 1004;
constexpr int kOpenWeb = 1005;
constexpr int kOpenLog = 1006;
constexpr int kRestart = 1007;
constexpr int kTrayOpen = 1010;
constexpr int kTrayStart = 1011;
constexpr int kTrayStop = 1012;
constexpr int kTrayWeb = 1013;
constexpr int kTrayQuit = 1014;
constexpr int kTrayRestart = 1015;
constexpr UINT kTrayMessage = WM_APP + 11;
constexpr UINT kAutoStartMessage = WM_APP + 12;
constexpr UINT kTrayId = 1;
constexpr int kTimer = 7;

constexpr COLORREF kBackground = RGB(14, 16, 22);
constexpr COLORREF kSurface = RGB(24, 27, 36);
constexpr COLORREF kSurface2 = RGB(31, 35, 46);
constexpr COLORREF kText = RGB(244, 246, 250);
constexpr COLORREF kMuted = RGB(157, 166, 181);
constexpr COLORREF kAccent = RGB(125, 105, 238);
constexpr COLORREF kGood = RGB(91, 202, 132);
constexpr COLORREF kWarn = RGB(245, 183, 77);
constexpr COLORREF kBad = RGB(238, 104, 115);

struct AppState {
    HWND window = nullptr;
    HWND status = nullptr;
    HWND identity = nullptr;
    HWND password = nullptr;
    HWND start = nullptr;
    HWND stop = nullptr;
    HWND restart = nullptr;
    HWND copyId = nullptr;
    HWND copyPassword = nullptr;
    HWND web = nullptr;
    HWND log = nullptr;
    HFONT titleFont = nullptr;
    HFONT bodyFont = nullptr;
    HFONT smallFont = nullptr;
    HBRUSH backgroundBrush = nullptr;
    HBRUSH surfaceBrush = nullptr;
    HANDLE process = nullptr;
    HANDLE job = nullptr;
    HANDLE logFile = nullptr;
    std::wstring logPath;
    std::wstring deviceId;
    std::wstring pairingCode;
    std::wstring lastError;
    bool running = false;
};

AppState g;
UINT g_taskbarCreated = 0;

void removeTrayIcon();
void showTrayMenu(HWND hwnd);

std::wstring moduleDirectory() {
    wchar_t path[MAX_PATH]{};
    DWORD n = GetModuleFileNameW(nullptr, path, MAX_PATH);
    if (!n || n >= MAX_PATH) return L".";
    std::wstring result(path, n);
    const auto slash = result.find_last_of(L"\\/");
    return slash == std::wstring::npos ? L"." : result.substr(0, slash);
}

std::wstring enginePath() {
    return moduleDirectory() + L"\\xydesk-host.exe";
}

std::wstring hostLogPath() {
    wchar_t localAppData[MAX_PATH]{};
    DWORD n = GetEnvironmentVariableW(L"LOCALAPPDATA", localAppData, ARRAYSIZE(localAppData));
    std::wstring base = n && n < ARRAYSIZE(localAppData)
        ? std::wstring(localAppData, n)
        : moduleDirectory();
    const auto directory = base + L"\\XyDesk";
    CreateDirectoryW(directory.c_str(), nullptr);
    return directory + L"\\host.log";
}

void hideConsoleProcess(PROCESS_INFORMATION& pi) {
    if (pi.hThread) CloseHandle(pi.hThread);
    if (pi.hProcess) CloseHandle(pi.hProcess);
}

std::wstring quote(const std::wstring& value) {
    std::wstring out = L"\"";
    for (wchar_t c : value) {
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
        unsigned char c = static_cast<unsigned char>(json[i]);
        result.push_back(static_cast<wchar_t>(c));
    }
    return result;
}

bool readIdentity() {
    SECURITY_ATTRIBUTES sa{sizeof(SECURITY_ATTRIBUTES), nullptr, TRUE};
    HANDLE readPipe = nullptr;
    HANDLE writePipe = nullptr;
    if (!CreatePipe(&readPipe, &writePipe, &sa, 0)) return false;
    SetHandleInformation(readPipe, HANDLE_FLAG_INHERIT, 0);

    const auto exe = enginePath();
    std::wstring command = quote(exe) + L" --identity-json";
    std::vector<wchar_t> commandLine(command.begin(), command.end());
    commandLine.push_back(L'\0');
    STARTUPINFOW si{sizeof(STARTUPINFOW)};
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
    hideConsoleProcess(pi);
    if (exitCode != 0) return false;

    const std::wstring id = jsonString(output, "deviceId");
    const std::wstring password = jsonString(output, "password");
    if (id.empty() || password.empty()) return false;
    g.deviceId = id;
    g.pairingCode = password;
    return true;
}

void setStatus(const std::wstring& text, COLORREF color) {
    if (!g.status) return;
    SetWindowTextW(g.status, text.c_str());
    SendMessageW(g.status, WM_SETFONT, reinterpret_cast<WPARAM>(g.bodyFont), TRUE);
    // Store color via window property; WM_CTLCOLORSTATIC reads it.
    SetPropW(g.status, L"XyDeskStatusColor", reinterpret_cast<HANDLE>(static_cast<ULONG_PTR>(color)));
    InvalidateRect(g.status, nullptr, TRUE);
}

void updateIdentityControls() {
    SetWindowTextW(g.identity, g.deviceId.empty() ? L"Belum tersedia" : g.deviceId.c_str());
    SetWindowTextW(g.password, g.pairingCode.empty() ? L"Belum tersedia" : g.pairingCode.c_str());
    EnableWindow(g.copyId, !g.deviceId.empty());
    EnableWindow(g.copyPassword, !g.pairingCode.empty());
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

bool startHost();

void stopHost() {
    if (g.job) TerminateJobObject(g.job, 0);
    closeHostHandles();
    EnableWindow(g.start, TRUE);
    EnableWindow(g.stop, FALSE);
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
        updateIdentityControls();
        return false;
    }
    if (!createJob()) {
        g.lastError = L"Windows tidak mengizinkan pengawasan proses host.";
        setStatus(g.lastError, kBad);
        return false;
    }

    g.logPath = hostLogPath();
    SECURITY_ATTRIBUTES logSecurity{sizeof(SECURITY_ATTRIBUTES), nullptr, TRUE};
    g.logFile = CreateFileW(
        g.logPath.c_str(),
        FILE_APPEND_DATA,
        FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
        &logSecurity,
        OPEN_ALWAYS,
        FILE_ATTRIBUTE_NORMAL,
        nullptr);
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
    STARTUPINFOW si{sizeof(STARTUPINFOW)};
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
        g.lastError = L"Host tidak dapat dimulai (Windows error " + std::to_wstring(error) + L"). Log: " + g.logPath;
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
    EnableWindow(g.start, FALSE);
    EnableWindow(g.stop, TRUE);
    setStatus(L"Host aktif sebagai user Windows ini", kGood);
    updateIdentityControls();
    return true;
}

void copyText(HWND owner, HWND source) {
    int length = GetWindowTextLengthW(source);
    if (length <= 0) return;
    std::wstring text(static_cast<size_t>(length) + 1, L'\0');
    GetWindowTextW(source, text.data(), length + 1);
    text.resize(static_cast<size_t>(length));
    if (!OpenClipboard(owner)) return;
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
    // Gunakan resource XyDesk yang sama untuk tray dan window, bukan ikon
    // aplikasi generik Windows; shell akan memilih ukuran paling sesuai.
    data.hIcon = LoadIconW(GetModuleHandleW(nullptr), MAKEINTRESOURCEW(IDI_XYDESK));
    lstrcpynW(data.szTip, L"XyDesk Host — klik kanan untuk kontrol", ARRAYSIZE(data.szTip));
    if (!data.hIcon) {
        data.hIcon = LoadIconW(nullptr, IDI_APPLICATION);
    }
    // NIM_SETVERSION harus membawa versi yang diminta. Pada NOTIFYICON_VERSION_4
    // callback mouse tidak selalu sama dengan WM_* mentah; windowProc menangani
    // format lama dan format baru sekaligus.
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

HFONT makeFont(int height, int weight) {
    return CreateFontW(height, 0, 0, 0, weight, FALSE, FALSE, FALSE,
        DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
        CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_DONTCARE, L"Segoe UI");
}

HWND addControl(const wchar_t* klass, const wchar_t* text, DWORD style, int id,
    int x, int y, int width, int height, HWND parent) {
    return CreateWindowExW(0, klass, text, WS_CHILD | WS_VISIBLE | style,
        x, y, width, height, parent, reinterpret_cast<HMENU>(static_cast<INT_PTR>(id)),
        GetModuleHandleW(nullptr), nullptr);
}

void paintBackground(HWND hwnd, HDC dc) {
    RECT rect{};
    GetClientRect(hwnd, &rect);
    FillRect(dc, &rect, g.backgroundBrush);
}

LRESULT CALLBACK windowProc(HWND hwnd, UINT message, WPARAM wParam, LPARAM lParam) {
    // Explorer mengirim pesan terdaftar ini ketika taskbar/tray restart
    // (mis. Explorer crash atau user restart Explorer). Daftarkan ulang agar
    // ikon dan menu tidak hilang setelah sesi Windows tetap hidup.
    if (g_taskbarCreated && message == g_taskbarCreated) {
        addTrayIcon(hwnd);
        return 0;
    }
    switch (message) {
    case WM_CREATE:
        g.window = hwnd;
        g.backgroundBrush = CreateSolidBrush(kBackground);
        g.surfaceBrush = CreateSolidBrush(kSurface);
        g.titleFont = makeFont(-24, FW_SEMIBOLD);
        g.bodyFont = makeFont(-15, FW_NORMAL);
        g.smallFont = makeFont(-13, FW_NORMAL);
        g.status = addControl(L"STATIC", L"Menyiapkan host…", SS_LEFT, 2001, 34, 72, 510, 28, hwnd);
        g.identity = addControl(L"EDIT", L"Belum tersedia", ES_READONLY | ES_AUTOHSCROLL, 2002, 34, 168, 310, 30, hwnd);
        g.password = addControl(L"EDIT", L"Belum tersedia", ES_READONLY | ES_AUTOHSCROLL, 2003, 34, 248, 310, 30, hwnd);
        g.start = addControl(L"BUTTON", L"Mulai host", BS_OWNERDRAW, kStart, 34, 330, 150, 42, hwnd);
        g.stop = addControl(L"BUTTON", L"Hentikan", BS_OWNERDRAW, kStop, 195, 330, 150, 42, hwnd);
        g.restart = addControl(L"BUTTON", L"Restart", BS_OWNERDRAW, kRestart, 356, 330, 94, 42, hwnd);
        g.copyId = addControl(L"BUTTON", L"Salin ID", BS_OWNERDRAW, kCopyId, 358, 166, 92, 34, hwnd);
        g.copyPassword = addControl(L"BUTTON", L"Salin kode", BS_OWNERDRAW, kCopyPassword, 358, 246, 92, 34, hwnd);
        g.web = addControl(L"BUTTON", L"Buka XyDesk Web", BS_OWNERDRAW, kOpenWeb, 34, 394, 250, 40, hwnd);
        g.log = addControl(L"BUTTON", L"Buka log host", BS_OWNERDRAW, kOpenLog, 294, 394, 156, 40, hwnd);
        for (HWND control : {g.status, g.identity, g.password, g.start, g.stop, g.restart, g.copyId, g.copyPassword, g.web, g.log}) {
            SendMessageW(control, WM_SETFONT, reinterpret_cast<WPARAM>(g.bodyFont), TRUE);
        }
        SendMessageW(g.status, WM_SETFONT, reinterpret_cast<WPARAM>(g.bodyFont), TRUE);
        EnableWindow(g.stop, FALSE);
        addTrayIcon(hwnd);
        readIdentity();
        updateIdentityControls();
        setStatus(L"Menyalakan host…", kMuted);
        SetTimer(hwnd, kTimer, 1000, nullptr);
        // Panel adalah satu pintu: ketika dibuka, host langsung hidup tanpa
        // tombol kedua. Post agar window selesai dibuat dan tetap responsif.
        PostMessageW(hwnd, kAutoStartMessage, 0, 0);
        return 0;

    case kAutoStartMessage:
        if (!g.running) startHost();
        return 0;

    case kTrayMessage: {
        // NOTIFYICON_VERSION_4 biasanya menaruh event di lParam, tetapi
        // beberapa build Explorer/compatibility mode mengemasnya di LOWORD.
        // NIN_SELECT/NIN_KEYSELECT adalah bentuk callback keyboard versi 4.
        const UINT raw = static_cast<UINT>(lParam);
        const UINT event = LOWORD(raw);
        if (raw == WM_LBUTTONUP || raw == WM_LBUTTONDBLCLK ||
            event == WM_LBUTTONUP || event == WM_LBUTTONDBLCLK ||
            raw == NIN_SELECT) {
            openPanel(hwnd);
        } else if (raw == WM_RBUTTONUP || raw == WM_RBUTTONDOWN ||
                   raw == WM_CONTEXTMENU || event == WM_RBUTTONUP ||
                   event == WM_RBUTTONDOWN || event == WM_CONTEXTMENU ||
                   raw == NIN_KEYSELECT) {
            showTrayMenu(hwnd);
        }
        return 0;
    }

    case WM_TIMER:
        if (wParam == kTimer && g.process) {
            DWORD code = STILL_ACTIVE;
            if (GetExitCodeProcess(g.process, &code) && code != STILL_ACTIVE) {
                const auto log = g.logPath;
                closeHostHandles();
                EnableWindow(g.start, TRUE);
                EnableWindow(g.stop, FALSE);
                setStatus(
                    L"Host berhenti (kode " + std::to_wstring(code) + L") · log: " + log,
                    kWarn);
            }
        }
        return 0;

    case WM_COMMAND:
        switch (LOWORD(wParam)) {
        case kStart:
            startHost();
            return 0;
        case kStop:
            stopHost();
            return 0;
        case kRestart:
            restartHost();
            return 0;
        case kCopyId:
            copyText(hwnd, g.identity);
            setStatus(L"ID disalin ke clipboard", kGood);
            return 0;
        case kCopyPassword:
            copyText(hwnd, g.password);
            setStatus(L"Kode pairing disalin ke clipboard", kGood);
            return 0;
        case kOpenWeb:
            ShellExecuteW(hwnd, L"open", L"https://app.xydesk.my.id", nullptr, nullptr, SW_SHOWNORMAL);
            return 0;
        case kOpenLog: {
            const auto log = g.logPath.empty() ? hostLogPath() : g.logPath;
            ShellExecuteW(hwnd, L"open", log.c_str(), nullptr, nullptr, SW_SHOWNORMAL);
            return 0;
        }
        case kTrayOpen:
            openPanel(hwnd);
            return 0;
        case kTrayStart:
            startHost();
            return 0;
        case kTrayStop:
            stopHost();
            return 0;
        case kTrayRestart:
            restartHost();
            return 0;
        case kTrayWeb:
            ShellExecuteW(hwnd, L"open", L"https://app.xydesk.my.id", nullptr, nullptr, SW_SHOWNORMAL);
            return 0;
        case kTrayQuit:
            removeTrayIcon();
            DestroyWindow(hwnd);
            return 0;
        default:
            break;
        }
        break;

    case WM_CTLCOLORSTATIC: {
        HDC dc = reinterpret_cast<HDC>(wParam);
        HWND control = reinterpret_cast<HWND>(lParam);
        COLORREF color = kText;
        if (control == g.status) {
            color = static_cast<COLORREF>(reinterpret_cast<ULONG_PTR>(GetPropW(control, L"XyDeskStatusColor")));
            if (!color) color = kMuted;
        } else if (control == g.identity || control == g.password) {
            color = kText;
        } else {
            color = kMuted;
        }
        SetTextColor(dc, color);
        SetBkColor(dc, kBackground);
        return reinterpret_cast<LRESULT>(g.backgroundBrush);
    }

    case WM_CTLCOLOREDIT: {
        HDC dc = reinterpret_cast<HDC>(wParam);
        SetTextColor(dc, kText);
        SetBkColor(dc, kSurface2);
        return reinterpret_cast<LRESULT>(g.surfaceBrush);
    }

    case WM_DRAWITEM: {
        auto* item = reinterpret_cast<DRAWITEMSTRUCT*>(lParam);
        if (!item) break;
        RECT r = item->rcItem;
        HBRUSH brush = CreateSolidBrush(item->itemState & ODS_SELECTED ? RGB(99, 83, 194) : kSurface2);
        FillRect(item->hDC, &r, brush);
        DeleteObject(brush);
        wchar_t label[128]{};
        GetWindowTextW(item->hwndItem, label, 128);
        SetBkMode(item->hDC, TRANSPARENT);
        SetTextColor(item->hDC, kText);
        SelectObject(item->hDC, g.bodyFont);
        DrawTextW(item->hDC, label, -1, &r, DT_CENTER | DT_VCENTER | DT_SINGLELINE);
        return TRUE;
    }

    case WM_CLOSE:
        // Tombol X menyembunyikan panel, bukan mematikan host. Kontrol penuh
        // tetap tersedia dari ikon tray; menu Keluar menghentikan host.
        ShowWindow(hwnd, SW_HIDE);
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
        paintBackground(hwnd, dc);
        SetBkMode(dc, TRANSPARENT);
        SetTextColor(dc, kText);
        SelectObject(dc, g.titleFont);
        RECT title{34, 24, 540, 58};
        DrawTextW(dc, L"XyDesk Host", -1, &title, DT_LEFT | DT_SINGLELINE);
        SelectObject(dc, g.smallFont);
        SetTextColor(dc, kMuted);
        RECT subtitle{34, 52, 540, 70};
        DrawTextW(dc, L"Quiet surface · native Windows · tanpa terminal", -1, &subtitle, DT_LEFT | DT_SINGLELINE);
        RECT idLabel{34, 140, 220, 162};
        DrawTextW(dc, L"Device ID", -1, &idLabel, DT_LEFT | DT_SINGLELINE);
        RECT pwLabel{34, 220, 220, 242};
        DrawTextW(dc, L"Kode pairing", -1, &pwLabel, DT_LEFT | DT_SINGLELINE);
        RECT hint{34, 458, 540, 500};
        DrawTextW(dc, L"Host berjalan sebagai user Windows yang membuka panel ini.\nGPU, resolusi, dan status streaming terlihat di web.", -1, &hint, DT_LEFT | DT_WORDBREAK);
        EndPaint(hwnd, &ps);
        return 0;
    }

    case WM_DESTROY:
        KillTimer(hwnd, kTimer);
        removeTrayIcon();
        stopHost();
        if (g.titleFont) DeleteObject(g.titleFont);
        if (g.bodyFont) DeleteObject(g.bodyFont);
        if (g.smallFont) DeleteObject(g.smallFont);
        if (g.backgroundBrush) DeleteObject(g.backgroundBrush);
        if (g.surfaceBrush) DeleteObject(g.surfaceBrush);
        PostQuitMessage(0);
        return 0;
    default:
        break;
    }
    return DefWindowProcW(hwnd, message, wParam, lParam);
}

} // namespace

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE, PWSTR, int show) {
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

    HWND window = CreateWindowExW(0, kClassName, kWindowTitle,
        WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX,
        CW_USEDEFAULT, CW_USEDEFAULT, 600, 560, nullptr, nullptr, instance, nullptr);
    if (!window) return 1;
    ShowWindow(window, show);
    UpdateWindow(window);

    MSG message{};
    while (GetMessageW(&message, nullptr, 0, 0) > 0) {
        TranslateMessage(&message);
        DispatchMessageW(&message);
    }
    return static_cast<int>(message.wParam);
}
