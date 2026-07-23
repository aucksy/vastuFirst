package com.vastufirst.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.vastufirst.app.navigation.VastuNavHost
import com.vastufirst.designsystem.theme.VastuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VastuTheme {
                VastuNavHost()
            }
        }
    }
}
