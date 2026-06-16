package com.example.service

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.data.ShieldDatabase
import com.example.data.ShieldRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SafeVpnService : VpnService() {

    private val vpnJob = SupervisorJob()
    private val vpnScope = CoroutineScope(Dispatchers.IO + vpnJob)
    private var vpnInterface: ParcelFileDescriptor? = null
    private lateinit var repository: ShieldRepository

    override fun onCreate() {
        super.onCreate()
        val db = ShieldDatabase.getDatabase(applicationContext)
        repository = ShieldRepository(db.shieldDao)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "START_VPN") {
            startVpn()
        } else if (action == "STOP_VPN") {
            stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return
        try {
            val builder = Builder()
                .setSession("kidsShiield VPN Shield")
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.3") // Cloudflare Family DNS (blocks adult sites natively)
                .setBlocking(false)

            vpnInterface = builder.establish()
            Log.d("SafeVpnService", "VPN Established and DNS routing active.")

            vpnScope.launch {
                repository.setSetting("vpn_active", "true")
                repository.logAlert(
                    appName = "Shield Engine",
                    detectedType = "DNS VPN Filter",
                    contentSnippet = "Local DNS Shield activated. Cloudflare Family DNS (1.1.1.3) routing adult content filters.",
                    status = "ALLOWED"
                )
            }
        } catch (e: Exception) {
            Log.e("SafeVpnService", "Failed to start VPN interface", e)
        }
    }

    private fun stopVpn() {
        try {
            vpnInterface?.close()
            vpnInterface = null
            vpnScope.launch {
                repository.setSetting("vpn_active", "false")
                repository.logAlert(
                    appName = "Shield Engine",
                    detectedType = "DNS VPN Filter",
                    contentSnippet = "DNS filtering suspended by parent command",
                    status = "ALLOWED"
                )
            }
        } catch (e: Exception) {
            Log.e("SafeVpnService", "Error stopping VPN Interface", e)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        vpnJob.cancel()
    }
}
