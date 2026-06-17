(ns bench
  "REPL-driven benchmarks for the authoring hot paths (3D projection math
  and mesh-to-node generation). Not run in CI — timing is too noisy on
  shared runners.

  Usage: start a REPL, load this file, evaluate the comment block."
  (:require
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
        torus (s3d/torus-mesh 1.0 0.4 {:ring-segments 24 :tube-segments 12})
        sphere (s3d/sphere-mesh 1.5 {:segments 16 :rings 12})]
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
  (bench-render-mesh))

(comment
  ;; Run at the REPL:
  (bench-all)

  ;; Or individually:
  (bench-math3d)
  (bench-render-mesh))
