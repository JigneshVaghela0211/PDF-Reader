package com.pdf.pdfreader.feature.pdf_ocr.domain.model

/**
 * Writing script the OCR engine should recognize. Each entry maps to a bundled
 * ML Kit recognizer model (selected via TextRecognizerFactory).
 */
enum class OcrScript(val displayName: String) {
    LATIN("Latin — English & European"),
    CHINESE("Chinese"),
    DEVANAGARI("Devanagari — Hindi, Marathi, Nepali"),
    JAPANESE("Japanese"),
    KOREAN("Korean")
}
