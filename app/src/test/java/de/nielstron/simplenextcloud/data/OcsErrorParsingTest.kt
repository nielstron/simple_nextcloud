package de.nielstron.simplenextcloud.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OcsErrorParsingTest {
    @Test
    fun `uses OCS message for an HTTP error`() {
        val body = """
            {"ocs":{"meta":{"status":"failure","statuscode":400,"message":"Password needs to be at least 12 characters long"},"data":[]}}
        """.trimIndent()

        val failure = assertThrows(NextcloudException::class.java) {
            NextcloudClient().parseOcs(400, "Bad Request", body)
        }

        assertEquals("Nextcloud returned 400: Password needs to be at least 12 characters long", failure.message)
    }
}
