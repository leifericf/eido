(ns eido.gen.series
  "Long-form generative series utilities.
  Deterministic seed derivation and parameter sampling for
  numbered editions (Art Blocks / fxhash style workflows)."
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
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

(defn trait-summary
  "Computes trait frequencies across n editions of a series.
  Returns {:trait-name {\"label\" count ...} ...} — pure data for REPL inspection.
  Useful for verifying that rare traits are actually rare before releasing."
  [spec master-seed n-editions trait-buckets]
  (let [params-seq (series-range spec master-seed 0 n-editions)
        traits-seq (mapv #(derive-traits % trait-buckets) params-seq)]
    (into {}
      (map (fn [k]
             [k (frequencies (mapv k traits-seq))]))
      (keys trait-buckets))))

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

;; --- seed bookmarks ---

(defn save-seed!
  "Appends a seed bookmark to an EDN log file.
  entry should contain at least :seed; :params and :note are optional.
  Adds :timestamp automatically. Accumulates one EDN form per line."
  [path entry]
  (let [enriched (assoc entry :timestamp (str (java.time.Instant/now)))]
    (spit path (str (pr-str enriched) "\n") :append true)
    enriched))

(defn load-seeds
  "Reads all seed bookmarks from an EDN log file.
  Returns a vector of maps in append order, or [] if the file does not exist."
  [path]
  (if (.exists (io/file path))
    (let [content (slurp path)]
      (into []
            (comp (remove clojure.string/blank?)
                  (map edn/read-string))
            (clojure.string/split-lines content)))
    []))
