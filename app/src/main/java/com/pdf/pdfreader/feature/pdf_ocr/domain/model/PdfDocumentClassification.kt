package com.pdf.pdfreader.feature.pdf_ocr.domain.model

/** Text content kind of a single PDF page, as detected by the document analyzer. */
enum class PageTextKind {
    /** Page has real, selectable text objects. */
    TEXT,

    /** Page has no selectable text but contains at least one image — a scan candidate. */
    IMAGE_ONLY,

    /** Page has neither meaningful text nor images (blank or vector-only). */
    EMPTY
}

/**
 * Result of analyzing a PDF for the OCR decision flow: does this document (or a
 * given page) need OCR before text editing, or can the real text-editing path
 * handle it?
 */
data class PdfDocumentClassification(
    val kind: DocKind,
    val pages: List<PageTextKind>
) {
    enum class DocKind {
        /** Every content page has selectable text — use real PDF text editing. */
        SEARCHABLE,

        /** Image-only pages, no selectable text anywhere — OCR is required to edit. */
        SCANNED,

        /** Mix of text pages and image-only pages — route per page. */
        MIXED
    }

    /** True when [pageIndex] is an image-only page that needs OCR before editing. */
    fun isPageScanned(pageIndex: Int): Boolean =
        pages.getOrNull(pageIndex) == PageTextKind.IMAGE_ONLY
}
