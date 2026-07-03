package com.pdf.pdfreader.feature.document_session.domain.model

import java.io.File
import java.security.MessageDigest

/**
 * Stable identity of the document a [DocumentSession] edits.
 *
 * A [DocumentSession] references this instead of a bare path, so a session is anchored to *which
 * file* it is (name, size, mtime) rather than just where it happened to live. [documentId] is a
 * deterministic key derived from path + size + lastModified, so reopening the same unchanged file
 * yields the same identity.
 *
 * [sha256] (full file-content hash) is optional and left null here — it is a placeholder for later
 * work; this chunk computes only the cheap metadata identity, no file reads for hashing.
 */
data class DocumentIdentity(
    val documentId: String,
    val documentPath: String,
    val fileName: String,
    val fileSize: Long,
    val lastModified: Long,
    val sha256: String? = null
) {
    companion object {
        /** Build an identity from a file path using its metadata (no content hashing). */
        fun fromPath(path: String): DocumentIdentity {
            val file = File(path)
            val size = if (file.exists()) file.length() else 0L
            val modified = if (file.exists()) file.lastModified() else 0L
            return DocumentIdentity(
                documentId = deterministicId(path, size, modified),
                documentPath = path,
                fileName = file.name,
                fileSize = size,
                lastModified = modified,
                sha256 = null
            )
        }

        /** SHA-256 hex of "path|size|mtime" — cheap, stable across reopens of the same file. */
        private fun deterministicId(path: String, size: Long, modified: Long): String {
            val key = "$path|$size|$modified"
            return MessageDigest.getInstance("SHA-256")
                .digest(key.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }
}
