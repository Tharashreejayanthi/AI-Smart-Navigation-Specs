package com.innovista.smartglasses.hardware

/**
 * Commands that can be sent from the app to the Smart Glasses hardware.
 */
enum class HardwareCommand {
    START_CAMERA,
    STOP_CAMERA,
    ACTIVATE_NIGHT_VISION,
    DEACTIVATE_NIGHT_VISION,
    TRIGGER_SOS,
    CAPTURE_IMAGE,
    GET_SENSOR_DATA,
    PING
}
