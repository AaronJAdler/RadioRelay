package com.radiorelay

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.radiorelay.databinding.ActivityMainBinding
import com.radiorelay.databinding.ItemStationBinding
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs
    private val rows = mutableListOf<ItemStationBinding>()
    private var previewCommands: List<SendCommand> = emptyList()
    private var updatingSwitch = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshStatus() }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            Prefs.K_LOG -> renderLog()
            Prefs.K_RECENT -> renderRecentChats()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)

        b.editChat.setText(prefs.chatFilter)
        b.editCooldown.setText(prefs.cooldownMinutes.toString())
        prefs.stations.forEach { addStationRow(it) }
        if (rows.isEmpty()) addStationRow(null)

        b.switchEnabled.setOnCheckedChangeListener { _, checked -> if (!updatingSwitch) onToggle(checked) }
        b.btnAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        b.btnSms.setOnClickListener { requestPermissions() }
        b.btnNotif.setOnClickListener { requestPermissions() }
        b.btnAppInfo.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            )
        }
        b.btnAddStation.setOnClickListener { addStationRow(null) }
        b.btnSave.setOnClickListener { if (saveSettings()) toast("Saved") }
        b.btnPreview.setOnClickListener { preview() }
        b.btnSendNow.setOnClickListener { confirmSendNow() }
        b.btnClearLog.setOnClickListener { prefs.clearLog() }
    }

    override fun onResume() {
        super.onResume()
        prefs.registerListener(prefsListener)
        refreshStatus()
        renderRecentChats()
        renderLog()
    }

    override fun onPause() {
        prefs.unregisterListener(prefsListener)
        super.onPause()
    }

    // ---------------- On / off ----------------

    private fun onToggle(checked: Boolean) {
        if (!checked) {
            prefs.enabled = false
            refreshStatus()
            return
        }
        val problem = when {
            !saveSettings() -> "Fix the highlighted settings first."
            prefs.chatFilter.isBlank() -> "Enter (or tap) the group chat name first."
            !listenerEnabled() -> "Turn on notification access first."
            !hasPermission(Manifest.permission.SEND_SMS) -> "Allow SMS first."
            else -> null
        }
        if (problem != null) {
            setSwitch(false)
            toast(problem)
            return
        }
        prefs.armedAt = System.currentTimeMillis()
        prefs.enabled = true
        refreshStatus()
    }

    private fun refreshStatus() {
        val access = listenerEnabled()
        val sms = hasPermission(Manifest.permission.SEND_SMS)

        b.textAccess.text = if (access) "✓ Notification access is on" else "Notification access — lets the app read the group chat"
        b.btnAccess.visibility = if (access) View.GONE else View.VISIBLE

        b.textSms.text = if (sms) "✓ Can send texts" else "SMS — lets the app text the station lines"
        b.btnSms.visibility = if (sms) View.GONE else View.VISIBLE

        if (Build.VERSION.SDK_INT >= 33) {
            val notif = hasPermission(Manifest.permission.POST_NOTIFICATIONS)
            b.rowNotif.visibility = View.VISIBLE
            b.textNotif.text = if (notif) "✓ Will notify you when it texts" else "Notifications (optional) — a heads-up each time it texts"
            b.btnNotif.visibility = if (notif) View.GONE else View.VISIBLE
        } else {
            b.rowNotif.visibility = View.GONE
        }

        val needsHelp = !access || !sms
        b.textRestrictedHint.visibility = if (needsHelp) View.VISIBLE else View.GONE
        b.btnAppInfo.visibility = if (needsHelp) View.VISIBLE else View.GONE

        setSwitch(prefs.enabled)
        b.textState.text = when {
            !prefs.enabled -> "Off. Nothing will be texted."
            !access -> "On, but notification access is off — it can't see the chat."
            !sms -> "On, but SMS permission is missing — it can't send."
            else -> "Watching chats named “${prefs.chatFilter}”. New entries like “98... Minnie” get texted automatically."
        }
    }

    private fun setSwitch(on: Boolean) {
        updatingSwitch = true
        b.switchEnabled.isChecked = on
        updatingSwitch = false
    }

    private fun listenerEnabled() =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun hasPermission(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        val wanted = mutableListOf(Manifest.permission.SEND_SMS)
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        permissionLauncher.launch(wanted.toTypedArray())
    }

    // ---------------- Chat picker ----------------

    private fun renderRecentChats() {
        val chats = prefs.recentChats()
        b.chipsRecent.removeAllViews()
        b.textRecentHint.text = if (chats.isEmpty()) {
            "Once notification access is on, chats that message you appear here — tap the group to pick it."
        } else {
            "Recently seen chats — tap to use:"
        }
        chats.forEach { title ->
            val chip = Chip(this).apply {
                text = title
                setOnClickListener {
                    b.editChat.setText(title)
                    prefs.chatFilter = title
                    refreshStatus()
                    toast("Watching “$title”")
                }
            }
            b.chipsRecent.addView(chip)
        }
    }

    // ---------------- Stations ----------------

    private fun addStationRow(station: Station?) {
        val row = ItemStationBinding.inflate(layoutInflater, b.stationsContainer, false)
        station?.let {
            row.editPrefix.setText(it.prefix)
            row.editNumber.setText(PhoneUtil.format(it.number))
        }
        row.btnRemove.setOnClickListener {
            b.stationsContainer.removeView(row.root)
            rows.remove(row)
        }
        rows += row
        b.stationsContainer.addView(row.root)
    }

    /** Reads the station rows; returns null (and highlights the problem) if any are invalid. */
    private fun readStations(): List<Station>? {
        val out = mutableListOf<Station>()
        var allValid = true
        for (row in rows) {
            val prefix = row.editPrefix.text?.toString()?.trim().orEmpty()
            val number = PhoneUtil.normalize(row.editNumber.text?.toString().orEmpty())
            row.layoutPrefix.error = null
            row.layoutNumber.error = null
            if (prefix.isEmpty() && number.isEmpty()) continue

            val prefixOk = MessageParser.isValidPrefix(prefix)
            val numberOk = PhoneUtil.isValid(number)
            if (!prefixOk) row.layoutPrefix.error = "e.g. 98"
            if (!numberOk) row.layoutNumber.error = "10-digit number"
            if (prefixOk && numberOk) out += Station(prefix, number) else allValid = false
        }
        return if (allValid) out else null
    }

    private fun saveSettings(): Boolean {
        val stations = readStations() ?: return false
        val cooldown = b.editCooldown.text?.toString()?.trim()?.toIntOrNull()
        if (cooldown == null || cooldown < 0) {
            b.layoutCooldown.error = "Enter minutes, e.g. 5"
            return false
        }
        b.layoutCooldown.error = null
        prefs.stations = stations
        prefs.cooldownMinutes = cooldown
        prefs.chatFilter = b.editChat.text?.toString().orEmpty()
        refreshStatus()
        return true
    }

    // ---------------- Try a message ----------------

    private fun preview() {
        val stations = readStations() ?: run {
            toast("Fix the station list first.")
            return
        }
        previewCommands = MessageParser.parse(b.editTest.text?.toString().orEmpty(), stations)
        b.textPreview.text = if (previewCommands.isEmpty()) {
            "Nothing would be sent. Each line needs to look like “98... Minnie” and start with one of your station numbers."
        } else {
            previewCommands.joinToString("\n") { "→ Text “${it.keyword}” to ${PhoneUtil.format(it.station.number)}" }
        }
        b.btnSendNow.visibility = if (previewCommands.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun confirmSendNow() {
        if (!hasPermission(Manifest.permission.SEND_SMS)) {
            requestPermissions()
            return
        }
        val commands = previewCommands
        if (commands.isEmpty()) return
        MaterialAlertDialogBuilder(this)
            .setTitle("Send ${commands.size} text${if (commands.size == 1) "" else "s"}?")
            .setMessage(commands.joinToString("\n") { "“${it.keyword}” → ${PhoneUtil.format(it.station.number)}" })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Send") { _, _ ->
                Relay.dispatch(this, prefs, "Sent by hand", commands, respectCooldown = false)
            }
            .show()
    }

    // ---------------- Log ----------------

    private fun renderLog() {
        val entries = prefs.log()
        if (entries.isEmpty()) {
            b.textLog.text = "Nothing texted yet."
            return
        }
        val timeFormat = DateFormat.getTimeFormat(this)
        b.textLog.text = entries.joinToString("\n\n") { e ->
            "${timeFormat.format(Date(e.time))}   “${e.keyword}” → ${PhoneUtil.format(e.number)}\n${e.status}  ·  ${e.chat}"
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
