---
name: repl-test
description: Run exploratory REPL-driven testing across all Eido user-facing functions with parametric edge cases, generative stress tests, and rendering pipeline verification.
user-invocable: true
allowed-tools: Bash Read Grep Glob mcp__noumenon__*
argument-hint: [focus-area]
---

# Exploratory REPL-Driven Testing

Find bugs the automated test suite misses, fix them, and add regression tests. This skill connects to the REPL and systematically probes for crashes, edge cases, and inconsistencies that static test suites can't cover.

## What the automated suite already covers (skip these)

These are handled by `clj -M:test` — do NOT duplicate them:
- **Facade completeness** → `test/eido/facade_test.clj`
- **Property-based invariants** (noise ranges, circle-pack bounds/overlap, poisson-disk distance, voronoi cell count, uniform range, shuffle preservation) → `test/eido/gen/property_test.clj`
- **Feature combination rendering** (gradient+clip, scatter+shadow, flow-field+opacity, etc.) → `test/eido/integration/feature_combo_test.clj`
- **Pixel sampling and determinism** (25 catalog scenes rendered twice, pixel-diffed) → `test/eido/integration/visual_regression_test.clj`
- **SVG structural validation** (gradients, clips in SVG output) → `test/eido/integration/feature_combo_test.clj`

## Core Loop

Repeat until no more issues are found:

1. **Test** — run the next layer of testing (see below)
2. **Find** — when a test fails, diagnose the root cause by reading the source
3. **Fix** — apply the fix in the source file
4. **Add regression test** — add an automated test (in the appropriate test file) so this bug stays caught by `clj -M:test`
5. **Verify** — run `clj -M:test` to confirm no regressions and the new test passes
6. **Commit** — `git add <files> && git commit -m "Fix ..."` with a descriptive message (include fix + test in same commit)
7. **Continue** — move to the next test

Do NOT batch fixes. Do NOT just report issues. Fix each one as you find it, add a regression test, verify, commit, then keep going.

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
2. **Run `clj -M:test` first.** If any automated tests fail, fix those before doing exploratory testing. The automated suite covers property-based invariants, feature combinations, visual regression, and facade completeness — there's no point exploring further if the foundation is broken.
3. Check for a running nREPL by reading `.nrepl-port`. If none, start one with `clj -M:dev`.
4. Use the nREPL eval helper at `/tmp/nrepl-eval.clj` if it exists, or create it:

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
- `api` — API consistency (parameter orders, opts maps, return types)
- `edge` — edge cases specifically (zero bounds, empty collections, nil values, extreme parameters)
- `fuzz` — random scene fuzzing (robustness under random valid input)
- `roundtrip` — round-trip testing (OBJ, polyline, manifest write→read)

## Testing Strategies

Run these in order. These are the layers that are NOT covered by the automated suite — they require REPL-level exploration.

### Layer 1: Docstring & Arity Audit (fast, high signal)

For every public fn in the API surface, check `:doc` exists and `:arglists` metadata is present. This catches functions that were added without documentation.

### Layer 2: Parametric Edge Cases

Systematic zero/negative/boundary value sweep. The automated property tests check invariants over random inputs, but they don't specifically target the dangerous boundary values.

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

### Layer 3: Random Scene Fuzzing

Generate 200+ random valid scenes with:
- Random shape types (rect, circle, ellipse, line, path)
- Random colors, random sizes, random positions
- Random nesting depth (1-5 levels of groups)
- Random transforms (translate, rotate, scale, shear — stacked)
- Random effects (shadow, glow, blur)
- Random gradients (linear, radial)
- Random clips

Verify: `eido/render` either succeeds or throws `ExceptionInfo` with `:errors`. Never NPE, ClassCastException, or StackOverflow.

```clojure
(binding [eido/*validate* false]  ;; test engine robustness, not validation
  (eido/render random-scene))
```

The automated visual regression tests use a fixed 25-scene catalog. This layer generates *random* scenes to find crashes the catalog doesn't cover.

### Layer 4: Cross-Format Consistency

Render the same scene to PNG and SVG. Verify:
- SVG shape count matches node count (+1 for background rect)
- SVG is valid XML (parse succeeds)
- No `NaN` or `Infinity` in SVG attribute values

```clojure
(defn parse-svg [s]
  (clojure.xml/parse (java.io.ByteArrayInputStream. (.getBytes s "UTF-8"))))
```

### Layer 5: Round-Trip Testing

- **OBJ**: `write-obj` → `parse-obj` → verify face count matches
- **Polylines**: `render {:format :polylines}` → `pr-str` → `edn/read-string` → verify structure
- **Manifest**: `render {:emit-manifest? true}` → `slurp .edn` → verify `:scene`, `:seed`

### Layer 6: API Consistency

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
