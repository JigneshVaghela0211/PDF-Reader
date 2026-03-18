package com.pdf.pdfreader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.pdf.pdfreader.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.util.FitPolicy
import com.pdf.pdfreader.ui.viewmodel.PdfReaderViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    viewModel: PdfReaderViewModel,
    path: String,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberLazyListState() // Not used by lib but kept for structural consistency
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(path) {
        viewModel.initialize(path)
    }

    val lastLoadedFile = remember { mutableStateOf("") }
    val lastLoadedPassword = remember { mutableStateOf("") }
    val lastReloadTrigger = remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
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
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { context ->
                    PDFView(context, null)
                },
                modifier = Modifier.fillMaxSize(),
                update = { pdfView ->
                    if (lastLoadedFile.value != uiState.filePath || 
                        lastLoadedPassword.value != uiState.password ||
                        lastReloadTrigger.intValue != uiState.reloadTrigger) {
                        
                        pdfView.fromFile(File(uiState.filePath))
                            .password(if (uiState.password.isNotEmpty()) uiState.password else null)
                            .defaultPage(uiState.currentPage)
                            .enableSwipe(true)
                            .swipeHorizontal(false)
                            .enableDoubletap(true)
                            .onLoad { pages -> viewModel.onLoadComplete(pages) }
                            .onPageChange { page, count -> viewModel.onPageChanged(page, count) }
                            .onError { t -> viewModel.onError(t) }
                            .enableAntialiasing(true)
                            .spacing(10)
                            .pageFitPolicy(FitPolicy.WIDTH)
                            .fitEachPage(true)
                            .load()
                            
                        // Set zoom limits for high-detail areas (like barcodes)
                        pdfView.setMinZoom(1f)
                        pdfView.setMidZoom(3f)
                        pdfView.setMaxZoom(10f)
                            
                        lastLoadedFile.value = uiState.filePath
                        lastLoadedPassword.value = uiState.password
                        lastReloadTrigger.intValue = uiState.reloadTrigger
                    } else if (pdfView.currentPage != uiState.currentPage) {
                        pdfView.jumpTo(uiState.currentPage)
                    }
                }
            )

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
                            // Library handles internal scrolling sync if we use its listeners, 
                            // here we just want to jump to page.
                            // But usually we don't need a slider with PDFView as it's a native scrollable.
                            viewModel.updateCurrentPage(page.toInt())
                            // No need to scroll LazyColumn anymore, PDFView handles it
                        },
                        valueRange = 0f..(uiState.totalPages - 1).toFloat(),
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
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
