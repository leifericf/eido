(ns eido.io.plotter
  "Per-layer SVG export for multi-pen plotters.

  Splits a scene by stroke color and writes one SVG file per pen,
  with optional travel optimization and a combined preview."
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]))

(defn- split-svg-groups
  "Splits a grouped plotter SVG string into per-pen SVG strings.
  The input SVG must have been rendered with :group-by-stroke true,
  producing <g id=\"pen-...\"> groups. Returns a vector of
  {:pen id-string :svg full-svg-string} maps."
  [svg-str]
  (let [lines    (str/split-lines svg-str)
        header   (first lines)
        groups   (loop [remaining (rest lines)
                        current   nil
                        result    []]
                   (if (empty? remaining)
                     (if current
                       (conj result current)
                       result)
                     (let [line (first remaining)]
                       (cond
                         ;; Start of a new group
                         (str/starts-with? (str/trim line) "<g id=\"pen-")
                         (let [pen-id (second (re-find #"id=\"([^\"]+)\"" line))]
                           (recur (rest remaining)
                                  {:pen pen-id :lines []}
                                  (if current (conj result current) result)))

                         ;; End of a group
                         (and current (= (str/trim line) "</g>"))
                         (recur (rest remaining)
                                nil
                                (conj result current))

                         ;; Closing </svg> tag
                         (= (str/trim line) "</svg>")
                         (recur (rest remaining) current result)

                         ;; Defs before or inside a group
                         current
                         (recur (rest remaining)
                                (update current :lines conj line)
                                result)

                         ;; Defs outside a group (shared)
                         :else
                         (recur (rest remaining) current result)))))]
    (mapv (fn [{:keys [pen lines]}]
            {:pen pen
             :svg (str/join "\n"
                    (concat [header]
                            (map #(str "  " (str/trim %)) lines)
                            ["</svg>"]))})
          groups)))

(defn export-layers
  "Renders a scene and writes one SVG file per stroke color (pen).
  Returns a vector of {:pen color-id :file path} maps.
  opts:
    :optimize-travel — reorder ops to minimize pen travel (default true)
    :preview         — also write a combined preview SVG (default true)"
  [scene output-dir opts]
  (let [render-fn (requiring-resolve 'eido.core/render-to-svg)
        optimize? (get opts :optimize-travel true)
        preview?  (get opts :preview true)
        svg-opts  {:stroke-only     true
                   :group-by-stroke true
                   :deduplicate     true
                   :optimize-travel optimize?}
        combined  (render-fn scene svg-opts)
        groups    (split-svg-groups combined)
        dir       (io/file output-dir)]
    (.mkdirs dir)
    (when preview?
      (spit (io/file dir "preview.svg") combined))
    (mapv (fn [{:keys [pen svg]}]
            (let [filename (str pen ".svg")
                  path     (str (io/file dir filename))]
              (spit path svg)
              {:pen pen :file path}))
          groups)))
