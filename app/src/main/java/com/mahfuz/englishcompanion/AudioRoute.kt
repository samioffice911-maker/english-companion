package com.mahfuz.englishcompanion

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Decides where sound goes in and out:
 *  - Bluetooth earbuds with a mic connected (and allowed in Settings) -> use the earbud mic ("call" mode)
 *  - otherwise the phone mic.
 * Also tells whether the user hears the AI in headphones (so the mic can't hear the AI -> safe to interrupt).
 */
class AudioRoute(context: Context, private val onChange: () -> Unit) {
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val main = Handler(Looper.getMainLooper())

    /** true while the earbud microphone is in use */
    @Volatile var earbudMic = false
        private set

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) { changed("added") }
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) { changed("removed") }
    }
    private var lastSignature = ""
    private var started = false

    fun start() {
        started = true
        lastSignature = signature()
        am.registerAudioDeviceCallback(callback, main)
        apply()
    }

    fun stop() {
        started = false
        try { am.unregisterAudioDeviceCallback(callback) } catch (_: Exception) { }
        release()
    }

    private fun changed(why: String) {
        if (!started) return
        val sig = signature()
        if (sig == lastSignature) return
        lastSignature = sig
        DebugLog.event("devices_changed", "why" to why, "devices" to sig)
        // give Bluetooth a moment to settle, then re-route and restart the mic
        main.postDelayed({
            if (!started) return@postDelayed
            release()
            apply()
            onChange()
        }, 800)
    }

    /** Only "physical" connections count (not the call link we switch on ourselves), to avoid loops. */
    private fun signature(): String =
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .map { it.type }
            .filter {
                it == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    (Build.VERSION.SDK_INT >= 31 && it == AudioDeviceInfo.TYPE_BLE_HEADSET)
            }
            .distinct().sorted().joinToString(",")

    /** Turn on the earbud mic if possible. */
    @Suppress("DEPRECATION")
    fun apply() {
        earbudMic = false
        if (!Engine.prefs.useEarbudMic) { log("phone_mic_setting"); return }
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val dev = am.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                if (dev != null) {
                    am.mode = AudioManager.MODE_IN_COMMUNICATION
                    earbudMic = am.setCommunicationDevice(dev)
                    log(if (earbudMic) "earbud_mic" else "earbud_mic_failed", dev.productName?.toString())
                } else log("no_bt_mic")
            } else {
                if (am.isBluetoothScoAvailableOffCall && hasBluetoothHeadset()) {
                    am.mode = AudioManager.MODE_IN_COMMUNICATION
                    am.startBluetoothSco()
                    am.isBluetoothScoOn = true
                    earbudMic = true
                    log("earbud_mic_sco_legacy")
                } else log("no_bt_mic")
            }
        } catch (e: Exception) {
            earbudMic = false
            log("route_error", e.message)
        }
        if (!earbudMic) am.mode = AudioManager.MODE_NORMAL
    }

    @Suppress("DEPRECATION")
    private fun release() {
        try {
            if (Build.VERSION.SDK_INT >= 31) am.clearCommunicationDevice()
            else {
                am.isBluetoothScoOn = false
                am.stopBluetoothSco()
            }
            am.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) { }
        earbudMic = false
    }

    private fun hasBluetoothHeadset(): Boolean =
        am.getDevices(AudioManager.GET_DEVICES_INPUTS).any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }

    @Volatile private var hpCache = false
    @Volatile private var hpCheckedAt = 0L

    /** Is the AI's voice going into headphones/earbuds (not the phone speaker)? Cached for 2 s (called often). */
    fun headphonesOut(): Boolean {
        val now = System.currentTimeMillis()
        if (now - hpCheckedAt > 2000) { hpCache = computeHeadphones(); hpCheckedAt = now }
        return hpCache
    }

    private fun computeHeadphones(): Boolean {
        if (earbudMic) return true
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    }

    private fun log(route: String, extra: String? = null) {
        hpCheckedAt = 0L
        DebugLog.event("route", "route" to route, "device" to extra, "headphones" to computeHeadphones())
    }
}
