# Ideas

Ideas under consideration for future Eido releases. This is a living document — nothing here is promised, but everything here is something we think would genuinely help artists make better work.

Organized by what matters to practicing generative and computational artists, grounded in published needs from the community.

---

## Composition and spatial layout

### Resolution-independent coordinates with real-world units

Artists who produce physical output (prints, plotter work) need to think in centimeters or inches, not pixels. Currently Eido works in pixels only, with DPI metadata as an afterthought.

**What to add:**
- Consider a coordinate system option: `:image/units :cm` with `:image/dpi 300` that translates real-world dimensions to pixel coordinates automatically

**Implementation notes:**
- Conversion is simple: `pixels = cm * (dpi / 2.54)`. Could be a preprocessing step that transforms `:image/size` before compilation.
- The harder question: should *all* coordinates in the scene (circle radii, line endpoints, stroke widths) be in the same unit? Probably yes — a scene described in centimeters should be internally consistent.
- Approach: a `scene/with-units` helper that walks the scene map and scales all numeric coordinates by the conversion factor. Applied once at render time. Keeps the core pipeline pixel-based.
- Standard paper sizes as presets: `scene/a4`, `scene/letter`, `scene/a3` that return `{:image/size [...] :image/dpi 300}`.

---

## Output and export

### Resolution-independent re-rendering

Regenerate any saved seed at a different resolution without changing the composition. The same seed at 800×800 and 8000×8000 should produce the same image, just sharper.

**What to add:**
- Document and verify that all generators produce resolution-independent output when coordinates are expressed relative to canvas size

**Implementation notes:**
- This is primarily a documentation and testing task. Write a test that renders the same scene at 400×400 and 4000×4000 (with `:scale 10`) and verifies visual equivalence (downscale the large render and compare).
- Any generator that uses absolute pixel values internally (e.g., hardcoded step sizes in flow fields) would need to scale those relative to canvas size. Audit each generator.
- Document the pattern: "express all coordinates as fractions of canvas size or relative to `:image/size`."

### Polyline / JSON data export

For CNC mills, laser cutters, and custom plotter software, raw polyline coordinate data (as JSON or EDN) is more useful than SVG.

**What to add:**
- `render` option to export path data as EDN or JSON (vector of polylines, each a vector of `[x y]` points)

**Implementation notes:**
- The IR already has path commands as data (`[[:move-to x y] [:line-to x y] ...]`). Extracting polylines means flattening curves to line segments and collecting point coordinates.
- Add a `:format :polylines` or `:format :edn-paths` option to `render` that, instead of rendering pixels, walks the compiled ops and extracts path data.
- For curves (`:curve-to`, `:quad-to`), flatten to line segments using de Casteljau subdivision at a configurable resolution.
- Output format: `{:polylines [[[x1 y1] [x2 y2] ...] ...] :bounds [w h]}`. Write as EDN (native) or JSON (via `clojure.data.json` if available, or manual formatting).

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
