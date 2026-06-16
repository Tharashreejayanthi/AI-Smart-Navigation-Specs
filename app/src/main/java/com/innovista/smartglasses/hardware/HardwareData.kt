package com.innovista.smartglasses.hardware

/**
 * Data packets received from the Smart Glasses hardware.
 */
data class HardwareData(
    val type: String,
    val payload: String,
    val timestamp: Long
)
