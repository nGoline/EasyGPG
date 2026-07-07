package com.ngoline.easygpg.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (Intent.ACTION_BOOT_COMPLETED == action) {
            Log.d("BootReceiver", "Device just booted.")
            // Optionally start a service or perform other actions
        } else if (Intent.ACTION_MY_PACKAGE_REPLACED == action) {
            Log.d("BootReceiver", "App has been updated or replaced.")
            // Optionally restart services or reinitialize something
        }
    }
}