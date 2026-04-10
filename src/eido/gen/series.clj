(ns eido.gen.series
  "Long-form generative series utilities.
  Deterministic seed derivation and parameter sampling for
  numbered editions (Art Blocks / fxhash style workflows)."
  (:require
    [eido.gen.prob :as prob]))

;; --- seed derivation ---

(defn- mix64
  "Murmur3-style 64-bit integer finalizer for seed mixing."
  ^long [^long x]
  (let [x (unchecked-multiply (bit-xor x (unsigned-bit-shift-right x 33))
                              (unchecked-long 0xff51afd7ed558ccd))
        x (unchecked-multiply (bit-xor x (unsigned-bit-shift-right x 33))
                              (unchecked-long 0xc4ceb9fe1a85ec53))]
    (bit-xor x (unsigned-bit-shift-right x 33))))

(defn edition-seed
  "Derives a deterministic, uncorrelated seed for an edition number
  from a master seed. Uses murmur3-style mixing so nearby editions
  produce independent seeds."
  ^long [master-seed edition-number]
  (mix64 (unchecked-add (long master-seed)
                        (unchecked-multiply (long edition-number) 2654435761))))

;; --- parameter sampling ---

(defn series-params
  "Generates a parameter map for one edition in a series.
  spec: map of {:param-name {:type :uniform/:gaussian/:choice/:weighted-choice/:boolean, ...}}
  Each parameter gets a deterministic sub-seed derived from the edition seed
  and a hash of the parameter name."
  [spec master-seed edition-number]
  (let [base-seed (edition-seed master-seed edition-number)]
    (into {}
          (map (fn [[k param-spec]]
                 (let [param-seed (mix64 (unchecked-add base-seed (long (hash k))))]
                   [k (prob/sample param-spec param-seed)])))
          spec)))

(defn series-range
  "Returns a vector of parameter maps for editions start (inclusive) to end (exclusive)."
  [spec master-seed start end]
  (mapv #(series-params spec master-seed %) (range start end)))

;; --- convenience helpers ---

(defn ^{:convenience true :convenience-for 'eido.gen.series/series-params}
  derive-traits
  "Categorizes continuous parameter values into named labels.
  Wraps a simple threshold lookup over a params map.
  buckets: {:param [[threshold label] ...]}
  Example: (derive-traits {:density 42} {:density [[20 \"sparse\"] [40 \"medium\"] [100 \"dense\"]]})"
  [params buckets]
  (into {}
        (map (fn [[k v]]
               (if-let [ranges (get buckets k)]
                 [k (or (some (fn [[thresh label]]
                                (when (<= (double v) (double thresh)) label))
                              ranges)
                        (second (last ranges)))]
                 [k v])))
        params))

;; --- batch edition rendering ---

(defn render-editions
  "Renders a batch of editions from a series spec.
  opts:
    :spec        — parameter spec map (as for series-params)
    :master-seed — master seed for the series
    :start       — first edition number (inclusive)
    :end         — last edition number (exclusive)
    :scene-fn    — (fn [params edition-number] scene-map)
    :output-dir  — directory to write files into
    :format      — :png (default) or :svg
    :render-opts — optional map passed to eido.core/render (e.g. {:scale 2})
    :traits      — optional trait buckets for derive-traits
  Returns a vector of {:edition n :params params :traits traits :file path}."
  [{:keys [spec master-seed start end scene-fn output-dir
           format render-opts traits]}]
  (let [render-fn (requiring-resolve 'eido.core/render)
        fmt       (or format :png)
        dir       (java.io.File. ^String output-dir)]
    (.mkdirs dir)
    (let [results
          (mapv
            (fn [edition]
              (let [params   (series-params spec master-seed edition)
                    scene    (scene-fn params edition)
                    ext      (name fmt)
                    filename (str "edition-" edition "." ext)
                    filepath (str output-dir "/" filename)
                    opts     (merge render-opts {:output filepath})]
                (render-fn scene opts)
                (cond-> {:edition edition
                         :params  params
                         :file    filepath}
                  traits (assoc :traits (derive-traits params traits)))))
            (range start end))]
      ;; Write metadata
      (spit (str output-dir "/metadata.edn")
            (pr-str (mapv #(dissoc % :file) results)))
      results)))

(comment
  (edition-seed 42 0)
  (edition-seed 42 1)
  (series-params
    {:density {:type :uniform :lo 10 :hi 50}
     :palette {:type :choice :options [:sunset :ocean :fire]}}
    42 0)
  (series-range
    {:density {:type :uniform :lo 10 :hi 50}}
    42 0 5))
