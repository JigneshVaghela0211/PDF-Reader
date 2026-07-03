package com.pdf.pdfreader.utiles

/**
 * Micro Chunk 4: the pure result of [ReplacementAnalyzer]. It answers "can this selected text be
 * safely replaced?" and NOTHING else — it performs no replacement and holds no PDFBox handles.
 * Every outcome carries a human-readable [reason]; richer outcomes add structured metadata.
 */
sealed interface ReplacementDecision {
    val reason: String

    /**
     * The selection maps to a single self-contained, encodable operator — safe in-place replace.
     * [confidence] is 0..1: how certain the analyzer is that an in-place swap is safe.
     */
    data class RegionReplace(
        override val reason: String,
        val confidence: Float
    ) : ReplacementDecision

    /**
     * Replaceable, but not safely in-place — the existing block-level fallback should be used.
     * [fallbackType] classifies *why* the safe path was declined.
     */
    data class Fallback(
        override val reason: String,
        val fallbackType: FallbackType
    ) : ReplacementDecision

    /** Replacement must not be attempted at all (no text operator found, ambiguous region, …). */
    data class Reject(override val reason: String) : ReplacementDecision
}

/** Structured classification of why a [ReplacementDecision.Fallback] was chosen. */
enum class FallbackType {
    FONT_SUBSTITUTION,
    MULTI_OPERATOR,
    KERNED_TEXT,
    OVERLAPPING_REGION,
    UNSUPPORTED_ENCODING,
    IMAGE_ONLY,
    UNKNOWN
}
