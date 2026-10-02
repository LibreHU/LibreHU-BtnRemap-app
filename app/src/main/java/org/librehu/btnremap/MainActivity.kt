package org.librehu.btnremap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.librehu.btnremap.ui.CarColors
import org.librehu.btnremap.ui.CarPalette
import org.librehu.btnremap.ui.CarTheme
import org.librehu.btnremap.ui.RemapScreen
import org.librehu.btnremap.ui.ThemeFollower

class MainActivity : ComponentActivity() {
    private val theme = lazy { ThemeFollower(this) { dark, _ -> CarColors.palette = CarPalette.of(dark) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        theme.value.start()
        RemapService.start(this)
        setContent { CarTheme { RemapScreen() } }
    }

    override fun onDestroy() {
        theme.value.stop()
        super.onDestroy()
    }
}
