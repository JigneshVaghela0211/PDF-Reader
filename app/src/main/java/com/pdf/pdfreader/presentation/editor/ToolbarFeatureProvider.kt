package com.pdf.pdfreader.presentation.editor

import com.pdf.pdfreader.core.config.PdfEditorFeatureConfig
import com.pdf.pdfreader.core.model.EditorFeature
import com.pdf.pdfreader.core.model.FeatureBadge
import com.pdf.pdfreader.core.model.FeatureCategory
import com.pdf.pdfreader.core.model.FeatureState

/**
 * A fully-resolved, UI-ready description of a single feature.
 *
 * UI surfaces (toolbar, bottom sheet, menu) consume this instead of touching
 * [PdfEditorFeatureConfig] directly. Everything the UI needs to render a control
 * correctly is precomputed here, so no enable/disable logic lives in the UI layer.
 */
data class FeatureUiModel(
    val feature: EditorFeature,
    val state: FeatureState,
    /** Render the control at all? (false ⇒ omit it entirely.) */
    val visible: Boolean,
    /** Is the control clickable / actionable right now? */
    val interactive: Boolean,
    /** Decoration to draw on the control. */
    val badge: FeatureBadge,
    /** Convenience: show a lock affordance (premium, not yet unlocked). */
    val locked: Boolean,
    val title: String
)

/**
 * Bridges [PdfEditorFeatureConfig] to the editor UI.
 *
 * The single rule this enforces: **UI never decides feature availability** — it asks
 * the provider for a [FeatureUiModel] (or a list of them) and renders mechanically.
 *
 * ```
 * PdfEditorFeatureConfig  →  ToolbarFeatureProvider  →  Toolbar / BottomSheet / Menu
 * ```
 */
object ToolbarFeatureProvider {

    /** Resolve a single feature into its UI model. */
    fun uiModel(feature: EditorFeature): FeatureUiModel {
        val state = PdfEditorFeatureConfig.stateOf(feature)
        return FeatureUiModel(
            feature = feature,
            state = state,
            visible = state.isVisible,
            interactive = state.isInteractive,
            badge = state.badge,
            locked = state == FeatureState.PREMIUM,
            title = feature.title
        )
    }

    /** Shorthand: should this control be shown at all? */
    fun isVisible(feature: EditorFeature): Boolean = PdfEditorFeatureConfig.isVisible(feature)

    /** Shorthand: can the user act on this control right now? */
    fun isInteractive(feature: EditorFeature): Boolean = PdfEditorFeatureConfig.isEnabled(feature)

    /** All visible features in a category, as UI models, in declaration order. */
    fun featuresIn(category: FeatureCategory): List<FeatureUiModel> =
        EditorFeature.entries
            .filter { it.category == category }
            .map { uiModel(it) }
            .filter { it.visible }

    /** Every visible feature, grouped by category — handy for a full feature menu. */
    fun visibleFeaturesByCategory(): Map<FeatureCategory, List<FeatureUiModel>> =
        FeatureCategory.entries.associateWith { featuresIn(it) }
            .filterValues { it.isNotEmpty() }
}
