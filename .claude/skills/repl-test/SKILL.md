---
name: repl-test
description: Run exploratory REPL-driven testing across all Eido user-facing functions with parametric edge cases, generative stress tests, and rendering pipeline verification.
user-invocable: true
allowed-tools: Bash Read Grep Glob mcp__noumenon__*
argument-hint: [focus-area]
---

# Exploratory REPL-Driven Testing

Find bugs and fix them. This skill runs comprehensive testing of Eido via the REPL, finds issues that unit tests miss, and **fixes every issue it finds** — one commit per fix.

## Core Loop

Repeat until no more issues are found:

1. **Test** — run the next layer of testing (see below)
2. **Find** — when a test fails, diagnose the root cause by reading the source
3. **Fix** — apply the fix in the source file
4. **Verify** — run `clj -M:test` to confirm no regressions
5. **Commit** — `git add <file> && git commit -m "Fix ..."` with a descriptive message
6. **Continue** — move to the next test

Do NOT batch fixes. Do NOT just report issues. Fix each one as you find it, verify, commit, then keep going. The goal is zero issues remaining when you're done.

## Noumenon MCP — Query Before Reading

**Always query Noumenon before reading source files.** A PreToolUse hook enforces this — file-reading tools are blocked until a Noumenon MCP query has been made.

1. Call `noumenon_status` with `repo_path: "eido"` to check the graph is populated.
2. Use `noumenon_query` or `noumenon_ask` to find files, dependencies, complexity hotspots, or code smells before reading.
3. Then read specific files for implementation details.

Pass `"eido"` as `repo_path`, not a filesystem path.

Useful queries for this skill:
- `smells-by-type` — find code smells to investigate
- `complex-hotspots` — high-churn + high-complexity files
- `uncalled-segments` — potentially dead code
- `segments-with-safety-concerns` — safety flags to verify
- `file-segment-issues` with `file-path` — smells in a specific file
- `bug-hotspots` — files with most fix commits (likely to have more bugs)

## Setup

1. Call `noumenon_status` to verify the knowledge graph is current.
2. Check for a running nREPL by reading `.nrepl-port`. If none, start one with `clj -M:dev`.
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

When a test fails:
1. Read the source file at the failing location
2. Diagnose: is it a missing guard, wrong parameter order, missing export, etc.?
3. Fix the source
4. Run `clj -M:test` — must pass with same or higher assertion count
5. Commit: `git add <file> && git commit -m "Fix <description>"`
6. Re-run the failing test to confirm it passes
7. Continue testing

At the end, summarize: total tests run, issues found and fixed (with commit SHAs), remaining areas of concern.

## Common Fix Patterns

These are the bug classes found in previous runs — check for them specifically:

- **Division by zero**: `(int (Math/ceil (/ x 0)))` → add `(pos? divisor)` guard, return `[]`
- **Missing facade export**: new public fn in sub-ns not in facade → add `(import-fn ns/fn-name)`
- **API inconsistency**: positional args that should be in opts map → move to opts, update callers
- **Parameter order**: doesn't match `[content origin font-spec ...]` or `[projection position opts]` convention → swap args, update callers
- **Empty collection crash**: `(.nextInt rng (count []))` → add `(pos? n)` guard, return `nil`
- **Empty palette mod-zero**: `(mod i (count []))` in palette cycling → add `(zero? pn)` guard before mod
- **Nil function call**: optional function params called without nil check → add `(or f default-fn)` fallback
- **IR constructor integration**: after adding new constructors to `ir.clj`, verify `compile` + `render` still produce correct op types for all shape types
- **Nil first-element destructuring**: `(:key (first []))` → nil, then destructuring nil causes NPE. Guard with `(empty? coll)` check. Found in animated SVG with 0 frames.
- **Missing geometry type in context**: when a new shape type is added, test it works as a clip mask, with transforms, with effects, and in SVG output — not just as a plain fill.
- **Style override dropping**: scatter/decorator/generator nodes can lose `:node/opacity` or `:node/transform` during IR lowering. Test by rendering with opacity < 1 and verifying pixel values.

## Git History Analysis

Run `git log --all --grep='[Ff]ix' --format='%s'` periodically to mine historical bug patterns. The 100+ fixes in Eido's history cluster into:

1. **Division-by-zero** (8 fixes): `Math/ceil`, `mod`, palette interpolation with n=1
2. **API migration drift** (12 fixes): old positional args surviving refactors, callers not updated
3. **Missing geometry support** (4 fixes): new shapes not wired into fill/clip/transform/SVG paths
4. **3D normal direction** (5 fixes): inverted normals on caps, side faces, torus
5. **Rendering state leaks** (3 fixes): opacity, buffer boundaries, composite modes
6. **SVG output correctness** (4 fixes): alpha channel dropped, NaN coordinates, SMIL timing
