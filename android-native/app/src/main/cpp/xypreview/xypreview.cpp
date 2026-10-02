#include <jni.h>
#include <cstdint>

// libxypreview.so — validasi JPEG wallpaper (FF D8 … FF D9), tanpa decode.
extern "C" JNIEXPORT jboolean JNICALL
Java_id_xyverse_xydesk_core_XyPreview_jpegOk(JNIEnv* env, jclass, jbyteArray arr) {
  if (!arr) return JNI_FALSE;
  const jsize n = env->GetArrayLength(arr);
  if (n < 4 || n > 350000) return JNI_FALSE;
  jbyte buf[4];
  env->GetByteArrayRegion(arr, 0, 2, buf);
  if (static_cast<uint8_t>(buf[0]) != 0xFF || static_cast<uint8_t>(buf[1]) != 0xD8) return JNI_FALSE;
  env->GetByteArrayRegion(arr, n - 2, 2, buf);
  if (static_cast<uint8_t>(buf[0]) != 0xFF || static_cast<uint8_t>(buf[1]) != 0xD9) return JNI_FALSE;
  return JNI_TRUE;
}
