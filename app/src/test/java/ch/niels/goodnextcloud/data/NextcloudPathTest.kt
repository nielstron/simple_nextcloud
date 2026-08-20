package ch.niels.goodnextcloud.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NextcloudPathTest {
    @Test
    fun `extracts server root from login flow URL`() {
        assertEquals(
            "https://cloud.example.com",
            NextcloudPath.serverRoot("https://cloud.example.com/login/v2/flow/token"),
        )
    }

    private val account = Account("https://cloud.example.com/", "niels@example.com", "secret")

    @Test
    fun `prefixes a bare server address with https`() {
        assertEquals("https://cloud.example.com", NextcloudPath.normalizeServerUrl(" cloud.example.com/ "))
    }

    @Test
    fun `keeps an explicit scheme`() {
        assertEquals("https://cloud.example.com", NextcloudPath.normalizeServerUrl("https://cloud.example.com/"))
        assertEquals("http://localhost:8080", NextcloudPath.normalizeServerUrl("http://localhost:8080/"))
    }

    @Test
    fun `builds an encoded dav url`() {
        assertEquals(
            "https://cloud.example.com/remote.php/dav/files/niels%40example.com/Work%20files/report%20%231.pdf",
            NextcloudPath.davUrl(account, "/Work files/report #1.pdf"),
        )
    }

    @Test
    fun `joins paths without duplicate separators`() {
        assertEquals("Projects/Client", NextcloudPath.child("/Projects/", "/Client/"))
    }

    @Test
    fun `builds selectable breadcrumbs from root to the current folder`() {
        assertEquals(
            listOf(
                "All files" to "",
                "Projects" to "Projects",
                "Client" to "Projects/Client",
                "Drafts" to "Projects/Client/Drafts",
            ),
            NextcloudPath.breadcrumbs("/Projects/Client/Drafts/"),
        )
    }

    @Test
    fun `extracts a top level folder path from a dav href`() {
        assertEquals(
            "Documents",
            NextcloudPath.relativePathFromDavHref(
                "/remote.php/dav/files/niels/Documents/",
                "niels",
            ),
        )
    }

    @Test
    fun `extracts a nested folder path from a dav href`() {
        assertEquals(
            "Documents/Work",
            NextcloudPath.relativePathFromDavHref(
                "/remote.php/dav/files/niels/Documents/Work/",
                "niels",
            ),
        )
    }

    @Test
    fun `decodes top level folder names exactly once`() {
        assertEquals(
            "Tax 20% #1",
            NextcloudPath.relativePathFromDavHref(
                "/remote.php/dav/files/niels/Tax%2020%25%20%231/",
                "niels",
            ),
        )
    }
}
