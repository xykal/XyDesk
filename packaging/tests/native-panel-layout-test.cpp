// Uji tata letak panel native XyDesk di Linux (tanpa Windows, tanpa GUI).
//
// Yang diuji bukan gambar, melainkan angka yang menentukan gambar itu:
// apakah semua kontrol masih berada di dalam panel, apakah tombol tidak
// bertumpuk, apakah hit-test klik mengembalikan sasaran yang benar, dan
// apakah skala DPI bekerja. Semua ini dijalankan di CI Linux sehingga
// kesalahan tata letak ketahuan sebelum masuk ke runner Windows.
//
// Sejak 27 Sep panel lebar 1100x720 dengan sidebar + tiga halaman dan TANPA
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
    check(l.window.w == 1100 && l.window.h == 720, "ukuran jendela 1100x720 pada skala 100");
    check(l.panel.w == 1100 && l.panel.h == 720, "panel sama besar dengan jendela (tanpa margin bayangan)");
    check(l.panel.x == 0 && l.panel.y == 0, "tanpa margin bayangan: panel di (0,0)");
    check(l.radiusPanel == 24 && l.radiusCard == 20 && l.radiusControl == 12, "rounded workspace: panel 24, cards 20, controls 12");

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

    // Caption: lampu lalu lintas macOS di kiri — tutup, perkecil, perbesar.
    check(!overlaps(l.minimizeButton, l.maximizeButton) && !overlaps(l.maximizeButton, l.closeButton), "tombol caption tidak bertumpuk");
    check(l.closeButton.x < l.minimizeButton.x && l.minimizeButton.x < l.maximizeButton.x, "urutan macOS: tutup, perkecil, perbesar");
    check(l.minimizeButton.y == l.maximizeButton.y && l.maximizeButton.y == l.closeButton.y, "tombol caption sejajar satu baris");
    check(!overlaps(l.title, l.maximizeButton) && !overlaps(l.subtitle, l.maximizeButton), "judul tidak bertumpuk dengan tombol caption");
    check(l.closeButton.x >= l.panel.x, "lampu tutup tetap di dalam panel");
    check(l.trafficLights.x >= l.panel.x + 20 - 1, "gugus lampu menghormati padding kiri");
    check(l.trafficLights.right() <= l.toggleSidebar.x, "lampu tidak menabrak tombol sidebar");
    check(l.maximizeButton.right() <= l.logo.x, "lampu berada sebelum logo");
    check(l.title.x > l.trafficLights.right(), "judul bergeser ke kanan lampu");
    // Kotak sentuh lebih besar dari lingkarannya supaya tetap mudah diklik.
    check(l.closeButton.w >= 24 && l.closeButton.h >= 24, "kotak sentuh lampu minimal 24 px");
    check(l.closeButton.w > 13, "kotak sentuh lebih besar dari lingkaran 13 px");

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
    check(bigger.radiusPanel == 36 && bigger.radiusCard == 30 && bigger.radiusControl == 18, "radius ikut skala");
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

void testCompactAndCollapsed() {
    for(int dpi:{96,120,144,192})for(int width:{720,1100,1600})for(bool collapsed:{false,true}){
        const auto l=xydesk::panel::computeLayout(dpi,width,480,collapsed);
        const struct {Rect rect;Target target;} common[]={
            {l.toggleSidebar,Target::ToggleSidebar},{l.settings,Target::Settings},{l.profile,Target::Profile},{l.help,Target::Help}};
        for(const auto& t:common){check(insidePanel(l,t.rect),"header tool inside panel");check(xydesk::panel::targetAt(l,Page::Status,xydesk::panel::centerX(t.rect),xydesk::panel::centerY(t.rect))==t.target,"header tool hit test");}
        check(insidePanel(l,l.connectionQr)&&l.connectionQr.bottom()<l.hint.y,"QR action fits compact layout");
        const struct{Rect rect;Target target;} screens[]={{l.sideConnections,Target::PageConnections}};
        for(const auto& screen:screens){check(insidePanel(l,screen.rect),"new navigation inside window");check(xydesk::panel::targetAt(l,Page::Status,xydesk::panel::centerX(screen.rect),xydesk::panel::centerY(screen.rect))==screen.target,"new screen hit target");}
        check(l.sideSettings.w==0&&l.sideAccount.w==0&&l.sideHelp.w==0,"utility pages have no duplicate sidebar hit areas");
        check(insidePanel(l,l.sidebarShell)&&insidePanel(l,l.workspaceShell),"floating surfaces inside window");
        check(!overlaps(l.sidebarShell,l.workspaceShell),"sidebar and workspace separated by gutter");
        check(l.pageDescription.bottom()<l.statusCard.y,"page heading does not overlap content");
        check(l.homeAccess.bottom()<l.hint.y&&l.homeConnections.bottom()<l.hint.y,"home actions above footer at compact sizes");
        check(l.title.right()<l.settings.x,"caption tools do not overlap title");
        check(l.idCard.w<=xydesk::panel::scaled(560,l.scalePct),"identity block compact");
        check(insidePanel(l,l.deviceLink)&&insidePanel(l,l.copyLink),"link inside panel");
        check(!overlaps(l.deviceLink,l.copyLink),"link copy independent");
        check(xydesk::panel::targetAt(l,Page::Pairing,xydesk::panel::centerX(l.copyLink),xydesk::panel::centerY(l.copyLink))==Target::CopyLink,"copy link hit");
        check(xydesk::panel::targetAt(l,Page::Status,xydesk::panel::centerX(l.copyLink),xydesk::panel::centerY(l.copyLink))!=Target::CopyLink,"invisible pairing link not clickable");
        check(collapsed?l.sideStatusLabel.w==0:l.sideStatusLabel.w>0,"collapse hides labels without hiding icons");
    }
}

// Tombol "Kirim berkas" hidup berdampingan dengan "Putus sesi" di halaman
// koneksi. Yang diuji di sini hanya yang murni angka: ia tidak menimpa
// tetangganya, tidak keluar dari kartu kerja, dan hanya bisa diklik di
// halaman koneksi.
void testSendFileButton() {
    for(int dpi:{96,120,144,192})for(int width:{720,1100,1600})for(bool collapsed:{false,true}){
        const auto l=xydesk::panel::computeLayout(dpi,width,480,collapsed);
        if(l.sendFile.w<=0)continue;  // Panel terlalu sempit: tombol memang dilipat.
        check(!overlaps(l.sendFile,l.stopSession),"kirim berkas tidak menimpa putus sesi");
        check(l.sendFile.x>=l.stopSession.right(),"kirim berkas di kanan putus sesi");
        check(l.sendFile.y==l.stopSession.y&&l.sendFile.h==l.stopSession.h,"dua tombol sesi sebaris");
        check(insidePanel(l,l.sendFile),"kirim berkas di dalam jendela");
        check(l.sendFile.right()<=l.workspaceShell.right(),"kirim berkas tidak keluar kartu kerja");
        const int cx=xydesk::panel::centerX(l.sendFile),cy=xydesk::panel::centerY(l.sendFile);
        check(xydesk::panel::targetAt(l,Page::Connections,cx,cy)==Target::SendFile,"kirim berkas bisa diklik di halaman koneksi");
        check(xydesk::panel::targetAt(l,Page::Status,cx,cy)!=Target::SendFile,"kirim berkas tidak bocor ke halaman lain");
    }
    check(std::string(xydesk::panel::targetName(Target::SendFile))=="SendFile","tombol kirim berkas punya nama probe");
}

// Baris jawaban berkas masuk: "Terima berkas" + "Tolak" tepat di bawah
// baris putus/kirim, tanpa pernah bertumpuk dengan keduanya.
void testIncomingFileRow() {
    for(int dpi:{96,120,144,192})for(int width:{720,1100,1600})for(bool collapsed:{false,true}){
        const auto l=xydesk::panel::computeLayout(dpi,width,480,collapsed);
        if(l.acceptFile.w<=0)continue;
        check(l.acceptFile.y>=l.stopSession.bottom(),"baris jawaban di bawah baris sesi");
        check(!overlaps(l.acceptFile,l.stopSession)&&!overlaps(l.acceptFile,l.sendFile),"terima tidak menimpa baris atasnya");
        check(!overlaps(l.rejectFile,l.acceptFile),"tolak tidak menimpa terima");
        check(l.acceptFile.x==l.stopSession.x,"dua baris rata kiri");
        check(insidePanel(l,l.acceptFile),"terima di dalam jendela");
        check(l.acceptFile.right()<=l.workspaceShell.right(),"terima tidak keluar kartu kerja");
        if(l.rejectFile.w>0)check(l.rejectFile.right()<=l.workspaceShell.right(),"tolak tidak keluar kartu kerja");
        const int ax=xydesk::panel::centerX(l.acceptFile),ay=xydesk::panel::centerY(l.acceptFile);
        check(xydesk::panel::targetAt(l,Page::Connections,ax,ay)==Target::AcceptFile,"terima bisa diklik di halaman koneksi");
        check(xydesk::panel::targetAt(l,Page::Status,ax,ay)!=Target::AcceptFile,"terima tidak bocor ke halaman lain");
        if(l.rejectFile.w>0){
            const int rx=xydesk::panel::centerX(l.rejectFile),ry=xydesk::panel::centerY(l.rejectFile);
            check(xydesk::panel::targetAt(l,Page::Connections,rx,ry)==Target::RejectFile,"tolak bisa diklik di halaman koneksi");
        }
        if(l.trustFile.w>0){
            check(!overlaps(l.trustFile,l.acceptFile)&&!overlaps(l.trustFile,l.rejectFile),"terima & ingat berdiri sendiri");
            check(l.trustFile.right()<=l.workspaceShell.right(),"terima & ingat tidak keluar kartu kerja");
            const int tx=xydesk::panel::centerX(l.trustFile),ty=xydesk::panel::centerY(l.trustFile);
            check(xydesk::panel::targetAt(l,Page::Connections,tx,ty)==Target::TrustFile,"terima & ingat bisa diklik");
        }
        // Baris kebijakan selalu ada, juga saat tidak ada tawaran.
        if(l.filePolicy.w>0){
            check(l.filePolicy.y>=l.acceptFile.bottom(),"baris kebijakan di bawah baris jawaban");
            check(!overlaps(l.filePolicy,l.acceptFile)&&!overlaps(l.filePolicy,l.trustFile),"kebijakan tidak menimpa jawaban");
            check(!overlaps(l.filePolicy,l.forgetTrusted),"kebijakan dan lupakan tidak bertumpuk");
            check(insidePanel(l,l.filePolicy),"kebijakan di dalam jendela");
            check(l.filePolicy.right()<=l.workspaceShell.right(),"kebijakan tidak keluar kartu kerja");
            const int px=xydesk::panel::centerX(l.filePolicy),py=xydesk::panel::centerY(l.filePolicy);
            check(xydesk::panel::targetAt(l,Page::Connections,px,py)==Target::FilePolicy,"kebijakan bisa diklik");
            check(xydesk::panel::targetAt(l,Page::Status,px,py)!=Target::FilePolicy,"kebijakan tidak bocor ke halaman lain");
        }
        if(l.forgetTrusted.w>0){
            check(l.forgetTrusted.right()<=l.workspaceShell.right(),"lupakan tidak keluar kartu kerja");
            const int fx=xydesk::panel::centerX(l.forgetTrusted),fy=xydesk::panel::centerY(l.forgetTrusted);
            check(xydesk::panel::targetAt(l,Page::Connections,fx,fy)==Target::ForgetTrusted,"lupakan bisa diklik");
        }
        // Baris kehadiran: satu baris penuh di bawah baris kebijakan berkas.
        if(l.unattendedPolicy.w>0){
            check(l.unattendedPolicy.y>=l.filePolicy.bottom(),"baris kehadiran di bawah baris kebijakan berkas");
            check(!overlaps(l.unattendedPolicy,l.filePolicy)&&!overlaps(l.unattendedPolicy,l.forgetTrusted),"kehadiran tidak menimpa baris kebijakan");
            check(!overlaps(l.unattendedPolicy,l.acceptFile)&&!overlaps(l.unattendedPolicy,l.trustFile)&&!overlaps(l.unattendedPolicy,l.rejectFile),"kehadiran tidak menimpa baris jawaban");
            check(!overlaps(l.unattendedPolicy,l.stopSession)&&!overlaps(l.unattendedPolicy,l.sendFile),"kehadiran tidak menimpa baris sesi");
            check(!overlaps(l.unattendedPolicy,l.unattendedGrant),"kebijakan akses dan izin tidak bertumpuk");
            check(insidePanel(l,l.unattendedPolicy),"kehadiran di dalam jendela");
            check(l.unattendedPolicy.right()<=l.workspaceShell.right(),"kehadiran tidak keluar kartu kerja");
            check(l.unattendedPolicy.x==l.stopSession.x,"kehadiran lurus dengan kolom tombol lain");
            check(l.unattendedPolicy.h==l.stopSession.h,"tinggi tombol kehadiran sama dengan tetangganya");
            const int ux=xydesk::panel::centerX(l.unattendedPolicy),uy=xydesk::panel::centerY(l.unattendedPolicy);
            check(xydesk::panel::targetAt(l,Page::Connections,ux,uy)==Target::UnattendedPolicy,"kebijakan akses bisa diklik");
            check(xydesk::panel::targetAt(l,Page::Status,ux,uy)!=Target::UnattendedPolicy,"kebijakan akses tidak bocor ke halaman Status");
            check(xydesk::panel::targetAt(l,Page::Pairing,ux,uy)!=Target::UnattendedPolicy,"kebijakan akses tidak bocor ke halaman Pairing");
            check(xydesk::panel::targetAt(l,Page::Control,ux,uy)!=Target::UnattendedPolicy,"kebijakan akses tidak bocor ke halaman Kontrol");
        }
        if(l.unattendedGrant.w>0){
            check(l.unattendedGrant.y==l.unattendedPolicy.y,"izin sebaris dengan kebijakan akses");
            check(l.unattendedGrant.x>=l.unattendedPolicy.right(),"izin di kanan kebijakan akses");
            check(l.unattendedGrant.right()<=l.workspaceShell.right(),"izin tidak keluar kartu kerja");
            check(insidePanel(l,l.unattendedGrant),"izin di dalam jendela");
            const int gx=xydesk::panel::centerX(l.unattendedGrant),gy=xydesk::panel::centerY(l.unattendedGrant);
            check(xydesk::panel::targetAt(l,Page::Connections,gx,gy)==Target::UnattendedGrant,"izin bisa diklik");
            check(xydesk::panel::targetAt(l,Page::Settings,gx,gy)!=Target::UnattendedGrant,"izin tidak bocor ke halaman Atur");
        }
        // Baris kehadiran tidak boleh mendorong petunjuk keluar dari konten.
        if(l.unattendedPolicy.w>0&&l.hint.h>0){
            check(l.unattendedPolicy.bottom()<=l.hint.bottom(),"baris kehadiran tidak melewati dasar konten");
        }
    }
    check(std::string(xydesk::panel::targetName(Target::AcceptFile))=="AcceptFile","terima punya nama probe");
    check(std::string(xydesk::panel::targetName(Target::RejectFile))=="RejectFile","tolak punya nama probe");
    check(std::string(xydesk::panel::targetName(Target::TrustFile))=="TrustFile","terima & ingat punya nama probe");
    check(std::string(xydesk::panel::targetName(Target::FilePolicy))=="FilePolicy","kebijakan punya nama probe");
    check(std::string(xydesk::panel::targetName(Target::ForgetTrusted))=="ForgetTrusted","lupakan punya nama probe");
    check(std::string(xydesk::panel::targetName(Target::UnattendedPolicy))=="UnattendedPolicy","kebijakan akses punya nama probe");
    check(std::string(xydesk::panel::targetName(Target::UnattendedGrant))=="UnattendedGrant","izin punya nama probe");
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
    testCompactAndCollapsed();
    testSendFileButton();
    testIncomingFileRow();

    if (g_failures == 0) {
        std::printf("Lulus: %d pemeriksaan tata letak panel native.\n", g_checks);
        return 0;
    }
    std::printf("GAGAL: %d dari %d pemeriksaan.\n", g_failures, g_checks);
    return 1;
}
