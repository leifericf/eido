# Ideas

Ideas under consideration for future Eido releases. This is a living document — nothing here is promised, but everything here is something we think would genuinely help artists make better work.

Organized by what matters to practicing generative and computational artists, grounded in published needs from the community.

---

---

## Reproducibility and archival

### Render manifest

A machine-readable sidecar file emitted alongside render output that captures everything needed to reproduce it exactly — even years later. The clearest gap for professional edition-based practice.

**The reproduction problem:** "same seed + same params = same output" only holds if the code hasn't changed. Two years from now, a noise function might have been tweaked or a rendering path refined. To guarantee reproduction, the manifest must capture enough to reconstruct the render without depending on the artist's scene-fn still existing or Eido being unchanged.

**What the manifest must include:**
- `:scene` — the complete scene map (the output of scene-fn, not the fn itself). This is plain EDN — if you have the scene map and the right Eido version, you can always re-render. This is the single most important field.
- `:seed` and `:params` — the inputs that produced the scene, for provenance and re-generation (useful if the artist wants to tweak, not just reproduce)
- `:eido/version` — both git tag and SHA (e.g., `{:tag "v1.0.0" :sha "abc1234"}`). Tag alone is ambiguous (tags can move, artist might be between tags); SHA alone is unfriendly. Both together.
- `:project/sha` — the artist's own project git SHA, pinning their scene-fn and any custom code
- `:timestamp` — when the render was produced
- `:output-paths` — list of files written
- `:render-opts` — the opts map passed to `render` (format, DPI, scale, plotter options, etc.)

**Optional enrichment (add when relevant):**
- `:traits` (from `trait-summary`), `:palette`, `:edition`, `:master-seed`

**Implementation notes:**
- Plain EDN sidecar file — no database, no dependency, just `spit` and `pr-str`. The scene archive idea (below, under Infrastructure) is a separate concern for queryable history over time; the manifest is simpler and stands alone.
- Wrap `render-to-file` and `render-editions` so they can optionally emit manifests via an `:emit-manifest? true` opt.
- The loader reads a manifest, extracts the scene map, and re-renders with the same opts — no need to re-run user code.
- Manifest format should be documented in the Guide and API docs.
- One canonical example: generate a small edition batch → manifests → re-render from manifest.

**Sketch:**
```clojure
;; Render with manifest
(render scene {:output "edition-042.png" :emit-manifest? true})
;; => writes edition-042.png + edition-042.edn

;; The manifest contains the full scene map:
;; {:scene     {:image/size [800 800] :image/nodes [...] ...}
;;  :seed      4217
;;  :params    {:hue 142.3 :density 22.1 :palette :ocean}
;;  :eido/version {:tag "v1.0.0" :sha "abc1234"}
;;  :project/sha  "def5678"
;;  :timestamp    "2026-04-10T14:32:00Z"
;;  :output-paths ["edition-042.png"]
;;  :render-opts  {:dpi 300}}

;; Re-render from manifest — uses stored scene + opts
(render-from-manifest "edition-042.edn")
;; => identical output
```

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

### Workflow guides

End-to-end guides organized by artistic goal, published as a new top-level section on the docs site alongside Guide, Gallery, API Reference, and How It Works. Each workflow walks through a complete process — longer and more narrative than existing Recipes, pulling together multiple Guide sections into a single coherent path.

**Structure:**
- New "Workflows" nav entry on the docs site, linking to separate pages
- The existing Recipes category in the Guide stays as-is — those are short technique patterns, not end-to-end workflows
- The existing Guide stays feature-by-feature; workflows are goal-by-goal

**Workflows to write (7 pages):**

1. **Sketching & iteration** — the REPL-driven making loop. `show`, `watch-scene`, `seed-grid`, `param-grid`, capturing interesting seeds. Every other workflow builds on this, so it's the natural first one.
2. **Long-form editions** — `series-params` → `edition-seed` → scene function → `render-editions` → `trait-summary` → manifest. The Art Blocks / numbered-edition pipeline.
3. **Plotter art** — scene design → stroke-only SVG → `group-by-stroke` → `optimize-travel` → per-layer export. Multi-pen plotting. Includes a "beyond plotters" section on polyline/fabrication export for CNC and laser cutters.
4. **Print production** — `paper` preset → `with-units` → `with-margin` → high-DPI TIFF/PNG with DPI metadata. Gallery-ready physical prints.
5. **Animation & screen** — frame sequences → looping noise / `pulse` / `fade-*` → GIF / animated SVG / PNG sequence for ffmpeg. Motion work, social media, projection.
6. **3D generative art** — mesh construction → transforms → UV texturing → NPR rendering → 2D↔3D bridge. Its own domain with real depth.
7. **Color & palette development** — OKLAB/OKLCH manipulation → palette generation → analysis (`min-contrast`, `sort-by-lightness`) → extraction (`from-image`) → application (`by-palette`, `by-noise-palette`). Developing a color system for a project.

**Implementation notes:**
- Most building blocks already exist and are documented individually in the Guide. Workflows are connective tissue — showing how they compose for a real artistic goal.
- Each page should include: a "what you'll need" list of namespaces, complete working code, rendered output, and tips for common variations.
- Pages are built in `examples/site/pages.clj` as new top-level content (like the Architecture page), not as entries in `docs-categories`.

### Machine discoverability

A structured summary file at the project root that helps automated systems understand Eido's capabilities and find relevant documentation.

**What to add:**
- `llm.txt` with project summary, namespace map, and documentation pointers

**Implementation notes:**
- Plain text file at project root, ~60 lines. Structured sections: project description, key concepts, namespace index with one-line descriptions, links to docs/examples/API.
- Also deployable to site root (`eido.leifericf.com/llm.txt`) for web-based discovery.
- Low effort, do alongside any release.

### Stability & compatibility policy

A short, clear statement of what's stable and what's provisional — so artists know what they can depend on.

**What to add:**
- A "Stability" section (in the Guide or README) that identifies: stable APIs (core scene structure, `render`, `eido.gen` facade, `eido.scene`, `eido.gen.series`), provisional APIs (newer modules like `eido.gen.ca`, `eido.gen.boids`), and what "stable" means (additions OK, deprecations with warning, core signatures won't change)
- `:stability` metadata on public vars (`:stable` or `:provisional`), rendered as badges in the API Reference — same pattern as the existing `:convenience` metadata badges

**Implementation notes:**
- This is ~20 lines of prose, not a formal SemVer policy document. Keep it honest and short. Can live in a "Stability" section of the Guide or as a short paragraph in the README.
- Default unlabeled functions to `:stable` so only provisional ones need explicit tagging. Two values only — no need for `:experimental`, `:deprecated`, etc. until there's a real need.

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
