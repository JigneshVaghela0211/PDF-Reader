package com.pdf.pdfreader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.pdf.pdfreader.domain.model.PdfAnnotation

enum class AnnotationTool {
    NONE, PEN, HIGHLIGHTER, ERASER, TEXT, EDIT_TEXT, INSERT_IMAGE
}

/**
 * PDF annotation overlay with color bug fix.
 *
 * BUG FIX: Previously, `currentColor` and `currentStrokeWidth` were used as
 * keys in `pointerInput()`, which caused the gesture handler to restart on
 * every color/stroke change — resetting currentPathPoints mid-stroke.
 *
 * FIX: Use `rememberUpdatedState` to capture the latest color/strokeWidth
 * without restarting the pointer input handler. The `pointerInput` block
 * is now only keyed on `isEditMode` and `currentTool`.
 */
@Composable
fun PdfAnnotationOverlay(
    modifier: Modifier = Modifier,
    isEditMode: Boolean,
    currentTool: AnnotationTool,
    currentColor: Color,
    currentStrokeWidth: Float,
    annotations: List<PdfAnnotation>,
    onAnnotationAdded: (PdfAnnotation) -> Unit,
    onAnnotationRemoved: (String) -> Unit,
    pageIndex: Int
) {
    // Capture latest values without restarting pointer input
    val latestColor by rememberUpdatedState(currentColor)
    val latestStrokeWidth by rememberUpdatedState(currentStrokeWidth)
    val latestAnnotations by rememberUpdatedState(annotations)

    var currentPathPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    // Track the color that was active when the current stroke started
    var strokeColor by remember { mutableStateOf(currentColor) }
    var strokeWidth by remember { mutableStateOf(currentStrokeWidth) }

    // Only attach gesture handlers for drawing tools.
    // For NONE / EDIT_TEXT / INSERT_IMAGE, the Canvas must NOT intercept
    // pointer events — those should pass through to overlays above.
    val isDrawingTool = isEditMode && (currentTool == AnnotationTool.PEN
            || currentTool == AnnotationTool.HIGHLIGHTER
            || currentTool == AnnotationTool.ERASER)
    val isTextPlaceTool = isEditMode && currentTool == AnnotationTool.TEXT

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (isDrawingTool) {
                    Modifier.pointerInput(currentTool) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (currentTool != AnnotationTool.ERASER) {
                                    // Capture color/width at stroke START — this is the key fix
                                    strokeColor = latestColor
                                    strokeWidth = latestStrokeWidth
                                    currentPathPoints = listOf(offset)
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                if (currentTool == AnnotationTool.ERASER) {
                                    val position = change.position
                                    val toRemove = latestAnnotations.filter { ann ->
                                        ann is PdfAnnotation.Path && ann.pageIndex == pageIndex &&
                                                ann.points.any { p ->
                                                    val dx = p.x - position.x
                                                    val dy = p.y - position.y
                                                    (dx * dx + dy * dy) < 2500f
                                                }
                                    }
                                    toRemove.forEach { onAnnotationRemoved(it.id) }
                                } else {
                                    currentPathPoints = currentPathPoints + change.position
                                }
                            },
                            onDragEnd = {
                                if (currentTool != AnnotationTool.ERASER && currentPathPoints.isNotEmpty()) {
                                    val newAnnotation = PdfAnnotation.Path(
                                        pageIndex = pageIndex,
                                        points = currentPathPoints,
                                        color = strokeColor,  // Use captured color, not current
                                        strokeWidth = strokeWidth,  // Use captured width, not current
                                        isHighlighter = currentTool == AnnotationTool.HIGHLIGHTER
                                    )
                                    onAnnotationAdded(newAnnotation)
                                    currentPathPoints = emptyList()
                                }
                            },
                            onDragCancel = {
                                currentPathPoints = emptyList()
                            }
                        )
                    }
                } else {
                    Modifier // No gesture handler — events pass through
                }
            )
            .then(
                if (isTextPlaceTool) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val newAnnotation = PdfAnnotation.TextNote(
                                pageIndex = pageIndex,
                                text = "",
                                position = offset,
                                color = latestColor,  // Use latest via rememberUpdatedState
                                fontSize = 24f
                            )
                            onAnnotationAdded(newAnnotation)
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        // Draw existing annotations for this page
        annotations.filter { it.pageIndex == pageIndex }.forEach { annotation ->
            when (annotation) {
                is PdfAnnotation.Path -> {
                    if (annotation.points.size >= 2) {
                        val path = Path()
                        val first = annotation.points.first()
                        path.moveTo(first.x, first.y)
                        for (i in 1 until annotation.points.size) {
                            val p = annotation.points[i]
                            path.lineTo(p.x, p.y)
                        }
                        drawPath(
                            path = path,
                            color = annotation.color,
                            style = Stroke(
                                width = annotation.strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            ),
                            alpha = if (annotation.isHighlighter) 0.5f else 1.0f
                        )
                    } else if (annotation.points.size == 1) {
                        drawCircle(
                            color = annotation.color,
                            radius = annotation.strokeWidth / 2,
                            center = annotation.points.first(),
                            alpha = if (annotation.isHighlighter) 0.5f else 1.0f
                        )
                    }
                }
                is PdfAnnotation.TextNote -> { /* handled by MovableTextNote composable */ }
                is PdfAnnotation.TextMarkup -> {
                    val rectColor = annotation.color
                    annotation.rects.forEach { rect ->
                        when (annotation.type) {
                            com.pdf.pdfreader.domain.model.PdfAnnotation.MarkupType.HIGHLIGHT -> {
                                drawRect(
                                    color = rectColor,
                                    topLeft = rect.topLeft,
                                    size = rect.size
                                )
                            }
                            com.pdf.pdfreader.domain.model.PdfAnnotation.MarkupType.UNDERLINE -> {
                                val y = rect.bottom + 2f
                                drawLine(
                                    color = rectColor,
                                    start = Offset(rect.left, y),
                                    end = Offset(rect.right, y),
                                    strokeWidth = 3f
                                )
                            }
                            com.pdf.pdfreader.domain.model.PdfAnnotation.MarkupType.STRIKETHROUGH -> {
                                val y = rect.top + rect.height / 2f
                                drawLine(
                                    color = rectColor,
                                    start = Offset(rect.left, y),
                                    end = Offset(rect.right, y),
                                    strokeWidth = 3f
                                )
                            }
                        }
                    }
                }
            }
        }

        // Draw current in-progress path
        if (isEditMode && currentPathPoints.size >= 2) {
            val path = Path()
            val first = currentPathPoints.first()
            path.moveTo(first.x, first.y)
            for (i in 1 until currentPathPoints.size) {
                val p = currentPathPoints[i]
                path.lineTo(p.x, p.y)
            }
            drawPath(
                path = path,
                color = strokeColor,  // Use captured stroke color
                style = Stroke(
                    width = strokeWidth,  // Use captured stroke width
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                ),
                alpha = if (currentTool == AnnotationTool.HIGHLIGHTER) 0.5f else 1.0f
            )
        } else if (isEditMode && currentPathPoints.size == 1) {
            drawCircle(
                color = strokeColor,
                radius = strokeWidth / 2,
                center = currentPathPoints.first(),
                alpha = if (currentTool == AnnotationTool.HIGHLIGHTER) 0.5f else 1.0f
            )
        }
    }
}
