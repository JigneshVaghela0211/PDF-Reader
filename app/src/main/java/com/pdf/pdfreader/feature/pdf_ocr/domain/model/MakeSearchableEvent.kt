package com.pdf.pdfreader.feature.pdf_ocr.domain.model

/** Stream of a Make Searchable run: per-page progress, then one terminal Done. */
sealed interface MakeSearchableEvent {
    data class Progress(val progress: OcrProgress) : MakeSearchableEvent

    /** Terminal event; [result] is null when the PDF could not be written. */
    data class Done(val result: MakeSearchableResult?) : MakeSearchableEvent
}
