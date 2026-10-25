package com.tvtext.app

object ReceiverStatusTests {
    fun run() {
        val off = ReceiverStatus.resolve(true, true, false, false, false)
        check(off.titleKey == "accessibility_off_title" && !off.compact)
        val preparing = ReceiverStatus.resolve(false, true, false, false, false)
        check(preparing.messageKey == "preparing_message" && preparing.compact)
        val failed = ReceiverStatus.resolve(false, false, false, true, false)
        check(failed.titleKey == "reception_error_title" && !failed.compact)
        val pairing = ReceiverStatus.resolve(false, false, true, false, true)
        check(pairing.titleKey == "pairing_title" && pairing.tone == ReceiverStatus.Tone.PAIRING && !pairing.compact)
        val ready = ReceiverStatus.resolve(false, false, true, false, false)
        check(ready.messageKey == "ready_to_pair_title" && ready.tone == ReceiverStatus.Tone.READY && ready.compact)
        val priority = ReceiverStatus.resolve(true, false, true, true, true)
        check(priority.titleKey == "accessibility_off_title")
        println("PASS: receiver setup, preparation, failure, pairing and ready-state priority")
    }
}
