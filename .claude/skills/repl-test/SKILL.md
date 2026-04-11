---
name: repl-test
description: Run exploratory REPL-driven testing across all Eido user-facing functions with parametric edge cases, generative stress tests, and rendering pipeline verification.
user-invocable: true
allowed-tools: Bash Read Grep Glob
argument-hint: [focus-area]
---

# Exploratory REPL-Driven Testing

Run comprehensive, generative, parametric edge-case testing of Eido via the REPL. This is a manual QA sweep — not a substitute for the test suite, but a complement that catches issues unit tests miss (zero-division crashes, API inconsistencies, rendering artifacts).

## Setup

1. Check for a running nREPL by reading `.nrepl-port`. If none, start one with `clj -M:dev`.
2. Use the nREPL eval helper at `/tmp/nrepl-eval.clj` if it exists, or create it:

```clojure
(require '[nrepl.core :as nrepl])
(let [port (parse-long (slurp "/Users/leif/Code/eido/.nrepl-port"))
      code (slurp *in*)]
  (with-open [conn (nrepl/connect :port port)]
    (let [client (nrepl/client conn 60000)
          msgs   (doall (nrepl/message client {:op "eval" :code code}))
          out    (apply str (keep :out msgs))
          err    (apply str (keep :err msgs))
          vals   (nrepl/response-values msgs)
          ex     (some :ex msgs)]
      (when (seq out) (print out))
      (when (seq err) (binding [*out* *err*] (print err)))
      (when ex (println "EXCEPTION:" ex))
      (doseq [v vals] (println v)))))
```

3. For tests that need a clean JVM (no stale REPL state), use `clojure -M:dev -e '...'` directly. Prefer this for large test batches — REPL state pollution from `:reload-all` can cause false failures.

## Focus Areas

The optional `$ARGUMENTS` narrows the scope. If empty, run all areas. Valid focus areas:

- `gen` — generative algorithms (noise, voronoi, circle-pack, scatter, flow, contour, lsystem, boids, subdivide, CA, hatch, stipple, particles, series, prob)
- `path` — path operations (boolean ops, morph, warp, distort, dash, smooth, outline, decorate)
- `render` — rendering pipeline (all shape types, fills, strokes, gradients, effects, clips, opacity, transforms)
- `3d` — 3D pipeline (meshes, cameras, transforms, topology, surface, convenience functions, full render)
- `text` — text functions (text, text-glyphs, text-on-path, text-stack, text-outline, text-clip)
- `format` — output formats (PNG, JPEG, TIFF, BMP, SVG, GIF, polylines, animated SVG)
- `api` — API consistency (parameter orders, opts maps, return types, facade completeness)
- `edge` — edge cases specifically (zero bounds, empty collections, nil values, extreme parameters)
- `property` — property-based testing (invariants over random inputs)
- `fuzz` — random scene fuzzing (robustness under random valid input)
- `pixel` — pixel sampling (verify rendered output correctness)
- `roundtrip` — round-trip testing (OBJ, polyline, manifest write→read)

## Testing Strategies

Run these in order — each layer builds on the previous:

### Layer 1: Mechanical Checks (fast, high signal)

#### Facade completeness
Walk every public var in sub-namespaces and verify the facade re-exports it:

```clojure
(defn check-facade [facade-ns sub-namespaces]
  (let [facade-vars (set (keys (ns-publics (find-ns facade-ns))))
        sub-vars    (reduce #(into %1 (keys (ns-publics (find-ns %2)))) #{} sub-namespaces)
        missing     (clojure.set/difference sub-vars facade-vars)]
    (when (seq missing) (println "MISSING:" (sort missing)))))
```

Check: `eido.gen`, `eido.path`, `eido.scene3d`. New sub-namespace functions are easy to forget.

#### Docstring/arity verification
For every public fn, check `:doc` exists and `:arglists` metadata is present.

### Layer 2: Parametric Edge Cases

#### Division-by-zero sweep
A recurring bug class: `(int (Math/ceil (/ x divisor)))` where `divisor` can be zero.

Search pattern: `Math/ceil (/ ` in `src/eido/gen/`

For EVERY function that takes spacing, density, resolution, or min-dist:
- Pass `0` — should return `[]`, not crash
- Pass negative — should return `[]` or throw descriptive error

Known sites (all should be guarded):
- `circle-pack` → bounds width/height
- `poisson-disk` → `:min-dist`
- `hatch-lines` → `:spacing`
- `contour-lines` → `:resolution`, bounds
- `flow-field` → `:density`, bounds

#### Boundary values
For every numeric parameter with a documented range, test BOTH boundaries:
- Opacity: 0.0 and 1.0
- RGB: 0 and 255
- HSL hue: 0 and 360; s/l: 0.0 and 1.0
- Gradient stops: exactly 0.0 and 1.0
- Arc extent: 0, 360, negative, >360
- Stroke width: 0.1, 100
- Scale: 0.0, -1, 100
- Corner radius: 0, larger than rect
- Image size: 1x1

### Layer 3: Property-Based Testing

Test invariants over many random inputs (100+ iterations each):

```clojure
;; P1: render always returns BufferedImage of [w h]
;; P2: circle-pack circles are within bounds
;; P3: perlin2d always in [-1, 1]
;; P4: voronoi cell count = input point count
;; P5: poisson-disk respects minimum distance
;; P6: flow-field returns :shape/path nodes
;; P7: color/resolve-color always has :r :g :b :a
;; P8: path/union is commutative (same command count)
;; P9: SVG output is always parseable XML
```

Use `java.util.Random` with a fixed seed for reproducibility.

### Layer 4: Random Scene Fuzzing

Generate 200+ random valid scenes with:
- Random shape types (rect, circle, ellipse, line, path)
- Random colors, random sizes, random positions
- Random nesting depth (1-5 levels of groups)
- Random transforms (translate, rotate, scale, shear — stacked)
- Random effects (shadow, glow, blur)

Verify: `eido/render` either succeeds or throws `ExceptionInfo` with `:errors`. Never NPE, ClassCastException, or StackOverflow.

```clojure
(binding [eido/*validate* false]  ;; test engine robustness, not validation
  (eido/render random-scene))
```

### Layer 5: Output Verification

#### Pixel sampling
Render known scenes, sample specific pixels:
- Red circle at center → pixel at center is red
- Left-half blue rect → left pixel blue, right pixel white
- Gradient → left red, right blue, middle purple
- 50% opacity red on white → r~255, g~128, b~128
- Translate moves shape → pixel at old position is background
- Clip masks correctly → inside red, outside white

```clojure
(defn pixel-rgb [^BufferedImage img x y]
  (let [rgb (.getRGB img (int x) (int y))]
    {:r (bit-and (bit-shift-right rgb 16) 0xFF)
     :g (bit-and (bit-shift-right rgb 8) 0xFF)
     :b (bit-and rgb 0xFF)}))
```

#### SVG structural validation
Parse SVG as XML, verify:
- No `NaN` or `Infinity` in attribute values
- Element count matches node count (+1 for background rect)
- Valid XML (parse succeeds)
- Gradients produce `<linearGradient>`/`<radialGradient>` + `<stop>` elements
- Clips produce `<clipPath>` elements

```clojure
(defn parse-svg [s]
  (clojure.xml/parse (java.io.ByteArrayInputStream. (.getBytes s "UTF-8"))))
```

#### Cross-format consistency
Render same scene to PNG and SVG. Count SVG shapes, verify matches node count.

#### Determinism
Render same scene twice with same seed, compare byte-identical output:
```clojure
(defn render-bytes [scene]
  (let [img (eido/render scene)
        buf (int-array (* (.getWidth img) (.getHeight img)))]
    (.getRGB img 0 0 (.getWidth img) (.getHeight img) buf 0 (.getWidth img))
    (vec buf)))
(= (render-bytes scene) (render-bytes scene))
```

Test with: simple shapes, seeded generative (circle-pack, flow-field), effects (grain with seed), hatch fills.

### Layer 6: Round-Trip Testing

- **OBJ**: `write-obj` → `parse-obj` → verify face count matches
- **Polylines**: `render {:format :polylines}` → `polylines->edn` → `edn/read-string` → verify structure
- **Manifest**: `render {:emit-manifest? true}` → `slurp .edn` → verify `:scene`, `:seed`

### Layer 7: API Consistency

Eido's beta5 established these conventions. Verify they hold:

- **Text functions**: `[content origin/path font-spec ...]` parameter order
- **3D convenience functions**: `[projection position opts]` with geometry in opts
- **Generative functions with bounds**: `[bounds opts]` where bounds is `[x y w h]`
- **Mesh factories**: `sphere-mesh`, `cylinder-mesh` etc. use `[primary-arg opts]`
- **All opts maps**: seeds as `:seed` key, not positional

## Reporting

For each test, print a short status line:
```
--- [area]: [test name] ---
  OK                           ;; or
  Error: [class] [message]     ;; with enough context to reproduce
```

At the end, summarize:
- Total tests run
- Failures found (with file:line if identifiable)
- Suggested fixes

If a fix is obvious and safe (e.g., adding a `(pos? x)` guard), fix it immediately, run `clj -M:test`, and commit with a descriptive message. One commit per fix.
