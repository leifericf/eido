# Ideas

Ideas under consideration for future Eido releases. This is a living document — nothing here is promised, but everything here is something we think would genuinely help artists make better work.

Organized by what matters to practicing generative and computational artists, grounded in published needs from the community.

---

---

## Exploration and iteration

### Visual seed browser (separate project)

A standalone GUI application for browsing seeds interactively — outside Eido's core REPL-driven workflow. This would be a separate project that consumes Eido as a library.

**Concept:**
- Desktop window with keyboard controls: arrow keys to step through seeds, 's' to bookmark, 'r' to render at full resolution
- Could be built with Swing, JavaFX, or a web UI served from a local server
- The REPL workflow (seed-grid + watch-scene + show) already covers this use case for REPL-comfortable users; a GUI would serve artists who prefer a more visual exploration tool

**Why separate:**
- Eido's core is a data-oriented library, not a GUI application
- A visual browser introduces state management and event handling that don't belong in the core
- Different release cadence and dependency profile (UI frameworks)

---

## Infrastructure

### Scene archive with queryable history

An optional, embedded database for versioning and querying scenes over time. Eido scenes are plain maps — a natural fit for an immutable, value-oriented database.

**Why:** Artists accumulate experiments over months and years. Queryable history ("show me everything tagged :sunset") and time travel ("what did this look like last week?") support the long-term practice that distinguishes serious generative work.

**Design constraints:**
- Must remain an optional dependency — core Eido stays zero-dep, purely Clojure/JVM
- Archive code in a separate source path behind an alias
- Default classpath never sees database classes

**Sketch:**
```clojure
;; Save scene + metadata
(archive/save! conn scene {:seed 42 :tags [:geometric :sunset] :params params})

;; Query past work
(archive/find conn {:tags [:sunset]})

;; Retrieve and re-render at the REPL
(show (archive/load conn entity-id))
```

---

## Outreach and tooling

### Visual editor for non-programmers (separate project)

A standalone GUI application that lets artists who don't write
Clojure build Eido scenes through a visual editor instead of authoring
EDN by hand.

**Concept:**
- Tree/outliner view of the scene graph — add shapes, groups, fills,
  generators with buttons, not keystrokes.
- Inspector panels for each node type with sliders, color pickers, and
  numeric fields. Changes preview in real time.
- Serializes to the same plain-data scene maps Eido already renders,
  so artwork made in the editor remains portable — export as `.edn`
  and render with `clj -X eido.core/render`.
- Could import existing scene files so REPL-driven artists hand off
  work to collaborators who prefer a visual workflow.

**Why separate:**
- Eido's core is a data-oriented library; a visual editor introduces
  UI state, event handling, and platform concerns that don't belong
  in the core.
- Independent release cadence and dependency profile (UI toolkit,
  bundling, code signing on macOS/Windows).
- Overlaps with the Visual Seed Browser idea above but aims at a
  wider audience — the seed browser assumes you've already written
  a scene function; this editor is for people who haven't.

**Design constraints:**
- Must round-trip: what the editor saves is exactly what a programmer
  would write by hand, so the two populations can share files.
- Features in the editor never gate features in the library — Eido
  stays usable without it.

### Content-addressed render cache for the docs/gallery build

Eido is deterministic: for a given scene map and library version, the
rendered bytes are fixed. Today the website build re-renders every
gallery and docs scene on every deploy, even when nothing about the
scene or the renderer changed — slow and wasteful.

**Concept:**
- Compute `hash = sha256(pr-str(scene) + eido-version)` for each scene
  before rendering.
- Maintain a small EDN manifest (e.g. `_site/images/.manifest.edn`)
  mapping `"scene-name.png" → hash`.
- Before rendering a scene: if the output file exists and its recorded
  hash matches the current hash, skip the render; otherwise render and
  update the manifest.
- In CI, wrap `_site/images/` (and the manifest) in an `actions/cache`
  step keyed on a coarse hash of `examples/site/render.clj`,
  `resources/eido/version.edn`, and `src/**/*.clj`. Cache hit = almost
  all scenes skip rendering; source change = only affected scenes
  rebuild.

**Design tradeoffs:**
- Hashing a `:frames` GIF spec hashes the full frame vector — a few ms
  per scene, but tiny next to the render cost being saved.
- The coarse CI cache key invalidates on any `src/**/*.clj` change,
  even unrelated ones. That's correct (safer than incorrect) but
  conservative. If in practice we see a lot of full rebuilds from
  unrelated edits, switch to hashing only the namespaces the render
  actually reaches.
- Content-addressed storage (`images/by-hash/<hash>.png` with named
  symlinks) is cleaner long-term but complicates Pages serving;
  the manifest approach is simpler and sufficient.

**Why hold off:**
- Current build is ~9 min on CI — slow, but not yet a release
  bottleneck. Implement when it starts blocking iteration, or when
  the gallery grows enough that the per-deploy cost is clearly felt.
- Must preserve a "force full rebuild" path for when the renderer
  changes in ways the version bump doesn't capture (e.g. a bug fix
  that shouldn't alter output but could) — likely a manifest-wipe
  CI input or a bump in an explicit `:cache-version` constant.

---

## Output backends and export targets

Eido's core produces polylines (and meshes, via OBJ). Each output
format is essentially a serializer on top of that IR. Current
backends: SVG, plotter, polyline, OBJ. This section surveys
potential new export targets, organized by effort and audience
captured.

### Prioritization framework

Five axes to score each target:

1. **Effort** — distance from the current IR. Serializer-only is
   cheap; a new IR is expensive.
2. **Audience overlap** — does it serve existing Eido users, or
   require recruiting a new community?
3. **Leverage** — does one format unlock many machines/workflows?
   (DXF is very high; a single-vendor format is low.)
4. **Novelty** — is anyone else doing generative → this from a
   code-first toolkit?
5. **Openness** — is the format documented, reverse-engineered, or
   open-spec? Safe to target long-term?

Two meta-questions dominate the ranking regardless of the table:

- **Who are we trying to attract next?** Existing
  plotter/fabrication users → tier 1. A new craft community → pick
  one tier 2 or tier 4 target and go deep. Each new audience is a
  distinct docs/gallery/example effort, not just code.
- **Is the plotter backend well-factored enough to cheaply add
  siblings?** Partly. The concrete IR is clean and already feeds a
  non-rendering backend (polyline extraction). But a small,
  bounded refactor at the polyline layer gates every motion-stream
  backend; see tier 0 below.

### Tier 0 — polyline-layer refactor (prerequisite)

Not a new backend; a substrate change that every motion-stream
backend (DXF, G-code, embroidery, laser G-code) will depend on.

**Current state (audited 2026-04-13):**

The concrete IR (`eido.ir` — `->RectOp`, `->CircleOp`, `->PathOp`,
`->BufferOp`, etc.) is plain data and format-neutral. Pipeline is:

    scene map → semantic IR → eido.ir.lower → concrete ops → backend

`eido.io.polyline/extract-polylines` already walks `:ir/ops` and
dispatches per-op, proving the IR can feed non-rendering backends.

Three responsibilities, however, are stranded inside the SVG
render path where motion-stream backends cannot reach them:

1. **Travel optimization** lives in `render-to-svg` via the
   `:optimize-travel` option. It is a polyline-graph problem, not
   an SVG-specific concern.
2. **Per-pen grouping** is passed as `:group-by-stroke` to
   `render-to-svg`; `eido.io.plotter/split-svg-groups` then
   recovers the grouping by parsing `<g id="pen-...">` tags out of
   the rendered SVG text. Grouping is not a first-class
   polyline-layer concept.
3. **Stroke color is discarded** by `extract-polylines` — the
   return is bare `[[x y] ...]` vectors with no color/pen/layer
   tag. Fine for a single-tool plotter; useless for DXF layers,
   G-code tool changes, or embroidery color changes.

Consequence: `eido.io.plotter` is an SVG post-processor, not a
peer serializer. New motion-stream backends must not extend that
pattern — they should sit next to `polyline.clj`, consuming the
concrete IR directly.

**Scope of the refactor:**

1. Enrich `eido.io.polyline/extract-polylines` to return

   ```clojure
   {:groups [{:stroke <color>
              :polylines [[[x y] ...] ...]} ...]
    :bounds [w h]}
   ```

   preserving stroke color and any pen/layer tag from each op's
   style fields.
2. Lift travel optimization to a pure function over polyline
   groups in its own namespace (e.g. `eido.io.travel`, or kept
   inside `eido.io.polyline`). Both SVG and new backends call it.
3. Leave `eido.io.plotter` and the SVG renderer untouched —
   existing behavior keeps working. New backends bypass them.

**Effort:** Small. `polyline.clj` is ~156 lines today; the
grouping change touches ~20 of them plus new code for travel
optimization.

**Value:** Gates DXF, G-code, and embroidery simultaneously.
Without this, each new backend either duplicates grouping and
optimization logic or becomes an SVG post-processor — debt that
compounds with each serializer added.

**Suggested first customer after the refactor:** DXF. Simpler than
G-code, doesn't need the stitch subdivision embroidery requires,
and exercises color/layer preservation (the whole point of the
refactor).

### Tier 1 — quick wins (serializer-only)

Cheap, universal, strictly expand utility for existing users.
**All three depend on tier 0 (except STL, which rides the OBJ
path and is independent).**

#### DXF export

**Concept:** Universal CAD interchange format. Accepted by laser
cutters, waterjets, vinyl cutters, plasma tables, CNC routers, and
practically every fabrication shop.

**Value:** Arguably the single highest-leverage "one format, many
machines" target. Opens Eido to any fabrication workflow that
accepts DXF (nearly all of them).

**Effort:** Low (after tier 0). Text-based format, well-documented,
existing Clojure/Java libraries available. Consumes the grouped
polyline output.

**Implementation notes:**
- Target DXF R12 ASCII — simplest spec, universally accepted, pure
  text. Newer versions add features (truecolor, splines) but R12 is
  the "safe" baseline.
- Entities needed: `LWPOLYLINE` for polyline runs, optionally
  `LINE` for single segments. Closed polylines get the closed flag.
- Map each stroke-color group from tier 0 to a DXF `LAYER` with an
  AutoCAD Color Index (ACI) number. DXF 2004+ adds 24-bit truecolor
  via extended group codes if needed.
- Units: DXF carries unit metadata (`$INSUNITS` header var). Default
  to millimeters to match plotter conventions.
- No library dependency required — DXF R12 encoder is ~100 lines of
  pure Clojure over the grouped polyline data.

#### G-code export (GRBL/Marlin flavors)

**Concept:** Direct G-code emission for pen-on-CNC, laser cutters,
foam cutters, and 2D CNC routers.

**Value:** Fills the obvious gap next to the plotter backend.
Unlocks lasers and 2D CNC for existing users without a round-trip
through DXF and a vendor slicer.

**Effort:** Low (after tier 0). Structurally similar to the plotter
backend — polylines + pen-up/down become G0 (rapid) / G1 (feed),
plus Z-axis and optional spindle/laser control.

**Design note:** GRBL and Marlin dialects differ. Target GRBL first
(desktop lasers, hobbyist CNC); add Marlin later if demanded.

**Implementation notes:**
- Header: `G21` (mm), `G90` (absolute coords), `G17` (XY plane).
  Footer: `M5` (spindle/laser off), `G0 X0 Y0` (park).
- Each stroke-color group becomes a tool-change sequence: `M5`,
  optional operator pause (`M0`) with a tool-change message,
  `M3 S<power>` to re-enable.
- Polyline start: `G0 Z<up>` → `G0 X<x0> Y<y0>` → `G1 Z<down> F<z-feed>`
  → tool on → `G1 X.. Y.. F<feed>`. Polyline end: tool off,
  `G0 Z<up>`.
- Y-axis flip: Eido's coordinate system likely has Y-down (SVG
  convention). Most CNC/laser setups are Y-up. Flip Y = bounds-height
  at serialization time.
- Configurable per-job: feed rate, Z-up/Z-down heights, spindle/laser
  power. Keep as opts; don't hardcode.
- Laser mode (`M4` dynamic power vs `M3` constant) is a GRBL config
  toggle; expose as an option.

#### STL / 3MF export

**Concept:** Standard 3D-printing mesh formats. STL is near-trivial
from existing OBJ output. 3MF is the modern XML replacement with
color, units, and multi-material support.

**Value:** Opens 3D printing to anyone using Eido's mesh output.

**Effort:** Low (STL) to medium (3MF). Do not attempt direct
slicing to printer G-code — delegate to Prusa/Cura/Bambu.

**Implementation notes:**
- Independent of the tier 0 refactor — STL/3MF ride the 3D mesh
  path (`scene3d/*`), not the 2D polyline IR.
- STL: binary format preferred (smaller, faster parse). Each
  triangle = 50 bytes (normal + 3 vertices + attribute short).
  The existing `write-obj` already has the face/vertex data model;
  STL is a short adapter that triangulates faces and emits bytes.
- 3MF: zipped XML with `3dmodel.model` as the payload. Adds color,
  units, multi-material. Worth doing only if/when users ask for
  multi-color 3D printing.
- Consider STL first; 3MF only on demand.

### Tier 2 — novel niche captures

Moderate effort; each opens a distinct adjacent audience where
generative tooling is scarce.

#### Embroidery export (DST, PES)

**Concept:** Emit machine-embroidery stitch files from polyline
output. DST is the commercial lingua franca (Tajima, late 1980s);
PES dominates home machines (Brother).

**Value:** Genuinely novel — almost no generative-art toolkit
targets embroidery. Captures a creative community adjacent to
plotter art with minimal crossover from existing tools. Home
embroidery machines are common and relatively affordable.

**Effort:** Medium. Core translation is polyline → stitch
subdivision (DST caps each step at ~12.1mm, requiring segment
splitting) plus color-change and trim commands at layer boundaries.
Everything else (path ordering, jump minimization) is the same
optimization plotter people already solve.

**Research notes:**
- **pyembroidery** — MIT-licensed Python library, reads/writes ~40
  formats, round-trips DST/PES/EXP/JEF/VP3. Reference
  implementation to crib from.
- **Ink/Stitch** — open-source Inkscape extension for SVG →
  embroidery. Handles fills (tatami, satin), underlay,
  auto-routing. Existence proof that SVG → embroidery is solved.
- **PEmbroider** — Processing library for generative embroidery.
  Closest analogue to what Eido would offer, but in Java-land.
- No formal open standard, but DST + PES are well-documented and
  reverse-engineered; safe to target.

**MVP:** DST with running stitch only, no fills, no underlay.
Small scope. Everything past that is incremental.

**Implementation notes:**
- Depends on tier 0 (color groups are load-bearing for
  `COLOR_CHANGE` commands).
- DST is a binary format: 512-byte ASCII header + sequence of
  3-byte stitch records. Header fields include stitch count,
  color-change count, and design extents.
- Each 3-byte record encodes signed dx/dy in 0.1mm units plus
  control-flag bits. Absolute displacement per record is capped
  at ±121 units (±12.1mm); longer moves must be split into
  consecutive records.
- Ops: `STITCH` (needle down each step), `JUMP` (move without
  stitching ≈ pen-up), `COLOR_CHANGE`, `STOP`, `END`. `TRIM` is
  encoded as a specific jump sequence (flavor-dependent).
- Pipeline: grouped polylines → per-segment subdivision at a
  configurable max stitch length (typically 3–5mm for normal
  stitches; jumps can use the full 12.1mm) → interleave JUMPs
  between polylines → interleave COLOR_CHANGE between groups →
  DST byte encoding.
- PES (Brother) is a wrapper around the same stitch stream with
  different headers and thread metadata. Implement DST first;
  PES is an adapter on top.
- Cross-check against pyembroidery for byte-level conformance
  before shipping; its DST reader/writer is the de facto
  reference.

**Patent caution:** Some embroidery fill algorithms (satin-column
generation, auto-tatami planners) may be patent-encumbered. Verify
before implementing beyond plain running stitches.

#### Risograph separations

**Concept:** A pipeline, not a format. Take a composition, split by
color, output one SVG (or high-res raster) per drum with
registration marks and a proof sheet.

**Value:** Riso is dominant in the contemporary art-print scene.
Artists currently do separations by hand in Photoshop/Illustrator.
Generative-native separations would be distinctive and directly
useful to a growing community.

**Effort:** Medium. The "format" part is trivial (SVG per color).
The interesting work is the separation pipeline — color
quantization to drum palettes, halftone options, registration
geometry.

#### Lottie (JSON vector animation)

**Concept:** Open vector-animation format that plays natively in
web, iOS, and Android. Accepts SVG keyframes.

**Value:** Almost no generative-art tooling targets Lottie. Reaches
web and motion designers as a distinct audience from plotter
artists.

**Effort:** Medium. Requires Eido to have a time dimension that
can be keyframed. Builds on whatever animation substrate tier 3
establishes.

### Tier 3 — animation and print plumbing

Expands Eido from "still images and objects" to "pieces that move"
and "pieces that ship to a printer."

#### MP4 / GIF via ffmpeg

**Concept:** Render frame sequences, pipe to ffmpeg for video
encoding.

**Value:** If the art has a time dimension, people want to share
it as video. Enabling step for Lottie and for any future time-based
work.

**Effort:** Medium. Mostly frame-sequence rendering conventions
plus an ffmpeg shell-out at the edge.

#### Print-ready PDF

**Concept:** CMYK color, bleed, crop marks, 300dpi raster fallbacks
for effects that can't be expressed as vectors.

**Value:** Dull but load-bearing for anyone selling prints or
zines. Current SVG/PNG output is not print-ready without manual
conversion.

**Effort:** Medium. Color-model conversion is the tricky part;
layout (bleed/crop) is mechanical.

### Tier 4 — ambitious adjacent-craft bets

New IRs, new communities. Save for when a statement is worth
making.

#### Weaving (WIF)

**Concept:** Weaving Information File is an open standard adopted
across loom software (AVL, TC2, Fiberworks, etc.). Pattern is a
threading/treadling/tie-up grid, not paths.

**Value:** Small but dedicated community. Almost no generative
tooling for weavers. Clear novelty.

**Effort:** High. Totally different IR from polylines — closer to
bitmap-with-structure. Requires new core representation.

#### Knitting (Knitout)

**Concept:** CMU Textiles Lab's open format for machine-knittable
instructions. Targets Shima Seiki, Kniterate, and hacked domestic
machines.

**Value:** Genuinely novel. Nobody bridges generative art → knit
machines from a code-first toolkit today.

**Effort:** High. Not path-based at all — stitch-bed state
operations (knit, tuck, xfer, rack). Requires a full new IR, yarn
carrier planning, bed assignments, and knittability validation.

**Alternatives considered:** KnitML (XML-based hand-knitting
standard) is a worse target — niche adoption, mostly dormant
ecosystem, reference implementation is Java. Knitout has an active
research community behind it and a cleaner spec.

### Tier 5 — wildcards

Tracked for completeness, not actively prioritized:

- **Longarm quilting (QLI, IQP, DXF)** — quilters buy motif files;
  path/stitch-length reasoning overlaps with embroidery. Medium
  effort, niche audience. Patent caution applies to motif-fitting.
- **GLTF** — modern 3D web standard, more useful than OBJ for web
  embedding. Medium effort, niche overlap with current users.
- **Paper-craft nets** — DXF/SVG of unfolded polyhedra for card
  stock. Niche but aesthetically distinctive.
- **Stained glass / marquetry patterns** — paths + color map + cut
  order. Extremely niche.
- **Tileable wallpaper / fabric print** — not a format; a render
  target with repeat-aware export and seam-checking.
- **Sonification / MIDI** — map path structure to notes/rhythm.
  Tiny but devoted audience (algorave, live-coding).
- **Interactive SVG** (SVG + embedded JS) — low effort, niche
  payoff.
- **Vinyl cutter specifics (Cricut, Silhouette)** — mostly
  SVG-based already; the gap is cut-specific metadata (force,
  speed, registration marks). Likely 80% free from existing SVG.

### Summary scoring

Rough H/M/L across the five axes (lower effort is better; higher
on the others is better):

| Target                | Effort | Overlap | Leverage | Novelty | Open |
|-----------------------|--------|---------|----------|---------|------|
| DXF                   | L      | H       | H        | M       | H    |
| G-code (GRBL)         | L      | H       | H        | M       | H    |
| STL / 3MF             | L      | M       | H        | L       | H    |
| Riso separations      | M      | H       | M        | H       | H    |
| Embroidery DST/PES    | M      | M       | H        | H       | H    |
| MP4 via ffmpeg        | M      | H       | H        | L       | H    |
| Print-ready PDF       | M      | H       | M        | L       | H    |
| Lottie                | M      | M       | M        | H       | H    |
| WIF weaving           | H      | L       | M        | H       | H    |
| Longarm quilting      | M      | L       | M        | M       | M    |
| Knitout knitting      | H      | L       | M        | H       | H    |
| GLTF                  | M      | L       | M        | L       | H    |
| MIDI / sonification   | M      | L       | L        | H       | H    |
