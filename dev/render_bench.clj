(ns render-bench
  "Before/after native-render benchmark for the clj-zig version bump.
  Measures end-to-end render-eido, the raw native FFM call (clj-zig
  overhead + phane render), and EDN serialization in isolation."
  (:require [criterium.core :as c]
            [eido.phane :as phane]))
(def ^:private raw
  "The private defnz boundary fn, dereffed for direct repeated calls."
  @(resolve 'eido.phane/render-edn-raw))

(def small-scene
  "An 8x8 single-circle scene: overhead-bound, so sensitive to clj-zig
  per-call cost."
  {:image/size      [8 8]
   :image/background [:color/rgb 0 0 0]
   :image/nodes     [{:node/type     :shape/circle
                      :circle/center [4 4]
                      :circle/radius 3
                      :style/fill    [:color/rgb 1 1 1]}]})

(def large-scene
  "A 512x512 scene with many nodes: render-bound, so phane dominates and
  clj-zig's slice-in/bytes-out copy is a smaller fraction."
  {:image/size      [512 512]
   :image/background [:color/rgb 0 0 0]
   :image/nodes     (into []
                          (for [i (range 64) j (range 64)]
                            {:node/type     :shape/circle
                             :circle/center [(+ 4 (* 8 i)) (+ 4 (* 8 j))]
                             :circle/radius 3
                             :style/fill    [:color/rgb 1 1 1]}))})

;; Pre-built graph EDN bytes for the raw-call microbenchmark (no EDN
;; printing inside the timed region).
(def ^:private small-graph-edn
  (eido.phane/graph->edn (eido.phane/scene->graph small-scene)))
(def ^:private small-graph-bytes (.getBytes ^String small-graph-edn "UTF-8"))
(def ^:private base-bytes (.getBytes "." "UTF-8"))

(defn -main [& _]
  ;; Warm up: forces lib compile + JIT on every path before timing.
  (phane/render-eido small-scene)
  (raw small-graph-bytes base-bytes)
  (phane/render-eido large-scene)
  (println (format "\n=== BENCH LABEL: %s ===\n"
                   (or (System/getenv "BENCH_LABEL") "unlabeled")))
  (println "=== native render (clj-zig FFM + phane) ===")
  (println "\n[s1] render-eido small (8x8, end-to-end)")
  (c/quick-bench (phane/render-eido small-scene))
  (println "\n[s2] raw native call small (clj-zig call + phane render)")
  (c/quick-bench (raw small-graph-bytes base-bytes))
  (println "\n[s3] graph->edn only (no native call)")
  (c/quick-bench (eido.phane/graph->edn (eido.phane/scene->graph small-scene)))
  (println "\n[s4] render-eido large (512x512, 4096 nodes, render-bound)")
  (c/quick-bench (phane/render-eido large-scene))
  (shutdown-agents))
