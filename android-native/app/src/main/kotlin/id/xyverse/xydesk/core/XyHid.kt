package id.xyverse.xydesk.core

/** libxyhid.so: deadzone stick. Dipakai overlay dan gamepad fisik. */
object XyHid {
    init {
        System.loadLibrary("xyhid")
    }

    /** [nx, ny, up, down, left, right] dengan up/down/left/right 0 atau 1. */
    external fun stick(x: Float, y: Float, dead: Float): FloatArray
}
