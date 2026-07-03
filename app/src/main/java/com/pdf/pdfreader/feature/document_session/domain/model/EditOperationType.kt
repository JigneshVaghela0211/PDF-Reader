package com.pdf.pdfreader.feature.document_session.domain.model

/**
 * The category of a single [EditOperation] within a [DocumentSession].
 *
 * Foundation only — this enum defines the vocabulary of edits a session can record. No undo/redo or
 * persistence is implemented in this chunk; these values exist so later work can classify operations
 * without reshaping the model.
 */
enum class EditOperationType {
    TEXT_REPLACE,
    TEXT_EDIT,
    ANNOTATION_ADD,
    ANNOTATION_REMOVE,
    MARKUP_ADD,
    IMAGE_ADD,
    IMAGE_TRANSFORM,
    IMAGE_DELETE,
    SIGNATURE_ADD,
    PAGE_MODIFY
}
