#pragma once
// Gerbang masuk panel host: Welcome → Login → Ready.
//
// Berkas ini sengaja murni angka dan state — tanpa Windows, tanpa GDI, tanpa
// jaringan — supaya seluruh aturan "kapan boleh masuk" bisa diuji di Linux
// lewat packaging/tests/native-onboarding-test.cpp. main.cpp hanya menggambar
// apa yang diputuskan di sini.
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <string>

namespace xydesk::onboarding {

// ── Tahap gerbang ───────────────────────────────────────────────────────────
enum class Stage {
    Welcome, // Sambutan: apa itu XyDesk, satu tombol "Mulai".
    Login,   // Pilihan masuk: Google atau email.
    Ready,   // Sudah masuk — panel utama boleh tampil.
};

inline const char* stageName(Stage stage) {
    switch (stage) {
    case Stage::Welcome: return "Welcome";
    case Stage::Login: return "Login";
    case Stage::Ready: return "Ready";
    }
    return "Welcome";
}

// Cara masuk yang ditawarkan.
enum class Method { Google, Email };

// Apa yang sedang terjadi pada percobaan masuk.
enum class LoginState {
    Idle,     // Belum mencoba.
    Waiting,  // Browser terbuka / kode terkirim, menunggu pengguna.
    Failed,   // Gagal; pesan ada di Gate::message.
};

/**
 * Aturan gerbang. Satu sumber kebenaran untuk pertanyaan "boleh masuk panel?".
 *
 * Yang sengaja TIDAK dilakukan di sini: menyentuh kredensial, membuka browser,
 * atau memutuskan apakah engine host boleh jalan. Gerbang ini hanya mengatur
 * urutan layar; identitas engine tetap milik `identity.rs` dan tidak bergantung
 * pada akun UI.
 */
struct Gate {
    Stage stage = Stage::Welcome;
    LoginState login = LoginState::Idle;
    Method method = Method::Google;
    bool sawWelcome = false; // Sambutan hanya untuk pemasangan pertama.
    std::string message;     // Pesan gagal, dalam bahasa pengguna.
    std::string email;       // Akun yang sedang masuk (kosong = belum).

    /**
     * Keadaan saat panel dibuka.
     * @param hasSession sesi tersimpan di Credential Manager masih sah.
     * @param welcomeSeen sambutan sudah pernah ditampilkan di mesin ini.
     */
    void start(bool hasSession, bool welcomeSeen, const std::string& account = {}) {
        sawWelcome = welcomeSeen;
        login = LoginState::Idle;
        message.clear();
        if (hasSession) {
            stage = Stage::Ready;
            sawWelcome = true;
            email = account;
            return;
        }
        email.clear();
        // Sambutan tidak diulang tiap kali keluar-masuk akun: yang pernah
        // melihatnya langsung mendarat di layar masuk.
        stage = welcomeSeen ? Stage::Login : Stage::Welcome;
    }

    /** Tombol "Mulai" di sambutan. */
    void beginLogin() {
        if (stage != Stage::Welcome) return;
        sawWelcome = true;
        stage = Stage::Login;
        login = LoginState::Idle;
        message.clear();
    }

    /** Pengguna menekan salah satu tombol masuk. */
    void attempt(Method chosen) {
        if (stage != Stage::Login) return;
        method = chosen;
        login = LoginState::Waiting;
        message.clear();
    }

    /** Percobaan masuk berhasil. */
    void succeed(const std::string& account) {
        stage = Stage::Ready;
        login = LoginState::Idle;
        sawWelcome = true;
        email = account;
        message.clear();
    }

    /** Percobaan masuk gagal; pengguna tetap di layar masuk dan bisa mengulang. */
    void fail(const std::string& reason) {
        stage = Stage::Login;
        login = LoginState::Failed;
        message = reason.empty() ? "Masuk tidak selesai. Coba lagi." : reason;
    }

    /** Pengguna membatalkan (menutup tab browser, menekan Esc). */
    void cancel() {
        if (login != LoginState::Waiting) return;
        login = LoginState::Idle;
        message.clear();
    }

    /** Keluar akun: kembali ke layar masuk, bukan ke sambutan. */
    void signOut() {
        stage = Stage::Login;
        login = LoginState::Idle;
        sawWelcome = true;
        email.clear();
        message.clear();
    }

    /** Panel utama hanya boleh tampil pada tahap ini. */
    [[nodiscard]] bool panelVisible() const { return stage == Stage::Ready; }

    /** Tombol masuk dinonaktifkan selama menunggu supaya tidak dobel-klik. */
    [[nodiscard]] bool busy() const { return login == LoginState::Waiting; }
};

// ── Bahasa gerak ────────────────────────────────────────────────────────────
// Kurva ala macOS: cepat di awal, mendarat halus, tanpa pantulan berlebihan.
// Semua fungsi menerima t pada [0,1] dan mengembalikan [0,1] (kecuali spring,
// yang boleh sedikit melewati 1 lalu kembali).

/** Standar untuk perpindahan layar: deselerasi tajam, mendarat tenang. */
inline float easeOutExpo(float t) {
    t = std::clamp(t, 0.0f, 1.0f);
    return t >= 1.0f ? 1.0f : 1.0f - std::pow(2.0f, -10.0f * t);
}

/** Untuk elemen yang muncul (kartu, tombol): sedikit mengembang lalu diam. */
inline float easeOutBack(float t) {
    t = std::clamp(t, 0.0f, 1.0f);
    constexpr float c1 = 1.70158f;
    constexpr float c3 = c1 + 1.0f;
    const float p = t - 1.0f;
    return 1.0f + c3 * p * p * p + c1 * p * p;
}

/** Untuk umpan balik tekan: redaman kritis, tanpa getar yang mengganggu. */
inline float spring(float t) {
    t = std::clamp(t, 0.0f, 1.0f);
    if (t >= 1.0f) return 1.0f;
    constexpr float omega = 12.0f;
    return 1.0f - std::exp(-omega * t) * (1.0f + omega * t);
}

/** Interpolasi linear; dipakai setelah t dilewatkan ke salah satu kurva. */
inline float mix(float from, float to, float t) { return from + (to - from) * t; }

/** Interpolasi untuk koordinat piksel — pembulatan, bukan pemotongan. */
inline int mixInt(int from, int to, float t) {
    return static_cast<int>(std::lround(mix(static_cast<float>(from), static_cast<float>(to), t)));
}

/**
 * Jam animasi berbasis waktu, bukan berbasis frame.
 *
 * Kenapa penting: panel menggambar pada timer 16 ms, tapi Windows tidak
 * menjamin timer itu tepat waktu. Kalau kemajuan animasi dihitung per frame
 * ("+0,06 tiap tick"), animasi melambat persis saat sistem sedang sibuk —
 * yaitu saat sesi remote berjalan. Dengan basis milidetik, durasinya tetap.
 */
struct Clock {
    std::uint64_t startMs = 0;
    std::uint32_t durationMs = 260;
    bool running = false;

    void begin(std::uint64_t nowMs, std::uint32_t duration) {
        startMs = nowMs;
        durationMs = duration == 0 ? 1 : duration;
        running = true;
    }

    /** Kemajuan mentah [0,1]; menutup diri sendiri saat selesai. */
    float progress(std::uint64_t nowMs) {
        if (!running) return 1.0f;
        if (nowMs <= startMs) return 0.0f;
        const float t = static_cast<float>(nowMs - startMs) / static_cast<float>(durationMs);
        if (t >= 1.0f) {
            running = false;
            return 1.0f;
        }
        return t;
    }
};

/** Durasi baku, disamakan dengan rasa macOS. */
constexpr std::uint32_t kStageMs = 320;  // Pindah layar gerbang.
constexpr std::uint32_t kCardMs = 420;   // Kartu dan isinya muncul.
constexpr std::uint32_t kPressMs = 180;  // Umpan balik tekan tombol.

/**
 * Penundaan berurutan: elemen ke-`index` mulai bergerak setelah elemen
 * sebelumnya. Inilah yang membuat layar terasa "disusun", bukan "dilempar".
 * Mengembalikan t lokal [0,1] untuk elemen tersebut.
 */
inline float staggered(float t, int index, int count, float overlap = 0.55f) {
    if (count <= 1) return std::clamp(t, 0.0f, 1.0f);
    const float step = (1.0f - overlap) / static_cast<float>(count - 1);
    const float begin = step * static_cast<float>(std::clamp(index, 0, count - 1));
    const float span = 1.0f - begin;
    if (span <= 0.0f) return std::clamp(t, 0.0f, 1.0f);
    return std::clamp((t - begin) / span, 0.0f, 1.0f);
}

} // namespace xydesk::onboarding
