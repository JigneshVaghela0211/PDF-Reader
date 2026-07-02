package com.pdf.pdfreader.feature.image.domain

import androidx.compose.ui.geometry.Offset
import com.pdf.pdfreader.domain.model.AnnotationCommand
import com.pdf.pdfreader.domain.model.ImageElement

/**
 * Pure, stateless conversions between [ImageElement] (live UI state) and
 * [AnnotationCommand.ImageState] (the serialized snapshot stored in undo/redo commands).
 *
 * Extracted from PdfEditorViewModel so the same mapping is not re-implemented inline at
 * each add/delete/duplicate site (which previously risked fields like flip being dropped
 * in one branch but not another). Keep both directions in sync when ImageElement grows.
 */
object ImageElementStateMapper {

    fun toState(element: ImageElement): AnnotationCommand.ImageState = AnnotationCommand.ImageState(
        elementId = element.id,
        uri = element.uri,
        positionX = element.position.x,
        positionY = element.position.y,
        width = element.width,
        height = element.height,
        scale = element.scale,
        rotation = element.rotation,
        opacity = element.opacity,
        isLocked = element.isLocked,
        zIndex = element.zIndex,
        flipHorizontal = element.flipHorizontal,
        flipVertical = element.flipVertical
    )

    fun toElement(state: AnnotationCommand.ImageState, pageIndex: Int): ImageElement = ImageElement(
        id = state.elementId,
        pageIndex = pageIndex,
        uri = state.uri,
        position = Offset(state.positionX, state.positionY),
        width = state.width,
        height = state.height,
        scale = state.scale,
        rotation = state.rotation,
        opacity = state.opacity,
        isLocked = state.isLocked,
        zIndex = state.zIndex,
        flipHorizontal = state.flipHorizontal,
        flipVertical = state.flipVertical
    )
}
