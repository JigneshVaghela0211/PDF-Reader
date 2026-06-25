package com.pdf.pdfreader.core.model

/**
 * Lifecycle/availability state of a single PDF editor feature.
 *
 * This is the only vocabulary the UI needs in order to decide how to present a
 * feature. The UI must never branch on hardcoded booleans or [com.pdf.pdfreader.BuildConfig]
 * — it asks [com.pdf.pdfreader.core.config.PdfEditorFeatureConfig] for the state and renders
 * according to the rules documented on each constant below.
 *
 * UI contract:
 * - [ENABLED]     → show normally, fully interactive.
 * - [DISABLED]    → hide completely (no toolbar item, no click, no shortcut).
 * - [COMING_SOON] → show, but disabled, with a "SOON" badge.
 * - [BETA]        → show, interactive, with a "BETA" label.
 * - [PREMIUM]     → show with a lock icon; not interactive until unlocked.
 */
enum class FeatureState {
    ENABLED,
    DISABLED,
    COMING_SOON,
    BETA,
    PREMIUM;

    /** Whether the feature should appear in the UI at all. Only [DISABLED] is hidden. */
    val isVisible: Boolean
        get() = this != DISABLED

    /** Whether the user can actually trigger the feature right now (click enabled). */
    val isInteractive: Boolean
        get() = this == ENABLED || this == BETA

    /** Badge to decorate the UI control with, if any. */
    val badge: FeatureBadge
        get() = when (this) {
            BETA -> FeatureBadge.BETA
            COMING_SOON -> FeatureBadge.COMING_SOON
            PREMIUM -> FeatureBadge.PREMIUM
            ENABLED, DISABLED -> FeatureBadge.NONE
        }
}

/** Visual decoration that accompanies a feature control. */
enum class FeatureBadge {
    NONE,
    BETA,
    COMING_SOON,
    PREMIUM
}
