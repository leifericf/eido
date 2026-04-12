(ns ^{:stability :provisional} eido.paint
  "Procedural paint engine for Eido.

  Provides declarative brush specifications, stroke constructors, and
  paint surface configuration. Integrates with the standard Eido
  rendering pipeline — paint surfaces appear as scene nodes alongside
  shapes, groups, and generators.

  Three entry points:
    1. Standalone paint surface — {:node/type :paint/surface ...}
    2. Paint parameters on paths — {:paint/brush :chalk ...} on any path
    3. Group modifier — {:paint/surface {...}} on a group for shared surfaces"
  (:require
    [eido.color :as color]
    [eido.paint.kernel :as kernel]
    [eido.paint.smudge :as smudge]
    [eido.paint.stroke :as stroke]
    [eido.paint.surface :as surface]
    [eido.paint.tip :as tip]
    [eido.paint.wet :as wet])
  (:import
    [java.util Random]))

;; --- brush presets ---

(def presets
  "Named brush presets. Each is a brush spec map."
  {:pencil   {:brush/type  :brush/dab
              :brush/tip   {:tip/shape :ellipse :tip/hardness 0.85 :tip/aspect 1.0}
              :brush/paint {:paint/opacity 0.6 :paint/flow 0.9 :paint/spacing 0.06}
              :brush/radius 1.5}

   :marker   {:brush/type  :brush/dab
              :brush/tip   {:tip/shape :ellipse :tip/hardness 0.95 :tip/aspect 1.0}
              :brush/paint {:paint/opacity 0.5 :paint/flow 1.0 :paint/spacing 0.04}
              :brush/radius 5.0}

   :airbrush {:brush/type  :brush/dab
              :brush/tip   {:tip/shape :ellipse :tip/hardness 0.15 :tip/aspect 1.0}
              :brush/paint {:paint/opacity 0.08 :paint/flow 0.7 :paint/spacing 0.03}
              :brush/radius 25.0}

   :chalk    {:brush/type  :brush/dab
              :brush/tip   {:tip/shape :ellipse :tip/hardness 0.5 :tip/aspect 1.0}
              :brush/paint {:paint/opacity 0.12 :paint/flow 0.8 :paint/spacing 0.05}
              :brush/jitter {:jitter/position 0.12 :jitter/opacity 0.25
                             :jitter/size 0.1 :jitter/angle 0.15}
              :brush/radius 6.0}

   :ink      {:brush/type  :brush/dab
              :brush/tip   {:tip/shape :ellipse :tip/hardness 0.8 :tip/aspect 1.0}
              :brush/paint {:paint/opacity 0.7 :paint/flow 1.0 :paint/spacing 0.04}
              :brush/radius 2.0}

   :oil      {:brush/type   :brush/dab
              :brush/tip    {:tip/shape :ellipse :tip/hardness 0.6 :tip/aspect 1.0}
              :brush/paint  {:paint/opacity 0.4 :paint/flow 0.85 :paint/spacing 0.06}
              :brush/smudge {:smudge/mode :smear :smudge/amount 0.45 :smudge/length 0.7}
              :brush/jitter {:jitter/position 0.08 :jitter/opacity 0.15
                             :jitter/size 0.08 :jitter/angle 0.05}
              :brush/radius 10.0}

   :watercolor {:brush/type  :brush/dab
                :brush/tip   {:tip/shape :ellipse :tip/hardness 0.3 :tip/aspect 1.0}
                :brush/paint {:paint/opacity 0.06 :paint/flow 0.6 :paint/spacing 0.04}
                :brush/wet   {:wet/enabled true :wet/deposit 0.3
                              :wet/diffusion 0.25 :wet/diffusion-steps 6
                              :wet/edge-darken 0.2}
                :brush/jitter {:jitter/position 0.1 :jitter/opacity 0.2
                               :jitter/size 0.12}
                :brush/radius 20.0}

   :pastel   {:brush/type  :brush/dab
              :brush/tip   {:tip/shape :ellipse :tip/hardness 0.4 :tip/aspect 1.2}
              :brush/paint {:paint/opacity 0.1 :paint/flow 0.75 :paint/spacing 0.05}
              :brush/jitter {:jitter/position 0.15 :jitter/opacity 0.3
                             :jitter/size 0.12 :jitter/angle 0.2}
              :brush/radius 8.0}

   ;; --- dry media ---

   :graphite  {:brush/type  :brush/dab
               :brush/tip   {:tip/shape :ellipse :tip/hardness 0.9 :tip/aspect 1.0}
               :brush/paint {:paint/opacity 0.3 :paint/flow 0.85 :paint/spacing 0.03}
               :brush/grain {:grain/type :fbm :grain/scale 0.05 :grain/contrast 0.2}
               :brush/radius 1.2}

   :charcoal  {:brush/type  :brush/dab
               :brush/tip   {:tip/shape :ellipse :tip/hardness 0.25 :tip/aspect 1.3}
               :brush/paint {:paint/opacity 0.15 :paint/flow 0.7 :paint/spacing 0.05}
               :brush/grain {:grain/type :turbulence :grain/scale 0.12 :grain/contrast 0.6}
               :brush/jitter {:jitter/opacity 0.3 :jitter/size 0.15 :jitter/position 0.1}
               :brush/radius 8.0}

   :conte     {:brush/type  :brush/dab
               :brush/tip   {:tip/shape :rect :tip/hardness 0.5 :tip/aspect 2.0}
               :brush/paint {:paint/opacity 0.12 :paint/flow 0.8 :paint/spacing 0.05}
               :brush/grain {:grain/type :fiber :grain/scale 0.1 :grain/contrast 0.4
                             :grain/stretch 3.0}
               :brush/jitter {:jitter/opacity 0.2 :jitter/angle 0.1}
               :brush/radius 5.0}

   :soft-pastel {:brush/type  :brush/dab
                 :brush/tip   {:tip/shape :ellipse :tip/hardness 0.3 :tip/aspect 1.3}
                 :brush/paint {:paint/opacity 0.08 :paint/flow 0.65 :paint/spacing 0.05}
                 :brush/grain {:grain/type :canvas :grain/scale 0.06 :grain/contrast 0.3}
                 :brush/jitter {:jitter/position 0.2 :jitter/opacity 0.35
                                :jitter/size 0.15 :jitter/angle 0.25}
                 :brush/radius 10.0}

   :crayon    {:brush/type  :brush/dab
               :brush/tip   {:tip/shape :ellipse :tip/hardness 0.55 :tip/aspect 1.1}
               :brush/paint {:paint/opacity 0.2 :paint/flow 0.8 :paint/spacing 0.04}
               :brush/grain {:grain/type :fbm :grain/scale 0.08 :grain/contrast 0.45}
               :brush/jitter {:jitter/position 0.08 :jitter/opacity 0.15 :jitter/size 0.08}
               :brush/radius 4.0}

   ;; --- ink and pen ---

   :ballpoint {:brush/type  :brush/dab
               :brush/tip   {:tip/shape :circle :tip/hardness 0.98}
               :brush/paint {:paint/opacity 0.7 :paint/flow 0.95 :paint/spacing 0.02}
               :brush/radius 0.8}

   :felt-tip  {:brush/type  :brush/dab
               :brush/tip   {:tip/shape :ellipse :tip/hardness 0.85 :tip/aspect 1.3}
               :brush/paint {:paint/opacity 0.6 :paint/flow 0.9 :paint/spacing 0.03}
               :brush/grain {:grain/type :fiber :grain/scale 0.08 :grain/contrast 0.15}
               :brush/radius 2.0}

   :fountain-pen {:brush/type  :brush/dab
                  :brush/tip   {:tip/shape :line :tip/hardness 0.9 :tip/aspect 3.0}
                  :brush/paint {:paint/opacity 0.65 :paint/flow 0.95 :paint/spacing 0.02}
                  :brush/radius 1.5}

   :brush-pen {:brush/type  :brush/dab
               :brush/tip   {:tip/shape :ellipse :tip/hardness 0.75 :tip/aspect 1.8}
               :brush/paint {:paint/opacity 0.6 :paint/flow 0.9 :paint/spacing 0.03}
               :brush/jitter {:jitter/angle 0.08}
               :brush/radius 4.0}

   :technical-pen {:brush/type  :brush/dab
                   :brush/tip   {:tip/shape :circle :tip/hardness 0.99}
                   :brush/paint {:paint/opacity 0.85 :paint/flow 1.0 :paint/spacing 0.015}
                   :brush/radius 0.5}

   ;; --- markers ---

   :flat-marker {:brush/type  :brush/dab
                 :brush/tip   {:tip/shape :rect :tip/hardness 0.92 :tip/aspect 2.5
                               :tip/corner-radius 0.1}
                 :brush/paint {:paint/opacity 0.35 :paint/flow 1.0 :paint/spacing 0.03
                               :paint/blend :glazed}
                 :brush/jitter {:jitter/angle 0.02}
                 :brush/radius 6.0}

   :chisel-marker {:brush/type  :brush/dab
                   :brush/tip   {:tip/shape :rect :tip/hardness 0.9 :tip/aspect 3.0}
                   :brush/paint {:paint/opacity 0.3 :paint/flow 1.0 :paint/spacing 0.03
                                 :paint/blend :glazed}
                   :brush/radius 5.0}

   :highlighter {:brush/type  :brush/dab
                 :brush/tip   {:tip/shape :rect :tip/hardness 0.85 :tip/aspect 4.0
                               :tip/corner-radius 0.15}
                 :brush/paint {:paint/opacity 0.15 :paint/flow 1.0 :paint/spacing 0.03
                               :paint/blend :glazed}
                 :brush/radius 8.0}

   ;; --- wet paint ---

   :gouache   {:brush/type  :brush/dab
               :brush/tip   {:tip/shape :ellipse :tip/hardness 0.55 :tip/aspect 1.0}
               :brush/paint {:paint/opacity 0.5 :paint/flow 0.85 :paint/spacing 0.05
                             :paint/blend :opaque}
               :brush/jitter {:jitter/opacity 0.1 :jitter/size 0.05}
               :brush/radius 12.0}

   :acrylic-wash {:brush/type  :brush/dab
                  :brush/tip   {:tip/shape :ellipse :tip/hardness 0.35 :tip/aspect 1.0}
                  :brush/paint {:paint/opacity 0.08 :paint/flow 0.65 :paint/spacing 0.04}
                  :brush/wet   {:wet/enabled true :wet/deposit 0.2
                                :wet/diffusion 0.15 :wet/diffusion-steps 3
                                :wet/edge-darken 0.1}
                  :brush/jitter {:jitter/position 0.08 :jitter/opacity 0.15}
                  :brush/radius 18.0}

   ;; --- thick paint ---

   :acrylic   {:brush/type  :brush/dab
               :brush/tip   {:tip/shape :ellipse :tip/hardness 0.65 :tip/aspect 1.0}
               :brush/paint {:paint/opacity 0.55 :paint/flow 0.9 :paint/spacing 0.05
                             :paint/blend :opaque}
               :brush/smudge {:smudge/mode :smear :smudge/amount 0.3 :smudge/length 0.5}
               :brush/jitter {:jitter/position 0.06 :jitter/opacity 0.1 :jitter/size 0.06}
               :brush/radius 10.0}

   :impasto   {:brush/type  :brush/dab
               :brush/tip   {:tip/shape :ellipse :tip/hardness 0.5 :tip/aspect 1.2}
               :brush/paint {:paint/opacity 0.7 :paint/flow 0.9 :paint/spacing 0.06
                             :paint/blend :opaque}
               :brush/impasto {:impasto/height 0.6}
               :brush/smudge {:smudge/mode :smear :smudge/amount 0.5 :smudge/length 0.8}
               :brush/jitter {:jitter/position 0.08 :jitter/opacity 0.12
                              :jitter/size 0.1 :jitter/angle 0.05}
               :brush/radius 14.0}

   :tempera   {:brush/type  :brush/dab
               :brush/tip   {:tip/shape :ellipse :tip/hardness 0.6 :tip/aspect 1.0}
               :brush/paint {:paint/opacity 0.45 :paint/flow 0.85 :paint/spacing 0.05}
               :brush/jitter {:jitter/opacity 0.1 :jitter/size 0.05}
               :brush/radius 10.0}

   ;; --- tools ---

   :smudge-tool {:brush/type  :brush/dab
                 :brush/tip   {:tip/shape :ellipse :tip/hardness 0.4}
                 :brush/paint {:paint/opacity 0.0 :paint/flow 0.0 :paint/spacing 0.04}
                 :brush/smudge {:smudge/mode :smear :smudge/amount 0.85
                                :smudge/length 1.0}
                 :brush/radius 12.0}

   :palette-knife {:brush/type  :brush/dab
                   :brush/tip   {:tip/shape :rect :tip/hardness 0.7 :tip/aspect 4.0}
                   :brush/paint {:paint/opacity 0.15 :paint/flow 0.3 :paint/spacing 0.06}
                   :brush/smudge {:smudge/mode :smear :smudge/amount 0.9
                                  :smudge/length 0.95}
                   :brush/impasto {:impasto/height 0.4}
                   :brush/jitter {:jitter/angle 0.05 :jitter/opacity 0.1}
                   :brush/radius 18.0}

   :eraser    {:brush/type  :brush/dab
               :brush/tip   {:tip/shape :ellipse :tip/hardness 0.6}
               :brush/paint {:paint/opacity 0.8 :paint/flow 1.0 :paint/spacing 0.04
                             :paint/blend :erase}
               :brush/radius 8.0}

   :blender   {:brush/type  :brush/dab
               :brush/tip   {:tip/shape :ellipse :tip/hardness 0.3}
               :brush/paint {:paint/opacity 0.0 :paint/flow 0.0 :paint/spacing 0.04}
               :brush/smudge {:smudge/mode :smear :smudge/amount 0.6
                              :smudge/length 0.8}
               :brush/radius 10.0}

   ;; --- effects ---

   :spray-paint {:brush/type  :brush/dab
                 :brush/tip   {:tip/shape :ellipse :tip/hardness 0.1 :tip/aspect 1.0}
                 :brush/paint {:paint/opacity 0.04 :paint/flow 0.5 :paint/spacing 0.02}
                 :brush/jitter {:jitter/position 0.4 :jitter/opacity 0.3
                                :jitter/size 0.3}
                 :brush/radius 30.0}

   :splatter  {:brush/type  :brush/dab
               :brush/tip   {:tip/shape :ellipse :tip/hardness 0.6 :tip/aspect 1.0}
               :brush/paint {:paint/opacity 0.4 :paint/flow 0.8 :paint/spacing 0.15}
               :brush/jitter {:jitter/position 0.5 :jitter/opacity 0.4
                              :jitter/size 0.5 :jitter/angle 0.5}
               :brush/radius 3.0}

   ;; --- deform tools ---

   :push      {:brush/type   :brush/deform
               :brush/tip    {:tip/shape :ellipse :tip/hardness 0.5}
               :brush/paint  {:paint/opacity 1.0 :paint/spacing 0.08}
               :brush/deform {:deform/mode :push :deform/strength 0.6}
               :brush/radius 20.0}

   :swirl     {:brush/type   :brush/deform
               :brush/tip    {:tip/shape :ellipse :tip/hardness 0.4}
               :brush/paint  {:paint/opacity 1.0 :paint/spacing 0.08}
               :brush/deform {:deform/mode :swirl :deform/strength 0.5}
               :brush/radius 25.0}

   :blur-tool {:brush/type   :brush/deform
               :brush/tip    {:tip/shape :ellipse :tip/hardness 0.3}
               :brush/paint  {:paint/opacity 1.0 :paint/spacing 0.06}
               :brush/deform {:deform/mode :blur :deform/strength 0.6}
               :brush/radius 15.0}

   :sharpen-tool {:brush/type   :brush/deform
                  :brush/tip    {:tip/shape :ellipse :tip/hardness 0.4}
                  :brush/paint  {:paint/opacity 1.0 :paint/spacing 0.06}
                  :brush/deform {:deform/mode :sharpen :deform/strength 0.4}
                  :brush/radius 12.0}})

;; --- brush resolution ---

(defn resolve-brush
  "Resolves a brush reference to a full brush spec.
  Accepts a keyword (preset name), a spec map, or nil (returns default)."
  [brush]
  (cond
    (keyword? brush) (get presets brush (:pencil presets))
    (map? brush)     brush
    :else            (:pencil presets)))

;; --- constructors ---

(defn brush
  "Returns a brush spec, optionally merging overrides onto a preset.
  (brush :chalk)
  (brush :chalk {:brush/paint {:paint/opacity 0.2}})
  (brush {:brush/type :brush/dab ...})"
  ([spec] (resolve-brush spec))
  ([preset overrides]
   (merge-with merge (resolve-brush preset) overrides)))

;; --- internal: render strokes onto a surface ---

(defn- brush-spacing-px
  "Computes spacing in pixels from brush spec and radius."
  ^double [brush ^double radius]
  (let [spacing-ratio (get-in brush [:brush/paint :paint/spacing] 0.06)]
    (max 1.0 (* (double spacing-ratio) radius 2.0))))

(defn render-stroke!
  "Renders a single stroke onto a surface.
  stroke-desc: {:stroke/brush :stroke/color :stroke/points :stroke/seed}
    or path-based: {:paint/brush :paint/color :path/commands :paint/pressure}
  Returns nil (mutates surface).
  substrate-spec: optional substrate from the surface config."
  ([surface stroke-desc] (render-stroke! surface stroke-desc nil))
  ([surface stroke-desc substrate-spec]
  (let [brush-spec (resolve-brush (or (:stroke/brush stroke-desc)
                                      (:paint/brush stroke-desc)))
        raw-color  (or (:paint/color stroke-desc)
                       (:stroke/color stroke-desc)
                       (:style/fill stroke-desc)
                       [:color/rgb 0 0 0])
        resolved   (color/resolve-color raw-color)
        radius     (double (or (:paint/radius stroke-desc)
                               (:stroke/radius stroke-desc)
                               (get-in brush-spec [:brush/radius])
                               8.0))
        tip-spec   (:brush/tip brush-spec)
        hardness   (get tip-spec :tip/hardness 0.7)
        aspect     (get tip-spec :tip/aspect 1.0)
        grain-spec    (:brush/grain brush-spec)
        bristle-spec  (:brush/bristles brush-spec)
        smudge-spec   (:brush/smudge brush-spec)
        wet-spec      (:brush/wet brush-spec)
        impasto-spec  (:brush/impasto brush-spec)
        jitter-spec   (:brush/jitter brush-spec)
        ;; Derive seed from stroke content if not explicit, so each
        ;; stroke gets unique jitter without requiring manual seeds
        stroke-seed   (long (or (:paint/seed stroke-desc)
                                (:stroke/seed stroke-desc)
                                (hash stroke-desc)))
        opacity    (get-in brush-spec [:brush/paint :paint/opacity] 0.5)
        spacing-px (brush-spacing-px brush-spec radius)
        ;; Get points — either explicit or from path commands
        explicit   (or (:paint/points stroke-desc) (:stroke/points stroke-desc))
        path-cmds  (:path/commands stroke-desc)
        {:keys [points pressure]}
        (cond
          explicit
          (stroke/explicit-points->stroke-points explicit)

          path-cmds
          {:points   (stroke/path-commands->points path-cmds 0.5)
           :pressure (or (:paint/pressure stroke-desc)
                         (:stroke/pressure stroke-desc))}

          :else
          {:points [] :pressure nil})
        dabs (stroke/resample-stroke points spacing-px
               {:color    resolved
                :radius   radius
                :hardness hardness
                :opacity  opacity
                :aspect   aspect
                :tip      tip-spec
                :pressure pressure
                :jitter   jitter-spec
                :seed     stroke-seed})
        ;; Initialize smudge state if brush has smudge config
        smudge-state (when smudge-spec
                       (smudge/make-smudge-state smudge-spec resolved))
        ;; Helper: apply smudge color mixing to a dab
        apply-smudge (fn [d]
                       (if smudge-state
                         (let [[mr mg mb] (smudge/update-smudge!
                                            smudge-state surface
                                            (long (:dab/cx d)) (long (:dab/cy d))
                                            resolved)
                               mixed-color {:r (Math/round (* mr 255.0))
                                            :g (Math/round (* mg 255.0))
                                            :b (Math/round (* mb 255.0))
                                            :a (get (:dab/color d) :a 1.0)}]
                           (assoc d :dab/color mixed-color))
                         d))
        blend-mode (get-in brush-spec [:brush/paint :paint/blend] :source-over)
        ;; Helper: add grain/substrate/blend to a dab
        apply-texture (fn [d]
                        (cond-> d
                          grain-spec                     (assoc :dab/grain grain-spec)
                          substrate-spec                 (assoc :dab/substrate substrate-spec)
                          (not= blend-mode :source-over) (assoc :dab/blend blend-mode)))
        ;; Helper: deposit wetness if wet brush
        deposit-wet (fn [d]
                      (when (and wet-spec (:wet/enabled wet-spec true))
                        (let [deposit (double (get wet-spec :wet/deposit 0.3))]
                          (wet/deposit-wetness! surface
                            (long (:dab/cx d)) (long (:dab/cy d))
                            (* deposit (:dab/opacity d))))))
        ;; Helper: deposit height if impasto brush
        deposit-height (fn [d]
                         (when impasto-spec
                           (let [h (double (get impasto-spec :impasto/height 0.3))]
                             (surface/deposit-height! surface
                               (long (:dab/cx d)) (long (:dab/cy d))
                               (* h (:dab/opacity d))))))]
    (if (= :brush/deform (:brush/type brush-spec))
      ;; Deform mode: modify existing pixels
      (let [deform-spec (:brush/deform brush-spec)]
        (doseq [d dabs]
          (kernel/deform-dab! surface
            (assoc d :dab/deform deform-spec))))
      ;; Regular paint mode
      (if bristle-spec
        ;; Bristle mode: emit N sub-dabs per dab
        (doseq [d dabs]
          (let [d (apply-smudge d)
                offsets (tip/bristle-offsets bristle-spec
                          (double (get d :dab/angle 0.0))
                          (hash (get d :dab/cx 0.0)))
                base-r  (double (:dab/radius d))]
            (doseq [{:keys [offset opacity-scale size-scale]} offsets]
              (let [[ox oy] offset
                    sub-dab (-> (assoc d
                                  :dab/cx (+ (:dab/cx d) (* ox base-r))
                                  :dab/cy (+ (:dab/cy d) (* oy base-r))
                                  :dab/radius (* base-r size-scale 0.4)
                                  :dab/opacity (* (:dab/opacity d) opacity-scale))
                                apply-texture)]
                (kernel/rasterize-dab! surface sub-dab)
                (deposit-wet sub-dab)
                (deposit-height sub-dab)))))
        ;; Single-tip mode
        (doseq [d dabs]
          (let [d (-> d apply-smudge apply-texture)]
            (kernel/rasterize-dab! surface d)
            (deposit-wet d)
            (deposit-height d)))))
    ;; Spatter: emit secondary particles for speed-driven effects
    (when-let [spatter-spec (:brush/spatter brush-spec)]
      (let [threshold (double (get spatter-spec :spatter/threshold 0.6))
            density   (double (get spatter-spec :spatter/density 0.3))
            spread    (double (get spatter-spec :spatter/spread 2.0))
            [size-min size-max] (get spatter-spec :spatter/size [0.05 0.3])
            [opa-min opa-max]   (get spatter-spec :spatter/opacity [0.2 0.8])
            mode      (get spatter-spec :spatter/mode :scatter)]
        (doseq [d dabs]
          (let [speed (double (get d :dab/speed 0.0))
                ;; Scale spatter by dab opacity (tracks pressure)
                dab-strength (double (:dab/opacity d))]
            (when (> speed threshold)
              (let [intensity (* (- speed threshold) (/ 1.0 (Math/max 0.01 (- 1.0 threshold))))
                    ;; Fewer particles where stroke is thin/faint
                    n         (int (Math/ceil (* density intensity dab-strength 3.0)))
                    ^Random rng (Random. (unchecked-add
                                           stroke-seed
                                           (long (* (:dab/cx d) 17.0))))]
                (dotimes [_ n]
                  (let [base-angle (double (:dab/angle d))
                        angle (case mode
                                :scatter (+ base-angle (* (- (.nextDouble rng) 0.5) Math/PI))
                                :spray   (+ base-angle (* (.nextGaussian rng) 0.4))
                                (+ base-angle (* (- (.nextDouble rng) 0.5) Math/PI)))
                        ;; Spread scales with dab radius (already pressure-scaled)
                        ;; Squared distribution clusters near stroke
                        dab-r (double (:dab/radius d))
                        dist (* spread dab-r (.nextDouble rng) (.nextDouble rng))
                        px    (+ (:dab/cx d) (* dist (Math/cos angle)))
                        py    (+ (:dab/cy d) (* dist (Math/sin angle)))
                        sz    (+ (double size-min)
                                 (* (.nextDouble rng) (- (double size-max) (double size-min))))
                        ;; Scale particle size to dab radius, min 1.5px
                        pr    (Math/max 1.5 (* sz dab-r))
                        op    (+ (double opa-min)
                                 (* (.nextDouble rng) (- (double opa-max) (double opa-min))))
                        particle (cond-> {:dab/cx px :dab/cy py
                                          :dab/radius pr
                                          :dab/hardness (get tip-spec :tip/hardness 0.3)
                                          :dab/opacity op
                                          :dab/aspect 1.0
                                          :dab/angle (double (:dab/angle d))
                                          :dab/tip tip-spec
                                          :dab/color (:dab/color d)}
                                   grain-spec                     (assoc :dab/grain grain-spec)
                                   substrate-spec                 (assoc :dab/substrate substrate-spec)
                                   (not= blend-mode :source-over) (assoc :dab/blend blend-mode))]
                    (kernel/rasterize-dab! surface particle)))))))))
    ;; Post-stroke: run wet diffusion if configured
    (when (and wet-spec (:wet/enabled wet-spec true))
      (let [iterations (long (get wet-spec :wet/diffusion-steps 4))
            strength   (double (get wet-spec :wet/diffusion 0.2))
            darken     (double (get wet-spec :wet/edge-darken 0.15))]
        (wet/apply-wet-pass! surface iterations strength darken wet-spec))))))

(defn render-strokes!
  "Renders all strokes onto a surface. Returns the surface."
  ([surface strokes] (render-strokes! surface strokes nil))
  ([surface strokes substrate-spec]
   (doseq [s strokes]
     (render-stroke! surface s substrate-spec))
   surface))

;; --- surface creation and compositing ---

(defn make-surface
  "Creates a paint surface from a surface config or size vector."
  [config]
  (let [[w h] (or (:paint/size config) config)]
    (surface/create-surface (long w) (long h))))

(defn compose
  "Composites a painted surface to a BufferedImage."
  [surface]
  (surface/compose-to-image surface))

;; --- convenience constructors ---
;; Following the pattern of eido.scene: helpers that return plain maps.

(defn painted-path
  "Creates a path node with paint parameters.
  points: [[x y] ...] — auto-smoothed via Catmull-Rom, or path commands.
  opts: {:brush :ink, :color [...], :radius 8.0, :pressure [[t p] ...], :opacity 0.5}"
  [points opts]
  (let [cmds (if (and (seq points) (vector? (first points)) (number? (ffirst points)))
               ;; [[x y] ...] — convert to smooth path commands
               (let [pts (vec points)
                     n   (count pts)]
                 (if (<= n 1)
                   (if (seq pts) [[:move-to (first pts)]] [])
                   (into [[:move-to (first pts)]]
                         (mapv (fn [p] [:line-to p]) (rest pts)))))
               ;; Already path commands
               points)]
    (cond-> {:node/type     :shape/path
             :path/commands cmds
             :paint/brush   (or (:brush opts) :pencil)}
      (:color opts)    (assoc :paint/color (:color opts))
      (:radius opts)   (assoc :paint/radius (:radius opts))
      (:pressure opts) (assoc :paint/pressure (:pressure opts))
      (:opacity opts)  (assoc :node/opacity (:opacity opts))
      (:seed opts)     (assoc :paint/seed (:seed opts)))))

(defn paint-surface
  "Creates a paint surface node.
  strokes: vector of stroke descriptors.
  opts: {:size [w h], :substrate {:substrate/tooth 0.4}, :children [nodes]}"
  ([strokes] (paint-surface strokes {}))
  ([strokes opts]
   (cond-> {:node/type      :paint/surface
            :paint/strokes  strokes}
     (:size opts)      (assoc :paint/size (:size opts))
     (:substrate opts) (assoc :paint/surface (:substrate opts))
     (:children opts)  (assoc :paint/children (:children opts)))))

(defn paint-group
  "Creates a group with a shared paint surface.
  children: painted path nodes or generators with paint params.
  opts: {:substrate {:substrate/tooth 0.4}}"
  ([children] (paint-group children {}))
  ([children opts]
   (cond-> {:node/type       :group
            :paint/surface   (or (:substrate opts) {})
            :group/children  (vec children)}
     (:opacity opts) (assoc :node/opacity (:opacity opts)))))

(defn stroke
  "Creates a stroke descriptor for use in :paint/strokes.
  points: [[x y pressure speed tilt-x tilt-y] ...]
  opts: {:brush :chalk, :color [...], :radius 12.0, :seed 42}"
  [points opts]
  (cond-> {:paint/points points
           :paint/brush  (or (:brush opts) :pencil)}
    (:color opts)  (assoc :paint/color (:color opts))
    (:radius opts) (assoc :paint/radius (:radius opts))
    (:seed opts)   (assoc :paint/seed (:seed opts))))

;; --- point generators ---

(defn circle-points
  "Generates stroke points around a circle or arc.
  center: [cx cy].
  opts: {:radius 50, :n 20, :start 0.0, :end (* 2 PI), :pressure 0.8}
  Returns [[x y pressure 0 0 0] ...] suitable for :paint/points."
  ([center] (circle-points center {}))
  ([center opts]
   (let [[cx cy] center
         radius  (double (get opts :radius 50.0))
         n       (long (get opts :n 20))
         start-a (double (get opts :start 0.0))
         end-a   (double (get opts :end (* 2.0 Math/PI)))
         p       (double (get opts :pressure 0.8))]
     (mapv (fn [i]
             (let [t (/ (double i) (double (dec n)))
                   a (+ start-a (* t (- end-a start-a)))]
               [(+ (double cx) (* radius (Math/cos a)))
                (+ (double cy) (* radius (Math/sin a)))
                p 0 0 0]))
           (range n)))))

(defn wave-points
  "Generates stroke points along a wavy line.
  from: [x0 y0].
  opts: {:to [x1 y1], :amplitude 15, :frequency 0.15, :n 20, :pressure 0.7}
  Returns [[x y pressure 0 0 0] ...]."
  ([from] (wave-points from {}))
  ([from opts]
   (let [[x0 y0] from
         [x1 y1] (get opts :to [(+ (double x0) 100) (double y0)])
         n     (long (get opts :n 20))
         amp   (double (get opts :amplitude 15.0))
         freq  (double (get opts :frequency 0.15))
         p     (double (get opts :pressure 0.7))
         dx    (- (double x1) (double x0))
         dy    (- (double y1) (double y0))
         len   (Math/sqrt (+ (* dx dx) (* dy dy)))
         nx    (if (> len 0) (/ (- dy) len) 0.0)
         ny    (if (> len 0) (/ dx len) 0.0)]
     (mapv (fn [i]
             (let [t (/ (double i) (double (dec n)))
                   wave (* amp (Math/sin (* t (double n) freq)))]
               [(+ (double x0) (* t dx) (* wave nx))
                (+ (double y0) (* t dy) (* wave ny))
                p 0 0 0]))
           (range n)))))

(defn line-points
  "Generates stroke points along a straight line.
  from: [x0 y0].
  opts: {:to [x1 y1], :n 15, :pressure 0.8}
  Returns [[x y pressure 0 0 0] ...]."
  ([from] (line-points from {}))
  ([from opts]
   (let [[x0 y0] from
         [x1 y1] (get opts :to [(+ (double x0) 100) (double y0)])
         n (long (get opts :n 15))
         p (double (get opts :pressure 0.8))]
     (mapv (fn [i]
             (let [t (/ (double i) (double (dec n)))]
               [(+ (double x0) (* t (- (double x1) (double x0))))
                (+ (double y0) (* t (- (double y1) (double y0))))
                p 0 0 0]))
           (range n)))))

;; --- area fill helpers ---

(defn fill-rect
  "Generates stroke descriptors to fill a rectangular area with paint.
  bounds: [x y w h].
  opts: {:brush :chalk, :color [...], :radius 8.0, :density 15, :seed 0}
  Returns a vector of stroke descriptors for :paint/strokes."
  ([bounds] (fill-rect bounds {}))
  ([bounds opts]
   (let [[bx by bw bh] bounds
         brush   (or (:brush opts) :chalk)
         color   (or (:color opts) [:color/rgb 0 0 0])
         radius  (double (get opts :radius 8.0))
         density (long (get opts :density 15))
         seed    (long (get opts :seed (hash bounds)))
         rng     (java.util.Random. seed)]
     (mapv (fn [i]
             (let [y (+ (double by) (* (double bh) (.nextDouble rng)))
                   x0 (+ (double bx) (* (double bw) 0.05 (.nextDouble rng)))
                   x1 (+ (double bx) (double bw) (* (double bw) -0.05 (.nextDouble rng)))]
               {:paint/brush brush :paint/color color :paint/radius radius
                :paint/seed (+ seed i)
                :paint/points (line-points [x0 y]
                                {:to [x1 y] :n 12
                                 :pressure (+ 0.4 (* 0.4 (.nextDouble rng)))})}))
           (range density)))))

(defn fill-ellipse
  "Generates stroke descriptors to fill an elliptical area with paint.
  center: [cx cy].
  opts: {:rx 50, :ry 30, :brush :oil, :color [...], :radius 8.0,
         :density 12, :seed 0}
  Returns a vector of stroke descriptors for :paint/strokes."
  ([center] (fill-ellipse center {}))
  ([center opts]
   (let [[cx cy] center
         rx      (double (get opts :rx 50.0))
         ry      (double (get opts :ry (get opts :rx 50.0)))
         brush   (or (:brush opts) :oil)
         color   (or (:color opts) [:color/rgb 0 0 0])
         radius  (double (get opts :radius 8.0))
         density (long (get opts :density 12))
         seed    (long (get opts :seed (hash center)))
         rng     (java.util.Random. seed)]
     (mapv (fn [i]
             (let [a (* 2.0 Math/PI (.nextDouble rng))
                   d (Math/sqrt (.nextDouble rng))
                   px (+ (double cx) (* rx d (Math/cos a)))
                   py (+ (double cy) (* ry d (Math/sin a)))
                   sa (* (.nextGaussian rng) 0.5)
                   len (+ 5.0 (* 12.0 (.nextDouble rng)))]
               {:paint/brush brush :paint/color color :paint/radius radius
                :paint/seed (+ seed i)
                :paint/points (mapv (fn [j]
                                      (let [t (/ (double j) 3.0)]
                                        [(+ px (* len t (Math/cos sa)))
                                         (+ py (* len t (Math/sin sa)))
                                         (+ 0.5 (* 0.4 (Math/sin (* t Math/PI)))) 0 0 0]))
                                    (range 4))}))
           (range density)))))

;; --- pressure helpers ---

(defn auto-pressure
  "Derives a pressure curve from path geometry.
  points: [[x y] ...] — point sequence.
  opts: {:mode :taper (default), :start 0.3, :end 0.2}
  Returns [[t pressure] ...] suitable for :paint/pressure."
  ([points] (auto-pressure points {}))
  ([points opts]
   (let [mode (get opts :mode :taper)
         start-p (double (get opts :start 0.3))
         end-p   (double (get opts :end 0.2))
         n (count points)]
     (if (< n 2)
       [[0.0 1.0]]
       (let [dists (loop [i 1 acc [0.0]]
                     (if (>= i n)
                       acc
                       (let [[x0 y0] (nth points (dec i))
                             [x1 y1] (nth points i)
                             d (Math/sqrt (+ (* (- (double x1) (double x0))
                                                (- (double x1) (double x0)))
                                             (* (- (double y1) (double y0))
                                                (- (double y1) (double y0)))))]
                         (recur (inc i) (conj acc (+ (peek acc) d))))))
             total (double (peek dists))]
         (if (< total 0.001)
           [[0.0 1.0]]
           (case mode
             :taper
             (mapv (fn [d]
                     (let [t (/ (double d) total)
                           mid-p (+ start-p (* (- 1.0 (Math/max start-p end-p))
                                                (Math/sin (* t Math/PI))))]
                       [t (Math/max 0.01 (Math/min 1.0 mid-p))]))
                   dists)

             :curvature
             (mapv (fn [i d]
                     (let [t (/ (double d) total)
                           curv (if (and (> i 0) (< i (dec n)))
                                  (let [[x0 y0] (nth points (dec i))
                                        [x1 y1] (nth points i)
                                        [x2 y2] (nth points (inc i))
                                        a1 (Math/atan2 (- (double y1) (double y0))
                                                       (- (double x1) (double x0)))
                                        a2 (Math/atan2 (- (double y2) (double y1))
                                                       (- (double x2) (double x1)))
                                        da (Math/abs (- a2 a1))]
                                    (Math/min 1.0 (* da 2.0)))
                                  0.5)
                           p (+ 0.3 (* 0.7 curv))]
                       [t (Math/max 0.01 (Math/min 1.0 p))]))
                   (range n) dists)

             [[0.0 1.0] [1.0 1.0]])))))))

(defn stroke-from-path
  "Creates a stroke descriptor from path commands with auto-derived
  pressure. Convenience for composing paths with paint.
  path-commands: [[:move-to [x y]] [:line-to [x y]] ...]
  opts: {:brush :chalk, :color [...], :radius 12.0,
         :pressure :taper (or :curvature, or explicit curve), :seed 42}"
  [path-commands opts]
  (let [points (stroke/path-commands->points path-commands 0.5)
        pressure-opt (get opts :pressure :taper)
        pressure (cond
                   (= pressure-opt :taper) (auto-pressure points {:mode :taper})
                   (= pressure-opt :curvature) (auto-pressure points {:mode :curvature})
                   (vector? pressure-opt) pressure-opt
                   :else (auto-pressure points {:mode :taper}))]
    (cond-> {:path/commands  path-commands
             :paint/brush    (or (:brush opts) :pencil)
             :paint/pressure pressure}
      (:color opts)  (assoc :paint/color (:color opts))
      (:radius opts) (assoc :paint/radius (:radius opts))
      (:seed opts)   (assoc :paint/seed (:seed opts)))))

(comment
  (let [s (make-surface [400 200])
        _ (render-stroke! s
            {:stroke/brush :chalk
             :stroke/color [:color/rgb 60 40 30]
             :stroke/radius 15.0
             :stroke/points [[50 100 0.8 0 0 0]
                             [200 60 1.0 1 0 0]
                             [350 100 0.4 0.5 0 0]]})
        img (compose s)]
    (javax.imageio.ImageIO/write img "png" (java.io.File. "/tmp/paint-api-test.png"))
    (println "Wrote /tmp/paint-api-test.png")))
