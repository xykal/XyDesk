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

#include <algorithm>
#include <cmath>

namespace xydesk::panel {

// Sudut membulat: radius 16 untuk permukaan dan kartu, 12 untuk kontrol,
// mengikuti skala radius desain XyDesk (8/12/16/20, lihat docs/DESIGN.md).
constexpr int kRadiusPanel = 16;
constexpr int kRadiusCard = 16;
constexpr int kRadiusControl = 12;

// Ruang untuk bayangan di luar panel. Jendela per-piksel-alpha membuat
// bayangan bisa digambar sendiri (bukan bayangan DWM bawaan Windows).
constexpr int kShadowMargin = 24;
constexpr int kShadowSpread = 26;
constexpr int kShadowStrength = 122;

constexpr int kPanelWidth = 560;
constexpr int kPanelHeight = 568;
constexpr int kPadding = 24;
constexpr int kGap = 12;

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
    Close,
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
    case Target::Close: return "Close";
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
    int radiusPanel = kRadiusPanel;
    int radiusCard = kRadiusCard;
    int radiusControl = kRadiusControl;

    Rect window{};   // seluruh permukaan berlapis, termasuk ruang bayangan
    Rect panel{};    // permukaan membulat yang terlihat

    Rect titleBar{};
    Rect logo{};
    Rect title{};
    Rect subtitle{};
    Rect closeButton{};

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
    Rect hint{};
};

inline int scaled(int value, int scalePct) {
    return (value * scalePct + 50) / 100;
}

// `dpi` datang dari GetDpiForWindow; 96 apa adanya, 120 = 125%, 144 = 150%.
inline int scalePctFromDpi(int dpi) {
    if (dpi <= 0) return 100;
    return std::clamp((dpi * 100 + 48) / 96, 75, 400);
}

inline PanelLayout computeLayout(int dpi) {
    const int s = scalePctFromDpi(dpi);
    const auto px = [s](int value) { return scaled(value, s); };

    PanelLayout l;
    l.scalePct = s;
    l.radiusPanel = px(kRadiusPanel);
    l.radiusCard = px(kRadiusCard);
    l.radiusControl = px(kRadiusControl);

    const int margin = px(kShadowMargin);
    const int panelW = px(kPanelWidth);
    const int panelH = px(kPanelHeight);
    l.panel = Rect{margin, margin, panelW, panelH};
    l.window = Rect{0, 0, panelW + 2 * margin, panelH + 2 * margin};

    const int pad = px(kPadding);
    const int gap = px(kGap);
    const int contentW = panelW - 2 * pad;
    const int contentX = l.panel.x + pad;

    // ── Judul: logo, nama, subjudul, satu tombol tutup ──
    const int logo = px(28);
    l.logo = Rect{contentX, l.panel.y + pad, logo, logo};
    const int closeSize = px(32);
    l.closeButton = Rect{l.panel.right() - pad - closeSize, l.panel.y + pad - px(2), closeSize, closeSize};
    const int textX = l.logo.right() + px(12);
    const int textW = l.closeButton.x - px(12) - textX;
    l.title = Rect{textX, l.panel.y + pad - px(3), textW, px(24)};
    l.subtitle = Rect{textX, l.title.bottom() + px(1), textW, px(18)};
    l.titleBar = Rect{l.panel.x, l.panel.y, panelW, pad + logo + px(10)};

    // ── Kartu status ──
    const int statusH = px(64);
    l.statusCard = Rect{contentX, l.titleBar.bottom() + px(20), contentW, statusH};
    const int dot = px(10);
    l.statusDot = Rect{l.statusCard.x + px(20), l.statusCard.y + px(22), dot, dot};
    const int lineX = l.statusDot.right() + px(14);
    const int lineW = l.statusCard.right() - px(18) - lineX;
    l.statusLine1 = Rect{lineX, l.statusCard.y + px(13), lineW, px(20)};
    l.statusLine2 = Rect{lineX, l.statusLine1.bottom() + px(3), lineW, px(18)};

    // ── Kartu identitas ──
    const int cardH = px(84);
    const int copyW = px(76);
    const int copyH = px(34);
    l.idCard = Rect{contentX, l.statusCard.bottom() + px(20), contentW, cardH};
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

    // ── Tombol aksi, dua baris, semua pas dalam lebar konten ──
    const int actionH = px(46);
    const int row1 = l.passwordCard.bottom() + px(20);
    l.start = Rect{contentX, row1, px(190), actionH};
    l.stop = Rect{l.start.right() + gap, row1, px(150), actionH};
    l.restart = Rect{l.stop.right() + gap, row1, contentX + contentW - (l.stop.right() + gap), actionH};

    const int row2 = row1 + actionH + gap;
    const int halfW = (contentW - gap) / 2;
    l.web = Rect{contentX, row2, halfW, actionH};
    l.openLog = Rect{l.web.right() + gap, row2, contentW - halfW - gap, actionH};

    l.hint = Rect{contentX, row2 + actionH + px(18), contentW, px(44)};
    return l;
}

// Sasaran klik di koordinat klien jendela. Tombol diperiksa lebih dulu
// supaya tombol tutup di dalam area judul tetap menang.
inline Target targetAt(const PanelLayout& l, int x, int y) {
    if (l.closeButton.contains(x, y)) return Target::Close;
    if (l.idCopy.contains(x, y)) return Target::CopyId;
    if (l.passwordCopy.contains(x, y)) return Target::CopyPassword;
    if (l.start.contains(x, y)) return Target::Start;
    if (l.stop.contains(x, y)) return Target::Stop;
    if (l.restart.contains(x, y)) return Target::Restart;
    if (l.web.contains(x, y)) return Target::Web;
    if (l.openLog.contains(x, y)) return Target::OpenLog;
    if (l.titleBar.contains(x, y)) return Target::TitleBar;
    return Target::None;
}

// ── Rasterisasi lembut ──
//
// Sudut membulat digambar sendiri, jadi tepinya harus dihitung sendiri juga:
// GDI tidak menghaluskan tepi (antialias), dan tepi bergerigi persis yang
// membuat jendela "kotak Windows" terlihat murah.

inline float smoothstep01(float t) {
    t = std::clamp(t, 0.0f, 1.0f);
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
    const float outsideX = std::max(dx, 0.0f);
    const float outsideY = std::max(dy, 0.0f);
    return std::sqrt(outsideX * outsideX + outsideY * outsideY) + std::min(std::max(dx, dy), 0.0f) - radius;
}

// Cakupan piksel 0..1 dengan tepi selebar ~1 piksel.
inline float roundedRectCoverage(float px, float py, const Rect& r, float radius) {
    const float distance = roundedRectDistance(px, py, r, radius);
    return std::clamp(0.5f - distance, 0.0f, 1.0f);
}

// Bayangan lembut di luar bentuk: 1 di tepi, memudar sampai 0 pada `spread`.
inline float roundedRectShadow(float px, float py, const Rect& r, float radius, float spread, float strength) {
    const float distance = roundedRectDistance(px, py, r, radius);
    if (distance <= 0.0f) return 0.0f;
    const float t = 1.0f - distance / spread;
    return smoothstep01(t) * strength;
}

// Titik tengah, dipakai uji dan penempatan ikon.
inline int centerX(const Rect& r) { return r.x + r.w / 2; }
inline int centerY(const Rect& r) { return r.y + r.h / 2; }

} // namespace xydesk::panel
