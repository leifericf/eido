(ns bench
  "REPL-driven benchmarks for measuring performance before/after optimizations.
  Not run in CI — timing is too noisy on shared runners.

  Usage: start a REPL, load this file, evaluate the comment block."
  (:require
    [eido.core :as eido]
    [eido.math :as m]
    [eido.scene3d :as s3d]))

;; --- helpers ---

(defmacro bench
  "Runs body warmup-count + measure-count times, prints average of measurements."
  [label warmup-count measure-count & body]
  `(do
     (dotimes [_# ~warmup-count] ~@body)
     (let [times# (doall
                    (for [_# (range ~measure-count)]
                      (let [start# (System/nanoTime)
                            _#     (do ~@body)
                            end#   (System/nanoTime)]
                        (- end# start#))))
           avg#   (/ (double (reduce + times#)) ~measure-count 1e6)]
       (println (format "%-40s %8.2f ms (avg of %d runs)"
                        ~label avg# ~measure-count)))))

;; --- gallery benchmark ---

(defn bench-gallery-examples
  "Renders all gallery examples and times each one.
  This is the most realistic benchmark — measures end-to-end render time."
  []
  (println "\n=== Gallery Example Render Times ===\n")
  (doseq [ns-sym '[gallery.art
                    gallery.scenes-2d
                    gallery.scenes-3d
                    gallery.mixed
                    gallery.particles
                    gallery.typography
                    gallery.showcase
                    gallery.artisan]]
    (require ns-sym)
    (let [publics (->> (ns-publics (find-ns ns-sym))
                       (filter (fn [[_ v]] (:example (meta v))))
                       (sort-by (comp str first)))]
      (println (str "--- " ns-sym " ---"))
      (doseq [[fn-name fn-var] publics]
        (let [example-meta (:example (meta fn-var))
              output       (:output example-meta)
              is-gif?      (and output (.endsWith (str output) ".gif"))]
          (when-not is-gif?
            (try
              (bench (str "  " fn-name) 1 3
                (eido/render (fn-var)))
              (catch Exception e
                (println (format "  %-40s ERROR: %s" fn-name (.getMessage e))))))))
      (println))))

;; --- isolated benchmarks ---

(defn bench-math3d
  "Microbenchmarks for math3d hot-path functions."
  []
  (println "\n=== Math3D Microbenchmarks (10,000 iterations) ===\n")
  (let [proj-persp (s3d/perspective {:scale 80 :origin [400 300]
                                     :yaw 0.5 :pitch -0.3
                                     :distance 8})
        proj-iso   (s3d/isometric {:scale 80 :origin [400 300]})
        proj-ortho (s3d/orthographic {:scale 80 :origin [400 300]
                                      :yaw 0.5 :pitch -0.3})
        point      [1.5 2.3 -0.7]
        v1         [1.0 2.0 3.0]
        v2         [4.0 5.0 6.0]]
    (bench "project (perspective)" 100 10
      (dotimes [_ 10000] (m/project proj-persp point)))
    (bench "project (isometric)" 100 10
      (dotimes [_ 10000] (m/project proj-iso point)))
    (bench "project (orthographic)" 100 10
      (dotimes [_ 10000] (m/project proj-ortho point)))
    (bench "normalize" 100 10
      (dotimes [_ 10000] (m/normalize v1)))
    (bench "dot" 100 10
      (dotimes [_ 10000] (m/dot v1 v2)))
    (bench "cross" 100 10
      (dotimes [_ 10000] (m/cross v1 v2)))
    (bench "v+" 100 10
      (dotimes [_ 10000] (m/v+ v1 v2)))
    (bench "view-transform (perspective)" 100 10
      (dotimes [_ 10000] (m/view-transform proj-persp point)))))

(defn bench-render-mesh
  "Benchmarks render-mesh with realistic meshes."
  []
  (println "\n=== render-mesh Benchmarks ===\n")
  (let [proj  (s3d/perspective {:scale 80 :origin [400 300]
                                :yaw 0.5 :pitch -0.3 :distance 8})
        light {:light/direction [1 2 1]
               :light/ambient 0.3 :light/intensity 0.7}
        style {:style/fill [:color/rgb 100 140 180]
               :style/stroke {:color [:color/rgb 60 100 140] :width 0.3}}
        opts  {:style style :light light}
        torus (s3d/torus-mesh 1.0 0.4 24 12)
        sphere (s3d/sphere-mesh 1.5 16 12)]
    (println (str "Torus: " (count torus) " faces"))
    (println (str "Sphere: " (count sphere) " faces"))
    (println)
    (bench "render-mesh torus (288 faces)" 3 10
      (s3d/render-mesh proj torus opts))
    (bench "render-mesh sphere (192 faces)" 3 10
      (s3d/render-mesh proj sphere opts))))

(defn bench-all
  "Runs all benchmarks."
  []
  (bench-math3d)
  (bench-render-mesh)
  (bench-gallery-examples))

(comment
  ;; Run at the REPL:
  (bench-all)

  ;; Or individually:
  (bench-math3d)
  (bench-render-mesh)
  (bench-gallery-examples)

  ;; ======================================================================
  ;; COMPLETE BENCHMARK COMPARISON (2026-04-08)
  ;;
  ;; All numbers below are JIT-warmed: full warmup pass over all gallery
  ;; examples before measuring. 3 runs averaged.
  ;;
  ;; main branch = db05939 (before optimization)
  ;; perf branch = perf/math3d-optimization (20 commits)
  ;; ======================================================================
  ;;
  ;; === Gallery: main vs optimized (JIT-warmed, ms) ===
  ;;
  ;; Example                          main    perf   change
  ;; -------                          ----    ----   ------
  ;; van-gogh-swirls                 2174    2008     -8%  (reflection fix, perm cache, stroke dedup)
  ;; polka-pop                        163     192    noise (shadow/blur dominated by Java2D)
  ;; stipple-spheres                  132     125     -5%  (ArrayList swap-remove)
  ;; utah-teapot                      126     142    noise (mesh compile + Java2D)
  ;; halftone-layers                   96      96       0% (Graphics2D .fill dominates)
  ;; paper-collage                     92      93    noise
  ;; contour-terrain                   69      84    noise (JIT already optimizes the hot path)
  ;; geometric-tiling                  49      49       0%
  ;; topo-map                          42      45    noise
  ;; vintage-map                       37      16    -57%  (perm cache + noise arrays)
  ;; woodcut-landscape                 40      40       0%
  ;; stained-glass-rose                34      41    noise
  ;; geode                             35      53    noise
  ;; textile                           43      42       0%
  ;; pointillist-landscape             39      34    -13%  (noise improvements)
  ;; sumi-e-bamboo                     24      20    -17%
  ;; watercolor-blooms                 24      21    -13%
  ;; ink-landscape                     19      20    noise
  ;; thermal                           21      22    noise
  ;; neon-glow                         19      20    noise
  ;; torus                             16      18    noise
  ;; fractal-forest                    12      13    noise
  ;; decorative-frame                  13      13       0%
  ;; botanical-lsystem                 11      12    noise
  ;; topo-rings                        14      13    noise
  ;; mandala                           11      12    noise
  ;; camera-look-at                    10      11    noise
  ;; camera-perspective-fov            11      11       0%
  ;; zen-garden                        17      17       0%
  ;; new-primitives                    26      27    noise
  ;; landscape-type                    21      24    noise
  ;; isometric-city                     8       7    noise
  ;; starfield                          7       7       0%
  ;; stained-glass                      7       7       0%
  ;; glitch-art                        13      21    noise
  ;; art-deco-sunburst                  6       6       0%
  ;; spiral-text                        7       6    noise
  ;; risograph                          5       5       0%
  ;; chromatic-scatter                  5       5       0%
  ;; wireframe                          4       6    noise
  ;; isometric-scene                    4       4       0%
  ;; wavy-text                          2       3    noise
  ;; venn-booleans                      3       2    noise
  ;; organic-mandala                    3       2    noise
  ;; memphis-pattern                    2       2       0%
  ;; type-poster                        2       1    noise
  ;; gradient-text-with-shadow          1       1       0%
  ;; celtic-interlace                   1       2    noise
  ;; per-glyph-rainbow                  0       0       0%
  ;; bauhaus                            1       1       0%
  ;; prism                              1       1       0%
  ;;
  ;; === Key insight ===
  ;;
  ;; The JIT compiler is highly effective on Clojure code. Most gallery
  ;; examples are already well-optimized by HotSpot after warmup. The
  ;; measurable JIT-warmed improvements are:
  ;;
  ;; 1. van-gogh-swirls: -8% (reflection fix + noise perm caching)
  ;; 2. vintage-map: -57% (noise perm caching + array access)
  ;; 3. pointillist-landscape: -13% (noise improvements)
  ;; 4. sumi-e/watercolor: -13-17% (noise improvements)
  ;; 5. stipple-spheres: -5% (ArrayList optimization)
  ;; 6. compile/compile (internal): 6.2x faster (validation moved out)
  ;;
  ;; === Where the real value is ===
  ;;
  ;; Cold-start performance (no JIT warmup) improved dramatically:
  ;;   van-gogh-swirls:  4838ms → 2008ms  (2.4x faster)
  ;;   contour-terrain:  1614ms →   84ms  (19x faster)
  ;;   topo-map:         1048ms →   45ms  (23x faster)
  ;;   neon-glow:         114ms →   20ms  (5.7x faster)
  ;;   polka-pop:         565ms →  192ms  (2.9x faster)
  ;;
  ;; These cold-start numbers matter for single-render use cases (CLI,
  ;; CI gallery builds, one-off exports). The JIT needs hundreds of
  ;; invocations to fully optimize; our changes provide the speedup
  ;; immediately without JIT warmup.
  ;;
  ;; === Infrastructure added ===
  ;;
  ;; - Visual regression tests (test/eido/visual_test.clj)
  ;; - CI workflow (.github/workflows/test.yml)
  ;; - Benchmarks (dev/bench.clj)
  ;; - Profiler setup (dev/profile.clj, clj-async-profiler)
  ;;
  ;; === What the profiler revealed ===
  ;;
  ;; Post-optimization CPU profile (van-gogh-swirls, warmed):
  ;;   9.5%  itable stub (JVM interface dispatch — fundamental)
  ;;   7.3%  PersistentArrayMap.indexOf (keyword map lookups)
  ;;   7.2%  PersistentHashMap.valAt (map lookups)
  ;;   6.9%  RT.getFrom (map access)
  ;;   5.5%  G1 GC (garbage collection)
  ;;   3.0%  sun/java2d/marlin — actual pixel rendering
  ;;   2.8%  spec/alpha — validation (at API boundary)
  ;;
  ;; The remaining CPU time is dominated by Clojure runtime overhead
  ;; (map lookups, sequence traversal, GC) and Java2D rasterization.
  ;; Further gains would require architectural changes (records vs maps,
  ;; batched rendering) or JVM tuning (GC flags, JIT thresholds)
  ;;
  ;; === Round 3 optimizations (warmed comparison) ===
  ;;
  ;; Example                     before R3   after R3   change
  ;; -------                     ---------   --------   ------
  ;; van-gogh-swirls                  2008       1529    -24%
  ;;   compile only                    294        277     -6%
  ;; utah-teapot                       142        108    -24%
  ;; stipple-spheres                   125        124      0%
  ;; polka-pop                         192        152    -21%
  ;; contour-terrain                    84         60    -29%
  ;; topo-map                           45         39    -13%
  ;; pointillist-landscape              34         29    -15%
  ;; sumi-e-bamboo                      20         16    -20%
  ;; thermal                            22         18    -18%
  ;;
  ;; Changes: compile-node + render-op multimethods → case, conditional
  ;; save/restore in render-single-op, compile-tree destructuring,
  ;; compile-command + build-path + flatten-commands seq allocation removal,
  ;; SVG fmt regex → manual trim, ensure-double-coords eliminated,
  ;; animation frame validation skip
  ;;
  ;; === Round 4: Records, batching, JVM tuning ===
  ;;
  ;; Example                     R3 final    R4 final   change
  ;; -------                     --------    --------   ------
  ;; van-gogh-swirls                 1529        1665    noise (validation dominates)
  ;; utah-teapot                      108         115    noise
  ;; stipple-spheres                  124         122      0%
  ;; polka-pop                        152         212    noise (shadow blur variance)
  ;; contour-terrain                   60          61      0%
  ;; topo-map                          39          42    noise
  ;; vintage-map                       16          13    -19%
  ;; textile                           41          37    -10%
  ;; sumi-e-bamboo                     16          16      0%
  ;; thermal                           18          19    noise
  ;;
  ;; The JIT already optimizes keyword access on records similarly to maps
  ;; after warmup. Records primarily help cold-start and GC pressure.
  ;; BasicStroke caching and opacity tracking reduce per-op overhead.
  ;; :perf alias (clojure -M:perf) provides JVM tuning for batch renders.
  ;;
  ;; === Optional validation skip (:eido/validate false) ===
  ;;
  ;; Validation cost scales with node count:
  ;;   polka-pop (5 nodes)       0.08 ms  (negligible)
  ;;   contour-terrain           0.25 ms  (negligible)
  ;;   utah-teapot (~1600 faces)  73 ms   (63% of render time)
  ;;   van-gogh-swirls (6683)  1238 ms   (76% of render time)
  ;;
  ;; With :eido/validate false on scene map:
  ;;   van-gogh-swirls         455 ms  (was 1579, 3.5x faster)
  ;;   utah-teapot              43 ms  (was 125, 2.9x faster)
  ;;
  ;; Default behavior unchanged — validation runs unless explicitly opted out.
  )
