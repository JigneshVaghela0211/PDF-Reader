package com.pdf.pdfreader.selection.model

import com.pdf.pdfreader.domain.model.TextWord

/**
 * A single word that can be selected on a rendered PDF page (text + normalized 0..1 bounds).
 *
 * This is intentionally a typealias for the existing domain [TextWord] rather than a parallel
 * model: the text extractor and the PDF markup engine already produce/consume [TextWord], so a
 * second word type would duplicate architecture. The alias just gives the selection package a
 * name that reads in its own vocabulary.
 */
typealias PdfSelectableWord = TextWord
