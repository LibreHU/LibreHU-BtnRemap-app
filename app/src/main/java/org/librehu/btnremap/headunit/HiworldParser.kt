package org.librehu.btnremap.headunit

/**
 * Stream decoder for the Hiworld CAN box protocol (bytes relayed by the MCU in its 0x10 frames, possibly split):
 * `5A A5 LEN CMD D0..Dn-1 CS`, LEN = n, CS = ((LEN + CMD + D0 + .. + Dn-1) & 0xFF) - 1.
 * Reference: LibreHU-service docs/ivi-services/12-canbus.md (Renault LNP002 decoder of com.can.activity).
 */
class HiworldParser(
    private val onFrame: (cmd: Int, frame: ByteArray) -> Unit,
) {
    private val buf = ByteArray(512)
    private var len = 0

    fun feed(bytes: ByteArray) {
        for (b in bytes) {
            if (len == buf.size) len = 0
            buf[len++] = b
            scan()
        }
    }

    private fun scan() {
        // Resynchronise on 5A A5.
        while (len >= 1 && u(0) != 0x5A) shift(1)
        if (len >= 2 && u(1) != 0xA5) {
            shift(1)
            return
        }
        if (len < 4) return
        val total = u(2) + 5
        if (len < total) return
        var sum = 0
        for (i in 2 until total - 1) sum += u(i)
        if (((sum - 1) and 0xFF) == u(total - 1)) onFrame(u(3), buf.copyOf(total))
        shift(total)
    }

    private fun u(i: Int) = buf[i].toInt() and 0xFF

    private fun shift(n: Int) {
        System.arraycopy(buf, n, buf, 0, len - n)
        len -= n
    }
}
