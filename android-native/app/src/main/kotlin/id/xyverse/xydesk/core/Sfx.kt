package id.xyverse.xydesk.core

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import id.xyverse.xydesk.R

/** Efek suara pendek UI (bundel di res/raw, tanpa jaringan). */
class Sfx(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build())
        .build()
    private val confirm = pool.load(context, R.raw.sfx_confirm, 1)

    fun confirm() = pool.play(confirm, 1f, 1f, 1, 0, 1f)

    fun release() = pool.release()
}
