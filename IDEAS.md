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

### Margin and edge control

Many compositions need explicit control over whether elements can touch the edges or must stay within a margin. Currently done ad-hoc per algorithm.

**What to add:**
- A margin/padding convention or helper that generators respect

**Implementation notes:**
- Simplest approach: a `scene/with-margin` helper that takes a scene and a margin value, wraps the nodes in a group with a clip rect inset by the margin. Works with existing clip infrastructure.
- Alternatively, a `:scene/margin` key in the scene map that generators can read to constrain their output bounds. Requires convention adoption across generators.
- The clip approach is more reliable — it works even with generators that don't know about margins.

---

## Texture and material

### Paper grain / texture masking

Simulating the texture of physical media (watercolor paper, canvas, printmaking stock) by masking or modulating output with a noise-based or image-based texture.

**What to add:**
- Consider texture overlay / mask support in the render pipeline, or a recipe pattern using existing noise and compositing

**Implementation notes:**
- Eido already has compositing modes (`group/composite`) and noise. A paper grain effect could be: generate a full-canvas noise field, render it as a semi-transparent overlay using a "multiply" or "overlay" blend mode.
- Could be a recipe rather than a feature: show how to create a texture node from noise and composite it over the artwork.
- If making it first-class: a `:texture/grain` effect that takes noise parameters and blend mode, applied at the render stage. Would live in `eido.ir.effect` alongside existing blur/glow.

---

## Output and export

### TIFF output

Standard format for archival fine-art printing. Currently missing — Eido supports PNG, JPEG, SVG, GIF, BMP.

**What to add:**
- TIFF export option in `eido.core/render`

**Implementation notes:**
- Java's `ImageIO` supports TIFF via the `javax.imageio` plugins available since Java 9. Check `ImageIO.getWriterFormatNames()` — if "tiff" is present, it's a one-line addition to the format dispatch in `core.clj`.
- If not available in the JVM version, the `com.twelvemonkeys.imageio` library adds TIFF support — but that would be a dependency. Prefer using the built-in JVM support.
- TIFF should support uncompressed and LZW compression options for archival use.

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

### Keyboard-driven seed browsing

At the REPL or in a preview window: press a key to advance to the next seed, another to save/bookmark the current one. A minimal interactive exploration interface.

**What to add:**
- Consider a `browse-seeds` helper that opens a window with keyboard controls for stepping through seeds

**Implementation notes:**
- Extend the existing `show` window (dev/user.clj) with keyboard listeners: left/right arrow to step seed, 's' to save/bookmark current seed, 'r' to render at full resolution.
- The `show` function already creates a reusable Swing JFrame. Adding a KeyListener that calls a callback `(fn [action seed] ...)` is ~20 lines.
- State: an atom holding the current seed. Arrow keys `swap!` it, the window re-renders on change.
- Bookmarked seeds could be appended to a simple vector atom or printed to REPL for manual recording.

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
