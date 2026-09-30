#include <jni.h>

#include "streamxy.h"

// Jembatan tipis Kotlin ↔ libstreamxy. Semua logika ada di file C++ lain.
namespace {
jbyteArray wrap(JNIEnv* env, const uint8_t* buf, size_t n) {
  jbyteArray arr = env->NewByteArray(static_cast<jsize>(n));
  env->SetByteArrayRegion(arr, 0, static_cast<jsize>(n), reinterpret_cast<const jbyte*>(buf));
  return arr;
}
}  // namespace

extern "C" {

JNIEXPORT jstring JNICALL Java_id_xyverse_xydesk_core_StreamXy_version(JNIEnv* env, jclass) {
  return env->NewStringUTF(sx_version());
}

JNIEXPORT jbyteArray JNICALL Java_id_xyverse_xydesk_core_StreamXy_moveRel(JNIEnv* env, jclass, jint dx, jint dy) {
  uint8_t b[8];
  return wrap(env, b, sx_input_move_rel(b, static_cast<int16_t>(dx), static_cast<int16_t>(dy)));
}

JNIEXPORT jbyteArray JNICALL Java_id_xyverse_xydesk_core_StreamXy_moveAbs(JNIEnv* env, jclass, jfloat x, jfloat y) {
  uint8_t b[8];
  return wrap(env, b, sx_input_move_abs(b, x, y));
}

JNIEXPORT jbyteArray JNICALL Java_id_xyverse_xydesk_core_StreamXy_button(JNIEnv* env, jclass, jint button, jboolean down) {
  uint8_t b[8];
  return wrap(env, b, sx_input_button(b, static_cast<uint8_t>(button), down));
}

JNIEXPORT jbyteArray JNICALL Java_id_xyverse_xydesk_core_StreamXy_scroll(JNIEnv* env, jclass, jint dx, jint dy) {
  uint8_t b[8];
  return wrap(env, b, sx_input_scroll(b, static_cast<int16_t>(dx), static_cast<int16_t>(dy)));
}

JNIEXPORT jbyteArray JNICALL Java_id_xyverse_xydesk_core_StreamXy_key(JNIEnv* env, jclass, jint vk, jboolean down) {
  uint8_t b[8];
  return wrap(env, b, sx_input_key(b, static_cast<uint16_t>(vk), down));
}

JNIEXPORT jbyteArray JNICALL Java_id_xyverse_xydesk_core_StreamXy_quality(JNIEnv* env, jclass, jint preset) {
  uint8_t b[4];
  return wrap(env, b, sx_input_quality(b, static_cast<uint8_t>(preset)));
}

JNIEXPORT jbyteArray JNICALL Java_id_xyverse_xydesk_core_StreamXy_text(JNIEnv* env, jclass, jstring s) {
  const char* utf8 = env->GetStringUTFChars(s, nullptr);
  const size_t len = static_cast<size_t>(env->GetStringUTFLength(s));
  uint8_t b[2048];
  const size_t n = sx_input_text(b, sizeof b, utf8, len);
  env->ReleaseStringUTFChars(s, utf8);
  return wrap(env, b, n);
}

JNIEXPORT jlong JNICALL Java_id_xyverse_xydesk_core_StreamXy_statsNew(JNIEnv*, jclass, jint cap) {
  return reinterpret_cast<jlong>(sx_stats_new(static_cast<size_t>(cap)));
}

JNIEXPORT void JNICALL Java_id_xyverse_xydesk_core_StreamXy_statsFree(JNIEnv*, jclass, jlong h) {
  sx_stats_free(reinterpret_cast<sx_stats*>(h));
}

JNIEXPORT void JNICALL Java_id_xyverse_xydesk_core_StreamXy_statsPush(JNIEnv*, jclass, jlong h, jfloat ms) {
  sx_stats_push(reinterpret_cast<sx_stats*>(h), ms);
}

JNIEXPORT jfloat JNICALL Java_id_xyverse_xydesk_core_StreamXy_statsPercentile(JNIEnv*, jclass, jlong h, jfloat p) {
  return sx_stats_percentile(reinterpret_cast<sx_stats*>(h), p);
}
}
