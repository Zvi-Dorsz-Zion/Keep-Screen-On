package com.keepscreenon.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * רץ כל עוד המתג דלוק ונבחרה אפליקציה. כשהמסך דלוק הוא בודק מה פתוח, וכשאפליקציה שנבחרה על המסך
 * הוא מחזיק נעילת מסך. כשהמסך כבוי לא רצה שום בדיקה, ולחיצה על כפתור ההפעלה מכבה כרגיל.
 * כל העבודה (חוץ מ-onStartCommand) נעשית על התהליכון של handler.
 */
class KeepOnService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var power: PowerManager
    private lateinit var usage: UsageStatsManager
    private lateinit var lock: PowerManager.WakeLock

    private val tracker = ResumedTracker()
    private var lastEventTime = 0L
    private var lockTakenAt = 0L
    private var misses = 0

    @Volatile
    private var keepingFor: String? = null

    private val tick = Runnable { step() }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handler.removeCallbacks(tick)
            if (intent.action == Intent.ACTION_SCREEN_ON) {
                refreshHomePackages()
                step()
            } else {
                keep(null)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION") // אין תחליף לשמירת מסך דלוק מתוך שירות
    override fun onCreate() {
        super.onCreate()
        power = getSystemService(POWER_SERVICE) as PowerManager
        usage = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        lock = power.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
            "KeepScreenOn:screen"
        ).apply { setReferenceCounted(false) }

        thread = HandlerThread("keep-screen-on").apply { start() }
        handler = Handler(thread.looper)
        handler.post { refreshHomePackages() }

        createChannel()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter, null, handler)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // חובה לקרוא לזה לפני הכול, גם אם מיד עוצרים, אחרת המערכת מפילה את האפליקציה
        try {
            val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(keepingFor), type)
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Prefs.shouldRun(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        handler.removeCallbacksAndMessages(null)
        handler.post {
            if (lock.isHeld) lock.release()
            thread.quitSafely()
        }
        super.onDestroy()
    }

    private fun step() {
        if (!power.isInteractive) {
            keep(null) // אין תזמון הבא: ACTION_SCREEN_ON יפעיל מחדש
            return
        }
        val hit = try {
            readEvents()
            val selected = Prefs.selected(this)
            tracker.visiblePackages().firstOrNull { it in selected }
        } catch (_: Exception) {
            null
        }
        when {
            hit != null -> {
                misses = 0
                keep(hit)
            }
            // מעבר בין מסכים בתוך אותה אפליקציה נראה לרגע כאילו שום דבר לא פתוח, ולכן לא משחררים מיד
            keepingFor != null && ++misses < MISSES_BEFORE_RELEASE -> keep(keepingFor)
            else -> {
                misses = 0
                keep(null)
            }
        }
        handler.postDelayed(tick, POLL_MS)
    }

    private fun readEvents() {
        val now = System.currentTimeMillis()
        if (lastEventTime > now) { // השעון של המכשיר חזר אחורה
            lastEventTime = 0
            tracker.reset()
        }
        val from = if (lastEventTime == 0L) now - FIRST_READ_MS else lastEventTime
        val events = usage.queryEvents(from, now + 1) ?: return
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            tracker.onEvent(event.packageName, event.className, event.eventType)
            if (event.timeStamp > lastEventTime) lastEventTime = event.timeStamp
        }
    }

    private fun refreshHomePackages() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        tracker.homePackages = packageManager.queryIntentActivities(home, 0)
            .mapTo(HashSet()) { it.activityInfo.packageName }
    }

    /** מקבל את האפליקציה שבגללה משאירים דלוק, או null כדי לשחרר. */
    private fun keep(packageName: String?) {
        if (packageName != null) {
            val now = SystemClock.elapsedRealtime()
            // נעילה עם תפוגה, ומחדשים אותה הרבה לפני שהיא נגמרת
            if (!lock.isHeld || now - lockTakenAt > LOCK_MS / 2) {
                lock.acquire(LOCK_MS)
                lockTakenAt = now
            }
        } else if (lock.isHeld) {
            lock.release()
        }
        if (packageName != keepingFor) {
            keepingFor = packageName
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification(packageName))
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "מצב המסך", NotificationManager.IMPORTANCE_LOW)
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(packageName: String?): Notification {
        val open = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (packageName != null) {
            "המסך נשאר דלוק בגלל " + labelOf(packageName)
        } else {
            "ממתין לאפליקציות שבחרת"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(pending)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun labelOf(packageName: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (_: Exception) {
        packageName
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "status"
        private const val POLL_MS = 1500L
        private const val MISSES_BEFORE_RELEASE = 2
        private const val LOCK_MS = 30 * 60 * 1000L
        private const val FIRST_READ_MS = 24 * 60 * 60 * 1000L

        /** מפעיל או עוצר את השירות לפי ההגדרות הנוכחיות. */
        fun sync(context: Context) {
            val intent = Intent(context, KeepOnService::class.java)
            try {
                if (Prefs.shouldRun(context)) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.stopService(intent)
                }
            } catch (_: Exception) {
                // המערכת סירבה להפעיל מהרקע. בפתיחת האפליקציה ננסה שוב
            }
        }
    }
}
