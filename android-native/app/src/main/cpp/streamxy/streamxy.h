#pragma once
#include <cstddef>
#include <cstdint>

// API C murni: stabil untuk JNI, FFI Dart, maupun Rust (host) di masa depan.
extern "C" {

const char* sx_version();

// ── Protokol input (cermin host/src/input.rs) ──────────────────────────
// Setiap fungsi menulis ke `out` (kapasitas >= 16 byte, kecuali text/clipboard)
// dan mengembalikan jumlah byte, 0 bila gagal.
size_t sx_input_move_rel(uint8_t* out, int16_t dx, int16_t dy);
size_t sx_input_move_abs(uint8_t* out, float nx, float ny);
size_t sx_input_button(uint8_t* out, uint8_t button, bool down);
size_t sx_input_scroll(uint8_t* out, int16_t dx, int16_t dy);
size_t sx_input_key(uint8_t* out, uint16_t vk, bool down);
size_t sx_input_text(uint8_t* out, size_t cap, const char* utf8, size_t len);
size_t sx_input_display(uint8_t* out, uint8_t index);
size_t sx_input_clipboard_set(uint8_t* out, size_t cap, const char* utf8, size_t len);
size_t sx_input_clipboard_req(uint8_t* out);
size_t sx_input_quality(uint8_t* out, uint8_t preset);
size_t sx_input_bitrate(uint8_t* out, uint16_t mbps);
size_t sx_input_resolution(uint8_t* out, uint8_t mode);
size_t sx_input_fps(uint8_t* out, uint8_t fps);
size_t sx_input_gamepad(uint8_t* out, uint16_t buttons, uint8_t lt, uint8_t rt, int16_t lx,
                        int16_t ly, int16_t rx, int16_t ry);

// Dekode pesan 0x08 CLIPBOARD_SET yang masuk dari host; mengembalikan
// pointer ke payload UTF-8 di dalam `packet` (dan menulis panjangnya ke `out_len`),
// atau nullptr bila bukan pesan clipboard.
const char* sx_clipboard_decode(const uint8_t* packet, size_t packet_len, size_t* out_len);

// ── Telemetri latensi ──────────────────────────────────────────────────
// Ring buffer sampel (ms). Thread-safe untuk satu penulis + satu pembaca.
struct sx_stats;
sx_stats* sx_stats_new(size_t capacity);
void sx_stats_free(sx_stats*);
void sx_stats_push(sx_stats*, float ms);
// Persentil 0..100 dari sampel yang ada; NaN bila kosong.
float sx_stats_percentile(const sx_stats*, float p);
size_t sx_stats_count(const sx_stats*);
}
