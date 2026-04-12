(ns eido.path.warp
  "Geometric coordinate warps: twist, fisheye, bulge, bend, and wave.
  Each warp remaps point positions through a spatial transformation."
  (:require
    [eido.text :as text]))

;; --- warp functions ---

(defn- wave-warp [x y {:keys [axis amplitude wavelength]
                        :or {axis :y amplitude 10 wavelength 100}}]
  (let [amp (double amplitude)
        wl  (double wavelength)]
    (case axis
      :y [x (+ y (* amp (Math/sin (* 2.0 Math/PI (/ x wl)))))]
      :x [(+ x (* amp (Math/sin (* 2.0 Math/PI (/ y wl))))) y])))

(defn- twist-warp [x y {:keys [center amount] :or {center [0 0] amount 1.0}}]
  (let [[cx cy] center
        dx (- (double x) (double cx))
        dy (- (double y) (double cy))
        dist (Math/sqrt (+ (* dx dx) (* dy dy)))
        angle (* (double amount) dist 0.01)
        cos-a (Math/cos angle)
        sin-a (Math/sin angle)]
    [(+ cx (- (* dx cos-a) (* dy sin-a)))
     (+ cy (+ (* dx sin-a) (* dy cos-a)))]))

(defn- fisheye-warp [x y {:keys [center strength radius]
                           :or {center [0 0] strength 0.5 radius 100}}]
  (let [[cx cy] center
        dx (- (double x) (double cx))
        dy (- (double y) (double cy))
        dist (Math/sqrt (+ (* dx dx) (* dy dy)))
        r (double radius)
        s (double strength)]
    (if (or (zero? dist) (> dist r))
      [x y]
      (let [factor (+ 1.0 (* s (- 1.0 (/ dist r))))]
        [(+ cx (* dx factor))
         (+ cy (* dy factor))]))))

(defn- bulge-warp [x y {:keys [center radius strength]
                         :or {center [0 0] radius 100 strength 0.5}}]
  (let [[cx cy] center
        dx (- (double x) (double cx))
        dy (- (double y) (double cy))
        dist (Math/sqrt (+ (* dx dx) (* dy dy)))
        r (double radius)
        s (double strength)]
    (if (or (zero? dist) (> dist r))
      [x y]
      (let [t (/ dist r)
            factor (+ 1.0 (* s (- 1.0 (* t t))))]
        [(+ cx (* dx factor))
         (+ cy (* dy factor))]))))

(defn- bend-warp [x y {:keys [amount center]
                        :or {amount 0.01 center [0 0]}}]
  (let [[cx cy] center
        dx (- (double x) (double cx))
        dy (- (double y) (double cy))
        amt (double amount)
        angle (* dx amt)
        r (+ (/ 1.0 (max 0.001 (Math/abs amt))) dy)]
    [(+ cx (* r (Math/sin angle)))
     (+ cy (- (* r (Math/cos angle)) (/ 1.0 (max 0.001 (Math/abs amt)))))]))

(defn- apply-warp-fn
  "Returns the warp function for a given warp spec."
  [spec]
  (case (:type spec)
    :wave    (fn [x y] (wave-warp x y spec))
    :twist   (fn [x y] (twist-warp x y spec))
    :fisheye (fn [x y] (fisheye-warp x y spec))
    :bulge   (fn [x y] (bulge-warp x y spec))
    :bend    (fn [x y] (bend-warp x y spec))
    (throw (ex-info "Unknown warp type"
                    {:type (:type spec)
                     :valid #{:wave :twist :fisheye :bulge :bend}}))))

;; --- command warping ---

(defn- warp-point [warp-fn [x y]]
  (let [[nx ny] (warp-fn (double x) (double y))]
    [nx ny]))

(defn warp-commands
  "Applies a warp transformation to path commands."
  [commands spec]
  (let [wf (apply-warp-fn spec)]
    (mapv (fn [[cmd & args :as c]]
            (case cmd
              :move-to  [:move-to (warp-point wf (first args))]
              :line-to  [:line-to (warp-point wf (first args))]
              :curve-to [:curve-to (warp-point wf (first args))
                                   (warp-point wf (second args))
                                   (warp-point wf (nth args 2))]
              :quad-to  [:quad-to (warp-point wf (first args))
                                  (warp-point wf (second args))]
              :close    [:close]))
          commands)))

;; --- recursive group warping ---

(defn- subdivide-rect-edges
  "Subdivides a rectangle's edges into n segments per side for smoother
  warping. Returns a vector of path commands (move-to + line-to + close)."
  [x y w h n]
  (let [x (double x) y (double y)
        w (double w) h (double h)
        n (double n)
        top    (for [i (range (inc (int n)))]
                 [(+ x (* w (/ i n))) y])
        right  (for [i (range 1 (inc (int n)))]
                 [(+ x w) (+ y (* h (/ i n)))])
        bottom (for [i (range (dec (int n)) -1 -1)]
                 [(+ x (* w (/ i n))) (+ y h)])
        left   (for [i (range (dec (int n)) 0 -1)]
                 [x (+ y (* h (/ i n)))])]
    (into [[:move-to (first top)]]
          (concat
            (mapv (fn [p] [:line-to p]) (rest top))
            (mapv (fn [p] [:line-to p]) right)
            (mapv (fn [p] [:line-to p]) bottom)
            (mapv (fn [p] [:line-to p]) left)
            [[:close]]))))

(defn shape->path-commands
  "Converts a primitive shape node to path commands."
  [node]
  (case (:node/type node)
    :shape/rect (let [[x y] (:rect/xy node)
                      [w h] (:rect/size node)]
                  (subdivide-rect-edges x y w h 20))
    :shape/circle (let [[cx cy] (:circle/center node)
                        r (double (:circle/radius node))
                        k (* r 0.5522847498)] ;; kappa for cubic approx
                    [[:move-to [(+ cx r) cy]]
                     [:curve-to [(+ cx r) (+ cy k)] [(+ cx k) (+ cy r)] [cx (+ cy r)]]
                     [:curve-to [(- cx k) (+ cy r)] [(- cx r) (+ cy k)] [(- cx r) cy]]
                     [:curve-to [(- cx r) (- cy k)] [(- cx k) (- cy r)] [cx (- cy r)]]
                     [:curve-to [(+ cx k) (- cy r)] [(+ cx r) (- cy k)] [(+ cx r) cy]]
                     [:close]])
    :shape/ellipse (let [[cx cy] (:ellipse/center node)
                         rx (double (:ellipse/rx node))
                         ry (double (:ellipse/ry node))
                         kx (* rx 0.5522847498)
                         ky (* ry 0.5522847498)]
                     [[:move-to [(+ cx rx) cy]]
                      [:curve-to [(+ cx rx) (+ cy ky)] [(+ cx kx) (+ cy ry)] [cx (+ cy ry)]]
                      [:curve-to [(- cx kx) (+ cy ry)] [(- cx rx) (+ cy ky)] [(- cx rx) cy]]
                      [:curve-to [(- cx rx) (- cy ky)] [(- cx kx) (- cy ry)] [cx (- cy ry)]]
                      [:curve-to [(+ cx kx) (- cy ry)] [(+ cx rx) (- cy ky)] [(+ cx rx) cy]]
                      [:close]])
    :shape/line (let [[x1 y1] (:line/from node)
                      [x2 y2] (:line/to node)]
                  [[:move-to [x1 y1]] [:line-to [x2 y2]]])
    :shape/arc (let [[cx cy] (:arc/center node)
                     rx (double (:arc/rx node))
                     ry (double (:arc/ry node))
                     kx (* rx 0.5522847498)
                     ky (* ry 0.5522847498)]
                 ;; Approximate as full ellipse; arc clipping is not
                 ;; supported for path-based transforms
                 [[:move-to [(+ cx rx) cy]]
                  [:curve-to [(+ cx rx) (+ cy ky)] [(+ cx kx) (+ cy ry)] [cx (+ cy ry)]]
                  [:curve-to [(- cx kx) (+ cy ry)] [(- cx rx) (+ cy ky)] [(- cx rx) cy]]
                  [:curve-to [(- cx rx) (- cy ky)] [(- cx kx) (- cy ry)] [cx (- cy ry)]]
                  [:curve-to [(+ cx kx) (- cy ry)] [(+ cx rx) (- cy ky)] [(+ cx rx) cy]]
                  [:close]])
    nil))

(defn warp-node
  "Recursively warps a node tree. Converts primitives to paths, then warps."
  [node spec]
  (case (:node/type node)
    :shape/path
    (let [;; Subdivide curves for smoother warping
          flat (text/flatten-commands
                 (mapv (fn [[cmd & args :as c]]
                         (case cmd
                           :move-to  [:move-to [(double ((first args) 0))
                                                (double ((first args) 1))]]
                           :line-to  [:line-to [(double ((first args) 0))
                                                (double ((first args) 1))]]
                           :curve-to (let [[c1 c2 pt] args]
                                       [:curve-to [(double (c1 0)) (double (c1 1))]
                                                  [(double (c2 0)) (double (c2 1))]
                                                  [(double (pt 0)) (double (pt 1))]])
                           :quad-to  (let [[cp pt] args]
                                       [:quad-to [(double (cp 0)) (double (cp 1))]
                                                 [(double (pt 0)) (double (pt 1))]])
                           c))
                       (:path/commands node))
                 2.0)]
      (assoc node :path/commands (warp-commands flat spec)))

    (:shape/rect :shape/circle :shape/ellipse :shape/line)
    (when-let [cmds (shape->path-commands node)]
      (-> node
          (assoc :node/type :shape/path)
          (assoc :path/commands (warp-commands cmds spec))
          (dissoc :rect/xy :rect/size :rect/corner-radius
                  :circle/center :circle/radius
                  :ellipse/center :ellipse/rx :ellipse/ry
                  :line/from :line/to)))

    :group
    (update node :group/children #(mapv (fn [c] (warp-node c spec)) %))

    ;; Unknown node types pass through
    node))

(comment
  (warp-commands
    [[:move-to [0.0 50.0]] [:line-to [100.0 50.0]] [:line-to [200.0 50.0]]]
    {:type :wave :axis :y :amplitude 20 :wavelength 100})
  )
