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

;; --- OBJ/MTL export ---

(defn write-mtl
  "Generates MTL text from a map of material-name to style map.
  Inverse of parse-mtl."
  [materials]
  (str/join "\n"
    (mapcat (fn [[name style]]
              (let [[_ r g b] (:style/fill style)
                    opacity    (get style :node/opacity 1.0)]
                [(str "newmtl " name)
                 (str "Kd " (/ (double r) 255.0) " "
                             (/ (double g) 255.0) " "
                             (/ (double b) 255.0))
                 (str "d " opacity)
                 ""]))
            materials)))

(defn write-obj
  "Generates OBJ text from a mesh (vector of face maps).
  opts:
    :name - object name (default \"mesh\")
    :mtl  - if true, includes material references; returns {:obj str :mtl str}
            if false (default), returns the OBJ string directly"
  ([mesh] (write-obj mesh {}))
  ([mesh opts]
   (let [obj-name  (get opts :name "mesh")
         emit-mtl? (get opts :mtl false)
         ;; Collect unique vertices and build index map
         all-verts (vec (distinct (mapcat :face/vertices mesh)))
         vert-idx  (into {} (map-indexed (fn [i v] [v (inc i)])) all-verts)
         ;; Collect unique normals
         all-normals (vec (distinct (map :face/normal mesh)))
         normal-idx  (into {} (map-indexed (fn [i n] [n (inc i)])) all-normals)
         ;; Group faces by style for material assignment
         style-groups (if emit-mtl?
                        (group-by :face/style mesh)
                        {nil mesh})
         ;; Build material name map
         style->mtl (when emit-mtl?
                      (into {}
                        (map-indexed (fn [i [style _]]
                                       [style (if style
                                                (str "mat_" i)
                                                "default")])
                                     style-groups)))
         ;; Write vertex lines
         v-lines (mapv (fn [[x y z]]
                         (str "v " (double x) " " (double y) " " (double z)))
                       all-verts)
         ;; Write normal lines
         vn-lines (mapv (fn [[x y z]]
                          (str "vn " (double x) " " (double y) " " (double z)))
                        all-normals)
         ;; Write face lines grouped by material
         f-lines (into []
                   (mapcat
                     (fn [[style faces]]
                       (let [mtl-line (when (and emit-mtl? style->mtl)
                                        [(str "usemtl " (get style->mtl style))])]
                         (into (vec mtl-line)
                           (map (fn [face]
                                  (let [vi (map #(get vert-idx %) (:face/vertices face))
                                        ni (get normal-idx (:face/normal face))]
                                    (str "f " (str/join " "
                                                (map #(str % "//" ni) vi)))))
                                faces))))
                     style-groups))
         obj-str (str/join "\n"
                   (concat [(str "# Eido OBJ export")
                            (str "o " obj-name)]
                           v-lines
                           vn-lines
                           f-lines
                           [""]))]
     (if emit-mtl?
       {:obj obj-str
        :mtl (write-mtl
               (into {}
                 (keep (fn [[style mtl-name]]
                         (when style
                           [mtl-name style]))
                       style->mtl)))}
       obj-str))))

;; --- OBJ parsing ---

(defn parse-obj
  "Parses OBJ text into a mesh (vector of face maps).

  opts:
    :materials     - map from material name to style (from parse-mtl)
    :default-style - style for faces with no material assignment

  Supported directives: v, vn, vt (parsed but not stored), f, usemtl,
  g (group name → :face/group), s (smooth group → :face/smooth-group),
  o (object name, ignored). All others are silently skipped."
  [text opts]
  (let [lines     (str/split-lines text)
        materials (get opts :materials {})
        default   (get opts :default-style nil)]
    (first
      (reduce
        (fn [[faces vertices normals current-mtl current-group smooth-group] line]
          (let [trimmed (str/trim line)]
            (cond
              (or (str/blank? trimmed) (str/starts-with? trimmed "#"))
              [faces vertices normals current-mtl current-group smooth-group]

              (and (str/starts-with? trimmed "v ")
                   (not (str/starts-with? trimmed "vt"))
                   (not (str/starts-with? trimmed "vn")))
              [faces (conj vertices (parse-vertex trimmed)) normals
               current-mtl current-group smooth-group]

              (str/starts-with? trimmed "vn ")
              [faces vertices (conj normals (parse-normal trimmed))
               current-mtl current-group smooth-group]

              (str/starts-with? trimmed "usemtl ")
              (let [name (str/trim (subs trimmed 7))]
                [faces vertices normals name current-group smooth-group])

              (str/starts-with? trimmed "g ")
              (let [name (str/trim (subs trimmed 2))]
                [faces vertices normals current-mtl name smooth-group])

              (str/starts-with? trimmed "s ")
              (let [val (str/trim (subs trimmed 2))
                    sg  (when-not (= val "off") val)]
                [faces vertices normals current-mtl current-group sg])

              (str/starts-with? trimmed "f ")
              (let [refs    (mapv parse-face-ref
                                 (str/split (str/trim (subs trimmed 2)) #"\s+"))
                    v-count (count vertices)
                    n-count (count normals)
                    verts   (mapv (fn [[vi _ni]]
                                   (nth vertices (resolve-index vi v-count)))
                                 refs)
                    normal  (let [[_vi ni] (first refs)]
                              (if (and ni (pos? n-count))
                                (nth normals (resolve-index ni n-count))
                                (m/face-normal verts)))
                    style   (or (get materials current-mtl) default)
                    face    (cond-> {:face/vertices verts
                                     :face/normal   normal}
                              style         (assoc :face/style style)
                              current-group (assoc :face/group current-group)
                              smooth-group  (assoc :face/smooth-group smooth-group))]
                [(conj faces face) vertices normals current-mtl current-group smooth-group])

              :else
              [faces vertices normals current-mtl current-group smooth-group])))
        [[] [] [] nil nil nil]
        lines))))
