(ns eido.validate
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

    (some #{:eido.spec/font-spec} via)
    "font map with :font/family (string) and :font/size (positive number)"

    (some #{:eido.spec/node} via)
    "valid node (type must be :shape/rect, :shape/circle, :shape/ellipse, :shape/arc, :shape/line, :shape/path, :shape/text, :shape/text-glyphs, :shape/text-on-path, or :group)"

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

(def ^:private color-tags
  #{:color/rgb :color/rgba :color/hsl :color/hsla
    :color/hsb :color/hsba :color/hex :color/name})

(defn- color-branch-mismatch?
  "True when a spec problem is a tag-mismatch from a non-matching s/or
  branch in the ::color spec (e.g. tried :color/rgba but tag was :color/rgb)."
  [{:keys [pred val via]}]
  (and (some #{:eido.spec/color} via)
       (set? pred)
       (= 1 (count pred))
       (color-tags (first pred))
       (keyword? val)
       (not (pred val))))

(defn- deduplicate-color-problems
  "Removes tag-mismatch noise from color s/or branches."
  [problems]
  (remove color-branch-mismatch? problems))

(defn validate
  "Validates a scene map against the Eido scene spec.
  Returns nil if valid, or a vector of error maps with
  :path, :pred, :message, and :value."
  [scene]
  (when-let [ed (s/explain-data :eido.spec/scene scene)]
    (let [problems (->> (::s/problems ed)
                        deduplicate-color-problems)]
      (when (seq problems)
        (mapv problem->error problems)))))

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
