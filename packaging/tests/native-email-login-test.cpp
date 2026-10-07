// Uji formulir masuk-email di dalam panel host: validasi alamat, saringan
// digit, badan permintaan, pesan galat, dan mesin langkah Email → Kode →
// Selesai. Murni angka dan string — tidak butuh Windows maupun jaringan.
#include "email_login.h"

#include <cstdio>
#include <string>

namespace {

int failures = 0;

void check(bool condition, const std::string& what) {
    if (condition) return;
    std::printf("GAGAL: %s\n", what.c_str());
    ++failures;
}

void checkEqual(const std::string& got, const std::string& want, const std::string& what) {
    check(got == want, what + " (dapat \"" + got + "\", mau \"" + want + "\")");
}

using xydesk::emaillogin::Form;
using xydesk::emaillogin::Step;
namespace el = xydesk::emaillogin;

// ── Alamat email ────────────────────────────────────────────────────────────

void alamatDinormalisasi() {
    checkEqual(el::normalizeEmail("  Budi@Contoh.ID \n"), "budi@contoh.id",
        "spasi dibuang dan huruf diturunkan");
    checkEqual(el::normalizeEmail(""), "", "string kosong tetap kosong");
    check(el::normalizeEmail(std::string(300, 'a') + "@x.id").size() <= el::kMaxEmailLength,
        "alamat kepanjangan dipotong");
}

void alamatDivalidasi() {
    check(el::validEmail("budi@contoh.id"), "alamat biasa diterima");
    check(el::validEmail("a.b+tag@mail.co.id"), "plus dan titik diterima");
    check(!el::validEmail("budi@contoh"), "domain tanpa titik ditolak");
    check(!el::validEmail("budi.contoh.id"), "tanpa @ ditolak");
    check(!el::validEmail("@contoh.id"), "tanpa bagian lokal ditolak");
    check(!el::validEmail("a@b@c.id"), "dua @ ditolak");
    check(!el::validEmail("budi @contoh.id"), "spasi di tengah ditolak");
    check(!el::validEmail("budi@.id"), "domain diawali titik ditolak");
    check(!el::validEmail("budi@contoh."), "domain diakhiri titik ditolak");
    check(!el::validEmail(""), "kosong ditolak");
}

// ── Kode enam digit ─────────────────────────────────────────────────────────

void kodeDisaring() {
    checkEqual(el::digitsOnly("123 456"), "123456", "spasi di tempelan dibuang");
    checkEqual(el::digitsOnly("kode: 98-76-54 abc"), "987654", "huruf dan tanda dibuang");
    checkEqual(el::digitsOnly("1234567890"), "123456", "lebih dari enam dipotong");
    checkEqual(el::digitsOnly("abc"), "", "tanpa digit menghasilkan kosong");
    check(el::validCode("000000"), "nol enam kali adalah kode sah");
    check(!el::validCode("12345"), "lima digit ditolak");
    check(!el::validCode("12345a"), "huruf di dalam kode ditolak");
}

// ── Badan permintaan ────────────────────────────────────────────────────────

void badanPermintaanBerbentukJson() {
    checkEqual(el::requestBody("budi@contoh.id"), "{\"email\":\"budi@contoh.id\"}",
        "badan request-otp");
    checkEqual(el::verifyBody("budi@contoh.id", "123456"),
        "{\"email\":\"budi@contoh.id\",\"otp\":\"123456\"}", "badan verify-otp");
    // Tanda petik di alamat tidak boleh memecah JSON.
    checkEqual(el::jsonEscape("a\"b\\c"), "a\\\"b\\\\c", "petik dan backslash di-escape");
    check(el::jsonEscape(std::string(1, '\n')) == "\\n", "baris baru di-escape");
}

// ── Pesan untuk pengguna ────────────────────────────────────────────────────

void pesanGalatMenyebutLangkahBerikutnya() {
    check(el::messageFor("otp-expired").find("kode baru") != std::string::npos,
        "kode hangus menyuruh minta kode baru");
    check(el::messageFor("wrong-otp", 1).find("satu percobaan") != std::string::npos,
        "percobaan terakhir diberi peringatan");
    check(el::messageFor("wrong-otp", 3).find("3") != std::string::npos,
        "sisa percobaan disebut saat masih banyak");
    check(!el::messageFor("entah-apa").empty(), "kode tak dikenal tetap punya kalimat");
    check(el::messageFor("store-failed").find("Windows") != std::string::npos,
        "gagal menyimpan sesi menyebut Windows");
    check(el::messageFor("offline").find("jaringan") != std::string::npos
        || el::messageFor("offline").find("koneksi") != std::string::npos,
        "offline menyebut jaringan");
}

// ── Mesin langkah ───────────────────────────────────────────────────────────

void alurLengkapSampaiMasuk() {
    Form form;
    check(form.step == Step::Email, "mulai di kotak email");
    check(!form.canSubmitEmail(), "tombol mati saat kotak kosong");

    form.typed = "Budi@Contoh.ID";
    check(form.canSubmitEmail(), "tombol hidup untuk alamat sah");

    const auto body = form.beginRequest(1000);
    checkEqual(body, "{\"email\":\"budi@contoh.id\"}", "alamat dinormalisasi sebelum dikirim");
    check(form.busy, "sibuk selama permintaan jalan");
    check(!form.canSubmitEmail(), "tombol mati selagi sibuk");

    form.onCodeSent(1000);
    check(form.step == Step::Code, "pindah ke kotak kode setelah kode terkirim");
    check(!form.busy, "selesai sibuk");
    check(form.typed.empty(), "kotak dikosongkan untuk kode");
    check(form.resendInSec(1000) == el::kResendCooldownSec, "cooldown kirim ulang 60 detik");
    check(!form.canResend(1000), "kirim ulang masih terkunci");
    check(form.canResend(1000 + 60 * 1000), "kirim ulang hidup setelah cooldown");

    form.typed = "12 34 56";
    check(form.canSubmitCode(), "kode tertempel dengan spasi tetap sah");
    checkEqual(form.beginVerify(), "{\"email\":\"budi@contoh.id\",\"otp\":\"123456\"}",
        "badan verifikasi memakai digit bersih");

    form.onVerified();
    check(form.step == Step::Done, "selesai setelah verifikasi");
    check(form.message.empty(), "tidak ada pesan tersisa saat berhasil");
}

void alamatSalahTidakMengirimApaPun() {
    Form form;
    form.typed = "bukan-email";
    check(form.beginRequest(0).empty(), "tidak ada permintaan untuk alamat tak sah");
    check(form.error && !form.busy, "ditandai galat tanpa menyibukkan jaringan");
    check(form.step == Step::Email, "tetap di kotak email");
}

void kodeSalahMengurangiPercobaan() {
    Form form;
    form.typed = "budi@contoh.id";
    form.beginRequest(0);
    form.onCodeSent(0);
    form.typed = "111111";
    form.beginVerify();
    form.onVerifyFailed(1000, "wrong-otp");
    check(form.attemptsLeft == el::kMaxAttempts - 1, "satu percobaan terpakai");
    check(form.typed.empty(), "kotak dikosongkan supaya tidak mengirim ulang kode yang sama");
    check(form.step == Step::Code, "tetap di kotak kode");
    check(form.error, "ditandai galat");
}

void kodeHangusMembukaKirimUlangSegera() {
    Form form;
    form.typed = "budi@contoh.id";
    form.beginRequest(0);
    form.onCodeSent(0);
    form.typed = "123456";
    form.beginVerify();
    form.onVerifyFailed(5000, "otp-expired");
    check(form.canResend(5000), "boleh minta kode baru saat itu juga");
    check(form.code.empty() && form.typed.empty(), "digit basi dibuang");
    check(form.attemptsLeft == el::kMaxAttempts, "jatah percobaan disetel ulang");

    Form banyak;
    banyak.typed = "budi@contoh.id";
    banyak.beginRequest(0);
    banyak.onCodeSent(0);
    banyak.onVerifyFailed(7000, "too-many-attempts");
    check(banyak.canResend(7000), "terlalu banyak percobaan juga membuka kirim ulang");
}

void cooldownTetapMenunjukkanKotakKode() {
    // Server bilang "baru saja dikirim": kode sebelumnya masih hidup, jadi
    // memaksa pengguna kembali ke kotak alamat hanya membuang kode itu.
    Form form;
    form.typed = "budi@contoh.id";
    form.beginRequest(0);
    form.onRequestFailed(0, "cooldown", 45);
    check(form.step == Step::Code, "cooldown mendarat di kotak kode");
    check(form.resendInSec(0) == 45, "cooldown server dipakai apa adanya");
    check(!form.busy && form.error, "berhenti sibuk dan ditandai galat");
}

void kodeKedaluwarsaDiketahuiJamLokal() {
    Form form;
    form.typed = "budi@contoh.id";
    form.beginRequest(0);
    form.onCodeSent(0);
    check(!form.codeExpired(0), "kode baru belum hangus");
    check(!form.codeExpired(599u * 1000), "masih hidup sebelum 10 menit");
    check(form.codeExpired(600u * 1000), "hangus tepat setelah 10 menit");
}

void gantiAlamatMengembalikanKotakEmail() {
    Form form;
    form.typed = "budi@contoh.id";
    form.beginRequest(0);
    form.onCodeSent(0);
    form.backToEmail();
    check(form.step == Step::Email, "kembali ke kotak email");
    checkEqual(form.typed, "budi@contoh.id", "alamat lama diisi ulang supaya tinggal disunting");

    Form sibuk;
    sibuk.typed = "budi@contoh.id";
    sibuk.beginRequest(0);
    sibuk.backToEmail();
    check(sibuk.step == Step::Email && sibuk.busy, "tombol kembali diabaikan selagi sibuk");
}

void labelDanPetunjukMengikutiLangkah() {
    Form form;
    checkEqual(form.actionLabel(), "Kirim kode", "label langkah email");
    check(form.hint(0).find("kata sandi") != std::string::npos,
        "petunjuk email menjelaskan tanpa kata sandi");

    form.typed = "budi@contoh.id";
    form.beginRequest(0);
    checkEqual(form.actionLabel(), "Mengirim…", "label saat mengirim");
    form.onCodeSent(0);
    checkEqual(form.actionLabel(), "Masuk", "label langkah kode");
    form.message.clear();
    check(form.hint(0).find("60") != std::string::npos, "hitungan mundur kirim ulang muncul");
    check(form.hint(60u * 1000).find("Kirim ulang") != std::string::npos,
        "ajakan kirim ulang setelah cooldown");
    form.typed = "123456";
    form.beginVerify();
    checkEqual(form.actionLabel(), "Memeriksa…", "label saat memeriksa");
}

void jaringanMatiTidakMenjebakFormulir() {
    Form form;
    form.typed = "budi@contoh.id";
    form.beginRequest(0);
    form.onOffline();
    check(!form.busy, "tidak terkunci sibuk selamanya");
    check(form.error, "ditandai galat");
    check(form.canSubmitEmail() || form.step == Step::Email, "boleh mencoba lagi");
}

void tidakAdaRahasiaYangDisimpan() {
    // Formulir hanya boleh memegang apa yang diketik pengguna. Token hasil
    // verifikasi adalah urusan Credential Manager di main.cpp.
    Form form;
    form.typed = "budi@contoh.id";
    form.beginRequest(0);
    form.onCodeSent(0);
    form.typed = "123456";
    form.beginVerify();
    form.onVerified();
    check(form.code.empty() && form.typed.empty(), "digit dibuang setelah berhasil");
}

} // namespace

int main() {
    alamatDinormalisasi();
    alamatDivalidasi();
    kodeDisaring();
    badanPermintaanBerbentukJson();
    pesanGalatMenyebutLangkahBerikutnya();
    alurLengkapSampaiMasuk();
    alamatSalahTidakMengirimApaPun();
    kodeSalahMengurangiPercobaan();
    kodeHangusMembukaKirimUlangSegera();
    cooldownTetapMenunjukkanKotakKode();
    kodeKedaluwarsaDiketahuiJamLokal();
    gantiAlamatMengembalikanKotakEmail();
    labelDanPetunjukMengikutiLangkah();
    jaringanMatiTidakMenjebakFormulir();
    tidakAdaRahasiaYangDisimpan();

    if (failures != 0) {
        std::printf("%d uji masuk-email GAGAL\n", failures);
        return 1;
    }
    std::printf("Semua uji masuk-email lulus.\n");
    return 0;
}
