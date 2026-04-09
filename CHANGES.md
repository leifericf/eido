# Changes

## v1.0.0-beta2 — Generative Art & Artist Experience

### New modules

- **`eido.gen.prob`** — Seeded probability distributions and sampling utilities. Uniform, Gaussian, weighted choice, weighted sampling, deterministic shuffle, coin flip, pick. All functions take an explicit seed for reproducible results — no global RNG state.
- **`eido.path.aesthetic`** — High-level path aesthetic helpers. `smooth-commands` (Catmull-Rom curve fitting from path commands), `jittered-commands` (organic noise displacement), `dash-commands` (arc-length dash segmentation). Re-exported from `eido.path`.
- **`eido.gen.circle`** — Variable-radius circle packing with spatial grid acceleration. Supports rectangular regions and arbitrary closed paths via `circle-pack-in-path`. Includes `pack->nodes` for scene integration.
- **`eido.gen.subdivide`** — Recursive rectangular subdivision (binary space partitioning). Configurable depth, min-size, split ratios, orientation bias, and padding. Includes `subdivide->nodes`.
- **`eido.gen.series`** — Long-form generative series utilities for seed-driven workflows (Art Blocks / fxhash style). `edition-seed` uses murmur3-style hashing for uncorrelated per-edition seeds. `series-params` samples from typed parameter specs (uniform, gaussian, choice, weighted-choice, boolean).
- **`eido.gen.ca`** — Cellular automata (Game of Life, Highlife, custom rules) and Gray-Scott reaction-diffusion simulation. Primitive arrays for performance. Named RD presets: coral, mitosis, ripple, spots.
- **`eido.gen.boids`** — Flocking simulation with steering behaviors (separation, alignment, cohesion, seek, flee, wander). Spatial grid for O(n) neighbor lookup. Presets: `classic`, `murmuration`. Includes `flock->nodes` for rendering as oriented triangles or circles.

### Enhancements

- **`eido.color.palette`** — Add `weighted-pick`, `weighted-sample`, `weighted-gradient`, and `shuffle-palette` for artist-friendly color frequency control
- **`eido.gen`** — Re-export all new module functions via the `eido.gen` facade namespace
- **`eido.path`** — Re-export `smooth-commands`, `jittered-commands`, `dash-commands`, `stylize` from `eido.path.aesthetic`

### Convenience helpers

21 convenience functions marked with `:convenience` metadata for API Reference display:

- **`eido.scene`** — `circle-node`, `rect-node`, `line-node` (node shorthands), `polar->xy` (trig-free coords), `ring` (place shapes in a circle), `points->path` (points to commands)
- **`eido.animate`** — `pulse` (sine oscillation), `fade-linear`, `fade-out`, `fade-in` (opacity decay)
- **`eido.color`** — `rgb`, `hsl` (shorthand constructors)
- **`eido.color.palette`** — `with-roles` (named palette roles)
- **`eido.gen.vary`** — `by-palette` (weighted palette coloring), `by-noise-palette` (noise-to-palette)
- **`eido.gen.circle`** — `pack->colored-nodes` (pack + palette in one call)
- **`eido.gen.subdivide`** — `subdivide->palette-nodes` (rects + palette coloring)
- **`eido.path.aesthetic`** — `stylize` (data-driven path transform pipeline)
- **`eido.gen.prob`** — `mixture` (sample from mixed distributions)
- **`eido.gen.series`** — `derive-traits` (categorize params into labels)

### Docs

- Rewrite Generative docs category with artist-friendly language, progressive examples, and "how to tweak" guidance throughout
- Add 14 rendered inline previews (including animated boids gif) to generative docs sections — all generated during CI site build
- Add documentation sections for controlling randomness, circle packing, subdivision, weighted palettes, path aesthetics (smooth/jitter/dash with visual comparisons), long-form series, cellular automata, reaction-diffusion, and boids
- Add new namespaces to API reference: `eido.gen.prob`, `eido.gen.circle`, `eido.gen.subdivide`, `eido.gen.series`, `eido.gen.ca`, `eido.gen.boids`, `eido.path.aesthetic`
- API Reference: structured signature cards with syntax highlighting, search bar, "Helper" badges on convenience functions with "Wraps: fn-name" links
- Rename "Docs" to "Guide" with subtitle "A hands-on tour of Eido — from first shapes to generative art"
- Clean URLs throughout (directory-style, no index.html in links)
- Landing page: add convenience shorthand example showing both full and shorthand syntax
- Use color names (`[:color/name "red"]`) instead of RGB in early Guide examples for friendlier onboarding
- Add rendered visuals to Colors, Strokes, Clipping, Compositing, Transforms, Noise, Particles, Animation, and Easing sections
- Add category intros for Drawing, Styling, Composition, Animation, and Generative
- Add 26 gallery examples showcasing generative features: circle packing (solid, in-path, subdivided), Mondrian grid, reaction-diffusion (coral, spots, mitosis, waves), boids (murmuration, trails, predator/prey), dashed flow, series preview, voronoi glass, trembling grid, painted flow, CA quilt, stippled sphere, contour elevation, dot cloud, dashed L-system, hatched subdivision, morphing waves, packed typography, depth gradient, terrain stripes, edition gallery
- Tag all 148 gallery examples with feature tags for filter bar (noise, flow-field, particles, circle-packing, boids, 3d, animation, palette, etc.)
- Add "How Eido Works" architecture page at `/architecture/` — step-by-step rendering pipeline walkthrough with pipeline diagram, rendered examples, and GitHub source links
- Add "How It Works" nav link and landing page hero link

## v1.0.0-beta1 — Bug Fixes & Stabilization

### Bug fixes

- Fix box-blur sliding window producing near-black output — accumulators were never initialized, causing catastrophic blur results
- Fix `path/decorated` nodes silently dropped during semantic compilation — `node->generator` read wrong key (`:decorator/path` instead of `:path/commands`)
- Fix scatter/decorator group transforms dropped during lowering — all scatter dots rendered at origin instead of their positions (root cause of broken starfield and chromatic-scatter gallery images)
- Fix generator nodes (flow-field, voronoi, scatter, etc.) ignoring base `:node/opacity`
- Fix `expand-flow-field` not applying base opacity or fill overrides to child paths
- Fix `lower-scene-node` passing raw scene transforms (`:transform/translate`) without compiling to concrete format (`:translate`)
- Fix `analogous` palette crashing with division by zero when n=1
- Fix `geometry-bounds` returning nil for `:arc` and `:line` geometries — broke hatch/stipple fills on these shapes
- Fix heightfield-mesh quad winding producing inverted (-Y) normals
- Fix heightfield-mesh division by zero with single-row or single-column grids
- Fix normal-map color mapping returning ~1.0 for all axis-aligned normals — X, Y, and Z faces all got the same palette color
- Fix missing `:arc` and `:line` support in fill lowering (`geometry->scene-node`, `scene-node->op`), transform path conversion, clip shape handling, and SVG clip export
- Fix arc clip shape using full ellipse instead of arc sector

### Performance

- Add `eido/*validate*` dynamic var — skip spec validation for ~3.4x faster REPL re-renders
- Dev helpers (`show`, `watch-file`, `watch-scene`) validate first render only, then skip on subsequent calls

### Docs

- Document REPL validation behavior and `*validate*` binding in docs site and `render` docstring

## v1.0.0-alpha10 — Namespace Restructuring

**Breaking change**: Major namespace reorganization to improve API discoverability and documentation structure as Eido matures toward a stable release. This is a one-time restructuring effort to establish clean, consistent namespace boundaries.

### 3D — `eido.scene3d` split into sub-modules

The monolithic `eido.scene3d` (2083 lines) is now split into 8 focused sub-namespaces. The `eido.scene3d` facade re-exports all public vars, so `(require '[eido.scene3d :as s3d])` continues to work unchanged.

- `eido.scene3d.camera` — projections & camera positioning
- `eido.scene3d.mesh` — primitive constructors, platonic solids, parametric generators
- `eido.scene3d.transform` — translate, rotate, scale, deformations, mirror
- `eido.scene3d.topology` — subdivision, auto-smooth, face adjacency
- `eido.scene3d.surface` — UV projection, coloring, painting, material maps
- `eido.scene3d.modeling` — extrude/inset/bevel faces, L-system mesh, instancing
- `eido.scene3d.render` — shading, NPR, render-mesh, depth-sort, convenience, text-3d
- `eido.scene3d.util` — shared helpers (make-face, mesh-bounds, etc.)

### Path operations — `eido.path.*`

Path manipulation functions grouped under `eido.path.*`:

- `eido.stroke` → `eido.path.stroke`
- `eido.distort` → `eido.path.distort`
- `eido.warp` → `eido.path.warp`
- `eido.morph` → `eido.path.morph`
- `eido.decorator` → `eido.path.decorate`

`eido.path` retains its boolean ops and re-exports all sub-module vars.

### Generative tools — `eido.gen.*`

Generative/procedural modules grouped under `eido.gen.*`:

- `eido.noise` → `eido.gen.noise`
- `eido.flow` → `eido.gen.flow`
- `eido.contour` → `eido.gen.contour`
- `eido.scatter` → `eido.gen.scatter`
- `eido.voronoi` → `eido.gen.voronoi`
- `eido.lsystem` → `eido.gen.lsystem`
- `eido.particle` → `eido.gen.particle`
- `eido.stipple` → `eido.gen.stipple`
- `eido.hatch` → `eido.gen.hatch`
- `eido.vary` → `eido.gen.vary`

`eido.gen` facade re-exports all sub-module vars.

### Color — `eido.color.*`

- `eido.palette` → `eido.color.palette`

### Math & I/O

- `eido.math3d` → `eido.math`
- `eido.obj` → `eido.io.obj`

### Engine internals — `eido.engine.*`

Internal pipeline namespaces moved under `eido.engine.*`:

- `eido.compile` → `eido.engine.compile`
- `eido.render` → `eido.engine.render`
- `eido.svg` → `eido.engine.svg`
- `eido.gif` → `eido.engine.gif`

### Migration guide

Update your `require` forms to use the new namespace paths:

- **Facades** (`eido.scene3d`, `eido.path`, `eido.gen`) re-export everything, so `[eido.scene3d :as s3d]` and `[eido.path :as path]` work without changes.
- **Direct imports** need updating: e.g., `[eido.noise :as noise]` → `[eido.gen.noise :as noise]`, `[eido.palette :as palette]` → `[eido.color.palette :as palette]`.
- **Math**: `[eido.math3d :as m]` → `[eido.math :as m]`.
- **OBJ**: `[eido.obj :as obj]` → `[eido.io.obj :as obj]`.
- **Engine internals** (`eido.compile`, `eido.render`, `eido.svg`, `eido.gif`) are now under `eido.engine.*`. These are not part of the public API — if you were importing them directly, update accordingly.

## v1.0.0-alpha9 — 3D Sculpting Pipeline & 2D↔3D Bridge

### 3D mesh operations

- Add composable mesh→mesh sculpting vocabulary: every operation takes a mesh and returns a mesh, all composable via `->` threading
- Add `deform-mesh` with six deformation types: twist, taper, bend, inflate, crumple (seeded noise), displace (field-driven)
- Add `extrude-faces` and `inset-faces` with declarative face selection by normal, field, axis, or all
- Add `subdivide` with Catmull-Clark subdivision, hard edge support via `:hard-edges` option
- Add `auto-smooth-edges` for detecting hard edges by angle threshold
- Add `mirror-mesh` for reflecting meshes across axis planes with optional merge
- Add `bevel-faces` convenience helper (composes inset + extrude)
- Add `detail-faces` convenience helper (noise-driven inset + per-face extrude for procedural surface detail)

### New shape generators

- Add `platonic-mesh` with keyword dispatch for tetrahedron, octahedron, dodecahedron, icosahedron
- Add `heightfield-mesh` for terrain generation from 2D noise fields
- Add `revolve-mesh` for spinning 2D profiles into 3D forms (vases, goblets, columns)
- Add `sweep-mesh` for extruding 2D profiles along 3D paths using Frenet frames (tubes, tentacles, pipes)
- Add `lsystem-mesh` for 3D L-system branching structures via 3D turtle interpreter + sweep (trees, coral, vascular networks)
- Add `instance-mesh` for scatter-based mesh placement with optional jitter and rotation

### Vertex color and smooth shading

- Add `paint-mesh` for per-vertex color from fields sampled at 3D positions or UV coordinates (`:color/source :uv`)
- Add `color-mesh` for per-face color from fields, axis gradients, or normal-map direction
- Both support `:select/*` face selectors for partial coloring
- Add `:shading :smooth` option to `render-mesh` — vertex normal averaging from face adjacency
- Fan-triangulation in `render-mesh` for smooth vertex color interpolation within faces

### Procedural texturing (2D↔3D bridge)

- Add `uv-project` with four UV projection methods: box, spherical, cylindrical, planar
- Extend `paint-mesh` with `:color/source :uv` — samples 2D fields at vertex UV coordinates
- Add `normal-map-mesh` for perturbing vertex normals from UV-sampled field gradients (TBN frame computation)
- Add `specular-map-mesh` for varying specular intensity per vertex from UV-sampled fields
- Add `face-tangent-bitangent` to `eido.math3d` for TBN matrix construction
- The same field descriptor (`field/noise-field`) works as 2D fill, 3D deformation, face selection, vertex color, texture, bump map, and specular map

### Non-photorealistic rendering

- Add `:render/mode :hatch` for cross-hatched 3D face rendering with lighting-driven density
- Add `:render/mode :stipple` for stipple-dot 3D face rendering
- Hatch lines clipped to face polygon boundaries via line-polygon intersection
- Stipple dots filtered by point-in-polygon test
- Bridges 2D hatch/stipple pattern systems to 3D surfaces

### OBJ import/export

- Add `write-obj` and `write-mtl` for Wavefront OBJ export with deduplicated vertices/normals
- Add UV texture coordinate import (`vt` lines) with `:face/texture-coords` per face
- Add UV texture coordinate export (vi/ti/ni face ref format)
- Add group (`g`) and smooth group (`s`) parsing → `:face/group`, `:face/smooth-group`
- OBJ roundtrip preserves geometry, normals, UVs, and materials

### Shared infrastructure

- Add `field/evaluate-3d` for 3D noise field evaluation using `perlin3d`
- Add `m/lerp` for 3D vector linear interpolation
- Extract `build-face-adjacency` and `compute-vertex-normals` as shared helpers for subdivision, smooth shading, and auto-smooth
- Face selectors as reusable data maps (`:select/*` namespace keys) composable across extrude, inset, color, and paint operations

### Gallery

- Add 17 new gallery examples: organic sculpture, alien landscape, coral growth, twisted vase, crystal cluster, geometric panels, geodesic sphere, mirrored sculpture, smooth geodesic, sweep tube, auto-smooth cube, detailed panel, vertex painted sphere, procedural textured sphere, scatter forest, hatched sphere, L-system tree

## v1.0.0-alpha8 — Semantic IR & Procedural Fills

### Semantic IR

- Add semantic IR layer to `eido.ir` with containers, draw items, and geometry constructors
- Preserve hatch fills, stipple fills, and effects as semantic data through the pipeline instead of expanding to geometry immediately
- Add `eido.ir.lower` to convert semantic IR to concrete ops consumed by existing renderers
- Add `compile-semantic` in `eido.compile` for building semantic IR containers
- Route `compile/compile` through semantic IR layer (compile-semantic → ir.lower/lower)

### Procedural fills

- Add `:fill/procedural` fill type — per-pixel program evaluation producing image-based fills
- Programs are pure data: `[:color/rgb [:* 255 [:field/noise {...} :uv]] 100 50]`
- Add `eido.ir.program` — minimal expression evaluator supporting arithmetic, math, vectors, mix/clamp/select, field sampling, color construction
- Add `eido.ir.field` — field descriptors for noise (Perlin raw/fbm/turbulence/ridge), constant, and distance fields
- Add `eido.ir.fill` — fill constructors (solid, gradient, hatch, stipple, procedural) and lowering
- Add `eido.ir.effect` — effect descriptors (shadow, glow) and lowering to BufferOp wrappers
- Add `:procedural-image` fill type to renderer for direct image-based fills

### Transforms

- Add `eido.ir.transform` with semantic descriptors for distort (noise/wave/roughen/jitter), warp (wave/twist/fisheye/bulge/bend), and morph (path interpolation)
- Transforms stored as `:item/pre-transforms` on draw items, applied to geometry before lowering

### Generators

- Add `eido.ir.generator` with descriptors for flow-field, contour, scatter, voronoi, delaunay, decorator, and particle generators
- Generators use `:item/generator` instead of `:item/geometry` on draw items
- Each generator wraps existing feature module functions for lowering

### Vary

- Add `eido.ir.vary` with override descriptors (by-index, by-position, by-noise, by-gradient)
- Generators accept vary descriptors in `:generator/overrides`

### 3D materials and lighting

- Add `eido.ir.material` with Blinn-Phong material descriptors (ambient, diffuse, specular, shininess)
- Add four light types: directional, point (omni), spot, hemisphere (sky)
- Light constructors: `material/directional`, `material/omni`, `material/spot`, `material/hemisphere`
- Multi-light support: `:lights` vector on render-mesh opts, contributions sum per-channel
- Light color tinting: each light's color modulates its diffuse and specular contribution
- Distance decay: `:none`, `:inverse`, `:inverse-square` with configurable `:light/decay-start`
- Spot light cone: `:light/hotspot` (inner angle) and `:light/falloff` (outer angle) with smoothstep
- Hemisphere light: sky/ground color blended by surface normal direction
- `smoothstep` added to `eido.math3d`
- Extend `scene3d/shade-face-style` to support materials and multi-light
- Backward compatible — existing scenes without `:material` or `:lights` unchanged

### Multi-pass rendering

- Add `pipeline` constructor for multi-pass IR containers
- Add `effect-pass` and `program-pass` pass constructors
- Effect passes wrap preceding ops in BufferOp with filter
- Support chaining draw → effect passes (grain, blur, posterize, etc.)

### Semantic pipeline

- Extend `normalize-node` to preserve generators and effects as semantic data
- Extend `scene-node->draw-item` to produce generator items for generator node types
- All scene node types now route through the semantic IR path

### Domains and resources

- Add `eido.ir.domain` with domain descriptors (image-grid, shape-local, world-2d, path-param, mesh-faces, points, particles, timeline)
- Add `eido.ir.resource` with named resource declarations (image, mask, geometry, points, field, particle-state, parameter-block)
- Programs with `:program/domain` validate evaluation environment bindings
- Pipeline resource validation via `validate-pipeline-resources`

### Internal refactoring

- IR modules (`ir.fill`, `ir.effect`, `ir.generator`) are self-sufficient with no `compile.clj` dependency
- Delete 315 lines of legacy compile machinery (`compile-tree`, `compile-node`, `compile-style`, `expand-hatch-fill`, `expand-stipple-fill`, `make-shadow-node`, `make-glow-node`, `expand-effects`, and related helpers)
- Implement group context handling (style inheritance, opacity multiplication, transform accumulation) natively in `ir.lower`
- Unify `compile-command` and `geometry-bounds` as shared utilities in `eido.ir`

### API

- New namespace group: `eido.ir`, `eido.ir.domain`, `eido.ir.resource`, `eido.ir.fill`, `eido.ir.effect`, `eido.ir.field`, `eido.ir.program`, `eido.ir.lower`, `eido.ir.transform`, `eido.ir.generator`, `eido.ir.vary`, `eido.ir.material`

## v1.0.0-alpha7 — Docs & Validation

### Website

- Add rendered preview images below 22 code examples on the docs page
- Preview images render on the fly during CI/CD build (not committed to repo)
- Add grouped sidebar categories to API reference page (Core, Drawing, Styling, Effects, Generative, Animation, 3D)

### Validation

- Add `format-errors` and `explain` for readable validation output
- Improve unknown node type error messages with list of valid types
- Deduplicate color and fill/stroke validation errors
- Add default clauses to case dispatch in compile/render/svg for clear error messages

### Fixes

- Fix opacity tracking leak across buffer boundaries
- Remove committed image files from repo (rendered by CI/CD)

## v1.0.0-alpha6 — Performance

### Rendering pipeline

- Add `make-projector` for precomputed 3D→2D projection with inlined rotation matrix
- Replace `project`, `compile-node`, `render-op`, `op->svg` multimethods with `case` dispatch
- Eliminate redundant `normalize` calls in `render-mesh` and `shade-face-style`
- Pre-normalize light direction once per render instead of per face
- Add offscreen buffer pooling for shadow/glow compositing groups
- Add `BasicStroke` caching to avoid repeated allocation in stroke-heavy scenes
- Skip redundant `Graphics2D` state changes (opacity, transform save/restore)
- Convert IR ops from maps to records for O(1) field access
- Move spec validation from `compile` to API boundary (validate once, compile fast)
- Support `:eido/validate false` on scene maps to skip validation for known-good scenes
- Skip validation for animation frames after the first

### Pixel operations

- Enable `unchecked-math` for pixel processing functions (blur, grain, blend, halftone)
- Eliminate per-pixel vector allocation in box-blur inner loop
- Reuse `Ellipse2D` object in halftone rendering loop
- Memoize pattern tile rendering by spec

### Noise and contour

- Cache seeded permutation tables instead of regenerating per noise call
- Convert permutation table and gradient vectors to primitive int-arrays
- Use `rem` instead of `mod` for primitive long division in noise
- Replace O(n²) contour segment connection with O(n) spatial hashing
- Convert contour grid sampling to flat `double-array` with `aget` access

### Stroke and text

- Deduplicate normal computation in stroke outline (compute once, use for both sides)
- Convert cumulative distances to `double-array`
- Remove redundant `ensure-double-coords` pass before path flattening
- Fix reflection in `flatten-commands` (type hints for `GeneralPath`, `PathIterator`)
- Eliminate `[cmd & args]` seq allocation in `compile-command`, `build-path`, `flatten-commands`

### Other

- Use `ArrayList` with swap-remove for O(1) Poisson disk active list removal
- Fix reflection warnings in `render.clj` and `stipple.clj`
- Replace SVG `fmt` regex with manual character trimming
- Destructure node keys once in `compile-tree` to reduce map lookups

### Infrastructure

- Add visual regression tests (`test/eido/visual_test.clj`) with pixel-diff against committed reference PNGs
- Add CI workflow (`.github/workflows/test.yml`) for tests on PRs and pushes
- Add REPL benchmarks (`dev/bench.clj`) with full gallery timing suite
- Add `clj-async-profiler` as dev dependency with profiling helpers (`dev/profile.clj`)
- Add `:perf` deps.edn alias with JVM tuning flags for throughput rendering

## v1.0.0-alpha5 — Artistic Toolkit, Gallery Website

### Artistic toolkit

- Add `eido.noise` namespace: Perlin noise 2D/3D, fractal brownian motion, turbulence, ridge noise
- Add `eido.palette` namespace: curated palettes, gradient-map, palette generation
- Add `eido.stroke` namespace: variable-width strokes with calligraphic profiles (brush, pointed)
- Add `eido.hatch` namespace: hatching and cross-hatching fill styles
- Add `eido.stipple` namespace: stippling fill style with Poisson disk sampling
- Add `eido.scatter` namespace: scatter/instancing with Poisson disk, grid, noise field distributions
- Add `eido.distort` namespace: path distortion transforms (noise, wave, roughen, jitter)
- Add `eido.decorator` namespace: path decorators for placing shapes along paths
- Add tiled pattern fills via `:fill/type :pattern`
- Add drop shadow and glow effects via `:effect/shadow` and `:effect/glow`

### Compositional features

- Add `eido.contour` namespace: contour line generation via marching squares
- Add `eido.flow` namespace: flow field streamline generation from noise vector fields
- Add `eido.voronoi` namespace: Voronoi tessellation and Delaunay triangulation
- Add `eido.lsystem` namespace: L-system grammar-based shape generation
- Add `eido.morph` namespace: shape morphing and path resampling
- Add `eido.warp` namespace: envelope warp for group-level coordinate transforms (wave, twist, bend)
- Add `eido.vary` namespace: per-instance variation for scatter, symmetry, voronoi, flow, decorator
- Add `eido.path` namespace: path boolean operations (union, intersection, difference, xor)
- Add symmetry systems via `:symmetry/type` (radial, bilateral, grid)
- Add gradient-map for continuous scalar-to-color mapping
- Add post-processing filters: grain, posterize, duotone, halftone

### Typography

- Add `eido.text` namespace: text-to-path conversion, font resolution, glyph extraction
- Add `:shape/text`, `:shape/text-glyphs`, `:shape/text-on-path` node types
- Add `text-outline` and `text-clip` convenience functions in `eido.scene`
- Add 3D extruded text via `eido.scene3d`
- Add per-glyph styling for creative typography

### Gallery website

- Add static site generator using Hiccup + Garden (`:gallery` deps.edn alias)
- Add gallery website at eido.leifericf.com with landing page, gallery, docs, and API reference
- Move 62 examples from README/dev into `examples/gallery/` as pure functions with `:example` metadata
- Add GitHub Actions workflow for artifact-based Pages deployment
- Add image lightbox, source code lightbox with syntax highlighting, copy-to-clipboard
- Add categorized docs page with rich beginner-friendly guides
- Add auto-generated API reference from source metadata
- Slim README from 2,657 to 84 lines
- Remove 80 images from repo (rendered by CI, served via GitHub Pages)

## v1.0.0-alpha4 — Particle Simulation

- Add `eido.particle` namespace: deterministic physics-based particle simulation
  - Emitter configuration: position, rate, spread, lifetime, colors, sizes
  - Force system: gravity, wind, turbulence, drag
  - Built-in presets: fire, snow, sparks, confetti, smoke, fountain
- Add 3D particle support with depth sorting
- Add Particle Gallery with campfire, fireworks, snowfall, fountain, volcano examples
- Add 2D/3D mixed gallery with three animated examples

## v1.0.0-alpha3 — 3D Rendering, Compositing & Blend Modes

### 3D rendering

- Add `eido.math3d` namespace: 3D vector math, rotations, and projection functions
- Add `eido.scene3d` namespace: 3D shape helpers projected into 2D scene nodes
  - Projection types: isometric, orthographic, perspective (with roll support)
  - Mesh constructors: `cube-mesh`, `prism-mesh`, `cylinder-mesh`, `sphere-mesh`, `torus-mesh`, `cone-mesh`, `extrude-mesh`
  - Mesh transforms: `translate-mesh`, `rotate-mesh`, `scale-mesh`
  - Mesh utilities: `merge-meshes`, `mesh-bounds`, `mesh-center`
  - Camera utilities: `look-at`, `orbit`, `fov->distance`
  - Rendering: `render-mesh` with back-face culling, depth sorting, diffuse shading, and wireframe mode
  - Convenience functions: `cube`, `prism`, `cylinder`, `sphere`, `torus`, `cone`
- Add `eido.obj` namespace: Wavefront OBJ/MTL parser (`parse-obj`, `parse-mtl`)
- Fix anti-aliasing gaps between adjacent 3D faces via polygon expansion

### Compositing, filters & blend modes

- Add group compositing via `:group/composite` (Porter-Duff rules: `:src-over`, `:src-in`, `:src-out`, `:dst-over`, `:xor`)
- Add blend modes: `:multiply`, `:screen`, `:overlay` via `:group/composite`
- Add color filters via `:group/filter`: `:grayscale`, `:sepia`, `:invert`
- Add Gaussian blur filter via `[:blur radius]`

## v1.0.0-alpha2 — Colors, Gradients & Easings

- Add `:color/name` with all 148 CSS Color Level 4 named colors (case-insensitive)
- Add `eido.scene/regular-polygon` for creating n-sided regular polygons
- Add `eido.scene/star` for creating n-pointed stars with outer/inner radii
- Add extended easing functions: cubic, quart, expo, circ, back, elastic, bounce (in/out/in-out variants for each)
- Add linear and radial gradient fills via `:style/fill` gradient maps
  - `:gradient/type :linear` with `:gradient/from`, `:gradient/to`, `:gradient/stops`
  - `:gradient/type :radial` with `:gradient/center`, `:gradient/radius`, `:gradient/stops`
  - Both Java2D and SVG backends supported

## v1.0.0-alpha1

- Add installation instructions to README (git dependency via deps.edn)

## v0.12.0 — Shapes & Styles

- Add `:shape/ellipse` primitive with independent x/y radii (`:ellipse/center`, `:ellipse/rx`, `:ellipse/ry`)
- Add `:shape/arc` primitive for partial ellipses (`:arc/start`, `:arc/extent`, `:arc/mode` — `:open`/`:chord`/`:pie`)
- Add `:shape/line` primitive for direct line segments (`:line/from`, `:line/to`)
- Add `:rect/corner-radius` for rounded rectangles
- Add `:quad-to` path command for quadratic bezier curves
- Add `:path/fill-rule` (`:even-odd`/`:non-zero`) for path contours and holes
- Add stroke `:cap` (`:butt`/`:round`/`:square`), `:join` (`:miter`/`:round`/`:bevel`), and `:dash` pattern support
- Add `:transform/shear-x` and `:transform/shear-y` for skew transforms
- Add `:color/hsb` and `:color/hsba` color spaces (hue/saturation/brightness)
- Add `:group/clip` for masking groups to a shape (rect, circle, ellipse, or path)
- Add `:antialias` render option for deliberately aliased output
- Add `eido.scene/polygon`, `eido.scene/triangle`, and `eido.scene/smooth-path` helpers
- Fix SVG renderer dropping color alpha channel — `rgba()` now emitted when alpha < 1.0
- Fix animated SVG SMIL timing so frames actually alternate instead of all appearing simultaneously

## v0.11.0 — Frames

- Add `eido.animate/frames` higher-order function for building frame sequences without boilerplate
- Update all README animation examples to use `anim/frames`
- Add inline example images throughout README
- Add gallery with advanced animated grid patterns (spiral, sine field, breathing wave)
- Add dancing bars and tentacles animations to gallery
- Add 7 advanced gallery examples (galaxy, pendulum wave, op art, Lissajous, cellular automaton, kaleidoscope, tree)
- Add fractal examples (blooming tree, Sierpinski triangle, Koch snowflake)

## v0.10.0 — Polish

- Move `->awt-color` to render namespace as private function (no longer public API)
- Add optional `:eido/version` key to scene spec (validates format `"X.Y"` when present)
- Add friendly error message for invalid version strings
- Expand GIF test coverage (no-loop, many frames, short delay, return value)
- Add edge case tests for single-frame animations and version key roundtrip
- Add REPL comment blocks to `spec.clj`, `validate.clj`, and `svg.clj`
- Update v1.0 spec document with full language coverage (all color formats, animation, export, API)
- Add `eido.color/rgb->hsl` to README API table
- Document animation options (`:loop`, `:prefix`) in README examples
- Update roadmap with phase completion status

## v0.9.0 — SVG

- Add `:scale` option to SVG rendering (multiplies width/height, preserves viewBox)
- Pass `:scale` through `render-to-svg` and `render-to-file` for SVG output
- Add animated SVG rendering with SMIL visibility toggling (`eido.svg/render-animated`)
- Add `eido.core/render-to-animated-svg` for exporting scene sequences as animated SVG files
- Add `eido.core/render-to-animated-svg-str` for getting animated SVG as a string

## v0.8.0 — Animation

- Add `eido.animate` namespace with temporal helpers: `progress`, `ping-pong`, `cycle-n`, `lerp`, `ease-in`, `ease-out`, `ease-in-out`, `stagger`
- Add `eido.core/render-animation` for exporting scene sequences to numbered PNG files
- Add `eido.gif` namespace for animated GIF encoding via Java ImageIO (no external dependencies)
- Add `eido.core/render-to-gif` for exporting scene sequences as animated GIFs
- Add `play` and `stop` functions in dev/user.clj for REPL animation preview

## v0.7.0 — Validation

- Add `eido.spec` namespace with clojure.spec definitions for the full scene structure
- Add specs for primitives, colors (RGB, RGBA, HSL, HSLA, hex), transforms, path commands, styles, nodes, and scenes
- Add recursive group validation via multi-spec
- Add `eido.validate` namespace with human-readable error translation
- Add `eido.core/validate` for checking scenes without rendering
- Integrate validation into `eido.core/render` (throws `ex-info` with `:errors` on invalid input)

## v0.6.0 — Export

- Add SVG backend (`eido.svg`) for pure IR-to-string vector rendering
- Add `eido.core/render-to-svg` for getting SVG strings directly
- Add multi-format raster export: JPEG (with quality), GIF, BMP
- Add render options: `:scale` for resolution multiplier, `:transparent-background`
- Add PNG DPI metadata support via `:dpi` option
- Add SVG transparency support
- Add `eido.core/render-batch` for rendering multiple scenes to files

## v0.5.0 — Color

- Add HSL and HSLA color format support
- Add hex color parsing (3, 4, 6, and 8-digit formats)
- Add RGB to HSL conversion (`eido.color/rgb->hsl`)
- Add color manipulation helpers: `lighten`, `darken`, `saturate`, `desaturate`, `rotate-hue`, `lerp`

## v0.4.0 — Workflow

- Add `eido.scene/grid` for generating nodes in a grid pattern
- Add `eido.scene/distribute` for distributing nodes along a line
- Add `eido.scene/radial` for distributing nodes around a circle
- Add `eido.core/read-scene` and `eido.core/render-file` for EDN file workflow
- Add file watching with auto-reload preview (`user/watch-file`)
- Add atom watching for live coding (`user/watch-scene`)
- Add `tap>` integration for rendering tapped scenes (`user/install-tap!`)

## v0.3.0 — Paths

- Add `:shape/path` node type with arbitrary shapes via path commands
- Path commands: `move-to`, `line-to`, `curve-to` (cubic bezier), `close`
- Add path compilation to IR
- Add path rendering via Java2D `GeneralPath`

## v0.2.0 — Composition

- Add `:group` node type for composing shapes
- Add style inheritance from parent groups to child nodes
- Add multiplicative opacity inheritance through the node tree
- Add transform accumulation (translate, rotate, scale) through groups
- Add transform application in the Java2D renderer

## v0.1.0 — Basic shapes

- Initial release
- Add `eido.core` with `render`, `render-to-file`
- Add `eido.compile` for scene-to-IR compilation with style resolution
- Add `eido.render` for Java2D raster rendering
- Add `eido.color` with `resolve-color` for RGB/RGBA color vectors
- Add `:shape/rect` and `:shape/circle` primitives
- Add `dev/user.clj` with `show` for REPL preview
