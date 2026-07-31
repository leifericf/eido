(ns render-jfr
  "Plain loop harness for JFR profiling of the raw native call path. No
  profiler deps; run under -XX:StartFlightRecording and analyze with:
    jfr view hot-methods /tmp/cpu_<label>.jfr
    jfr view --interaction-style plain --event jdk.ExecutionSample /tmp/cpu_<label>.jfr"
  (:require [eido.phane :as phane]))

(def ^:private raw
  @(resolve 'eido.phane/render-edn-raw))

(def ^:private graph-bytes
  (.getBytes ^String (eido.phane/graph->edn
                       (eido.phane/scene->graph
                         {:image/size       [8 8]
                          :image/background [:color/rgb 0 0 0]
                          :image/nodes      [{:node/type     :shape/circle
                                              :circle/center [4 4]
                                              :circle/radius 3
                                              :style/fill    [:color/rgb 1 1 1]}]}))
             "UTF-8"))
(def ^:private base-bytes (.getBytes "." "UTF-8"))

(defn -main [& [label]]
  (let [label (or label "profile")]
    (raw graph-bytes base-bytes)
    (println "warming 50k calls...")
    (dotimes [_ 50000] (raw graph-bytes base-bytes))
    (println "profiling ~18s of raw calls...")
    (let [deadline (+ (System/nanoTime) (* 18 1e9))
          counter  (volatile! 0)]
      (while (< (System/nanoTime) deadline)
        (raw graph-bytes base-bytes)
        (vswap! counter inc))
      (println (format "ran %d calls; label=%s" @counter label)))
  (shutdown-agents)))
