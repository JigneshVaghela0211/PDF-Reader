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

## 🚀 REVIEWING UPCOMING PHASES (Pending Implementation)

Here is a breakdown of what the NEXT stages must involve to finalize production grade deployments:

### • CHUNK 18: GROUP TRANSFORM LOGIC (Scaling & Rotation)
**Reason:** Currently, dragging grouped elements is easy because spatial displacement vectors (`change.positionChange()`) are identical across elements. However, scaling or rotating groups requires advanced Geometry. We must calculate a **Centroid** (Bounding box of all objects mixed together) and apply a mathematical affine transformation scalar to each child node relative to the centroid coordinates.
**Action Needed:** Implement a dedicated `GroupTransformOverlay` and augment `PdfReaderViewModel.rotateGroup` to map Sine/Cosine angle displacement recursively.

### • CHUNK 20-22: THREADING & MEMORY VALIDATION
**Reason:** Large PDF files (1,000+ pages) handling nested extraction arrays (`TextWord`) and generating scaled Bitmaps could fragment Dalvik Heap Memory logic, causing OOM (Out-of-Memory) crashes on older devices.
**Action Needed:** Hardening `Dispatchers.IO.limitedParallelism(1)` for extraction logic, dropping unviewed page Bitmaps via LRU caching routines, and integrating manual `.recycle()` calls globally across the `PdfExportBox` engine loop.

### • CHUNK 23-25: INTERACTION UI REFINEMENTS (Floating Configs)
**Reason:** Selecting an element currently initiates changes immediately. 
**Action Needed:** Polishing the actual Floating Action layouts (the visual menu popup offering "Duplicate", "Group", "Lock", "Delete") to anchor cleanly onto the selected items based on geometric center calculations, resolving user collision paths.
