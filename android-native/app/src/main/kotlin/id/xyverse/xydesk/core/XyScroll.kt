package id.xyverse.xydesk.core

object XyScroll {
    init {
        System.loadLibrary("xyscroll")
    }

    external fun delta(dragY: Float): Int
}
