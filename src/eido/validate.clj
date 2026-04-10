(ns eido.validate
  "Scene validation against the Eido spec. Returns human-readable error
  messages describing what failed and where."
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [eido.spec]))

(defn- pred->description
  "Translates a spec predicate to a human-readable string."
  [pred via]
  (cond
    (some #{:eido.spec/rgb-val} via)
    "integer in 0..255"

    (some #{:eido.spec/unit-val} via)
    "number in 0..1"

    (some #{:eido.spec/hue-val} via)
    "number in 0..360"

    (some #{:eido.spec/pos-number} via)
    "positive number"

    (some #{:eido.spec/pos-size} via)
    "vector of [positive-width positive-height]"

    (some #{:eido.spec/point} via)
    "vector of [x y]"

    (and (seq? pred) (= 'contains? (first pred)))
    (str "missing required key " (last pred))

    ;; s/keys wraps contains? in (fn [%] (contains? % :key))
    ;; The :default node-type multimethod wraps it in
    ;;   (fn [%] (contains? #{:shape/rect ...} (:node/type %)))
    ;; Distinguish by checking whether the second arg to contains? is a set.
    (and (seq? pred) (= 'clojure.core/fn (first pred))
         (let [body (nth pred 2 nil)]
           (and (seq? body) (= 'clojure.core/contains? (first body))
                (not (set? (nth body 1 nil))))))
    (str "missing required key " (last (nth pred 2)))

    (and (seq? pred) (= 'clojure.core/fn (first pred))
         (let [body (nth pred 2 nil)]
           (and (seq? body) (= 'clojure.core/contains? (first body))
                (set? (nth body 1 nil)))))
    (let [valid-types (nth (nth pred 2) 1)]
      (str "unknown node type; valid types are: "
           (str/join ", " (sort (map str valid-types)))))

    (some #{:eido/version} via)
    "version string matching \"X.Y\" (e.g. \"1.0\")"

    (some #{:image/units} via)
    "unit type (:cm, :mm, or :in)"

    (some #{:image/dpi} via)
    "positive number (dots per inch)"

    (some #{:eido.spec/font-spec} via)
    "font map with :font/family (string) and :font/size (positive number)"

    ;; Fallback for ::node via — rarely hit since the contains? set match
    ;; in the :default multimethod produces a better message above.
    (and (some #{:eido.spec/node} via)
         (= :eido.spec/node (last via)))
    "valid node type"

    (some #{:style/fill} via)
    "fill (expected a color, gradient, hatch, stipple, or pattern)"

    (some #{:style/stroke} via)
    "stroke map with :color and :width (optional: :cap, :join, :dash)"

    (and (set? pred) (= 1 (count pred))
         (let [tag (first pred)]
           (and (keyword? tag) (= "transform" (namespace tag)))))
    "transform type (must be :transform/translate, :transform/rotate, :transform/scale, :transform/shear-x, :transform/shear-y, or :transform/distort)"

    :else
    (pr-str pred)))

(defn- format-message
  "Builds a human-readable error message."
  [in desc val]
  (let [loc (when (seq in)
              (str "at " (pr-str (vec in)) ": "))]
    (str loc desc
         (when (some? val)
           (str ", got: " (pr-str val))))))

(defn- problem->error
  "Converts a spec problem to a user-friendly error map."
  [{:keys [pred val in via]}]
  (let [desc (pred->description pred via)]
    {:path    (vec in)
     :pred    desc
     :message (format-message in desc val)
     :value   val}))

(def ^:private tagged-or-specs
  "Keywords that appear as tags in s/or branches for tagged vectors.
  Used to detect and filter tag-mismatch noise."
  #{:color/rgb :color/rgba :color/hsl :color/hsla
    :color/hsb :color/hsba :color/oklab :color/oklaba
    :color/oklch :color/oklcha :color/hex :color/name
    :transform/translate :transform/rotate :transform/scale
    :transform/shear-x :transform/shear-y :transform/distort})

(defn- tag-branch-mismatch?
  "True when a spec problem is a tag-mismatch from a non-matching s/or
  branch (e.g. tried :color/rgba but tag was :color/rgb, or tried
  :transform/rotate but tag was :transform/translate).
  Only filters when the actual value is itself a recognized tag —
  if the value is unknown (e.g. :rotate instead of :transform/rotate),
  all branches are kept so the user sees valid options."
  [{:keys [pred val]}]
  (and (set? pred)
       (= 1 (count pred))
       (tagged-or-specs (first pred))
       (keyword? val)
       (tagged-or-specs val)
       (not (pred val))))

(defn- keyword-branch-mismatch?
  "True when a spec problem is the ::color-keyword branch failing on a
  non-keyword value (e.g. a color vector). Filters noise from s/or."
  [{:keys [via val]}]
  (and (some #{:eido.spec/color-keyword} via)
       (not (keyword? val))))

(defn- deduplicate-tag-mismatches
  "Removes tag-mismatch noise from s/or branches in tagged vectors."
  [problems]
  (->> problems
       (remove tag-branch-mismatch?)
       (remove keyword-branch-mismatch?)))

(defn- deduplicate-by-path
  "When multiple s/or branches fail for the same value at the same path,
  keep only the one with the deepest :via (most specific match)."
  [problems]
  (let [grouped (group-by (fn [p] [(:in p) (:val p)]) problems)]
    (mapcat (fn [[_k ps]]
              (if (= 1 (count ps))
                ps
                ;; Keep only the problem(s) with the deepest :via
                (let [max-depth (apply max (map #(count (:via %)) ps))]
                  (filter #(= max-depth (count (:via %))) ps))))
            grouped)))

(defn validate
  "Validates a scene map against the Eido scene spec.
  Returns nil if valid, or a vector of error maps with
  :path, :pred, :message, and :value."
  [scene]
  (when-let [ed (s/explain-data :eido.spec/scene scene)]
    (let [problems (->> (::s/problems ed)
                        deduplicate-tag-mismatches
                        deduplicate-by-path)]
      (when (seq problems)
        (let [errors (mapv problem->error problems)]
          ;; Deduplicate errors with identical messages (from s/or branches
          ;; that all translate to the same human-readable description).
          (->> errors
               (reduce (fn [acc e]
                         (if (contains? (::seen (meta acc)) (:message e))
                           acc
                           (with-meta (conj acc e)
                                      {::seen (conj (::seen (meta acc)) (:message e))})))
                       (with-meta [] {::seen #{}}))
               vec))))))

(defn format-errors
  "Formats validation errors as a human-readable string.
  Takes a vector of error maps as returned by validate."
  [errors]
  (if (seq errors)
    (str (count errors) " validation error"
         (when (> (count errors) 1) "s") ":\n\n"
         (str/join "\n\n"
           (map-indexed
             (fn [i {:keys [message]}]
               (str "  " (inc i) ". " message))
             errors)))
    "No errors."))

(defn explain
  "Validates a scene and prints human-readable errors to *out*.
  Returns the error vector, or nil if valid."
  [scene]
  (when-let [errors (validate scene)]
    (println (format-errors errors))
    errors))

(comment
  ;; Valid scene returns nil
  (validate {:image/size [800 600]
             :image/background [:color/rgb 255 255 255]
             :image/nodes []})

  ;; Missing required key
  (validate {:image/size [800 600]
             :image/background [:color/rgb 255 255 255]
             :image/nodes [{:node/type :shape/rect}]})

  ;; Invalid version
  (validate {:eido/version "bad"
             :image/size [100 100]
             :image/background [:color/rgb 0 0 0]
             :image/nodes []})
  )
