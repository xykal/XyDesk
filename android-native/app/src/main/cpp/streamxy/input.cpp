#include "streamxy.h"

#include <algorithm>
#include <cstring>

namespace {
enum Tag : uint8_t {
  kMoveRel = 0x01,
  kMoveAbs = 0x02,
  kButton = 0x03,
  kScroll = 0x04,
  kKey = 0x05,
  kText = 0x06,
  kQuality = 0x0A,
};

inline void put16(uint8_t* p, uint16_t v) {
  p[0] = static_cast<uint8_t>(v & 0xff);
  p[1] = static_cast<uint8_t>(v >> 8);
}
}  // namespace

extern "C" {

const char* sx_version() { return "streamxy 0.1.0"; }

size_t sx_input_move_rel(uint8_t* out, int16_t dx, int16_t dy) {
  out[0] = kMoveRel;
  put16(out + 1, static_cast<uint16_t>(dx));
  put16(out + 3, static_cast<uint16_t>(dy));
  return 5;
}

size_t sx_input_move_abs(uint8_t* out, float nx, float ny) {
  const auto clamp = [](float v) { return static_cast<uint16_t>(std::clamp(v, 0.f, 1.f) * 65535.f); };
  out[0] = kMoveAbs;
  put16(out + 1, clamp(nx));
  put16(out + 3, clamp(ny));
  return 5;
}

size_t sx_input_button(uint8_t* out, uint8_t button, bool down) {
  out[0] = kButton;
  out[1] = button;
  out[2] = down ? 1 : 0;
  return 3;
}

size_t sx_input_scroll(uint8_t* out, int16_t dx, int16_t dy) {
  out[0] = kScroll;
  put16(out + 1, static_cast<uint16_t>(dx));
  put16(out + 3, static_cast<uint16_t>(dy));
  return 5;
}

size_t sx_input_key(uint8_t* out, uint16_t vk, bool down) {
  out[0] = kKey;
  put16(out + 1, vk);
  out[3] = down ? 1 : 0;
  return 4;
}

size_t sx_input_text(uint8_t* out, size_t cap, const char* utf8, size_t len) {
  if (cap < len + 1) return 0;
  out[0] = kText;
  std::memcpy(out + 1, utf8, len);
  return len + 1;
}

size_t sx_input_quality(uint8_t* out, uint8_t preset) {
  out[0] = kQuality;
  out[1] = preset;
  return 2;
}

size_t sx_input_display(uint8_t* out, uint8_t index) {
  out[0] = 0x07;
  out[1] = index;
  return 2;
}

size_t sx_input_resolution(uint8_t* out, uint8_t mode) {
  out[0] = 0x0C;
  out[1] = mode;
  return 2;
}
}
