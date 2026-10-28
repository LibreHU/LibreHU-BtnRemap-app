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
    /** A click on each tap, the sound chosen in LibreHU Launcher (see KeyClick). */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        org.librehu.btnremap.ui.KeyClick.onTouch(this, ev, org.librehu.btnremap.ui.RetroLook.clickSound)
        return super.dispatchTouchEvent(ev)
    }

    private val theme =
        lazy {
            ThemeFollower(this) { dark, accent ->
                CarColors.palette =
                    CarPalette.of(
                        dark,
                        if (accent != 0) {
                            androidx.compose.ui.graphics
                                .Color(accent)
                        } else {
                            null
                        },
                    )
            }
        }

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
