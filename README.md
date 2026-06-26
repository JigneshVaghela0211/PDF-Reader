# PDF Reader / Editor

Native Android PDF reader & editor — 100% Kotlin, Jetpack Compose + Material 3, Hilt DI.
See [CLAUDE.md](CLAUDE.md) for build/run and architecture details.

## Package structure (migration in progress)

The codebase is moving from a **layer-first** layout (`ui/`, `domain/`, `data/`, `utiles/`)
to a **feature-first** one, gradually — new feature work lands under `feature/<name>/` while
existing working code stays in place until it's touched:

```
feature/
  reader/presentation/component/      ← BookmarksSheet, GoToPageDialog
  annotation/presentation/component/  ← AnnotationListSheet
```

The legacy fragment-based DI scaffold (`base/` package + `di/ActivityModule`) was unused and
has been removed; the app is single-Activity Compose (`ui/MainActivity`). `viewBinding` is off
(Compose-only UI).

## PDF Editor Feature Configuration

All PDF-editing capabilities are governed by a **single source of truth**:

```
app/src/main/java/com/pdf/pdfreader/
├── core/config/PdfEditorFeatureConfig.kt   ← the only place feature availability is set
├── core/model/EditorFeature.kt             ← catalogue of every editor capability
├── core/model/FeatureState.kt              ← ENABLED / DISABLED / COMING_SOON / BETA / PREMIUM
└── presentation/editor/ToolbarFeatureProvider.kt  ← exposes UI-ready models to toolbars/menus
```

Data flow:

```
PdfEditorFeatureConfig  →  ToolbarFeatureProvider  →  Editor Toolbar / Bottom Sheet / Menu
        (state)                  (FeatureUiModel)              (renders mechanically)
```

### Rules

1. **UI never decides availability.** Toolbars, bottom sheets, menus and the ViewModel ask
   `PdfEditorFeatureConfig` / `ToolbarFeatureProvider` — no `if (true)`, no `BuildConfig.DEBUG`
   gating, no scattered `currentTool ==` visibility hacks.
2. **Changing one feature = editing one file.** Flip a value in `PdfEditorFeatureConfig` (and, for
   a brand-new capability, add one entry to `EditorFeature`). Nothing else.
3. **State drives presentation** (see table below).

### Feature states

| State         | UI behavior                                  |
|---------------|----------------------------------------------|
| `ENABLED`     | Shown normally, fully interactive            |
| `DISABLED`    | Hidden completely (no item, click, shortcut) |
| `COMING_SOON` | Shown, disabled, with a **SOON** badge       |
| `BETA`        | Shown, interactive, with a **BETA** label    |
| `PREMIUM`     | Shown with a **lock** icon, not interactive  |

### Usage

```kotlin
// Enum API (preferred for new code):
if (PdfEditorFeatureConfig.isEnabled(EditorFeature.SIGNATURE)) { showSignature() }
val state = PdfEditorFeatureConfig.stateOf(EditorFeature.OCR)   // COMING_SOON

// Named accessors (mirror the ENABLE_* convention):
if (PdfEditorFeatureConfig.ENABLE_SIGNATURE) { showSignature() }

// UI surfaces consume a ready-made model — no branching in the view:
val model = ToolbarFeatureProvider.uiModel(EditorFeature.EDIT_TEXT)
// model.visible / model.interactive / model.badge / model.locked
```

### Environment support

`PdfEditorFeatureConfig` layers build-variant overrides on top of the production baseline:

- **Debug** (`BuildConfig.DEBUG`): enables experimental features for testing
  (`REAL_PDF_TEXT_EDITING` → `BETA`).
- **Release**: only production-ready features (experimental ones forced to `DISABLED`).

### Available feature switches

Defaults below are the **production baseline** (debug builds may upgrade experimental ones).

#### Text Edit
| Switch | Feature | Default |
|---|---|---|
| `ENABLE_EDIT_TEXT` | `EDIT_TEXT` | ENABLED |
| `ENABLE_REAL_PDF_TEXT_EDITING` | `REAL_PDF_TEXT_EDITING` | DISABLED (BETA in debug) |
| `ENABLE_TEXT_FONT_FALLBACK` | `TEXT_FONT_FALLBACK` | ENABLED |
| `ENABLE_ADD_TEXT` | `ADD_TEXT` | ENABLED |
| `ENABLE_TEXT_COLOR` | `TEXT_COLOR` | ENABLED |
| `ENABLE_TEXT_SIZE` | `TEXT_SIZE` | ENABLED |
| `ENABLE_TEXT_ROTATION` | `TEXT_ROTATION` | BETA |

#### Markup
| Switch | Feature | Default |
|---|---|---|
| `ENABLE_HIGHLIGHT` | `HIGHLIGHT` | ENABLED |
| `ENABLE_UNDERLINE` | `UNDERLINE` | ENABLED |
| `ENABLE_STRIKETHROUGH` | `STRIKETHROUGH` | ENABLED |
| `ENABLE_FREEHAND_DRAWING` | `FREEHAND_DRAWING` | ENABLED |
| `ENABLE_ERASER` | `ERASER` | ENABLED |

#### Signature
| Switch | Feature | Default |
|---|---|---|
| `ENABLE_SIGNATURE` | `SIGNATURE` | ENABLED |
| `ENABLE_SAVED_SIGNATURE` | `SAVED_SIGNATURE` | ENABLED |
| `ENABLE_SIGNATURE_RESIZE` | `SIGNATURE_RESIZE` | ENABLED |
| `ENABLE_SIGNATURE_ROTATION` | `SIGNATURE_ROTATION` | ENABLED |

#### Image
| Switch | Feature | Default |
|---|---|---|
| `ENABLE_INSERT_IMAGE` | `INSERT_IMAGE` | ENABLED |
| `ENABLE_IMAGE_RESIZE` | `IMAGE_RESIZE` | ENABLED |
| `ENABLE_IMAGE_MOVE` | `IMAGE_MOVE` | ENABLED |
| `ENABLE_IMAGE_ROTATION` | `IMAGE_ROTATION` | ENABLED |
| `ENABLE_IMAGE_DELETE` | `IMAGE_DELETE` | ENABLED |

#### Page Management
| Switch | Feature | Default |
|---|---|---|
| `ENABLE_INSERT_PAGE` | `INSERT_PAGE` | BETA |
| `ENABLE_DELETE_PAGE` | `DELETE_PAGE` | ENABLED |
| `ENABLE_ROTATE_PAGE` | `ROTATE_PAGE` | ENABLED |
| `ENABLE_EXTRACT_PAGE` | `EXTRACT_PAGE` | ENABLED |
| `ENABLE_REORDER_PAGE` | `REORDER_PAGE` | BETA |

#### PDF Tools
| Switch | Feature | Default |
|---|---|---|
| `ENABLE_SEARCH` | `SEARCH` | ENABLED |
| `ENABLE_COPY_TEXT` | `COPY_TEXT` | ENABLED |
| `ENABLE_OCR` | `OCR` | BETA |
| `ENABLE_COMPRESS` | `COMPRESS` | BETA |
| `ENABLE_MERGE` | `MERGE` | BETA |
| `ENABLE_SPLIT` | `SPLIT` | BETA |
| `ENABLE_AI_SUMMARY` | `AI_SUMMARY` | PREMIUM |

#### Save Options
| Switch | Feature | Default |
|---|---|---|
| `ENABLE_SAVE_ORIGINAL` | `SAVE_ORIGINAL` | ENABLED |
| `ENABLE_SAVE_AS_COPY` | `SAVE_AS_COPY` | ENABLED |
| `ENABLE_AUTO_BACKUP` | `AUTO_BACKUP` | DISABLED |
| `ENABLE_UNDO_REDO_PERSISTENCE` | `UNDO_REDO_PERSISTENCE` | ENABLED |

### Adding a new editor feature

1. Add an entry to `EditorFeature` (with its `FeatureCategory` + title).
2. Add its baseline `FeatureState` to the `baseline` map in `PdfEditorFeatureConfig`
   (and a `debugOverrides` / `releaseOverrides` entry if it's experimental).
3. (Optional) add an `ENABLE_*` accessor for ergonomic call sites.
4. Gate the UI control via `ToolbarFeatureProvider.uiModel(...)` — never with a hardcoded check.
