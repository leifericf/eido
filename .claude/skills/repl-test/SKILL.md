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

3. For tests that need a clean JVM (no stale REPL state), use `clojure -M:dev -e '...'` directly.

## Focus Areas

The optional `$ARGUMENTS` narrows the scope. If empty, run all areas. Valid focus areas:

- `gen` — generative algorithms (noise, voronoi, circle-pack, scatter, flow, contour, lsystem, boids, subdivide, CA, hatch, stipple, particles, series, prob)
- `path` — path operations (boolean ops, morph, warp, distort, dash, smooth, outline, decorate)
- `render` — rendering pipeline (all shape types, fills, strokes, gradients, effects, clips, opacity, transforms)
- `3d` — 3D pipeline (meshes, cameras, transforms, topology, surface, convenience functions, full render)
- `text` — text functions (text, text-glyphs, text-on-path, text-stack, text-outline, text-clip)
- `format` — output formats (PNG, JPEG, TIFF, BMP, SVG, GIF, polylines, animated SVG)
- `api` — API consistency (parameter orders, opts maps, return types, docstring accuracy)
- `edge` — edge cases specifically (zero bounds, empty collections, nil values, extreme parameters)

## What to Test

For EACH function tested, exercise:

1. **Happy path** — normal usage with typical parameters, verify correct return type and shape
2. **Zero/empty** — zero-area bounds `[0 0 0 0]`, empty collections `[]`, zero counts, zero spacing/distance/resolution/density
3. **Negative** — negative dimensions, negative spacing, negative radius
4. **Extreme** — very large values (1e6 coordinates, 10000 items), very small values (0.001 spacing), `Long/MAX_VALUE` as seed
5. **Render end-to-end** — for anything that produces scene nodes, actually render to a `BufferedImage` and verify dimensions

## Division-by-Zero Pattern

A recurring bug class in generative code: `(int (Math/ceil (/ x divisor)))` where `divisor` can be zero. This produces `Infinity` which fails on `int`/`long` cast. Check ALL functions that take spacing, density, resolution, or min-dist parameters with value `0`.

Search pattern: `Math/ceil (/ ` in `src/eido/gen/`

## API Consistency Checks

Eido's beta5 established these conventions. Verify they hold:

- **Text functions**: `[content origin/path font-spec ...]` parameter order
- **3D convenience functions**: `[projection position opts]` with geometry in opts
- **Generative functions with bounds**: `[bounds opts]` where bounds is `[x y w h]`
- **Mesh factories**: `sphere-mesh`, `cylinder-mesh` etc. use `[primary-arg opts]`
- **All opts maps**: seeds as `:seed` key, not positional

## Generative Algorithm Stress Tests

For each gen function, test with these parametric edge cases:

```clojure
;; Circle-pack
(gen/circle-pack [0 0 0 0] {:seed 42})           ;; zero bounds
(gen/circle-pack [0 0 5 5] {:min-radius 50})      ;; min > bounds
(gen/circle-pack bounds {:min-radius 50 :max-radius 10}) ;; min > max

;; Poisson-disk
(gen/poisson-disk [0 0 100 100] {:min-dist 0})    ;; zero distance
(gen/poisson-disk [0 0 10 10] {:min-dist 100})     ;; dist > bounds

;; Flow-field
(gen/flow-field [0 0 100 100] {:density 0})        ;; zero density
(gen/flow-field [0 0 0 0] {:density 20})           ;; zero bounds

;; Contour
(gen/contour-lines f [0 0 100 100] {:resolution 0})  ;; zero res
(gen/contour-lines f [0 0 100 100] {:thresholds []})  ;; no thresholds

;; Hatch
(gen/hatch-lines [0 0 100 100] {:spacing 0})       ;; zero spacing

;; Subdivide
(gen/subdivide [0 0 0 0] {:iterations 3})          ;; zero bounds

;; Prob
(gen/pick [] 42)                                    ;; empty collection
(gen/weighted-sample 5 [] 42)                       ;; empty weights

;; Voronoi
(gen/voronoi-cells [] [0 0 100 100])               ;; no points
(gen/voronoi-cells [[50 50]] [0 0 100 100])        ;; single point
```

## Rendering Pipeline Tests

Test every shape type renders without error:

```clojure
;; Base scene
(def base {:image/size [200 200] :image/background [:color/rgb 255 255 255]})

;; Shape types: :shape/rect, :shape/circle, :shape/ellipse, :shape/arc,
;;              :shape/line, :shape/path, :shape/text
;; Fill types: solid color, gradient (linear + radial), hatch, stipple, pattern
;; Effects: :shadow, :glow, :blur, :duotone, :halftone, :grain
;; Transforms: translate, rotate, scale, shear-x, shear-y (stacked)
;; Groups: nested groups, clip groups, opacity
```

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
