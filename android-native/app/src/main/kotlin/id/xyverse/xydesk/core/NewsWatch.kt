package id.xyverse.xydesk.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import id.xyverse.xydesk.R
import id.xyverse.xydesk.ui.MainActivity
import java.util.concurrent.TimeUnit

/** Cek berita baru tiap ~6 jam di latar; satu notifikasi per artikel yang belum pernah dilihat. */
class NewsWatch(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val store = Store(applicationContext)
        val latest = runCatching { News.list(limit = 1).firstOrNull() }.getOrNull() ?: return Result.retry()
        if (latest.slug == store.newsSeen || latest.slug == store.newsNotified) return Result.success()
        store.newsNotified = latest.slug
        notify(applicationContext, latest)
        return Result.success()
    }

    private fun notify(ctx: Context, p: NewsPost) {
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Berita XyDesk", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).putExtra("tab", "news").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify).setContentTitle(p.title).setContentText(p.excerpt)
            .setStyle(NotificationCompat.BigTextStyle().bigText(p.excerpt)).setContentIntent(open).setAutoCancel(true).build()
        nm.notify(p.slug.hashCode(), n)
    }

    companion object {
        private const val CHANNEL = "news"

        fun cancel(ctx: Context) = WorkManager.getInstance(ctx).cancelUniqueWork("news-watch")

        fun schedule(ctx: Context) {
            if (!Store(ctx).newsNotify) return
            val req = PeriodicWorkRequestBuilder<NewsWatch>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("news-watch", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
