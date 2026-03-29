package com.pdf.pdfreader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.pdf.pdfreader.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdf.pdfreader.ui.viewmodel.PdfReaderViewModel
import com.pdf.pdfreader.ui.components.AnnotationTool
import com.pdf.pdfreader.ui.components.PdfAnnotationOverlay
import com.pdf.pdfreader.ui.components.AnnotationTopBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    viewModel: PdfReaderViewModel,
    path: String,
    initialPageIndex: Int = -1,
    searchQuery: String? = null,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(path) {
        viewModel.initialize(path)
    }

    LaunchedEffect(uiState.totalPages, initialPageIndex) {
        if (uiState.totalPages > 0 && initialPageIndex in 0 until uiState.totalPages) {
            scrollState.scrollToItem(initialPageIndex)
            viewModel.updateCurrentPage(initialPageIndex)
        }
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 10f)
        if (scale > 1f) {
            offsetX += offsetChange.x
            offsetY += offsetChange.y
        } else {
            offsetX = 0f
            offsetY = 0f
        }
    }

    Scaffold(
        topBar = {
            if (uiState.isEditMode) {
                AnnotationTopBar(
                    currentTool = uiState.currentTool,
                    currentColor = uiState.currentColor,
                    currentStrokeWidth = uiState.currentStrokeWidth,
                    onToolChange = viewModel::setAnnotationTool,
                    onColorChange = viewModel::setAnnotationColor,
                    onStrokeWidthChange = viewModel::setAnnotationStrokeWidth,
                    onClose = { viewModel.setEditMode(false) },
                    onSave = { viewModel.saveAnnotationsToPdf(screenWidthPx) }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = uiState.fileName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            if (uiState.totalPages > 0) {
                                Text(
                                    text = "${stringResource(R.string.page)} ${uiState.currentPage + 1} ${stringResource(R.string.of)} ${uiState.totalPages}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.setEditMode(true) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        if (uiState.totalPages > 0) {
                            Text(
                                text = "${((uiState.currentPage + 1).toFloat() / uiState.totalPages * 100).toInt()}%",
                                modifier = Modifier.padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (!uiState.isLoading && uiState.totalPages > 0) {
                LazyColumn(
                    state = scrollState,
                    userScrollEnabled = !uiState.isEditMode,
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(state = transformableState)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                ) {
                    items(uiState.totalPages, key = { it }) { pageIndex ->
                        PdfPage(
                            pageIndex = pageIndex,
                            viewModel = viewModel,
                            width = screenWidthPx,
                            searchQuery = searchQuery
                        )
                    }
                }

                LaunchedEffect(scrollState.firstVisibleItemIndex) {
                    viewModel.updateCurrentPage(scrollState.firstVisibleItemIndex)
                }
            }

            if (uiState.totalPages > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp, start = 24.dp, end = 24.dp)
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Slider(
                        value = uiState.currentPage.toFloat(),
                        onValueChange = { page -> 
                            viewModel.updateCurrentPage(page.toInt())
                            coroutineScope.launch {
                                scrollState.scrollToItem(page.toInt())
                            }
                        },
                        valueRange = 0f..(uiState.totalPages - 1).coerceAtLeast(1).toFloat(),
                        steps = if (uiState.totalPages > 2) uiState.totalPages - 2 else 0,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                    )
                }
            }
            
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp
                )
            }

            if (uiState.isPasswordPromptVisible) {
                PasswordPromptDialog(
                    fileName = uiState.fileName,
                    password = uiState.password,
                    isError = !uiState.isPasswordCorrect,
                    onPasswordChange = { viewModel.onPasswordChange(it) },
                    onDismiss = onNavigateBack,
                    onSubmit = { viewModel.submitPassword(uiState.password) }
                )
            }

            uiState.errorMessage?.let { message ->
                ErrorView(
                    message = message,
                    onRetry = { viewModel.initialize(uiState.filePath) },
                    onExit = onNavigateBack
                )
            }
        }
    }
}

@Composable
fun PdfPage(pageIndex: Int, viewModel: PdfReaderViewModel, width: Int, searchQuery: String? = null) {
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var highlights by remember { mutableStateOf<List<androidx.compose.ui.geometry.Rect>>(emptyList()) }
    
    LaunchedEffect(pageIndex, width) {
        bitmap = viewModel.getPageBitmap(pageIndex, width)
        if (!searchQuery.isNullOrEmpty() && bitmap != null) {
            highlights = viewModel.getSearchHighlights(pageIndex, searchQuery, width, bitmap!!.height)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
                if (highlights.isNotEmpty()) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                        highlights.forEach { rect ->
                            drawRect(
                                color = Color.Yellow.copy(alpha = 0.4f),
                                topLeft = androidx.compose.ui.geometry.Offset(rect.left, rect.top),
                                size = androidx.compose.ui.geometry.Size(rect.width, rect.height)
                            )
                        }
                    }
                }
                
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                PdfAnnotationOverlay(
                    modifier = Modifier.matchParentSize(),
                    isEditMode = uiState.isEditMode,
                    currentTool = uiState.currentTool,
                    currentColor = uiState.currentColor,
                    currentStrokeWidth = uiState.currentStrokeWidth,
                    annotations = uiState.annotations,
                    onAnnotationAdded = viewModel::addAnnotation,
                    onAnnotationRemoved = viewModel::removeAnnotation,
                    pageIndex = pageIndex
                )
                
                uiState.annotations.filterIsInstance<com.pdf.pdfreader.domain.model.PdfAnnotation.TextNote>()
                    .filter { it.pageIndex == pageIndex }
                    .forEach { textNote ->
                        key(textNote.id) {
                            com.pdf.pdfreader.ui.components.MovableTextNote(
                                note = textNote,
                                isEditMode = uiState.isEditMode,
                                onUpdate = viewModel::updateAnnotation,
                                onDelete = { viewModel.removeAnnotation(textNote.id) }
                            )
                        }
                    }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f/1.414f) // Standard A4 ratio as placeholder
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun PasswordPromptDialog(
    fileName: String,
    password: String,
    isError: Boolean,
    onPasswordChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.enter_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${stringResource(R.string.pdf_locked_desc).replace("PDF", "\"$fileName\"")}",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.enter_password)) },
                    isError = isError,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val icon = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(icon, contentDescription = null)
                        }
                    },
                    supportingText = {
                        if (isError) {
                            Text(stringResource(R.string.incorrect_password), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = password.isNotEmpty(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.open_pdf))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    onExit: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = stringResource(R.string.opening_pdf_failed),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.go_back))
                }
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}
