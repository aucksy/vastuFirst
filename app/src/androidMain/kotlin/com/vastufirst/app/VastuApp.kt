package com.vastufirst.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.vastufirst.app.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import java.lang.ref.WeakReference

class VastuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // ⭐ FIRST, before anything else can throw. A crash during startup is exactly the crash worth
        // knowing about, and installing the recorder after Koin would miss every one of them.
        CrashLog.install(this, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        registerActivityLifecycleCallbacks(CurrentActivity)
        startKoin {
            androidLogger()
            androidContext(this@VastuApp)
            modules(appModule)
        }
    }
}

/**
 * The activity currently in front, held WEAKLY.
 *
 * Google Play's checkout has to be launched from a real Activity, and the thing that launches it is
 * a repository-shaped singleton with no view of the UI. A strong reference here would keep a
 * destroyed Activity — and its whole window — alive for as long as the process lives, which is the
 * textbook Android memory leak.
 */
object CurrentActivity : Application.ActivityLifecycleCallbacks {
    private var ref: WeakReference<Activity>? = null

    /** The Activity in front, or null (backgrounded, or mid-rotation). Callers must handle null. */
    fun get(): Activity? = ref?.get()

    override fun onActivityResumed(activity: Activity) { ref = WeakReference(activity) }
    override fun onActivityPaused(activity: Activity) {
        if (ref?.get() === activity) ref = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) {
        if (ref?.get() === activity) ref = null
    }
}
