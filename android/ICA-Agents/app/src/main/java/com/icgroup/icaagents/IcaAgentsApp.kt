package com.icgroup.icaagents

import android.app.Application
import com.icgroup.icaagents.data.SessionStore

class IcaAgentsApp : Application() {
    lateinit var session: SessionStore
        private set

    override fun onCreate() {
        super.onCreate()
        session = SessionStore(this)
    }
}
