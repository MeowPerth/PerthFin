package dev.jdtech.jellyfin.cast.dlna

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber

/** Acquires a Wi-Fi multicast lock for the duration of [close]. */
fun interface NetworkLock {
    fun acquire(): AutoCloseable
}

/**
 * SSDP discovery for UPnP MediaRenderers.
 *
 * Multiple M-SEARCH requests are sent because devices are allowed to drop requests while busy.
 * Responses are de-duplicated by USN and emitted as soon as they arrive so the device picker can
 * fill in progressively.
 */
class SsdpDiscovery(private val networkLock: NetworkLock = NetworkLock { AutoCloseable {} }) {

    fun discover(
        timeout: Duration = Duration.ofSeconds(4),
        targets: List<String> = listOf(SsdpMessages.MEDIA_RENDERER),
    ): Flow<SsdpResponse> = flow {
        val lock = runCatching { networkLock.acquire() }.getOrNull()
        val socket =
            try {
                DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(0))
                    soTimeout = SOCKET_POLL_TIMEOUT_MS
                    broadcast = true
                }
            } catch (e: Exception) {
                lock?.close()
                throw e
            }

        try {
            val group = InetAddress.getByName(SsdpMessages.MULTICAST_HOST)
            val seen = mutableSetOf<String>()
            val deadline = System.currentTimeMillis() + timeout.toMillis()

            var round = 0
            var nextSearchAt = 0L
            while (System.currentTimeMillis() < deadline) {
                val now = System.currentTimeMillis()
                if (round < SEARCH_ROUNDS && now >= nextSearchAt) {
                    for (target in targets) {
                        val message = SsdpMessages.search(target)
                        val bytes = message.toByteArray(Charsets.UTF_8)
                        runCatching {
                            socket.send(
                                DatagramPacket(
                                    bytes,
                                    bytes.size,
                                    group,
                                    SsdpMessages.MULTICAST_PORT,
                                )
                            )
                        }
                            .onFailure { Timber.w(it, "Failed to send M-SEARCH for $target") }
                    }
                    round++
                    nextSearchAt = now + SEARCH_INTERVAL_MS
                }

                val packet = DatagramPacket(ByteArray(MAX_PACKET_SIZE), MAX_PACKET_SIZE)
                try {
                    socket.receive(packet)
                } catch (_: java.net.SocketTimeoutException) {
                    continue
                } catch (e: Exception) {
                    Timber.w(e, "SSDP receive failed")
                    continue
                }

                val raw = String(packet.data, 0, packet.length, Charsets.UTF_8)
                val response = SsdpMessages.parseResponse(raw) ?: continue
                val searchTarget = response.searchTarget.orEmpty()
                if (
                    targets.none { searchTarget.contains(it, ignoreCase = true) } &&
                        !searchTarget.contains("MediaRenderer", ignoreCase = true)
                ) {
                    Timber.v("Ignoring SSDP response from ${packet.address}: $searchTarget")
                    continue
                }
                if (!seen.add(response.key)) continue

                emit(response)
            }

            // Give late responders a final chance without busy-waiting.
            delay(SEARCH_INTERVAL_MS)
        } finally {
            socket.close()
            lock?.close()
        }
    }
        .flowOn(Dispatchers.IO)

    private companion object {
        const val SEARCH_ROUNDS = 3
        const val SEARCH_INTERVAL_MS = 350L
        const val SOCKET_POLL_TIMEOUT_MS = 250
        const val MAX_PACKET_SIZE = 4096
    }
}
