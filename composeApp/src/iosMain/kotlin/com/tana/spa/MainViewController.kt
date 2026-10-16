package com.tana.spa

import androidx.compose.ui.window.ComposeUIViewController
import com.tana.spa.di.initKoin

// ContentView.swift calls MainViewControllerKt.MainViewController(), so the name is the
// interop contract rather than a style choice.
@Suppress("ktlint:standard:function-naming")
fun MainViewController() = ComposeUIViewController(
    configure = { initKoin() },
) {
    App()
}
