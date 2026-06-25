package com.pdf.pdfreader.core.config

import com.pdf.pdfreader.BuildConfig
import com.pdf.pdfreader.core.model.EditorFeature
import com.pdf.pdfreader.core.model.FeatureState

/**
 * SINGLE SOURCE OF TRUTH for every PDF editing feature's availability.
 *
 * Goals:
 * - One place controls which editing options are enabled / disabled / beta / premium / coming-soon.
 * - The UI (toolbars, bottom sheets, menus, ViewModel) NEVER hardcodes feature on/off logic.
 *   It asks this object (directly, or via
 *   [com.pdf.pdfreader.presentation.editor.ToolbarFeatureProvider]) and renders accordingly.
 * - Adding / removing a feature only requires editing [EditorFeature] + this map.
 *
 * Usage:
 * ```
 * if (PdfEditorFeatureConfig.isEnabled(EditorFeature.SIGNATURE)) { showSignature() }
 * // or via the named accessors:
 * if (PdfEditorFeatureConfig.ENABLE_SIGNATURE) { showSignature() }
 * ```
 *
 * Environment support:
 * - DEBUG builds may turn on experimental / not-yet-production features (see [debugOverrides]).
 * - RELEASE builds expose only production-ready features (see [releaseOverrides]).
 */
object PdfEditorFeatureConfig {

    /**
     * Production baseline state for every feature. This is the authoritative default;
     * environment overrides are layered on top in [resolvedStates].
     */
    private val baseline: Map<EditorFeature, FeatureState> = mapOf(
        // ─── TEXT EDIT ───
        EditorFeature.EDIT_TEXT to FeatureState.ENABLED,
        EditorFeature.REAL_PDF_TEXT_EDITING to FeatureState.DISABLED, // experimental; enabled in debug
        EditorFeature.TEXT_FONT_FALLBACK to FeatureState.ENABLED,
        EditorFeature.ADD_TEXT to FeatureState.ENABLED,
        EditorFeature.TEXT_COLOR to FeatureState.ENABLED,
        EditorFeature.TEXT_SIZE to FeatureState.ENABLED,
        EditorFeature.TEXT_ROTATION to FeatureState.BETA,

        // ─── MARKUP ───
        EditorFeature.HIGHLIGHT to FeatureState.ENABLED,
        EditorFeature.UNDERLINE to FeatureState.ENABLED,
        EditorFeature.STRIKETHROUGH to FeatureState.ENABLED,
        EditorFeature.FREEHAND_DRAWING to FeatureState.ENABLED,
        EditorFeature.ERASER to FeatureState.ENABLED,

        // ─── SIGNATURE ───
        EditorFeature.SIGNATURE to FeatureState.ENABLED,
        EditorFeature.SAVED_SIGNATURE to FeatureState.ENABLED,
        EditorFeature.SIGNATURE_RESIZE to FeatureState.ENABLED,
        EditorFeature.SIGNATURE_ROTATION to FeatureState.ENABLED,

        // ─── IMAGE ───
        EditorFeature.INSERT_IMAGE to FeatureState.ENABLED,
        EditorFeature.IMAGE_RESIZE to FeatureState.ENABLED,
        EditorFeature.IMAGE_MOVE to FeatureState.ENABLED,
        EditorFeature.IMAGE_ROTATION to FeatureState.ENABLED,
        EditorFeature.IMAGE_DELETE to FeatureState.ENABLED,

        // ─── PAGE MANAGEMENT ───
        EditorFeature.INSERT_PAGE to FeatureState.BETA, // insert blank / duplicate page (PdfPageManager)
        EditorFeature.DELETE_PAGE to FeatureState.ENABLED,
        EditorFeature.ROTATE_PAGE to FeatureState.ENABLED,
        EditorFeature.EXTRACT_PAGE to FeatureState.ENABLED,
        EditorFeature.REORDER_PAGE to FeatureState.BETA, // drag-to-reorder grid (PdfPageManager.reorder)

        // ─── PDF TOOLS ───
        EditorFeature.SEARCH to FeatureState.ENABLED,
        EditorFeature.COPY_TEXT to FeatureState.ENABLED,
        EditorFeature.OCR to FeatureState.BETA, // PdfOcrEngine (ML Kit) + PDF Tools sheet
        EditorFeature.COMPRESS to FeatureState.BETA, // PdfCompressionEngine + PDF Tools sheet
        EditorFeature.MERGE to FeatureState.BETA,    // PdfMergeEngine + PDF Tools sheet
        EditorFeature.SPLIT to FeatureState.BETA,    // PdfSplitEngine + PDF Tools sheet
        EditorFeature.AI_SUMMARY to FeatureState.PREMIUM,

        // ─── SAVE OPTIONS ───
        EditorFeature.SAVE_ORIGINAL to FeatureState.ENABLED,
        EditorFeature.SAVE_AS_COPY to FeatureState.ENABLED,
        EditorFeature.AUTO_BACKUP to FeatureState.DISABLED,
        EditorFeature.UNDO_REDO_PERSISTENCE to FeatureState.ENABLED,
    )

    /** Overrides applied only on DEBUG builds — enable experimental features for testing. */
    private val debugOverrides: Map<EditorFeature, FeatureState> = mapOf(
        EditorFeature.REAL_PDF_TEXT_EDITING to FeatureState.BETA,
    )

    /** Overrides applied only on RELEASE builds — keep non-production features hidden. */
    private val releaseOverrides: Map<EditorFeature, FeatureState> = mapOf(
        // Production ships without experimental real-text editing.
        EditorFeature.REAL_PDF_TEXT_EDITING to FeatureState.DISABLED,
    )

    /** Fully resolved state map for the current build variant. Computed once. */
    private val resolvedStates: Map<EditorFeature, FeatureState> = buildMap {
        putAll(baseline)
        putAll(if (BuildConfig.DEBUG) debugOverrides else releaseOverrides)
    }

    // ─── Core API ─────────────────────────────────────────────────

    /** The resolved state of [feature] for the current build. Never null. */
    fun stateOf(feature: EditorFeature): FeatureState =
        resolvedStates[feature] ?: FeatureState.DISABLED

    /** True when the feature is fully usable right now (ENABLED or BETA). */
    fun isEnabled(feature: EditorFeature): Boolean = stateOf(feature).isInteractive

    /** True when the feature should appear in the UI at all (anything but DISABLED). */
    fun isVisible(feature: EditorFeature): Boolean = stateOf(feature).isVisible

    /** All features (optionally within a category) that should be shown in the UI. */
    fun visibleFeatures(): List<EditorFeature> =
        EditorFeature.entries.filter { isVisible(it) }

    // ─── Named convenience accessors ──────────────────────────────
    // Mirror the requested `ENABLE_*` naming. These delegate to the map above so the
    // map remains the single source of truth (no duplicated booleans to keep in sync).

    // TEXT EDIT
    val ENABLE_EDIT_TEXT: Boolean get() = isEnabled(EditorFeature.EDIT_TEXT)
    val ENABLE_REAL_PDF_TEXT_EDITING: Boolean get() = isEnabled(EditorFeature.REAL_PDF_TEXT_EDITING)
    val ENABLE_TEXT_FONT_FALLBACK: Boolean get() = isEnabled(EditorFeature.TEXT_FONT_FALLBACK)
    val ENABLE_ADD_TEXT: Boolean get() = isEnabled(EditorFeature.ADD_TEXT)
    val ENABLE_TEXT_COLOR: Boolean get() = isEnabled(EditorFeature.TEXT_COLOR)
    val ENABLE_TEXT_SIZE: Boolean get() = isEnabled(EditorFeature.TEXT_SIZE)
    val ENABLE_TEXT_ROTATION: Boolean get() = isEnabled(EditorFeature.TEXT_ROTATION)

    // MARKUP
    val ENABLE_HIGHLIGHT: Boolean get() = isEnabled(EditorFeature.HIGHLIGHT)
    val ENABLE_UNDERLINE: Boolean get() = isEnabled(EditorFeature.UNDERLINE)
    val ENABLE_STRIKETHROUGH: Boolean get() = isEnabled(EditorFeature.STRIKETHROUGH)
    val ENABLE_FREEHAND_DRAWING: Boolean get() = isEnabled(EditorFeature.FREEHAND_DRAWING)
    val ENABLE_ERASER: Boolean get() = isEnabled(EditorFeature.ERASER)

    // SIGNATURE
    val ENABLE_SIGNATURE: Boolean get() = isEnabled(EditorFeature.SIGNATURE)
    val ENABLE_SAVED_SIGNATURE: Boolean get() = isEnabled(EditorFeature.SAVED_SIGNATURE)
    val ENABLE_SIGNATURE_RESIZE: Boolean get() = isEnabled(EditorFeature.SIGNATURE_RESIZE)
    val ENABLE_SIGNATURE_ROTATION: Boolean get() = isEnabled(EditorFeature.SIGNATURE_ROTATION)

    // IMAGE
    val ENABLE_INSERT_IMAGE: Boolean get() = isEnabled(EditorFeature.INSERT_IMAGE)
    val ENABLE_IMAGE_RESIZE: Boolean get() = isEnabled(EditorFeature.IMAGE_RESIZE)
    val ENABLE_IMAGE_MOVE: Boolean get() = isEnabled(EditorFeature.IMAGE_MOVE)
    val ENABLE_IMAGE_ROTATION: Boolean get() = isEnabled(EditorFeature.IMAGE_ROTATION)
    val ENABLE_IMAGE_DELETE: Boolean get() = isEnabled(EditorFeature.IMAGE_DELETE)

    // PAGE MANAGEMENT
    val ENABLE_INSERT_PAGE: Boolean get() = isEnabled(EditorFeature.INSERT_PAGE)
    val ENABLE_DELETE_PAGE: Boolean get() = isEnabled(EditorFeature.DELETE_PAGE)
    val ENABLE_ROTATE_PAGE: Boolean get() = isEnabled(EditorFeature.ROTATE_PAGE)
    val ENABLE_EXTRACT_PAGE: Boolean get() = isEnabled(EditorFeature.EXTRACT_PAGE)
    val ENABLE_REORDER_PAGE: Boolean get() = isEnabled(EditorFeature.REORDER_PAGE)

    // PDF TOOLS
    val ENABLE_SEARCH: Boolean get() = isEnabled(EditorFeature.SEARCH)
    val ENABLE_COPY_TEXT: Boolean get() = isEnabled(EditorFeature.COPY_TEXT)
    val ENABLE_OCR: Boolean get() = isEnabled(EditorFeature.OCR)
    val ENABLE_COMPRESS: Boolean get() = isEnabled(EditorFeature.COMPRESS)
    val ENABLE_MERGE: Boolean get() = isEnabled(EditorFeature.MERGE)
    val ENABLE_SPLIT: Boolean get() = isEnabled(EditorFeature.SPLIT)
    val ENABLE_AI_SUMMARY: Boolean get() = isEnabled(EditorFeature.AI_SUMMARY)

    // SAVE OPTIONS
    val ENABLE_SAVE_ORIGINAL: Boolean get() = isEnabled(EditorFeature.SAVE_ORIGINAL)
    val ENABLE_SAVE_AS_COPY: Boolean get() = isEnabled(EditorFeature.SAVE_AS_COPY)
    val ENABLE_AUTO_BACKUP: Boolean get() = isEnabled(EditorFeature.AUTO_BACKUP)
    val ENABLE_UNDO_REDO_PERSISTENCE: Boolean get() = isEnabled(EditorFeature.UNDO_REDO_PERSISTENCE)
}
