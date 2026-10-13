package com.rishit.blemesh

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Minimal test UI: one log, one text box, one Send button. */
class MainActivity : Activity() {

    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }
        scroll = ScrollView(this)
        logView = TextView(this).apply { textSize = 16f }
        scroll.addView(logView)
        root.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        input = EditText(this).apply { hint = "Message" }
        val send = Button(this).apply {
            text = "Send"
            setOnClickListener { sendMessage() }
        }
        row.addView(input, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row.addView(send)
        root.addView(row)
        setContentView(root)

        ensurePermissionsThenStart()
    }

    override fun onResume() {
        super.onResume()
        logView.text = MeshHub.history.joinToString("\n")
        MeshHub.listener = { line ->
            logView.append(if (logView.text.isEmpty()) line else "\n$line")
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onPause() {
        MeshHub.listener = null
        super.onPause()
    }

    private fun sendMessage() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        val node = MeshHub.node
        if (node == null) {
            Toast.makeText(this, "Mesh not running (check permissions and Bluetooth)", Toast.LENGTH_SHORT).show()
            return
        }
        if (node.send(text)) {
            MeshHub.post("Me: $text")
            input.setText("")
        } else {
            Toast.makeText(this, "Too long. Max ${node.maxPayloadBytes} bytes on this phone.", Toast.LENGTH_LONG).show()
        }
    }

    // ---- permissions ----

    private fun criticalPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= 31) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun allPermissions(): List<String> =
        criticalPermissions() +
            if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()

    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun ensurePermissionsThenStart() {
        val missing = allPermissions().filterNot(::granted)
        if (missing.isEmpty()) startMesh() else requestPermissions(missing.toTypedArray(), REQ_PERMS)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code != REQ_PERMS) return
        if (criticalPermissions().all(::granted)) startMesh()
        else Toast.makeText(this, "Bluetooth permissions are required", Toast.LENGTH_LONG).show()
    }

    private fun startMesh() {
        val bt = getSystemService(BluetoothManager::class.java).adapter
        if (bt == null || !bt.isEnabled) {
            Toast.makeText(this, "Turn Bluetooth on, then reopen the app", Toast.LENGTH_LONG).show()
            return
        }
        startForegroundService(Intent(this, MeshService::class.java))
    }

    companion object {
        private const val REQ_PERMS = 1
    }
}
