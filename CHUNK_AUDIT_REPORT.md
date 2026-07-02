# PDF Reader / Editor — Audit & Implementation Report

> 15-chunk audit-then-implement pass over the codebase.
> Governing policy: **gradual feature-first migration** (new code under `feature/<name>/`,
> existing working code left in place until touched) and **report-then-implement per chunk**.
> Every implemented change below is build-verified (`./gradlew assembleDebug` green).

Status legend: ✅ Completed · 🟡 Partial · 🔵 UI Only · 🟣 Backend Only · ❌ Missing · ⏭️ Skipped (by decision)

---

## Chunk 1 — Project Audit

**Shape:** 127 Kotlin files (~19.5k LOC), single `:app` module, layer-first packaging.

**Strengths**
- Feature-flag system is a genuine single source of truth: `EditorFeature` enum → `FeatureState`
  → `PdfEditorFeatureConfig` → `ToolbarFeatureProvider` (UI consumes `FeatureUiModel`, never raw booleans).
- Few debt markers (5 TODOs, 0 FIXME). Centralized word-level selection engine (`selection/`).

**Findings**
- **God classes** (violate file-size rule): `PdfEditorViewModel` (1215), `PdfReaderScreen` (~890),
  `FileUtils` (745), `PdfReaderViewModel` (~660).
- **Dead code:** legacy `base/` fragment-DI scaffold (12 files) + `di/ActivityModule` — removed (Chunk 15).
- **Architecture gap:** mandated `feature/<name>/{presentation,domain,data,engine}` vs. actual layer-first.
  Resolved via **gradual migration** (only `selection/`, `presentation/editor/` previously complied).
- **Flags without engines:** `AI_SUMMARY`, shapes (rect/circle/arrow), watermark, TOC/outline, RTL,
  facing pages, magnifier, whole-word/case-sensitive search.

---

## Chunk 2 — Reader Experience ✅ (implemented)

| Feature | Status | Notes |
|---|---|---|
| Continuous Vertical / Horizontal Scroll | ✅ | `LazyColumn` / `LazyRow` via `ReadingMode` |
| Single Page (snap) | ✅ | `isPageSnap` → `snapFlingBehavior` |
| Reading Themes | ✅ | `BackgroundMode` Original/Paper/EyeComfort/Invert + color matrix |
| Page Slider | ✅ | `PageScrollbar` |
| Keep Screen On | ✅ | `FLAG_KEEP_SCREEN_ON` via `DisposableEffect` |
| **Bookmarks** | 🟣→✅ | Was Room-backed with **no UI**. Added top-bar toggle + `feature/reader/presentation/component/BookmarksSheet.kt` + `PdfReaderViewModel.removeBookmarkAt`. |
| **Recent Pages (resume)** | 🟡→✅ | `lastOpenedPage` was **read** but never **written**. Added debounced `persistReadingPosition()` → `updateLastOpenedPage`. |
| **Go To Page** | ❌→✅ | `feature/reader/presentation/component/GoToPageDialog.kt`, reached via new overflow menu. |
| Facing Pages / Book Mode / RTL / Thumbnail-nav / Table of Contents | ❌ | Deferred — each a substantial standalone feature (TOC needs PDF outline parsing). |

---

## Chunk 3 — Search & Text Selection ✅ (implemented)

| Feature | Status | Notes |
|---|---|---|
| Search / Highlight / Prev / Next | ✅ | existing |
| **Whole Word** / **Case Sensitive** | ❌→✅ | `searchCaseSensitive`/`searchWholeWord` state, `toggleSearch*` + `matchRangesIn()` in `PdfReaderViewModel` (replaces lowercased `indexOf` loop); "Aa"/"W" toggle pills in `ReaderSearchBar`. |
| Long-press Copy / Word Selection / Multi-line / Drag Handles | ✅ | `selection/` engine + `TextSelectionOverlay` |
| Character / Paragraph / Multi-page selection, Auto-scroll, Magnifier | ❌ | Deferred (architectural). |

---

## Chunk 4 — Annotation ✅ (implemented)

| Feature | Status | Notes |
|---|---|---|
| Highlight / Underline / Strikethrough | ✅ | `PdfAnnotation.TextMarkup` |
| Pen / Marker / Eraser | ✅ | `AnnotationTool.PEN` / `HIGHLIGHTER` / `ERASER` |
| Sticky Notes | ✅ | `PdfAnnotation.TextNote` |
| **Annotation List** | ❌→✅ | `feature/annotation/presentation/component/AnnotationListSheet.kt` — lists all annotations by page, tap-to-jump, delete via undoable `removeAnnotation(id)`. Overflow menu → "Annotations". |
| Rectangle / Circle / Arrow | ❌ | Deferred — needs new `PdfAnnotation.Shape` type + drawing gesture + PDF export. |

---

## Chunk 5 — Text Editing 🟡 (analysis only)

- ✅ Real PDF text editing engine (`PdfTextReplacementEngine`, `PdfContentStreamEditor`, no white-box),
  Add Text, Color, Size, Rotation.
- ❌ Font family, Bold, Italic, Alignment, Letter/Line spacing, document-wide **Find & Replace**
  (only single edited-block replacement exists during export).
- _Sizeable: needs a formatting toolbar + matching export support._

## Chunk 6 — Image Editing 🟡→ (Flip implemented)

- ✅ Insert / Move / Resize / Rotate / Delete / Opacity / Layer order / Lock / Group.
- **Flip** ❌→✅ — `ImageElement.flipHorizontal/flipVertical`, `FlipImageCommand` (undoable + serialized
  as `FLIP_IMAGE`), `graphicsLayer` scaleX/scaleY=-1 render, baked into PDF export via
  `PdfExportManager.applyFlip`, flip buttons in `ImageEditToolbar`. Mapping centralized in
  `feature/image/domain/ImageElementStateMapper`.
- ❌ Crop, Replace (still deferred).

## Chunk 7 — Signature 🟡→ (Date Stamp / Initials implemented)

- ✅ Draw, Saved signatures, Resize, Rotate, Color, Thickness (vector strokes).
- **Date Stamp** ❌→✅ and **Initials** ❌→✅ — inserted as movable, undoable text notes
  (`PdfEditorViewModel.insertDateStamp` / `insertInitials` / `insertTextNote`), reached from the
  Signature sheet; initials via `feature/signature/presentation/component/InitialsDialog`.

## Chunk 8 — Page Management 🟡 (partially implemented)

- ✅ Insert / Delete / Rotate / Extract / Duplicate / Reorder (via `PdfPageManager`).
- **Reverse Pages** ❌→✅ — `ManagePagesViewModel.reversePages` (reuses `reorder` with `(n-1 downTo 0)`),
  `ImportExport` top-bar button.
- ❌ Remove Blank Pages, Page Numbering (Bates).

## Chunk 9 — Document Tools 🟣 (report only — by decision)

- ✅ Merge / Split / Compress (engines + PDF Tools sheet).
- ❌ Watermark, Background, Header, Footer, Bates, Flatten, Optimize, Repair, Remove metadata,
  Password protection, Encryption.

## Chunk 10 — OCR ✅

- ✅ `PdfOcrEngine` (ML Kit) → searchable PDF (invisible text layer), wired into PDF Tools.
- ❌ Batch OCR, editable OCR, language selection UI.

## Chunk 11 — AI Features ⏭️ (skipped — by decision)

- All ❌. No LLM backend by decision; `AI_SUMMARY` stays a PREMIUM placeholder.

## Chunk 12 — File Manager 🟡→ (Tags/Labels implemented)

- ✅ Favorites, Recent (Room-backed).
- **Tags / Labels** ❌→✅ — `pdf_files.tags` column (Room **v8→v9** migration, preserved across
  re-sync), `PdfFile.tags: List<String>`, `PdfRepository.updateTags`, editor via
  `feature/filemanager/presentation/component/TagEditorDialog` reached from the file actions sheet.
- ❌ Hidden, Secure Folder, Duplicate detection, filter-by-tag (deferred).

## Chunk 13 — Export / Import ❌ (report only — by decision)

- ❌ PDF→Image, PDF→Word/Excel/PPT/HTML, Image→PDF, Scan→PDF.

## Chunk 14 — Performance ✅ (strong)

- ✅ Persisted undo/redo Command pattern (survives process death), `LruCache` page cache,
  thumbnail cache, lazy loading, explicit bitmap recycling, IO-dispatcher serialization, large-PDF support.
- ❌ Document auto-save, WorkManager background/incremental save, crash-recovery of in-flight edits.

## Chunk 15 — Cleanup 🟡 (partially implemented)

- ✅ Deleted dead `base/` package (12 files) + `di/ActivityModule.kt` (its only consumer);
  verified `MainActivity : AppCompatActivity` and zero injections of `Navigator`/`FragmentHandler`/`BaseActivity`.
- ✅ Removed unused `viewBinding = true` (UI is 100% Compose).
- ✅ God-class split (first safe slice): extracted the duplicated `ImageElement`↔`ImageState` mapping
  out of `PdfEditorViewModel` into `feature/image/domain/ImageElementStateMapper`.
- ⏳ Remaining (incremental, needs device testing): continue splitting `PdfEditorViewModel`
  (undo/redo apply blocks, export/markup writers), `PdfReaderScreen`, `FileUtils`.

---

## New packages introduced (feature-first migration seed)

```
feature/
  reader/presentation/component/       BookmarksSheet, GoToPageDialog
  annotation/presentation/component/   AnnotationListSheet
  signature/presentation/component/    InitialsDialog
  filemanager/presentation/component/  TagEditorDialog
  image/domain/                        ImageElementStateMapper
```

## Suggested next contained wins — ✅ all four done

1. ✅ **Image Flip** (Ch6)
2. ✅ **Signature Date Stamp / Initials** (Ch7)
3. ✅ **File Tags / Labels** (Ch12, Room v8→v9 migration)
4. ✅ **`PdfEditorViewModel` god-class split** — first safe slice (`ImageElementStateMapper`)

### Further contained wins still available
- Filter-by-tag UI in the file list (storage + editing already done)
- Image **Crop / Replace** (Ch6)
- **Remove blank pages**, **Page numbering** (Ch8)
- Continue the incremental `PdfEditorViewModel` / `PdfReaderScreen` split (device-test each slice)
