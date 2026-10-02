package id.xyverse.xydesk.core

object XyPreview {
    init {
        System.loadLibrary("xypreview")
    }

    external fun jpegOk(bytes: ByteArray): Boolean
}
