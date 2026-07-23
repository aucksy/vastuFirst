package com.vastufirst.app

import android.app.Application
import com.vastufirst.app.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class VastuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@VastuApp)
            modules(appModule)
        }
    }
}
