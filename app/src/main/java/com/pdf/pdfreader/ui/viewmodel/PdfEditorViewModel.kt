package com.pdf.pdfreader.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdf.pdfreader.core.config.PdfEditorFeatureConfig
import com.pdf.pdfreader.core.model.EditorFeature
import com.pdf.pdfreader.data.local.CommandSerializer
import com.pdf.pdfreader.domain.model.*
import com.pdf.pdfreader.domain.usecase.UndoRedoManager
import com.pdf.pdfreader.ui.components.AnnotationTool
import com.pdf.pdfreader.utiles.PdfExportManager
import com.pdf.pdfreader.utiles.PdfTextBlockExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ─── Editor UI State ─────────────────────────────────────────────
data class PdfEditorUiState(
    val isEditMode: Boolean = false,
    val currentTool: AnnotationTool = AnnotationTool.PEN,
    val currentColor: Color = Color.Red,
    val currentStrokeWidth: Float = 5f,
    val interactionMode: InteractionMode = InteractionMode.NONE,
    val annotations: List<PdfAnnotation> = emptyList(),
    val selectedAnnotationId: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val textBlocks: Map<Int, List<TextBlock>> = emptyMap(),
    val editedTextBlocks: List<EditedTextBlock> = emptyList(),
    val selectedTextBlockId: String? = null,
    val isTextBlocksLoading: Boolean = false,
    val textSelection: TextSelectionState? = null,
    val imageElements: List<ImageElement> = emptyList(),
    val selectedImageId: String? = null,
    val selectedImageIds: Set<String> = emptySet(),
    val savedSignatures: List<String> = emptyList(),
    val isSignaturePadVisible: Boolean = false,
    val isSignatureSheetVisible: Boolean = false,
    val isExporting: Boolean = false,
    val exportResult: String? = null
) {
    val hasEditableOverlays: Boolean
        get() = editedTextBlocks.isNotEmpty() || imageElements.isNotEmpty()

    val selectedElementType: SelectedElementType
        get() = when {
            selectedImageId != null -> {
                val img = imageElements.find { it.id == selectedImageId }
                if (img?.isSignature == true) SelectedElementType.SIGNATURE
                else SelectedElementType.IMAGE
            }
            selectedImageIds.size >= 2 -> SelectedElementType.GROUP
            selectedTextBlockId != null -> SelectedElementType.TEXT
            else -> SelectedElementType.NONE
        }
}

@HiltViewModel
class PdfEditorViewModel @Inject constructor(
    application: Application,
    private val undoRedoManager: UndoRedoManager,
    private val textBlockExtractor: PdfTextBlockExtractor,
    private val pdfExportManager: PdfExportManager,
    private val signatureManager: com.pdf.pdfreader.domain.repository.SignatureManager
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PdfEditorVM"
    }

    private val _uiState = MutableStateFlow(PdfEditorUiState())
    val uiState = _uiState.asStateFlow()

    private var pdfFilePath: String = ""
    private var currentPage: Int = 0

    // ─── Lifecycle ────────────────────────────────────────────────

    fun initialize(filePath: String) {
        if (pdfFilePath == filePath) return
        pdfFilePath = filePath
        viewModelScope.launch {
            val sigs = signatureManager.getAllSignatureUris()
            _uiState.update { it.copy(savedSignatures = sigs) }
            undoRedoManager.restoreState(filePath)
            _uiState.update {
                it.copy(canUndo = undoRedoManager.canUndo.value, canRedo = undoRedoManager.canRedo.value)
            }
        }
        // Proactively extract text words so long-press text selection (and the
        // Highlight / Underline / Strikethrough popup) works in plain reading mode,
        // not only after the Edit Text tool has been opened. Runs async; safe no-op
        // if already extracted.
        extractTextBlocks()
    }

    fun setCurrentPage(page: Int) { currentPage = page }

    private fun syncUndoRedoState() {
        _uiState.update { it.copy(canUndo = undoRedoManager.canUndo.value, canRedo = undoRedoManager.canRedo.value) }
    }

    // ─── Edit Mode & Tool State ──────────────────────────────────

    fun setEditMode(isEditMode: Boolean) {
        _uiState.update {
            if (isEditMode) {
                it.copy(isEditMode = true)
            } else {
                // Exiting edit mode: reset the active tool so overlay-based tools like
                // EDIT_TEXT (which highlights every text block in light yellow) stop
                // rendering, and clear any in-progress selections.
                it.copy(
                    isEditMode = false,
                    currentTool = AnnotationTool.NONE,
                    selectedTextBlockId = null,
                    selectedImageId = null,
                    selectedImageIds = emptySet()
                )
            }
        }
    }
    fun setAnnotationTool(tool: AnnotationTool) { _uiState.update { it.copy(currentTool = tool) } }
    fun setAnnotationColor(color: Color) { _uiState.update { it.copy(currentColor = color) } }
    fun setAnnotationStrokeWidth(width: Float) { _uiState.update { it.copy(currentStrokeWidth = width) } }

    fun setAnnotationToolWithAutoExtract(tool: AnnotationTool) {
        _uiState.update { it.copy(currentTool = tool) }
        if (tool == AnnotationTool.EDIT_TEXT) extractTextBlocks()
    }

    fun setInteractionMode(mode: InteractionMode) { _uiState.update { it.copy(interactionMode = mode) } }

    // ─── Annotations ─────────────────────────────────────────────

    fun addAnnotation(annotation: PdfAnnotation) {
        if (annotation is PdfAnnotation.TextNote) {
            _uiState.update { it.copy(annotations = it.annotations + annotation) }
            return
        }
        _uiState.update { it.copy(annotations = it.annotations + annotation) }
        viewModelScope.launch {
            val command = annotationToCommand(annotation)
            if (command != null) undoRedoManager.execute(command)
            syncUndoRedoState()
        }
    }

    fun removeAnnotation(id: String) {
        val annotation = _uiState.value.annotations.find { it.id == id } ?: return
        _uiState.update { it.copy(annotations = it.annotations.filter { a -> a.id != id }) }
        viewModelScope.launch {
            val command = annotationToCommand(annotation)
            if (command != null) {
                val removeCmd = AnnotationCommand.RemoveAnnotation(
                    id = java.util.UUID.randomUUID().toString(),
                    pdfPath = pdfFilePath,
                    pageIndex = annotation.pageIndex,
                    timestamp = System.currentTimeMillis(),
                    annotationId = annotation.id,
                    removedAnnotationPayload = serializeAnnotation(annotation)
                )
                undoRedoManager.execute(removeCmd)
                syncUndoRedoState()
            }
        }
    }

    fun updateAnnotation(annotation: PdfAnnotation) {
        _uiState.update { state ->
            state.copy(annotations = state.annotations.map { if (it.id == annotation.id) annotation else it })
        }
    }

    fun selectAnnotation(id: String?) { _uiState.update { it.copy(selectedAnnotationId = id) } }

    fun textNoteToState(note: PdfAnnotation.TextNote): AnnotationCommand.TextState {
        return AnnotationCommand.TextState(
            id = note.id, text = note.text,
            color = note.color.value.toLong(), fontSize = note.fontSize,
            positionX = note.position.x, positionY = note.position.y,
            rotation = note.rotation
        )
    }

    private fun stateToTextNote(state: AnnotationCommand.TextState, pageIndex: Int): PdfAnnotation.TextNote {
        return PdfAnnotation.TextNote(
            id = state.id, pageIndex = pageIndex,
            text = state.text,
            position = Offset(state.positionX, state.positionY),
            color = Color(state.color.toULong()),
            fontSize = state.fontSize,
            rotation = state.rotation
        )
    }

    fun commitTextAnnotation(before: AnnotationCommand.TextState?, after: AnnotationCommand.TextState, pageIndex: Int) {
        val updated = stateToTextNote(after, pageIndex)
        _uiState.update { state ->
            state.copy(annotations = state.annotations.map { if (it.id == after.id) updated else it })
        }
        viewModelScope.launch {
            val command = AnnotationCommand.TextCommand(
                id = java.util.UUID.randomUUID().toString(),
                pdfPath = pdfFilePath, pageIndex = pageIndex,
                timestamp = System.currentTimeMillis(),
                before = before, after = after
            )
            undoRedoManager.execute(command)
            syncUndoRedoState()
        }
    }

    // ─── Text Block Extraction & Editing ─────────────────────────

    fun extractTextBlocks() {
        if (pdfFilePath.isEmpty() || _uiState.value.textBlocks.isNotEmpty()) return
        _uiState.update { it.copy(isTextBlocksLoading = true) }
        viewModelScope.launch {
            val blocks = textBlockExtractor.extractTextBlocks(pdfFilePath)
            _uiState.update { it.copy(textBlocks = blocks, isTextBlocksLoading = false) }
        }
    }

    fun selectTextBlock(id: String?) { _uiState.update { it.copy(selectedTextBlockId = id) } }

    fun editTextBlock(blockId: String, newText: String, newFontSize: Float, newColor: Color) {
        val allBlocks = _uiState.value.textBlocks.values.flatten()
        val originalBlock = allBlocks.find { it.id == blockId } ?: return
        val existingEdit = _uiState.value.editedTextBlocks.find { it.originalBlock.id == blockId }

        val newEditedBlock = EditedTextBlock(
            id = existingEdit?.id ?: java.util.UUID.randomUUID().toString(),
            originalBlock = originalBlock,
            newText = newText,
            newFontSize = newFontSize,
            newColor = newColor
        )

        _uiState.update { state ->
            val updatedEdits = if (existingEdit != null) {
                state.editedTextBlocks.map { if (it.originalBlock.id == blockId) newEditedBlock else it }
            } else {
                state.editedTextBlocks + newEditedBlock
            }
            state.copy(editedTextBlocks = updatedEdits, selectedTextBlockId = null)
        }

        viewModelScope.launch {
            val beforeState = existingEdit?.let {
                AnnotationCommand.EditTextState(
                    blockId = blockId,
                    originalText = it.originalBlock.text, newText = it.newText,
                    originalFontSize = it.originalBlock.fontSize, newFontSize = it.newFontSize,
                    newColor = it.newColor.value.toLong(),
                    x = it.originalBlock.x, y = it.originalBlock.y,
                    width = it.originalBlock.width, height = it.originalBlock.height
                )
            }
            val afterState = AnnotationCommand.EditTextState(
                blockId = blockId,
                originalText = originalBlock.text, newText = newText,
                originalFontSize = originalBlock.fontSize, newFontSize = newFontSize,
                newColor = newColor.value.toLong(),
                x = originalBlock.x, y = originalBlock.y,
                width = originalBlock.width, height = originalBlock.height
            )
            val command = AnnotationCommand.EditTextCommand(
                id = java.util.UUID.randomUUID().toString(),
                pdfPath = pdfFilePath, pageIndex = originalBlock.pageIndex,
                timestamp = System.currentTimeMillis(),
                before = beforeState, after = afterState
            )
            undoRedoManager.execute(command)
            syncUndoRedoState()
        }
    }

    // ─── Text Selection ──────────────────────────────────────────

    fun startTextSelection(pageIndex: Int, word: TextWord, allWords: List<TextWord>) {
        _uiState.update { state ->
            state.copy(
                interactionMode = InteractionMode.SELECT_TEXT,
                textSelection = TextSelectionState(
                    pageIndex = pageIndex, selectedWords = listOf(word), bounds = null
                )
            )
        }
    }

    fun updateTextSelection(pageIndex: Int, endWord: TextWord, allWords: List<TextWord>) {
        val sel = _uiState.value.textSelection ?: return
        if (sel.pageIndex != pageIndex) return
        val startWord = sel.selectedWords.firstOrNull() ?: return
        val startIdx = allWords.indexOf(startWord); val endIdx = allWords.indexOf(endWord)
        if (startIdx == -1 || endIdx == -1) return
        val actualStart = minOf(startIdx, endIdx); val actualEnd = maxOf(startIdx, endIdx)
        val selectedRange = allWords.subList(actualStart, actualEnd + 1)
        _uiState.update { it.copy(textSelection = sel.copy(selectedWords = selectedRange)) }
    }

    fun finalizeTextSelection() {
        val sel = _uiState.value.textSelection ?: return
        if (sel.selectedWords.isEmpty()) { clearTextSelection(); return }
        val minX = sel.selectedWords.minOf { it.x }; val minY = sel.selectedWords.minOf { it.y }
        val maxX = sel.selectedWords.maxOf { it.x + it.width }; val maxY = sel.selectedWords.maxOf { it.y + it.height }
        val bounds = androidx.compose.ui.geometry.Rect(minX, minY, maxX, maxY)
        _uiState.update { it.copy(textSelection = sel.copy(bounds = bounds)) }
    }

    fun clearTextSelection() { _uiState.update { it.copy(textSelection = null) } }

    /**
     * Copy the current text selection to the Android clipboard, preserving spaces and inserting
     * line breaks between visual lines (words are in reading order, so a downward jump in y marks
     * a new line). Returns true if something was actually copied — the UI uses this to confirm.
     */
    fun copySelectedText(context: android.content.Context): Boolean {
        if (!PdfEditorFeatureConfig.ENABLE_COPY_TEXT) return false
        val words = _uiState.value.textSelection?.selectedWords ?: return false
        if (words.isEmpty()) return false

        val sb = StringBuilder()
        var prev: TextWord? = null
        for (w in words) {
            prev?.let { sb.append(if (w.y - it.y > it.height * 0.6f) "\n" else " ") }
            sb.append(w.text)
            prev = w
        }
        val text = sb.toString()
        if (text.isBlank()) return false

        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("PDF Text", text))
        clearTextSelection()
        return true
    }

    fun editSelectedText() {
        if (!PdfEditorFeatureConfig.ENABLE_EDIT_TEXT) return
        val sel = _uiState.value.textSelection ?: return
        val words = sel.selectedWords
        if (words.isEmpty()) return
        
        val blocksOnPage = _uiState.value.textBlocks[sel.pageIndex] ?: emptyList()
        val firstWord = words.first()
        val cx = firstWord.x + firstWord.width / 2f
        val cy = firstWord.y + firstWord.height / 2f
        // Prefer the block whose bounds contain the word's center; otherwise pick the nearest
        // block so Edit always lands on something rather than silently doing nothing.
        val block = blocksOnPage.firstOrNull { b ->
            cx >= b.x && cx <= b.x + b.width && cy >= b.y && cy <= b.y + b.height
        } ?: blocksOnPage.minByOrNull { b ->
            val bcx = b.x + b.width / 2f; val bcy = b.y + b.height / 2f
            (bcx - cx) * (bcx - cx) + (bcy - cy) * (bcy - cy)
        }
        clearTextSelection()
        if (block != null) {
            _uiState.update { it.copy(isEditMode = true, currentTool = AnnotationTool.EDIT_TEXT) }
            selectTextBlock(block.id)
        }
    }

    fun annotateSelectedText(type: PdfAnnotation.MarkupType) {
        val markupFeature = when (type) {
            PdfAnnotation.MarkupType.HIGHLIGHT -> EditorFeature.HIGHLIGHT
            PdfAnnotation.MarkupType.UNDERLINE -> EditorFeature.UNDERLINE
            PdfAnnotation.MarkupType.STRIKETHROUGH -> EditorFeature.STRIKETHROUGH
        }
        if (!PdfEditorFeatureConfig.isEnabled(markupFeature)) return
        val sel = _uiState.value.textSelection ?: return
        val rects = sel.selectedWords.map { w ->
            androidx.compose.ui.geometry.Rect(w.x, w.y, w.x + w.width, w.y + w.height)
        }
        val color = when (type) {
            PdfAnnotation.MarkupType.HIGHLIGHT -> Color(0xFFFFEB3B).copy(alpha = 0.4f)
            PdfAnnotation.MarkupType.UNDERLINE -> Color(0xFFE53935)
            PdfAnnotation.MarkupType.STRIKETHROUGH -> Color.Red
        }
        val annotation = PdfAnnotation.TextMarkup(
            pageIndex = sel.pageIndex, rects = rects, color = color, type = type
        )
        addAnnotation(annotation)
        clearTextSelection()
    }

    // ─── Signature Methods ───────────────────────────────────────

    fun setSignatureSheetVisible(visible: Boolean) { _uiState.update { it.copy(isSignatureSheetVisible = visible) } }
    fun setSignaturePadVisible(visible: Boolean) { _uiState.update { it.copy(isSignaturePadVisible = visible) } }

    fun saveSignature(strokes: List<com.pdf.pdfreader.ui.components.SignatureStroke>, width: Float, height: Float) {
        viewModelScope.launch {
            val uri = signatureManager.saveSignature(strokes, width, height)
            val updatedList = signatureManager.getAllSignatureUris()
            val serializableStrokes = strokes.map { SerializableStroke.fromComposeStroke(it) }

            _uiState.update {
                it.copy(savedSignatures = updatedList, isSignaturePadVisible = false, isSignatureSheetVisible = false)
            }
            insertSignatureWithStrokes(uri, serializableStrokes, width, height)
        }
    }

    fun deleteSignature(uri: String) {
        viewModelScope.launch {
            signatureManager.deleteSignature(uri)
            val updatedList = signatureManager.getAllSignatureUris()
            _uiState.update { it.copy(savedSignatures = updatedList) }
        }
    }

    fun insertSignatureAsImage(uri: String) {
        if (!PdfEditorFeatureConfig.ENABLE_SIGNATURE) return
        viewModelScope.launch {
            // If editable stroke data was persisted for this signature, insert it as an
            // editable signature (thickness / colour can be changed). Otherwise fall back
            // to a plain image (older signatures saved before stroke persistence existed).
            val data = signatureManager.loadSignatureData(uri)
            setSignatureSheetVisible(false)
            if (data != null) {
                insertSignatureWithStrokes(uri, data.strokes, data.canvasWidth, data.canvasHeight)
            } else {
                insertImageUri(uri, 300f, 150f, isSignature = true)
            }
        }
    }

    private fun insertSignatureWithStrokes(uri: String, strokes: List<SerializableStroke>, canvasWidth: Float, canvasHeight: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val element = ImageElement(
                    pageIndex = currentPage, uri = uri,
                    position = Offset(100f, 100f), width = 300f, height = 150f,
                    scale = 1f, rotation = 0f, opacity = 1f, isLocked = false, zIndex = 10,
                    isSignature = true, signatureStrokes = strokes,
                    signatureCanvasWidth = canvasWidth, signatureCanvasHeight = canvasHeight
                )
                _uiState.update { state ->
                    state.copy(imageElements = state.imageElements + element,
                        selectedImageId = element.id, isEditMode = true, currentTool = AnnotationTool.NONE)
                }
            } catch (e: Exception) { Log.e(TAG, "Failed to insert signature", e) }
        }
    }

    fun updateSignatureProperties(elementId: String, newColor: Color? = null, newStrokeWidth: Float? = null) {
        val element = _uiState.value.imageElements.find { it.id == elementId } ?: return
        val currentStrokes = element.signatureStrokes ?: return
        val previousUri = element.uri
        viewModelScope.launch {
            try {
                val updatedStrokes = currentStrokes.map { stroke ->
                    stroke.copy(
                        color = newColor?.value?.toLong() ?: stroke.color,
                        strokeWidth = newStrokeWidth ?: stroke.strokeWidth
                    )
                }
                val composeStrokes = updatedStrokes.map { it.toComposeStroke() }
                val newUri = signatureManager.reRenderSignature(
                    composeStrokes,
                    element.signatureCanvasWidth,
                    element.signatureCanvasHeight
                )
                // Discard the previous re-render temp so edits don't pile up. This only
                // deletes cache temps — the original saved signature is left untouched.
                signatureManager.deleteRerenderFile(previousUri)
                withContext(Dispatchers.Main) {
                    _uiState.update { state ->
                        state.copy(imageElements = state.imageElements.map {
                            if (it.id == elementId) it.copy(uri = newUri, signatureStrokes = updatedStrokes, bitmapVersion = it.bitmapVersion + 1)
                            else it
                        })
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "Failed to update signature", e) }
        }
    }

    // ─── Image Element Operations ────────────────────────────────

    fun addImage(uri: Uri, pageIndex: Int, viewWidth: Int) {
        if (!PdfEditorFeatureConfig.ENABLE_INSERT_IMAGE) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                inputStream?.use { android.graphics.BitmapFactory.decodeStream(it, null, options) }
                val imgWidth = options.outWidth.toFloat(); val imgHeight = options.outHeight.toFloat()
                if (imgWidth <= 0 || imgHeight <= 0) return@launch
                val maxWidth = viewWidth * 0.5f
                val scale = if (imgWidth > maxWidth) maxWidth / imgWidth else 1f
                val displayWidth = imgWidth * scale; val displayHeight = imgHeight * scale
                val posX = (viewWidth - displayWidth) / 2f

                val element = ImageElement(
                    pageIndex = pageIndex, uri = uri.toString(),
                    position = Offset(posX, 100f), width = displayWidth, height = displayHeight
                )
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(imageElements = it.imageElements + element, selectedImageId = element.id) }
                }
                val command = AnnotationCommand.AddImageCommand(
                    id = java.util.UUID.randomUUID().toString(), pdfPath = pdfFilePath,
                    pageIndex = pageIndex, timestamp = System.currentTimeMillis(),
                    imageState = AnnotationCommand.ImageState(
                        elementId = element.id, uri = uri.toString(),
                        positionX = posX, positionY = 100f,
                        width = displayWidth, height = displayHeight, scale = 1f, rotation = 0f
                    )
                )
                undoRedoManager.execute(command); syncUndoRedoState()
            } catch (e: Exception) { Log.e(TAG, "Failed to add image", e) }
        }
    }

    private fun insertImageUri(uriString: String, width: Float, height: Float, isSignature: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val element = ImageElement(
                    pageIndex = currentPage, uri = uriString,
                    position = Offset(100f, 100f), width = width, height = height,
                    scale = 1f, rotation = 0f, opacity = 1f, isLocked = false, zIndex = 10,
                    isSignature = isSignature
                )
                _uiState.update { state ->
                    state.copy(imageElements = state.imageElements + element, selectedImageId = element.id,
                        isEditMode = true,
                        currentTool = if (isSignature) AnnotationTool.NONE else AnnotationTool.INSERT_IMAGE)
                }
            } catch (e: Exception) { Log.e(TAG, "Failed to insert image", e) }
        }
    }

    fun selectImage(id: String?) {
        _uiState.update { it.copy(selectedImageId = id, selectedImageIds = if (id == null) emptySet() else setOf(id)) }
    }

    fun toggleImageSelection(id: String) {
        _uiState.update { state ->
            val newSel = if (state.selectedImageIds.contains(id)) state.selectedImageIds - id else state.selectedImageIds + id
            state.copy(selectedImageIds = newSel, selectedImageId = newSel.lastOrNull())
        }
    }

    fun groupSelectedImages() {
        val state = _uiState.value; if (state.selectedImageIds.size < 2) return
        val gid = java.util.UUID.randomUUID().toString()
        _uiState.update { s -> s.copy(
            imageElements = s.imageElements.map { if (s.selectedImageIds.contains(it.id)) it.copy(groupId = gid) else it },
            selectedImageIds = emptySet(), selectedImageId = null
        )}
    }

    fun ungroupSelectedImage() {
        val id = _uiState.value.selectedImageId ?: return
        val gid = _uiState.value.imageElements.find { it.id == id }?.groupId ?: return
        _uiState.update { s -> s.copy(imageElements = s.imageElements.map { if (it.groupId == gid) it.copy(groupId = null) else it }) }
    }

    // ─── Drag (Performance-Optimized) ────────────────────────────

    private val cachedScreenWidth: Float by lazy { getApplication<Application>().resources.displayMetrics.widthPixels.toFloat() }
    private val cachedScreenHeight: Float by lazy { getApplication<Application>().resources.displayMetrics.heightPixels.toFloat() }
    private val groupMoveStartStates = mutableMapOf<String, Offset>()

    fun moveImage(id: String, delta: Offset) {
        val state = _uiState.value
        val element = state.imageElements.find { it.id == id } ?: return
        if (element.isLocked) return
        val moveIds = buildSet {
            add(id)
            if (element.groupId != null) { state.imageElements.forEach { if (it.groupId == element.groupId) add(it.id) } }
            addAll(state.selectedImageIds)
        }
        if (groupMoveStartStates.isEmpty()) {
            state.imageElements.forEach { if (moveIds.contains(it.id)) groupMoveStartStates[it.id] = it.position }
        }
        _uiState.update { s ->
            val list = s.imageElements.toMutableList(); var changed = false
            for (i in list.indices) {
                val elem = list[i]; if (!moveIds.contains(elem.id)) continue
                val vis = 0.2f
                val clampedX = kotlin.math.round((elem.position.x + delta.x).coerceIn(-elem.width * elem.scale * (1f - vis), cachedScreenWidth - elem.width * elem.scale * vis))
                val clampedY = kotlin.math.round((elem.position.y + delta.y).coerceIn(-elem.height * elem.scale * (1f - vis), cachedScreenHeight - elem.height * elem.scale * vis))
                if (clampedX != elem.position.x || clampedY != elem.position.y) { list[i] = elem.copy(position = Offset(clampedX, clampedY)); changed = true }
            }
            if (changed) s.copy(imageElements = list) else s
        }
    }

    fun onMoveEnd(id: String) {
        if (groupMoveStartStates.isEmpty()) return
        val commands = groupMoveStartStates.mapNotNull { (eid, startPos) ->
            val el = _uiState.value.imageElements.find { it.id == eid } ?: return@mapNotNull null
            if (startPos == el.position) return@mapNotNull null
            AnnotationCommand.MoveImageCommand(
                id = java.util.UUID.randomUUID().toString(), pdfPath = pdfFilePath,
                pageIndex = el.pageIndex, timestamp = System.currentTimeMillis(),
                elementId = eid, beforeX = startPos.x, beforeY = startPos.y, afterX = el.position.x, afterY = el.position.y
            )
        }
        groupMoveStartStates.clear(); if (commands.isEmpty()) return
        viewModelScope.launch {
            if (commands.size == 1) undoRedoManager.execute(commands.first())
            else undoRedoManager.execute(AnnotationCommand.CompositeCommand(
                id = java.util.UUID.randomUUID().toString(), pdfPath = pdfFilePath,
                pageIndex = -1, timestamp = System.currentTimeMillis(), commands = commands
            ))
            syncUndoRedoState()
        }
    }

    fun detectPageBoundaryAfterMove(id: String, scrollState: androidx.compose.foundation.lazy.LazyListState) {
        val element = _uiState.value.imageElements.find { it.id == id } ?: return
        val pageLayouts = scrollState.layoutInfo.visibleItemsInfo
        val globalY = (pageLayouts.find { it.index == element.pageIndex }?.offset?.toFloat() ?: 0f) + element.position.y
        val scaledH = element.height * element.scale
        for (item in pageLayouts) {
            val pageTop = item.offset.toFloat(); val pageBottom = pageTop + item.size.toFloat()
            val overlapFraction = if (scaledH > 0) (minOf(globalY + scaledH, pageBottom) - maxOf(globalY, pageTop)) / scaledH else 0f
            if (overlapFraction > 0.5f && item.index != element.pageIndex) {
                _uiState.update { state -> state.copy(imageElements = state.imageElements.map {
                    if (it.id == id) it.copy(pageIndex = item.index, position = Offset(it.position.x, globalY - pageTop)) else it
                })}
                break
            }
        }
    }

    // ─── Shared Helpers ──────────────────────────────────────────

    private fun elementToImageState(elem: ImageElement) = AnnotationCommand.ImageState(
        elementId = elem.id, uri = elem.uri,
        positionX = elem.position.x, positionY = elem.position.y,
        width = elem.width, height = elem.height,
        scale = elem.scale, rotation = elem.rotation,
        opacity = elem.opacity, isLocked = elem.isLocked, zIndex = elem.zIndex
    )

    // ─── Resize ──────────────────────────────────────────────────

    private val resizeStartStates = mutableMapOf<String, ImageElement>()

    fun resizeImage(id: String, handle: ResizeHandle, delta: Offset) {
        val element = _uiState.value.imageElements.find { it.id == id } ?: return
        if (element.isLocked) return
        if (!resizeStartStates.containsKey(id)) resizeStartStates[id] = element

        val ar = element.width / element.height
        val cw = element.width * element.scale; val ch = element.height * element.scale; val min = 30f
        val (nw, nh, nx, ny) = when (handle) {
            ResizeHandle.TOP_LEFT -> { val w = (cw - delta.x).coerceAtLeast(min); val h = (w / ar).coerceAtLeast(min); arrayOf(w, h, element.position.x + (cw - w), element.position.y + (ch - h)) }
            ResizeHandle.TOP_RIGHT -> { val w = (cw + delta.x).coerceAtLeast(min); val h = (w / ar).coerceAtLeast(min); arrayOf(w, h, element.position.x, element.position.y + (ch - h)) }
            ResizeHandle.BOTTOM_LEFT -> { val w = (cw - delta.x).coerceAtLeast(min); val h = (w / ar).coerceAtLeast(min); arrayOf(w, h, element.position.x + (cw - w), element.position.y) }
            ResizeHandle.BOTTOM_RIGHT -> { val w = (cw + delta.x).coerceAtLeast(min); val h = (w / ar).coerceAtLeast(min); arrayOf(w, h, element.position.x, element.position.y) }
            else -> arrayOf(cw, ch, element.position.x, element.position.y) // Center handles: no-op for now
        }
        _uiState.update { s -> s.copy(imageElements = s.imageElements.map {
            if (it.id == id) it.copy(width = nw / it.scale, height = nh / it.scale, position = Offset(nx, ny)) else it
        })}
    }

    fun resizeGroup(ids: Set<String>, handle: ResizeHandle, delta: Offset, groupW: Float, groupH: Float) {
        ids.forEach { eid -> if (!resizeStartStates.containsKey(eid)) { _uiState.value.imageElements.find { it.id == eid }?.let { resizeStartStates[eid] = it } } }
        val sfx = when (handle) { ResizeHandle.TOP_LEFT, ResizeHandle.BOTTOM_LEFT -> ((groupW - delta.x) / groupW).coerceIn(0.1f, 5f); else -> ((groupW + delta.x) / groupW).coerceIn(0.1f, 5f) }
        val sfy = when (handle) { ResizeHandle.TOP_LEFT, ResizeHandle.TOP_RIGHT -> ((groupH - delta.y) / groupH).coerceIn(0.1f, 5f); else -> ((groupH + delta.y) / groupH).coerceIn(0.1f, 5f) }
        val sf = minOf(sfx, sfy)
        _uiState.update { s -> s.copy(imageElements = s.imageElements.map { elem ->
            if (!ids.contains(elem.id)) return@map elem
            val ss = resizeStartStates[elem.id] ?: return@map elem
            elem.copy(scale = (ss.scale * sf).coerceIn(0.1f, 10f))
        })}
    }

    fun onResizeGroupEnd(ids: Set<String>) {
        val commands = ids.mapNotNull { eid ->
            val start = resizeStartStates[eid] ?: return@mapNotNull null
            val final = _uiState.value.imageElements.find { it.id == eid } ?: return@mapNotNull null
            AnnotationCommand.ResizeImageCommand(id = java.util.UUID.randomUUID().toString(), pdfPath = pdfFilePath,
                pageIndex = final.pageIndex, timestamp = System.currentTimeMillis(),
                elementId = eid,
                beforeWidth = start.width, beforeHeight = start.height,
                beforeX = start.position.x, beforeY = start.position.y,
                afterWidth = final.width, afterHeight = final.height,
                afterX = final.position.x, afterY = final.position.y)
        }
        resizeStartStates.clear(); if (commands.isEmpty()) return
        viewModelScope.launch { undoRedoManager.execute(AnnotationCommand.CompositeCommand(
            id = java.util.UUID.randomUUID().toString(), pdfPath = pdfFilePath, pageIndex = -1,
            timestamp = System.currentTimeMillis(), commands = commands
        )); syncUndoRedoState() }
    }

    fun onResizeEnd(id: String) {
        val start = resizeStartStates.remove(id) ?: return
        val el = _uiState.value.imageElements.find { it.id == id } ?: return
        viewModelScope.launch {
            undoRedoManager.execute(AnnotationCommand.ResizeImageCommand(
                id = java.util.UUID.randomUUID().toString(), pdfPath = pdfFilePath,
                pageIndex = el.pageIndex, timestamp = System.currentTimeMillis(),
                elementId = id,
                beforeWidth = start.width, beforeHeight = start.height,
                beforeX = start.position.x, beforeY = start.position.y,
                afterWidth = el.width, afterHeight = el.height,
                afterX = el.position.x, afterY = el.position.y
            )); syncUndoRedoState()
        }
    }

    // ─── Image Transform Operations ─────────────────────────────

    fun rotateImage(id: String, degrees: Float) {
        val el = _uiState.value.imageElements.find { it.id == id } ?: return
        val after = (el.rotation + degrees) % 360f
        _uiState.update { s -> s.copy(imageElements = s.imageElements.map { if (it.id == id) it.copy(rotation = after) else it }) }
        viewModelScope.launch { undoRedoManager.execute(AnnotationCommand.RotateImageCommand(
            id = java.util.UUID.randomUUID().toString(), pdfPath = pdfFilePath,
            pageIndex = el.pageIndex, timestamp = System.currentTimeMillis(),
            elementId = id, beforeRotation = el.rotation, afterRotation = after
        )); syncUndoRedoState() }
    }

    fun deleteImage(id: String) {
        val el = _uiState.value.imageElements.find { it.id == id } ?: return
        _uiState.update { s -> s.copy(
            imageElements = s.imageElements.filter { it.id != id },
            selectedImageId = if (s.selectedImageId == id) null else s.selectedImageId,
            selectedImageIds = s.selectedImageIds - id
        )}
        viewModelScope.launch { undoRedoManager.execute(AnnotationCommand.DeleteImageCommand(
            id = java.util.UUID.randomUUID().toString(), pdfPath = pdfFilePath,
            pageIndex = el.pageIndex, timestamp = System.currentTimeMillis(),
            deletedImageState = elementToImageState(el)
        )); syncUndoRedoState() }
    }

    fun duplicateImage(id: String) {
        val el = _uiState.value.imageElements.find { it.id == id } ?: return
        val dup = el.copy(id = java.util.UUID.randomUUID().toString(), position = Offset(el.position.x + 20f, el.position.y + 20f))
        _uiState.update { s -> s.copy(imageElements = s.imageElements + dup, selectedImageId = dup.id) }
        viewModelScope.launch { undoRedoManager.execute(AnnotationCommand.AddImageCommand(
            id = java.util.UUID.randomUUID().toString(), pdfPath = pdfFilePath,
            pageIndex = dup.pageIndex, timestamp = System.currentTimeMillis(),
            imageState = elementToImageState(dup)
        )); syncUndoRedoState() }
    }

    fun bringToFront(id: String) {
        val el = _uiState.value.imageElements.find { it.id == id } ?: return
        val newZ = (_uiState.value.imageElements.maxOfOrNull { it.zIndex } ?: 0) + 1
        _uiState.update { s -> s.copy(imageElements = s.imageElements.map { if (it.id == id) it.copy(zIndex = newZ) else it }) }
        viewModelScope.launch { undoRedoManager.execute(AnnotationCommand.ChangeLayerCommand(
            id = java.util.UUID.randomUUID().toString(), pdfPath = pdfFilePath,
            pageIndex = el.pageIndex, timestamp = System.currentTimeMillis(),
            elementId = id, beforeZIndex = el.zIndex, afterZIndex = newZ
        )); syncUndoRedoState() }
    }

    fun sendToBack(id: String) {
        val el = _uiState.value.imageElements.find { it.id == id } ?: return
        val newZ = (_uiState.value.imageElements.minOfOrNull { it.zIndex } ?: 0) - 1
        _uiState.update { s -> s.copy(imageElements = s.imageElements.map { if (it.id == id) it.copy(zIndex = newZ) else it }) }
        viewModelScope.launch { undoRedoManager.execute(AnnotationCommand.ChangeLayerCommand(
            id = java.util.UUID.randomUUID().toString(), pdfPath = pdfFilePath,
            pageIndex = el.pageIndex, timestamp = System.currentTimeMillis(),
            elementId = id, beforeZIndex = el.zIndex, afterZIndex = newZ
        )); syncUndoRedoState() }
    }

    fun toggleImageLock(id: String) {
        val el = _uiState.value.imageElements.find { it.id == id } ?: return
        val newL = !el.isLocked
        _uiState.update { s -> s.copy(imageElements = s.imageElements.map { if (it.id == id) it.copy(isLocked = newL) else it }) }
        viewModelScope.launch { undoRedoManager.execute(AnnotationCommand.LockImageCommand(
            id = java.util.UUID.randomUUID().toString(), pdfPath = pdfFilePath,
            pageIndex = el.pageIndex, timestamp = System.currentTimeMillis(),
            elementId = id, beforeLocked = el.isLocked, afterLocked = newL
        )); syncUndoRedoState() }
    }

    fun setImageOpacity(id: String, opacity: Float) {
        val el = _uiState.value.imageElements.find { it.id == id } ?: return
        val c = opacity.coerceIn(0f, 1f)
        _uiState.update { s -> s.copy(imageElements = s.imageElements.map { if (it.id == id) it.copy(opacity = c) else it }) }
        viewModelScope.launch { undoRedoManager.execute(AnnotationCommand.ChangeImageOpacityCommand(
            id = java.util.UUID.randomUUID().toString(), pdfPath = pdfFilePath,
            pageIndex = el.pageIndex, timestamp = System.currentTimeMillis(),
            elementId = id, beforeOpacity = el.opacity, afterOpacity = c
        )); syncUndoRedoState() }
    }

    fun snapImageToCenter(id: String, pageWidth: Int, pageHeight: Int) {
        val el = _uiState.value.imageElements.find { it.id == id } ?: return
        val cx = (pageWidth - el.width * el.scale) / 2f; val cy = (pageHeight - el.height * el.scale) / 2f
        _uiState.update { s -> s.copy(imageElements = s.imageElements.map { if (it.id == id) it.copy(position = Offset(cx, cy)) else it }) }
    }

    // ─── Undo / Redo ─────────────────────────────────────────────

    fun undo() {
        viewModelScope.launch {
            val command = undoRedoManager.undo() ?: return@launch
            applyUndoCommand(command)
            syncUndoRedoState()
        }
    }

    fun redo() {
        viewModelScope.launch {
            val command = undoRedoManager.redo() ?: return@launch
            applyRedoCommand(command)
            syncUndoRedoState()
        }
    }

    private fun applyUndoCommand(command: AnnotationCommand) {
        when (command) {
            is AnnotationCommand.AddPath -> _uiState.update { s -> s.copy(annotations = s.annotations.filter { it.id != command.annotationId }) }
            is AnnotationCommand.AddTextNote -> _uiState.update { s -> s.copy(annotations = s.annotations.filter { it.id != command.annotationId }) }
            is AnnotationCommand.AddMarkupCommand -> _uiState.update { s -> s.copy(annotations = s.annotations.filter { it.id != command.annotationId }) }
            is AnnotationCommand.RemoveAnnotation -> {
                val snap = CommandSerializer.deserializeSnapshot(command.removedAnnotationPayload)
                snapshotToAnnotation(snap)?.let { a -> _uiState.update { it.copy(annotations = it.annotations + a) } }
            }
            is AnnotationCommand.UpdateAnnotation -> {
                val snap = CommandSerializer.deserializeSnapshot(command.previousPayload)
                snapshotToAnnotation(snap)?.let { prev -> _uiState.update { s -> s.copy(annotations = s.annotations.map { if (it.id == command.annotationId) prev else it }) } }
            }
            is AnnotationCommand.TextCommand -> {
                if (command.before == null) _uiState.update { s -> s.copy(annotations = s.annotations.filter { it.id != command.after.id }) }
                else { val n = stateToTextNote(command.before, command.pageIndex); _uiState.update { s -> s.copy(annotations = s.annotations.map { if (it.id == n.id) n else it }) } }
            }
            is AnnotationCommand.EditTextCommand -> _uiState.update { s ->
                // If the block was edited for the first time, undo removes the edit.
                // If it was re-edited (before != null), undo restores the previous edit.
                val restored = command.before?.let { editedBlockFromState(it) }
                s.copy(editedTextBlocks = applyEditedBlock(s.editedTextBlocks, command.after.blockId, restored))
            }
            is AnnotationCommand.MoveImageCommand -> _uiState.update { s -> s.copy(imageElements = s.imageElements.map { if (it.id == command.elementId) it.copy(position = Offset(command.beforeX, command.beforeY)) else it }) }
            is AnnotationCommand.ResizeImageCommand -> _uiState.update { s -> s.copy(imageElements = s.imageElements.map { if (it.id == command.elementId) it.copy(position = Offset(command.beforeX, command.beforeY), width = command.beforeWidth, height = command.beforeHeight) else it }) }
            is AnnotationCommand.RotateImageCommand -> _uiState.update { s -> s.copy(imageElements = s.imageElements.map { if (it.id == command.elementId) it.copy(rotation = command.beforeRotation) else it }) }
            is AnnotationCommand.AddImageCommand -> _uiState.update { s -> s.copy(imageElements = s.imageElements.filter { it.id != command.imageState.elementId }) }
            is AnnotationCommand.DeleteImageCommand -> { val ds = command.deletedImageState; _uiState.update { it.copy(imageElements = it.imageElements + ImageElement(id = ds.elementId, pageIndex = command.pageIndex, uri = ds.uri, position = Offset(ds.positionX, ds.positionY), width = ds.width, height = ds.height, scale = ds.scale, rotation = ds.rotation, opacity = ds.opacity, isLocked = ds.isLocked, zIndex = ds.zIndex)) } }
            is AnnotationCommand.ChangeLayerCommand -> _uiState.update { s -> s.copy(imageElements = s.imageElements.map { if (it.id == command.elementId) it.copy(zIndex = command.beforeZIndex) else it }) }
            is AnnotationCommand.ChangeImageOpacityCommand -> _uiState.update { s -> s.copy(imageElements = s.imageElements.map { if (it.id == command.elementId) it.copy(opacity = command.beforeOpacity) else it }) }
            is AnnotationCommand.LockImageCommand -> _uiState.update { s -> s.copy(imageElements = s.imageElements.map { if (it.id == command.elementId) it.copy(isLocked = command.beforeLocked) else it }) }
            is AnnotationCommand.CompositeCommand -> command.commands.reversed().forEach { applyUndoCommand(it) }
            else -> {}
        }
    }

    private fun applyRedoCommand(command: AnnotationCommand) {
        when (command) {
            is AnnotationCommand.AddPath -> { val a = PdfAnnotation.Path(id = command.annotationId, pageIndex = command.pageIndex, points = command.points.map { Offset(it.x, it.y) }, color = Color(command.color.toULong()), strokeWidth = command.strokeWidth, isHighlighter = command.isHighlighter); _uiState.update { it.copy(annotations = it.annotations + a) } }
            is AnnotationCommand.AddTextNote -> { val a = PdfAnnotation.TextNote(id = command.annotationId, pageIndex = command.pageIndex, text = command.text, position = Offset(command.positionX, command.positionY), color = Color(command.color.toULong()), fontSize = command.fontSize); _uiState.update { it.copy(annotations = it.annotations + a) } }
            is AnnotationCommand.AddMarkupCommand -> { val a = markupFromCommand(command); _uiState.update { s -> if (s.annotations.any { it.id == a.id }) s else s.copy(annotations = s.annotations + a) } }
            is AnnotationCommand.RemoveAnnotation -> _uiState.update { s -> s.copy(annotations = s.annotations.filter { it.id != command.annotationId }) }
            is AnnotationCommand.UpdateAnnotation -> { val snap = CommandSerializer.deserializeSnapshot(command.newPayload); snapshotToAnnotation(snap)?.let { na -> _uiState.update { s -> s.copy(annotations = s.annotations.map { if (it.id == command.annotationId) na else it }) } } }
            is AnnotationCommand.TextCommand -> { val n = stateToTextNote(command.after, command.pageIndex); _uiState.update { s -> val ex = s.annotations.find { it.id == n.id }; if (ex != null) s.copy(annotations = s.annotations.map { if (it.id == n.id) n else it }) else s.copy(annotations = s.annotations + n) } }
            is AnnotationCommand.EditTextCommand -> _uiState.update { s ->
                val restored = editedBlockFromState(command.after)
                s.copy(editedTextBlocks = applyEditedBlock(s.editedTextBlocks, command.after.blockId, restored))
            }
            is AnnotationCommand.MoveImageCommand -> _uiState.update { s -> s.copy(imageElements = s.imageElements.map { if (it.id == command.elementId) it.copy(position = Offset(command.afterX, command.afterY)) else it }) }
            is AnnotationCommand.ResizeImageCommand -> _uiState.update { s -> s.copy(imageElements = s.imageElements.map { if (it.id == command.elementId) it.copy(position = Offset(command.afterX, command.afterY), width = command.afterWidth, height = command.afterHeight) else it }) }
            is AnnotationCommand.RotateImageCommand -> _uiState.update { s -> s.copy(imageElements = s.imageElements.map { if (it.id == command.elementId) it.copy(rotation = command.afterRotation) else it }) }
            is AnnotationCommand.AddImageCommand -> { val is_ = command.imageState; _uiState.update { it.copy(imageElements = it.imageElements + ImageElement(id = is_.elementId, pageIndex = command.pageIndex, uri = is_.uri, position = Offset(is_.positionX, is_.positionY), width = is_.width, height = is_.height, scale = is_.scale, rotation = is_.rotation, opacity = is_.opacity, isLocked = is_.isLocked, zIndex = is_.zIndex)) } }
            is AnnotationCommand.DeleteImageCommand -> _uiState.update { s -> s.copy(imageElements = s.imageElements.filter { it.id != command.deletedImageState.elementId }) }
            is AnnotationCommand.ChangeLayerCommand -> _uiState.update { s -> s.copy(imageElements = s.imageElements.map { if (it.id == command.elementId) it.copy(zIndex = command.afterZIndex) else it }) }
            is AnnotationCommand.ChangeImageOpacityCommand -> _uiState.update { s -> s.copy(imageElements = s.imageElements.map { if (it.id == command.elementId) it.copy(opacity = command.afterOpacity) else it }) }
            is AnnotationCommand.LockImageCommand -> _uiState.update { s -> s.copy(imageElements = s.imageElements.map { if (it.id == command.elementId) it.copy(isLocked = command.afterLocked) else it }) }
            is AnnotationCommand.CompositeCommand -> command.commands.forEach { applyRedoCommand(it) }
            else -> {}
        }
    }

    /**
     * Inserts/replaces (or removes when [edited] is null) the edited block for [blockId]
     * in the current list, preserving order.
     */
    private fun applyEditedBlock(
        current: List<EditedTextBlock>,
        blockId: String,
        edited: EditedTextBlock?
    ): List<EditedTextBlock> {
        val without = current.filter { it.originalBlock.id != blockId }
        return if (edited == null) without else without + edited
    }

    /**
     * Reconstructs an [EditedTextBlock] from a persisted [EditTextState]. Uses the live
     * [TextBlock] from state when available (richer metadata: font, words, page size),
     * otherwise rebuilds a minimal block from the state geometry.
     */
    private fun editedBlockFromState(state: AnnotationCommand.EditTextState): EditedTextBlock {
        val originalBlock = _uiState.value.textBlocks.values.flatten()
            .find { it.id == state.blockId }
            ?: TextBlock(
                id = state.blockId,
                pageIndex = 0,
                text = state.originalText,
                x = state.x, y = state.y, width = state.width, height = state.height,
                fontSize = state.originalFontSize
            )
        val alignment = runCatching { TextAlignment.valueOf(state.alignment) }
            .getOrDefault(TextAlignment.LEFT)
        return EditedTextBlock(
            originalBlock = originalBlock,
            newText = state.newText,
            newFontSize = state.newFontSize,
            newColor = Color(state.newColor.toULong()),
            opacity = state.opacity,
            isLocked = state.isLocked,
            alignment = alignment
        )
    }

    // ─── Export ───────────────────────────────────────────────────

    fun saveAnnotationsToPdf(viewWidth: Int) {
        if (pdfFilePath.isEmpty() || _uiState.value.annotations.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val state = _uiState.value
                val file = java.io.File(pdfFilePath)
                com.tom_roush.pdfbox.pdmodel.PDDocument.load(file).use { document ->
                    for ((pageIndex, pageAnns) in state.annotations.groupBy { it.pageIndex }) {
                        val page = document.getPage(pageIndex)
                        val cropBox = page.cropBox; val pdfHeight = cropBox.height
                        val scaleX = cropBox.width / viewWidth.toFloat()
                        com.tom_roush.pdfbox.pdmodel.PDPageContentStream(document, page,
                            com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                            for (ann in pageAnns) {
                                when (ann) {
                                    is PdfAnnotation.Path -> {
                                        if (ann.points.size < 2) continue
                                        cs.setStrokingColor((ann.color.red * 255).toInt(), (ann.color.green * 255).toInt(), (ann.color.blue * 255).toInt())
                                        cs.setLineWidth(ann.strokeWidth * scaleX)
                                        val gs = com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState()
                                        gs.strokingAlphaConstant = if (ann.isHighlighter) 0.5f else 1.0f
                                        cs.setGraphicsStateParameters(gs)
                                        val s = ann.points.first(); cs.moveTo(s.x * scaleX, pdfHeight - (s.y * scaleX))
                                        for (i in 1 until ann.points.size) { val p = ann.points[i]; cs.lineTo(p.x * scaleX, pdfHeight - (p.y * scaleX)) }
                                        cs.stroke()
                                    }
                                    is PdfAnnotation.TextNote -> {
                                        cs.beginText()
                                        cs.setNonStrokingColor((ann.color.red * 255).toInt(), (ann.color.green * 255).toInt(), (ann.color.blue * 255).toInt())
                                        cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, ann.fontSize * scaleX)
                                        val tx = ann.position.x * scaleX
                                        val ty = pdfHeight - (ann.position.y * scaleX) - (ann.fontSize * scaleX)
                                        // Compose rotationZ is clockwise (screen y-down); PDF text rotation is
                                        // counter-clockwise (y-up), so negate the angle. Pivot at the baseline origin.
                                        if (ann.rotation != 0f) {
                                            cs.setTextRotation(Math.toRadians(-ann.rotation.toDouble()), tx.toDouble(), ty.toDouble())
                                        } else {
                                            cs.newLineAtOffset(tx, ty)
                                        }
                                        cs.showText(ann.text); cs.endText()
                                    }
                                    // Markup (highlight/underline/strikeout) is a real
                                    // page-level annotation object, not content-stream
                                    // drawing — added after this stream closes below.
                                    is PdfAnnotation.TextMarkup -> Unit
                                }
                            }
                        }

                        // ─── Real text-markup annotations (page-level) ──────────
                        // Written as PDF Highlight/Underline/StrikeOut annotation
                        // objects with QuadPoints so other viewers (Acrobat, Chrome,
                        // Preview) recognise and can edit/remove them. Markup rects are
                        // normalized (0..1), so they map straight to PDF points and do
                        // not use the view-pixel scaleX path above.
                        pageAnns.filterIsInstance<PdfAnnotation.TextMarkup>().forEach { markup ->
                            writeMarkupAnnotation(page, markup, cropBox.width, pdfHeight)
                        }
                    }
                    document.save(file)
                }
                Log.d(TAG, "Annotations saved to PDF")
            } catch (e: Exception) { Log.e(TAG, "Failed to save annotations", e) }
        }
    }

    /**
     * Write one [PdfAnnotation.TextMarkup] as a real PDF text-markup annotation
     * (Highlight / Underline / StrikeOut) on [page].
     *
     * The selection rects are normalized (0..1, origin top-left like the renderer), so they
     * convert directly to PDF user-space points (origin bottom-left) — no view-pixel scaling.
     * QuadPoints are emitted in Acrobat's expected order (top-left, top-right, bottom-left,
     * bottom-right) per word so highlights wrap each word tightly. Opacity is carried on /CA;
     * [com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup.constructAppearances]
     * generates the appearance stream so viewers without auto-rendering still show it.
     */
    private fun writeMarkupAnnotation(
        page: com.tom_roush.pdfbox.pdmodel.PDPage,
        markup: PdfAnnotation.TextMarkup,
        pdfWidth: Float,
        pdfHeight: Float
    ) {
        if (markup.rects.isEmpty()) return

        val subType = when (markup.type) {
            PdfAnnotation.MarkupType.HIGHLIGHT ->
                com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT
            PdfAnnotation.MarkupType.UNDERLINE ->
                com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE
            PdfAnnotation.MarkupType.STRIKETHROUGH ->
                com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup.SUB_TYPE_STRIKEOUT
        }
        val annotation = com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup(subType)

        val quads = ArrayList<Float>(markup.rects.size * 8)
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (r in markup.rects) {
            val left = r.left * pdfWidth
            val right = r.right * pdfWidth
            val top = pdfHeight - r.top * pdfHeight
            val bottom = pdfHeight - r.bottom * pdfHeight
            quads.add(left); quads.add(top)      // top-left
            quads.add(right); quads.add(top)     // top-right
            quads.add(left); quads.add(bottom)   // bottom-left
            quads.add(right); quads.add(bottom)  // bottom-right
            if (left < minX) minX = left
            if (right > maxX) maxX = right
            if (bottom < minY) minY = bottom
            if (top > maxY) maxY = top
        }
        annotation.setQuadPoints(quads.toFloatArray())

        // Full-opacity RGB; transparency is applied via /CA so the colour stays true.
        annotation.setColor(
            com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor(
                floatArrayOf(markup.color.red, markup.color.green, markup.color.blue),
                com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB.INSTANCE
            )
        )
        annotation.cosObject.setFloat(com.tom_roush.pdfbox.cos.COSName.CA, markup.color.alpha)
        annotation.setRectangle(
            com.tom_roush.pdfbox.pdmodel.common.PDRectangle(minX, minY, maxX - minX, maxY - minY)
        )
        annotation.setPrinted(true)
        annotation.constructAppearances()
        page.annotations.add(annotation)
    }

    fun exportEditedPdf(viewWidth: Int) {
        _uiState.update { it.copy(isExporting = true, exportResult = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val state = _uiState.value
                val result = pdfExportManager.exportEditedPdf(
                    getApplication(), pdfFilePath,
                    state.editedTextBlocks, state.imageElements, viewWidth
                )
                withContext(Dispatchers.Main) { _uiState.update { it.copy(isExporting = false, exportResult = result) } }
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                withContext(Dispatchers.Main) { _uiState.update { it.copy(isExporting = false, exportResult = "Export failed: ${e.message}") } }
            }
        }
    }

    fun clearExportResult() { _uiState.update { it.copy(exportResult = null) } }

    // ─── Annotation Serialization Helpers ─────────────────────────

    private fun annotationToCommand(annotation: PdfAnnotation): AnnotationCommand? {
        return when (annotation) {
            is PdfAnnotation.Path -> AnnotationCommand.AddPath(
                id = java.util.UUID.randomUUID().toString(), pdfPath = pdfFilePath,
                pageIndex = annotation.pageIndex, timestamp = System.currentTimeMillis(),
                annotationId = annotation.id,
                points = annotation.points.map { SerializableOffset(it.x, it.y) },
                color = annotation.color.value.toLong(), strokeWidth = annotation.strokeWidth,
                isHighlighter = annotation.isHighlighter
            )
            is PdfAnnotation.TextNote -> AnnotationCommand.AddTextNote(
                id = java.util.UUID.randomUUID().toString(), pdfPath = pdfFilePath,
                pageIndex = annotation.pageIndex, timestamp = System.currentTimeMillis(),
                annotationId = annotation.id, text = annotation.text,
                positionX = annotation.position.x, positionY = annotation.position.y,
                color = annotation.color.value.toLong(), fontSize = annotation.fontSize
            )
            is PdfAnnotation.TextMarkup -> AnnotationCommand.AddMarkupCommand(
                id = java.util.UUID.randomUUID().toString(), pdfPath = pdfFilePath,
                pageIndex = annotation.pageIndex, timestamp = System.currentTimeMillis(),
                annotationId = annotation.id,
                rects = annotation.rects.map { SerializableRect(it.left, it.top, it.right, it.bottom) },
                color = annotation.color.value.toLong(), markupType = annotation.type.name
            )
        }
    }

    private fun commandToAnnotation(command: AnnotationCommand): PdfAnnotation? {
        return when (command) {
            is AnnotationCommand.AddPath -> PdfAnnotation.Path(
                id = command.annotationId, pageIndex = command.pageIndex,
                points = command.points.map { Offset(it.x, it.y) },
                color = Color(command.color.toULong()), strokeWidth = command.strokeWidth,
                isHighlighter = command.isHighlighter
            )
            is AnnotationCommand.AddTextNote -> PdfAnnotation.TextNote(
                id = command.annotationId, pageIndex = command.pageIndex,
                text = command.text, position = Offset(command.positionX, command.positionY),
                color = Color(command.color.toULong()), fontSize = command.fontSize
            )
            is AnnotationCommand.AddMarkupCommand -> markupFromCommand(command)
            is AnnotationCommand.TextCommand -> stateToTextNote(command.after, command.pageIndex)
            else -> null
        }
    }

    /** Reconstruct a [PdfAnnotation.TextMarkup] from its persisted command. */
    private fun markupFromCommand(command: AnnotationCommand.AddMarkupCommand): PdfAnnotation.TextMarkup =
        PdfAnnotation.TextMarkup(
            id = command.annotationId, pageIndex = command.pageIndex,
            rects = command.rects.map { androidx.compose.ui.geometry.Rect(it.left, it.top, it.right, it.bottom) },
            color = Color(command.color.toULong()),
            type = runCatching { PdfAnnotation.MarkupType.valueOf(command.markupType) }
                .getOrDefault(PdfAnnotation.MarkupType.HIGHLIGHT)
        )

    private fun serializeAnnotation(annotation: PdfAnnotation): String {
        return when (annotation) {
            is PdfAnnotation.Path -> CommandSerializer.serializePathAnnotation(
                annotationId = annotation.id, pageIndex = annotation.pageIndex,
                points = annotation.points.map { SerializableOffset(it.x, it.y) },
                color = annotation.color.value.toLong(), strokeWidth = annotation.strokeWidth,
                isHighlighter = annotation.isHighlighter
            )
            is PdfAnnotation.TextNote -> CommandSerializer.serializeTextAnnotation(
                annotationId = annotation.id, pageIndex = annotation.pageIndex,
                text = annotation.text, positionX = annotation.position.x,
                positionY = annotation.position.y, color = annotation.color.value.toLong(),
                fontSize = annotation.fontSize
            )
            is PdfAnnotation.TextMarkup -> CommandSerializer.serializeMarkupAnnotation(
                annotationId = annotation.id, pageIndex = annotation.pageIndex,
                rects = annotation.rects.map { SerializableRect(it.left, it.top, it.right, it.bottom) },
                color = annotation.color.value.toLong(), markupType = annotation.type.name
            )
        }
    }

    private fun snapshotToAnnotation(snapshot: CommandSerializer.AnnotationSnapshot): PdfAnnotation? {
        return when (snapshot.type) {
            CommandSerializer.TYPE_ADD_PATH -> {
                val data = snapshot.pathData ?: return null
                PdfAnnotation.Path(
                    id = snapshot.annotationId, pageIndex = snapshot.pageIndex,
                    points = data.points.map { Offset(it.x, it.y) },
                    color = Color(data.color.toULong()), strokeWidth = data.strokeWidth,
                    isHighlighter = data.isHighlighter
                )
            }
            CommandSerializer.TYPE_ADD_TEXT -> {
                val data = snapshot.textData ?: return null
                PdfAnnotation.TextNote(
                    id = snapshot.annotationId, pageIndex = snapshot.pageIndex,
                    text = data.text, position = Offset(data.positionX, data.positionY),
                    color = Color(data.color.toULong()), fontSize = data.fontSize
                )
            }
            CommandSerializer.TYPE_ADD_MARKUP -> {
                val data = snapshot.markupData ?: return null
                PdfAnnotation.TextMarkup(
                    id = snapshot.annotationId, pageIndex = snapshot.pageIndex,
                    rects = data.rects.map { androidx.compose.ui.geometry.Rect(it.left, it.top, it.right, it.bottom) },
                    color = Color(data.color.toULong()),
                    type = runCatching { PdfAnnotation.MarkupType.valueOf(data.markupType) }
                        .getOrDefault(PdfAnnotation.MarkupType.HIGHLIGHT)
                )
            }
            else -> null
        }
    }
}
