(ns bench
  "REPL-driven benchmarks for measuring performance before/after optimizations.
  Not run in CI — timing is too noisy on shared runners.

  Usage: start a REPL, load this file, evaluate the comment block."
  (:require
    [eido.core :as eido]
    [eido.math3d :as m]
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

  ;; === Baseline (before optimization, 2026-04-08) ===
  ;;
  ;; Math3D Microbenchmarks (10,000 iterations):
  ;;   project (perspective)          0.79 ms
  ;;   project (isometric)            0.60 ms
  ;;   project (orthographic)         0.81 ms
  ;;   normalize                      0.45 ms
  ;;   dot                            0.31 ms
  ;;   cross                          0.35 ms
  ;;   v+                             0.33 ms
  ;;   view-transform (perspective)   0.54 ms
  ;;
  ;; render-mesh:
  ;;   torus (288 faces)              1.10 ms
  ;;   sphere (192 faces)             0.51 ms
  ;;
  ;; Gallery highlights:
  ;;   van-gogh-swirls              4838 ms
  ;;   contour-terrain              1614 ms
  ;;   topo-map                     1048 ms
  ;;   polka-pop                     565 ms
  ;;   paper-collage                 289 ms
  ;;   vintage-map                   222 ms
  ;;   stipple-spheres               154 ms
  ;;   utah-teapot                   126 ms

  ;; === After optimization (Phases 1-3, 2026-04-08) ===
  ;;
  ;; Math3D Microbenchmarks (10,000 iterations):
  ;;   project (perspective)          0.76 ms  (was 0.79)
  ;;   project (isometric)            0.54 ms  (was 0.60)
  ;;   project (orthographic)         0.72 ms  (was 0.81)
  ;;   normalize                      0.32 ms  (was 0.45)
  ;;   dot                            0.28 ms  (was 0.31)
  ;;
  ;; render-mesh (with JIT warmup):
  ;;   torus (288 faces)              0.25 ms  (was 1.10, ~4.4x faster)
  ;;   sphere (192 faces)             0.17 ms  (was 0.51, ~3x faster)
  ;;
  ;; Gallery highlights (selected):
  ;;   van-gogh-swirls              4111 ms  (was 4838, ~15% faster)
  ;;   ink-landscape                  31 ms  (was 60, ~2x faster)
  ;;   utah-teapot                   116 ms  (was 126)
  ;;   stained-glass                   6 ms  (was 12, ~2x faster)
  ;;   isometric-scene                 3 ms  (was 4)

  ;; === After contour segment connection optimization ===
  ;;   contour-terrain              1391 ms  (was 1540, ~10% faster)
  ;;   topo-map                      926 ms  (was 1048, ~12% faster)
  ;;   connect-segments now O(n) via spatial hashing instead of O(n²)

  ;; === After box-blur vector allocation removal ===
  ;;   ink-landscape                   33 ms  (was 60, ~45% faster)
  ;;   Eliminated per-pixel vector allocation [sa sr sg sb] in blur inner loop

  ;; === After pattern tile memoization ===
  ;;   Memoize pattern->paint so identical pattern specs reuse tiles.
  ;;   polka-pop not helped (each circle has unique pattern + bottleneck is shadow blur)
  )
