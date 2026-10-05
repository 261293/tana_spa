package com.tana.spa.shared.helpers

data class DeviceCapabilities(
    val hasCamera: Boolean = false,
    val hasBiometrics: Boolean = false,
    val supportsDarkMode: Boolean = true,
)
