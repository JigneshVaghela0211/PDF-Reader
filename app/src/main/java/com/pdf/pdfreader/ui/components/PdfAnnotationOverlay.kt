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
    PEN, HIGHLIGHTER, ERASER, TEXT
}

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
    var currentPathPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isEditMode, currentTool, currentColor, currentStrokeWidth) {
                if (isEditMode && (currentTool == AnnotationTool.PEN || currentTool == AnnotationTool.HIGHLIGHTER || currentTool == AnnotationTool.ERASER)) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (currentTool != AnnotationTool.ERASER) {
                                currentPathPoints = listOf(offset)
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (currentTool == AnnotationTool.ERASER) {
                                val position = change.position
                                val toRemove = annotations.filter { ann ->
                                    ann is PdfAnnotation.Path && ann.pageIndex == pageIndex &&
                                            ann.points.any { p -> 
                                                val dx = p.x - position.x
                                                val dy = p.y - position.y
                                                (dx * dx + dy * dy) < 2500f // 50px radius squared
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
                                    color = currentColor,
                                    strokeWidth = currentStrokeWidth,
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
            }
            .pointerInput(isEditMode, currentTool, currentColor) {
                if (isEditMode && currentTool == AnnotationTool.TEXT) {
                    detectTapGestures { offset ->
                        val newAnnotation = PdfAnnotation.TextNote(
                            pageIndex = pageIndex,
                            text = "",
                            position = offset,
                            color = currentColor,
                            fontSize = 24f
                        )
                        onAnnotationAdded(newAnnotation)
                    }
                }
            }
    ) {
        // Draw existing annotations
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
                is PdfAnnotation.TextNote -> { /* handled by standard compose components outside canvas */ }
            }
        }

        // Draw path in progress
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
                color = currentColor,
                style = Stroke(
                    width = currentStrokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                ),
                alpha = if (currentTool == AnnotationTool.HIGHLIGHTER) 0.5f else 1.0f
            )
        } else if (isEditMode && currentPathPoints.size == 1) {
            drawCircle(
                color = currentColor,
                radius = currentStrokeWidth / 2,
                center = currentPathPoints.first(),
                alpha = if (currentTool == AnnotationTool.HIGHLIGHTER) 0.5f else 1.0f
            )
        }
    }
}
