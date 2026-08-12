package ch.niels.goodnextcloud.data

import java.net.URLEncoder
import java.net.URI
import java.net.URLDecoder

object NextcloudPath {
    fun normalizeServerUrl(value: String): String = value.trim().trimEnd('/')

    fun encodePath(path: String): String = path
        .split('/')
        .filter(String::isNotEmpty)
        .joinToString("/") { URLEncoder.encode(it, Charsets.UTF_8.name()).replace("+", "%20") }

    fun davUrl(account: Account, path: String = ""): String {
        val user = URLEncoder.encode(account.username, Charsets.UTF_8.name()).replace("+", "%20")
        val suffix = encodePath(path)
        return "${normalizeServerUrl(account.serverUrl)}/remote.php/dav/files/$user/" +
            if (suffix.isEmpty()) "" else suffix
    }

    fun child(parent: String, name: String): String =
        listOf(parent.trim('/'), name.trim('/')).filter(String::isNotEmpty).joinToString("/")

    fun relativePathFromDavHref(href: String, username: String): String {
        val decodedPath = URLDecoder.decode(URI(href).rawPath, Charsets.UTF_8.name())
        val davFilesMarker = "/remote.php/dav/files/"
        val accountPath = decodedPath.substringAfter(davFilesMarker)
        val responseUsername = accountPath.substringBefore('/')
        check(responseUsername == username) {
            "WebDAV response belongs to '$responseUsername', expected '$username'"
        }
        return accountPath.removePrefix(responseUsername).trim('/')
    }
}
