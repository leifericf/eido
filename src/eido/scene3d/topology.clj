(ns eido.scene3d.topology
  "Mesh topology: adjacency, subdivision, and auto-smoothing."
  (:require
    [eido.math :as m]
    [eido.scene3d.util :as u]))

;; --- mesh adjacency ---

(defn build-face-adjacency
  "Builds adjacency maps from a mesh.
  Returns {:edge-faces {edge-key → [face-index ...]}
           :vert-faces {vertex → [face-index ...]}
           :face-data  [{:vertices [...] :style ... :normal ... :point ...} ...]}"
  [mesh]
  (let [face-data (mapv (fn [face]
                          (let [verts (:face/vertices face)]
                            {:vertices verts
                             :style    (:face/style face)
                             :normal   (:face/normal face)
                             :point    (m/face-centroid verts)}))
                        mesh)
        edge-faces (reduce
                     (fn [acc [fi fd]]
                       (let [verts (:vertices fd)
                             n     (count verts)]
                         (reduce (fn [acc i]
                                   (let [j  (mod (inc i) n)
                                         ek (u/edge-key (nth verts i) (nth verts j))]
                                     (update acc ek (fnil conj []) fi)))
                                 acc (range n))))
                     {} (map-indexed vector face-data))
        vert-faces (reduce
                     (fn [acc [fi fd]]
                       (reduce (fn [acc v]
                                 (update acc v (fnil conj []) fi))
                               acc (:vertices fd)))
                     {} (map-indexed vector face-data))]
    {:edge-faces edge-faces
     :vert-faces vert-faces
     :face-data  face-data}))

(defn compute-vertex-normals
  "Computes smooth vertex normals by averaging adjacent face normals.
  Returns {vertex → normalized-average-normal}."
  [face-data vert-faces]
  (reduce-kv
    (fn [acc v fi-list]
      (let [sum (reduce (fn [s fi]
                          (m/v+ s (m/normalize (:normal (nth face-data fi)))))
                        [0.0 0.0 0.0] fi-list)]
        (assoc acc v (m/normalize sum))))
    {} vert-faces))

;; --- subdivision ---

(defn- subdivide-once
  "One iteration of Catmull-Clark subdivision."
  ([mesh] (subdivide-once mesh nil))
  ([mesh hard-edges]
   (let [{:keys [edge-faces vert-faces face-data]} (build-face-adjacency mesh)

         vert-edges (reduce-kv
                      (fn [acc ek _]
                        (let [[a b] ek]
                          (-> acc
                              (update a (fnil conj #{}) ek)
                              (update b (fnil conj #{}) ek))))
                      {} edge-faces)

         edge-points (reduce-kv
                       (fn [acc ek face-indices]
                         (let [[a b] ek
                               mid   (m/lerp a b 0.5)]
                           (if (and hard-edges (contains? hard-edges ek))
                             ;; Hard edge: use midpoint (no averaging)
                             (assoc acc ek mid)
                             (if (= 2 (count face-indices))
                               (let [fp0 (:point (nth face-data (first face-indices)))
                                     fp1 (:point (nth face-data (second face-indices)))]
                                 (assoc acc ek (m/v* (reduce m/v+ [a b fp0 fp1]) (/ 1.0 4.0))))
                               (assoc acc ek mid)))))
                       {} edge-faces)

         new-verts  (reduce-kv
                      (fn [acc v fi-list]
                        (let [edges (get vert-edges v)
                              n-hard (if hard-edges
                                       (count (filter #(contains? hard-edges %) edges))
                                       0)]
                          (if (>= n-hard 2)
                            ;; Vertex on hard edge crease: use boundary rule
                            (let [hard-mids (->> edges
                                                 (filter #(contains? hard-edges %))
                                                 (mapv (fn [[a b]] (m/lerp a b 0.5))))
                                  avg-mid   (m/v* (reduce m/v+ hard-mids)
                                                  (/ 1.0 (count hard-mids)))]
                              (assoc acc v (m/v* (m/v+ (m/v* v 6.0)
                                                       (m/v* avg-mid 2.0))
                                                 (/ 1.0 8.0))))
                            ;; Normal Catmull-Clark vertex rule
                            (let [n  (count fi-list)
                                  fp (m/v* (reduce m/v+ (mapv #(:point (nth face-data %)) fi-list))
                                           (/ 1.0 n))
                                  ep (if (seq edges)
                                       (m/v* (reduce m/v+ (mapv (fn [[a b]] (m/lerp a b 0.5)) edges))
                                             (/ 1.0 (count edges)))
                                       v)
                                  new-pos (m/v* (m/v+ (m/v+ fp (m/v* ep 2.0))
                                                      (m/v* v (- n 3.0)))
                                                (/ 1.0 n))]
                              (assoc acc v new-pos)))))
                      {} vert-faces)]

     ;; Generate new quad faces
     (into []
       (mapcat
         (fn [{:keys [vertices style point]}]
           (let [n (count vertices)]
             (for [i (range n)
                   :let [j  (mod (inc i) n)
                         v0 (get new-verts (nth vertices i))
                         e0 (get edge-points (u/edge-key (nth vertices i) (nth vertices j)))
                         fp point
                         e1 (get edge-points (u/edge-key (nth vertices (mod (dec i) n))
                                                       (nth vertices i)))]]
               (u/make-face [v0 e0 fp e1] style))))
         face-data)))))

(defn subdivide
  "Applies Catmull-Clark subdivision to a mesh.
  opts:
    :iterations  - number of subdivision passes (each 4× face count)
    :hard-edges  - optional set of edge keys to preserve as creases
  Returns a new mesh of quad faces."
  [mesh opts]
  (let [iterations (get opts :iterations 1)
        hard       (:hard-edges opts)]
    (if (<= iterations 0)
      mesh
      (recur (subdivide-once mesh hard) (assoc opts :iterations (dec iterations))))))

;; --- auto-smooth ---

(defn auto-smooth-edges
  "Returns a set of hard edge keys where adjacent faces meet at more than
  the given angle. Use with subdivide's :hard-edges option.
  opts:
    :angle - threshold angle in radians (edges sharper than this are hard)"
  [mesh opts]
  (let [angle     (double (:angle opts))
        cos-limit (Math/cos angle)
        {:keys [edge-faces face-data]} (build-face-adjacency mesh)]
    (into #{}
      (keep (fn [[ek fi-list]]
              (when (= 2 (count fi-list))
                (let [n0 (m/normalize (:normal (nth face-data (first fi-list))))
                      n1 (m/normalize (:normal (nth face-data (second fi-list))))
                      cos-angle (m/dot n0 n1)]
                  (when (< cos-angle cos-limit)
                    ek)))))
      edge-faces)))
