package com.tana.spa

import androidx.compose.ui.window.ComposeUIViewController
import com.tana.spa.di.initKoin

fun MainViewController() = ComposeUIViewController(
    configure = { initKoin() },
) {
    App()
}
