#include <jni.h>

#include <algorithm>
#include <cmath>
#include <cstdint>

// libxygamepad.so — skala analog ke XInput (i16 / trigger u8), bukan jaringan.
namespace {
int16_t axis(float v) {
  v = std::clamp(v, -1.f, 1.f);
  return static_cast<int16_t>(std::lround(v * 32767.f));
}
uint8_t trigger(float v) { return static_cast<uint8_t>(std::lround(std::clamp(v, 0.f, 1.f) * 255.f)); }
}  // namespace

extern "C" JNIEXPORT jint JNICALL Java_id_xyverse_xydesk_core_XyGamepad_axis(JNIEnv*, jclass, jfloat v) {
  return axis(v);
}

extern "C" JNIEXPORT jint JNICALL Java_id_xyverse_xydesk_core_XyGamepad_trigger(JNIEnv*, jclass, jfloat v) {
  return trigger(v);
}

extern "C" JNIEXPORT jint JNICALL Java_id_xyverse_xydesk_core_XyGamepad_xbit(JNIEnv*, jclass, jint keyCode) {
  switch (keyCode) {
    case 19: return 0x0001;   // DPAD_UP
    case 20: return 0x0002;   // DPAD_DOWN
    case 21: return 0x0004;   // DPAD_LEFT
    case 22: return 0x0008;   // DPAD_RIGHT
    case 108: return 0x0010;  // START
    case 109: return 0x0020;  // SELECT/BACK
    case 106: return 0x0040;  // THUMBL
    case 107: return 0x0080;  // THUMBR
    case 102: return 0x0100;  // L1
    case 103: return 0x0200;  // R1
    case 96: return 0x1000;   // A
    case 97: return 0x2000;   // B
    case 99: return 0x4000;   // X
    case 100: return 0x8000;  // Y
    default: return 0;
  }
}
