// Uji gerbang masuk panel host: aturan tahap, kurva gerak, dan geometri kartu.
// Murni angka — tidak butuh Windows, dijalankan CI Linux lewat
// packaging/tests/test-native-panel-layout.sh.
#include "gate_layout.h"
#include "onboarding.h"

#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <string>

namespace {

int failures = 0;

void check(bool condition, const std::string& what) {
    if (condition) return;
    std::printf("GAGAL: %s\n", what.c_str());
    ++failures;
}

void checkNear(float got, float want, float tolerance, const std::string& what) {
    check(std::fabs(got - want) <= tolerance,
        what + " (dapat " + std::to_string(got) + ", mau " + std::to_string(want) + ")");
}

using xydesk::onboarding::Gate;
using xydesk::onboarding::Method;
using xydesk::onboarding::LoginState;
using xydesk::onboarding::Stage;

// ── Aturan tahap ────────────────────────────────────────────────────────────

void pemasanganBaruMelihatSambutan() {
    Gate gate;
    gate.start(/*hasSession=*/false, /*welcomeSeen=*/false);
    check(gate.stage == Stage::Welcome, "pemasangan baru mulai di sambutan");
    check(!gate.panelVisible(), "panel tidak boleh terlihat sebelum masuk");

    gate.beginLogin();
    check(gate.stage == Stage::Login, "tombol Mulai membawa ke layar masuk");
    check(gate.sawWelcome, "sambutan ditandai sudah dilihat");
    check(!gate.panelVisible(), "layar masuk belum membuka panel");

    gate.attempt(Method::Google);
    check(gate.busy(), "menunggu browser = sibuk, tombol dikunci");
    check(!gate.panelVisible(), "menunggu bukan berarti masuk");

    gate.succeed("budi@xyverse.id");
    check(gate.stage == Stage::Ready, "berhasil masuk membuka panel");
    check(gate.panelVisible(), "panel terlihat setelah masuk");
    check(!gate.busy(), "tidak sibuk lagi setelah berhasil");
    check(gate.email == "budi@xyverse.id", "email akun tercatat");
}

void sesiTersimpanLangsungMasuk() {
    Gate gate;
    gate.start(/*hasSession=*/true, /*welcomeSeen=*/false, "lama@xyverse.id");
    check(gate.stage == Stage::Ready, "sesi sah langsung ke panel");
    check(gate.panelVisible(), "tidak ada layar masuk yang mengganggu");
    check(gate.sawWelcome, "sesi sah berarti sambutan tidak relevan lagi");
    check(gate.email == "lama@xyverse.id", "email dari sesi tersimpan dipakai");
}

void sambutanTidakDiulang() {
    Gate gate;
    gate.start(/*hasSession=*/false, /*welcomeSeen=*/true);
    check(gate.stage == Stage::Login, "yang pernah melihat sambutan langsung masuk");

    // Dan tombol Mulai tidak punya efek di luar sambutan.
    const Stage before = gate.stage;
    gate.beginLogin();
    check(gate.stage == before, "beginLogin diabaikan di luar sambutan");
}

void keluarAkunKembaliKeLayarMasuk() {
    Gate gate;
    gate.start(true, true, "budi@xyverse.id");
    gate.signOut();
    check(gate.stage == Stage::Login, "keluar akun kembali ke layar masuk");
    check(!gate.panelVisible(), "panel ditutup setelah keluar akun");
    check(gate.email.empty(), "email dihapus dari state UI");
    check(gate.sawWelcome, "keluar akun tidak mengulang sambutan");
}

void gagalMasukBisaDiulang() {
    Gate gate;
    gate.start(false, true);
    gate.attempt(Method::Google);
    gate.fail("Jaringan tidak tersedia.");
    check(gate.stage == Stage::Login, "gagal tetap di layar masuk");
    check(gate.login == LoginState::Failed, "status gagal tercatat");
    check(!gate.busy(), "gagal melepas kunci tombol");
    check(gate.message == "Jaringan tidak tersedia.", "pesan gagal apa adanya");
    check(!gate.panelVisible(), "gagal tidak pernah membuka panel");

    gate.fail("");
    check(!gate.message.empty(), "pesan kosong diganti kalimat yang bisa dibaca");

    gate.attempt(Method::Email);
    check(gate.busy() && gate.message.empty(), "mencoba lagi membersihkan pesan");
    check(gate.method == Method::Email, "cara masuk terakhir diingat");

    gate.succeed("budi@xyverse.id");
    check(gate.panelVisible(), "percobaan kedua yang berhasil tetap membuka panel");
}

void batalHanyaSaatMenunggu() {
    Gate gate;
    gate.start(false, true);
    gate.cancel();
    check(gate.login == LoginState::Idle, "batal saat diam tidak merusak apa pun");

    gate.attempt(Method::Google);
    gate.cancel();
    check(!gate.busy(), "batal melepas status menunggu");
    check(gate.stage == Stage::Login, "batal tidak memindahkan tahap");

    // Batal setelah gagal tidak boleh menghapus pesan kegagalan.
    gate.fail("Token ditolak.");
    gate.cancel();
    check(gate.message == "Token ditolak.", "batal tidak menelan pesan gagal");
}

void panelTidakPernahBocorSebelumMasuk() {
    // Invarian paling penting dari PR ini: satu-satunya jalan ke Ready adalah
    // succeed() atau start() dengan sesi sah.
    Gate gate;
    gate.start(false, false);
    const Method methods[] = {Method::Google, Method::Email};
    for (int round = 0; round < 3; ++round) {
        for (Method method : methods) {
            gate.beginLogin();
            check(!gate.panelVisible(), "beginLogin tidak membuka panel");
            gate.attempt(method);
            check(!gate.panelVisible(), "attempt tidak membuka panel");
            gate.cancel();
            check(!gate.panelVisible(), "cancel tidak membuka panel");
            gate.fail("gagal");
            check(!gate.panelVisible(), "fail tidak membuka panel");
            gate.signOut();
            check(!gate.panelVisible(), "signOut tidak membuka panel");
        }
    }
}

// ── Bahasa gerak ────────────────────────────────────────────────────────────

void kurvaMulaiDiNolBerakhirDiSatu() {
    using namespace xydesk::onboarding;
    const struct { const char* name; float (*fn)(float); } curves[] = {
        {"easeOutExpo", easeOutExpo}, {"easeOutBack", easeOutBack}, {"spring", spring},
    };
    for (const auto& curve : curves) {
        checkNear(curve.fn(0.0f), 0.0f, 0.001f, std::string(curve.name) + " mulai di 0");
        checkNear(curve.fn(1.0f), 1.0f, 0.001f, std::string(curve.name) + " berakhir di 1");
        // Di luar rentang tetap terjepit — animasi tidak boleh meledak.
        checkNear(curve.fn(-1.0f), 0.0f, 0.001f, std::string(curve.name) + " menjepit t negatif");
        checkNear(curve.fn(2.0f), 1.0f, 0.001f, std::string(curve.name) + " menjepit t > 1");
    }
}

void kurvaTerasaCepatDiAwal() {
    using namespace xydesk::onboarding;
    // Rasa "macOS" datang dari separuh perjalanan diselesaikan jauh sebelum
    // separuh waktu. Kalau ini tidak benar, gerakannya terasa malas.
    check(easeOutExpo(0.25f) > 0.75f, "easeOutExpo sudah lewat 75% di seperempat waktu");
    check(spring(0.3f) > 0.85f, "spring mendarat cepat");
    check(spring(0.5f) > 0.98f, "spring praktis diam di separuh waktu");
    // easeOutBack boleh melewati 1 lalu kembali — itu memang maksudnya.
    bool overshoots = false;
    for (float t = 0.0f; t <= 1.0f; t += 0.01f) {
        if (easeOutBack(t) > 1.0f) overshoots = true;
    }
    check(overshoots, "easeOutBack memang sedikit melewati target");
    // Tapi tidak liar: overshoot di bawah 15%.
    for (float t = 0.0f; t <= 1.0f; t += 0.01f) {
        check(easeOutBack(t) <= 1.15f, "overshoot easeOutBack tetap sopan");
    }
}

void kurvaMenanjakTanpaLompatan() {
    using namespace xydesk::onboarding;
    float previous = easeOutExpo(0.0f);
    for (float t = 0.01f; t <= 1.0f; t += 0.01f) {
        const float value = easeOutExpo(t);
        check(value >= previous - 0.0001f, "easeOutExpo tidak pernah mundur");
        previous = value;
    }
}

void jamAnimasiBerbasisWaktuBukanFrame() {
    using namespace xydesk::onboarding;
    Clock clock;
    clock.begin(1000, 320);
    checkNear(clock.progress(1000), 0.0f, 0.001f, "mulai di 0");
    checkNear(clock.progress(1160), 0.5f, 0.01f, "separuh durasi = separuh kemajuan");
    check(clock.running, "masih berjalan di tengah");
    checkNear(clock.progress(1320), 1.0f, 0.001f, "selesai tepat di akhir durasi");
    check(!clock.running, "jam menutup dirinya sendiri");
    checkNear(clock.progress(9999), 1.0f, 0.001f, "setelah berhenti tetap 1");

    // Frame yang datang terlambat (sistem sibuk) tidak memperpanjang animasi.
    Clock late;
    late.begin(0, 320);
    checkNear(late.progress(5000), 1.0f, 0.001f, "frame telat langsung mendarat, bukan melambat");

    // Durasi nol tidak membagi dengan nol.
    Clock instant;
    instant.begin(0, 0);
    checkNear(instant.progress(1), 1.0f, 0.001f, "durasi nol langsung selesai");
}

void penundaanBerurutanMenyusunLayar() {
    using namespace xydesk::onboarding;
    // Elemen pertama mulai segera, elemen terakhir selesai bersama animasi.
    checkNear(staggered(0.0f, 0, 5), 0.0f, 0.001f, "elemen pertama mulai di awal");
    checkNear(staggered(1.0f, 4, 5), 1.0f, 0.001f, "elemen terakhir selesai di akhir");
    check(staggered(0.3f, 0, 5) > staggered(0.3f, 4, 5), "elemen awal mendahului elemen akhir");
    // Elemen belakang masih diam saat elemen depan sudah bergerak.
    checkNear(staggered(0.05f, 4, 5), 0.0f, 0.001f, "elemen terakhir menunggu gilirannya");
    // Satu elemen = tanpa penundaan sama sekali.
    checkNear(staggered(0.4f, 0, 1), 0.4f, 0.001f, "elemen tunggal tidak ditunda");
    // Index di luar rentang tidak merusak apa pun.
    checkNear(staggered(1.0f, 99, 5), 1.0f, 0.001f, "index liar dijepit");
}

// ── Geometri kartu ──────────────────────────────────────────────────────────

void kartuGerbangSelaluDiDalamPanel() {
    using xydesk::panel::computeGateLayout;
    using xydesk::panel::Rect;
    const Rect panels[] = {
        Rect{0, 0, 1100, 720},  // ukuran baku
        Rect{0, 0, 900, 640},   // sekecil-kecilnya panel
        Rect{0, 0, 1920, 1080}, // dimaksimalkan
        Rect{40, 24, 980, 700}, // panel dengan offset
    };
    for (const Rect& panel : panels) {
        for (int scale : {75, 100, 125, 150, 200}) {
            for (bool withSecondary : {false, true}) {
                const auto g = computeGateLayout(panel, scale, withSecondary);
                const std::string tag = "panel " + std::to_string(panel.w) + "x"
                    + std::to_string(panel.h) + " @" + std::to_string(scale) + "%";
                check(g.card.x >= panel.x, tag + ": kartu tidak keluar kiri");
                check(g.card.right() <= panel.right(), tag + ": kartu tidak keluar kanan");
                check(g.card.y >= panel.y, tag + ": kartu tidak keluar atas");
                check(g.card.w > 0 && g.card.h > 0, tag + ": kartu punya ukuran");
                // Tengah secara horizontal, dalam toleransi pembulatan 1 px.
                const int cardCenter = g.card.x + g.card.w / 2;
                const int panelCenter = panel.x + panel.w / 2;
                check(std::abs(cardCenter - panelCenter) <= 1, tag + ": kartu di tengah");
            }
        }
    }
}

void isiKartuBerurutanDanTidakSalingTimpa() {
    using xydesk::panel::computeGateLayout;
    using xydesk::panel::Rect;
    for (int scale : {75, 100, 150, 200}) {
        for (bool withSecondary : {false, true}) {
            const auto g = computeGateLayout(Rect{0, 0, 1100, 720}, scale, withSecondary);
            const std::string tag = "@" + std::to_string(scale) + "%"
                + (withSecondary ? " (masuk)" : " (sambutan)");
            check(g.mark.bottom() <= g.title.y, tag + ": logo di atas judul");
            check(g.title.bottom() <= g.subtitle.y, tag + ": judul di atas penjelas");
            check(g.subtitle.bottom() <= g.primary.y, tag + ": penjelas di atas tombol utama");
            if (withSecondary) {
                check(g.primary.bottom() <= g.secondary.y, tag + ": tombol kedua di bawah");
                check(g.secondary.h > 0, tag + ": tombol kedua punya tinggi");
                check(g.secondary.bottom() <= g.note.y, tag + ": catatan di bawah tombol");
            } else {
                check(g.secondary.h == 0, tag + ": sambutan tidak punya tombol kedua");
                check(g.primary.bottom() <= g.note.y, tag + ": catatan di bawah tombol utama");
            }
            check(g.note.bottom() <= g.dots.y, tag + ": titik langkah paling bawah");
            check(g.dots.bottom() <= g.card.bottom(), tag + ": semua isi muat di kartu");
            // Isi tidak menempel dinding kartu.
            check(g.title.x > g.card.x, tag + ": ada padding kiri");
            check(g.title.right() < g.card.right(), tag + ": ada padding kanan");
            // Tombol cukup besar untuk disentuh/diklik dengan nyaman.
            check(g.primary.h >= 32, tag + ": tombol utama tidak kekecilan");
        }
    }
}

void kartuMenyempitTapiTidakHilangDiPanelSempit() {
    using xydesk::panel::computeGateLayout;
    using xydesk::panel::Rect;
    // Panel 200% di lebar minimum: kartu harus mengecil, bukan meluber.
    const auto g = computeGateLayout(Rect{0, 0, 900, 640}, 200, true);
    check(g.card.w <= 900, "kartu tidak melebihi panel sempit");
    check(g.card.w >= 280, "kartu tetap cukup lebar untuk dibaca");
    check(g.card.x >= 0, "kartu tidak negatif");
}

void tombolKeluarSelaluDiPojokKartu() {
    using xydesk::panel::computeGateLayout;
    using xydesk::panel::Rect;
    for (int scale : {100, 150}) {
        const auto g = computeGateLayout(Rect{0, 0, 1100, 720}, scale, true);
        check(g.quit.right() <= g.card.right(), "tombol keluar di dalam kartu");
        check(g.quit.y >= g.card.y, "tombol keluar tidak di atas kartu");
        check(g.quit.w > 0 && g.quit.h > 0, "tombol keluar bisa diklik");
    }
}

void kartuFormulirEmailPunyaKotakIsian() {
    using xydesk::panel::computeGateLayout;
    using xydesk::panel::Rect;
    for (int scale : {100, 125, 150, 200}) {
        const auto polos = computeGateLayout(Rect{0, 0, 1100, 720}, scale, true, false);
        const auto form = computeGateLayout(Rect{0, 0, 1100, 720}, scale, true, true);
        check(polos.field.h == 0, "tanpa formulir tidak ada kotak isian");
        check(form.field.h > 0, "formulir email punya kotak isian");
        check(form.field.h == form.primary.h, "kotak isian setinggi tombol utama");
        check(form.field.bottom() <= form.primary.y, "kotak isian di atas tombol utama");
        check(form.field.x == form.primary.x && form.field.w == form.primary.w,
            "kotak isian selebar tombol");
        check(form.field.y >= form.subtitle.bottom(), "kotak isian di bawah subjudul");
        check(form.card.h > polos.card.h, "kartu tumbuh untuk menampung kotak isian");
        check(form.primary.bottom() <= form.secondary.y, "tombol utama tetap di atas tombol kedua");
        check(form.secondary.bottom() <= form.note.y, "catatan tetap paling bawah");
        check(form.card.bottom() <= 720 || form.card.y >= 0,
            "kartu formulir tetap masuk akal di panel");
        check(form.field.y > form.card.y && form.field.bottom() < form.card.bottom(),
            "kotak isian di dalam kartu");
    }
}

} // namespace

int main() {
    pemasanganBaruMelihatSambutan();
    sesiTersimpanLangsungMasuk();
    sambutanTidakDiulang();
    keluarAkunKembaliKeLayarMasuk();
    gagalMasukBisaDiulang();
    batalHanyaSaatMenunggu();
    panelTidakPernahBocorSebelumMasuk();

    kurvaMulaiDiNolBerakhirDiSatu();
    kurvaTerasaCepatDiAwal();
    kurvaMenanjakTanpaLompatan();
    jamAnimasiBerbasisWaktuBukanFrame();
    penundaanBerurutanMenyusunLayar();

    kartuGerbangSelaluDiDalamPanel();
    isiKartuBerurutanDanTidakSalingTimpa();
    kartuMenyempitTapiTidakHilangDiPanelSempit();
    tombolKeluarSelaluDiPojokKartu();
    kartuFormulirEmailPunyaKotakIsian();

    if (failures) {
        std::printf("%d pemeriksaan gagal\n", failures);
        return EXIT_FAILURE;
    }
    std::printf("Gerbang onboarding host: semua pemeriksaan lulus.\n");
    return EXIT_SUCCESS;
}
