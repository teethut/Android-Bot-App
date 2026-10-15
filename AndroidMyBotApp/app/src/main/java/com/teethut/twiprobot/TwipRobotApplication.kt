package com.teethut.twiprobot

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex

class TwipRobotApplication : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }
}

