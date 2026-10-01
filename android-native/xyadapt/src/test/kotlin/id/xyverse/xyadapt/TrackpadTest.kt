package id.xyverse.xyadapt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadTest {
    private fun pad(): Pair<Trackpad, MutableList<Act>> {
        val acts = mutableListOf<Act>()
        return Trackpad { acts += it } to acts
    }

    @Test fun tapIsLeftClick() {
        val (p, a) = pad()
        p.down(0, 10f, 10f); p.up(100)
        assertEquals(listOf<Act>(Act.Click(0)), a)
    }

    @Test fun holdIsRightClick() {
        val (p, a) = pad()
        p.down(0, 10f, 10f); p.tick(500); p.up(600)
        assertEquals(listOf(Act.Haptic, Act.Click(1)), a)
    }

    @Test fun twoFingerTapIsRightClick() {
        val (p, a) = pad()
        p.down(0, 10f, 10f); p.pointerDown(); p.pointerUp(); p.up(120)
        assertEquals(listOf<Act>(Act.Click(1)), a)
    }

    @Test fun twoFingerMoveScrolls() {
        val (p, a) = pad()
        p.down(0, 10f, 10f); p.pointerDown(); p.move(16, 10f, 60f); p.up(200)
        assertTrue(a.any { it is Act.Scroll && it.dy < 0 })
        assertTrue(a.none { it is Act.Click })
    }

    @Test fun doubleTapHoldDrags() {
        val (p, a) = pad()
        p.down(0, 0f, 0f); p.up(80)
        p.down(200, 0f, 0f); p.move(230, 40f, 40f); p.up(600)
        assertEquals(Act.Button(0, true), a[1])
        assertEquals(Act.Button(0, false), a.last())
    }

    @Test fun adaptiveStepsDownThenUp() {
        val v = AdaptiveVideo()
        repeat(3) { v.sample(10.0, 300.0, 5.0, 60) }
        assertEquals(1, v.index)
        repeat(8) { v.sample(60.0, 20.0, 3.0, 60) }
        assertEquals(2, v.index)
    }
}

class ExtraTest {
    @Test fun pinchZoomsOut() {
        val acts = mutableListOf<Act>()
        val p = Trackpad { acts += it }
        p.down(0, 0f, 0f); p.pointerDown()
        p.pinch(100f); p.pinch(130f); p.pinch(160f)
        assertTrue(acts.filterIsInstance<Act.Zoom>().sumOf { it.steps } >= 2)
    }

    @Test fun threeFingerSwipeRight() {
        val acts = mutableListOf<Act>()
        val p = Trackpad { acts += it }
        p.down(0, 0f, 0f); p.pointerDown(); p.pointerDown()
        p.move(20, 60f, 0f); p.move(40, 120f, 0f); p.up(100)
        assertEquals(Act.Swipe3(1), acts.last { it is Act.Swipe3 })
        assertTrue(acts.none { it is Act.Click })
    }

    @Test fun networkScoreGrades() {
        val s = NetworkScore()
        repeat(5) { s.push(60.0, 30.0) }
        assertEquals(Grade.BAGUS, s.grade(60))
        repeat(5) { s.push(12.0, 300.0) }
        assertEquals(Grade.BURUK, s.grade(60))
    }

    @Test fun reconnectBackoff() {
        val r = ReconnectPolicy(maxAttempts = 3)
        assertEquals(1000L, r.nextDelayMs()); assertEquals(2000L, r.nextDelayMs()); assertEquals(4000L, r.nextDelayMs())
        assertEquals(null, r.nextDelayMs()); assertTrue(r.exhausted)
        r.reset(); assertEquals(1000L, r.nextDelayMs())
    }
}

class TraceTest {
    @Test
    fun traceCompactsAndRoundTrips() {
        val t = SessionTrace(buckets = 10)
        repeat(100) { t.push(60.0 - it % 5, 40.0 + it % 7) }
        val pts = SessionTrace.decode(t.encode())
        assertTrue(pts.size in 1..10)
        assertTrue(pts.all { it.first in 55..60 && it.second in 40..46 })
        assertEquals(100, t.size)
        assertTrue(t.avgFps() in 57.0..59.0)
    }

    @Test
    fun emptyTraceEncodesEmpty() {
        assertEquals("", SessionTrace().encode())
        assertTrue(SessionTrace.decode("").isEmpty())
        assertTrue(SessionTrace.decode("x,1:2,:").size == 1)
    }

    @Test
    fun semverCompares() {
        assertTrue(Semver.newer("6.10.0", "6.9.0+63"))
        assertTrue(Semver.newer("v7.0.0", "6.9.9"))
        assertTrue(!Semver.newer("6.9.0", "6.9.0+63"))
        assertTrue(!Semver.newer("6.8.12", "6.9.0"))
    }
}
