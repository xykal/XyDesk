// XyDesk Virtual Display Adapter & Monitor Controller (Win32 SetupAPI + IddCx)
// Hak Cipta (c) 2026 XyVerse Technology Global. MIT License.
//
// Mengelola siklus hidup penuh XyDesk Virtual Display Adapter (setara StarDesk
// Virtual Display):
//   1. Instalasi sertifikat ke LocalMachine\Root & TrustedPublisher
//   2. Generasi EDID 1.4 + CEA-861 HDR kustom ("XyDesk VDD", manufaktur XYD)
//   3. Penulisan matriks resolusi & refresh rate lengkap (30Hz..240Hz,
//      720p/1080p/1440p/4K/Ultrawide/Tablet/Mobile) ke C:\VirtualDisplayDriver
//      dan C:\IddSampleDriver beserta ACL untuk LOCAL SERVICE (WUDFHost.exe)
//   4. Pembuatan Root-Enumerated PnP Device Node via SetupAPI
//      (SetupDiCreateDeviceInfoW + DIF_REGISTERDEVICE) dan pengikatan driver
//      UMDF2 IddCx via UpdateDriverForPlugAndPlayDevicesW (newdev.dll)
//   5. Penamaan perangkat "XyDesk Virtual Display Adapter" di Device Manager
//      dan class registry Display ({4d36e968-e325-11ce-bfc1-08002be10318})
//   6. Kontrol runtime (status JSON, set-mode resolusi/Hz, reload pipe,
//      ensure desktop attachment, dan uninstall bersih).

#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif
#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0A00
#endif
#ifndef NOMINMAX
#define NOMINMAX
#endif

#include <windows.h>
#include <setupapi.h>
#include <newdev.h>
#include <cfgmgr32.h>
#include <wincrypt.h>
#include <shellapi.h>
#include <initguid.h>
#include <devguid.h>

#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

#if defined(_MSC_VER)
#pragma comment(lib, "setupapi.lib")
#pragma comment(lib, "newdev.lib")
#pragma comment(lib, "cfgmgr32.lib")
#pragma comment(lib, "crypt32.lib")
#pragma comment(lib, "advapi32.lib")
#pragma comment(lib, "user32.lib")
#pragma comment(lib, "gdi32.lib")
#pragma comment(lib, "shell32.lib")
#pragma comment(lib, "ole32.lib")
#endif

namespace {

constexpr wchar_t kAdapterFriendlyName[] = L"XyDesk Virtual Display Adapter";
constexpr wchar_t kMonitorFriendlyName[] = L"XyDesk Virtual Display";
constexpr wchar_t kManufacturerName[] = L"XyVerse Technology Global";
constexpr wchar_t kVddDir[] = L"C:\\VirtualDisplayDriver";
constexpr wchar_t kIddDir[] = L"C:\\IddSampleDriver";
constexpr wchar_t kMttPipeName[] = L"\\\\.\\pipe\\MTTVirtualDisplayPipe";
constexpr wchar_t kDisplayClassKey[] =
    L"SYSTEM\\CurrentControlSet\\Control\\Class\\{4d36e968-e325-11ce-bfc1-08002be10318}";

struct ResPreset {
    int width;
    int height;
};

// Matriks resolusi komprehensif XyDesk Virtual Display (16:9, 16:10, 21:9, 32:9,
// 4:3, Tablet/iPad/Surface, Mobile 20:9, hingga 4K/5K/8K).
constexpr ResPreset kPresets[] = {
    // 16:9 Standar & Gaming
    {1280, 720},
    {1366, 768},
    {1600, 900},
    {1920, 1080},
    {2560, 1440},
    {3200, 1800},
    {3840, 2160},
    {5120, 2880},
    {7680, 4320},
    // 16:10 Produktivitas / Laptop / Tablet
    {1280, 800},
    {1440, 900},
    {1680, 1050},
    {1920, 1200},
    {2560, 1600},
    {2880, 1800},
    {3840, 2400},
    // 21:9 & 32:9 Ultrawide
    {2560, 1080},
    {3440, 1440},
    {3840, 1600},
    {5120, 1440},
    // 4:3 / 3:2 / Tablet / Mobile
    {800, 600},
    {1024, 768},
    {1280, 960},
    {1280, 1024},
    {1400, 1050},
    {2048, 1536},
    {2160, 1440},
    {2256, 1504},
    {2400, 1080},
    {2732, 2048},
};

constexpr int kRefreshRates[] = {30, 60, 75, 90, 120, 144, 165, 240};

std::string narrowUtf8(const std::wstring& w) {
    if (w.empty()) return {};
    int needed = WideCharToMultiByte(CP_UTF8, 0, w.data(), static_cast<int>(w.size()), nullptr, 0, nullptr, nullptr);
    if (needed <= 0) return {};
    std::string out(static_cast<std::size_t>(needed), '\0');
    WideCharToMultiByte(CP_UTF8, 0, w.data(), static_cast<int>(w.size()), out.data(), needed, nullptr, nullptr);
    return out;
}

std::wstring toLower(std::wstring s) {
    std::transform(s.begin(), s.end(), s.begin(), [](wchar_t c) {
        return (c >= L'A' && c <= L'Z') ? static_cast<wchar_t>(c - L'A' + L'a') : c;
    });
    return s;
}

bool fileExists(const std::wstring& path) {
    DWORD attr = GetFileAttributesW(path.c_str());
    return attr != INVALID_FILE_ATTRIBUTES && (attr & FILE_ATTRIBUTE_DIRECTORY) == 0;
}

std::wstring exeDir() {
    wchar_t buf[MAX_PATH * 2] = {};
    DWORD len = GetModuleFileNameW(nullptr, buf, MAX_PATH * 2 - 1);
    std::wstring full(buf, len);
    auto pos = full.find_last_of(L"\\/");
    return pos == std::wstring::npos ? L"." : full.substr(0, pos);
}

bool isProcessElevated() {
    HANDLE token = nullptr;
    if (!OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &token)) return false;
    TOKEN_ELEVATION elev = {};
    DWORD sz = 0;
    BOOL ok = GetTokenInformation(token, TokenElevation, &elev, sizeof(elev), &sz);
    CloseHandle(token);
    return ok && elev.TokenIsElevated != 0;
}

int runCommandSilent(const std::wstring& cmdLine) {
    STARTUPINFOW si = {};
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;
    PROCESS_INFORMATION pi = {};
    std::wstring mutableCmd = cmdLine;
    if (!CreateProcessW(nullptr, mutableCmd.data(), nullptr, nullptr, FALSE,
                        CREATE_NO_WINDOW, nullptr, nullptr, &si, &pi)) {
        return -1;
    }
    WaitForSingleObject(pi.hProcess, 60000);
    DWORD exitCode = 1;
    GetExitCodeProcess(pi.hProcess, &exitCode);
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
    return static_cast<int>(exitCode);
}

// ── 1. Generasi Custom EDID 256-byte ("XyDesk VDD", manufaktur XYD) ──
bool writeCustomXyDeskEdid(const std::wstring& path) {
    std::uint8_t edid[256] = {
        0x00, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x00,
        0x63, 0x24, // Manufacturer ID: "XYD" ((24<<10)|(25<<5)|4 = 0x6324)
        0x58, 0x59, // Product Code: "XY" (0x5958 LE)
        0x01, 0x09, 0x06, 0x00, // Serial Number
        0x26, 0x24, // Week 38, Year 2026 (2026 - 1990 = 36 = 0x24)
        0x01, 0x04, // EDID 1.4
        0xa5, 0x3c, 0x22, 0x78, 0x3a,
        0xee, 0x95, 0xa3, 0x54, 0x4c, 0x99, 0x26, 0x0f, 0x50, 0x54,
        0x21, 0x08, 0x00,
        0xd1, 0xc0, 0xa9, 0xc0, 0x81, 0xc0, 0x01, 0x01,
        0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
        // Detailed Timing Descriptor 1: 1920x1080 @ 60Hz (148.5 MHz)
        0x02, 0x3a, 0x80, 0x18, 0x71, 0x38, 0x2d, 0x40, 0x58, 0x2c,
        0x45, 0x00, 0x58, 0x54, 0x21, 0x00, 0x00, 0x1e,
        // Monitor Range Limits Descriptor (0xFD): 24..240 Hz, max pixel clock 1200 MHz
        0x00, 0x00, 0x00, 0xfd, 0x00, 0x18, 0xf0, 0x0f, 0xff, 0x78,
        0x00, 0x0a, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20,
        // Monitor Serial Descriptor (0xFF): "XYDESK-VDD\n  "
        0x00, 0x00, 0x00, 0xff, 0x00,
        'X', 'Y', 'D', 'E', 'S', 'K', '-', 'V', 'D', 'D', 0x0a, 0x20, 0x20,
        // Monitor Name Descriptor (0xFC): "XyDesk VDD\n  "
        0x00, 0x00, 0x00, 0xfc, 0x00,
        'X', 'y', 'D', 'e', 's', 'k', ' ', 'V', 'D', 'D', 0x0a, 0x20, 0x20,
        // Extension flag = 1 (CEA-861 block), Checksum calculated below
        0x01, 0x00,
        // Block 1: CEA-861 Extension Block (HDR10 + YCbCr 4:4:4/4:2:0)
        0x02, 0x03, 0x20, 0x40, 0xe6, 0x06, 0x0d, 0x01, 0xa2, 0xa2,
        0x10, 0xe3, 0x05, 0xd8, 0x00, 0x67, 0xd8, 0x5d, 0xc4, 0x01,
        0x6e, 0x80, 0x00, 0x68, 0x03, 0x0c, 0x00, 0x10, 0x00, 0x30,
        0x00, 0x0b
    };

    unsigned int sum0 = 0;
    for (int i = 0; i < 127; ++i) sum0 += edid[i];
    edid[127] = static_cast<std::uint8_t>((256 - (sum0 % 256)) % 256);

    unsigned int sum1 = 0;
    for (int i = 128; i < 255; ++i) sum1 += edid[i];
    edid[255] = static_cast<std::uint8_t>((256 - (sum1 % 256)) % 256);

    std::ofstream ofs(path, std::ios::binary | std::ios::trunc);
    if (!ofs) return false;
    ofs.write(reinterpret_cast<const char*>(edid), sizeof(edid));
    return ofs.good();
}

// ── 2. Tulis vdd_settings.xml, option.txt, dan Registry Konfigurasi ──
bool writeDriverConfigs(int preferredW, int preferredH, int preferredHz, int monitorCount) {
    monitorCount = std::clamp(monitorCount, 1, 4);
    preferredW = std::clamp(preferredW, 640, 7680);
    preferredH = std::clamp(preferredH, 480, 4320);
    preferredHz = std::clamp(preferredHz, 24, 240);

    CreateDirectoryW(kVddDir, nullptr);
    CreateDirectoryW(L"C:\\VirtualDisplayDriver\\Logs", nullptr);
    CreateDirectoryW(kIddDir, nullptr);

    // Berikan izin baca/tulis pada LOCAL SERVICE (WUDFHost.exe) dan Users.
    runCommandSilent(L"icacls.exe \"C:\\VirtualDisplayDriver\" /grant \"*S-1-5-19:(OI)(CI)F\" \"*S-1-5-32-545:(OI)(CI)M\" /T /C /Q");
    runCommandSilent(L"icacls.exe \"C:\\IddSampleDriver\" /grant \"*S-1-5-19:(OI)(CI)F\" \"*S-1-5-32-545:(OI)(CI)M\" /T /C /Q");

    writeCustomXyDeskEdid(std::wstring(kVddDir) + L"\\user_edid.bin");

    // Susun daftar resolusi dengan mode utama pilihan di urutan pertama.
    std::vector<ResPreset> ordered;
    ordered.push_back({preferredW, preferredH});
    for (const auto& p : kPresets) {
        if (p.width == preferredW && p.height == preferredH) continue;
        ordered.push_back(p);
    }

    // A) C:\VirtualDisplayDriver\vdd_settings.xml
    {
        std::ostringstream xml;
        xml << "<?xml version='1.0' encoding='utf-8'?>\r\n"
            << "<vdd_settings>\r\n"
            << "    <monitors>\r\n"
            << "        <count>" << monitorCount << "</count>\r\n"
            << "    </monitors>\r\n"
            << "    <gpu>\r\n"
            << "        <friendlyname>default</friendlyname>\r\n"
            << "    </gpu>\r\n"
            << "    <resolutions>\r\n";
        for (const auto& r : ordered) {
            xml << "        <resolution>\r\n"
                << "            <width>" << r.width << "</width>\r\n"
                << "            <height>" << r.height << "</height>\r\n";
            bool hasPrefHz = false;
            for (int hz : kRefreshRates) {
                if (hz == preferredHz) hasPrefHz = true;
                xml << "            <refresh_rate>" << hz << "</refresh_rate>\r\n";
            }
            if (!hasPrefHz) {
                xml << "            <refresh_rate>" << preferredHz << "</refresh_rate>\r\n";
            }
            xml << "        </resolution>\r\n";
        }
        xml << "    </resolutions>\r\n"
            << "    <options>\r\n"
            << "        <CustomEdid>true</CustomEdid>\r\n"
            << "        <PreventSpoof>true</PreventSpoof>\r\n"
            << "        <EdidCeaOverride>true</EdidCeaOverride>\r\n"
            << "        <HardwareCursor>true</HardwareCursor>\r\n"
            << "        <SDR10bit>false</SDR10bit>\r\n"
            << "        <HDRPlus>false</HDRPlus>\r\n"
            << "        <logging>false</logging>\r\n"
            << "        <debuglogging>false</debuglogging>\r\n"
            << "    </options>\r\n"
            << "</vdd_settings>\r\n";

        std::ofstream ofs(std::wstring(kVddDir) + L"\\vdd_settings.xml", std::ios::binary | std::ios::trunc);
        if (ofs) {
            const auto content = xml.str();
            ofs.write(content.data(), static_cast<std::streamsize>(content.size()));
        }
    }

    // B) C:\VirtualDisplayDriver\option.txt & C:\IddSampleDriver\option.txt
    // Format wajib IddSampleDriver/MttVDD:
    //   Baris 1: bilangan bulat jumlah monitor (tanpa komentar di baris 1!)
    //   Baris berikutnya: width, height, hz
    {
        std::ostringstream opt;
        opt << monitorCount << "\r\n"
            << "# XyDesk Virtual Display Adapter — Matriks Resolusi & Refresh Rate\r\n"
            << "# Format: width, height, refresh_rate_hz\r\n"
            << preferredW << ", " << preferredH << ", " << preferredHz << "\r\n";
        for (const auto& r : ordered) {
            for (int hz : kRefreshRates) {
                if (r.width == preferredW && r.height == preferredH && hz == preferredHz) continue;
                opt << r.width << ", " << r.height << ", " << hz << "\r\n";
            }
        }
        const auto content = opt.str();
        for (const wchar_t* dir : {kVddDir, kIddDir}) {
            std::ofstream ofs(std::wstring(dir) + L"\\option.txt", std::ios::binary | std::ios::trunc);
            if (ofs) {
                ofs.write(content.data(), static_cast<std::streamsize>(content.size()));
            }
        }
    }

    // C) Registry HKLM\SOFTWARE\MikeTheTech\VirtualDisplayDriver & XyDesk
    HKEY hKey = nullptr;
    if (RegCreateKeyExW(HKEY_LOCAL_MACHINE, L"SOFTWARE\\MikeTheTech\\VirtualDisplayDriver",
                        0, nullptr, 0, KEY_SET_VALUE | KEY_WOW64_64KEY, nullptr, &hKey, nullptr) == ERROR_SUCCESS) {
        RegSetValueExW(hKey, L"VDDPATH", 0, REG_SZ,
                       reinterpret_cast<const BYTE*>(kVddDir),
                       static_cast<DWORD>((wcslen(kVddDir) + 1) * sizeof(wchar_t)));
        DWORD one = 1;
        RegSetValueExW(hKey, L"CustomEdidEnabled", 0, REG_DWORD, reinterpret_cast<const BYTE*>(&one), sizeof(one));
        RegSetValueExW(hKey, L"PreventMonitorSpoof", 0, REG_DWORD, reinterpret_cast<const BYTE*>(&one), sizeof(one));
        RegSetValueExW(hKey, L"EdidCeaOverride", 0, REG_DWORD, reinterpret_cast<const BYTE*>(&one), sizeof(one));
        RegSetValueExW(hKey, L"HardwareCursorEnabled", 0, REG_DWORD, reinterpret_cast<const BYTE*>(&one), sizeof(one));
        RegCloseKey(hKey);
    }

    if (RegCreateKeyExW(HKEY_LOCAL_MACHINE, L"SOFTWARE\\XyVerse Technology Global\\XyDesk\\VirtualDisplay",
                        0, nullptr, 0, KEY_SET_VALUE | KEY_WOW64_64KEY, nullptr, &hKey, nullptr) == ERROR_SUCCESS) {
        DWORD one = 1;
        RegSetValueExW(hKey, L"Installed", 0, REG_DWORD, reinterpret_cast<const BYTE*>(&one), sizeof(one));
        RegSetValueExW(hKey, L"AdapterName", 0, REG_SZ,
                       reinterpret_cast<const BYTE*>(kAdapterFriendlyName),
                       static_cast<DWORD>((wcslen(kAdapterFriendlyName) + 1) * sizeof(wchar_t)));
        RegSetValueExW(hKey, L"MonitorName", 0, REG_SZ,
                       reinterpret_cast<const BYTE*>(kMonitorFriendlyName),
                       static_cast<DWORD>((wcslen(kMonitorFriendlyName) + 1) * sizeof(wchar_t)));
        RegSetValueExW(hKey, L"ConfigPath", 0, REG_SZ,
                       reinterpret_cast<const BYTE*>(kVddDir),
                       static_cast<DWORD>((wcslen(kVddDir) + 1) * sizeof(wchar_t)));
        RegCloseKey(hKey);
    }

    return true;
}

// ── 3. Instalasi Sertifikat ke LocalMachine\Root & TrustedPublisher ──
bool installCertificateToStore(const std::wstring& cerPath, const wchar_t* storeName) {
    HCERTSTORE hStore = CertOpenStore(
        CERT_STORE_PROV_SYSTEM_W, 0, NULL,
        CERT_SYSTEM_STORE_LOCAL_MACHINE | CERT_STORE_OPEN_EXISTING_FLAG,
        storeName);
    if (!hStore) return false;

    HCERTSTORE hFileStore = nullptr;
    PCCERT_CONTEXT pCert = nullptr;
    DWORD encoding = 0, contentType = 0, formatType = 0;
    BOOL ok = CryptQueryObject(
        CERT_QUERY_OBJECT_FILE, cerPath.c_str(),
        CERT_QUERY_CONTENT_FLAG_CERT | CERT_QUERY_CONTENT_FLAG_PKCS7_SIGNED,
        CERT_QUERY_FORMAT_FLAG_ALL, 0,
        &encoding, &contentType, &formatType, &hFileStore, nullptr,
        reinterpret_cast<const void**>(&pCert));

    bool added = false;
    if (ok && pCert) {
        added = CertAddCertificateContextToStore(hStore, pCert, CERT_STORE_ADD_REPLACE_EXISTING, nullptr) != FALSE;
        CertFreeCertificateContext(pCert);
    } else {
        std::ifstream ifs(cerPath, std::ios::binary | std::ios::ate);
        if (ifs) {
            auto sz = ifs.tellg();
            ifs.seekg(0, std::ios::beg);
            std::vector<BYTE> buf(static_cast<std::size_t>(sz));
            if (ifs.read(reinterpret_cast<char*>(buf.data()), sz)) {
                added = CertAddEncodedCertificateToStore(
                            hStore, X509_ASN_ENCODING | PKCS_7_ASN_ENCODING,
                            buf.data(), static_cast<DWORD>(buf.size()),
                            CERT_STORE_ADD_REPLACE_EXISTING, nullptr) != FALSE;
            }
        }
    }
    if (hFileStore) CertCloseStore(hFileStore, 0);
    CertCloseStore(hStore, 0);

    // Fallback certutil untuk memastikan kompatibilitas penuh Group Policy.
    if (!added) {
        std::wstring cmd = L"certutil.exe -addstore -f \"" + std::wstring(storeName) + L"\" \"" + cerPath + L"\"";
        added = (runCommandSilent(cmd) == 0);
    }
    return added;
}

void installAllCertificates(const std::wstring& drvDir) {
    const wchar_t* certs[] = {
        L"Virtual_Display_Driver.cer",
        L"iddsampledriver.cer",
        L"IddSampleDriver.cer",
    };
    for (const wchar_t* name : certs) {
        std::wstring p = drvDir + L"\\" + name;
        if (fileExists(p)) {
            installCertificateToStore(p, L"Root");
            installCertificateToStore(p, L"TrustedPublisher");
        }
    }
}

// ── 4. Pemeriksaan & Branding PnP Device Node (SetupAPI) ──
bool matchVirtualHwid(const std::wstring& hwidLower) {
    return hwidLower.find(L"root\\mttvdd") != std::wstring::npos ||
           hwidLower.find(L"root\\iddsampledriver") != std::wstring::npos ||
           hwidLower.find(L"root\\xydeskvdd") != std::wstring::npos ||
           hwidLower == L"mttvdd";
}

void brandVirtualDisplayAdapterNodes() {
    HDEVINFO hDevInfo = SetupDiGetClassDevsW(&GUID_DEVCLASS_DISPLAY, nullptr, nullptr, DIGCF_PRESENT);
    if (hDevInfo != INVALID_HANDLE_VALUE) {
        SP_DEVINFO_DATA devData = {};
        devData.cbSize = sizeof(devData);
        for (DWORD i = 0; SetupDiEnumDeviceInfo(hDevInfo, i, &devData); ++i) {
            wchar_t hwidBuf[1024] = {};
            if (SetupDiGetDeviceRegistryPropertyW(
                    hDevInfo, &devData, SPDRP_HARDWAREID, nullptr,
                    reinterpret_cast<PBYTE>(hwidBuf), sizeof(hwidBuf) - sizeof(wchar_t), nullptr)) {
                std::wstring hwid = toLower(hwidBuf);
                if (matchVirtualHwid(hwid)) {
                    SetupDiSetDeviceRegistryPropertyW(
                        hDevInfo, &devData, SPDRP_FRIENDLYNAME,
                        reinterpret_cast<const BYTE*>(kAdapterFriendlyName),
                        static_cast<DWORD>((wcslen(kAdapterFriendlyName) + 1) * sizeof(wchar_t)));
                    SetupDiSetDeviceRegistryPropertyW(
                        hDevInfo, &devData, SPDRP_DEVICEDESC,
                        reinterpret_cast<const BYTE*>(kAdapterFriendlyName),
                        static_cast<DWORD>((wcslen(kAdapterFriendlyName) + 1) * sizeof(wchar_t)));
                    SetupDiSetDeviceRegistryPropertyW(
                        hDevInfo, &devData, SPDRP_MFG,
                        reinterpret_cast<const BYTE*>(kManufacturerName),
                        static_cast<DWORD>((wcslen(kManufacturerName) + 1) * sizeof(wchar_t)));
                }
            }
        }
        SetupDiDestroyDeviceInfoList(hDevInfo);
    }

    // Update DriverDesc pada kunci kelas Display agar EnumDisplayDevicesW
    // dan Device Manager menampilkan "XyDesk Virtual Display Adapter".
    for (int idx = 0; idx < 32; ++idx) {
        wchar_t subKey[256] = {};
        swprintf_s(subKey, L"%s\\%04d", kDisplayClassKey, idx);
        HKEY hKey = nullptr;
        if (RegOpenKeyExW(HKEY_LOCAL_MACHINE, subKey, 0,
                          KEY_READ | KEY_SET_VALUE | KEY_WOW64_64KEY, &hKey) == ERROR_SUCCESS) {
            wchar_t matchId[256] = {};
            DWORD sz = sizeof(matchId) - sizeof(wchar_t);
            if (RegQueryValueExW(hKey, L"MatchingDeviceId", nullptr, nullptr,
                                 reinterpret_cast<LPBYTE>(matchId), &sz) == ERROR_SUCCESS) {
                std::wstring m = toLower(matchId);
                if (matchVirtualHwid(m)) {
                    RegSetValueExW(hKey, L"DriverDesc", 0, REG_SZ,
                                   reinterpret_cast<const BYTE*>(kAdapterFriendlyName),
                                   static_cast<DWORD>((wcslen(kAdapterFriendlyName) + 1) * sizeof(wchar_t)));
                    RegSetValueExW(hKey, L"ProviderName", 0, REG_SZ,
                                   reinterpret_cast<const BYTE*>(kManufacturerName),
                                   static_cast<DWORD>((wcslen(kManufacturerName) + 1) * sizeof(wchar_t)));
                }
            }
            RegCloseKey(hKey);
        }
    }
}

int countVirtualPnPNodes(const wchar_t* targetHwidFilter = nullptr) {
    HDEVINFO hDevInfo = SetupDiGetClassDevsW(&GUID_DEVCLASS_DISPLAY, nullptr, nullptr, DIGCF_PRESENT);
    if (hDevInfo == INVALID_HANDLE_VALUE) return 0;

    int count = 0;
    SP_DEVINFO_DATA devData = {};
    devData.cbSize = sizeof(devData);
    std::wstring filterLower = targetHwidFilter ? toLower(targetHwidFilter) : L"";

    for (DWORD i = 0; SetupDiEnumDeviceInfo(hDevInfo, i, &devData); ++i) {
        wchar_t hwidBuf[1024] = {};
        if (SetupDiGetDeviceRegistryPropertyW(
                hDevInfo, &devData, SPDRP_HARDWAREID, nullptr,
                reinterpret_cast<PBYTE>(hwidBuf), sizeof(hwidBuf) - sizeof(wchar_t), nullptr)) {
            std::wstring hwid = toLower(hwidBuf);
            if (filterLower.empty() ? matchVirtualHwid(hwid) : (hwid.find(filterLower) != std::wstring::npos)) {
                ++count;
            }
        }
    }
    SetupDiDestroyDeviceInfoList(hDevInfo);
    return count;
}

bool createRootPnPNode(const wchar_t* hardwareId) {
    HDEVINFO hDevInfo = SetupDiCreateDeviceInfoList(&GUID_DEVCLASS_DISPLAY, nullptr);
    if (hDevInfo == INVALID_HANDLE_VALUE) return false;

    SP_DEVINFO_DATA devInfoData = {};
    devInfoData.cbSize = sizeof(SP_DEVINFO_DATA);
    if (!SetupDiCreateDeviceInfoW(
            hDevInfo, L"XyDeskVDD", &GUID_DEVCLASS_DISPLAY,
            kAdapterFriendlyName, nullptr, DICD_GENERATE_ID, &devInfoData)) {
        SetupDiDestroyDeviceInfoList(hDevInfo);
        return false;
    }

    // MULTI_SZ membutuhkan double null-terminator.
    std::vector<wchar_t> multiSz;
    for (const wchar_t* p = hardwareId; *p; ++p) multiSz.push_back(*p);
    multiSz.push_back(L'\0');
    multiSz.push_back(L'\0');

    if (!SetupDiSetDeviceRegistryPropertyW(
            hDevInfo, &devInfoData, SPDRP_HARDWAREID,
            reinterpret_cast<const BYTE*>(multiSz.data()),
            static_cast<DWORD>(multiSz.size() * sizeof(wchar_t)))) {
        SetupDiDestroyDeviceInfoList(hDevInfo);
        return false;
    }

    if (!SetupDiCallClassInstaller(DIF_REGISTERDEVICE, hDevInfo, &devInfoData)) {
        SetupDiDestroyDeviceInfoList(hDevInfo);
        return false;
    }

    SetupDiDestroyDeviceInfoList(hDevInfo);
    return true;
}

void removeAllVirtualPnPNodes() {
    HDEVINFO hDevInfo = SetupDiGetClassDevsW(&GUID_DEVCLASS_DISPLAY, nullptr, nullptr, 0);
    if (hDevInfo == INVALID_HANDLE_VALUE) return;

    SP_DEVINFO_DATA devData = {};
    devData.cbSize = sizeof(devData);
    for (DWORD i = 0; SetupDiEnumDeviceInfo(hDevInfo, i, &devData); ++i) {
        wchar_t hwidBuf[1024] = {};
        if (SetupDiGetDeviceRegistryPropertyW(
                hDevInfo, &devData, SPDRP_HARDWAREID, nullptr,
                reinterpret_cast<PBYTE>(hwidBuf), sizeof(hwidBuf) - sizeof(wchar_t), nullptr)) {
            std::wstring hwid = toLower(hwidBuf);
            if (matchVirtualHwid(hwid)) {
                SetupDiCallClassInstaller(DIF_REMOVE, hDevInfo, &devData);
            }
        }
    }
    SetupDiDestroyDeviceInfoList(hDevInfo);
}

bool installInfAndBindDevice(const std::wstring& infPath, const wchar_t* hardwareId) {
    wchar_t fullInf[MAX_PATH * 2] = {};
    if (!GetFullPathNameW(infPath.c_str(), MAX_PATH * 2 - 1, fullInf, nullptr)) {
        return false;
    }

    // 1. Stage INF ke DriverStore terlebih dahulu.
    std::wstring pnpCmd = L"pnputil.exe /add-driver \"" + std::wstring(fullInf) + L"\" /install";
    runCommandSilent(pnpCmd);

    // 2. Pastikan Root-Enumerated PnP Device Node ada di Device Manager.
    if (countVirtualPnPNodes(hardwareId) == 0) {
        if (!createRootPnPNode(hardwareId)) {
            return false;
        }
    }

    // 3. Ikat driver UMDF2 IddCx ke Device Node via UpdateDriverForPlugAndPlayDevicesW.
    BOOL rebootRequired = FALSE;
    BOOL updated = UpdateDriverForPlugAndPlayDevicesW(
        nullptr, hardwareId, fullInf, INSTALLFLAG_FORCE, &rebootRequired);

    if (!updated) {
        // Fallback: pnputil /add-driver /install setelah node Root\... terdaftar.
        int rc = runCommandSilent(pnpCmd);
        updated = (rc == 0 || rc == 3010);
    }

    if (updated) {
        brandVirtualDisplayAdapterNodes();
    }
    return updated != FALSE;
}

bool notifyMttPipe(const wchar_t* command) {
    HANDLE hPipe = CreateFileW(
        kMttPipeName, GENERIC_READ | GENERIC_WRITE, 0, nullptr, OPEN_EXISTING, 0, nullptr);
    if (hPipe == INVALID_HANDLE_VALUE) return false;
    DWORD written = 0;
    DWORD bytes = static_cast<DWORD>((wcslen(command) + 1) * sizeof(wchar_t));
    BOOL ok = WriteFile(hPipe, command, bytes, &written, nullptr);
    CloseHandle(hPipe);
    return ok != FALSE;
}

// ── 5. Temukan Output GDI \\.\DISPLAYx Milik XyDesk Virtual Display Adapter ──
struct VirtualGdiOutput {
    std::wstring gdiDevice;
    std::wstring adapterDesc;
    std::wstring hardwareId;
    bool attached = false;
    int width = 0;
    int height = 0;
    int hz = 0;
};

std::vector<VirtualGdiOutput> queryVirtualOutputs() {
    std::vector<VirtualGdiOutput> out;
    for (DWORD i = 0; i < 64; ++i) {
        DISPLAY_DEVICEW dd = {};
        dd.cb = sizeof(dd);
        if (!EnumDisplayDevicesW(nullptr, i, &dd, 0)) break;

        std::wstring desc = dd.DeviceString;
        std::wstring hwid = dd.DeviceID;
        std::wstring descLow = toLower(desc);
        std::wstring hwidLow = toLower(hwid);

        bool isVirt = matchVirtualHwid(hwidLow) ||
                      descLow == L"xydesk virtual display adapter" ||
                      descLow == L"xydesk virtual display" ||
                      descLow == L"virtual display driver" ||
                      descLow == L"iddsampledriver" ||
                      descLow == L"iddsampledriver device" ||
                      descLow == L"mtt virtual display" ||
                      descLow == L"mikethetech virtual display";

        if (!isVirt) continue;

        VirtualGdiOutput item;
        item.gdiDevice = dd.DeviceName;
        item.adapterDesc = desc;
        item.hardwareId = hwid;
        item.attached = (dd.StateFlags & DISPLAY_DEVICE_ATTACHED_TO_DESKTOP) != 0;

        DEVMODEW dm = {};
        dm.dmSize = sizeof(dm);
        if (EnumDisplaySettingsW(dd.DeviceName, ENUM_CURRENT_SETTINGS, &dm) ||
            EnumDisplaySettingsW(dd.DeviceName, ENUM_REGISTRY_SETTINGS, &dm)) {
            item.width = static_cast<int>(dm.dmPelsWidth);
            item.height = static_cast<int>(dm.dmPelsHeight);
            item.hz = static_cast<int>(dm.dmDisplayFrequency);
        }
        out.push_back(item);
    }
    return out;
}

bool applyVirtualDisplayMode(int width, int height, int hz) {
    auto outputs = queryVirtualOutputs();
    if (outputs.empty()) return false;

    bool anyApplied = false;
    for (const auto& v : outputs) {
        DEVMODEW current = {};
        current.dmSize = sizeof(current);
        bool hasCurrent = EnumDisplaySettingsW(v.gdiDevice.c_str(), ENUM_CURRENT_SETTINGS, &current) != FALSE;

        DEVMODEW best = {};
        best.dmSize = sizeof(best);
        bool foundMode = false;

        for (DWORD idx = 0; idx < 2048; ++idx) {
            DEVMODEW mode = {};
            mode.dmSize = sizeof(mode);
            if (!EnumDisplaySettingsW(v.gdiDevice.c_str(), idx, &mode)) break;
            if (static_cast<int>(mode.dmPelsWidth) == width &&
                static_cast<int>(mode.dmPelsHeight) == height) {
                if (!foundMode || static_cast<int>(mode.dmDisplayFrequency) == hz) {
                    best = mode;
                    foundMode = true;
                    if (static_cast<int>(mode.dmDisplayFrequency) == hz) break;
                }
            }
        }

        if (!foundMode) {
            best = hasCurrent ? current : best;
            best.dmPelsWidth = static_cast<DWORD>(width);
            best.dmPelsHeight = static_cast<DWORD>(height);
            best.dmDisplayFrequency = static_cast<DWORD>(hz);
            best.dmBitsPerPel = 32;
            best.dmFields = DM_PELSWIDTH | DM_PELSHEIGHT | DM_BITSPERPEL | DM_DISPLAYFREQUENCY;
        }

        LONG res = ChangeDisplaySettingsExW(
            v.gdiDevice.c_str(), &best, nullptr, CDS_UPDATEREGISTRY | CDS_GLOBAL, nullptr);
        if (res != DISP_CHANGE_SUCCESSFUL) {
            res = ChangeDisplaySettingsExW(v.gdiDevice.c_str(), &best, nullptr, 0, nullptr);
        }
        if (res == DISP_CHANGE_SUCCESSFUL) {
            anyApplied = true;
        }
    }
    return anyApplied;
}

std::wstring locateDriverDir(const std::wstring& explicitDir) {
    if (!explicitDir.empty() &&
        (fileExists(explicitDir + L"\\MttVDD.inf") || fileExists(explicitDir + L"\\iddsampledriver.inf") ||
         fileExists(explicitDir + L"\\IddSampleDriver.inf"))) {
        return explicitDir;
    }
    std::wstring base = exeDir();
    const std::wstring candidates[] = {
        base,
        base + L"\\drivers\\IddSampleDriver",
        base + L"\\..\\drivers\\IddSampleDriver",
        L"C:\\Program Files\\XyDesk\\drivers\\IddSampleDriver",
    };
    for (const auto& c : candidates) {
        if (fileExists(c + L"\\MttVDD.inf") || fileExists(c + L"\\iddsampledriver.inf") ||
            fileExists(c + L"\\IddSampleDriver.inf")) {
            return c;
        }
    }
    return explicitDir.empty() ? base : explicitDir;
}

int cmdInstall(const std::wstring& drvDirArg, int width, int height, int hz, int count) {
    if (!isProcessElevated()) {
        std::fprintf(stderr, "[XyDesk VDD] Butuh hak Administrator untuk memasang XyDesk Virtual Display Adapter.\n");
        return 4;
    }

    std::wstring drvDir = locateDriverDir(drvDirArg);
    writeDriverConfigs(width, height, hz, count);
    installAllCertificates(drvDir);

    std::wstring mttInf = drvDir + L"\\MttVDD.inf";
    std::wstring iddInf = drvDir + L"\\iddsampledriver.inf";
    if (!fileExists(iddInf)) iddInf = drvDir + L"\\IddSampleDriver.inf";

    bool ok = false;
    if (fileExists(mttInf)) {
        std::printf("[XyDesk VDD] Memasang XyDesk Virtual Display Adapter (MttVDD HDR IddCx)...\n");
        ok = installInfAndBindDevice(mttInf, L"Root\\MttVDD");
    }
    if (!ok && fileExists(iddInf)) {
        std::printf("[XyDesk VDD] Memasang XyDesk Virtual Display Adapter (IddSampleDriver IddCx)...\n");
        ok = installInfAndBindDevice(iddInf, L"Root\\IddSampleDriver");
    }

    if (!ok) {
        std::fprintf(stderr, "[XyDesk VDD] GAGAL memasang driver dari direktori: %s\n",
                     narrowUtf8(drvDir).c_str());
        return 1;
    }

    brandVirtualDisplayAdapterNodes();
    notifyMttPipe(L"RELOAD_DRIVER");
    Sleep(400);
    applyVirtualDisplayMode(width, height, hz);

    std::printf("[XyDesk VDD] XyDesk Virtual Display Adapter berhasil dipasang dan diaktifkan (%dx%d @ %dHz).\n",
                width, height, hz);
    return 0;
}

int cmdSetMode(int width, int height, int hz, int count) {
    writeDriverConfigs(width, height, hz, count);
    notifyMttPipe(L"RELOAD_DRIVER");
    Sleep(250);
    bool applied = applyVirtualDisplayMode(width, height, hz);
    std::printf("{\"ok\":true,\"applied\":%s,\"width\":%d,\"height\":%d,\"hz\":%d,\"count\":%d}\n",
                applied ? "true" : "false", width, height, hz, count);
    return 0;
}

int cmdEnsure(const std::wstring& drvDirArg) {
    brandVirtualDisplayAdapterNodes();
    auto outputs = queryVirtualOutputs();
    for (const auto& o : outputs) {
        if (o.attached && o.width > 0 && o.height > 0) {
            std::printf("{\"ok\":true,\"active\":true,\"device\":\"%s\",\"width\":%d,\"height\":%d,\"hz\":%d}\n",
                        narrowUtf8(o.gdiDevice).c_str(), o.width, o.height, o.hz);
            return 0;
        }
    }
    if (countVirtualPnPNodes() == 0 && isProcessElevated()) {
        int rc = cmdInstall(drvDirArg, 1920, 1080, 60, 1);
        if (rc != 0) return rc;
    } else {
        notifyMttPipe(L"RELOAD_DRIVER");
        Sleep(300);
        applyVirtualDisplayMode(1920, 1080, 60);
    }
    outputs = queryVirtualOutputs();
    for (const auto& o : outputs) {
        if (o.attached) {
            std::printf("{\"ok\":true,\"active\":true,\"device\":\"%s\",\"width\":%d,\"height\":%d,\"hz\":%d}\n",
                        narrowUtf8(o.gdiDevice).c_str(), o.width, o.height, o.hz);
            return 0;
        }
    }
    std::printf("{\"ok\":false,\"active\":false}\n");
    return 1;
}

int cmdUninstall() {
    if (!isProcessElevated()) {
        std::fprintf(stderr, "[XyDesk VDD] Butuh hak Administrator untuk menghapus driver.\n");
        return 4;
    }
    removeAllVirtualPnPNodes();
    RegDeleteKeyExW(HKEY_LOCAL_MACHINE, L"SOFTWARE\\XyVerse Technology Global\\XyDesk\\VirtualDisplay",
                    KEY_WOW64_64KEY, 0);
    std::printf("[XyDesk VDD] Perangkat XyDesk Virtual Display Adapter berhasil dihapus.\n");
    return 0;
}

int cmdStatus() {
    int pnpNodes = countVirtualPnPNodes();
    auto outputs = queryVirtualOutputs();
    int attachedCount = 0;
    std::string firstDev;
    std::string firstDesc = narrowUtf8(kAdapterFriendlyName);
    int w = 0, h = 0, hz = 0;

    for (const auto& o : outputs) {
        if (o.attached) {
            ++attachedCount;
            if (firstDev.empty()) {
                firstDev = narrowUtf8(o.gdiDevice);
                firstDesc = narrowUtf8(o.adapterDesc);
                w = o.width;
                h = o.height;
                hz = o.hz;
            }
        }
    }

    std::printf(
        "{\"installed\":%s,\"pnpNodes\":%d,\"outputs\":%zu,\"attachedOutputs\":%d,"
        "\"adapterName\":\"%s\",\"gdiDevice\":\"%s\",\"width\":%d,\"height\":%d,\"hz\":%d}\n",
        (pnpNodes > 0 || !outputs.empty()) ? "true" : "false",
        pnpNodes, outputs.size(), attachedCount,
        firstDesc.c_str(), firstDev.c_str(), w, h, hz);
    return 0;
}

} // namespace

int wmain(int argc, wchar_t* argv[]) {
    std::wstring cmd = (argc >= 2) ? toLower(argv[1]) : L"status";
    std::wstring drvDir;
    int width = 1920;
    int height = 1080;
    int hz = 60;
    int count = 1;

    for (int i = 2; i < argc; ++i) {
        std::wstring arg = toLower(argv[i]);
        if ((arg == L"--dir" || arg == L"-d") && i + 1 < argc) {
            drvDir = argv[++i];
        } else if (arg == L"--width" && i + 1 < argc) {
            width = _wtoi(argv[++i]);
        } else if (arg == L"--height" && i + 1 < argc) {
            height = _wtoi(argv[++i]);
        } else if (arg == L"--hz" && i + 1 < argc) {
            hz = _wtoi(argv[++i]);
        } else if (arg == L"--count" && i + 1 < argc) {
            count = _wtoi(argv[++i]);
        }
    }

    if (cmd == L"install") {
        return cmdInstall(drvDir, width, height, hz, count);
    }
    if (cmd == L"set-mode") {
        if (argc >= 5 && argv[2][0] != L'-') {
            width = _wtoi(argv[2]);
            height = _wtoi(argv[3]);
            hz = _wtoi(argv[4]);
            if (argc >= 6 && argv[5][0] != L'-') count = _wtoi(argv[5]);
        }
        return cmdSetMode(width, height, hz, count);
    }
    if (cmd == L"ensure") {
        return cmdEnsure(drvDir);
    }
    if (cmd == L"uninstall" || cmd == L"remove") {
        return cmdUninstall();
    }
    if (cmd == L"brand") {
        brandVirtualDisplayAdapterNodes();
        return 0;
    }
    return cmdStatus();
}
