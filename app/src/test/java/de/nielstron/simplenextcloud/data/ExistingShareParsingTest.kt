package de.nielstron.simplenextcloud.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ExistingShareParsingTest {
    @Test
    fun `parses user and public link shares`() {
        val json = """
            {"ocs":{"data":[
              {"id":"12","share_type":0,"share_with":"alice","share_with_displayname":"Alice Adams","permissions":31,"url":null,"expiration":null,"uid_owner":"niels"},
              {"id":"13","share_type":3,"share_with":null,"share_with_displayname":null,"permissions":1,"url":"https://cloud.example/s/abc","expiration":"2026-09-03 00:00:00","uid_owner":"niels"}
            ]}}
        """.trimIndent()

        assertEquals(
            listOf(
                ExistingShare("12", 0, "alice", "Alice Adams", 31, null, null, "niels"),
                ExistingShare("13", 3, null, null, 1, "https://cloud.example/s/abc", "2026-09-03", "niels"),
            ),
            NextcloudClient().parseShares(json),
        )
    }
}
