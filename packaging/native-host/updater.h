#pragma once

// Pembaruan host Windows — bagian yang bisa diuji tanpa Windows.
//
// Sampai sekarang host Windows tidak punya jalur pembaruan sama sekali: APK
// membaca `update.json` dari GitHub Release, sedangkan PC harus dipasang
// ulang dengan tangan. Berkas ini memuat seluruh keputusannya — mem-parse
// manifes, membandingkan versi, dan menolak manifes yang mencurigakan —
// sebagai fungsi murni, supaya logikanya terbukti di job Linux dan hanya
// jaringan serta pemasangannya yang tersisa untuk runner Windows.
//
// Aturan keras yang dijaga di sini: **unduhan hanya boleh dari rilis resmi
// repo ini, lewat HTTPS, dan hash SHA-256-nya wajib ada**. Manifes yang
// melanggar salah satunya ditolak mentah-mentah, bukan sekadar diberi
// peringatan — karena berkas yang diunduh di jalur ini akan dijalankan
// sebagai installer.

#include <algorithm>
#include <cstdint>
#include <optional>
#include <string>
#include <vector>

#include "vendor/json/json.hpp"

namespace xydesk::updater {

/// Satu-satunya asal unduhan yang diterima.
inline constexpr const char* kAllowedPrefix =
    "https://github.com/xykal/XyDesk/releases/download/";

/// Alamat manifes rilis terbaru (sama dengan yang dibaca APK).
inline constexpr const char* kManifestUrl =
    "https://github.com/xykal/XyDesk/releases/latest/download/update.json";

/// Installer lebih besar dari ini dianggap tidak masuk akal (NSIS ~6 MB).
inline constexpr std::int64_t kMaxInstallerBytes = 300LL * 1024 * 1024;

/// Manifes sendiri kecil; batas ini menahan balasan yang membengkak.
inline constexpr std::int64_t kMaxManifestBytes = 256 * 1024;

struct Version {
    int major = 0;
    int minor = 0;
    int patch = 0;
    long long build = 0;  // angka setelah '+', 0 bila tidak ada
};

/// "6.11.11+76" → {6,11,11,76}. Menolak bentuk yang tidak dikenal, termasuk
/// angka berlebih, huruf, dan bagian kosong.
inline std::optional<Version> parseVersion(const std::string& text) {
    Version out;
    int part = 0;
    bool digitSeen = false;
    bool inBuild = false;
    long long value = 0;
    auto commit = [&]() -> bool {
        if (!digitSeen) return false;
        if (inBuild) {
            out.build = value;
        } else if (part == 0) {
            out.major = static_cast<int>(value);
        } else if (part == 1) {
            out.minor = static_cast<int>(value);
        } else if (part == 2) {
            out.patch = static_cast<int>(value);
        } else {
            return false;
        }
        value = 0;
        digitSeen = false;
        return true;
    };
    for (char ch : text) {
        if (ch >= '0' && ch <= '9') {
            value = value * 10 + (ch - '0');
            if (value > 1000000000LL) return std::nullopt;
            digitSeen = true;
            continue;
        }
        if (ch == '.' && !inBuild) {
            if (!commit()) return std::nullopt;
            ++part;
            continue;
        }
        if (ch == '+' && !inBuild) {
            if (!commit()) return std::nullopt;
            if (part != 2) return std::nullopt;
            inBuild = true;
            continue;
        }
        return std::nullopt;
    }
    if (!commit()) return std::nullopt;
    if (!inBuild && part != 2) return std::nullopt;
    return out;
}

/// Urutan: major, minor, patch, lalu nomor build.
inline int compare(const Version& a, const Version& b) {
    if (a.major != b.major) return a.major < b.major ? -1 : 1;
    if (a.minor != b.minor) return a.minor < b.minor ? -1 : 1;
    if (a.patch != b.patch) return a.patch < b.patch ? -1 : 1;
    if (a.build != b.build) return a.build < b.build ? -1 : 1;
    return 0;
}

inline bool newer(const Version& candidate, const Version& current) {
    return compare(candidate, current) > 0;
}

struct Release {
    std::string version;   // "6.11.12"
    long long build = 0;   // 77
    std::string tag;       // "v6.11.12"
    std::string title;
    std::string summary;
    std::string url;       // installer EXE
    std::string sha256;    // 64 heksadesimal huruf kecil
    std::int64_t bytes = 0;
};

inline bool isHex64(const std::string& text) {
    if (text.size() != 64) return false;
    for (char ch : text) {
        const bool digit = ch >= '0' && ch <= '9';
        const bool lower = ch >= 'a' && ch <= 'f';
        if (!digit && !lower) return false;
    }
    return true;
}

inline bool allowedUrl(const std::string& url) {
    const std::string prefix = kAllowedPrefix;
    if (url.size() <= prefix.size()) return false;
    if (url.compare(0, prefix.size(), prefix) != 0) return false;
    // Tidak ada ruang untuk trik path: hanya karakter alamat yang wajar.
    for (char ch : url) {
        const bool ok = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') ||
                        (ch >= '0' && ch <= '9') || ch == ':' || ch == '/' ||
                        ch == '.' || ch == '-' || ch == '_' || ch == '+';
        if (!ok) return false;
    }
    if (url.find("..") != std::string::npos) return false;
    if (url.size() < 5 || url.compare(url.size() - 4, 4, ".exe") != 0) return false;
    return true;
}

/**
 * Baca manifes `update.json`. Mengembalikan nullopt bila manifes tidak
 * dikenal, tidak lengkap, atau melanggar salah satu aturan keras.
 */
inline std::optional<Release> parseManifest(const std::string& text) {
    if (text.empty() || static_cast<std::int64_t>(text.size()) > kMaxManifestBytes) {
        return std::nullopt;
    }
    const auto doc = nlohmann::json::parse(text, nullptr, false);
    if (doc.is_discarded() || !doc.is_object()) return std::nullopt;

    const auto schema = doc.value("schema", 0);
    if (schema < 2) return std::nullopt;

    Release out;
    out.version = doc.value("version", std::string{});
    if (!parseVersion(out.version)) return std::nullopt;
    out.build = doc.value("build", 0LL);
    if (out.build < 0) return std::nullopt;
    out.tag = doc.value("tag", std::string{});
    out.title = doc.value("title", std::string{});
    out.summary = doc.value("summary", std::string{});

    if (!doc.contains("windows") || !doc["windows"].is_object()) return std::nullopt;
    const auto& windows = doc["windows"];
    if (!windows.contains("x64") || !windows["x64"].is_object()) return std::nullopt;
    const auto& x64 = windows["x64"];

    out.url = x64.value("url", std::string{});
    out.sha256 = x64.value("sha256", std::string{});
    out.bytes = x64.value("bytes", 0LL);

    if (!allowedUrl(out.url)) return std::nullopt;
    if (!isHex64(out.sha256)) return std::nullopt;
    if (out.bytes <= 0 || out.bytes > kMaxInstallerBytes) return std::nullopt;
    return out;
}

/// Versi lengkap rilis, memakai nomor build bila ada.
inline Version releaseVersion(const Release& release) {
    Version v = parseVersion(release.version).value_or(Version{});
    if (release.build > 0) v.build = release.build;
    return v;
}

enum class Decision {
    UpToDate,    // sudah versi terbaru (atau lebih baru)
    Available,   // ada pembaruan
    Unreadable,  // manifes tidak bisa dipercaya
};

/**
 * Keputusan akhir: perlu menawarkan pembaruan atau tidak.
 *
 * `currentText` adalah isi VERSION yang dikompilasi ke dalam host.
 */
inline Decision decide(const std::string& currentText, const std::string& manifestText,
                       Release* found = nullptr) {
    const auto current = parseVersion(currentText);
    if (!current) return Decision::Unreadable;
    const auto release = parseManifest(manifestText);
    if (!release) return Decision::Unreadable;
    if (found) *found = *release;
    return newer(releaseVersion(*release), *current) ? Decision::Available : Decision::UpToDate;
}

/// "8,1 MB" — koma desimal, gaya Indonesia, satu angka di belakang koma.
inline std::string humanBytes(std::int64_t bytes) {
    if (bytes < 0) return "0 B";
    if (bytes < 1024) return std::to_string(bytes) + " B";
    const char* units[] = {"kB", "MB", "GB"};
    double value = static_cast<double>(bytes) / 1024.0;
    int unit = 0;
    while (value >= 1024.0 && unit < 2) {
        value /= 1024.0;
        ++unit;
    }
    const long long scaled = static_cast<long long>(value * 10.0 + 0.5);
    std::string out = std::to_string(scaled / 10) + "," + std::to_string(scaled % 10);
    out += " ";
    out += units[unit];
    return out;
}

/**
 * Nama berkas installer di folder sementara. Dibentuk sendiri dari versi —
 * tidak pernah dari URL — supaya nama dari jaringan tidak bisa mengarahkan
 * penulisan ke tempat lain.
 */
inline std::string installerFileName(const Release& release) {
    std::string safe;
    for (char ch : release.version) {
        const bool ok = (ch >= '0' && ch <= '9') || ch == '.';
        safe.push_back(ok ? ch : '-');
        if (safe.size() >= 24) break;
    }
    if (safe.empty()) safe = "baru";
    return "XyDesk-" + safe + "-x64.exe";
}

/// Kalimat satu baris untuk tray dan kotak dialog.
inline std::string offerText(const Release& release) {
    return "XyDesk " + release.version + " tersedia (" + humanBytes(release.bytes) + ").";
}

}  // namespace xydesk::updater
