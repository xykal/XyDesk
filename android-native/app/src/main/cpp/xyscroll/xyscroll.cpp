#include <jni.h>
#include <algorithm>

// libxyscroll.so — delta scroll overlay (pil geser), clamp ke rentang protokol.
extern "C" JNIEXPORT jint JNICALL
Java_id_xyverse_xydesk_core_XyScroll_delta(JNIEnv*, jclass, jfloat dragY) {
  const int dy = static_cast<int>(-dragY / 3.2f);
  return std::clamp(dy, -360, 360);
}
