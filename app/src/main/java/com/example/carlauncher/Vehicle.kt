package com.example.carlauncher
/** Single source of truth for vehicle data. OBD service writes, UI reads via onChange. */
object Vehicle {
    @Volatile var speed = 0; @Volatile var rpm = 0; @Volatile var coolant = -99; @Volatile var volts = 0f
    @Volatile var obdConnected = false; @Volatile var dtc: List<String> = emptyList()
    @Volatile var onChange: (() -> Unit)? = null
    fun post() { onChange?.invoke() }
}
