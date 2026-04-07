(ns eido.obj
  "Wavefront OBJ and MTL parser. Pure functions: string in, mesh data out."
  (:require
    [clojure.string :as str]
    [eido.math3d :as m]))

;; --- MTL parsing ---

(defn parse-mtl
  "Parses MTL text into a map of material-name to style map.
  Returns e.g. {\"wood\" {:style/fill [:color/rgb 139 90 43] :node/opacity 1.0}}

  Supported directives: newmtl, Kd, d. All others are ignored."
  [text]
  (let [lines (str/split-lines text)]
    (first
      (reduce
        (fn [[materials current-name] line]
          (let [trimmed (str/trim line)]
            (cond
              (or (str/blank? trimmed) (str/starts-with? trimmed "#"))
              [materials current-name]

              (str/starts-with? trimmed "newmtl ")
              (let [name (str/trim (subs trimmed 7))]
                [(assoc materials name {:node/opacity 1.0}) name])

              (and current-name (str/starts-with? trimmed "Kd "))
              (let [parts (str/split (str/trim (subs trimmed 3)) #"\s+")
                    [r g b] (mapv #(int (Math/round (* 255.0 (Double/parseDouble %)))) parts)]
                [(assoc-in materials [current-name :style/fill]
                           [:color/rgb r g b])
                 current-name])

              (and current-name (str/starts-with? trimmed "d "))
              (let [d (Double/parseDouble (str/trim (subs trimmed 2)))]
                [(assoc-in materials [current-name :node/opacity] d)
                 current-name])

              :else [materials current-name])))
        [{} nil]
        lines))))

;; --- OBJ parsing ---

(defn- parse-vertex [line]
  (let [parts (str/split (str/trim (subs line 2)) #"\s+")]
    (mapv #(Double/parseDouble %) (take 3 parts))))

(defn- parse-normal [line]
  (let [parts (str/split (str/trim (subs line 3)) #"\s+")]
    (mapv #(Double/parseDouble %) (take 3 parts))))

(defn- parse-face-ref
  "Parses a single face vertex reference like '1', '1//2', or '1/2/3'.
  Returns [vertex-idx normal-idx-or-nil]."
  [ref-str]
  (let [parts (str/split ref-str #"/")]
    [(Integer/parseInt (first parts))
     (when (and (= 3 (count parts))
                (not (str/blank? (nth parts 2))))
       (Integer/parseInt (nth parts 2)))]))

(defn- resolve-index
  "Resolves an OBJ index (1-based, or negative for relative) to 0-based."
  [idx total]
  (if (neg? idx)
    (+ total idx)
    (dec idx)))

(defn parse-obj
  "Parses OBJ text into a mesh (vector of face maps).

  opts:
    :materials     - map from material name to style (from parse-mtl)
    :default-style - style for faces with no material assignment"
  [text opts]
  (let [lines     (str/split-lines text)
        materials (get opts :materials {})
        default   (get opts :default-style nil)]
    (first
      (reduce
        (fn [[faces vertices normals current-mtl] line]
          (let [trimmed (str/trim line)]
            (cond
              (or (str/blank? trimmed) (str/starts-with? trimmed "#"))
              [faces vertices normals current-mtl]

              (and (str/starts-with? trimmed "v ")
                   (not (str/starts-with? trimmed "vt"))
                   (not (str/starts-with? trimmed "vn")))
              [faces (conj vertices (parse-vertex trimmed)) normals current-mtl]

              (str/starts-with? trimmed "vn ")
              [faces vertices (conj normals (parse-normal trimmed)) current-mtl]

              (str/starts-with? trimmed "usemtl ")
              (let [name (str/trim (subs trimmed 7))]
                [faces vertices normals name])

              (str/starts-with? trimmed "f ")
              (let [refs    (mapv parse-face-ref
                                 (str/split (str/trim (subs trimmed 2)) #"\s+"))
                    v-count (count vertices)
                    n-count (count normals)
                    verts   (mapv (fn [[vi _ni]]
                                   (nth vertices (resolve-index vi v-count)))
                                 refs)
                    ;; Use explicit normal if provided, else compute
                    normal  (let [[_vi ni] (first refs)]
                              (if (and ni (pos? n-count))
                                (nth normals (resolve-index ni n-count))
                                (m/face-normal verts)))
                    style   (or (get materials current-mtl) default)
                    face    (cond-> {:face/vertices verts
                                     :face/normal   normal}
                              style (assoc :face/style style))]
                [(conj faces face) vertices normals current-mtl])

              :else
              [faces vertices normals current-mtl])))
        [[] [] [] nil]
        lines))))
