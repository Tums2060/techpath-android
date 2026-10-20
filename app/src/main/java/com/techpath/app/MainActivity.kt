package com.techpath.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.techpath.app.ui.TechPathRoot
import com.techpath.app.ui.theme.TechPathTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as TechPathApp).container
        setContent {
            TechPathTheme {
                TechPathRoot(container)
            }
        }
    }
}
