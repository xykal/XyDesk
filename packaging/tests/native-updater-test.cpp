// Uji logika pembaruan host. Dijalankan di Linux oleh job Host Rust lewat
// packaging/tests/test-native-panel-layout.sh — tanpa Windows, tanpa jaringan.

#include <cassert>
#include <cstdio>
#include <string>

#include "updater.h"

using namespace xydesk::updater;

namespace {

std::string manifest(const std::string& version, const std::string& url,
                     const std::string& sha, long long bytes, int schema = 2,
                     long long build = 0) {
    std::string out = "{";
    out += "\"schema\":" + std::to_string(schema) + ",";
    out += "\"version\":\"" + version + "\",";
    out += "\"build\":" + std::to_string(build) + ",";
    out += "\"tag\":\"v" + version + "\",";
    out += "\"title\":\"XyDesk Update\",";
    out += "\"summary\":\"ringkas\",";
    out += "\"windows\":{\"x64\":{";
    out += "\"url\":\"" + url + "\",";
    out += "\"sha256\":\"" + sha + "\",";
    out += "\"bytes\":" + std::to_string(bytes);
    out += "}}}";
    return out;
}

const std::string kSha(64, 'a');
const std::string kUrl =
    "https://github.com/xykal/XyDesk/releases/download/v6.11.12/XyDesk-x64.exe";

void testVersionParsing() {
    const auto v = parseVersion("6.11.11+76");
    assert(v.has_value());
    assert(v->major == 6 && v->minor == 11 && v->patch == 11 && v->build == 76);

    const auto tanpaBuild = parseVersion("1.2.3");
    assert(tanpaBuild.has_value() && tanpaBuild->build == 0);

    // Bentuk yang harus ditolak, bukan ditebak.
    assert(!parseVersion("6.11"));
    assert(!parseVersion("6.11.11.4"));
    assert(!parseVersion("6.11.11+"));
    assert(!parseVersion("v6.11.11"));
    assert(!parseVersion("6.11.x"));
    assert(!parseVersion(""));
    assert(!parseVersion("6..11"));
    assert(!parseVersion("6.11+76"));
}

void testCompare() {
    const Version a = *parseVersion("6.11.11+76");
    assert(newer(*parseVersion("6.11.12+77"), a));
    assert(newer(*parseVersion("6.12.0+1"), a));
    assert(newer(*parseVersion("7.0.0+1"), a));
    // Nomor build naik tanpa perubahan versi tetap dianggap lebih baru.
    assert(newer(*parseVersion("6.11.11+77"), a));
    // Yang sama atau lebih lama tidak pernah ditawarkan.
    assert(!newer(*parseVersion("6.11.11+76"), a));
    assert(!newer(*parseVersion("6.11.10+99"), a));
    assert(!newer(*parseVersion("5.99.99+999"), a));
    assert(compare(a, a) == 0);
}

void testManifestHappyPath() {
    const auto release = parseManifest(manifest("6.11.12", kUrl, kSha, 6162386, 2, 77));
    assert(release.has_value());
    assert(release->version == "6.11.12");
    assert(release->build == 77);
    assert(release->url == kUrl);
    assert(release->bytes == 6162386);
    assert(releaseVersion(*release).build == 77);
}

void testManifestRejections() {
    // Skema lama tidak dipakai untuk memasang apa pun.
    assert(!parseManifest(manifest("6.11.12", kUrl, kSha, 100, 1)));
    // Bukan JSON / kosong.
    assert(!parseManifest("bukan json"));
    assert(!parseManifest(""));
    assert(!parseManifest("[]"));
    // Hash hilang, pendek, huruf besar, atau bukan heksadesimal.
    assert(!parseManifest(manifest("6.11.12", kUrl, "", 100)));
    assert(!parseManifest(manifest("6.11.12", kUrl, std::string(63, 'a'), 100)));
    assert(!parseManifest(manifest("6.11.12", kUrl, std::string(64, 'A'), 100)));
    assert(!parseManifest(manifest("6.11.12", kUrl, std::string(64, 'z'), 100)));
    // Ukuran tidak masuk akal.
    assert(!parseManifest(manifest("6.11.12", kUrl, kSha, 0)));
    assert(!parseManifest(manifest("6.11.12", kUrl, kSha, -5)));
    assert(!parseManifest(manifest("6.11.12", kUrl, kSha, kMaxInstallerBytes + 1)));
    // Versi tidak terbaca.
    assert(!parseManifest(manifest("enam", kUrl, kSha, 100)));
    // Tidak ada bagian windows sama sekali.
    assert(!parseManifest("{\"schema\":2,\"version\":\"6.11.12\",\"apks\":{}}"));
}

void testUrlIsLockedToOfficialReleases() {
    assert(allowedUrl(kUrl));

    // Host lain, protokol lain, repo lain — semuanya ditolak.
    assert(!allowedUrl("http://github.com/xykal/XyDesk/releases/download/v1/XyDesk-x64.exe"));
    assert(!allowedUrl("https://github.com/jahat/XyDesk/releases/download/v1/XyDesk-x64.exe"));
    assert(!allowedUrl("https://githubXcom/xykal/XyDesk/releases/download/v1/a.exe"));
    assert(!allowedUrl("https://evil.example/XyDesk-x64.exe"));
    assert(!allowedUrl(""));
    assert(!allowedUrl(kAllowedPrefix));
    // Bukan installer.
    assert(!allowedUrl("https://github.com/xykal/XyDesk/releases/download/v1/XyDesk.apk"));
    // Trik jalur dan karakter aneh.
    assert(!allowedUrl("https://github.com/xykal/XyDesk/releases/download/../../a.exe"));
    assert(!allowedUrl("https://github.com/xykal/XyDesk/releases/download/v1/a.exe?x=1"));
    assert(!allowedUrl("https://github.com/xykal/XyDesk/releases/download/v1/a b.exe"));
    assert(!allowedUrl("https://github.com/xykal/XyDesk/releases/download/v1/a.exe\n"));

    // Manifes dengan URL terlarang ditolak seluruhnya, bukan sebagian.
    assert(!parseManifest(manifest("6.11.12", "https://evil.example/x.exe", kSha, 100)));
}

void testDecision() {
    Release found;
    const std::string baru = manifest("6.11.12", kUrl, kSha, 6162386, 2, 77);
    assert(decide("6.11.11+76", baru, &found) == Decision::Available);
    assert(found.version == "6.11.12");

    assert(decide("6.11.12+77", baru) == Decision::UpToDate);
    assert(decide("6.12.0+80", baru) == Decision::UpToDate);

    // Manifes rusak tidak pernah menjadi "sudah terbaru" diam-diam: ia
    // punya hasil sendiri supaya UI bisa jujur bilang gagal memeriksa.
    assert(decide("6.11.11+76", "{}") == Decision::Unreadable);
    assert(decide("bukan-versi", baru) == Decision::Unreadable);

    // Manifes yang menjanjikan versi lebih tinggi tetapi URL-nya asing
    // tidak boleh lolos sebagai pembaruan.
    const std::string palsu = manifest("9.9.9", "https://evil.example/x.exe", kSha, 100);
    assert(decide("6.11.11+76", palsu) == Decision::Unreadable);
}

void testHumanBytes() {
    assert(humanBytes(0) == "0 B");
    assert(humanBytes(512) == "512 B");
    assert(humanBytes(6162386) == "5,9 MB");
    assert(humanBytes(8454144) == "8,1 MB");
    assert(humanBytes(1536) == "1,5 kB");
    assert(humanBytes(-1) == "0 B");
}

void testInstallerName() {
    Release release;
    release.version = "6.11.12";
    assert(installerFileName(release) == "XyDesk-6.11.12-x64.exe");

    // Nama berkas tidak pernah mewarisi karakter dari jaringan.
    release.version = "6.11../..12";
    const std::string safe = installerFileName(release);
    assert(safe.find("..") == std::string::npos || safe.find('/') == std::string::npos);
    assert(safe.find('/') == std::string::npos);
    assert(safe.find('\\') == std::string::npos);

    release.version = "";
    assert(installerFileName(release) == "XyDesk-baru-x64.exe");
}

void testOfferText() {
    Release release;
    release.version = "6.11.12";
    release.bytes = 6162386;
    assert(offerText(release) == "XyDesk 6.11.12 tersedia (5,9 MB).");
}

}  // namespace

int main() {
    testVersionParsing();
    testCompare();
    testManifestHappyPath();
    testManifestRejections();
    testUrlIsLockedToOfficialReleases();
    testDecision();
    testHumanBytes();
    testInstallerName();
    testOfferText();
    std::printf("native-updater-test: semua pemeriksaan lulus\n");
    return 0;
}
