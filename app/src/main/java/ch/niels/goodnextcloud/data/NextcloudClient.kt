package ch.niels.goodnextcloud.data

import android.content.ContentResolver
import android.net.Uri
import java.io.OutputStream
import java.io.File
import android.util.Xml
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

class NextcloudClient(
    private val http: OkHttpClient = OkHttpClient(),
) {
    @Volatile
    private var previewServiceFailed = false

    fun initiateLogin(serverUrl: String): LoginFlowSession {
        val request = Request.Builder()
            .url("${NextcloudPath.normalizeServerUrl(serverUrl)}/index.php/login/v2")
            .post(FormBody.Builder().build())
            .header("User-Agent", "GoodNextcloud/0.1")
            .build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw NextcloudException(response.code, response.message)
            parseLoginSession(response.body.string())
        }
    }

    fun pollLogin(session: LoginFlowSession): Account? {
        val request = Request.Builder()
            .url(session.pollEndpoint)
            .post(FormBody.Builder().add("token", session.token).build())
            .header("User-Agent", "GoodNextcloud/0.1")
            .build()
        return http.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw NextcloudException(response.code, response.message)
            parseLoginAccount(response.body.string())
        }
    }

    internal fun parseLoginSession(json: String): LoginFlowSession {
        val root = JSONObject(json)
        val poll = root.getJSONObject("poll")
        return LoginFlowSession(root.getString("login"), poll.getString("endpoint"), poll.getString("token"))
    }

    internal fun parseLoginAccount(json: String): Account {
        val root = JSONObject(json)
        return Account(
            NextcloudPath.normalizeServerUrl(root.getString("server")),
            root.getString("loginName"),
            root.getString("appPassword"),
        )
    }

    fun list(account: Account, path: String): List<CloudFile> {
        val body = """<?xml version="1.0"?>
            <d:propfind xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns" xmlns:nc="http://nextcloud.org/ns">
              <d:prop><d:resourcetype/><d:getcontentlength/><d:getcontenttype/><d:getlastmodified/><d:getetag/><oc:fileid/><oc:owner-id/><oc:owner-display-name/><oc:share-types/><nc:mount-type/></d:prop>
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

    fun upload(account: Account, path: String, source: File, mimeType: String?) {
        executeEmpty(
            authenticated(account, NextcloudPath.davUrl(account, path))
                .put(source.asRequestBody(mimeType?.toMediaTypeOrNull()))
                .build(),
            setOf(200, 201, 204),
        )
    }

    fun createFolder(account: Account, path: String) {
        executeEmpty(
            authenticated(account, NextcloudPath.davUrl(account, path)).method("MKCOL", null).build(),
            setOf(201),
        )
    }

    fun download(account: Account, file: CloudFile, resolver: ContentResolver, target: Uri) {
        resolver.openOutputStream(target)!!.use { output -> download(account, file, output) }
    }

    fun download(account: Account, file: CloudFile, output: OutputStream) {
        val request = authenticated(account, NextcloudPath.davUrl(account, file.path))
            .get()
            .apply { if (file.isFolder) header("Accept", "application/zip") }
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw NextcloudException(response.code, response.message)
            response.body.byteStream().copyTo(output)
        }
    }

    fun delete(account: Account, file: CloudFile) {
        executeEmpty(
            authenticated(account, NextcloudPath.davUrl(account, file.path)).delete().build(),
            setOf(204),
        )
    }

    fun copy(account: Account, file: CloudFile, destinationFolder: String) {
        transfer("COPY", account, file, NextcloudPath.child(destinationFolder, file.name))
    }

    fun move(account: Account, file: CloudFile, destinationFolder: String) {
        transfer("MOVE", account, file, NextcloudPath.child(destinationFolder, file.name))
    }

    fun rename(account: Account, file: CloudFile, newName: String) {
        val parent = file.path.substringBeforeLast('/', "")
        transfer("MOVE", account, file, NextcloudPath.child(parent, newName))
    }

    private fun transfer(method: String, account: Account, file: CloudFile, destinationPath: String) {
        executeEmpty(
            authenticated(account, NextcloudPath.davUrl(account, file.path))
                .method(method, null)
                .header("Destination", NextcloudPath.davUrl(account, destinationPath))
                .header("Overwrite", "F")
                .build(),
            setOf(201, 204),
        )
    }

    fun preview(account: Account, file: CloudFile): ByteArray {
        fun original() = responseBytes(
            authenticated(account, NextcloudPath.davUrl(account, file.path)).get().build(),
        )
        if (file.mimeType == "image/webp" || previewServiceFailed) return original()
        val fileId = requireNotNull(file.fileId) { "Nextcloud did not provide a file ID for ${file.name}" }
        val url = "${NextcloudPath.normalizeServerUrl(account.serverUrl)}/core/preview"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("fileId", fileId)
            .addQueryParameter("x", "1200")
            .addQueryParameter("y", "1200")
            .addQueryParameter("a", "1")
            .addQueryParameter("mode", "fit")
            .addQueryParameter("forceIcon", "0")
            .build()
        val request = authenticated(account, url.toString()).get().build()
        return try {
            responseBytes(request)
        } catch (failure: NextcloudException) {
            if (failure.statusCode >= 500) previewServiceFailed = true
            original()
        }
    }

    fun share(account: Account, path: String, shareWith: String?, permissions: Int): ShareResult {
        val form = FormBody.Builder()
            .add("path", "/${path.trimStart('/')}")
            .add("shareType", if (shareWith.isNullOrBlank()) "3" else "0")
            .add("permissions", permissions.toString())
            .apply { if (!shareWith.isNullOrBlank()) add("shareWith", shareWith.trim()) }
            .build()
        return createShare(account, form)
    }

    fun createLink(account: Account, path: String, options: LinkShareOptions): ShareResult {
        val form = FormBody.Builder()
            .add("path", "/${path.trimStart('/')}")
            .add("shareType", "3")
            .add("permissions", options.permissions.toString())
            .apply {
                if (options.password.isNotBlank()) add("password", options.password)
                if (options.expireDate.isNotBlank()) add("expireDate", options.expireDate)
                if (options.permissions and (2 or 4 or 8) != 0) add("publicUpload", "true")
            }
            .build()
        return createShare(account, form)
    }

    fun listShares(account: Account, path: String): List<ExistingShare> {
        val url = shareApiUrl(account)
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("path", "/${path.trimStart('/')}")
            .addQueryParameter("reshares", "true")
            .build()
        val request = authenticated(account, url.toString())
            .get()
            .header("OCS-APIRequest", "true")
            .header("Accept", "application/json")
            .build()
        val data = executeOcs(request).getJSONArray("data")
        return buildList {
            for (index in 0 until data.length()) add(data.getJSONObject(index).toExistingShare())
        }
    }

    internal fun parseShares(json: String): List<ExistingShare> {
        val data = JSONObject(json).getJSONObject("ocs").getJSONArray("data")
        return buildList {
            for (index in 0 until data.length()) add(data.getJSONObject(index).toExistingShare())
        }
    }

    fun updateShare(account: Account, id: String, update: ShareUpdate) {
        updateShareField(account, id, "permissions", update.permissions.toString())
        update.expireDate?.let { updateShareField(account, id, "expireDate", it) }
        update.password?.let { updateShareField(account, id, "password", it) }
    }

    fun deleteShare(account: Account, id: String) {
        val request = authenticated(account, "${shareApiUrl(account)}/$id")
            .delete()
            .header("OCS-APIRequest", "true")
            .header("Accept", "application/json")
            .build()
        executeOcs(request)
    }

    fun searchUsers(account: Account, query: String, itemType: String): List<ShareUser> {
        val request = authenticated(
            account,
            "${NextcloudPath.normalizeServerUrl(account.serverUrl)}/ocs/v1.php/apps/files_sharing/api/v1/sharees",
        )
            .get()
            .header("OCS-APIRequest", "true")
            .header("Accept", "application/json")
            .url(
                "${NextcloudPath.normalizeServerUrl(account.serverUrl)}/ocs/v1.php/apps/files_sharing/api/v1/sharees"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("search", query)
                    .addQueryParameter("itemType", itemType)
                    .addQueryParameter("perPage", "20")
                    .addQueryParameter("lookup", "false")
                    .build(),
            )
            .build()

        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw NextcloudException(response.code, response.message)
            parseShareUsers(response.body.string())
        }
    }

    fun recommendedUsers(account: Account, itemType: String): List<ShareUser> {
        val url = "${NextcloudPath.normalizeServerUrl(account.serverUrl)}/ocs/v1.php/apps/files_sharing/api/v1/sharees_recommended"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("itemType", itemType)
            .build()
        val request = authenticated(account, url.toString())
            .get()
            .header("OCS-APIRequest", "true")
            .header("Accept", "application/json")
            .build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw NextcloudException(response.code, response.message)
            parseShareUsers(response.body.string())
        }
    }

    internal fun parseShareUsers(json: String): List<ShareUser> {
        val data = JSONObject(json).getJSONObject("ocs").getJSONObject("data")
        val result = buildList {
            val exactUsers = data.getJSONObject("exact").getJSONArray("users")
            for (index in 0 until exactUsers.length()) add(exactUsers.getJSONObject(index).toShareUser())
            val users = data.getJSONArray("users")
            for (index in 0 until users.length()) add(users.getJSONObject(index).toShareUser())
        }
        return result.distinctBy(ShareUser::id)
    }

    private fun JSONObject.toShareUser(): ShareUser {
        val value = getJSONObject("value")
        return ShareUser(id = value.getString("shareWith"), displayName = getString("label"))
    }

    private fun JSONObject.toExistingShare() = ExistingShare(
        id = get("id").toString(),
        shareType = getInt("share_type"),
        shareWith = nullableString("share_with"),
        displayName = nullableString("share_with_displayname"),
        permissions = getInt("permissions"),
        url = nullableString("url"),
        expireDate = nullableString("expiration")?.substringBefore(' '),
        ownerId = nullableString("uid_owner"),
    )

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).ifBlank { null }

    private fun updateShareField(account: Account, id: String, field: String, value: String) {
        val request = authenticated(account, "${shareApiUrl(account)}/$id")
            .put(FormBody.Builder().add(field, value).build())
            .header("OCS-APIRequest", "true")
            .header("Accept", "application/json")
            .build()
        executeOcs(request)
    }

    private fun createShare(account: Account, form: FormBody): ShareResult {
        val request = authenticated(
            account,
            shareApiUrl(account),
        )
            .post(form)
            .header("OCS-APIRequest", "true")
            .header("Accept", "application/json")
            .build()

        val data = executeOcs(request).getJSONObject("data")
        return ShareResult(data.get("id").toString(), data.optString("url").ifBlank { null })
    }

    private fun shareApiUrl(account: Account) =
        "${NextcloudPath.normalizeServerUrl(account.serverUrl)}/ocs/v2.php/apps/files_sharing/api/v1/shares"

    private fun executeOcs(request: Request): JSONObject = http.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw NextcloudException(response.code, response.message)
        val root = JSONObject(response.body.string()).getJSONObject("ocs")
        val meta = root.getJSONObject("meta")
        if (meta.getInt("statuscode") !in setOf(100, 200)) {
            throw IllegalStateException(meta.optString("message", "Nextcloud request failed"))
        }
        root
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

    private fun responseBytes(request: Request): ByteArray = http.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw NextcloudException(response.code, response.message)
        response.body.bytes()
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
                    "fileid" -> current.fileId = parser.nextText().ifBlank { null }
                    "owner-id" -> current.ownerId = parser.nextText().ifBlank { null }
                    "owner-display-name" -> current.ownerDisplayName = parser.nextText().ifBlank { null }
                    "mount-type" -> current.mountType = parser.nextText().ifBlank { null }
                    "share-type" -> current.shareTypes += parser.nextText().toInt()
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
        var fileId: String? = null,
        var ownerId: String? = null,
        var ownerDisplayName: String? = null,
        var mountType: String? = null,
        var shareTypes: Set<Int> = emptySet(),
    ) {
        fun toFile(username: String): CloudFile? {
            val path = href?.let { NextcloudPath.relativePathFromDavHref(it, username) } ?: return null
            val name = path.substringAfterLast('/', path)
            return CloudFile(
                name,
                path,
                folder,
                size,
                mime,
                modified,
                etag,
                fileId,
                ownerId,
                ownerDisplayName,
                mountType,
                shareTypes,
            )
        }
    }
}

class NextcloudException(val statusCode: Int, message: String) :
    Exception("Nextcloud returned $statusCode: $message")
