(ns site.pages
  "Static content for eido website pages — landing page, features, docs.")

;; --- Landing page ---

(defn hero-images
  "Filenames of hero images to display on the landing page."
  []
  ["galaxy.gif"
   "art-ink-landscape.png"
   "3d-teapot-spin.gif"
   "spiral-grid.gif"
   "art-stained-glass.png"
   "mixed-neon-orbit.gif"])

(defn features
  "Feature bullet points for the landing page."
  []
  [{:title "Images are values"
    :desc  "A scene is a plain Clojure map — printable, serializable, diffable. Nothing opaque."}
   {:title "One function"
    :desc  "render takes a scene (or a sequence of scenes) and produces output. That's the API."}
   {:title "Description, not instruction"
    :desc  "You declare what the image contains; eido decides how to draw it."}
   {:title "Animations are sequences"
    :desc  "60 frames = 60 maps in a vector. No timeline, no keyframes, no mutation."}
   {:title "Particle simulation"
    :desc  "Physics-based effects configured as data — emitters, forces, and lifetime curves."}
   {:title "Typography as paths"
    :desc  "Text compiled to vector paths — compatible with gradients, transforms, 3D extrusion."}
   {:title "No state, no framework"
    :desc  "Every function takes data and returns data. You bring your own workflow."}
   {:title "Zero dependencies"
    :desc  "Just Clojure and the standard library."}])

(defn quick-start-code []
  "(require '[eido.core :as eido])

(eido/render
  {:image/size [400 400]
   :image/background [:color/rgb 245 243 238]
   :image/nodes
   [{:node/type :shape/circle
     :circle/center [200 200]
     :circle/radius 120
     :style/fill [:color/rgb 200 50 50]}]}
  {:output \"circle.png\"})")

(defn install-code []
  "io.github.leifericf/eido {:git/tag \"v1.0.0-alpha4\" :git/sha \"1e8d203\"}")

;; --- Docs page ---
;; Each section is {:id "anchor" :title "Display Title" :content [:hiccup ...]}
;; Content will be populated when we extract from the README.

(defn docs-sections
  "Feature documentation sections for the docs page."
  []
  [{:id    "shapes"
    :title "Shapes"
    :content
    [:div
     [:p "Eido supports circles, rectangles, ellipses, lines, polygons, stars, and arbitrary paths."]
     [:pre [:code
            "{:node/type     :shape/circle
 :circle/center [200 200]
 :circle/radius 80
 :style/fill    [:color/rgb 100 150 255]}"]]
     [:pre [:code
            "{:node/type :shape/rect
 :rect/position [50 50]
 :rect/size [200 100]
 :style/fill [:color/rgb 255 200 50]
 :style/stroke {:color [:color/rgb 0 0 0] :width 2}}"]]
     [:p "Polygons and stars take a center, radius, and number of points:"]
     [:pre [:code
            "{:node/type       :shape/polygon
 :polygon/center  [200 200]
 :polygon/radius  80
 :polygon/sides   6
 :style/fill      [:color/rgb 100 200 150]}"]]]}

   {:id    "text"
    :title "Text"
    :content
    [:div
     [:p "Text is rendered as vector paths — no font rasterization. Every text feature composes with gradients, transforms, and 3D."]
     [:pre [:code
            "{:node/type    :shape/text
 :text/content \"Hello\"
 :text/position [100 200]
 :text/font {:font/family \"serif\" :font/size 48}
 :style/fill [:color/rgb 0 0 0]}"]]]}

   {:id    "composition"
    :title "Composition"
    :content
    [:div
     [:p "Group nodes to apply shared transforms, opacity, and clipping:"]
     [:pre [:code
            "{:node/type :group
 :group/children [...]
 :node/opacity 0.7
 :transform/translate [100 50]}"]]]}

   {:id    "stroke-styling"
    :title "Stroke Styling"
    :content
    [:div
     [:p "Strokes support width, dash patterns, line caps, and line joins:"]
     [:pre [:code
            ":style/stroke {:color [:color/rgb 0 0 0]
               :width 3
               :dash [10 5]
               :cap :round
               :join :round}"]]]}

   {:id    "clipping"
    :title "Clipping"
    :content
    [:div
     [:p "Clip groups to any shape — the clip shape masks everything inside:"]
     [:pre [:code
            "{:node/type :group
 :group/clip {:node/type :shape/circle
              :circle/center [200 200]
              :circle/radius 100}
 :group/children [...]}"]]]}

   {:id    "colors"
    :title "Colors"
    :content
    [:div
     [:p "RGB, HSL, and named colors:"]
     [:pre [:code
            "[:color/rgb 255 100 50]
[:color/hsl 210 0.8 0.5]
[:color/named :cornflowerblue]"]]]}

   {:id    "gradients"
    :title "Gradient Fills"
    :content
    [:div
     [:p "Linear and radial gradients as fill values:"]
     [:pre [:code
            ":style/fill {:fill/type :gradient/linear
               :gradient/start [0 0]
               :gradient/end [200 200]
               :gradient/stops [[0.0 [:color/rgb 255 0 0]]
                                [1.0 [:color/rgb 0 0 255]]]}"]]]}

   {:id    "patterns"
    :title "Generative Patterns"
    :content
    [:div
     [:p "Hatching, stippling, and custom pattern fills — all data-driven:"]
     [:pre [:code
            ":style/fill {:fill/type :hatch
               :hatch/angle 45
               :hatch/spacing 4
               :hatch/stroke-width 1
               :hatch/color [:color/rgb 0 0 0]}"]]]}

   {:id    "transforms"
    :title "Transforms"
    :content
    [:div
     [:p "Translate, rotate, and scale any node or group:"]
     [:pre [:code
            "{:node/type :shape/rect
 :rect/position [0 0]
 :rect/size [100 50]
 :transform/translate [200 100]
 :transform/rotate 0.5
 :transform/scale 1.5
 :style/fill [:color/rgb 200 100 50]}"]]]}

   {:id    "animation"
    :title "Animation"
    :content
    [:div
     [:p "Animations are sequences of scene maps. Use " [:code "eido.animate/frames"] " to generate them:"]
     [:pre [:code
            "(require '[eido.animate :as anim])

(def frames
  (anim/frames 60
    (fn [t]
      {:image/size [400 400]
       :image/nodes
       [{:node/type :shape/circle
         :circle/center [200 200]
         :circle/radius (* 100 t)
         :style/fill [:color/hsl (* 360 t) 0.8 0.5]}]})))

(eido/render frames {:output \"grow.gif\" :fps 30})"]]]}

   {:id    "particles"
    :title "Particle Simulation"
    :content
    [:div
     [:p "Physics-based particle effects configured as data:"]
     [:pre [:code
            "(require '[eido.particle :as particle])

(eido/render
  (particle/simulate particle/fire 60 30)
  {:output \"fire.gif\" :fps 30})"]]]}

   {:id    "3d"
    :title "3D Scenes"
    :content
    [:div
     [:p "3D primitives projected to 2D with lighting and shading:"]
     [:pre [:code
            "(require '[eido.scene3d :as s3d])

(let [proj (s3d/perspective {:scale 100 :origin [200 200]
                             :yaw 0.5 :pitch -0.3 :distance 5})]
  (s3d/sphere proj [0 0 0] 1.5
    {:style {:style/fill [:color/rgb 100 150 255]}
     :light {:light/direction [1 1 0.5]
             :light/ambient 0.2
             :light/intensity 0.8}}))"]]]}

   {:id    "contours"
    :title "Path Contours"
    :content
    [:div
     [:p "Generate contour paths from shapes for decorative effects:"]
     [:pre [:code
            "(require '[eido.contour :as contour])

(contour/contour-paths shape {:levels 5 :spacing 8})"]]]}

   {:id    "compositing"
    :title "Compositing"
    :content
    [:div
     [:p "Blend modes, masks, and group opacity for layered compositions:"]
     [:pre [:code
            "{:node/type :group
 :group/children [...]
 :node/opacity 0.5
 :composite/blend :screen}"]]]}

   {:id    "export"
    :title "Export"
    :content
    [:div
     [:p "Render to PNG, JPEG, SVG, animated GIF, or frame sequences:"]
     [:pre [:code
            "(eido/render scene {:output \"out.png\"})
(eido/render scene {:output \"out.svg\"})
(eido/render scene {:output \"out.jpg\" :quality 0.9})
(eido/render frames {:output \"anim.gif\" :fps 30})
(eido/render frames {:output \"frames/\" :fps 30})"]]]}

   {:id    "validation"
    :title "Validation"
    :content
    [:div
     [:p "Validate scene data before rendering:"]
     [:pre [:code
            "(require '[eido.validate :as validate])

(validate/validate scene)
;; => nil (valid) or throws with details"]]]}])
