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
backends: SVG, plotter, polyline, OBJ, DXF, G-code, HPGL. This
section surveys further export targets, organized by effort and
audience captured.

The shared motion-stream substrate —
`eido.io.polyline/extract-grouped-polylines` and
`optimize-travel-polylines` — is in place and ready to be consumed
by new backends; no further IR refactor is needed for any tier-1
or tier-2 target below. The substrate also clips polylines against
`:group/clip` geometry and surfaces dropped fills via
`summarize-drops`, so plotter output matches the visible
composition and the loss is auditable in the manifest sidecar.

### Substrate enhancements

Improvements to the polyline extractor itself — they expand what
*every* motion-stream backend (DXF, G-code, HPGL, polyline EDN,
future embroidery/Lottie/etc.) can faithfully represent.

#### Polyline merging at shared endpoints

**Concept:** Two polylines whose endpoints coincide (within an
epsilon) can be concatenated into one longer polyline, eliminating
a pen-up/pen-down pair. Common after path operations or generative
algorithms that emit segment-at-a-time output (contour lines,
hatch fills, mesh wireframes).

**Effort:** Low to medium. Build an endpoint hash, greedy-merge
until no more merges possible. Reverse one polyline when needed to
align endpoints. Should run before travel optimization since merged
polylines change the candidate set.

**Value:** Reduces pen-up time on plotter output and stitch-jump
count on embroidery output. Especially impactful on hatch fills
and contour-line scenes (currently each segment is its own
polyline). No correctness change.

#### Smarter travel optimization

**Concept:** Current `optimize-travel-polylines` is greedy
nearest-neighbor — fast but typically ~25% worse than optimal on
TSP-shaped problems. 2-opt local search is a small extension
(swap pairs of polyline orderings, accept if total travel
shrinks) that closes most of that gap, and OR-opt or simulated
annealing can do better on larger inputs.

**Effort:** Low (2-opt) to medium (full SA). Polyline reversal
is also a free degree of freedom — for two-endpoint polylines
either end can be the start, doubling the search space cheaply.

**Value:** Direct plotter time savings, especially on dense
designs (hundreds of polylines). Embroidery jump-minimization
benefits identically.

**Why hold off:** Greedy-NN is "good enough" for tens of
polylines. Revisit when users hit hour-long plot times or when
the hatch/contour density used in catalog scenes grows.

#### All-positive coordinate normalization

**Concept:** Many CNC controllers (and some plotters) reject
negative coordinates or have soft limits at the bed origin.
Geometry placed near `[0 0]` in scene space, after Y-flip and
scale, can still emit negative X — and a shape that extends past
the canvas edge produces negative coords directly.

Add an optional `:translate-to-positive` flag (default off for
backward compat) that computes the bounding box of all emitted
points and shifts everything so the minimum is `[0 0]`.

**Effort:** Low. Single bbox pass plus a translate before
emission.

**Value:** "It just works" on stricter controllers without the
user having to compute and apply a manual offset.

### Backend refinements

Improvements to *existing* shipped backends (DXF R12, GRBL G-code).
Each preserves the current default behavior and adds an opt-in mode
or option.

#### Higher-fidelity DXF: native CIRCLE/ARC/ELLIPSE + truecolor

**Concept:** R12 ASCII (current target) requires polyline
approximation for circles, arcs, and ellipses, and limits color
to the 8-entry ACI palette. R2000+ supports native `CIRCLE`,
`ARC`, `ELLIPSE` entities and truecolor via group code 420.

For artists exporting curves to laser/router workflows, native
arcs cut more smoothly (no polygonal stair-stepping) and are
smaller files. Truecolor preserves stroke colors exactly instead
of nearest-neighbor mapping.

**Effort:** Medium. R2000+ has a mandatory `CLASSES` section and
more required header variables than R12; not a one-line version
bump. Worth doing as a separate `:dxf-version :r2000` opt rather
than replacing R12.

**Why hold off:** R12 is the most universally readable DXF
flavor and "works everywhere"; R2000+ adds fidelity at the cost
of compatibility with legacy CAD tools. Track until a user wants
either truecolor or native curves.

#### G-code: dialect option + G2/G3 arcs

**Concept:** Current writer emits GRBL only. Marlin (3D-printer
firmware sometimes used for pen plotting) and LinuxCNC have
slightly different conventions for spindle/laser commands and
tool-change semantics. A `:dialect` option (`:grbl :marlin
:linuxcnc`) would let users target their controller without
post-processing.

Separately, GRBL itself supports `G2`/`G3` (clockwise/CCW arc
moves), which would let circles and arcs traverse natively
instead of as polygon approximations — same fidelity benefit as
DXF native arcs, plus smoother motion on the controller.

**Effort:** Medium. Dialect is a thin per-feature dispatch.
G2/G3 needs arc-aware extraction (probably a sibling of
`extract-grouped-polylines` that preserves arc primitives — and
the substrate change ripples to any other backend that wants
native arcs).

**Why hold off:** GRBL covers most desktop CNC and laser users;
Marlin/LinuxCNC are minority asks. Add when a specific user
hits the gap.

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

One meta-question dominates the ranking regardless of the table:

- **Who are we trying to attract next?** Existing
  plotter/fabrication users → tier 1. A new craft community → pick
  one tier 2 or tier 4 target and go deep. Each new audience is a
  distinct docs/gallery/example effort, not just code.

### Tier 1 — quick wins (serializer-only)

Cheap, universal, strictly expand utility for existing users. DXF
(universal CAD interchange), GRBL G-code (lasers, 2D CNC), and
HPGL (vintage pen plotters) are already shipped on the substrate.
STL is the remaining candidate.

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
- Builds directly on the shipped grouped-polyline substrate
  (color groups are load-bearing for `COLOR_CHANGE` commands).
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
