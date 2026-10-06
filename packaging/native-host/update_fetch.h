#pragma once

// Pembaruan host Windows — bagian yang butuh Windows: ambil manifes, unduh
// installer, verifikasi SHA-256, lalu jalankan.
//
// Keputusannya tidak ada di sini. Semua penilaian "boleh atau tidak" ada di
// updater.h yang diuji di Linux; berkas ini hanya mengeksekusi keputusan itu
// dan menolak bergerak kalau salah satu syaratnya tidak terpenuhi.
//
// Tiga penjaga yang tidak boleh dilepas:
//   1. Alamat unduhan diperiksa ulang lewat allowedUrl() tepat sebelum
//      koneksi dibuka — bukan hanya saat manifes dibaca.
//   2. Berkas ditulis ke folder sementara milik pengguna dengan nama yang
//      dibentuk sendiri, lalu SHA-256-nya dibandingkan dengan manifes.
//   3. Installer hanya dijalankan kalau hash cocok persis. Kalau tidak,
//      berkasnya dihapus.

#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0A00
#endif
#include <windows.h>
#include <winhttp.h>
#include <bcrypt.h>
#include <shellapi.h>

#include <array>
#include <atomic>
#include <string>
#include <vector>

#include "updater.h"

#pragma comment(lib, "winhttp.lib")
#pragma comment(lib, "bcrypt.lib")
#pragma comment(lib, "shell32.lib")

namespace xydesk::updater {

struct Handle {
    HINTERNET value;
    explicit Handle(HINTERNET h) : value(h) {}
    ~Handle() { if (value) WinHttpCloseHandle(value); }
    Handle(const Handle&) = delete;
    Handle& operator=(const Handle&) = delete;
};

inline std::wstring widen(const std::string& value) {
    if (value.empty()) return {};
    const int n = MultiByteToWideChar(CP_UTF8, 0, value.data(), static_cast<int>(value.size()), nullptr, 0);
    if (n <= 0) return {};
    std::wstring out(static_cast<size_t>(n), 0);
    MultiByteToWideChar(CP_UTF8, 0, value.data(), static_cast<int>(value.size()), out.data(), n);
    return out;
}

/// Pecah URL https menjadi host dan path. Hanya untuk URL yang sudah lolos
/// allowedUrl(), jadi bentuknya dijamin sederhana.
inline bool splitUrl(const std::string& url, std::wstring& host, std::wstring& path) {
    const std::string scheme = "https://";
    if (url.compare(0, scheme.size(), scheme) != 0) return false;
    const auto slash = url.find('/', scheme.size());
    if (slash == std::string::npos) return false;
    host = widen(url.substr(scheme.size(), slash - scheme.size()));
    path = widen(url.substr(slash));
    return !host.empty() && !path.empty();
}

/**
 * GET sederhana yang mengikuti redirect (GitHub mengalihkan unduhan rilis ke
 * CDN-nya). Isi dibatasi `limit` byte; melewati batas = gagal, bukan
 * terpotong diam-diam.
 */
inline bool httpGet(const std::string& url, std::string& out, std::int64_t limit,
                    const std::atomic_bool* cancelled = nullptr) {
    out.clear();
    std::wstring host, path;
    if (!splitUrl(url, host, path)) return false;

    Handle session(WinHttpOpen(L"XyDesk host updater", WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY,
                               WINHTTP_NO_PROXY_NAME, WINHTTP_NO_PROXY_BYPASS, 0));
    if (!session.value) return false;
    WinHttpSetTimeouts(session.value, 5000, 8000, 15000, 30000);

    Handle connection(WinHttpConnect(session.value, host.c_str(), INTERNET_DEFAULT_HTTPS_PORT, 0));
    if (!connection.value) return false;

    Handle request(WinHttpOpenRequest(connection.value, L"GET", path.c_str(), nullptr,
                                      WINHTTP_NO_REFERER, WINHTTP_DEFAULT_ACCEPT_TYPES,
                                      WINHTTP_FLAG_SECURE));
    if (!request.value) return false;

    // Redirect diizinkan, tetapi hanya yang tetap HTTPS.
    DWORD policy = WINHTTP_OPTION_REDIRECT_POLICY_DISALLOW_HTTPS_TO_HTTP;
    WinHttpSetOption(request.value, WINHTTP_OPTION_REDIRECT_POLICY, &policy, sizeof(policy));

    if (!WinHttpSendRequest(request.value, WINHTTP_NO_ADDITIONAL_HEADERS, 0,
                            WINHTTP_NO_REQUEST_DATA, 0, 0, 0)) {
        return false;
    }
    if (!WinHttpReceiveResponse(request.value, nullptr)) return false;

    DWORD code = 0, size = sizeof(code);
    if (!WinHttpQueryHeaders(request.value, WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER,
                             WINHTTP_HEADER_NAME_BY_INDEX, &code, &size, WINHTTP_NO_HEADER_INDEX)) {
        return false;
    }
    if (code != 200) return false;

    std::vector<char> chunk(64 * 1024);
    for (;;) {
        if (cancelled && cancelled->load()) return false;
        DWORD read = 0;
        if (!WinHttpReadData(request.value, chunk.data(), static_cast<DWORD>(chunk.size()), &read)) {
            return false;
        }
        if (read == 0) break;
        if (static_cast<std::int64_t>(out.size()) + read > limit) return false;
        out.append(chunk.data(), read);
    }
    return true;
}

/// SHA-256 heksadesimal huruf kecil, bentuk yang sama dengan SHA256SUMS.txt.
inline std::string sha256Hex(const std::string& data) {
    BCRYPT_ALG_HANDLE alg = nullptr;
    if (BCryptOpenAlgorithmProvider(&alg, BCRYPT_SHA256_ALGORITHM, nullptr, 0) != 0) return {};
    std::array<unsigned char, 32> digest{};
    const auto status = BCryptHash(alg, nullptr, 0,
                                   reinterpret_cast<PUCHAR>(const_cast<char*>(data.data())),
                                   static_cast<ULONG>(data.size()), digest.data(),
                                   static_cast<ULONG>(digest.size()));
    BCryptCloseAlgorithmProvider(alg, 0);
    if (status != 0) return {};
    static const char* hex = "0123456789abcdef";
    std::string out;
    out.reserve(64);
    for (unsigned char byte : digest) {
        out.push_back(hex[byte >> 4]);
        out.push_back(hex[byte & 15]);
    }
    return out;
}

inline bool equalHash(const std::string& a, const std::string& b) {
    if (a.size() != b.size()) return false;
    unsigned diff = 0;
    for (size_t i = 0; i < a.size(); ++i) diff |= static_cast<unsigned char>(a[i] ^ b[i]);
    return diff == 0;
}

/// Ambil manifes rilis terbaru dan putuskan. Tidak mengunduh apa pun.
inline Decision check(const std::string& currentVersion, Release& found,
                      const std::atomic_bool* cancelled = nullptr) {
    std::string body;
    if (!httpGet(kManifestUrl, body, kMaxManifestBytes, cancelled)) return Decision::Unreadable;
    return decide(currentVersion, body, &found);
}

enum class InstallResult {
    Started,       // installer berjalan, aplikasi sebaiknya menutup diri
    Cancelled,     // dibatalkan pengguna
    NetworkFailed, // unduhan gagal atau terputus
    HashMismatch,  // berkas sampai, tetapi bukan berkas yang dijanjikan
    WriteFailed,   // tidak bisa menulis ke folder sementara
    LaunchFailed,  // berkas sah, tetapi Windows menolak menjalankannya
};

/**
 * Unduh installer, verifikasi, jalankan.
 *
 * Berkas ditulis ke %TEMP% dengan nama bentukan sendiri. Hash dihitung dari
 * isi yang ada di memori, bukan dari berkas di disk, sehingga tidak ada
 * jendela waktu antara "diverifikasi" dan "dijalankan" yang bisa dipakai
 * mengganti isinya.
 */
inline InstallResult downloadAndRun(const Release& release, std::wstring& pathOut,
                                    const std::atomic_bool* cancelled = nullptr) {
    pathOut.clear();
    if (!allowedUrl(release.url) || !isHex64(release.sha256)) return InstallResult::HashMismatch;

    std::string payload;
    if (!httpGet(release.url, payload, kMaxInstallerBytes, cancelled)) {
        return (cancelled && cancelled->load()) ? InstallResult::Cancelled
                                                : InstallResult::NetworkFailed;
    }
    if (release.bytes > 0 && static_cast<std::int64_t>(payload.size()) != release.bytes) {
        return InstallResult::HashMismatch;
    }
    if (!equalHash(sha256Hex(payload), release.sha256)) return InstallResult::HashMismatch;

    wchar_t folder[MAX_PATH]{};
    const DWORD n = GetTempPathW(MAX_PATH, folder);
    if (n == 0 || n >= MAX_PATH) return InstallResult::WriteFailed;
    std::wstring path = std::wstring(folder) + widen(installerFileName(release));

    HANDLE file = CreateFileW(path.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS,
                              FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE) return InstallResult::WriteFailed;
    DWORD written = 0;
    const BOOL ok = WriteFile(file, payload.data(), static_cast<DWORD>(payload.size()), &written, nullptr);
    CloseHandle(file);
    if (!ok || written != payload.size()) {
        DeleteFileW(path.c_str());
        return InstallResult::WriteFailed;
    }

    SHELLEXECUTEINFOW info{};
    info.cbSize = sizeof(info);
    info.fMask = SEE_MASK_NOASYNC;
    info.lpVerb = L"runas";  // installer butuh elevasi; UAC yang bertanya
    info.lpFile = path.c_str();
    info.nShow = SW_SHOWNORMAL;
    if (!ShellExecuteExW(&info)) {
        const DWORD error = GetLastError();
        DeleteFileW(path.c_str());
        return error == ERROR_CANCELLED ? InstallResult::Cancelled : InstallResult::LaunchFailed;
    }
    pathOut = path;
    return InstallResult::Started;
}

}  // namespace xydesk::updater
