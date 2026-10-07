#pragma once
// Masuk lewat email, di dalam aplikasi.
//
// Sebelumnya tombol "Masuk dengan email" hanya membuka
// https://xydesk.my.id/masuk di browser dan berhenti di situ: halaman web
// menyimpan sesinya sendiri, panel tidak pernah menerima token, dan pengguna
// kembali ke jendela yang masih meminta login. Secara praktis tombol itu
// jalan buntu.
//
// Alurnya sekarang sama dengan web dan APK — kode sekali pakai (OTP) enam
// digit lewat `POST /auth/request-otp` lalu `POST /auth/verify-otp` — tetapi
// seluruh langkahnya terjadi di dalam kartu gerbang.
//
// Berkas ini sengaja murni: tanpa Windows, tanpa WinHTTP, tanpa GDI. Semua
// aturan "boleh kirim belum?", "kode ini sah?", "pesan apa yang muncul?" bisa
// diuji di Linux lewat packaging/tests/native-email-login-test.cpp. main.cpp
// hanya menggambar dan memanggil jaringan atas keputusan di sini.
#include <algorithm>
#include <cctype>
#include <cstdint>
#include <string>

namespace xydesk::emaillogin {

// Dicerminkan dari cloudflare/src/auth.js — kalau Worker berubah, dua angka
// ini ikut berubah. Panel boleh lebih longgar dari server, tidak boleh lebih
// ketat secara diam-diam.
constexpr int kCodeLength = 6;          // /^\d{6}$/ di verifyOtp
constexpr int kResendCooldownSec = 60;  // OTP_RESEND_COOLDOWN
constexpr int kCodeTtlSec = 600;        // OTP_TTL, 10 menit
constexpr int kMaxAttempts = 5;         // OTP_MAX_ATTEMPTS
constexpr std::size_t kMaxEmailLength = 254; // batas praktis alamat email

/** Buang spasi di tepi dan turunkan ke huruf kecil (Worker juga lowercase). */
inline std::string normalizeEmail(std::string value) {
    const auto notSpace = [](unsigned char c) { return !std::isspace(c); };
    value.erase(value.begin(), std::find_if(value.begin(), value.end(), notSpace));
    value.erase(std::find_if(value.rbegin(), value.rend(), notSpace).base(), value.end());
    if (value.size() > kMaxEmailLength) value.resize(kMaxEmailLength);
    std::transform(value.begin(), value.end(), value.begin(),
        [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return value;
}

/**
 * Validasi alamat, menirukan `validateEmail` di cloudflare/src/auth.js:
 * /^[^\s@]+@[^\s@]+\.[^\s@]+$/. Sengaja selonggar itu — menolak alamat sah
 * yang terlihat aneh jauh lebih merugikan daripada meneruskan satu permintaan
 * yang nanti ditolak server.
 */
inline bool validEmail(const std::string& value) {
    if (value.empty() || value.size() > kMaxEmailLength) return false;
    const auto at = value.find('@');
    if (at == std::string::npos || at == 0) return false;
    if (value.find('@', at + 1) != std::string::npos) return false;
    const auto domain = value.substr(at + 1);
    if (domain.empty()) return false;
    const auto dot = domain.find('.');
    if (dot == std::string::npos || dot == 0 || dot + 1 >= domain.size()) return false;
    for (unsigned char c : value) {
        if (std::isspace(c) || c < 32 || c == 127) return false;
    }
    return true;
}

/**
 * Saring apa pun yang diketik/ditempel menjadi digit saja, maksimum enam.
 * Email dari Resend menampilkan kode sebagai "123 456" dan orang menempelnya
 * apa adanya; menolak tempelan itu hanya membuat pengguna mengetik ulang.
 */
inline std::string digitsOnly(const std::string& value) {
    std::string out;
    for (unsigned char c : value) {
        if (std::isdigit(c)) out += static_cast<char>(c);
        if (out.size() == static_cast<std::size_t>(kCodeLength)) break;
    }
    return out;
}

inline bool validCode(const std::string& value) {
    return value.size() == static_cast<std::size_t>(kCodeLength)
        && std::all_of(value.begin(), value.end(),
            [](unsigned char c) { return std::isdigit(c) != 0; });
}

/** Escape minimal untuk menaruh nilai ke dalam JSON tanpa menarik parser. */
inline std::string jsonEscape(const std::string& value) {
    std::string out;
    for (unsigned char c : value) {
        switch (c) {
        case '"': out += "\\\""; break;
        case '\\': out += "\\\\"; break;
        case '\n': out += "\\n"; break;
        case '\r': out += "\\r"; break;
        case '\t': out += "\\t"; break;
        default:
            if (c < 32) {
                static const char* hex = "0123456789abcdef";
                out += "\\u00";
                out += hex[c >> 4];
                out += hex[c & 15];
            } else {
                out += static_cast<char>(c);
            }
        }
    }
    return out;
}

inline std::string requestBody(const std::string& email) {
    return "{\"email\":\"" + jsonEscape(email) + "\"}";
}

inline std::string verifyBody(const std::string& email, const std::string& code) {
    return "{\"email\":\"" + jsonEscape(email) + "\",\"otp\":\"" + jsonEscape(code) + "\"}";
}

/**
 * Terjemahkan kode galat Worker menjadi kalimat yang berguna.
 *
 * "Gagal" saja tidak memberi tahu apa yang harus dilakukan; tiap kalimat di
 * bawah menyebut langkah berikutnya. `wrong-otp` sengaja tidak menyebut sisa
 * percobaan kecuali tinggal sedikit — hitungan mundur membuat orang panik
 * mengetik.
 */
inline std::string messageFor(const std::string& code, int attemptsLeft = -1) {
    if (code == "invalid-email") return "Alamat email itu tidak dikenali. Periksa ejaannya.";
    if (code == "invalid-input") return "Kode harus enam angka.";
    if (code == "wrong-otp") {
        if (attemptsLeft == 1) return "Kode salah. Tersisa satu percobaan sebelum kode hangus.";
        if (attemptsLeft > 1) {
            return "Kode salah. Tersisa " + std::to_string(attemptsLeft) + " percobaan.";
        }
        return "Kode salah. Periksa lagi email yang masuk.";
    }
    if (code == "otp-expired") return "Kode sudah kedaluwarsa. Minta kode baru.";
    if (code == "too-many-attempts") return "Terlalu banyak percobaan. Minta kode baru.";
    if (code == "cooldown") return "Kode baru saja dikirim. Tunggu sebentar sebelum minta lagi.";
    if (code == "rate-limited") return "Terlalu sering meminta kode. Coba lagi beberapa menit lagi.";
    if (code == "account-disabled") return "Akun ini dinonaktifkan. Hubungi dukungan XyDesk.";
    if (code == "bad-json" || code == "method-not-allowed") {
        return "Permintaan ditolak server. Perbarui XyDesk lalu coba lagi.";
    }
    if (code == "offline") return "Tidak ada koneksi ke server XyDesk. Periksa jaringan.";
    if (code == "store-failed") {
        return "Windows tidak dapat menyimpan sesi dengan aman. Masuk belum tersimpan.";
    }
    return "Masuk belum berhasil. Coba lagi sebentar lagi.";
}

// ── Tahap formulir ──────────────────────────────────────────────────────────
enum class Step {
    Email, // Mengetik alamat.
    Code,  // Kode sudah dikirim, menunggu enam digit.
    Done,  // Terverifikasi; main.cpp menyimpan token dan menutup gerbang.
};

/**
 * Keadaan formulir masuk-email di dalam kartu gerbang.
 *
 * Yang sengaja TIDAK disimpan di sini: token, kata sandi, atau apa pun yang
 * rahasia. Panel hanya memegang alamat dan enam digit yang sudah dikirimkan
 * pengguna sendiri; token hasil verifikasi langsung pindah ke Credential
 * Manager di main.cpp dan tidak pernah singgah di struct ini.
 */
struct Form {
    Step step = Step::Email;
    std::string email;    // sudah dinormalisasi saat submit
    std::string typed;    // isi kotak yang sedang aktif
    std::string code;     // enam digit terakhir yang diketik
    std::string message;  // kalimat untuk pengguna
    bool error = false;   // message adalah kegagalan, bukan petunjuk
    bool busy = false;    // permintaan jaringan sedang jalan
    int attemptsLeft = kMaxAttempts;
    std::uint64_t resendAtMs = 0;  // boleh minta kode lagi setelah ini
    std::uint64_t expiresAtMs = 0; // kode hangus setelah ini

    void reset() { *this = Form{}; }

    /** Boleh menekan "Kirim kode"? */
    [[nodiscard]] bool canSubmitEmail() const {
        return !busy && step == Step::Email && validEmail(normalizeEmail(typed));
    }

    /** Boleh menekan "Masuk"? */
    [[nodiscard]] bool canSubmitCode() const {
        return !busy && step == Step::Code && validCode(digitsOnly(typed));
    }

    [[nodiscard]] bool canResend(std::uint64_t nowMs) const {
        return !busy && step == Step::Code && nowMs >= resendAtMs;
    }

    /** Sisa detik sebelum tombol "Kirim ulang" hidup (0 = sudah boleh). */
    [[nodiscard]] int resendInSec(std::uint64_t nowMs) const {
        if (nowMs >= resendAtMs) return 0;
        return static_cast<int>((resendAtMs - nowMs + 999) / 1000);
    }

    /** Kode sudah hangus menurut jam lokal? Server tetap yang memutuskan. */
    [[nodiscard]] bool codeExpired(std::uint64_t nowMs) const {
        return step == Step::Code && expiresAtMs != 0 && nowMs >= expiresAtMs;
    }

    /** Pengguna menekan "Kirim kode"; kembalikan body yang harus dikirim. */
    std::string beginRequest(std::uint64_t nowMs) {
        (void)nowMs;
        const auto normalized = normalizeEmail(step == Step::Email ? typed : email);
        if (!validEmail(normalized)) {
            error = true;
            message = messageFor("invalid-email");
            return {};
        }
        email = normalized;
        busy = true;
        error = false;
        message = "Mengirim kode ke " + email + "…";
        return requestBody(email);
    }

    /** Server menerima permintaan: pindah ke langkah kode. */
    void onCodeSent(std::uint64_t nowMs, int cooldownSec = kResendCooldownSec) {
        busy = false;
        error = false;
        step = Step::Code;
        typed.clear();
        code.clear();
        attemptsLeft = kMaxAttempts;
        resendAtMs = nowMs + static_cast<std::uint64_t>(std::max(0, cooldownSec)) * 1000;
        expiresAtMs = nowMs + static_cast<std::uint64_t>(kCodeTtlSec) * 1000;
        message = "Kode enam digit dikirim ke " + email + ". Berlaku 10 menit.";
    }

    /**
     * Permintaan kode gagal. `retryInSec` dari field `resend_in`/`retry_in`
     * Worker; nol berarti tidak diberitahu.
     */
    void onRequestFailed(std::uint64_t nowMs, const std::string& code_, int retryInSec = 0) {
        busy = false;
        error = true;
        message = messageFor(code_);
        if (retryInSec > 0) {
            resendAtMs = nowMs + static_cast<std::uint64_t>(retryInSec) * 1000;
        }
        // Cooldown berarti kode sebelumnya masih hidup di server: tetap
        // tunjukkan kotak kode supaya pengguna bisa memakainya, bukan
        // memaksanya mengulang dari alamat email.
        if ((code_ == "cooldown" || code_ == "rate-limited") && step == Step::Email
            && validEmail(email)) {
            step = Step::Code;
            typed.clear();
            if (expiresAtMs == 0) {
                expiresAtMs = nowMs + static_cast<std::uint64_t>(kCodeTtlSec) * 1000;
            }
        }
    }

    /** Pengguna menekan "Masuk"; kembalikan body verifikasi. */
    std::string beginVerify() {
        const auto digits = digitsOnly(typed);
        if (!validCode(digits)) {
            error = true;
            message = messageFor("invalid-input");
            return {};
        }
        code = digits;
        busy = true;
        error = false;
        message = "Memeriksa kode…";
        return verifyBody(email, code);
    }

    /** Verifikasi berhasil — token sudah diterima main.cpp. */
    void onVerified() {
        busy = false;
        error = false;
        step = Step::Done;
        typed.clear();
        code.clear();
        message.clear();
    }

    /** Verifikasi gagal; pengguna tetap di kotak kode kecuali kode hangus. */
    void onVerifyFailed(std::uint64_t nowMs, const std::string& code_) {
        busy = false;
        error = true;
        if (code_ == "wrong-otp") {
            attemptsLeft = std::max(0, attemptsLeft - 1);
            message = messageFor(code_, attemptsLeft);
            typed.clear();
            return;
        }
        message = messageFor(code_);
        if (code_ == "otp-expired" || code_ == "too-many-attempts") {
            // Kode mati: izinkan minta yang baru sekarang juga, dan kosongkan
            // kotaknya supaya tidak ada enam digit basi yang menggoda.
            typed.clear();
            code.clear();
            expiresAtMs = 0;
            resendAtMs = nowMs;
            attemptsLeft = kMaxAttempts;
        }
    }

    /** Jaringan mati / server tidak menjawab. */
    void onOffline() {
        busy = false;
        error = true;
        message = messageFor("offline");
    }

    /** Tombol "Ganti email" / tombol kembali. */
    void backToEmail() {
        if (busy) return;
        step = Step::Email;
        typed = email;
        code.clear();
        error = false;
        message.clear();
        attemptsLeft = kMaxAttempts;
    }

    /** Label tombol utama formulir, mengikuti langkah dan kesibukan. */
    [[nodiscard]] const char* actionLabel() const {
        if (step == Step::Code) return busy ? "Memeriksa…" : "Masuk";
        return busy ? "Mengirim…" : "Kirim kode";
    }

    /** Petunjuk di bawah kotak isian saat tidak ada pesan galat. */
    [[nodiscard]] std::string hint(std::uint64_t nowMs) const {
        if (!message.empty()) return message;
        if (step == Step::Email) return "Kami kirim kode sekali pakai, tanpa kata sandi.";
        const int wait = resendInSec(nowMs);
        if (wait > 0) return "Kirim ulang kode dalam " + std::to_string(wait) + " detik.";
        return "Belum menerima kode? Kirim ulang.";
    }
};

} // namespace xydesk::emaillogin
