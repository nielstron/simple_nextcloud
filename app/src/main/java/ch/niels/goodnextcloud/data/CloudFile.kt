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
    val ownerId: String? = null,
    val ownerDisplayName: String? = null,
    val mountType: String? = null,
    val shareTypes: Set<Int> = emptySet(),
) {
    val isIncomingShare: Boolean get() = isFolder && mountType == "shared"
}

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
    val permissions: Int = 1,
)

data class ShareUser(
    val id: String,
    val displayName: String,
)

data class ExistingShare(
    val id: String,
    val shareType: Int,
    val shareWith: String?,
    val displayName: String?,
    val permissions: Int,
    val url: String?,
    val expireDate: String?,
    val ownerId: String?,
)

data class ShareUpdate(
    val permissions: Int,
    val expireDate: String? = null,
    val password: String? = null,
)
