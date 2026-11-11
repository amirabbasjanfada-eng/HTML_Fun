package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.ViewModelProvider
import com.example.ui.MainScreen
import com.example.ui.MainViewModel
import com.example.ui.theme.WebPaperTheme
import com.example.ui.theme.resolveDark

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this)[MainViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = resolveDark(viewModel.themeMode)

            // Keep the status/navigation bar icons readable when the in-app theme differs from
            // the system theme.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(
                        Color.argb(0xE6, 0xFF, 0xFF, 0xFF),
                        Color.argb(0x80, 0x1B, 0x1B, 0x1B)
                    ) { dark }
                )
                onDispose { }
            }

            WebPaperTheme(darkTheme = dark) {
                MainScreen(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have set (or changed) the live wallpaper in the system picker.
        viewModel.refreshLive()
    }
}
