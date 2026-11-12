package com.lrlr.carmigo

import android.app.Application

class DaysApp : Application() {
    val controller: Controller by lazy { Controller(this) }

    override fun onCreate() {
        super.onCreate()
        controller.start()
    }
}
