package com.keepscreenon.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** מחזיר את השירות לפעולה אחרי הפעלה מחדש של המכשיר ואחרי עדכון של האפליקציה. */
class StartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        KeepOnService.sync(context)
    }
}
