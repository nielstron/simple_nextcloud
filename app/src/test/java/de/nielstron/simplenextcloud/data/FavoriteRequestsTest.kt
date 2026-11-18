package de.nielstron.simplenextcloud.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteRequestsTest {
    @Test
    fun `proppatch succeeds only when every property status is 2xx`() {
        val ok = """<d:multistatus xmlns:d="DAV:"><d:response><d:href>/x</d:href>
            <d:propstat><d:prop><oc:favorite/></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
            </d:response></d:multistatus>"""
        val forbidden = ok.replace("HTTP/1.1 200 OK", "HTTP/1.1 403 Forbidden")

        assertNull(failedPropStatus(ok))
        assertEquals(403, failedPropStatus(forbidden))
    }

    @Test
    fun `file id search escapes the user name`() {
        val body = fileIdSearchBody("a&b<c", "42")

        assertTrue("<d:href>/files/a&amp;b&lt;c</d:href>" in body)
        assertTrue("<d:literal>42</d:literal>" in body)
        assertFalse("a&b" in body)
    }

    @Test
    fun `cached listings without a favorite field decode as not starred`() {
        val json = """[{"name":"a","path":"a","folder":true,"size":0,"mimeType":null,"modifiedAt":null,
            "etag":null,"fileId":"1","ownerId":null,"ownerDisplayName":null,"mountType":null,"shareTypes":[]}]"""

        assertFalse(decodeCloudFiles(json).single().favorite)
    }

    @Test
    fun `favorite flag round trips through the cache codec`() {
        val files = listOf(CloudFile("a", "a", true, 0, null, null, null, fileId = "1", favorite = true))

        assertEquals(files, decodeFolderListing(encodeFolderListing(files)))
        assertEquals(files, decodeCloudFiles(encodeCloudFiles(files)))
    }
}
