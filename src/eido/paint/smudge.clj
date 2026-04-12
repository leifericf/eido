(ns ^{:stability :provisional} eido.paint.smudge
  "Smudge and color pickup for the paint engine.

  Maintains a persistent pickup color that mixes with the surface
  as the brush moves. Two modes:
    :smear — drags picked-up color forward along the stroke
    :dull  — samples local color and mutes it under the current dab")

;; --- smudge state ---

(defn make-smudge-state
  "Creates initial smudge state from a smudge spec.
  Returns a mutable atom containing smudge state."
  [smudge-spec brush-color]
  (atom {:pickup-r (/ (double (:r brush-color)) 255.0)
         :pickup-g (/ (double (:g brush-color)) 255.0)
         :pickup-b (/ (double (:b brush-color)) 255.0)
         :mode     (get smudge-spec :smudge/mode :smear)
         :amount   (double (get smudge-spec :smudge/amount 0.3))
         :length   (double (get smudge-spec :smudge/length 0.7))}))

;; --- color sampling ---

(defn sample-surface-color
  "Samples the average color in a small neighborhood around (px, py).
  Returns [r g b] as premultiplied floats, or nil if area is empty."
  [surface ^long px ^long py ^long sample-radius]
  (let [ts    (long (get surface :surface/tiles-size 64))
        sw    (long (:surface/width surface))
        sh    (long (:surface/height surface))
        x0    (max 0 (- px sample-radius))
        y0    (max 0 (- py sample-radius))
        x1    (min sw (+ px sample-radius 1))
        y1    (min sh (+ py sample-radius 1))
        get-px (requiring-resolve 'eido.paint.surface/get-pixel)
        acc    (double-array 4)  ;; [r g b a]
        cnt    (atom 0)]
    (loop [sy y0]
      (when (< sy y1)
        (loop [sx x0]
          (when (< sx x1)
            (let [[pr pg pb pa] (get-px surface sx sy)]
              (aset acc 0 (+ (aget acc 0) (double pr)))
              (aset acc 1 (+ (aget acc 1) (double pg)))
              (aset acc 2 (+ (aget acc 2) (double pb)))
              (aset acc 3 (+ (aget acc 3) (double pa)))
              (swap! cnt inc))
            (recur (inc sx))))
        (recur (inc sy))))
    (let [r (aget acc 0) g (aget acc 1) b (aget acc 2) a (aget acc 3)
          cnt @cnt]
      (when (and (pos? cnt) (> a 0.001))
        (let [inv-a (/ 1.0 a)]
          ;; Un-premultiply to get straight RGB
          [(* r inv-a) (* g inv-a) (* b inv-a)])))))

;; --- smudge update ---

(defn update-smudge!
  "Updates the smudge state by mixing with a sampled surface color.
  Returns the mixed color to use for the current dab as [r g b] in 0-1 range."
  [smudge-state surface px py brush-color]
  (let [state @smudge-state
        amount (:amount state)
        length (:length state)
        mode   (:mode state)
        ;; Sample surface color
        sampled (sample-surface-color surface px py 2)
        ;; Current pickup color
        pr (:pickup-r state)
        pg (:pickup-g state)
        pb (:pickup-b state)
        ;; Brush color (0-1 range)
        br (/ (double (:r brush-color)) 255.0)
        bg (/ (double (:g brush-color)) 255.0)
        bb (/ (double (:b brush-color)) 255.0)]
    (if sampled
      (let [[sr sg sb] sampled
            ;; Mix pickup with sampled color
            new-pr (+ (* length pr) (* (- 1.0 length) sr))
            new-pg (+ (* length pg) (* (- 1.0 length) sg))
            new-pb (+ (* length pb) (* (- 1.0 length) sb))
            ;; Mix pickup with brush color based on amount
            out-r (+ (* amount new-pr) (* (- 1.0 amount) br))
            out-g (+ (* amount new-pg) (* (- 1.0 amount) bg))
            out-b (+ (* amount new-pb) (* (- 1.0 amount) bb))]
        ;; Update state
        (swap! smudge-state assoc
          :pickup-r new-pr :pickup-g new-pg :pickup-b new-pb)
        [out-r out-g out-b])
      ;; No surface color — use mix of pickup and brush color
      (let [out-r (+ (* amount pr) (* (- 1.0 amount) br))
            out-g (+ (* amount pg) (* (- 1.0 amount) bg))
            out-b (+ (* amount pb) (* (- 1.0 amount) bb))]
        [out-r out-g out-b]))))

(comment
  (def ss (make-smudge-state {:smudge/mode :smear :smudge/amount 0.5 :smudge/length 0.7}
                              {:r 200 :g 50 :b 30 :a 1.0}))
  @ss)
