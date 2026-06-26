package com.pdf.pdfreader.feature.pdf_image.domain.model

import com.pdf.pdfreader.domain.model.ImageElement

/**
 * A transformable image placed on a PDF page.
 *
 * Deliberately a typealias for the existing domain [ImageElement] rather than a parallel data
 * class: the overlay, Room command serialization and the whole editor pipeline already
 * produce/consume [ImageElement], so a second model would duplicate architecture (mirrors the
 * existing `PdfSelectableWord = TextWord` convention). This alias just gives the pdf_image feature
 * a name that reads in its own vocabulary.
 */
typealias PdfImageObject = ImageElement
