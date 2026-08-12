package ch.niels.goodnextcloud.data

data class CloudFile(
    val name: String,
    val path: String,
    val isFolder: Boolean,
    val size: Long,
    val mimeType: String?,
    val modifiedAt: String?,
    val etag: String?,
)

data class Account(
    val serverUrl: String,
    val username: String,
    val appPassword: String,
)

data class ShareResult(
    val id: String,
    val url: String?,
)
