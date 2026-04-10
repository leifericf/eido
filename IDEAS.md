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

### Save-interesting-seed pattern

A lightweight helper for capturing seeds and parameters during exploration, so discoveries made during a sketching session aren't lost.

**Why:** Artists working at the REPL often stumble on interesting results while sweeping seeds or parameters. Without a capture mechanism, they have to manually note seeds — easy to forget, breaks flow.

**What to add:**
- A `save-seed` or `bookmark` helper that appends seed + params + optional notes to a log file (EDN or CSV)
- Optionally renders a small thumbnail alongside the bookmark for later browsing
- Works naturally with `seed-grid` and `param-grid` exploration

**Sketch:**
```clojure
;; During exploration
(save-seed! "sketches/keeper.edn" {:seed 4217 :params params :note "nice density"})

;; Later
(load-seeds "sketches/keeper.edn")
;; => [{:seed 4217 :params {...} :note "nice density" :timestamp "..."}]
```

---

## Export and fabrication

### Per-layer plotter export

A helper that splits a scene by stroke color or pen assignment and writes one file per layer — the standard workflow for multi-pen plotters.

**Why:** Plotter artists typically assign colors to physical pens and need separate files per pen. Eido already has `:group-by-stroke` in SVG output, but there's no helper that takes that grouping and writes `pen-0.svg`, `pen-1.svg`, etc.

**What to add:**
- A `plotter/export-layers` function that takes a scene and output directory, splits by stroke, and writes one SVG per layer
- Each layer SVG uses `:stroke-only` and `:optimize-travel`
- Optionally writes a combined preview SVG with all layers visible

**Implementation notes:**
- Builds on existing `:group-by-stroke` and `:optimize-travel` plotter opts.
- Should also document typical tolerances: polyline flattening for curves, minimum stroke width for physical pens.

**Sketch:**
```clojure
(plotter/export-layers scene "output/plotter/"
  {:optimize-travel? true})
;; => output/plotter/pen-0.svg
;;    output/plotter/pen-1.svg
;;    output/plotter/preview.svg
```

### Batch fabrication export

A helper that takes a batch of editions and exports a complete package: images, vector files, manifests, and an optional contact sheet.

**Why:** Edition-based artists need to produce not just images but a complete archival package. Doing this manually for 50+ editions is tedious and error-prone.

**What to add:**
- A function that wraps `render-editions` and produces: per-edition images (PNG/TIFF/SVG), per-edition manifests, and an optional contact sheet (grid of thumbnails as a single image)
- Contact sheet useful for proofing, exhibition planning, and social media

**Implementation notes:**
- Contact sheet can reuse `seed-grid` rendering internally.
- This is convenience — artists can compose the pieces manually today. Ship if the pattern emerges as common, defer otherwise.

---

## Documentation and onboarding

### Onboarding by artistic intent

Artists think "I want to make plotter-friendly line work" not "I need the `eido.gen.flow` namespace." An intent-based entry point in the Guide would connect goals to recipes.

**What to add:**
- "I want to..." section linking artistic goals to recipes, examples, and modules

**Implementation notes:**
- Docs-only change in `examples/site/pages.clj`. Add a new section to the Guide intro with 5–7 intent cards, each linking to the relevant recipe page and 2–3 gallery examples.
- Intents to cover: plotter line work, painterly fields, geometric grids, animated patterns, long-form editions, organic textures, color exploration.
- Could also serve as the landing page's "Start here" section for new users.

### Machine discoverability

A structured summary file at the project root that helps automated systems understand Eido's capabilities and find relevant documentation.

**What to add:**
- `llm.txt` with project summary, namespace map, and documentation pointers

**Implementation notes:**
- Plain text file at project root, ~60 lines. Structured sections: project description, key concepts, namespace index with one-line descriptions, links to docs/examples/API.
- Also deployable to site root (`eido.leifericf.com/llm.txt`) for web-based discovery.
- Low effort, do alongside any release.

### Known limitations and scope boundary

An honest statement of what Eido does and doesn't do — sets expectations and prevents scope creep.

**What to add:**
- A short page or section covering: practical limits (performance cliffs for very large scenes, in-memory growth), and explicit non-goals (no GUI editor, no heavy CAD/CAM, no audio/livecoding, no web IDE, no deep 3D modeling beyond the existing scene3d pipeline)

**Implementation notes:**
- Part documentation, part product identity. Saying "no" clearly is as important as listing features. Can be a few paragraphs in the Guide or a standalone page.

---

## Infrastructure

### Golden workflow tests

End-to-end tests that assert key workflows produce valid output — a stronger trust signal than unit tests alone.

**What to add:**
- A small suite of integration tests that run complete workflows: long-form edition render (PNG + manifest), plotter SVG (stroke-only, optimized travel), print-ready TIFF (units, DPI, paper, margins), param grid, polyline export
- Tests verify the workflow completes without error and outputs have expected structure (file exists, correct format, reasonable size) — not pixel-level comparison

**Implementation notes:**
- Don't gate on checksums or image diffs — too brittle across JVM/platform versions. Assert structure: files written, manifest parseable, polyline output has expected keys.
- Build on the existing 90-file test suite. This is additive infrastructure, not a rewrite.
- Consider running as a separate test alias (`:test/golden`) since render tests are slower than pure-function tests.

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
