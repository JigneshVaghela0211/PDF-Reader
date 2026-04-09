package com.pdf.pdfreader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class SignatureStroke(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

@Composable
fun SignaturePadDialog(
    onDismissRequest: () -> Unit,
    onSaveSignature: (List<SignatureStroke>, width: Float, height: Float) -> Unit
) {
    var strokes by remember { mutableStateOf<List<SignatureStroke>>(emptyList()) }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var undoStack by remember { mutableStateOf<List<List<SignatureStroke>>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<List<SignatureStroke>>>(emptyList()) }

    var selectedColor by remember { mutableStateOf(Color.Black) }
    var selectedStrokeWidth by remember { mutableStateOf(5f) }

    val colors = listOf(Color.Black, Color(0xFF1565C0), Color(0xFFD32F2F))

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Draw Signature", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        colors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(color, RoundedCornerShape(16.dp))
                                    .border(
                                        width = if (selectedColor == color) 2.dp else 0.dp,
                                        color = if (selectedColor == color) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .pointerInput(color) {
                                        detectTapGestures {
                                            selectedColor = color
                                        }
                                    }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = {
                                if (strokes.isNotEmpty()) {
                                    redoStack = redoStack + listOf(strokes)
                                    strokes = undoStack.lastOrNull() ?: emptyList()
                                    undoStack = undoStack.dropLast(1)
                                }
                            },
                            enabled = strokes.isNotEmpty() || undoStack.isNotEmpty() // Actually, undo goes to history
                        ) {
                            Icon(Icons.Default.Undo, "Undo")
                        }
                        IconButton(
                            onClick = {
                                if (redoStack.isNotEmpty()) {
                                    undoStack = undoStack + listOf(strokes)
                                    strokes = redoStack.last()
                                    redoStack = redoStack.dropLast(1)
                                }
                            },
                            enabled = redoStack.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Redo, "Redo")
                        }
                        IconButton(
                            onClick = {
                                undoStack = undoStack + listOf(strokes)
                                strokes = emptyList()
                                redoStack = emptyList()
                            },
                            enabled = strokes.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Clear, "Clear")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Drawing Canvas
                var canvasWidth by remember { mutableStateOf(1f) }
                var canvasHeight by remember { mutableStateOf(1f) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                        .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                        .clipToBounds()
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(selectedColor, selectedStrokeWidth) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        undoStack = undoStack + listOf(strokes)
                                        redoStack = emptyList()
                                        currentPoints = listOf(offset)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentPoints = currentPoints + change.position
                                    },
                                    onDragEnd = {
                                        if (currentPoints.size >= 2) {
                                            strokes = strokes + SignatureStroke(
                                                points = currentPoints,
                                                color = selectedColor,
                                                strokeWidth = selectedStrokeWidth
                                            )
                                        }
                                        currentPoints = emptyList()
                                    },
                                    onDragCancel = {
                                        currentPoints = emptyList()
                                    }
                                )
                            }
                    ) {
                        canvasWidth = size.width
                        canvasHeight = size.height

                        // Draw all finished strokes
                        strokes.forEach { stroke ->
                            if (stroke.points.size >= 2) {
                                val path = Path().apply {
                                    moveTo(stroke.points.first().x, stroke.points.first().y)
                                    for (i in 1 until stroke.points.size) {
                                        lineTo(stroke.points[i].x, stroke.points[i].y)
                                    }
                                }
                                drawPath(
                                    path = path,
                                    color = stroke.color,
                                    style = Stroke(
                                        width = stroke.strokeWidth,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            } else if (stroke.points.size == 1) {
                                drawCircle(
                                    color = stroke.color,
                                    radius = stroke.strokeWidth / 2,
                                    center = stroke.points.first()
                                )
                            }
                        }

                        // Draw current stroke
                        if (currentPoints.size >= 2) {
                            val path = Path().apply {
                                moveTo(currentPoints.first().x, currentPoints.first().y)
                                for (i in 1 until currentPoints.size) {
                                    lineTo(currentPoints[i].x, currentPoints[i].y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = selectedColor,
                                style = Stroke(
                                    width = selectedStrokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        } else if (currentPoints.size == 1) {
                            drawCircle(
                                color = selectedColor,
                                radius = selectedStrokeWidth / 2,
                                center = currentPoints.first()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (strokes.isNotEmpty()) {
                                onSaveSignature(strokes, canvasWidth, canvasHeight)
                            }
                        },
                        enabled = strokes.isNotEmpty()
                    ) {
                        Text("Save Signature")
                    }
                }
            }
        }
    }
}
