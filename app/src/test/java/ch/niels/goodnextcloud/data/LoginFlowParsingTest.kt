package ch.niels.goodnextcloud.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LoginFlowParsingTest {
    private val client = NextcloudClient()

    @Test
    fun `parses login session`() {
        val session = client.parseLoginSession(
            """{"poll":{"token":"token-1","endpoint":"https://cloud.example.com/login/v2/poll"},"login":"https://cloud.example.com/login/v2/flow/flow-1"}""",
        )
        assertEquals("token-1", session.token)
        assertEquals("https://cloud.example.com/login/v2/poll", session.pollEndpoint)
        assertEquals("https://cloud.example.com/login/v2/flow/flow-1", session.loginUrl)
    }

    @Test
    fun `parses approved account`() {
        assertEquals(
            Account("https://cloud.example.com", "niels", "app-secret"),
            client.parseLoginAccount(
                """{"server":"https://cloud.example.com/","loginName":"niels","appPassword":"app-secret"}""",
            ),
        )
    }
}
