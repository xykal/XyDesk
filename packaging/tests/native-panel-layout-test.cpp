// Uji tata letak panel native XyDesk di Linux (tanpa Windows, tanpa GUI).
//
// Yang diuji bukan gambar, melainkan angka yang menentukan gambar itu:
// apakah semua kontrol masih berada di dalam panel, apakah tombol tidak
// bertumpuk, apakah hit-test klik mengembalikan sasaran yang benar, apakah
// kontrol bagian tersembunyi tidak bisa tertekan, dan apakah skala DPI
// bekerja. Semua ini dijalankan di CI Linux sehingga kesalahan tata letak
// ketahuan sebelum masuk ke runner Windows.
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
    check(l.window.w == 960 + 2 * 2 && l.window.h == 600 + 2 * 2, "ukuran jendela = panel + margin tepi halus");
    check(l.panel.w == 960 && l.panel.h == 600, "panel lebar 960x600 pada skala 100");
    check(l.panel.x == 2 && l.panel.y == 2, "margin tepi hanya 2 (tanpa bayangan)");
    check(l.radiusPanel == 16 && l.radiusCard == 16 && l.radiusControl == 12, "radius panel/kartu/kontrol");

    // Sidebar menempati kiri penuh dan tidak menimpa daerah isi.
    check(l.sidebar.x == l.panel.x && l.sidebar.h == l.panel.h, "sidebar setinggi panel di kiri");
    check(l.sidebar.w == 208, "lebar sidebar 208");
    check(l.content.x == l.sidebar.right() + 24, "daerah isi mulai setelah sidebar + padding");
    check(l.content.right() <= l.panel.right() - 24, "daerah isi menghormati padding kanan");

    const Rect interactive[] = {l.minimizeButton, l.maximizeButton, l.closeButton, l.navStatus, l.navControl,
        l.navHelp, l.idCopy, l.passwordCopy, l.start, l.stop, l.restart, l.web, l.openLog};
    for (const Rect& rect : interactive) {
        check(rect.valid(), "setiap kontrol punya ukuran positif");
        check(insidePanel(l, rect), "setiap kontrol berada di dalam panel");
    }

    check(insidePanel(l, l.titleBar), "area judul ada di dalam panel");
    check(insidePanel(l, l.logo) && insidePanel(l, l.title) && insidePanel(l, l.subtitle), "identitas sidebar di dalam panel");
    check(l.statusCard.y >= l.content.y, "kartu status di dalam daerah isi");
    check(l.idCard.y > l.statusCard.bottom(), "kartu identitas di bawah kartu status");
    check(l.passwordCard.y > l.idCard.bottom(), "kartu pairing di bawah kartu identitas");
    check(l.stop.x > l.start.right() && l.restart.x > l.stop.right(), "tombol aksi urut kiri ke kanan");
    check(l.openLog.x > l.web.right(), "tombol tautan urut kiri ke kanan");

    check(!overlaps(l.start, l.stop) && !overlaps(l.stop, l.restart), "tombol baris pertama tidak bertumpuk");
    check(!overlaps(l.web, l.openLog), "tombol baris kedua tidak bertumpuk");
    check(!overlaps(l.start, l.web) && !overlaps(l.restart, l.openLog), "baris tombol tidak bertumpuk antar baris");
    check(!overlaps(l.idValue, l.idCopy), "nilai Device ID tidak bertumpuk dengan tombol salin");
    check(!overlaps(l.passwordValue, l.passwordCopy), "nilai kode pairing tidak bertumpuk dengan tombol salin");
    check(!overlaps(l.sectionTitle, l.minimizeButton), "judul bagian tidak bertumpuk dengan tombol caption");
    check(!overlaps(l.navStatus, l.navControl) && !overlaps(l.navControl, l.navHelp), "navigasi tidak bertumpuk");
    check(!overlaps(l.sidebar, l.content), "sidebar dan isi tidak bertumpuk");

    check(!overlaps(l.minimizeButton, l.maximizeButton) && !overlaps(l.maximizeButton, l.closeButton), "tombol caption tidak bertumpuk");
    check(l.minimizeButton.x < l.maximizeButton.x && l.maximizeButton.x < l.closeButton.x, "urutan caption: perkecil, perbesar, tutup");
    check(l.minimizeButton.y == l.maximizeButton.y && l.maximizeButton.y == l.closeButton.y, "tombol caption sejajar satu baris");
    check(l.closeButton.right() <= l.panel.right() - 12 + 1, "tombol tutup menghormati padding kanan");
    check(l.titleBar.h >= 56, "area geser setinggi minimal 56");
}

void testSections() {
    PanelLayout status = xydesk::panel::computeLayout(96);
    status.section = xydesk::panel::kSectionStatus;
    PanelLayout control = xydesk::panel::computeLayout(96);
    control.section = xydesk::panel::kSectionControl;

    const auto center = [](const Rect& r) { return std::pair<int, int>{r.x + r.w / 2, r.y + r.h / 2}; };
    const auto [sx, sy] = center(status.start);
    check(xydesk::panel::targetAt(status, sx, sy) == Target::None, "tombol Mulai tidak tertekan di bagian Status");
    check(xydesk::panel::targetAt(control, sx, sy) == Target::Start, "tombol Mulai tertekan di bagian Kontrol");
    const auto [ix, iy] = center(status.idCopy);
    check(xydesk::panel::targetAt(status, ix, iy) == Target::CopyId, "salin ID aktif di bagian Status");
    // Di bagian Kontrol titik itu boleh jatuh ke tombol yang memang digambar
    // di sana, tetapi TIDAK BOLEH menjadi salin ID yang tak terlihat.
    check(xydesk::panel::targetAt(control, ix, iy) != Target::CopyId, "salin ID tidak tertekan di bagian Kontrol");
    check(!xydesk::panel::sectionShowsTarget(xydesk::panel::kSectionControl, Target::CopyId), "gerbang seksi menolak salin ID di Kontrol");
    // Navigasi dan caption selalu hidup di bagian mana pun.
    const auto [nx, ny] = center(status.navControl);
    check(xydesk::panel::targetAt(status, nx, ny) == Target::NavControl, "navigasi hidup di semua bagian");
    const auto [cx, cy] = center(status.closeButton);
    check(xydesk::panel::targetAt(control, cx, cy) == Target::Close, "tutup hidup di semua bagian");
}

void testHitTesting() {
    PanelLayout l = xydesk::panel::computeLayout(96);
    l.section = xydesk::panel::kSectionControl;
    const auto center = [](const Rect& r) { return std::pair<int, int>{r.x + r.w / 2, r.y + r.h / 2}; } ;

    struct Case {
        Rect rect;
        Target expected;
        const char* name;
    };
    const Case cases[] = {
        {l.minimizeButton, Target::Minimize, "tombol perkecil"},
        {l.maximizeButton, Target::Maximize, "tombol perbesar"},
        {l.closeButton, Target::Close, "tombol tutup"},
        {l.navStatus, Target::NavStatus, "navigasi status"},
        {l.navControl, Target::NavControl, "navigasi kontrol"},
        {l.navHelp, Target::NavHelp, "navigasi bantuan"},
        {l.start, Target::Start, "mulai host"},
        {l.stop, Target::Stop, "hentikan"},
        {l.restart, Target::Restart, "restart"},
        {l.web, Target::Web, "buka web"},
        {l.openLog, Target::OpenLog, "buka log"},
    };
    for (const Case& item : cases) {
        const auto [cx, cy] = center(item.rect);
        check(xydesk::panel::targetAt(l, cx, cy) == item.expected, std::string("klik tengah ") + item.name);
    }

    // Tombol caption berada DI DALAM area geser: urutan prioritas harus menang.
    const auto [closeX, closeY] = center(l.closeButton);
    check(l.titleBar.contains(closeX, closeY), "tombol tutup memang di dalam area judul");
    check(xydesk::panel::targetAt(l, closeX, closeY) == Target::Close, "tombol tutup menang atas area geser");
    const auto [minX, minY] = center(l.minimizeButton);
    const auto [maxX, maxY] = center(l.maximizeButton);
    check(l.titleBar.contains(minX, minY) && l.titleBar.contains(maxX, maxY), "tombol perkecil/perbesar di dalam area judul");
    check(xydesk::panel::targetAt(l, minX, minY) == Target::Minimize, "tombol perkecil menang atas area geser");
    check(xydesk::panel::targetAt(l, maxX, maxY) == Target::Maximize, "tombol perbesar menang atas area geser");

    check(xydesk::panel::targetAt(l, l.sidebar.x + 100, l.sidebar.bottom() - 80) == Target::None, "dasar sidebar bukan sasaran kontrol");
    check(xydesk::panel::targetAt(l, 0, 0) == Target::None, "poin di luar panel tidak menghalangi klik");
    check(xydesk::panel::targetAt(l, l.panel.x + 2, l.panel.bottom() - 2) == Target::None, "sudut bawah panel bukan sasaran kontrol");
    check(xydesk::panel::targetAt(l, l.content.x + 10, l.content.y + l.content.h - 6) == Target::None, "dasar daerah isi bukan sasaran klik");
}

void testDpiScaling() {
    const PanelLayout base = xydesk::panel::computeLayout(96);
    const PanelLayout bigger = xydesk::panel::computeLayout(144);
    check(bigger.scalePct == 150, "DPI 144 memberi skala 150");
    check(bigger.panel.w == base.panel.w * 3 / 2, "lebar panel ikut skala");
    check(bigger.panel.h == base.panel.h * 3 / 2, "tinggi panel ikut skala");
    check(bigger.radiusPanel == 24 && bigger.radiusControl == 18, "radius ikut skala");
    check(bigger.panel.x == 3, "margin tepi ikut skala");
    for (const Rect& rect : {bigger.closeButton, bigger.start, bigger.openLog, bigger.idCopy, bigger.navStatus}) {
        check(insidePanel(bigger, rect), "kontrol tetap di dalam panel pada skala 150");
    }
    PanelLayout control150 = bigger;
    control150.section = xydesk::panel::kSectionControl;
    const auto [cx, cy] = std::pair<int, int>{bigger.start.x + bigger.start.w / 2, bigger.start.y + bigger.start.h / 2};
    check(xydesk::panel::targetAt(control150, cx, cy) == Target::Start, "hit-test bekerja pada skala 150");

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

    // Sudut: titik yang tepat di luar busur tidak boleh tercakup, dan titik di
    // dalam busur harus tercakup — inilah yang membuat sudut terlihat halus,
    // bukan bergerigi seperti SetWindowRgn.
    checkNear(xydesk::panel::roundedRectCoverage(40.5f, 40.5f, rect, radius), 0.0, 0.05, "sudut persegi membulat dipotong");
    checkNear(xydesk::panel::roundedRectCoverage(64.0f, 64.0f, rect, radius), 1.0, 0.001, "titik di dalam busur tercakup");
}

void testTargetNames() {
    check(std::string(xydesk::panel::targetName(Target::Start)) == "Start", "nama sasaran dipakai di berkas probe");
    check(std::string(xydesk::panel::targetName(Target::Minimize)) == "Minimize", "tombol perkecil punya nama");
    check(std::string(xydesk::panel::targetName(Target::Maximize)) == "Maximize", "tombol perbesar punya nama");
    check(std::string(xydesk::panel::targetName(Target::NavStatus)) == "NavStatus", "navigasi status punya nama");
    check(std::string(xydesk::panel::targetName(Target::None)) == "None", "sasaran kosong punya nama");
}

} // namespace

int main() {
    testGeometry100();
    testSections();
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
