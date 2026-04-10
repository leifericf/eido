# Ideas

Ideas under consideration for future Eido releases. This is a living document — nothing here is promised, but everything here is something we think would genuinely help artists make better work.

Organized by what matters to practicing generative and computational artists, grounded in published needs from the community.

---

## Color

Color is consistently described as the hardest problem in generative art. Artists spend more time on palettes than on algorithms, and existing tools rarely go deep enough.

### Palette-level manipulation

Adjusting an entire palette (warmer, cooler, more muted, darker) currently requires manually mapping over each color. Palette-level operations would make this a single call.

**What to add:**
- `palette/warmer`, `palette/cooler` — shift hue toward warm/cool
- `palette/muted`, `palette/vivid` — adjust saturation across palette
- `palette/darker`, `palette/lighter` — shift lightness across palette

**Implementation notes:**
- Each is a one-liner wrapping `mapv` over existing `color/rotate-hue`, `color/saturate`, `color/desaturate`, `color/lighten`, `color/darken`. The value is in naming the operation at the palette level, not in new algorithms.
- Warmer = rotate hue toward ~30° (orange), cooler = rotate toward ~210° (blue). Amount parameter controls how far.

### Color contrast checking

Knowing whether two colors have enough visual separation matters for readability, plotter work (ink on paper), and accessibility.

**What to add:**
- `color/contrast` — WCAG luminance contrast ratio between two colors
- `color/perceptual-distance` — OKLAB deltaE between two colors

**Implementation notes:**
- WCAG contrast ratio: convert both colors to relative luminance (`0.2126*R + 0.7152*G + 0.0722*B` after linearizing sRGB), then `(L1 + 0.05) / (L2 + 0.05)`. ~15 lines.
- OKLAB deltaE: Euclidean distance in OKLAB space. Requires OKLAB conversion (see above). More perceptually meaningful than RGB distance.
- Both pure functions, no state, no dependencies.

### Non-linear gradient interpolation

Current gradient mapping is linear only. Quadratic, logarithmic, and easing-function-based gradients produce more natural-feeling transitions — especially for mapping noise or depth to color.

**What to add:**
- Easing parameter in `palette/gradient-map` (quadratic, logarithmic, ease-in/out)

**Implementation notes:**
- `gradient-map` currently takes a linear `t` in [0,1]. Add an optional `:easing` key that applies a function to `t` before interpolation: `(gradient-map stops (ease-fn t))`.
- Easing functions are pure `double -> double` math. Could reuse `eido.animate`'s easing if it has them, or add a small set: `ease-in-quad` (`t²`), `ease-out-quad` (`1-(1-t)²`), `ease-in-out-cubic`, `ease-exponential`.

### Color extraction from images

A common workflow: photograph a landscape or a physical painting, extract the dominant colors, use them as a palette. Currently requires external tools.

**What to add:**
- `palette/from-image` — extract dominant colors from a BufferedImage using k-means clustering

**Implementation notes:**
- Sample N random pixels from the image, run k-means in OKLAB space (better perceptual clustering than RGB), return k cluster centroids as a palette vector.
- k-means is ~40 lines of pure Clojure (iterate: assign points to nearest centroid, recompute centroids, repeat until stable).
- Input is a BufferedImage (from `javax.imageio.ImageIO/read`), output is a standard Eido palette vector. Could also accept a file path for convenience.
- Consider sorting the resulting palette by lightness for consistent ordering.

---

## Randomness and distributions

Uniform randomness looks artificial. Natural phenomena follow Gaussian, power-law, and other shaped distributions. Artists spend significant time fine-tuning probability distributions to get organic-feeling results.

### Geometric random distributions

Sampling uniformly on or inside circles and spheres is a common need for scatter patterns, particle emission, and spatial layouts. Currently requires manual trigonometry.

**What to add:**
- `prob/on-circle`, `prob/in-circle` — point on circumference / inside disc
- `prob/on-sphere`, `prob/in-sphere` — point on surface / inside volume

**Implementation notes:**
- `on-circle`: `[r*cos(θ) r*sin(θ)]` with `θ` uniform in [0, 2π). 2 lines.
- `in-circle`: rejection sampling (sample in square, reject outside circle) or `sqrt(u)*r` trick for uniform area distribution. The sqrt trick is ~3 lines.
- `on-sphere`: use Gaussian method — sample 3 independent Gaussians, normalize to unit vector, scale by radius. Avoids polar clustering artifacts.
- `in-sphere`: same as on-sphere but multiply by `cbrt(u)` for uniform volume distribution.
- All take `(radius seed)` or `(radius center seed)`, return `[x y]` or `[x y z]`.

### Controlled disorder (calibrated randomness)

A recurring theme in generative art: not total chaos, but precisely calibrated deviation from order. Introduce a small percentage of randomness into an otherwise systematic composition.

**What to add:**
- Consider a `jitter` or `disorder` parameter pattern that can be applied uniformly across systematic layouts (grids, radial patterns, etc.)

**Implementation notes:**
- Could be a function `(scatter/jitter points amount seed)` that displaces each point by a Gaussian offset scaled by `amount`. Works on any `[[x y] ...]` collection.
- Or a `:jitter` option on `scatter/grid` and `scene/distribute` that adds displacement during generation.
- The key insight: 0% jitter = perfect grid, 1% = barely perceptible wobble, 50% = structured chaos. The amount parameter is the artistic control.

---

## Noise

### Simplex noise

Eido has Perlin noise (2D, 3D, FBM, turbulence, ridge). Simplex noise has fewer directional artifacts, better scaling properties, and is the preferred choice for many artists. Both should be available.

**What to add:**
- `noise/simplex2d`, `noise/simplex3d`
- Simplex-based FBM variant

**Implementation notes:**
- The original simplex noise patent (US 6,867,776) expired January 2022. However, prefer OpenSimplex2 — a clean public-domain algorithm specifically designed as an unencumbered alternative. Well-documented, translates directly to Clojure/Java.
- ~150-200 lines for 2D+3D. Pure math, no dependencies. Uses a different gradient table and simplex grid instead of Perlin's hypercube grid.
- FBM variant: same octave-layering pattern as existing `noise/fbm`, just swap the base noise function. Could generalize `fbm` to accept a noise function parameter.

### 4D noise

Essential for seamlessly looping animated noise (use the 4th dimension as a time loop) and for generating tileable patterns.

**What to add:**
- `noise/perlin4d` and/or `noise/simplex4d`

**Implementation notes:**
- 4D Perlin extends the existing 3D implementation with one more dimension in the gradient table and interpolation. Conceptually straightforward but the code is bulkier (~100 lines for the 4D case alone).
- 4D simplex is more efficient than 4D Perlin (fewer interpolation steps). If implementing simplex noise, do 4D at the same time.
- Looping trick: `noise(x, y, cos(t*2π)*r, sin(t*2π)*r)` gives seamless temporal loops. Document this pattern.

---

## Curves and paths

### Chaikin curve smoothing

Eido has Catmull-Rom smoothing (`smooth-commands`). Chaikin smoothing is a different algorithm that produces a distinct aesthetic — rounder, more uniform curves. Both are standard tools.

**What to add:**
- `aesthetic/chaikin` or `aesthetic/chaikin-commands` — iterative corner-cutting smoothing

**Implementation notes:**
- Chaikin's algorithm: for each pair of adjacent points, replace with two new points at 25% and 75% along the segment. Repeat N times (typically 3-5).
- ~15 lines of pure Clojure. Input/output are path command vectors (same format as `smooth-commands`).
- Consider a `:retain-ends` option that keeps the first and last points fixed (common variant for open curves).
- Produces a very different feel from Catmull-Rom — more "rounded corners" than "flowing curves."

### Path simplification

Reduce the number of points in a path while preserving its shape. Essential for plotter output (fewer pen movements) and for cleaning up noisy generated paths.

**What to add:**
- `path/simplify` — Douglas-Peucker or Visvalingam simplification

**Implementation notes:**
- Douglas-Peucker: recursive algorithm, ~30 lines. Find the point farthest from the line between start and end; if distance > epsilon, recurse on both halves; otherwise, discard intermediate points. Epsilon controls aggressiveness.
- Visvalingam: area-based, removes the point that contributes the least triangle area. Better at preserving overall shape character. ~40 lines with a priority queue.
- Douglas-Peucker is simpler and probably sufficient as a first implementation. Operates on `[[x y] ...]` point vectors; wrap to work with path commands.

### Curve splitting and interpolation

Split a curve at regular arc-length intervals, interpolate between two curves, trim a curve to bounds. These are fundamental operations for flow field streamlines, morphing animations, and bounded compositions.

**What to add:**
- `path/split-at-length` — divide curve into equal-length segments
- `path/interpolate` — blend between two paths
- `path/trim-to-bounds` — clip path to a bounding rectangle

**Implementation notes:**
- `split-at-length`: walk the path accumulating arc length, emit a new point each time accumulated length crosses the step threshold. Bezier segments need subdivision (de Casteljau) for accurate arc-length measurement. ~50 lines.
- `path/interpolate`: given two paths with the same number of commands, lerp corresponding control points by parameter `t`. Simpler than full morphing — just requires matching structure. ~20 lines.
- `path/trim-to-bounds`: Cohen-Sutherland or Liang-Barsky line clipping extended to path segments. More complex for curves — may need to flatten to line segments first, then clip. ~40 lines for the line-segment case.

### Polygon operations

Point-in-polygon testing, polygon shrinking/insetting, polygon rotation. Useful for spatial queries, margin control, and composition.

**What to add:**
- `path/contains-point?` — point-in-polygon test
- `path/inset` — shrink polygon inward by a distance

**Implementation notes:**
- `contains-point?`: ray casting algorithm — cast a horizontal ray from the point, count edge crossings. Odd = inside. ~20 lines. Works on any closed polygon defined as `[[x y] ...]`.
- `path/inset`: offset each edge inward by `d` along its normal, recompute intersections. Simple for convex polygons (~15 lines); concave polygons need special handling at reflex vertices (clip or skip collapsing edges). Start with convex-only, document the limitation.

---

## Composition and spatial layout

### Flow field collision detection

When tracing streamlines through a flow field, enforce minimum distance between curves. Without this, flow field output tends to have uneven density — clumps and voids.

**What to add:**
- Collision avoidance option in flow field tracing, using spatial hashing to halt curves that approach existing ones too closely

**Implementation notes:**
- Maintain a spatial grid (same acceleration structure used in circle packing). As each streamline step is taken, check the grid for nearby existing points. If any point is within `min-distance`, halt the curve.
- Add `:collision-distance` option to `flow/flow-field`. Default `nil` (current behavior). When set, enables the spatial grid.
- Starting point distribution also matters: Poisson disc sampling for starting points (already available via `gen.stipple/poisson-disk`) produces more even coverage than random or grid starts.
- ~40 lines on top of existing flow field code.

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

### Low-opacity layered rendering

A core technique for simulating watercolor, ink wash, and other translucent media: render 30–100 layers of slightly deformed, low-opacity shapes. The result has organic depth that single-layer rendering cannot achieve.

**What to add:**
- Consider helpers or a recipe pattern for iterative transparent layering with per-layer deformation

**Implementation notes:**
- Not a single function — more of a compositional pattern. The building blocks exist: shapes with opacity, transforms, noise-based deformation via `path/distort`.
- A recipe showing the pattern: generate a base polygon, deform it N times (each with independent noise seed), render each at low opacity (0.03–0.05), layer them into a group.
- Could add a `texture/layered` helper that takes a shape, deformation function, layer count, and opacity, and returns a group node. ~20 lines.
- Key challenge: rendering many low-opacity overlapping polygons can be slow with Java2D. May need to document performance characteristics and recommended layer counts.

### Paper grain / texture masking

Simulating the texture of physical media (watercolor paper, canvas, printmaking stock) by masking or modulating output with a noise-based or image-based texture.

**What to add:**
- Consider texture overlay / mask support in the render pipeline, or a recipe pattern using existing noise and compositing

**Implementation notes:**
- Eido already has compositing modes (`group/composite`) and noise. A paper grain effect could be: generate a full-canvas noise field, render it as a semi-transparent overlay using a "multiply" or "overlay" blend mode.
- Could be a recipe rather than a feature: show how to create a texture node from noise and composite it over the artwork.
- If making it first-class: a `:texture/grain` effect that takes noise parameters and blend mode, applied at the render stage. Would live in `eido.ir.effect` alongside existing blur/glow.

### Analog media emulation

Ink, pencil, gouache, watercolor, woodcut — each has distinct stroke characteristics. Artists working at the boundary of digital and physical want their algorithmic output to carry the feel of a specific medium.

**What to add:**
- Path aesthetic presets tuned for specific physical media characteristics (stroke width variation, opacity falloff, edge roughness)

**Implementation notes:**
- `eido.path.aesthetic/stylize` already accepts a data-driven pipeline of transforms. Media presets would be curated parameter maps fed to `stylize`.
- Example: `aesthetic/ink-preset` = `{:smooth {:tension 0.3} :jitter {:amount 0.8 :seed s} :dash {:length 3 :gap 0.5}}`. Each preset is just data — no new machinery.
- Caution: presets can become "easy, not simple" if they hide too much. Keep them as named parameter maps that users can inspect, modify, and combine. Not opaque black boxes.

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

### Focused sub-exploration

After a broad seed sweep, narrow in: "I liked seeds 42–48, now show me 420–480 at higher resolution." Telescoping from overview to detail.

**What to add:**
- Consider `seed-grid` accepting a `:zoom` or `:around` parameter that generates variations near a specific seed region

**Implementation notes:**
- Simplest approach: `:around seed :radius 100` generates editions with seed values `(range (- seed radius) (+ seed radius))`. Since `edition-seed` uses murmur3 mixing, nearby edition numbers still produce uncorrelated outputs — but the artist gets a semantic "neighborhood" to browse.
- Alternative: `:seeds [42 47 103 256 ...]` to render a specific list of hand-picked seeds. Useful after initial exploration to compare favorites side by side.

### Parameter narrowing

Start with a wide parameter sweep, then narrow: "density between 15 and 25 looked best, now sweep that range at finer resolution."

**What to add:**
- Natural with existing `param-grid` — document the pattern of progressive narrowing

**Implementation notes:**
- No new code needed — the existing `param-grid` supports this directly. First call: `:values [0 50 100 150 200]`. Second call: `:values [50 60 70 80 90 100]`. Third call: `:values [72 74 76 78 80]`.
- Document this as a recipe in the Guide: "Start wide, find the interesting zone, zoom in."

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

## Long-form editions and series

### Complete parameter space testing

Before releasing a series, artists need to verify that the full output space is coherent — no broken outputs, acceptable trait distribution, consistent quality. This means rendering hundreds or thousands of test editions.

**What to add:**
- `seed-grid` already helps. Consider adding trait distribution summary (histogram of trait values across N editions)

**Implementation notes:**
- Generate N editions' worth of params via `series-range`, apply `derive-traits`, then `frequencies` on each trait key.
- Output: a map `{:trait-name {"label" count ...} ...}`. Pure data — artists can inspect it at the REPL, or build a visual histogram from it.
- ~15 lines wrapping existing `series-range` and `derive-traits`.

### Trait distribution analysis

After generating many editions, see the statistical distribution of derived traits. "Are 'rare' traits actually rare? Is the density distribution skewed how I intended?"

**What to add:**
- `series/trait-summary` — given a spec and seed range, compute frequencies of each trait bucket

**Implementation notes:**
- Signature: `(trait-summary spec master-seed n-editions trait-buckets)`. Returns `{:trait {"label" count} ...}`.
- Internally: `(series-range spec master-seed 0 n-editions)` → `(mapv #(derive-traits % trait-buckets) %)` → per-key `frequencies`.
- Could also return percentages alongside counts for quick readability checks ("is :rare actually 5%?").
- ~15 lines. Pure function, no rendering needed.

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
