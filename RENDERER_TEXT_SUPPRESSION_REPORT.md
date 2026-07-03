# Renderer-Based Text Suppression — Architecture Report

**Task:** Determine how to make the renderer *skip the selected text operators* so original glyphs
are never drawn during editing (no cover, no patch, no overlay). Research only — no code changes.

---

## 0. Decisive finding (changes the premise)

**The page bitmaps the user sees are NOT rendered by PDFBox.** They are rendered by Android's
native, closed **`android.graphics.pdf.PdfRenderer`**, wrapped in
`utiles/PdfPageRenderer.kt` and driven by `PdfReaderViewModel`:

```
PdfReaderViewModel.renderPageInternal()
  → PdfPageRenderer.renderPage(pageIndex, width, height, isNightMode)
     → android.graphics.pdf.PdfRenderer.openPage(i).render(bitmap, …)   // native, opaque
  → LruCache<Bitmap>  →  PageRenderState.Success(bitmap, width)
  → ReaderPageView: Image(bitmap.asImageBitmap())
```

`android.graphics.pdf.PdfRenderer` is a system renderer with **no glyph/text/operator hooks** — you
cannot override `showText`/`showGlyph`, install a `PageDrawer`, or filter operators. So on the
*current* path, renderer-level suppression is **impossible**.

**The good news:** the app already depends on **PDFBox-Android `com.tom-roush:pdfbox-android:2.0.27.0`**,
which ships a *second, fully-hookable* renderer (`com.tom_roush.pdfbox.rendering.PDFRenderer` +
`PageDrawer`). Renderer-based suppression is therefore achievable — but it requires rendering the
edited page **through PDFBox instead of the native renderer**. That is the core architectural
decision this report is about.

---

## 1. Where PDFBox renders text (the hookable stack)

Confirmed by inspecting the resolved artifact (`javap` on the 2.0.27.0 api jar):

```
PDFStreamEngine
  ├─ protected void showText(byte[])                                   // 1 call per Tj / ' / " ; drives TJ arrays
  ├─ protected void showGlyph(Matrix trm, PDFont, int code, Vector)    // ← the PAINT hook (render variant)
  ├─ protected void showGlyph(Matrix, PDFont, int code, String uni, Vector)  // text-extraction variant
  └─ public PDPage getCurrentPage()
        ▲
PDFGraphicsStreamEngine (abstract: appendRectangle/drawImage/fill/clip/…)
        ▲
PageDrawer   (com.tom_roush.pdfbox.rendering)
  ├─ protected void showFontGlyph(Matrix, PDFont, int code, Vector)    // concrete glyph paint
  └─ protected void showType3Glyph(Matrix, PDType3Font, int, Vector)   // concrete Type3 paint

PDFRenderer  (com.tom_roush.pdfbox.rendering)
  ├─ public  Bitmap renderImage(int page, float scale, ImageType, RenderDestination)   // → android.graphics.Bitmap
  └─ protected PageDrawer createPageDrawer(PageDrawerParameters)        // ← the INJECTION seam
```

**Injection seam:** `PDFRenderer.createPageDrawer(...)` is `protected` → subclass `PDFRenderer`,
return a custom `PageDrawer`. **Suppression seam:** override `showGlyph(...)` (or
`showFontGlyph`/`showType3Glyph`) in that `PageDrawer` and *conditionally not call `super`* → the
glyph is never painted. Everything else on the page (rules, backgrounds, images, other text) draws
normally, so surrounding graphics are preserved with zero cover.

**Precedent already in this codebase:** `utiles/PdfTextObjectDetector.OpCollector extends
PDFTextStripper` and *already overrides* `showText(byte[])` and `showGlyph(...)`. It assigns a
`showIndex` (one per `Tj`/`'`/`"` and one per `COSString` inside a `TJ`) in **content-stream order,
identical to `PdfContentStreamEditor`**. This proves the override pattern works here and — crucially
— gives us a ready-made way to *name* the operators to suppress (see §3).

---

## 2. Candidate mechanisms (evaluated)

| Mechanism | Verdict |
|---|---|
| **Custom `PDFRenderer` + custom `PageDrawer`** (override `createPageDrawer`, then `showGlyph`) | ✅ **Recommended.** Public/protected, supported, matches existing `OpCollector` precedent. |
| Override `showGlyph()` | ✅ This *is* the suppression point (render 4-arg variant, inside the custom PageDrawer). |
| Override `showText()` | ✅ Needed too — as the per-operator *counter* to know which op we're inside; delegates to `super` which calls `showGlyph` per glyph. |
| Override `showFontGlyph`/`showType3Glyph` | ✅ Alternative/complementary paint hooks on `PageDrawer` (skip here instead of `showGlyph`). |
| "Temporary operator filter" / render callback | ⚠️ No first-class callback API in 2.0.27.0; the PageDrawer override *is* the filter. A pre-parse "operator filter" (rewrite the content stream to drop ops before rendering) is possible but that mutates the stream → closer to replacement; rejected for a *preview*. |
| Custom native (`android.graphics.pdf.PdfRenderer`) drawer | ❌ Impossible — closed system renderer, no hooks. |

---

## 3. Identifying *which* glyphs to suppress

Three strategies; recommend **A driven by B's geometry**, exactly mirroring the existing detector.

**A. By show-operator index (precise, recommended).**
The selection already resolves to concrete operators: `ReplacementAnalyzer` → `PdfTextObjectDetector
.detectInRegion(doc, page, region)` returns `DetectedTextOp`s each carrying a `showIndex`. The
suppression `PageDrawer` counts `showText` calls the same way and skips every glyph whose current op
index ∈ the target set. Because detector, drawer, and `PdfContentStreamEditor` all index in
content-stream order, they stay in lock-step — the same guarantee the codebase already relies on.

**B. By glyph bounding box (simple, good fallback / mapping input).**
Override `showGlyph`, take the glyph origin from `textRenderingMatrix.translateX/translateY` (+
extent from `displacement`×scale — the exact math `OpCollector.showGlyph` already does), and skip if
it lies inside any selected word's PDF-point rect. No op-indexing needed; robust; but a shared
operator holding word + neighbour could over/under-suppress at the boundary.

**C. By text content.** ❌ Fragile (repeated words, ligatures) — rejected.

**Chosen:** map selection → target `showIndex`es via the existing detector (A), suppress by index in
the drawer, using bbox (B) only to build that mapping. This is the safest and reuses `detectInRegion`
verbatim.

---

## 4. Where the suppressing renderer runs — architectural options

The hard part is not the drawer; it's that PDFBox rendering is a **different, heavier, lower-fidelity
backend** than the native renderer, and (per `CLAUDE.md`) PDFBox work here must be serialized on a
single IO dispatcher (`.limitedParallelism(1)`, not concurrency-safe).

### Option A — Full renderer swap (native → PDFBox everywhere)
Replace `PdfPageRenderer` with the PDFBox renderer for all pages.
- ✅ Suppression trivially available; one pipeline.
- ❌ Major reading-mode regression risk: PDFBox-Android render is slower, more memory-hungry, and
  lower fidelity than native; single-thread constraint hurts scroll. **Reject.**

### Option B — Hybrid, page-level, edit-mode only  ⭐ RECOMMENDED
Keep native rendering for normal reading. When a page is in **Edit-Text mode and has active
edits**, render *that one page* via `SuppressingPDFRenderer` (skipping the edited ops) → an "edit
bitmap" with the original glyphs **absent**. Display that in place of the cached native bitmap while
editing; draw the edited text + selection handles on top as today — but with **no cover**, because
the glyphs are already gone. Exit/Cancel → drop the edit bitmap → instant revert to the cached
native bitmap (non-destructive; PDF never touched).
- ✅ True suppression, no cover; reading path untouched; cost bounded to one page, only while editing.
- ⚠️ The edited page may look marginally different from its neighbours (AA/color/font fidelity between
  the two backends); ~hundreds-of-ms render latency on entering edit (mitigate: keep native bitmap
  until the edit bitmap is ready, small spinner); +1 full-page bitmap in memory; must run on the
  PDFBox IO dispatcher.

### Option C — Hybrid, region-level (crop the PDFBox page to the edited row)
Like B, but composite only the edited word's row from the PDFBox render over the native bitmap.
- ✅ Minimises visible backend mismatch (only a small strip differs).
- ❌ `renderImage` renders the *whole* page anyway (no partial-render API in 2.x) → same render cost,
  then crop; and compositing a PDFBox strip onto a native bitmap can show a seam. Marginal over B.

**Recommendation: Option B**, keyed/cached per `(pageIndex, edited-op-set, scale)`, on the existing
serialized PDFBox IO dispatcher.

---

## 5. Proposed design (illustrative only — NOT implemented)

```kotlin
// 1. Injection: a PDFRenderer that installs a suppressing PageDrawer.
class SuppressingPDFRenderer(doc: PDDocument, private val skip: Set<Int>) : PDFRenderer(doc) {
    override fun createPageDrawer(p: PageDrawerParameters) = SuppressingPageDrawer(p, skip)
}

// 2. Suppression: count ops like OpCollector; don't paint glyphs of targeted ops.
class SuppressingPageDrawer(p: PageDrawerParameters, private val skip: Set<Int>) : PageDrawer(p) {
    private var opIndex = 0
    private var suppressCurrent = false
    override fun showText(s: ByteArray) { suppressCurrent = opIndex in skip; super.showText(s); opIndex++ }
    override fun showGlyph(trm: Matrix, font: PDFont, code: Int, disp: Vector) {
        if (suppressCurrent) return                 // ← glyph never drawn, no cover needed
        super.showGlyph(trm, font, code, disp)
    }
}

// 3. Use: render the edited page to a Bitmap with the selected ops absent.
val skip = detector.detectInRegion(doc, page, region).map { it.showIndex }.toSet()
val editBitmap = SuppressingPDFRenderer(doc, skip)
    .renderImage(page, scaleToMatchNativeWidth, ImageType.ARGB)   // on the PDFBox IO dispatcher
```

Pipeline the user described, realised:

```
PDF Page → SuppressingPDFRenderer → (PageDrawer skips selected ops) → edit bitmap
        → Compose: draw edited text over the now-empty word slot → selection handles
```

**Alignment note:** render at `scale = nativeBitmapWidthPx / page.cropBox.width` so the edit bitmap
is pixel-aligned with the reader's width-based layout and existing normalized word coords still map.

---

## 6. Risks / open questions to settle before implementing

1. **Backend fidelity gap** — PDFBox-Android vs native rendering of the *same* page may differ
   (font hinting, anti-aliasing, some blends/shadings). Verify on real invoices; the edited page must
   look acceptably like its neighbours. This is the biggest unknown.
2. **Latency** — full-page PDFBox render on entering edit mode (~hundreds of ms). Keep the native
   bitmap visible until ready; render on the serialized IO dispatcher.
3. **Shared-operator boundary** — if the selected word shares a `Tj`/`TJ` op with neighbours,
   suppressing the whole op also hides the neighbours. `ReplacementAnalyzer` already flags this as
   `OVERLAPPING_REGION`/`MULTI_OPERATOR`; for suppression we'd either (a) suppress at glyph-bbox
   granularity (strategy B) within the op, or (b) only enter renderer-suppression when the analyzer
   says the op is self-contained, else keep today's behaviour. Needs a decision.
4. **Memory** — one extra ARGB full-page bitmap while editing; release on exit (mirror existing
   `DisposableEffect { recycle() }`).
5. **Night mode / color matrix** — the reader applies a night-mode/ColorFilter; the edit bitmap must
   respect the same treatment or editing will flash a different appearance.
6. **Type3 / annotations / form XObjects** — glyphs inside Type3 fonts or form XObjects route through
   `showType3Glyph`/`showForm`; suppression by op-index must account for those paths (or restrict v1
   to simple `Tj`/`TJ` text, which invoices use).

---

## 7. Recommendation summary

- Renderer-level suppression **is achievable** via a **custom `PDFRenderer` + `PageDrawer`** from the
  already-bundled PDFBox-Android — **not** via the native `android.graphics.pdf.PdfRenderer`, which
  has no hooks.
- Adopt **Option B**: render only the edited page through the suppressing PDFBox renderer *while in
  edit mode*, keep native rendering for all normal reading. Non-destructive, no cover, bounded cost.
- Identify targets by **`showIndex`** using the existing `PdfTextObjectDetector` (lock-step with
  `PdfContentStreamEditor`), mapped from the selected word's bbox.
- Gate on the analyzer's self-contained-operator check to avoid suppressing shared-op neighbours.
- Settle the six risks in §6 (fidelity + latency first) before writing code.

**No code was changed. Awaiting approval on the approach (Option B + showIndex suppression) before
any implementation.**

### Note on current working tree
The previously-rejected inline-edit + sampled-cover changes are still **uncommitted** in the working
tree (nothing committed since MC4 `90f4ba2`). Per "do not continue iterating," I left them untouched.
On approval of this report I can revert the cover/patch code and implement Option B instead.
