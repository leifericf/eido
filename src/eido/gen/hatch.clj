(ns eido.gen.hatch
  "Cross-hatch line patterns for pen-plotter-style fills.
  Generates parallel lines at arbitrary angles with multi-layer support.")

;; --- line generation ---

(defn hatch-lines
  "Generates hatch line segments [x1 y1 x2 y2] within a bounding box.
  bounds: [x y w h]. opts: :angle (45), :spacing (5)."
  [bounds {:keys [angle spacing] :or {angle 45 spacing 5}}]
  (let [[bx by bw bh] bounds
        angle-rad (* (double angle) (/ Math/PI 180.0))
        cos-a     (Math/cos angle-rad)
        sin-a     (Math/sin angle-rad)
        ;; Diagonal of bounding box — ensures full coverage at any angle
        diag      (Math/sqrt (+ (* bw bw) (* bh bh)))
        cx        (+ bx (/ bw 2.0))
        cy        (+ by (/ bh 2.0))
        spacing   (double spacing)
        n         (int (Math/ceil (/ diag spacing)))]
    (into []
          (for [i (range (- n) (inc n))
                :let [offset (* i spacing)
                      ;; Line perpendicular to angle direction, offset along normal
                      ;; Line runs along the angle direction
                      px (+ cx (* offset (- sin-a)))
                      py (+ cy (* offset cos-a))
                      x1 (- px (* diag cos-a))
                      y1 (- py (* diag sin-a))
                      x2 (+ px (* diag cos-a))
                      y2 (+ py (* diag sin-a))]]
            [x1 y1 x2 y2]))))

(defn hatch-fill->nodes
  "Converts a hatch fill spec to scene path nodes (lines).
  bounds: [x y w h]. Each line becomes a path node with the specified stroke.
  Spec keys: :hatch/angle, :hatch/spacing, :hatch/stroke-width,
             :hatch/color, :hatch/layers [{:angle :spacing} ...]."
  [bounds spec]
  (let [stroke-w (get spec :hatch/stroke-width 1)
        color    (get spec :hatch/color [:color/rgb 0 0 0])
        layers   (or (:hatch/layers spec)
                     [{:angle (get spec :hatch/angle 45)
                       :spacing (get spec :hatch/spacing 5)}])]
    (into []
          (mapcat
            (fn [{:keys [angle spacing]}]
              (let [lines (hatch-lines bounds
                            {:angle angle :spacing (or spacing 5)})]
                (mapv (fn [[x1 y1 x2 y2]]
                        {:node/type     :shape/path
                         :path/commands [[:move-to [x1 y1]]
                                         [:line-to [x2 y2]]]
                         :style/stroke  {:color color :width stroke-w}})
                      lines))))
          layers)))

(comment
  (hatch-lines [0 0 100 100] {:angle 45 :spacing 10})
  (hatch-fill->nodes [0 0 100 100] {:hatch/angle 45 :hatch/spacing 8 :hatch/stroke-width 1})
  )
