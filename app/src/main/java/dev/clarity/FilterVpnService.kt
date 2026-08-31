package dev.clarity

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

/**
 * DNS filtering VPN. It captures only DNS traffic (everything else uses the
 * normal network untouched), inspects the requested hostname, and either
 * forwards the query to a real resolver or answers NXDOMAIN to kill it.
 *
 * This is how we block video hosts (canvaz.scdn.co, googlevideo.com, ...) while
 * letting Spotify audio and normal app traffic through. We can only act on
 * hostnames, not payloads, so it is domain filtering, not content filtering.
 *
 * Set the phone's DNS to us by advertising 10.111.0.3 as the DNS server and
 * routing only that address into the tunnel.
 */
class FilterVpnService : VpnService() {

    private var tunnel: ParcelFileDescriptor? = null
    @Volatile private var running = false
    private var worker: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            teardown()
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundNotice()
        if (!running) bringUp()
        return START_STICKY
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun bringUp() {
        val fd = Builder()
            .setSession("Clarity filter")
            .addAddress(TUN_ADDR, 32)
            .addDnsServer(DNS_PROXY)
            .addRoute(DNS_PROXY, 32)   // only DNS to us; all other traffic untouched
            .setBlocking(true)
            .establish() ?: return
        tunnel = fd
        running = true
        worker = Thread({ pump(fd) }, "clarity-dns").also { it.start() }
    }

    private fun teardown() {
        running = false
        worker?.interrupt()
        worker = null
        try { tunnel?.close() } catch (_: Exception) {}
        tunnel = null
    }

    private fun pump(fd: ParcelFileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buffer = ByteArray(32767)
        while (running) {
            val len = try { input.read(buffer) } catch (e: Exception) { break }
            if (len <= 0) continue
            try { handle(buffer, len, output) } catch (e: Exception) {
                Log.w(TAG, "packet handling failed", e)
            }
        }
    }

    /** Parse one captured IPv4/UDP/53 packet and respond. */
    private fun handle(packet: ByteArray, len: Int, out: FileOutputStream) {
        if (len < 28) return
        val version = (packet[0].toInt() ushr 4) and 0xF
        if (version != 4) return
        val ihl = (packet[0].toInt() and 0xF) * 4
        if (packet[9].toInt() and 0xFF != 17) return          // not UDP
        val udp = ihl
        val dstPort = u16(packet, udp + 2)
        if (dstPort != 53) return

        val dnsStart = udp + 8
        val dnsLen = len - dnsStart
        if (dnsLen < 12) return

        val host = parseQueryName(packet, dnsStart)
        val blocked = host != null && DomainRules.isBlocked(host)
        Log.d(DNS_TAG, (if (blocked) "BLOCK " else "allow ") + (host ?: "?"))

        val response = if (blocked) {
            nxdomain(packet, dnsStart, dnsLen)
        } else {
            resolveUpstream(packet, dnsStart, dnsLen) ?: return
        }

        // Wrap DNS response in a fresh IPv4+UDP packet with src/dst swapped.
        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)
        val srcPort = u16(packet, udp)
        val reply = buildUdpIpv4(
            srcIp = dstIp,      // from the DNS server
            dstIp = srcIp,      // back to the app
            srcPort = 53,
            dstPort = srcPort,
            payload = response,
        )
        out.write(reply)
        out.flush()
    }

    private fun resolveUpstream(packet: ByteArray, dnsStart: Int, dnsLen: Int): ByteArray? {
        val query = packet.copyOfRange(dnsStart, dnsStart + dnsLen)
        DatagramSocket().use { socket ->
            protect(socket)               // keep this query off the tunnel
            socket.soTimeout = 4000
            socket.connect(InetSocketAddress(UPSTREAM_DNS, 53))
            socket.send(DatagramPacket(query, query.size))
            val in0 = ByteArray(4096)
            val resp = DatagramPacket(in0, in0.size)
            return try {
                socket.receive(resp)
                in0.copyOf(resp.length)
            } catch (e: java.net.SocketTimeoutException) {
                null
            }
        }
    }

    // ---- DNS helpers -------------------------------------------------------

    private fun parseQueryName(p: ByteArray, dnsStart: Int): String? {
        var i = dnsStart + 12                 // skip 12 byte DNS header
        val sb = StringBuilder()
        while (i < p.size) {
            val labelLen = p[i].toInt() and 0xFF
            if (labelLen == 0) break
            if (labelLen and 0xC0 != 0) return null  // compression pointer, not in a question
            i++
            if (i + labelLen > p.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (j in 0 until labelLen) sb.append((p[i + j].toInt() and 0xFF).toChar())
            i += labelLen
        }
        return if (sb.isEmpty()) null else sb.toString()
    }

    /** Build an NXDOMAIN response echoing the question section. */
    private fun nxdomain(p: ByteArray, dnsStart: Int, dnsLen: Int): ByteArray {
        // find end of question: name + null byte + qtype(2) + qclass(2)
        var i = dnsStart + 12
        while (i < dnsStart + dnsLen) {
            val l = p[i].toInt() and 0xFF
            if (l == 0) { i++; break }
            i += l + 1
        }
        val questionEnd = (i + 4).coerceAtMost(dnsStart + dnsLen)
        val size = questionEnd - dnsStart
        val r = p.copyOfRange(dnsStart, dnsStart + size)
        // flags: QR=1, keep RD, RA=1, RCODE=3 (NXDOMAIN)
        r[2] = (0x80 or (r[2].toInt() and 0x01)).toByte()
        r[3] = 0x83.toByte()
        // counts: QD=1 (unchanged), AN=NS=AR=0
        r[6] = 0; r[7] = 0
        r[8] = 0; r[9] = 0
        r[10] = 0; r[11] = 0
        return r
    }

    private fun buildUdpIpv4(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray,
    ): ByteArray {
        val udpLen = 8 + payload.size
        val total = 20 + udpLen
        val b = ByteArray(total)
        // IPv4 header
        b[0] = 0x45                                   // version 4, IHL 5
        b[1] = 0
        b[2] = (total ushr 8).toByte(); b[3] = total.toByte()
        b[4] = 0; b[5] = 0                            // id
        b[6] = 0x40; b[7] = 0                         // don't fragment
        b[8] = 64                                     // TTL
        b[9] = 17                                     // UDP
        // checksum (10,11) computed below
        System.arraycopy(srcIp, 0, b, 12, 4)
        System.arraycopy(dstIp, 0, b, 16, 4)
        val ipChecksum = checksum(b, 0, 20)
        b[10] = (ipChecksum ushr 8).toByte(); b[11] = ipChecksum.toByte()
        // UDP header
        b[20] = (srcPort ushr 8).toByte(); b[21] = srcPort.toByte()
        b[22] = (dstPort ushr 8).toByte(); b[23] = dstPort.toByte()
        b[24] = (udpLen ushr 8).toByte(); b[25] = udpLen.toByte()
        b[26] = 0; b[27] = 0                          // UDP checksum optional in IPv4
        System.arraycopy(payload, 0, b, 28, payload.size)
        return b
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) sum += (data[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun u16(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    private fun startForegroundNotice() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, "Clarity filter", NotificationManager.IMPORTANCE_MIN)
            nm.createNotificationChannel(ch)
        }
        val n: Notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("Clarity active")
            .setContentText("Video and distraction filtering on")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, n)
    }

    companion object {
        private const val TAG = "ClarityVpn"
        const val DNS_TAG = "ClarityDNS"
        const val ACTION_STOP = "dev.clarity.STOP_VPN"

        private const val TUN_ADDR = "10.111.0.2"
        private const val DNS_PROXY = "10.111.0.3"
        private const val UPSTREAM_DNS = "1.1.1.1"

        private const val CHANNEL = "clarity_filter"
        private const val NOTIF_ID = 42

        fun start(context: Context) {
            val i = Intent(context, FilterVpnService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, FilterVpnService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
