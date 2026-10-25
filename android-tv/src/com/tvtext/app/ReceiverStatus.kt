package com.tvtext.app

/** Receiver state translated into resource keys; no Android views or styling here. */
data class ReceiverStatus(val titleKey: String, val messageKey: String, val tone: Tone, val compact: Boolean) {
    enum class Tone { ATTENTION, PAIRING, READY }

    companion object {
        fun resolve(accessibilityOff: Boolean, missingService: Boolean, listening: Boolean,
                    hasError: Boolean, pairing: Boolean): ReceiverStatus {
            val compact = !pairing && !accessibilityOff && !hasError
            return when {
                accessibilityOff -> ReceiverStatus("accessibility_off_title", "accessibility_off_message", Tone.ATTENTION, compact)
                missingService -> ReceiverStatus("status_caption", "preparing_message", Tone.ATTENTION, compact)
                hasError -> ReceiverStatus("reception_error_title", "reception_error_message", Tone.ATTENTION, compact)
                listening && pairing -> ReceiverStatus("pairing_title", "pairing_hint", Tone.PAIRING, compact)
                listening -> ReceiverStatus("status_caption", "ready_to_pair_title", Tone.READY, compact)
                else -> ReceiverStatus("status_caption", "preparing_message", Tone.ATTENTION, compact)
            }
        }
    }
}
