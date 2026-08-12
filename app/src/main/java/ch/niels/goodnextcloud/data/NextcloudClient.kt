package ch.niels.goodnextcloud.data

import android.content.ContentResolver
import android.net.Uri
import android.util.Xml
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

class NextcloudClient(
    private val http: OkHttpClient = OkHttpClient(),
) {
    fun list(account: Account, path: String): List<CloudFile> {
        val body = """<?xml version="1.0"?>
            <d:propfind xmlns:d="DAV:">
              <d:prop><d:resourcetype/><d:getcontentlength/><d:getcontenttype/><d:getlastmodified/><d:getetag/></d:prop>
            </d:propfind>""".trimIndent()
        val request = authenticated(account, NextcloudPath.davUrl(account, path))
            .method("PROPFIND", body.toRequestBody("application/xml".toMediaTypeOrNull()))
            .header("Depth", "1")
            .build()

        return http.newCall(request).execute().use { response ->
            if (response.code != 207) throw NextcloudException(response.code, response.message)
            parseFiles(response.body.byteStream(), account.username)
                .filterNot { it.path.trim('/') == path.trim('/') }
        }
    }

    fun upload(
        account: Account,
        path: String,
        resolver: ContentResolver,
        sourceUri: Uri,
        size: Long,
        mimeType: String?,
    ) {
        val body = object : RequestBody() {
            override fun contentType() = mimeType?.toMediaTypeOrNull()
            override fun contentLength() = size
            override fun writeTo(sink: BufferedSink) {
                resolver.openInputStream(sourceUri)!!.source().use { sink.writeAll(it) }
            }
        }
        executeEmpty(
            authenticated(account, NextcloudPath.davUrl(account, path))
                .put(body)
                .header("If-None-Match", "*")
                .build(),
            setOf(200, 201, 204),
        )
    }

    fun download(account: Account, file: CloudFile, resolver: ContentResolver, target: Uri) {
        val request = authenticated(account, NextcloudPath.davUrl(account, file.path)).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw NextcloudException(response.code, response.message)
            resolver.openOutputStream(target)!!.use { output -> response.body.byteStream().copyTo(output) }
        }
    }

    fun share(account: Account, path: String, shareWith: String?): ShareResult {
        val form = FormBody.Builder()
            .add("path", "/${path.trimStart('/')}")
            .add("shareType", if (shareWith.isNullOrBlank()) "3" else "0")
            .apply { if (!shareWith.isNullOrBlank()) add("shareWith", shareWith.trim()) }
            .build()
        val request = authenticated(
            account,
            "${NextcloudPath.normalizeServerUrl(account.serverUrl)}/ocs/v2.php/apps/files_sharing/api/v1/shares",
        )
            .post(form)
            .header("OCS-APIRequest", "true")
            .header("Accept", "application/json")
            .build()

        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw NextcloudException(response.code, response.message)
            val root = JSONObject(response.body.string()).getJSONObject("ocs")
            val meta = root.getJSONObject("meta")
            if (meta.getInt("statuscode") != 100 && meta.getInt("statuscode") != 200) {
                throw IllegalStateException(meta.optString("message", "Share failed"))
            }
            val data = root.getJSONObject("data")
            ShareResult(data.get("id").toString(), data.optString("url").ifBlank { null })
        }
    }

    private fun authenticated(account: Account, url: String): Request.Builder = Request.Builder()
        .url(url)
        .header("Authorization", Credentials.basic(account.username, account.appPassword))
        .header("User-Agent", "GoodNextcloud/0.1")

    private fun executeEmpty(request: Request, accepted: Set<Int>) {
        http.newCall(request).execute().use { response ->
            if (response.code !in accepted) throw NextcloudException(response.code, response.message)
        }
    }

    private fun parseFiles(input: java.io.InputStream, username: String): List<CloudFile> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(input, null)
        }
        val files = mutableListOf<CloudFile>()
        var current = MutableFile()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.lowercase()) {
                    "response" -> current = MutableFile()
                    "href" -> current.href = parser.nextText()
                    "collection" -> current.folder = true
                    "getcontentlength" -> current.size = parser.nextText().toLongOrNull() ?: 0
                    "getcontenttype" -> current.mime = parser.nextText().ifBlank { null }
                    "getlastmodified" -> current.modified = parser.nextText().ifBlank { null }
                    "getetag" -> current.etag = parser.nextText().trim('"').ifBlank { null }
                }
            } else if (event == XmlPullParser.END_TAG && parser.name.equals("response", true)) {
                current.toFile(username)?.let(files::add)
            }
            event = parser.next()
        }
        return files
    }

    private data class MutableFile(
        var href: String? = null,
        var folder: Boolean = false,
        var size: Long = 0,
        var mime: String? = null,
        var modified: String? = null,
        var etag: String? = null,
    ) {
        fun toFile(username: String): CloudFile? {
            val path = href?.let { NextcloudPath.relativePathFromDavHref(it, username) } ?: return null
            val name = path.substringAfterLast('/', path)
            return CloudFile(name, path, folder, size, mime, modified, etag)
        }
    }
}

class NextcloudException(val statusCode: Int, message: String) :
    Exception("Nextcloud returned $statusCode: $message")
