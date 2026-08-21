package de.nielstron.simplenextcloud.data

import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkAddressTest {
    @Test
    fun `recognizes addresses protected by Android local network access`() {
        listOf(
            "192.168.0.34",
            "10.5.32.1",
            "172.16.0.1",
            "169.254.1.1",
            "100.64.0.1",
            "127.0.0.1",
            "224.0.0.1",
            "fc00::1",
            "fe80::1",
        ).forEach { address ->
            assertTrue(address, InetAddress.getByName(address).isLocalNetworkAddress())
        }
    }

    @Test
    fun `does not classify public addresses as local`() {
        listOf("1.1.1.1", "100.63.255.255", "100.128.0.1", "2606:4700:4700::1111").forEach { address ->
            assertFalse(address, InetAddress.getByName(address).isLocalNetworkAddress())
        }
    }
}
