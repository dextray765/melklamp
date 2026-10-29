package com.dextray.melklamp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.dextray.melklamp.ui.MelkApp
import com.dextray.melklamp.ui.MelkTheme

class MainActivity : ComponentActivity() {
    private val vm: LampViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MelkTheme {
                MelkApp(vm)
            }
        }
    }
}
