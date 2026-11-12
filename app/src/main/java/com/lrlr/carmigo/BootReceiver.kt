package com.lrlr.carmigo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 시동 뒤(또는 앱 업데이트 뒤) 화면을 열지 않아도 기록 서비스가 뜨게 한다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> RecorderService.start(context)
        }
    }
}
