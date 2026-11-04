package de.nielstron.simplenextcloud.data

import de.nielstron.simplenextcloud.ServerNotifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException

class ServerNotificationsTest {
    private val body = """
        {"ocs":{"meta":{"status":"ok","statuscode":200,"message":"OK"},"data":[
          {"notification_id":3,"app":"updatenotification","subject":"Update for Calendar","message":"","link":"https://cloud.example.com/settings/apps"},
          {"notification_id":7,"app":"files_sharing","subject":"Ana shared Holiday with you","message":"Open it","link":""}
        ]}}
    """.trimIndent()

    @Test
    fun `parses notifications newest first`() {
        val client = NextcloudClient()
        val parsed = client.parseNotifications(client.parseOcs(200, "OK", body))
        assertEquals(listOf(7L, 3L), parsed.map(ServerNotification::id))
        assertEquals("Ana shared Holiday with you", parsed[0].subject)
        assertEquals(null, parsed[0].link)
        assertEquals("https://cloud.example.com/settings/apps", parsed[1].link)
    }

    @Test
    fun `first check shows nothing, later checks only newer ones`() {
        val all = listOf(7L, 3L).map { ServerNotification(it, "", "", "", null) }
        assertEquals(emptyList<ServerNotification>(), ServerNotifications.newSince(null, all))
        assertEquals(listOf(7L), ServerNotifications.newSince(3L, all).map(ServerNotification::id))
        assertEquals(emptyList<ServerNotification>(), ServerNotifications.newSince(7L, all))
    }

    @Test
    fun `recognises untrusted certificates`() {
        val untrusted = SSLHandshakeException("Trust anchor for certification path not found").apply {
            initCause(CertificateException(CertPathValidatorException("Trust anchor for certification path not found")))
        }
        assertTrue(TrustedCertificates.isCertificateFailure(untrusted))
        assertTrue(TrustedCertificates.isCertificateFailure(IOException("wrapped", untrusted)))
        assertFalse(TrustedCertificates.isCertificateFailure(SSLHandshakeException("Connection reset")))
        assertFalse(TrustedCertificates.isCertificateFailure(IOException("timeout")))
    }
}
