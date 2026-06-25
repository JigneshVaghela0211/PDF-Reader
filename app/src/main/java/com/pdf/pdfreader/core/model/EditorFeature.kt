package com.pdf.pdfreader.core.model

/**
 * Logical grouping of editor features, used to build toolbars / menus / sheets.
 */
enum class FeatureCategory(val title: String) {
    TEXT_EDIT("Text Edit"),
    MARKUP("Markup"),
    SIGNATURE("Signature"),
    IMAGE("Image"),
    PAGE_MANAGEMENT("Page Management"),
    PDF_TOOLS("PDF Tools"),
    SAVE_OPTIONS("Save Options")
}

/**
 * The complete catalogue of every PDF editing capability in the app.
 *
 * This enum is the single, exhaustive list of "things the editor can do". Whether
 * any given entry is shown, hidden, beta, premium or coming-soon is decided
 * separately by [com.pdf.pdfreader.core.config.PdfEditorFeatureConfig] — this enum only
 * declares identity, category and a human-readable title.
 *
 * Adding a new editor capability = add one entry here + one line in the config map.
 * Nothing else in the UI should hardcode the existence of a feature.
 */
enum class EditorFeature(
    val category: FeatureCategory,
    val title: String
) {
    // ─── TEXT EDIT ────────────────────────────────────────────────
    EDIT_TEXT(FeatureCategory.TEXT_EDIT, "Edit Text"),
    REAL_PDF_TEXT_EDITING(FeatureCategory.TEXT_EDIT, "Real PDF Text Editing"),
    TEXT_FONT_FALLBACK(FeatureCategory.TEXT_EDIT, "Font Fallback"),
    ADD_TEXT(FeatureCategory.TEXT_EDIT, "Add Text"),
    TEXT_COLOR(FeatureCategory.TEXT_EDIT, "Text Color"),
    TEXT_SIZE(FeatureCategory.TEXT_EDIT, "Text Size"),
    TEXT_ROTATION(FeatureCategory.TEXT_EDIT, "Text Rotation"),

    // ─── MARKUP ───────────────────────────────────────────────────
    HIGHLIGHT(FeatureCategory.MARKUP, "Highlight"),
    UNDERLINE(FeatureCategory.MARKUP, "Underline"),
    STRIKETHROUGH(FeatureCategory.MARKUP, "Strikethrough"),
    FREEHAND_DRAWING(FeatureCategory.MARKUP, "Freehand Drawing"),
    ERASER(FeatureCategory.MARKUP, "Eraser"),

    // ─── SIGNATURE ────────────────────────────────────────────────
    SIGNATURE(FeatureCategory.SIGNATURE, "Signature"),
    SAVED_SIGNATURE(FeatureCategory.SIGNATURE, "Saved Signatures"),
    SIGNATURE_RESIZE(FeatureCategory.SIGNATURE, "Signature Resize"),
    SIGNATURE_ROTATION(FeatureCategory.SIGNATURE, "Signature Rotation"),

    // ─── IMAGE ────────────────────────────────────────────────────
    INSERT_IMAGE(FeatureCategory.IMAGE, "Insert Image"),
    IMAGE_RESIZE(FeatureCategory.IMAGE, "Image Resize"),
    IMAGE_MOVE(FeatureCategory.IMAGE, "Image Move"),
    IMAGE_ROTATION(FeatureCategory.IMAGE, "Image Rotation"),
    IMAGE_DELETE(FeatureCategory.IMAGE, "Image Delete"),

    // ─── PAGE MANAGEMENT ──────────────────────────────────────────
    INSERT_PAGE(FeatureCategory.PAGE_MANAGEMENT, "Insert Page"),
    DELETE_PAGE(FeatureCategory.PAGE_MANAGEMENT, "Delete Page"),
    ROTATE_PAGE(FeatureCategory.PAGE_MANAGEMENT, "Rotate Page"),
    EXTRACT_PAGE(FeatureCategory.PAGE_MANAGEMENT, "Extract Page"),
    REORDER_PAGE(FeatureCategory.PAGE_MANAGEMENT, "Reorder Page"),

    // ─── PDF TOOLS ────────────────────────────────────────────────
    SEARCH(FeatureCategory.PDF_TOOLS, "Search"),
    COPY_TEXT(FeatureCategory.PDF_TOOLS, "Copy Text"),
    OCR(FeatureCategory.PDF_TOOLS, "OCR"),
    COMPRESS(FeatureCategory.PDF_TOOLS, "Compress"),
    MERGE(FeatureCategory.PDF_TOOLS, "Merge"),
    SPLIT(FeatureCategory.PDF_TOOLS, "Split"),
    AI_SUMMARY(FeatureCategory.PDF_TOOLS, "AI Summary"),

    // ─── SAVE OPTIONS ─────────────────────────────────────────────
    SAVE_ORIGINAL(FeatureCategory.SAVE_OPTIONS, "Save to Original"),
    SAVE_AS_COPY(FeatureCategory.SAVE_OPTIONS, "Save as Copy"),
    AUTO_BACKUP(FeatureCategory.SAVE_OPTIONS, "Auto Backup"),
    UNDO_REDO_PERSISTENCE(FeatureCategory.SAVE_OPTIONS, "Undo/Redo Persistence");
}
