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
    [eido.paint.stroke :as stroke]
    [eido.paint.surface :as surface]))

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
              :brush/paint {:paint/opacity 0.12 :paint/flow 0.8 :paint/spacing 0.05}}

   :ink      {:brush/type  :brush/dab
              :brush/tip   {:tip/shape :ellipse :tip/hardness 0.8 :tip/aspect 1.0}
              :brush/paint {:paint/opacity 0.7 :paint/flow 1.0 :paint/spacing 0.04}}

   :oil      {:brush/type  :brush/dab
              :brush/tip   {:tip/shape :ellipse :tip/hardness 0.6 :tip/aspect 1.0}
              :brush/paint {:paint/opacity 0.4 :paint/flow 0.85 :paint/spacing 0.06}}

   :watercolor {:brush/type  :brush/dab
                :brush/tip   {:tip/shape :ellipse :tip/hardness 0.3 :tip/aspect 1.0}
                :brush/paint {:paint/opacity 0.06 :paint/flow 0.6 :paint/spacing 0.04}}

   :pastel   {:brush/type  :brush/dab
              :brush/tip   {:tip/shape :ellipse :tip/hardness 0.4 :tip/aspect 1.2}
              :brush/paint {:paint/opacity 0.1 :paint/flow 0.75 :paint/spacing 0.05}}})

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
  Returns nil (mutates surface)."
  [surface stroke-desc]
  (let [brush-spec (resolve-brush (or (:stroke/brush stroke-desc)
                                      (:paint/brush stroke-desc)))
        raw-color  (or (:stroke/color stroke-desc)
                       (:paint/color stroke-desc)
                       [:color/rgb 0 0 0])
        resolved   (color/resolve-color raw-color)
        radius     (double (get stroke-desc :stroke/radius
                             (get stroke-desc :paint/radius 8.0)))
        hardness   (get-in brush-spec [:brush/tip :tip/hardness] 0.7)
        opacity    (get-in brush-spec [:brush/paint :paint/opacity] 0.5)
        spacing-px (brush-spacing-px brush-spec radius)
        ;; Get points — either explicit or from path commands
        explicit   (or (:stroke/points stroke-desc) nil)
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
                :pressure pressure})]
    (doseq [d dabs]
      (kernel/rasterize-dab! surface d))))

(defn render-strokes!
  "Renders all strokes onto a surface. Returns the surface."
  [surface strokes]
  (doseq [s strokes]
    (render-stroke! surface s))
  surface)

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
