#pragma once
// Geometri layar gerbang (sambutan & masuk). Murni angka seperti layout.h,
// jadi bisa diuji tanpa Windows: packaging/tests/native-onboarding-test.cpp.
#include "layout.h"

namespace xydesk::panel {

// Kartu gerbang mengambang di tengah panel — bahasa macOS: satu kartu fokus,
// sisanya diredam, tanpa sidebar dan tanpa isi yang mengganggu.
constexpr int kGateCardWidth = 420;
constexpr int kGateCardPadding = 32;
constexpr int kGateButtonHeight = 44;
constexpr int kGateGap = 12;
constexpr int kGateMarkSize = 72;

struct GateLayout {
    int scalePct = 100;
    int radiusCard = kRadiusCard;
    int radiusControl = kRadiusControl;

    Rect panel{};     // seluruh permukaan (untuk latar yang diredam)
    Rect card{};      // kartu fokus di tengah
    Rect mark{};      // logo bulat di atas
    Rect title{};     // judul besar
    Rect subtitle{};  // satu-dua baris penjelas
    Rect primary{};   // tombol utama (Mulai / Lanjut dengan Google)
    Rect secondary{}; // tombol kedua (Masuk dengan email) — kosong di sambutan
    Rect note{};      // catatan kecil / pesan gagal
    Rect dots{};      // indikator langkah, dua titik
    Rect quit{};      // "Keluar" kecil di pojok kartu

    // Jumlah elemen yang dianimasikan berurutan; dipakai onboarding::staggered.
    static constexpr int kSteps = 5;
};

/**
 * Hitung kartu gerbang untuk panel `panelRect`.
 * `withSecondary` = sediakan baris tombol kedua (layar masuk), false untuk
 * sambutan yang hanya punya satu tombol.
 */
inline GateLayout computeGateLayout(const Rect& panelRect, int scalePct, bool withSecondary) {
    GateLayout g;
    g.scalePct = scalePct;
    g.panel = panelRect;
    g.radiusCard = scaled(kRadiusCard, scalePct);
    g.radiusControl = scaled(kRadiusControl, scalePct);

    const int pad = scaled(kGateCardPadding, scalePct);
    const int gap = scaled(kGateGap, scalePct);
    const int button = scaled(kGateButtonHeight, scalePct);
    const int markSize = scaled(kGateMarkSize, scalePct);

    // Lebar kartu mengikuti panel bila panel sempit, tapi tidak pernah
    // melebihi lebar nyaman membaca.
    const int maxWidth = maxValue(panelRect.w - scaled(80, scalePct), scaled(280, scalePct));
    const int cardWidth = minValue(scaled(kGateCardWidth, scalePct), maxWidth);

    const int titleHeight = scaled(38, scalePct);
    const int subtitleHeight = scaled(44, scalePct);
    const int noteHeight = scaled(34, scalePct);
    const int dotsHeight = scaled(10, scalePct);

    int cardHeight = pad + markSize + gap + titleHeight + scaled(6, scalePct) + subtitleHeight
        + scaled(20, scalePct) + button;
    if (withSecondary) cardHeight += gap + button;
    cardHeight += gap + noteHeight + scaled(10, scalePct) + dotsHeight + pad;

    g.card = Rect{
        panelRect.x + (panelRect.w - cardWidth) / 2,
        panelRect.y + maxValue((panelRect.h - cardHeight) / 2, scaled(24, scalePct)),
        cardWidth,
        cardHeight,
    };

    const int left = g.card.x + pad;
    const int innerWidth = g.card.w - 2 * pad;
    int y = g.card.y + pad;

    g.mark = Rect{g.card.x + (g.card.w - markSize) / 2, y, markSize, markSize};
    y += markSize + gap;

    g.title = Rect{left, y, innerWidth, titleHeight};
    y += titleHeight + scaled(6, scalePct);

    g.subtitle = Rect{left, y, innerWidth, subtitleHeight};
    y += subtitleHeight + scaled(20, scalePct);

    g.primary = Rect{left, y, innerWidth, button};
    y += button;

    if (withSecondary) {
        y += gap;
        g.secondary = Rect{left, y, innerWidth, button};
        y += button;
    } else {
        g.secondary = Rect{left, y, innerWidth, 0};
    }

    y += gap;
    g.note = Rect{left, y, innerWidth, noteHeight};
    y += noteHeight + scaled(10, scalePct);

    const int dotsWidth = scaled(34, scalePct);
    g.dots = Rect{g.card.x + (g.card.w - dotsWidth) / 2, y, dotsWidth, dotsHeight};

    const int quitSize = scaled(28, scalePct);
    g.quit = Rect{g.card.right() - quitSize - scaled(10, scalePct), g.card.y + scaled(10, scalePct),
        quitSize, quitSize};
    return g;
}

} // namespace xydesk::panel
