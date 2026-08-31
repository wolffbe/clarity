package dev.clarity

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Status console. In a release build it is purely informational: there is no
 * on-phone way to relax the lockdown. The only reset is a full wipe from a
 * computer (see README). The debug build adds a lift button for development.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private var btBtn: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()

        val title = TextView(this).apply {
            text = "Clarity"
            textSize = 26f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, pad, 0, 0)
        }
        status = TextView(this).apply {
            textSize = 15f
            setPadding(0, pad, 0, pad)
        }
        val enforceBtn = Button(this).apply {
            text = "Enforce now"
            setOnClickListener { refresh(PolicyEnforcer.enforce(this@MainActivity)) }
        }
        val bt = Button(this).apply { setOnClickListener { toggleBluetooth() } }
        btBtn = bt
        val simBtn = Button(this).apply {
            text = "Mobile data & SIM"
            setOnClickListener { openMobileSettings() }
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(title)
            addView(status)
            addView(bt)
            addView(simBtn)
            addView(enforceBtn)
        }

        if (BuildConfig.DEBUG) {
            column.addView(Button(this).apply {
                text = "Lift all (debug only)"
                setOnClickListener {
                    try { stopLockTask() } catch (_: Exception) {}
                    PolicyEnforcer.liftAll(this@MainActivity)
                    refresh(null)
                }
            })
        }

        setContentView(ScrollView(this).apply { addView(column) })

        maybeRequestVpnConsent()
        if (PolicyEnforcer.isDeviceOwner(this)) {
            EnforcementJob.schedule(this)
            refresh(PolicyEnforcer.enforce(this))
        } else {
            refresh(null)
        }
    }

    override fun onResume() {
        super.onResume()
        updateBtLabel()
    }

    private fun bluetoothAdapter(): android.bluetooth.BluetoothAdapter? =
        (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    @Suppress("DEPRECATION", "MissingPermission")
    private fun toggleBluetooth() {
        val a = bluetoothAdapter() ?: return
        // enable()/disable() are deprecated for normal apps but still work for a
        // device owner, so no Settings screen is needed.
        try { if (a.isEnabled) a.disable() else a.enable() } catch (_: Exception) {}
        updateBtLabel()
        btBtn?.postDelayed({ updateBtLabel() }, 800)
    }

    @Suppress("MissingPermission")
    private fun updateBtLabel() {
        val a = bluetoothAdapter()
        btBtn?.text = when {
            a == null -> "Bluetooth: unavailable"
            a.isEnabled -> "Bluetooth: ON (tap to turn off)"
            else -> "Bluetooth: OFF (tap to turn on)"
        }
    }

    /** Opens the mobile data / SIM panel. Works because Settings is allowed to
     *  run in lock task (but is kept off the home grid). */
    private fun openMobileSettings() {
        val panel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        } else {
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        }
        try {
            startActivity(panel)
        } catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) } catch (_: Exception) {}
        }
    }

    private fun maybeRequestVpnConsent() {
        val intent = VpnService.prepare(this) ?: return
        startActivityForResult(intent, REQ_VPN)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VPN && resultCode == RESULT_OK) FilterVpnService.start(this)
    }

    private fun refresh(result: EnforceResult?) {
        updateBtLabel()
        val owner = PolicyEnforcer.isDeviceOwner(this)
        status.text = buildString {
            if (!owner) {
                append("Not active yet.\n\n")
                append("From a computer, on a phone with NO accounts added:\n\n")
                append("adb shell dpm set-device-owner dev.clarity/.ClarityAdminReceiver\n\n")
                append("Then reopen this app.")
                return@buildString
            }
            append("LOCKDOWN ACTIVE\n\n")
            append("Mode: whitelist (only allowed apps visible)\n")
            append("Home: Clarity launcher, recents removed (kiosk)\n")
            append("Status bar / shade / quick settings: disabled\n")
            append("Play Store & general Settings: off the grid\n")
            append("WiFi: blocked (cellular only)\n")
            append("Bluetooth & mobile SIM: managed via buttons above\n")
            append("Accessibility & keyboards: third party barred\n")
            append("Lock screen camera & widgets: off\n")
            append("adb / USB debugging: disabled\n")
            append("Safe boot: disabled\n")
            append("Installs: blocked\n")
            append("Uninstall / force stop / clear data: blocked\n")
            append("Factory reset from phone: blocked\n")
            append("Filtering VPN: always on, video hosts blocked\n")
            append("Mobile data: kept on; cannot be turned off on device\n")
            append("Link taps: dead end (no browser)\n\n")
            append("No on-phone off switch. Only reset is a full wipe from\n")
            append("a computer, which erases everything.")
            if (result != null) {
                append("\n\nAllowed apps present (").append(result.kept.size).append("), ")
                append("hidden (").append(result.hidden.size).append(").")
            }
        }
    }

    companion object {
        private const val REQ_VPN = 1001
    }
}
