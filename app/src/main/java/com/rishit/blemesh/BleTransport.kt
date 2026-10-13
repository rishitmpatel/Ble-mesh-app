package com.rishit.blemesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Sends and receives Packets using BLE advertising and scanning only.
 * No GATT connections, so there is no connection-count limit.
 * Uses extended advertising when the phone supports it, legacy otherwise.
 * Everything runs on the main thread.
 */
@SuppressLint("MissingPermission")
class BleTransport(
    context: Context,
    private val onPacket: (Packet) -> Unit,
) {
    private val adapter: BluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Packet>()
    private var advertising = false
    private var scanning = false

    val extendedSupported: Boolean get() = adapter.isLeExtendedAdvertisingSupported

    /** Max payload bytes (after header and the 4-byte manufacturer-data wrapper). */
    val maxPayloadBytes: Int
        get() {
            val maxData = if (extendedSupported) minOf(adapter.leMaximumAdvertisingDataLength, 250) else 31
            return (maxData - 4 - Packet.HEADER).coerceAtLeast(0)
        }

    fun send(p: Packet) {
        handler.post {
            queue.addLast(p)
            pump()
        }
    }

    private fun pump() {
        if (advertising) return
        val p = queue.removeFirstOrNull() ?: return
        val advertiser = adapter.bluetoothLeAdvertiser ?: return
        advertising = true

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addManufacturerData(COMPANY_ID, p.toBytes())
            .build()

        if (extendedSupported) {
            val params = AdvertisingSetParameters.Builder()
                .setLegacyMode(false)
                .setConnectable(false)
                .setScannable(false)
                .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
                .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
                .build()
            val cb = object : AdvertisingSetCallback() {}
            advertiser.startAdvertisingSet(params, data, null, null, null, cb)
            handler.postDelayed({
                advertiser.stopAdvertisingSet(cb)
                advertising = false
                pump()
            }, ADV_HOLD_MS)
        } else {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .build()
            val cb = object : AdvertiseCallback() {}
            advertiser.startAdvertising(settings, data, cb)
            handler.postDelayed({
                advertiser.stopAdvertising(cb)
                advertising = false
                pump()
            }, ADV_HOLD_MS)
        }
    }

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val raw = result.scanRecord?.getManufacturerSpecificData(COMPANY_ID) ?: return
            Packet.fromBytes(raw)?.let(onPacket)
        }
    }

    fun startScan() {
        if (scanning) return
        val scanner = adapter.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder()
            .setManufacturerData(COMPANY_ID, byteArrayOf())
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setLegacy(false) // also receive extended adverts
            .build()
        scanner.startScan(listOf(filter), settings, scanCb)
        scanning = true
    }

    fun stop() {
        if (scanning) {
            adapter.bluetoothLeScanner?.stopScan(scanCb)
            scanning = false
        }
        queue.clear()
        handler.removeCallbacksAndMessages(null)
        advertising = false
    }

    companion object {
        /** How long each packet is advertised before the next one in the queue. */
        const val ADV_HOLD_MS = 1500L
    }
}
