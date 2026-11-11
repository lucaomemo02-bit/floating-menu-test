package com.example.floatingmenu

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Tela inicial: concede a permissão de sobreposição e liga/desliga o menu flutuante.
 */
class MainActivity : Activity() {

    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusView = findViewById(R.id.tv_status)
        findViewById<Button>(R.id.btn_permission).setOnClickListener { openOverlaySettings() }
        findViewById<Button>(R.id.btn_start).setOnClickListener { startMenu() }
        findViewById<Button>(R.id.btn_stop).setOnClickListener { stopMenu() }

        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus() // o usuário pode ter voltado das Configurações
    }

    private fun startMenu() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.toast_need_permission, Toast.LENGTH_LONG).show()
            openOverlaySettings()
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, FloatingMenuService::class.java))
        // Manda o app para segundo plano para o menu aparecer sobre a tela anterior.
        moveTaskToBack(true)
    }

    private fun stopMenu() {
        stopService(Intent(this, FloatingMenuService::class.java))
        statusView.postDelayed(::refreshStatus, STATUS_REFRESH_DELAY_MS)
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun refreshStatus() {
        val permission = getString(
            if (Settings.canDrawOverlays(this)) R.string.status_perm_ok else R.string.status_perm_no
        )
        val service = getString(
            if (FloatingMenuService.isRunning) R.string.status_service_on else R.string.status_service_off
        )
        statusView.text = "$permission\n$service"
    }

    private companion object {
        const val REQUEST_NOTIFICATIONS = 1
        const val STATUS_REFRESH_DELAY_MS = 300L
    }
}
