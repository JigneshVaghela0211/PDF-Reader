# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Native Android PDF reader/editor. Single Gradle module (`:app`), 100% Kotlin, Jetpack Compose UI with Material 3, Hilt DI. `namespace`/`applicationId` is `com.pdf.pdfreader`; `minSdk 29`, `compileSdk`/`targetSdk 35`, JDK 11.

## Build & Run

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build + install on connected device/emulator
./gradlew lint                   # Android lint
./gradlew :app:dependencies      # inspect dependency tree
```

- Dependency versions are centralized in [gradle/libs.versions.toml](gradle/libs.versions.toml) (version catalog). Add/upgrade deps there, then reference via `libs.*` in [app/build.gradle.kts](app/build.gradle.kts).
- **Firebase (Crashlytics + Analytics) is wired via the `google-services` plugin**, so `app/google-services.json` is required for the build to succeed. If it's missing the Gradle config phase fails.
- **No automated tests exist** (no `src/test` or `src/androidTest`). `testInstrumentationRunner` is declared but there are no test sources.
- `release` build type has `isMinifyEnabled = false` — ProGuard/R8 shrinking is off.

## Architecture

Root package `app/src/main/java/com/pdf/pdfreader/`. The codebase is **migrating from layer-first to feature-first, gradually**: new feature work lands under `feature/<name>/` (own `domain`/`data`/`presentation` sub-layers), while existing working code stays in the legacy layered packages until it's touched. Don't relocate working legacy code as a side effect of unrelated changes.

- **`feature/`** — feature-first modules: `pdf_ocr/` (fully layered OCR editing, see below), `pdf_image/` (image transform engine, see below), plus `reader/`, `annotation/`, `signature/`, `filemanager/`, `image/` (presentation components / mappers extracted so far).
- **`core/`** — `config/PdfEditorFeatureConfig` + `model/EditorFeature`, `model/FeatureState` (the feature-flag system, see below).
- **`domain/`** — `model/` (data classes like `PdfFile`, `ImageElement`, `TextBlock`, `PdfAnnotation`, `AnnotationCommand`), `repository/` (interfaces + `SignatureManager`), `usecase/` (`UndoRedoManager`, file sync use cases).
- **`data/`** — `local/` Room database + DAOs + `PreferenceManager` (DataStore), and `repository/` implementations bound to domain interfaces.
- **`ui/`** — legacy presentation layer: `screens/`, `components/` (~27 Compose components), `viewmodel/`, `theme/`, `navigation/`. `MainActivity` is the only Activity.
- **`presentation/editor/`** — `ToolbarFeatureProvider` (UI-ready feature models for toolbars/menus).
- **`utiles/`** (note the spelling) — PDF rendering, text extraction/replacement (`PdfTextReplacementEngine`, `PdfContentStreamEditor`), export, merge/split/compress engines, thumbnails.
- **`selection/`** — word-level text-selection engine over rendered pages (`hit/`, `range/`, `clipboard/`, `model/`); see the text-selection note below.
- **`extension/`** — small Kotlin extension helpers.
- **`di/`** — Hilt modules: `DatabaseModule` (Room), `RepositoryModule` (`@Binds` impls → interfaces), `ApplicationComponent`. App class is `app/PDF.kt` (`@HiltAndroidApp`), which **must** call `PDFBoxResourceLoader.init()` before any PDFBox use.

### Feature flags (single source of truth)

Every editor capability is gated by [core/config/PdfEditorFeatureConfig.kt](app/src/main/java/com/pdf/pdfreader/core/config/PdfEditorFeatureConfig.kt): each `EditorFeature` enum entry maps to a `FeatureState` (`ENABLED` / `DISABLED` / `COMING_SOON` / `BETA` / `PREMIUM`), with debug/release build-variant overrides layered on a production baseline (experimental features like `REAL_PDF_TEXT_EDITING` and the OCR-edit family are `DISABLED` in release, `BETA` in debug).

Rules: **UI never decides availability** — toolbars/menus consume `ToolbarFeatureProvider.uiModel(feature)` (`visible` / `interactive` / `badge` / `locked`) or check `PdfEditorFeatureConfig.isEnabled(...)`; no hardcoded gating. Adding a feature = one `EditorFeature` entry + one baseline map entry (+ optional `ENABLE_*` accessor). Full switch tables and usage examples are in [README.md](README.md).

### Navigation (single-Activity Compose)
[ui/MainActivity.kt](app/src/main/java/com/pdf/pdfreader/ui/MainActivity.kt) hosts one `NavHost` with routes: `splash` → `main` → `pdf_reader/{path}?pageIndex=&searchQuery=` → `manage_pages/{path}`. File paths are passed as `Uri.encode`d route args. `main` (`MainScreen`) holds its **own nested** `NavHost` for the bottom tabs defined in [ui/navigation/BottomNavItem.kt](app/src/main/java/com/pdf/pdfreader/ui/navigation/BottomNavItem.kt) (Home / Recent / Favorite / Settings). Cross-screen signals (e.g. "refresh after page edits") use `backStackEntry.savedStateHandle`, not new args.

### PDF rendering vs. text extraction (two separate engines)
- **Rendering pages to bitmaps**: Android's built-in `android.graphics.pdf.PdfRenderer`, wrapped by [utiles/PdfPageRenderer.kt](app/src/main/java/com/pdf/pdfreader/utiles/PdfPageRenderer.kt). **Not** thread-safe — all render calls must be serialized by the caller on a single dispatcher. `PdfReaderViewModel` caches rendered pages in an `LruCache`.
- **Text extraction / search / export**: Apache **PDFBox-Android** (`com.tom-roush:pdfbox-android`). PDFBox stream processing is CPU-heavy and not concurrency-safe here, so extractors (`PdfTextBlockExtractor`, `PdfTextExtractor`, `PdfExportManager`) run on an IO dispatcher constrained with `.limitedParallelism(1)`.
- The `android-pdf-viewer` (mhiew) dependency is present but the primary reader is built on the native renderer above.

### Annotation / editing engine (the core complexity lives in `PdfReaderViewModel`)
- **Undo/redo is a persisted Command pattern.** [domain/usecase/UndoRedoManager.kt](app/src/main/java/com/pdf/pdfreader/domain/usecase/UndoRedoManager.kt) keeps in-memory `ArrayDeque` stacks (Mutex-guarded, max 50) for instant UI response **and** persists every `AnnotationCommand` to the Room `annotation_commands` table (serialized as JSON `type`+`payload` via [data/local/CommandSerializer.kt](app/src/main/java/com/pdf/pdfreader/data/local/CommandSerializer.kt)) so edits survive process death. When adding a new command/annotation type, register it in `CommandSerializer` — schema migrations are usually unnecessary since payloads are opaque JSON.
- **Images and signatures share one pipeline.** Signatures are drawn (`SignaturePadDialog`), saved by `SignatureManager` as cropped transparent PNGs in `filesDir/signatures/`, then inserted as `ImageElement`s — inheriting drag/resize/rotate/z-index/lock/undo behavior for free. Don't build a parallel transform stack for signatures.
- **Image transform math lives in `feature/pdf_image/engine/`**, not the ViewModel: `ImageTransformEngine` (pure transform mutations), `ImageGestureProcessor` (gesture → transform), `ImageHitTester`, `ImageMatrixCalculator`. Key invariant: drag deltas are in **viewport space** (position is applied via `Modifier.offset` *outside* the rotation `graphicsLayer`), so a viewport delta maps directly onto translation — **never rotate it by the image angle** (that historical bug inverted drag at 180° and skewed it at 90°). Handle-anchored resize intentionally stays in the ViewModel.
- **Grouping is flat**, via a shared `groupId: String?` on `ImageElement` (a UUID), not a nested hierarchy. Multi-select + group moves/resizes are computed in `PdfReaderViewModel` and serialized as a `CompositeCommand` so a multi-element drag is one atomic undo.
- **Text selection over rendered pages** is a custom gesture bridge (`TextSelectionOverlay`/`TextSelectionToolbar`) mapping touch bounds to PDFBox `TextWord` rects — there is no native OS text selection on rendered bitmaps. Selection is **word-level** and its logic lives in a dedicated `selection/` package (so the ViewModel/overlay stay thin and there's no duplicate selection stack): `PdfTextBlockExtractor` splits words on whitespace (not just glyph-gap); `selection/hit/PdfWordHitTester` maps a touch to exactly one word; `selection/range/PdfSelectionRangeManager` computes the inclusive range + bounds (returning `selection/model/PdfSelectionRange`); `selection/clipboard/PdfClipboardManager` builds clipboard text. `PdfSelectableWord` is a typealias of the existing `TextWord` (no second word model). A long-press selects a single word; `TextSelectionState` carries `startWord`/`endWord` anchors; two `SelectionHandle`s drive `PdfEditorViewModel.moveSelectionStart`/`moveSelectionEnd`, which only update state by delegating to the range manager. Handles can cross over each other (range is order-independent).
- Bitmaps that scroll off-screen are explicitly released via `DisposableEffect { ... recycle() }` to avoid OOM on large PDFs.

### OCR editing (`feature/pdf_ocr/`)

OCR-based text editing for scanned/image-only PDFs, built on ML Kit (all five script models — Latin/Chinese/Devanagari/Japanese/Korean — are **bundled**; `TextRecognizerFactory` picks per script). It only engages when a page has no selectable text: opening Edit Text runs `PdfDocumentAnalyzer` (SEARCHABLE / SCANNED / MIXED, per page); text pages keep using `PdfTextReplacementEngine` untouched, image-only pages prompt "Run OCR & Edit". Key mechanics:

- Pipeline: render at ~200 DPI → `OcrImagePreProcessor` → ML Kit → `OcrPage` hierarchy (blocks → lines → words, normalized coords + confidence) → `OcrResultCache` (JSON per page under `filesDir/ocr_cache/<sha256(path|size|mtime)>/`). **Cache = resume**: completed pages persist across cancel; replacing the source PDF changes the key → full re-run.
- OCR edits live only in `PdfOcrViewModel` — they must never enter the editor ViewModel or the real text-replacement path.
- Export (`OcrEditExporter`) writes a sibling `<name>_ocr.pdf` (original never modified): unedited words become an invisible searchable text layer; edited words get a background-color-sampled patch + visible replacement text. Non-WinAnsi text embeds a system Noto font (`OcrFontProvider`); CFF-flavored Noto CJK can't be embedded (PDFBox 2.x needs TrueType outlines) and falls back to `?`-sanitized output.

Full pipeline details and v1 limitations are in [README.md](README.md).

### Room database
[data/local/AppDatabase.kt](app/src/main/java/com/pdf/pdfreader/data/local/AppDatabase.kt), name `pdf_reader_db`, currently **version 9**. Entities: `PdfEntity`, `PdfTextSnippet`, `BookmarkEntity`, `AnnotationCommandEntity`. Migrations are hand-written in `DatabaseModule.provideAppDatabase`; `fallbackToDestructiveMigration()` is set, so a missing migration silently wipes user data — **always add an explicit `Migration` when you change schema** and bump the version.

## Conventions & gotchas

- Storage scan relies on the broad `MANAGE_EXTERNAL_STORAGE` permission (declared in the manifest) to discover PDFs across the device.
- The UI is Compose-only (`viewBinding` is off; no meaningful XML layouts). The old fragment-based `base/` scaffold and `di/ActivityModule` have been **removed** — follow the Compose `NavHost` pattern for new screens.
- Background/architectural history is documented narratively in [CHANGELOG_PHASES.md](CHANGELOG_PHASES.md) and [CHUNK_AUDIT_REPORT.md](CHUNK_AUDIT_REPORT.md). Useful context, but verify against current code before relying on it.
- Package directory is spelled `utiles` (not `utils`).
