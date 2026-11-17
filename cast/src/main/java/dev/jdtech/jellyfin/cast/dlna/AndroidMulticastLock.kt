package dev.jdtech.jellyfin.cast.dlna

import android.content.Context
import android.net.wifi.WifiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Wi-Fi multicast filters are often enabled on Android, which silently drops SSDP replies. Holding
 * a multicast lock keeps them flowing for the short discovery window.
 */
class AndroidMulticastLock @Inject constructor(@ApplicationContext context: Context) : NetworkLock {
    private val applicationContext = context.applicationContext

    override fun acquire(): AutoCloseable {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifiManager?.createMulticastLock("perthfin-dlna")
        lock?.setReferenceCounted(false)
        runCatching { lock?.acquire() }
        return AutoCloseable { runCatching { lock?.release() } }
    }
}
