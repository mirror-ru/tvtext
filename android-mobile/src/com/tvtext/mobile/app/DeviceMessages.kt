package com.tvtext.mobile.app

/** Holds transient status text independently from screen construction and rendering. */
internal data class DeviceMessage(val key: String, val error: Boolean = false, val detail: String? = null)

internal class DeviceMessages {
    private val byDevice = mutableMapOf<String, DeviceMessage>()
    private var list: DeviceMessage? = null

    fun forDevice(id: String, fallback: DeviceMessage): DeviceMessage = byDevice[id] ?: fallback

    fun listMessage(): DeviceMessage? = list

    fun set(message: DeviceMessage, target: String, deviceIds: Set<String>) {
        if (target.isNotEmpty() && target in deviceIds) byDevice[target] = message
        else list = message
    }

    fun clear(target: String? = null) {
        if (target == null) {
            byDevice.clear()
            list = null
        } else {
            byDevice.remove(target)
        }
    }
}
