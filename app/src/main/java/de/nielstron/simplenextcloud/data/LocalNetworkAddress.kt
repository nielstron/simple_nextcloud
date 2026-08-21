package de.nielstron.simplenextcloud.data

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

internal fun InetAddress.isLocalNetworkAddress(): Boolean {
    if (isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress || isMulticastAddress) {
        return true
    }

    val octets = address.map(Byte::toInt).map { it and 0xff }
    return when (this) {
        is Inet4Address ->
            octets[0] == 100 && octets[1] in 64..127 || // Shared address space (CGNAT)
                octets.all { it == 255 }
        is Inet6Address -> octets[0] and 0xfe == 0xfc // Unique local addresses (fc00::/7)
        else -> false
    }
}
