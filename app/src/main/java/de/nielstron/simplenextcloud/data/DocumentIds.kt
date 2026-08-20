package de.nielstron.simplenextcloud.data

import java.util.Base64

object DocumentIds {
    const val ROOT = "root"

    fun fromPath(path: String): String {
        val normalized = path.trim('/')
        if (normalized.isEmpty()) return ROOT
        return Base64.getUrlEncoder().withoutPadding().encodeToString(normalized.toByteArray())
    }

    fun toPath(documentId: String): String {
        if (documentId == ROOT) return ""
        return Base64.getUrlDecoder().decode(documentId).decodeToString()
    }

    fun isChild(parentDocumentId: String, documentId: String): Boolean {
        val parent = toPath(parentDocumentId)
        val child = toPath(documentId)
        return child != parent && (parent.isEmpty() || child.startsWith("$parent/"))
    }
}
