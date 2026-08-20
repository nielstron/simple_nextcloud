package de.nielstron.simplenextcloud.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareUserParsingTest {
    @Test
    fun `parses exact and suggested users without duplicates`() {
        val json = """
            {"ocs":{"data":{
              "exact":{"users":[{"label":"Alice Adams","value":{"shareType":0,"shareWith":"alice"}}]},
              "users":[
                {"label":"Alice Adams","value":{"shareType":0,"shareWith":"alice"}},
                {"label":"Bob Brown","value":{"shareType":0,"shareWith":"bob"}}
              ]
            }}}
        """.trimIndent()

        assertEquals(
            listOf(ShareUser("alice", "Alice Adams"), ShareUser("bob", "Bob Brown")),
            NextcloudClient().parseShareUsers(json),
        )
    }
}
