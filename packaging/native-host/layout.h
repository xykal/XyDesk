#pragma once
// Tata letak dan geometri panel XyDesk (v2: lebar, bersidebar, tanpa bayangan).
//
// File ini sengaja murni angka: tidak menyentuh Windows API, tidak menyentuh
// GDI. Dua alasannya nyata. Pertama, tata letak bisa diuji di Linux
// (packaging/tests/native-panel-layout-test.cpp) sehingga ukuran tombol,
// hit-test klik, dan pembulatan sudut tidak bergantung pada mata manusia.
// Kedua, main.cpp memakai file yang sama apa adanya, jadi angka di sini dan
// angka yang digambar tidak bisa melenceng satu sama lain.
//
// Semua ukuran di bawah ditulis untuk 96 DPI (skala 100) dan dikalikan
// `scalePct` saat dipakai, supaya di layar 125%/150% tata letaknya tetap utuh.
//
// v2 (25 Sep, umpan balik pemilik): panel lebar 960x600 dengan sidebar navigasi
// (Status / Kontrol / Bantuan), tanpa bayangan luar — tepi hanya dihaluskan
// satu-dua piksel. Kontrol baku memakai ikon; teks hanya untuk nilai yang
// memang milik pengguna (Device ID, kode pairing, judul bagian).

#include <algorithm>
#include <cmath>

namespace xydesk::panel {

// Sudut membulat: radius 16 untuk permukaan dan kartu, 12 untuk kontrol,
// mengikuti skala radius desain XyDesk (8/12/16/20, lihat docs/DESIGN.md).
constexpr int kRadiusPanel = 16;
constexpr int kRadiusCard = 16;
constexpr int kRadiusControl = 12;

// v1 punya bayangan luar 24px; pemilik minta tanpa bayangan. Margin kecil ini
// sekarang hanya memberi ruang tepi yang dihaluskan (antialias) supaya sudut
// tidak bergerigi di atas wallpaper apa pun.
constexpr int kEdgeMargin = 2;

constexpr int kPanelWidth = 960;
constexpr int kPanelHeight = 600;
constexpr int kPadding = 24;
constexpr int kGap = 12;
constexpr int kSidebarWidth = 208;
constexpr int kCaptionHeight = 56;

// Bagian konten yang dipilih dari sidebar. Nilai ini ikut di PanelLayout
// supaya hit-test dan urutan Tab tidak pernah menampilkan kontrol yang
// sedang tidak terlihat di layar.
constexpr int kSectionStatus = 0;
constexpr int kSectionControl = 1;
constexpr int kSectionHelp = 2;

struct Rect {
    int x = 0;
    int y = 0;
    int w = 0;
    int h = 0;

    [[nodiscard]] int right() const { return x + w; }
    [[nodiscard]] int bottom() const { return y + h; }
    [[nodiscard]] bool contains(int px, int py) const {
        return px >= x && py >= y && px < right() && py < bottom();
    }
    [[nodiscard]] bool valid() const { return w > 0 && h > 0; }
    [[nodiscard]] Rect inset(int amount) const {
        return Rect{x + amount, y + amount, w - 2 * amount, h - 2 * amount};
    }
};

// Sasaran klik. Satu daftar untuk tata letak, hit-test mouse, dan urutan Tab
// supaya ketiganya tidak pernah berbeda pendapat.
enum class Target {
    None,
    TitleBar,
    Minimize,
    Maximize,
    Close,
    NavStatus,
    NavControl,
    NavHelp,
    CopyId,
    CopyPassword,
    Start,
    Stop,
    Restart,
    Web,
    OpenLog,
};

inline const char* targetName(Target target) {
    switch (target) {
    case Target::TitleBar: return "TitleBar";
    case Target::Minimize: return "Minimize";
    case Target::Maximize: return "Maximize";
    case Target::Close: return "Close";
    case Target::NavStatus: return "NavStatus";
    case Target::NavControl: return "NavControl";
    case Target::NavHelp: return "NavHelp";
    case Target::CopyId: return "CopyId";
    case Target::CopyPassword: return "CopyPassword";
    case Target::Start: return "Start";
    case Target::Stop: return "Stop";
    case Target::Restart: return "Restart";
    case Target::Web: return "Web";
    case Target::OpenLog: return "OpenLog";
    default: return "None";
    }
}

struct PanelLayout {
    int scalePct = 100;
    int section = kSectionStatus;
    int radiusPanel = kRadiusPanel;
    int radiusCard = kRadiusCard;
    int radiusControl = kRadiusControl;

    Rect window{};   // seluruh permukaan berlapis, termasuk ruang tepi halus
    Rect panel{};    // permukaan membulat yang terlihat

    Rect sidebar{};      // kolom navigasi kiri
    Rect logo{};
    Rect title{};        // nama aplikasi di sidebar
    Rect subtitle{};     // keterangan kecil di bawah nama
    Rect navStatus{};
    Rect navControl{};
    Rect navHelp{};
    Rect sidebarFoot{};  // versi + catatan kecil di dasar sidebar

    Rect titleBar{};     // strip atas: area geser + judul bagian + caption
    Rect sectionTitle{};
    Rect minimizeButton{};
    Rect maximizeButton{};
    Rect closeButton{};

    Rect content{};      // daerah isi di kanan sidebar, di bawah caption

    Rect statusCard{};
    Rect statusDot{};
    Rect statusLine1{};
    Rect statusLine2{};

    Rect idCard{};
    Rect idLabel{};
    Rect idValue{};
    Rect idCopy{};

    Rect passwordCard{};
    Rect passwordLabel{};
    Rect passwordValue{};
    Rect passwordCopy{};

    Rect start{};
    Rect stop{};
    Rect restart{};
    Rect web{};
    Rect openLog{};
    Rect logLine{};
    Rect hint{};
};

// Pembantu min/max/clamp sendiri, bukan std::, karena windows.h mendefinisikan
// makro min/max yang mematahkan std::min/std::max di MSVC (error C2589).
// Berkas ini dipakai bersama EXE dan uji, jadi ia tidak boleh bergantung pada
// urutan include pemakainya.
template <typename T>
constexpr T minValue(T a, T b) { return a < b ? a : b; }

template <typename T>
constexpr T maxValue(T a, T b) { return a > b ? a : b; }

template <typename T>
constexpr T clampValue(T value, T low, T high) {
    return value < low ? low : (value > high ? high : value);
}

inline int scaled(int value, int scalePct) {
    return (value * scalePct + 50) / 100;
}

// `dpi` datang dari GetDpiForWindow; 96 apa adanya, 120 = 125%, 144 = 150%.
inline int scalePctFromDpi(int dpi) {
    if (dpi <= 0) return 100;
    return clampValue((dpi * 100 + 48) / 96, 75, 400);
}

inline PanelLayout computeLayout(int dpi) {
    const int s = scalePctFromDpi(dpi);
    const auto px = [s](int value) { return scaled(value, s); };

    PanelLayout l;
    l.scalePct = s;
    l.radiusPanel = px(kRadiusPanel);
    l.radiusCard = px(kRadiusCard);
    l.radiusControl = px(kRadiusControl);

    const int margin = px(kEdgeMargin);
    const int panelW = px(kPanelWidth);
    const int panelH = px(kPanelHeight);
    l.panel = Rect{margin, margin, panelW, panelH};
    l.window = Rect{0, 0, panelW + 2 * margin, panelH + 2 * margin};

    const int pad = px(kPadding);
    const int gap = px(kGap);

    // ── Sidebar kiri: identitas aplikasi + navigasi ──
    const int sideW = px(kSidebarWidth);
    l.sidebar = Rect{l.panel.x, l.panel.y, sideW, panelH};
    const int logo = px(30);
    l.logo = Rect{l.panel.x + px(20), l.panel.y + px(17), logo, logo};
    const int nameX = l.logo.right() + px(12);
    l.title = Rect{nameX, l.panel.y + px(14), sideW - px(20) - (nameX - l.panel.x), px(22)};
    l.subtitle = Rect{nameX, l.title.bottom() + px(1), l.title.w, px(16)};

    const int navX = l.panel.x + px(12);
    const int navW = sideW - px(24);
    const int navH = px(42);
    const int navGap = px(6);
    int navY = l.panel.y + px(78);
    l.navStatus = Rect{navX, navY, navW, navH};
    navY += navH + navGap;
    l.navControl = Rect{navX, navY, navW, navH};
    navY += navH + navGap;
    l.navHelp = Rect{navX, navY, navW, navH};
    l.sidebarFoot = Rect{l.panel.x + px(16), l.panel.bottom() - px(52), sideW - px(32), px(36)};

    // ── Strip atas: area geser, judul bagian, tombol caption ──
    const int captionH = px(kCaptionHeight);
    l.titleBar = Rect{l.panel.x, l.panel.y, panelW, captionH};
    const int closeSize = px(32);
    const int captionGap = px(4);
    const int captionY = l.panel.y + (captionH - closeSize) / 2;
    l.closeButton = Rect{l.panel.right() - px(12) - closeSize, captionY, closeSize, closeSize};
    l.maximizeButton = Rect{l.closeButton.x - captionGap - closeSize, captionY, closeSize, closeSize};
    l.minimizeButton = Rect{l.maximizeButton.x - captionGap - closeSize, captionY, closeSize, closeSize};
    l.sectionTitle = Rect{l.sidebar.right() + pad, l.panel.y, l.minimizeButton.x - px(16) - (l.sidebar.right() + pad), captionH};

    // ── Daerah isi ──
    const int contentX = l.sidebar.right() + pad;
    const int contentW = l.panel.right() - pad - contentX;
    l.content = Rect{contentX, l.panel.y + captionH + px(16), contentW,
                     l.panel.bottom() - px(20) - (l.panel.y + captionH + px(16))};

    // ── Bagian Status: kartu status + dua kartu identitas ──
    const int statusH = px(64);
    l.statusCard = Rect{contentX, l.content.y, contentW, statusH};
    const int dot = px(10);
    l.statusDot = Rect{l.statusCard.x + px(20), l.statusCard.y + px(22), dot, dot};
    const int lineX = l.statusDot.right() + px(14);
    const int lineW = l.statusCard.right() - px(18) - lineX;
    l.statusLine1 = Rect{lineX, l.statusCard.y + px(13), lineW, px(20)};
    l.statusLine2 = Rect{lineX, l.statusLine1.bottom() + px(3), lineW, px(18)};

    const int cardH = px(84);
    const int copyW = px(84);
    const int copyH = px(36);
    l.idCard = Rect{contentX, l.statusCard.bottom() + px(16), contentW, cardH};
    l.idLabel = Rect{l.idCard.x + px(18), l.idCard.y + px(14), contentW - px(140), px(16)};
    l.idValue = Rect{l.idCard.x + px(18), l.idCard.y + px(36), contentW - px(134), px(32)};
    l.idCopy = Rect{l.idCard.right() - px(18) - copyW, l.idCard.y + (cardH - copyH) / 2, copyW, copyH};

    l.passwordCard = Rect{contentX, l.idCard.bottom() + gap, contentW, cardH};
    l.passwordLabel = Rect{l.passwordCard.x + px(18), l.passwordCard.y + px(14), contentW - px(140), px(16)};
    l.passwordValue = Rect{l.passwordCard.x + px(18), l.passwordCard.y + px(36), contentW - px(134), px(32)};    l.passwordCopy = Rect{
        l.passwordCard.right() - px(18) - copyW,
        l.passwordCard.y + (cardH - copyH) / 2,
        copyW,
        copyH};

    // ── Bagian Kontrol: tiga aksi + dua tautan + baris log ──
    const int actionH = px(64);
    const int thirdW = (contentW - 2 * gap) / 3;
    l.start = Rect{contentX, l.content.y, thirdW, actionH};
    l.stop = Rect{l.start.right() + gap, l.content.y, thirdW, actionH};
    l.restart = Rect{l.stop.right() + gap, l.content.y, contentW - 2 * thirdW - 2 * gap, actionH};
    const int row2 = l.start.bottom() + gap;
    const int halfW = (contentW - gap) / 2;
    l.web = Rect{contentX, row2, halfW, px(48)};
    l.openLog = Rect{l.web.right() + gap, row2, contentW - halfW - gap, px(48)};
    l.logLine = Rect{contentX, l.web.bottom() + px(14), contentW, px(18)};

    // ── Bagian Bantuan: teks petunjuk ──
    l.hint = Rect{contentX, l.content.y, contentW, l.content.h};
    return l;
}

// Kontrol isi hanya boleh diklik saat bagiannya tampil — menekan tombol yang
// tidak terlihat sama saja dengan jebakan.
inline bool sectionShowsTarget(int section, Target target) {
    switch (target) {
    case Target::CopyId:
    case Target::CopyPassword:
        return section == kSectionStatus;
    case Target::Start:
    case Target::Stop:
    case Target::Restart:
    case Target::Web:
    case Target::OpenLog:
        return section == kSectionControl;
    default:
        return true;
    }
}

// Sasaran klik di koordinat klien jendela. Tombol diperiksa lebih dulu
// supaya tombol caption di dalam area judul tetap menang.
inline Target targetAt(const PanelLayout& l, int x, int y) {
    if (l.minimizeButton.contains(x, y)) return Target::Minimize;
    if (l.maximizeButton.contains(x, y)) return Target::Maximize;
    if (l.closeButton.contains(x, y)) return Target::Close;
    if (l.navStatus.contains(x, y)) return Target::NavStatus;
    if (l.navControl.contains(x, y)) return Target::NavControl;
    if (l.navHelp.contains(x, y)) return Target::NavHelp;
    if (l.idCopy.contains(x, y) && sectionShowsTarget(l.section, Target::CopyId)) return Target::CopyId;
    if (l.passwordCopy.contains(x, y) && sectionShowsTarget(l.section, Target::CopyPassword)) return Target::CopyPassword;
    if (l.start.contains(x, y) && sectionShowsTarget(l.section, Target::Start)) return Target::Start;
    if (l.stop.contains(x, y) && sectionShowsTarget(l.section, Target::Stop)) return Target::Stop;
    if (l.restart.contains(x, y) && sectionShowsTarget(l.section, Target::Restart)) return Target::Restart;
    if (l.web.contains(x, y) && sectionShowsTarget(l.section, Target::Web)) return Target::Web;
    if (l.openLog.contains(x, y) && sectionShowsTarget(l.section, Target::OpenLog)) return Target::OpenLog;
    if (l.titleBar.contains(x, y)) return Target::TitleBar;
    return Target::None;
}

// ── Rasterisasi lembut ──
//
// Sudut membulat digambar sendiri, jadi tepinya harus dihitung sendiri juga:
// GDI tidak menghaluskan tepi (antialias), dan tepi bergerigi persis yang
// membuat jendela "kotak Windows" terlihat murah.

inline float smoothstep01(float t) {
    t = clampValue(t, 0.0f, 1.0f);
    return t * t * (3.0f - 2.0f * t);
}

// Jarak bertanda ke tepi persegi membulat: negatif di dalam, positif di luar.
inline float roundedRectDistance(float px, float py, const Rect& r, float radius) {
    const float halfW = static_cast<float>(r.w) * 0.5f;
    const float halfH = static_cast<float>(r.h) * 0.5f;
    const float cx = static_cast<float>(r.x) + halfW;
    const float cy = static_cast<float>(r.y) + halfH;
    const float dx = std::fabs(px - cx) - (halfW - radius);
    const float dy = std::fabs(py - cy) - (halfH - radius);
    const float outsideX = maxValue(dx, 0.0f);
    const float outsideY = maxValue(dy, 0.0f);
    return std::sqrt(outsideX * outsideX + outsideY * outsideY) + minValue(maxValue(dx, dy), 0.0f) - radius;
}

// Cakupan piksel 0..1 dengan tepi selebar ~1 piksel.
inline float roundedRectCoverage(float px, float py, const Rect& r, float radius) {
    const float distance = roundedRectDistance(px, py, r, radius);
    return clampValue(0.5f - distance, 0.0f, 1.0f);
}

// Titik tengah, dipakai uji dan penempatan ikon.
inline int centerX(const Rect& r) { return r.x + r.w / 2; }
inline int centerY(const Rect& r) { return r.y + r.h / 2; }

} // namespace xydesk::panel
