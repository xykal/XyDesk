// Uji tata letak panel native XyDesk di Linux (tanpa Windows, tanpa GUI).
//
// Yang diuji bukan gambar, melainkan angka yang menentukan gambar itu:
// apakah semua kontrol masih berada di dalam panel, apakah tombol tidak
// bertumpuk, apakah hit-test klik mengembalikan sasaran yang benar, dan
// apakah skala DPI bekerja. Semua ini dijalankan di CI Linux sehingga
// kesalahan tata letak ketahuan sebelum masuk ke runner Windows.
//
// Sejak 25 Sep panel lebar 900x560 dengan sidebar + tiga halaman dan TANPA
// bayangan — uji ikut bentuk baru itu.
//
// Bangun dan jalankan: packaging/tests/test-native-panel-layout.sh

#include "../native-host/layout.h"

#include <cmath>
#include <cstdio>
#include <string>

namespace {

int g_failures = 0;
int g_checks = 0;

void check(bool condition, const std::string& what) {
    ++g_checks;
    if (!condition) {
        ++g_failures;
        std::printf("GAGAL  %s\n", what.c_str());
    }
}

void checkNear(double actual, double expected, double tolerance, const std::string& what) {
    ++g_checks;
    if (std::fabs(actual - expected) > tolerance) {
        ++g_failures;
        std::printf("GAGAL  %s (dapat %.3f, harusnya %.3f)\n", what.c_str(), actual, expected);
    }
}

using xydesk::panel::Page;
using xydesk::panel::PanelLayout;
using xydesk::panel::Rect;
using xydesk::panel::Target;

bool insidePanel(const PanelLayout& l, const Rect& r) {
    return r.x >= l.panel.x && r.y >= l.panel.y && r.right() <= l.panel.right() && r.bottom() <= l.panel.bottom();
}

bool overlaps(const Rect& a, const Rect& b) {
    return a.x < b.right() && b.x < a.right() && a.y < b.bottom() && b.y < a.bottom();
}

void testGeometry100() {
    const PanelLayout l = xydesk::panel::computeLayout(96);
    check(l.scalePct == 100, "DPI 96 memberi skala 100");
    check(l.window.w == 900 && l.window.h == 560, "ukuran jendela 900x560 pada skala 100");
    check(l.panel.w == 900 && l.panel.h == 560, "panel sama besar dengan jendela (tanpa margin bayangan)");
    check(l.panel.x == 0 && l.panel.y == 0, "tanpa margin bayangan: panel di (0,0)");
    check(l.radiusPanel == 16 && l.radiusCard == 16 && l.radiusControl == 12, "radius panel/kartu/kontrol");

    const Rect interactive[] = {l.minimizeButton, l.maximizeButton, l.closeButton,
        l.sideStatus, l.sidePairing, l.sideControl,
        l.idCopy, l.passwordCopy, l.start, l.stop, l.restart, l.web, l.openLog};
    for (const Rect& rect : interactive) {
        check(rect.valid(), "setiap kontrol punya ukuran positif");
        check(insidePanel(l, rect), "setiap kontrol berada di dalam panel");
    }

    check(insidePanel(l, l.titleBar) && insidePanel(l, l.hint), "area judul dan petunjuk ada di dalam panel");
    check(insidePanel(l, l.statusCard) && insidePanel(l, l.captureCard), "kartu status & capture di dalam panel");
    check(insidePanel(l, l.idCard) && insidePanel(l, l.passwordCard), "kartu pairing di dalam panel");

    // Sidebar: tiga item sejajar menurun, tidak bertumpuk, di kiri konten.
    check(l.sideStatus.y < l.sidePairing.y && l.sidePairing.y < l.sideControl.y, "sidebar menurun");
    check(!overlaps(l.sideStatus, l.sidePairing) && !overlaps(l.sidePairing, l.sideControl), "sidebar tidak bertumpuk");
    check(l.sideStatus.x == l.sidePairing.x && l.sidePairing.x == l.sideControl.x, "sidebar satu kolom");
    check(l.sideStatus.right() <= l.statusCard.x && l.sideStatus.right() <= l.idCard.x
        && l.sideStatus.right() <= l.start.x, "sidebar tidak menabrak konten");
    check(l.sideStatusIcon.valid() && l.sidePairingIcon.valid() && l.sideControlIcon.valid(), "ikon sidebar punya ukuran");
    for (const Rect& icon : {l.sideStatusIcon, l.sidePairingIcon, l.sideControlIcon}) {
        check(insidePanel(l, icon), "ikon sidebar di dalam panel");
    }

    // Caption: tiga tombol sejajar, urutan Windows, judul tidak menabrak.
    check(!overlaps(l.minimizeButton, l.maximizeButton) && !overlaps(l.maximizeButton, l.closeButton), "tombol caption tidak bertumpuk");
    check(l.minimizeButton.x < l.maximizeButton.x && l.maximizeButton.x < l.closeButton.x, "urutan caption: perkecil, perbesar, tutup");
    check(l.minimizeButton.y == l.maximizeButton.y && l.maximizeButton.y == l.closeButton.y, "tombol caption sejajar satu baris");
    check(!overlaps(l.title, l.minimizeButton) && !overlaps(l.subtitle, l.minimizeButton), "judul tidak bertumpuk dengan tombol caption");
    check(l.closeButton.right() <= l.panel.right() - 20 + 1, "tombol tutup menghormati padding kanan");

    // Konten per halaman tidak saling menumpuk di halamannya.
    check(!overlaps(l.statusCard, l.captureCard), "kartu status dan capture tidak bertumpuk");
    check(l.captureCard.y > l.statusCard.bottom(), "kartu capture di bawah kartu status");
    check(!overlaps(l.idCard, l.passwordCard), "kartu pairing tidak bertumpuk");
    check(l.passwordCard.y > l.idCard.bottom(), "kartu pairing di bawah kartu ID");
    check(!overlaps(l.start, l.stop) && !overlaps(l.stop, l.restart), "tombol baris pertama tidak bertumpuk");
    check(!overlaps(l.web, l.openLog), "tombol baris kedua tidak bertumpuk");
    check(l.web.y > l.start.bottom(), "baris tombol tidak bertumpuk antar baris");
    check(l.hint.y > l.captureCard.bottom() && l.hint.y > l.passwordCard.bottom() && l.hint.y > l.openLog.bottom(),
        "petunjuk di bawah semua konten");
    check(l.hint.bottom() <= l.panel.bottom(), "petunjuk tidak keluar panel");
}

void testHitTesting() {
    const PanelLayout l = xydesk::panel::computeLayout(96);
    const auto center = [](const Rect& r) { return std::pair<int, int>{r.x + r.w / 2, r.y + r.h / 2}; };

    // Caption dan sidebar berlaku di semua halaman.
    struct Case {
        Rect rect;
        Target expected;
        const char* name;
    };
    const Case common[] = {
        {l.minimizeButton, Target::Minimize, "tombol perkecil"},
        {l.maximizeButton, Target::Maximize, "tombol perbesar"},
        {l.closeButton, Target::Close, "tombol tutup"},
        {l.sideStatus, Target::PageStatus, "sidebar status"},
        {l.sidePairing, Target::PagePairing, "sidebar pairing"},
        {l.sideControl, Target::PageControl, "sidebar kontrol"},
    };
    for (const Page page : {Page::Status, Page::Pairing, Page::Control}) {
        for (const Case& item : common) {
            const auto [cx, cy] = center(item.rect);
            check(xydesk::panel::targetAt(l, page, cx, cy) == item.expected,
                std::string("klik tengah ") + item.name + " di halaman " + xydesk::panel::pageName(page));
        }
    }

    // Sasaran konten hanya hidup di halamannya.
    const auto [startX, startY] = center(l.start);
    check(xydesk::panel::targetAt(l, Page::Control, startX, startY) == Target::Start, "tombol mulai hidup di halaman kontrol");
    check(xydesk::panel::targetAt(l, Page::Status, startX, startY) != Target::Start, "tombol mulai mati di halaman status");
    const auto [copyX, copyY] = center(l.idCopy);
    check(xydesk::panel::targetAt(l, Page::Pairing, copyX, copyY) == Target::CopyId, "salin ID hidup di halaman pairing");
    check(xydesk::panel::targetAt(l, Page::Control, copyX, copyY) != Target::CopyId, "salin ID mati di halaman kontrol");

    // Tombol caption berada DI DALAM area geser: prioritas harus menang.
    const auto [closeX, closeY] = center(l.closeButton);
    check(l.titleBar.contains(closeX, closeY), "tombol tutup memang di dalam area judul");
    check(xydesk::panel::targetAt(l, Page::Status, closeX, closeY) == Target::Close, "tombol tutup menang atas area geser");

    check(xydesk::panel::targetAt(l, Page::Status, l.panel.x + 400, l.panel.y + 6) == Target::TitleBar, "sisa area judul bisa dipakai menggeser");
    check(xydesk::panel::targetAt(l, Page::Status, l.panel.x + 2, l.panel.bottom() - 2) == Target::None, "sudut bawah panel bukan sasaran kontrol");
    check(xydesk::panel::targetAt(l, Page::Status, l.statusCard.x + 10, l.statusCard.y + 10) == Target::None, "kartu status bukan sasaran klik");
}

void testDpiScaling() {
    const PanelLayout base = xydesk::panel::computeLayout(96);
    const PanelLayout bigger = xydesk::panel::computeLayout(144);
    check(bigger.scalePct == 150, "DPI 144 memberi skala 150");
    check(bigger.panel.w == base.panel.w * 3 / 2, "lebar panel ikut skala");
    check(bigger.panel.h == base.panel.h * 3 / 2, "tinggi panel ikut skala");
    check(bigger.radiusPanel == 24 && bigger.radiusControl == 18, "radius ikut skala");
    for (const Rect& rect : {bigger.closeButton, bigger.start, bigger.sidePairing, bigger.idCopy}) {
        check(insidePanel(bigger, rect), "kontrol tetap di dalam panel pada skala 150");
    }
    const auto [cx, cy] = std::pair<int, int>{bigger.sidePairing.x + bigger.sidePairing.w / 2, bigger.sidePairing.y + bigger.sidePairing.h / 2};
    check(xydesk::panel::targetAt(bigger, Page::Pairing, cx, cy) == Target::PagePairing, "hit-test sidebar bekerja pada skala 150");

    const PanelLayout tiny = xydesk::panel::computeLayout(0);
    check(tiny.scalePct == 100, "DPI tidak valid kembali ke 100");
    const PanelLayout huge = xydesk::panel::computeLayout(960);
    check(huge.scalePct <= 400, "skala dibatasi agar panel tidak keluar layar");
}

void testRoundingMath() {
    const Rect rect{40, 40, 200, 100};
    const float radius = 16.0f;

    // Konvensi: `roundedRectCoverage` menerima titik tengah piksel. Tepi
    // bentuk ada di d = 0, jadi titik tengah yang tepat jatuh di tepi lurus
    // mendapat setengah cakupan, sedangkan piksel penuh di dalam tepi (0,5
    // piksel dari batas, seperti 40.5 dari tepi y=40) sudah tercakup penuh.
    checkNear(xydesk::panel::roundedRectCoverage(140.0f, 90.0f, rect, radius), 1.0, 0.001, "titik tengah tercakup penuh");
    checkNear(xydesk::panel::roundedRectCoverage(140.0f, 39.0f, rect, radius), 0.0, 0.001, "di luar bentuk tidak tercakup");
    checkNear(xydesk::panel::roundedRectCoverage(140.0f, 40.0f, rect, radius), 0.5, 0.02, "titik tengah di tepi atas: setengah cakupan");
    checkNear(xydesk::panel::roundedRectCoverage(240.0f, 90.0f, rect, radius), 0.5, 0.02, "titik tengah di tepi kanan: setengah cakupan");
    checkNear(xydesk::panel::roundedRectCoverage(140.0f, 40.5f, rect, radius), 1.0, 0.001, "piksel pertama di dalam tepi tercakup penuh");
    checkNear(xydesk::panel::roundedRectCoverage(240.5f, 90.0f, rect, radius), 0.0, 0.001, "piksel pertama di luar tepi tidak tercakup");

    // Sudut: titik yang tepat di luar busur tidak boleh tercakup, dan titik
    // di dalam busur harus tercakup — inilah yang membuat sudut terlihat
    // halus, bukan bergerigi seperti SetWindowRgn.
    checkNear(xydesk::panel::roundedRectCoverage(40.5f, 40.5f, rect, radius), 0.0, 0.05, "sudut persegi membulat dipotong");
    checkNear(xydesk::panel::roundedRectCoverage(64.0f, 64.0f, rect, radius), 1.0, 0.001, "titik di dalam busur tercakup");

    // Bayangan dimatikan (spread 0) sejak panel tanpa bayangan.
    checkNear(xydesk::panel::roundedRectShadow(140.0f, 39.0f, rect, radius, 0.0f, 0.478f), 0.0, 0.001, "spread 0 = tanpa bayangan");

    const float edge = xydesk::panel::roundedRectShadow(140.0f, 39.0f, rect, radius, 26.0f, 0.478f);
    const float farther = xydesk::panel::roundedRectShadow(140.0f, 25.0f, rect, radius, 26.0f, 0.478f);
    const float far = xydesk::panel::roundedRectShadow(140.0f, 5.0f, rect, radius, 26.0f, 0.478f);
    checkNear(xydesk::panel::roundedRectShadow(140.0f, 90.0f, rect, radius, 26.0f, 0.478f), 0.0, 0.001, "di dalam bentuk tidak ada bayangan");
    check(edge > farther && farther > far, "bayangan memudar menjauh dari tepi");
    checkNear(far, 0.0, 0.01, "bayangan habis di luar sebaran");
    check(edge <= 0.478f + 0.001f, "bayangan tidak melebihi kekuatan yang diminta");
}

void testTargetNames() {
    check(std::string(xydesk::panel::targetName(Target::Start)) == "Start", "nama sasaran dipakai di berkas probe");
    check(std::string(xydesk::panel::targetName(Target::Minimize)) == "Minimize", "tombol perkecil punya nama");
    check(std::string(xydesk::panel::targetName(Target::Maximize)) == "Maximize", "tombol perbesar punya nama");
    check(std::string(xydesk::panel::targetName(Target::PageStatus)) == "PageStatus", "sidebar status punya nama");
    check(std::string(xydesk::panel::targetName(Target::PagePairing)) == "PagePairing", "sidebar pairing punya nama");
    check(std::string(xydesk::panel::targetName(Target::PageControl)) == "PageControl", "sidebar kontrol punya nama");
    check(std::string(xydesk::panel::targetName(Target::None)) == "None", "sasaran kosong punya nama");
}

} // namespace

int main() {
    testGeometry100();
    testHitTesting();
    testDpiScaling();
    testRoundingMath();
    testTargetNames();

    if (g_failures == 0) {
        std::printf("Lulus: %d pemeriksaan tata letak panel native.\n", g_checks);
        return 0;
    }
    std::printf("GAGAL: %d dari %d pemeriksaan.\n", g_failures, g_checks);
    return 1;
}
