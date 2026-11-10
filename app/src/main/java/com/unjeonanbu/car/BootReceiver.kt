package com.unjeonanbu.car

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 시동(부팅)이 끝나면 운전자가 앱을 열지 않아도 기록 서비스를 시작한다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            RecorderService.start(context, fromBoot = true)
        }
    }
}
