#pragma once
// Tata letak dan geometri panel XyDesk.
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
// Bentuk sejak 25 Sep (umpan balik pemilik): panel LEBAR (900x560) dengan
// sidebar ikon di kiri dan tiga halaman (Status, Pairing, Kontrol), TANPA
// bayangan di luar jendela — sudut membulat dan tepi halus tetap digambar
// sendiri biar tidak terlihat seperti kotak Windows bawaan.

#include <algorithm>
#include <cmath>

namespace xydesk::panel {

// Sudut membulat: radius 16 untuk permukaan dan kartu, 12 untuk kontrol,
// mengikuti skala radius desain XyDesk (8/12/16/20, lihat docs/DESIGN.md).
constexpr int kRadiusPanel = 16;
constexpr int kRadiusCard = 16;
constexpr int kRadiusControl = 12;

// Tanpa bayangan: jendela pas sebesar panel (permintaan pemilik). Konstanta
// bayangan dibiarkan 0 supaya pemakai lama tetap kompilasi.
constexpr int kShadowMargin = 0;
constexpr int kShadowSpread = 0;
constexpr int kShadowStrength = 0;

constexpr int kPanelWidth = 900;
constexpr int kPanelHeight = 560;
constexpr int kPadding = 20;
constexpr int kGap = 12;

// Lebar sidebar ikon; konten mengisi sisanya.
constexpr int kSidebarX = 12;
constexpr int kSidebarWidth = 84;
constexpr int kSidebarItemHeight = 64;
constexpr int kSidebarGap = 6;
constexpr int kCaptionHeight = 64;

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

// Halaman konten. Sidebar memilih satu dari tiga.
enum class Page {
    Status,
    Pairing,
    Control,
};

// Sasaran klik. Satu daftar untuk tata letak, hit-test mouse, dan urutan Tab
// supaya ketiganya tidak pernah berbeda pendapat.
enum class Target {
    None,
    TitleBar,
    Minimize,
    Maximize,
    Close,
    PageStatus,
    PagePairing,
    PageControl,
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
    case Target::PageStatus: return "PageStatus";
    case Target::PagePairing: return "PagePairing";
    case Target::PageControl: return "PageControl";
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

inline const char* pageName(Page page) {
    switch (page) {
    case Page::Status: return "Status";
    case Page::Pairing: return "Pairing";
    case Page::Control: return "Control";
    }
    return "Status";
}

struct PanelLayout {
    int scalePct = 100;
    int radiusPanel = kRadiusPanel;
    int radiusCard = kRadiusCard;
    int radiusControl = kRadiusControl;

    Rect window{};   // seluruh permukaan berlapis (kini sama dengan panel)
    Rect panel{};    // permukaan membulat yang terlihat

    Rect titleBar{};
    Rect logo{};
    Rect title{};
    Rect subtitle{};
    Rect minimizeButton{};
    Rect maximizeButton{};
    Rect closeButton{};

    // Sidebar: tiga tombol halaman, masing-masing dengan area ikon + label.
    Rect sideStatus{};
    Rect sideStatusIcon{};
    Rect sideStatusLabel{};
    Rect sidePairing{};
    Rect sidePairingIcon{};
    Rect sidePairingLabel{};
    Rect sideControl{};
    Rect sideControlIcon{};
    Rect sideControlLabel{};

    // Halaman Status.
    Rect statusCard{};
    Rect statusDot{};
    Rect statusLine1{};
    Rect statusLine2{};
    Rect captureCard{};
    Rect captureTitle{};
    Rect captureLine1{};
    Rect captureLine2{};

    // Halaman Pairing.
    Rect idCard{};
    Rect idLabel{};
    Rect idValue{};
    Rect idCopy{};

    Rect passwordCard{};
    Rect passwordLabel{};
    Rect passwordValue{};
    Rect passwordCopy{};

    // Halaman Kontrol.
    Rect start{};
    Rect stop{};
    Rect restart{};
    Rect web{};
    Rect openLog{};

    // Petunjuk kecil di dasar konten, ikut di semua halaman.
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

inline int centerX(const Rect& r) { return r.x + r.w / 2; }
inline int centerY(const Rect& r) { return r.y + r.h / 2; }

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

    const int panelW = px(kPanelWidth);
    const int panelH = px(kPanelHeight);
    const int pad = px(kPadding);
    const int gap = px(kGap);
    l.window = Rect{0, 0, panelW, panelH};
    l.panel = Rect{0, 0, panelW, panelH};

    // ── Caption: logo, nama, subjudul, tiga tombol caption ──
    const int logo = px(30);
    l.logo = Rect{pad, pad - px(3), logo, logo};
    const int closeSize = px(32);
    const int captionGap = px(4);
    const int captionY = pad - px(2);
    l.closeButton = Rect{panelW - pad - closeSize, captionY, closeSize, closeSize};
    l.maximizeButton = Rect{l.closeButton.x - captionGap - closeSize, captionY, closeSize, closeSize};
    l.minimizeButton = Rect{l.maximizeButton.x - captionGap - closeSize, captionY, closeSize, closeSize};
    const int textX = l.logo.right() + px(12);
    const int textW = l.minimizeButton.x - px(12) - textX;
    l.title = Rect{textX, pad - px(4), textW, px(24)};
    l.subtitle = Rect{textX, l.title.bottom() + px(1), textW, px(18)};
    l.titleBar = Rect{0, 0, panelW, px(kCaptionHeight)};

    // ── Sidebar kiri: ikon + label kecil, halaman aktif diberi pill ──
    const int sideX = px(kSidebarX);
    const int sideW = px(kSidebarWidth);
    const int itemH = px(kSidebarItemHeight);
    const int itemGap = px(kSidebarGap);
    int itemY = l.titleBar.bottom() + px(8);
    const auto sidebarItem = [&](Rect& item, Rect& icon, Rect& label) {
        item = Rect{sideX, itemY, sideW, itemH};
        icon = Rect{item.x + (sideW - px(24)) / 2, item.y + px(10), px(24), px(24)};
        label = Rect{item.x + px(4), icon.bottom() + px(5), sideW - px(8), px(14)};
        itemY = item.bottom() + itemGap;
    };
    sidebarItem(l.sideStatus, l.sideStatusIcon, l.sideStatusLabel);
    sidebarItem(l.sidePairing, l.sidePairingIcon, l.sidePairingLabel);
    sidebarItem(l.sideControl, l.sideControlIcon, l.sideControlLabel);

    // ── Area konten di kanan sidebar ──
    const int contentX = sideX + sideW + px(8);
    const int contentW = panelW - contentX - pad;
    const int topY = l.titleBar.bottom() + px(12);

    // Halaman Status: kartu status + kartu kesehatan capture.
    const int statusH = px(72);
    l.statusCard = Rect{contentX, topY, contentW, statusH};
    const int dot = px(10);
    l.statusDot = Rect{l.statusCard.x + px(20), l.statusCard.y + px(24), dot, dot};
    const int lineX = l.statusDot.right() + px(14);
    const int lineW = l.statusCard.right() - px(18) - lineX;
    l.statusLine1 = Rect{lineX, l.statusCard.y + px(15), lineW, px(20)};
    l.statusLine2 = Rect{lineX, l.statusLine1.bottom() + px(3), lineW, px(18)};

    const int captureH = px(88);
    l.captureCard = Rect{contentX, l.statusCard.bottom() + gap, contentW, captureH};
    l.captureTitle = Rect{l.captureCard.x + px(20), l.captureCard.y + px(12), contentW - px(40), px(16)};
    l.captureLine1 = Rect{l.captureCard.x + px(20), l.captureTitle.bottom() + px(6), contentW - px(40), px(18)};
    l.captureLine2 = Rect{l.captureCard.x + px(20), l.captureLine1.bottom() + px(3), contentW - px(40), px(18)};

    // Halaman Pairing: dua kartu identitas.
    const int cardH = px(84);
    const int copyW = px(76);
    const int copyH = px(34);
    l.idCard = Rect{contentX, topY, contentW, cardH};
    l.idLabel = Rect{l.idCard.x + px(18), l.idCard.y + px(14), contentW - px(140), px(16)};
    l.idValue = Rect{l.idCard.x + px(18), l.idCard.y + px(36), contentW - px(126), px(32)};
    l.idCopy = Rect{l.idCard.right() - px(18) - copyW, l.idCard.y + (cardH - copyH) / 2, copyW, copyH};

    l.passwordCard = Rect{contentX, l.idCard.bottom() + gap, contentW, cardH};
    l.passwordLabel = Rect{l.passwordCard.x + px(18), l.passwordCard.y + px(14), contentW - px(140), px(16)};
    l.passwordValue = Rect{l.passwordCard.x + px(18), l.passwordCard.y + px(36), contentW - px(126), px(32)};
    l.passwordCopy = Rect{
        l.passwordCard.right() - px(18) - copyW,
        l.passwordCard.y + (cardH - copyH) / 2,
        copyW,
        copyH};

    // Halaman Kontrol: dua baris tombol aksi.
    const int actionH = px(46);
    l.start = Rect{contentX, topY, px(240), actionH};
    l.stop = Rect{l.start.right() + gap, topY, px(190), actionH};
    l.restart = Rect{l.stop.right() + gap, topY, contentX + contentW - (l.stop.right() + gap), actionH};
    const int row2 = topY + actionH + gap;
    const int halfW = (contentW - gap) / 2;
    l.web = Rect{contentX, row2, halfW, actionH};
    l.openLog = Rect{l.web.right() + gap, row2, contentW - halfW - gap, actionH};

    // Petunjuk di dasar konten, milik semua halaman.
    l.hint = Rect{contentX, panelH - pad - px(38), contentW, px(38)};
    return l;
}

// Apakah sasaran konten terlihat pada halaman ini? Tombol caption dan sidebar
// selalu; sisanya hanya pada halamannya masing-masing — kalau tidak, klik di
// halaman Pairing bisa mengenai tombol tak terlihat halaman Kontrol.
inline bool targetOnPage(Target target, Page page) {
    switch (target) {
    case Target::CopyId:
    case Target::CopyPassword:
        return page == Page::Pairing;
    case Target::Start:
    case Target::Stop:
    case Target::Restart:
    case Target::Web:
    case Target::OpenLog:
        return page == Page::Control;
    default:
        return true;
    }
}

// Sasaran klik di koordinat klien jendela. Tombol diperiksa lebih dulu
// supaya tombol caption di dalam area judul tetap menang.
inline Target targetAt(const PanelLayout& l, Page page, int x, int y) {
    if (l.minimizeButton.contains(x, y)) return Target::Minimize;
    if (l.maximizeButton.contains(x, y)) return Target::Maximize;
    if (l.closeButton.contains(x, y)) return Target::Close;
    if (l.sideStatus.contains(x, y)) return Target::PageStatus;
    if (l.sidePairing.contains(x, y)) return Target::PagePairing;
    if (l.sideControl.contains(x, y)) return Target::PageControl;
    if (targetOnPage(Target::CopyId, page) && l.idCopy.contains(x, y)) return Target::CopyId;
    if (targetOnPage(Target::CopyPassword, page) && l.passwordCopy.contains(x, y)) return Target::CopyPassword;
    if (targetOnPage(Target::Start, page)) {
        if (l.start.contains(x, y)) return Target::Start;
        if (l.stop.contains(x, y)) return Target::Stop;
        if (l.restart.contains(x, y)) return Target::Restart;
        if (l.web.contains(x, y)) return Target::Web;
        if (l.openLog.contains(x, y)) return Target::OpenLog;
    }
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

// Bayangan lembut di luar bentuk. Sejak 25 Sep panel tidak memakai bayangan
// (permintaan pemilik); fungsi dipertahankan karena uji matematis memakainya
// dan suatu saat bayangan bisa dinyalakan lagi lewat `spread` > 0.
inline float roundedRectShadow(float px, float py, const Rect& r, float radius, float spread, float strength) {
    if (spread <= 0.0f) return 0.0f;
    const float distance = roundedRectDistance(px, py, r, radius);
    if (distance <= 0.0f) return 0.0f;
    const float t = 1.0f - distance / spread;
    return strength * smoothstep01(t);
}

} // namespace xydesk::panel
