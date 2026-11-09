package br.edu.uniconnect

import android.app.Application
import android.webkit.WebView

class UniConnectApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (getProcessName().endsWith(":automation")) WebView.setDataDirectorySuffix("automation")
    }
}
