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
    [eido.paint.wet :as wet]))

;; --- brush presets ---

(def presets
  "Named brush presets. Each is a brush spec map."
  {:pencil   {:brush/type  :brush/dab
              :brush/tip   {:tip/shape :ellipse :tip/hardness 0.85 :tip/aspect 1.0}
              :brush/paint {:paint/opacity 0.6 :paint/flow 0.9 :paint/spacing 0.06}}

   :marker   {:brush/type  :brush/dab
              :brush/tip   {:tip/shape :ellipse :tip/hardness 0.95 :tip/aspect 1.0}
              :brush/paint {:paint/opacity 0.5 :paint/flow 1.0 :paint/spacing 0.04}}

   :airbrush {:brush/type  :brush/dab
              :brush/tip   {:tip/shape :ellipse :tip/hardness 0.15 :tip/aspect 1.0}
              :brush/paint {:paint/opacity 0.08 :paint/flow 0.7 :paint/spacing 0.03}}

   :chalk    {:brush/type  :brush/dab
              :brush/tip   {:tip/shape :ellipse :tip/hardness 0.5 :tip/aspect 1.0}
              :brush/paint {:paint/opacity 0.12 :paint/flow 0.8 :paint/spacing 0.05}
              :brush/jitter {:jitter/position 0.12 :jitter/opacity 0.25
                             :jitter/size 0.1 :jitter/angle 0.15}}

   :ink      {:brush/type  :brush/dab
              :brush/tip   {:tip/shape :ellipse :tip/hardness 0.8 :tip/aspect 1.0}
              :brush/paint {:paint/opacity 0.7 :paint/flow 1.0 :paint/spacing 0.04}}

   :oil      {:brush/type   :brush/dab
              :brush/tip    {:tip/shape :ellipse :tip/hardness 0.6 :tip/aspect 1.0}
              :brush/paint  {:paint/opacity 0.4 :paint/flow 0.85 :paint/spacing 0.06}
              :brush/smudge {:smudge/mode :smear :smudge/amount 0.45 :smudge/length 0.7}
              :brush/jitter {:jitter/position 0.08 :jitter/opacity 0.15
                             :jitter/size 0.08 :jitter/angle 0.05}}

   :watercolor {:brush/type  :brush/dab
                :brush/tip   {:tip/shape :ellipse :tip/hardness 0.3 :tip/aspect 1.0}
                :brush/paint {:paint/opacity 0.06 :paint/flow 0.6 :paint/spacing 0.04}
                :brush/wet   {:wet/enabled true :wet/deposit 0.3
                              :wet/diffusion 0.25 :wet/diffusion-steps 6
                              :wet/edge-darken 0.2}
                :brush/jitter {:jitter/position 0.1 :jitter/opacity 0.2
                               :jitter/size 0.12}}

   :pastel   {:brush/type  :brush/dab
              :brush/tip   {:tip/shape :ellipse :tip/hardness 0.4 :tip/aspect 1.2}
              :brush/paint {:paint/opacity 0.1 :paint/flow 0.75 :paint/spacing 0.05}
              :brush/jitter {:jitter/position 0.15 :jitter/opacity 0.3
                             :jitter/size 0.12 :jitter/angle 0.2}}})

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
        stroke-seed   (long (or (:paint/seed stroke-desc)
                                (:stroke/seed stroke-desc) 0))
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
          (deposit-height d))))
    ;; Post-stroke: run wet diffusion if configured
    (when (and wet-spec (:wet/enabled wet-spec true))
      (let [iterations (long (get wet-spec :wet/diffusion-steps 4))
            strength   (double (get wet-spec :wet/diffusion 0.2))
            darken     (double (get wet-spec :wet/edge-darken 0.15))]
        (wet/apply-wet-pass! surface iterations strength darken))))))

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
