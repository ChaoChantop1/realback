package com.realback.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.realback.app.ui.DashboardScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RealBackApp()
        }
    }
}

@Composable
fun RealBackApp() {
    MaterialTheme {
        Surface {
            DashboardScreen()
        }
    }
}
