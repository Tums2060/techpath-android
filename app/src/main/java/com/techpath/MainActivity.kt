package com.techpath

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import com.techpath.ui.components.LoadingState
import com.techpath.ui.navigation.TechPathNavGraph
import com.techpath.ui.theme.TechPathTheme
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsFlow = (application as TechPathApp).container.user.settings
        setContent {
            TechPathTheme {
                // Read once: the start destination must not change after the NavHost is created.
                val startOnboarding by produceState<Boolean?>(null) { value = !settingsFlow.first().onboarded }
                Surface(Modifier.fillMaxSize()) {
                    when (val start = startOnboarding) {
                        null -> LoadingState()
                        else -> TechPathNavGraph(startOnboarding = start)
                    }
                }
            }
        }
    }
}
