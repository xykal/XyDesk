#include <jni.h>

#include <algorithm>
#include <cmath>

// libxyhid.so — deadzone & arah stick untuk gamepad/overlay. Bukan protokol
// jaringan; libstreamxy tetap yang mengemas paket.

namespace {
void stick(float x, float y, float dead, float* out) {
  const float mag = std::sqrt(x * x + y * y);
  if (mag < dead) {
    out[0] = 0;
    out[1] = 0;
    out[2] = out[3] = out[4] = out[5] = 0;
    return;
  }
  const float s = (mag - dead) / (1.f - dead);
  out[0] = x / mag * std::min(s, 1.f);
  out[1] = y / mag * std::min(s, 1.f);
  out[2] = out[1] < -0.28f ? 1.f : 0.f;
  out[3] = out[1] > 0.28f ? 1.f : 0.f;
  out[4] = out[0] < -0.28f ? 1.f : 0.f;
  out[5] = out[0] > 0.28f ? 1.f : 0.f;
}
}  // namespace

extern "C" JNIEXPORT jfloatArray JNICALL
Java_id_xyverse_xydesk_core_XyHid_stick(JNIEnv* env, jclass, jfloat x, jfloat y, jfloat dead) {
  float o[6];
  stick(x, y, std::clamp(dead, 0.05f, 0.5f), o);
  jfloatArray a = env->NewFloatArray(6);
  env->SetFloatArrayRegion(a, 0, 6, o);
  return a;
}
