#include <jni.h>

#include <algorithm>
#include <string>
#include <vector>

#include "streamxy.h"

// Jembatan tipis Kotlin ↔ libstreamxy. Semua logika ada di file C++ lain.
namespace {
constexpr size_t kMaxTextPayload = 32768;

jbyteArray wrap(JNIEnv* env, const uint8_t* buf, size_t n) {
  jbyteArray arr = env->NewByteArray(static_cast<jsize>(n));
  if (n > 0) {
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(n), reinterpret_cast<const jbyte*>(buf));
  }
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

JNIEXPORT jbyteArray JNICALL Java_id_xyverse_xydesk_core_StreamXy_bitrate(JNIEnv* env, jclass, jint mbps) {
  uint8_t b[4];
  const auto clamped = static_cast<uint16_t>(std::clamp<jint>(mbps, 0, 50));
  return wrap(env, b, sx_input_bitrate(b, clamped));
}

JNIEXPORT jbyteArray JNICALL Java_id_xyverse_xydesk_core_StreamXy_display(JNIEnv* env, jclass, jint index) {
  uint8_t b[4];
  return wrap(env, b, sx_input_display(b, static_cast<uint8_t>(index)));
}

JNIEXPORT jbyteArray JNICALL Java_id_xyverse_xydesk_core_StreamXy_resolution(JNIEnv* env, jclass, jint mode) {
  uint8_t b[4];
  return wrap(env, b, sx_input_resolution(b, static_cast<uint8_t>(mode)));
}

JNIEXPORT jbyteArray JNICALL Java_id_xyverse_xydesk_core_StreamXy_fps(JNIEnv* env, jclass, jint target_fps) {
  uint8_t b[4];
  return wrap(env, b, sx_input_fps(b, static_cast<uint8_t>(target_fps)));
}

JNIEXPORT jbyteArray JNICALL Java_id_xyverse_xydesk_core_StreamXy_text(JNIEnv* env, jclass, jstring s) {
  if (!s) return wrap(env, nullptr, 0);
  const char* utf8 = env->GetStringUTFChars(s, nullptr);
  const size_t len = std::min(static_cast<size_t>(env->GetStringUTFLength(s)), kMaxTextPayload);
  std::vector<uint8_t> b(len + 1);
  const size_t n = sx_input_text(b.data(), b.size(), utf8, len);
  env->ReleaseStringUTFChars(s, utf8);
  return wrap(env, b.data(), n);
}

JNIEXPORT jbyteArray JNICALL Java_id_xyverse_xydesk_core_StreamXy_clipboardSet(JNIEnv* env, jclass, jstring s) {
  if (!s) return wrap(env, nullptr, 0);
  const char* utf8 = env->GetStringUTFChars(s, nullptr);
  const size_t len = std::min(static_cast<size_t>(env->GetStringUTFLength(s)), kMaxTextPayload);
  std::vector<uint8_t> b(len + 1);
  const size_t n = sx_input_clipboard_set(b.data(), b.size(), utf8, len);
  env->ReleaseStringUTFChars(s, utf8);
  return wrap(env, b.data(), n);
}

JNIEXPORT jbyteArray JNICALL Java_id_xyverse_xydesk_core_StreamXy_clipboardReq(JNIEnv* env, jclass) {
  uint8_t b[2];
  return wrap(env, b, sx_input_clipboard_req(b));
}

JNIEXPORT jstring JNICALL Java_id_xyverse_xydesk_core_StreamXy_decodeClipboard(JNIEnv* env, jclass, jbyteArray packet) {
  if (!packet) return nullptr;
  const jsize len = env->GetArrayLength(packet);
  if (len <= 1) return nullptr;
  std::vector<uint8_t> buf(static_cast<size_t>(len));
  env->GetByteArrayRegion(packet, 0, len, reinterpret_cast<jbyte*>(buf.data()));
  size_t out_len = 0;
  const char* ptr = sx_clipboard_decode(buf.data(), buf.size(), &out_len);
  if (!ptr || out_len == 0) return nullptr;
  std::string text(ptr, out_len);
  return env->NewStringUTF(text.c_str());
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
