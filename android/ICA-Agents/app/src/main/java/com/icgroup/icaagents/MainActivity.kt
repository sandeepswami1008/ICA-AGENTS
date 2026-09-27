package com.icgroup.icaagents

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.icgroup.icaagents.data.AgentRepository
import com.icgroup.icaagents.data.ApiClient
import com.icgroup.icaagents.ui.AppRoot
import com.icgroup.icaagents.ui.ViewModelFactory
import com.icgroup.icaagents.ui.theme.IcaAgentsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as IcaAgentsApp
        val session = app.session
        val dmsApi = ApiClient.buildDmsApi(session)
        val emsApi = ApiClient.buildEmsApi(session)
        val repo = AgentRepository(dmsApi, emsApi, session)
        val factory = ViewModelFactory(repo)

        setContent {
            IcaAgentsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(factory = factory, session = session)
                }
            }
        }
    }
}
