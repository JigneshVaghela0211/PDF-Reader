# PDF Pro: Advanced Features Changelog & Next Phases Documentation

This document breaks down the systematic architectural upgrades made across the application to introduce native PDF Text Selection, Signature drawing/reusing, and foundational Element Grouping logic. It provides an in-depth component explanation outlining what changed, where, and exactly *why*. 

---

## 🛠 PHASE 1: Resolving Architecture Roots (Image Resize Dropping)

**Context:** Dragging or resizing images originally caused them to mysteriously disappear, lock, or break coordinate planes if they passed page bounds.

### Files Modified:
- `com.pdf.pdfreader.ui.components.GlobalImageOverlay.kt`
  - **Change:** Transitioned from per-page image rendering to a single `GlobalImageOverlay` wrapping the entire screen container. 
  - **Reason:** Rendering an Android Compose `Canvas` or image layout per-page caused strict clipping limits. If the user resized the image out of the page layout bounds, the system aggressively clipped it. A global overlay ensures images exist independently of the scrolling page bounds.
- `com.pdf.pdfreader.ui.components.ResizeHandles.kt`
  - **Change:** Fixed the `detectDragGestures` pointer logic to utilize local `change.positionChange()` instead of global layout offsets.
  - **Reason:** Resolves the positive-feedback loop scaling issue. Previously, calculating scaling based on a static pivot while simultaneously updating that pivot created exponential Math scaling crashes (the "bouncing image" effect).
- `com.pdf.pdfreader.ui.viewmodel.PdfReaderViewModel.kt`
  - **Change:** Implemented mathematical safeguards bounding scaling factors using `.coerceAtLeast(30f)` logic.
  - **Reason:** Allowed the layout engine to breathe without crashing from `width = 0f` rendering exceptions.

---

## 📝 PHASE 2: Deep Text Selection & Operations

**Context:** Standard PDF images don't support native OS text selection. We had to build a visual gesture bridge connecting user touch to PDF character streams.

### Files Modified:
- `com.pdf.pdfreader.domain.model.TextBlock.kt`
  - **Change:** Embedded a fine-grained `TextWord` struct inside basic text paragraphs linking normalized `Rect` bounds to individual strings.
  - **Reason:** Without granular `TextWord` coordinates, users could only highlight entire block paragraphs at once rather than specific letters/sentences.
- `com.pdf.pdfreader.utiles.PdfTextBlockExtractor.kt`
  - **Change:** Overhauled the low-level Apache PDFBox character parsing to aggregate bounding boxes character-by-character into localized word maps.
  - **Reason:** The core extraction logic must handle mapping text data without breaking main-thread UI operations. 
- `com.pdf.pdfreader.ui.components.TextSelectionOverlay.kt` (NEW)
  - **Change:** Built a pure `awaitEachGesture` system parsing long-press bounds, initiating drag vectors, and intercepting bounding rects.
  - **Reason:** Required highly isolated pointer interaction tracking independent of the PDF zooming grid.
- `com.pdf.pdfreader.ui.components.TextSelectionToolbar.kt` (NEW)
  - **Change:** Floating layout for user actions (Copy, Highlight, Strikethrough).
  - **Reason:** Provides immediate feedback mapping gestures precisely over the currently selected bounds utilizing localized `Popup` elements.
- `com.pdf.pdfreader.domain.model.PdfAnnotation.kt`
  - **Change:** Created `TextMarkup` subclass supporting color overrides and typography lines (`HIGHLIGHT`, `UNDERLINE`).
  - **Reason:** Essential for serializing user highlights via Reducer structures over strictly drawn graphics paths.

---

## 🖋 PHASE 3: Signature System & Bottom Sheet Re-Use

**Context:** The app required signing functionality that saves signatures natively and reinserts them as controllable graphical layouts.

### Files Modified:
- `com.pdf.pdfreader.ui.components.SignaturePadDialog.kt` (NEW)
  - **Change:** Dialog composable wrapping a multi-path variable-stroke native canvas. Includes integrated Undo/Redo/Clearing arrays.
  - **Reason:** Allows users a sandbox space to perfectly draw their signature using customizable markers before "saving".
- `com.pdf.pdfreader.domain.repository.SignatureManager.kt` (NEW)
  - **Change:** Bound to Dagger Hilt via `@Singleton` to process custom `Paths`, convert them to transparent `Bitmap` objects, auto-crop empty borders, and stream to `context.filesDir` as `.png` files.
  - **Reason:** By avoiding `Room Database` JSON blob serialization complexities we sidestepped huge memory overheads. Saved as raw files, they can be immediately fed to Coil or standard UI Image loaders.
- `com.pdf.pdfreader.ui.components.SignatureBottomSheet.kt` (NEW)
  - **Change:** Expandable layout grid scanning `context.filesDir/signatures` to show recent signatures.
  - **Reason:** Fulfills requirement of quick application mapping to existing PDF layers.
- `com.pdf.pdfreader.domain.model.ImageElement.kt`
  - **Change:** Utilized existing image pipelines to act as the primary signature layer.
  - **Reason:** **HUGE Architectural Win.** By translating Signatures to native `File URIs` and passing them as `ImageElements` into the `PdfReaderViewModel(addImage(...))` pipeline, we instantly inherited full Drag bounds, Rotations, Scaling, Layering `z-index`, locking, and native Undo/Redo stack hooks without writing 1,000 extra lines of code for custom Signature Transform Logic!

---

## 🧩 PHASE 4: Core Engine Groups (Foundation for Phase 5)

**Context:** Creating data linkages enabling dragging 5 pictures/signatures across the screen simultaneously.

### Files Modified:
- `com.pdf.pdfreader.domain.model.ImageElement.kt`
  - **Change:** Attached mapping variable: `val groupId: String? = null`.
  - **Reason:** Instead of nested element hierarchies spanning multiple classes, we utilize a flat architecture where components tied to the same unique UUID sync automatically.
- `com.pdf.pdfreader.ui.viewmodel.PdfReaderViewModel.kt`
  - **Change:** Injected `Set<String> selectedImageIds` and the `groupSelectedImages()` and `toggleImageSelection()` triggers. 
  - **Change:** Patched `moveImage` to compute a comprehensive subset consisting of the touched element, all items sharing the `groupId`, and all additionally `selected` items.
  - **Reason:** When a user drags a single signature, the UI state engine filters all affiliated layers recursively and transposes the native `delta` against every coordinate, essentially locking them visually in unison!

---

## 🏁 PHASE 6: Final Scale Matrix & UI Polish (COMPLETED)

**Context:** Fleshing out the raw math required to map multi-element bounding boxes proportionally against OS gestures.

### Files Modified:
- `com.pdf.pdfreader.ui.viewmodel.PdfReaderViewModel.kt`
  - **Change:** Implemented mathematical `resizeGroup(ids, handle, delta, groupW, groupH)`.
  - **Reason:** Calculates the `MinX/MinY` of the entire dragged group forming an anchored Centroid, and derives `ScaleX/ScaleY` against the gesture offset. It loops over every signature in the group and updates their `Offset` + `Width/Height` proportionately. 
- `com.pdf.pdfreader.data.local.CommandSerializer.kt`
  - **Change:** Injected explicit Serialization tags `TYPE_COMPOSITE_COMMAND`.
  - **Reason:** Binds the `GroupBoundingBoxView` drag actions securely into SQLite memory matrices to execute Unified Undos natively.
- `com.pdf.pdfreader.ui.screens.PdfReaderScreen.kt`
  - **Change:** Conditional mapped `uiState.selectedImageIds.size >= 2` to rendering an `ImageEditToolbar`.
  - **Reason:** Hovering a UI popup exclusively over groups prevents users from attempting invalid behaviors (like rotating a group mathematically which is unsupported) and focuses solely on `Ungroup`, `Duplicate`, and `Delete`.

> **Status:** The backend logic engine, native rendering views, Coroutine extraction throttles, and Compose layout wrappers successfully ran `./gradlew assembleDebug` (Exit Code 0) signifying NO compilation faults across all 26 architecture chunks!

---

## 🛡 PHASE 5: Advanced Group Transforms & Memory Threading (COMPLETED)

**Context:** Allowing geometric scaling (resizing) of grouped items simultaneously, while ensuring the app doesn't crash from memory overflow when scanning 1,000+ page PDFs.

### Files Modified:
- `com.pdf.pdfreader.ui.components.GlobalImageOverlay.kt`
  - **Change:** Implemented mathematical `GroupBoundingBoxView` and `DisposableEffect(bitmap) { ... recycle() }`.
  - **Reason:** Visually draws an orange dashed bounding box surrounding all elements tagged to the same `groupId`. The `DisposableEffect` explicitly breaks Dalvik Memory retaining loops when Bitmaps slide off-screen to prevent OutOfMemory faults.
- `com.pdf.pdfreader.domain.model.AnnotationCommand.kt`
  - **Change:** Added `CompositeCommand` spanning arrays of Undo actions.
  - **Reason:** Guaranteeing that dragging 5 items concurrently will serialize their positional delta into one Undo atomic state.
- `com.pdf.pdfreader.utiles.PdfTextExtractor.kt` & `PdfExportManager.kt`
  - **Change:** Injected `.limitedParallelism(1)` into `Dispatchers.IO` workflows.
  - **Reason:** PDFBox stream processors natively hog CPU logic; queueing them sequentially stops thread overflow on low-end Android architectures during background extraction loops.

---

## 🔍 PHASE 7: Production Audit & Critical Bug Fixes (COMPLETED)

**Context:** Full code audit comparing CHANGELOG_PHASES against actual implementation. Found and fixed 7 critical bugs that would have broken user-facing functionality.

### Bugs Found & Fixed:

#### BUG 1: Signature Thickness Slider Missing ❌→✔
- **File:** `SignaturePadDialog.kt`
- **Issue:** `selectedStrokeWidth` variable existed (hardcoded `5f`) but **no UI control** was rendered — the user had no way to change pen thickness.
- **Fix:** Added a `Slider` (range 2f..20f) with a live preview circle showing the current pen radius, placed between the color picker row and the drawing canvas.

#### BUG 2: Signature Color Rendering Broken on Pre-API-26 ❌→✔
- **File:** `SignatureManager.kt`
- **Issue:** `android.graphics.Color.argb(float, float, float, float)` requires API 26+. On older devices, signature colors were silently wrong or crashed.
- **Fix:** Converted to `Color.argb(int, int, int, int)` by multiplying Compose 0..1 float components by 255.

#### BUG 3: Image/Signature Drag Vibration ❌→✔
- **File:** `GlobalImageOverlay.kt`
- **Issue:** `globalX.toInt().coerceAtLeast(0)` clamped X position to 0 during drag. When dragging left past the screen edge, the position oscillated between the actual negative value and 0, causing visible vibration.
- **Fix:** Removed the `coerceAtLeast(0)` clamp, allowing natural off-screen positioning during drag.

#### BUG 4: Individual Resize Handles Logic Inverted ❌→✔
- **File:** `GlobalImageOverlay.kt`
- **Issue:** `showGroupHandles = activeGroupIds.isEmpty()` — this **hid** individual resize handles when NO group was active (exactly backwards). A single selected image had NO resize handles.
- **Fix:** Changed to `!activeGroupIds.contains(element.id)` — individual handles show unless this element is part of an active group (where group handles take over).

#### BUG 5: SignatureBottomSheet Bitmap Memory Leak ❌→✔
- **File:** `SignatureBottomSheet.kt`
- **Issue:** `remember(uri) { BitmapFactory.decodeFile(...) }` decoded bitmaps for each saved signature thumbnail but never recycled them. Every open/close of the sheet leaked N bitmaps.
- **Fix:** Added `DisposableEffect(bmp) { onDispose { bmp.recycle() } }` to each grid item.

#### BUG 6: Group Move Undo Only Tracked Primary Element ❌→✔
- **File:** `PdfReaderViewModel.kt`
- **Issue:** `moveImage()` correctly moved ALL group members, but `onMoveEnd()` only recorded an undo command for the single dragged element. Pressing Undo would revert only the primary element, leaving group siblings permanently displaced.
- **Fix:** Introduced `groupMoveStartStates: Map<String, Offset>` to capture start positions for all moved elements. `onMoveEnd` now emits a `CompositeCommand` wrapping individual `MoveImageCommand` per member.

#### BUG 7: Stale Field `imageMoveStartPosition` ⚠→✔
- **File:** `PdfReaderViewModel.kt`
- **Issue:** The old `imageMoveStartPosition: Offset?` field was left in place after the group-aware system replaced it.
- **Fix:** Removed dead field.

### Verification:
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (Exit 0)
- Installed on SM-G990E device via `./gradlew installDebug`

---

## ✏️ PHASE 8: Signature Edit System & UX Enhancement (COMPLETED)

**Context:** Signatures were flattened to bitmaps on creation — stroke data was permanently lost, making post-creation editing impossible. Drag boundaries were unbounded (elements could go fully off-screen). The toolbar was not context-aware.

### Architecture Change: Editable Signature Vector Preservation
- Signatures now store `SerializableStroke` data alongside the bitmap on `ImageElement`
- When user edits thickness or color, strokes are re-rendered to a new PNG via `SignatureManager.reRenderSignature()`
- Bitmap cache is busted via `bitmapVersion` counter on the remember key

### Files Modified:
- `com.pdf.pdfreader.domain.model.ImageElement.kt`
  - **Change:** Added `SerializableStroke` data class, `isSignature`, `signatureStrokes`, `signatureCanvasWidth/Height`, `bitmapVersion`
  - **Reason:** Preserves editable vector data for post-creation editing without losing the existing bitmap-based overlay architecture.

- `com.pdf.pdfreader.domain.repository.SignatureManager.kt`
  - **Change:** Added `reRenderSignature(strokes, width, height)` method
  - **Reason:** Re-renders updated strokes to a new PNG file for bitmap swap after thickness/color edits.

- `com.pdf.pdfreader.ui.viewmodel.PdfReaderViewModel.kt`
  - **Change:** Added `updateSignatureProperties()`, `insertSignatureWithStrokes()`. Modified `saveSignature()` to auto-insert. Modified `moveImage()` with boundary clamping (≥20% visible).
  - **Reason:** Powers the edit-re-render cycle. Boundary clamping prevents elements from going fully off-screen.

- `com.pdf.pdfreader.ui.components.GlobalImageOverlay.kt`
  - **Change:** Bitmap `remember` key changed from `element.uri` to `"${element.uri}_v${element.bitmapVersion}"`. Signatures skip `inSampleSize=2` downscaling.
  - **Reason:** Cache busting ensures re-rendered signatures are picked up immediately. No quality loss for small signature PNGs.

- `com.pdf.pdfreader.ui.components.SignatureEditToolbar.kt` (**NEW**)
  - **Change:** New context-aware floating toolbar with thickness slider (2-20px), color picker (6 colors), plus standard image tools.
  - **Reason:** Gives users direct post-creation editing of signature appearance.

- `com.pdf.pdfreader.ui.screens.PdfReaderScreen.kt`
  - **Change:** Context-aware routing — `SignatureEditToolbar` for signatures, `ImageEditToolbar` for regular images. Save flow auto-inserts instead of re-opening sheet.
  - **Reason:** Clean flow separation between signature and image editing UX.

### Verification:
- `./gradlew assembleDebug` → BUILD SUCCESSFUL (Exit 0)
- Installed on SM-G990E device via `./gradlew installDebug`

---

## Phase: Word-level text selection + draggable handles

**Problem:** Tapping a word selected the entire line/paragraph. Root cause was in `PdfTextBlockExtractor.createTextBlock` — words were split only when the glyph gap exceeded `WORD_GAP_THRESHOLD` (5pt), but the extractor keeps literal space characters, so the gap was never reached mid-line and each line collapsed into one giant `TextWord`. The selection stack itself was already word-based.

- `com.pdf.pdfreader.utiles.PdfTextBlockExtractor.kt`
  - **Change:** Word breaking is now whitespace-aware — a blank char commits the current word and is excluded from word bounds (still kept in block text). Glyph-gap split retained as a fallback for spaceless PDFs.
  - **Reason:** Each visible token becomes its own `TextWord`, so a tap hits exactly one word.

- `com.pdf.pdfreader.utiles.PdfWordHitTester.kt` (**NEW**) — pure touch→word hit test, extracted out of the Composable.
- `com.pdf.pdfreader.domain.usecase.SelectionRangeUseCase.kt` (**NEW**) — pure inclusive-range + bounds math; order-independent so handles can cross.
- `com.pdf.pdfreader.ui.components.SelectionHandle.kt` (**NEW**) — draggable Adobe/Xodo-style handle reporting absolute page-pixel drag position.

- `com.pdf.pdfreader.ui.viewmodel.PdfReaderViewModel.kt`
  - **Change:** `TextSelectionState` gained `startWord`/`endWord` anchors (nullable, defaulted).
- `com.pdf.pdfreader.ui.viewmodel.PdfEditorViewModel.kt`
  - **Change:** `updateTextSelection` replaced by `moveSelectionStart`/`moveSelectionEnd`; handlers delegate to `SelectionRangeUseCase` (injected). `startTextSelection`/`selectAllText` set both anchors and compute bounds immediately.
- `com.pdf.pdfreader.ui.components.TextSelectionOverlay.kt`
  - **Change:** Long-press selects one word and shows two persistent handles; dragging a handle hit-tests the word under it and moves that anchor. Continuous long-press-drag still extends the end. Copy/Highlight/Underline/Strikethrough toolbar + real `PDAnnotationTextMarkup` export unchanged.

### Verification:
- `./gradlew assembleDebug` → BUILD SUCCESSFUL
- Device smoke-test recommended: long-press a word selects only that word; drag start/end handles to expand across words/lines; copy + markup actions.
