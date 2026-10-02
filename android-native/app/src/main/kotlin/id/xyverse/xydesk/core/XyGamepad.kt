package id.xyverse.xydesk.core

object XyGamepad {
    init {
        System.loadLibrary("xygamepad")
    }

    external fun axis(v: Float): Int
    external fun trigger(v: Float): Int
    external fun xbit(keyCode: Int): Int
}
