package ch.niels.goodnextcloud.data

data class CloudFile(
    val name: String,
    val path: String,
    val isFolder: Boolean,
    val size: Long,
    val mimeType: String?,
    val modifiedAt: String?,
    val etag: String?,
    val fileId: String? = null,
)

data class Account(
    val serverUrl: String,
    val username: String,
    val appPassword: String,
)

data class LoginFlowSession(
    val loginUrl: String,
    val pollEndpoint: String,
    val token: String,
)

data class ShareResult(
    val id: String,
    val url: String?,
)

data class LinkShareOptions(
    val password: String = "",
    val expireDate: String = "",
    val allowUpload: Boolean = false,
)

data class ShareUser(
    val id: String,
    val displayName: String,
)
