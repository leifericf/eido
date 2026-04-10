(ns eido.gen.series
  "Long-form generative series utilities.
  Deterministic seed derivation and parameter sampling for
  numbered editions (Art Blocks / fxhash style workflows)."
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [eido.gen.prob :as prob])
  (:import
    [java.io File]
    [javax.imageio ImageIO]))

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

;; --- visual exploration grids ---

(defn- compose-grid
  "Composes a vector of BufferedImages into a grid. Returns a BufferedImage."
  [images cols [tw th]]
  (let [n    (count images)
        rows (int (Math/ceil (/ (double n) cols)))
        w    (* cols (int tw))
        h    (* rows (int th))
        out  (java.awt.image.BufferedImage. w h java.awt.image.BufferedImage/TYPE_INT_ARGB)
        g    (.createGraphics out)]
    (doseq [i (range n)]
      (let [col (mod i cols)
            row (quot i cols)
            img (nth images i)]
        (.drawImage g ^java.awt.image.BufferedImage img
                    (int (* col tw)) (int (* row th))
                    (int tw) (int th) nil)))
    (.dispose g)
    out))

(defn seed-grid
  "Renders a grid of editions as a single BufferedImage for REPL exploration.
  Each cell shows a different seed; pass the result to show at the REPL.
  opts:
    :spec        — parameter spec map
    :master-seed — master seed for the series
    :start       — first edition number (inclusive, default 0)
    :end         — last edition number (exclusive)
    :seeds       — explicit list of edition numbers (overrides start/end)
    :scene-fn    — (fn [params edition-number] scene-map)
    :cols        — number of columns (default 5)
    :thumb-size  — [width height] per thumbnail (default [160 160])"
  [{:keys [spec master-seed start end seeds scene-fn cols thumb-size]}]
  (let [render-fn  (requiring-resolve 'eido.core/render)
        editions   (or seeds (range (or start 0) end))
        cols       (or cols 5)
        [tw th]    (or thumb-size [160 160])
        images     (mapv
                     (fn [edition]
                       (let [params (series-params spec master-seed edition)
                             scene  (scene-fn params edition)
                             scene  (assoc scene :image/size [tw th])]
                         (render-fn scene)))
                     editions)]
    (compose-grid images cols [tw th])))

(defn param-grid
  "Renders a parameter sweep as a single BufferedImage for REPL exploration.
  Varies one param across rows, optionally another across columns.
  Pass the result to show at the REPL.
  opts:
    :base-params — base parameter map
    :row-param   — {:key :param-name :values [v1 v2 ...]}
    :col-param   — {:key :param-name :values [v1 v2 ...]} (optional, default 1 col)
    :seed        — seed for scene generation
    :scene-fn    — (fn [params] scene-map) — takes merged params, returns scene
    :thumb-size  — [width height] per thumbnail (default [160 160])"
  [{:keys [base-params row-param col-param seed scene-fn thumb-size]}]
  (let [render-fn  (requiring-resolve 'eido.core/render)
        [tw th]    (or thumb-size [160 160])
        row-vals   (:values row-param)
        col-vals   (or (:values col-param) [nil])
        cols       (count col-vals)
        images     (vec
                     (for [rv row-vals
                           cv col-vals]
                       (let [params (cond-> (assoc base-params (:key row-param) rv)
                                     cv (assoc (:key col-param) cv))
                             scene  (scene-fn params)
                             scene  (assoc scene :image/size [tw th])]
                         (render-fn scene))))]
    (compose-grid images cols [tw th])))

;; --- batch edition rendering ---

(defn render-editions
  "Renders a batch of editions from a series spec.
  opts:
    :spec           — parameter spec map (as for series-params)
    :master-seed    — master seed for the series
    :start          — first edition number (inclusive)
    :end            — last edition number (exclusive)
    :scene-fn       — (fn [params edition-number] scene-map)
    :output-dir     — directory to write files into
    :format         — :png (default) or :svg
    :render-opts    — optional map passed to eido.core/render (e.g. {:scale 2})
    :traits         — optional trait buckets for derive-traits
    :emit-manifest? — write per-edition .edn sidecar manifests (default false)
  Returns a vector of {:edition n :params params :traits traits :file path}."
  [{:keys [spec master-seed start end scene-fn output-dir
           format render-opts traits emit-manifest?]}]
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
                    opts     (cond-> (merge render-opts {:output filepath})
                               emit-manifest?
                               (assoc :emit-manifest? true
                                      :seed (edition-seed master-seed edition)
                                      :params params))]
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

(defn export-edition-package
  "Renders a complete edition package: images, manifests, and optional contact sheet.
  opts: same as render-editions, plus:
    :contact-sheet? — generate a grid thumbnail image (default true)
    :contact-cols   — columns in contact sheet (default 5)
    :thumb-size     — per-thumbnail size (default [160 160])
  Returns {:editions [...] :contact-sheet path-or-nil}."
  [{:keys [spec master-seed start end scene-fn output-dir
           format render-opts traits
           contact-sheet? contact-cols thumb-size]
    :or   {contact-sheet? true contact-cols 5 thumb-size [160 160]}}]
  (let [editions (render-editions
                   {:spec spec :master-seed master-seed
                    :start start :end end
                    :scene-fn scene-fn :output-dir output-dir
                    :format format :render-opts render-opts
                    :traits traits :emit-manifest? true})
        sheet    (when contact-sheet?
                   (let [img  (seed-grid {:spec spec :master-seed master-seed
                                          :start start :end end
                                          :scene-fn scene-fn
                                          :cols contact-cols
                                          :thumb-size thumb-size})
                         path (str output-dir "/contact-sheet.png")]
                     (ImageIO/write img "png" (File. ^String path))
                     path))]
    {:editions      editions
     :contact-sheet sheet}))

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
