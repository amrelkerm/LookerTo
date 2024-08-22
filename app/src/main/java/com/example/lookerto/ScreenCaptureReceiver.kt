package com.example.lookerto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle

class ScreenCaptureReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val captureIntent = Intent(context, ScreenCaptureService::class.java).apply {
            putExtras(intent.extras ?: Bundle())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(captureIntent)
        } else {
            context.startService(captureIntent)
        }
    }
}
