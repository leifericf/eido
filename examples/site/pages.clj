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
    :desc  "A scene is a plain data structure — printable, serializable, diffable. Nothing opaque."}
   {:title "One function"
    :desc  "render takes a scene (or a sequence of scenes) and produces output. That's the entire API."}
   {:title "Description, not instruction"
    :desc  "You declare what the image contains — Eido decides how to draw it."}
   {:title "Animations are sequences"
    :desc  "60 frames = 60 scenes in a list. No timeline, no keyframes, no mutable state."}
   {:title "Particle simulation"
    :desc  "Physics-based effects — fire, snow, sparks — configured as data with deterministic results."}
   {:title "Typography as paths"
    :desc  "Text converted to vector paths — compatible with gradients, transforms, and 3D extrusion."}
   {:title "Bring your own workflow"
    :desc  "Every function takes data and returns data. No framework, no state management."}
   {:title "Zero dependencies"
    :desc  "Just the language and the standard library. Nothing to install, nothing to break."}])

(defn quick-start-content
  "Friendly Quick Start walkthrough for the landing page."
  []
  [:div
   [:p "In Eido, an image is just a description — a plain data structure that says what the image contains. Here's a red circle on a light background:"]
   [:pre [:code
          "{:image/size [400 400]                       ;; 400x400 pixels
 :image/background [:color/rgb 245 243 238]  ;; warm off-white
 :image/nodes
 [{:node/type     :shape/circle
   :circle/center [200 200]                  ;; center of the canvas
   :circle/radius 120
   :style/fill    [:color/rgb 200 50 50]}]}  ;; red fill"]]
   [:p "That's it — no drawing commands, no canvas API, no mutable state. You describe "
    [:em "what you see"] ", and Eido renders it. To produce an image file:"]
   [:pre [:code
          "(eido/render scene {:output \"circle.png\"})"]]
   [:p "Want animation? Return a different scene for each frame. Here's a circle that grows and shifts color over 60 frames:"]
   [:pre [:code
          "(def frames
  (anim/frames 60
    (fn [t]                             ;; t goes from 0.0 to 1.0
      {:image/size [400 400]
       :image/background [:color/rgb 30 30 40]
       :image/nodes
       [{:node/type     :shape/circle
         :circle/center [200 200]
         :circle/radius (* 150 t)       ;; grows over time
         :style/fill [:color/hsl        ;; hue shifts through
                      (* 360 t)         ;;   the rainbow
                      0.8 0.5]}]})))

(eido/render frames {:output \"grow.gif\" :fps 30})"]]
   [:p "Every example in the " [:a {:href "./gallery/index.html"} "gallery"]
    " works this way — pure data in, image out."]])

(defn install-code []
  "io.github.leifericf/eido {:git/tag \"v1.0.0-alpha5\" :git/sha \"UPDATE_AFTER_TAG\"}")

;; --- Docs page ---
;; Docs are organized into categories, each containing sections.
;; {:category "Name" :id "anchor" :sections [{:id :title :content}]}

(defn docs-categories
  "Feature documentation organized by category."
  []
  [{:category "Drawing"
    :id       "drawing"
    :sections
    [{:id    "shapes"
      :title "Shapes"
      :content
      [:div
       [:p "Everything in Eido starts with shapes. You describe a shape as a map — a collection of key-value pairs that says "
        [:em "what"] " the shape is, " [:em "where"] " it goes, and " [:em "how"] " it looks. Eido takes care of drawing it."]
       [:p "Here are the basic building blocks:"]
       [:h4 "Rectangle"]
       [:pre [:code
              "{:node/type :shape/rect
 :rect/xy [50 50]          ;; top-left corner position
 :rect/size [200 100]      ;; width and height
 :style/fill [:color/rgb 0 128 255]}"]]
       [:p "Add rounded corners with " [:code ":rect/corner-radius"] ":"]
       [:pre [:code
              "{:node/type :shape/rect
 :rect/xy [50 50]
 :rect/size [200 100]
 :rect/corner-radius 16
 :style/fill [:color/rgb 0 128 255]}"]]
       [:h4 "Circle"]
       [:pre [:code
              "{:node/type :shape/circle
 :circle/center [200 200]  ;; center point
 :circle/radius 80         ;; radius in pixels
 :style/stroke {:color [:color/rgb 0 0 0] :width 2}}"]]
       [:h4 "Ellipse"]
       [:pre [:code
              "{:node/type :shape/ellipse
 :ellipse/center [200 200]
 :ellipse/rx 120            ;; horizontal radius
 :ellipse/ry 60             ;; vertical radius
 :style/fill [:color/rgb 200 50 50]}"]]
       [:h4 "Arc"]
       [:p "A partial ellipse — like a pie slice or an open curve:"]
       [:pre [:code
              "{:node/type :shape/arc
 :arc/center [200 200]
 :arc/rx 80 :arc/ry 80
 :arc/start 0 :arc/extent 270   ;; degrees
 :arc/mode :pie                  ;; :open, :chord, or :pie
 :style/fill [:color/rgb 255 200 50]}"]]
       [:h4 "Line"]
       [:pre [:code
              "{:node/type :shape/line
 :line/from [50 50]
 :line/to [350 250]
 :style/stroke {:color [:color/rgb 0 0 0] :width 2}}"]]
       [:h4 "Path"]
       [:p "For anything that isn't a basic shape, use a path. Paths are sequences of drawing commands — move to a point, draw a line, draw a curve, and close the shape. This is how you create arbitrary freeform shapes:"]
       [:pre [:code
              "{:node/type :shape/path
 :path/commands [[:move-to [100 200]]          ;; pick up the pen
                 [:line-to [200 50]]           ;; draw a straight line
                 [:curve-to [250 0] [300 100]  ;; cubic bezier curve
                            [300 200]]         ;;   (two control points + end)
                 [:quad-to [250 250]           ;; quadratic bezier curve
                           [200 200]]          ;;   (one control point + end)
                 [:close]]                     ;; connect back to start
 :style/fill [:color/rgb 255 200 50]}"]]
       [:h4 "Convenience helpers"]
       [:p "The " [:code "eido.scene"] " namespace provides shortcuts for common shapes:"]
       [:pre [:code
              "(require '[eido.scene :as scene])

(scene/regular-polygon [200 200] 80 6)    ;; hexagon
(scene/star [200 200] 80 35 5)            ;; 5-pointed star
(scene/triangle [100 200] [200 50] [300 200])
(scene/smooth-path [[50 200] [150 50] [250 200] [350 50]])  ;; smooth curve through points"]]]}

     {:id    "text"
      :title "Text"
      :content
      [:div
       [:p "Text in Eido is not rasterized pixels — it's converted to vector paths, just like any other shape. That means text works with everything: gradient fills, strokes, transforms, clipping, even 3D extrusion."]
       [:h4 "Simple text"]
       [:pre [:code
              "{:node/type   :shape/text
 :text/content \"Hello\"
 :text/font    {:font/family \"Serif\" :font/size 48 :font/weight :bold}
 :text/origin  [50 100]       ;; baseline-left anchor
 :text/align   :center        ;; :left (default), :center, :right
 :style/fill   [:color/rgb 0 0 0]}"]]
       [:h4 "Per-glyph control"]
       [:p "Style each character independently — great for rainbow text, animated reveals, or creative typography:"]
       [:pre [:code
              "{:node/type    :shape/text-glyphs
 :text/content \"COLOR\"
 :text/font    {:font/family \"SansSerif\" :font/size 64}
 :text/origin  [50 100]
 :text/glyphs  [{:glyph/index 0 :style/fill [:color/rgb 255 0 0]}
                {:glyph/index 1 :style/fill [:color/rgb 0 255 0]}]
 :style/fill   [:color/rgb 100 100 100]}  ;; default for unlisted glyphs"]]
       [:h4 "Text on a path"]
       [:p "Make text follow any curve:"]
       [:pre [:code
              "{:node/type    :shape/text-on-path
 :text/content \"ALONG A CURVE\"
 :text/font    {:font/family \"SansSerif\" :font/size 24}
 :text/path    [[:move-to [50 200]]
                [:curve-to [150 50] [350 50] [450 200]]]
 :text/offset  10             ;; start distance along path
 :text/spacing 1              ;; extra inter-glyph spacing
 :style/fill   [:color/rgb 0 0 0]}"]]
       [:p "Fonts reference system fonts by name. Java's built-in fonts — "
        [:code "\"Serif\""] ", " [:code "\"SansSerif\""] ", " [:code "\"Monospaced\""]
        " — work on every system."]]}]}

   {:category "Styling"
    :id       "styling"
    :sections
    [{:id    "colors"
      :title "Colors"
      :content
      [:div
       [:p "Eido understands several color formats. Use whichever feels natural — they all work everywhere:"]
       [:pre [:code
              "[:color/rgb 255 0 0]             ;; red, green, blue (0-255)
[:color/rgba 255 0 0 0.5]        ;; same, with transparency (0-1)
[:color/hsl 0 1.0 0.5]           ;; hue (0-360), saturation, lightness
[:color/hsb 0 1.0 1.0]           ;; hue, saturation, brightness
[:color/hex \"#FF0000\"]           ;; hex notation
[:color/name \"coral\"]            ;; 148 CSS named colors"]]
       [:p "All formats work directly in style maps — just drop them in:"]
       [:pre [:code
              "{:style/fill [:color/hsl 200 0.9 0.5]}
{:style/fill [:color/hex \"#FF6B35\"]}
{:style/fill [:color/name \"tomato\"]}"]]
       [:h4 "Color manipulation"]
       [:p "The " [:code "eido.color"] " namespace provides functions for adjusting colors — lighten, darken, blend, shift hue:"]
       [:pre [:code
              "(require '[eido.color :as color])

(color/lighten [:color/rgb 255 0 0] 0.2)     ;; lighter red
(color/darken [:color/rgb 255 0 0] 0.2)      ;; darker red
(color/saturate [:color/rgb 150 100 100] 0.3) ;; more vivid
(color/rotate-hue [:color/rgb 255 0 0] 120)   ;; shift hue → green
(color/lerp color-a color-b 0.5)               ;; blend two colors"]]]}

     {:id    "stroke-styling"
      :title "Strokes"
      :content
      [:div
       [:p "Strokes are the outlines around shapes. You can control the width, the shape of line endings (caps), how corners look (joins), and add dashed patterns:"]
       [:pre [:code
              ";; Rounded line endings and beveled corners
{:style/stroke {:color [:color/rgb 0 0 0]
                :width 4
                :cap :round      ;; :butt, :round, or :square
                :join :bevel}}   ;; :miter, :round, or :bevel

;; Dashed lines
{:style/stroke {:color [:color/rgb 0 0 0]
                :width 2
                :dash [10 5]}}   ;; alternating dash and gap lengths"]]
       [:p "A shape can have both a fill and a stroke — the stroke is drawn on top."]]}

     {:id    "gradients"
      :title "Gradients"
      :content
      [:div
       [:p "Instead of a flat color, fill a shape with a smooth color transition. Eido supports two kinds:"]
       [:h4 "Linear gradient"]
       [:p "Colors transition along a line from one point to another:"]
       [:pre [:code
              "{:style/fill {:gradient/type :linear
               :gradient/from [0 0]       ;; start point
               :gradient/to [200 0]       ;; end point
               :gradient/stops [[0.0 [:color/rgb 255 0 0]]    ;; red at start
                                [1.0 [:color/rgb 0 0 255]]]}}" ;; blue at end
              ]]
       [:h4 "Radial gradient"]
       [:p "Colors radiate outward from a center point:"]
       [:pre [:code
              "{:style/fill {:gradient/type :radial
               :gradient/center [100 100]
               :gradient/radius 100
               :gradient/stops [[0.0 [:color/name \"white\"]]
                                [1.0 [:color/name \"black\"]]]}}"]]
       [:p "Add as many color stops as you want for multi-color transitions. Any color format works in stops."]]}

     {:id    "patterns"
      :title "Pattern Fills"
      :content
      [:div
       [:p "Beyond solid colors and gradients, Eido supports texture-like fills that give shapes a hand-crafted look:"]
       [:h4 "Hatching"]
       [:p "Parallel lines drawn across a shape — like pen-and-ink cross-hatching:"]
       [:pre [:code
              "{:style/fill {:fill/type :hatch
               :hatch/angle 45            ;; line angle in degrees
               :hatch/spacing 4           ;; distance between lines
               :hatch/stroke-width 1
               :hatch/color [:color/rgb 0 0 0]}}"]]
       [:h4 "Stippling"]
       [:p "Random dots packed inside a shape — like pointillism:"]
       [:pre [:code
              "{:style/fill {:fill/type :stipple
               :stipple/density 0.6       ;; how packed (0-1)
               :stipple/radius 1.0        ;; dot size
               :stipple/seed 42           ;; for reproducibility
               :stipple/color [:color/rgb 0 0 0]}}"]]
       [:h4 "Custom tile patterns"]
       [:p "Tile any collection of shapes as a repeating pattern:"]
       [:pre [:code
              "{:style/fill {:fill/type :pattern
               :pattern/size [20 20]      ;; tile size
               :pattern/nodes [...]}}"]]]}]}

   {:category "Composition"
    :id       "composition"
    :sections
    [{:id    "groups"
      :title "Groups"
      :content
      [:div
       [:p "Groups let you treat multiple shapes as one unit. Any style, transform, or effect applied to the group affects all its children. Styles "
        [:em "inherit"] " — children get the group's fill color unless they specify their own. Opacity " [:em "multiplies"] " through the tree."]
       [:pre [:code
              "{:node/type :group
 :node/transform [[:transform/translate 200 200]]
 :style/fill [:color/rgb 255 0 0]
 :node/opacity 0.8
 :group/children
 [{:node/type :shape/circle        ;; inherits red fill
   :circle/center [0 0]
   :circle/radius 80}
  {:node/type :shape/rect
   :rect/xy [-30 -30]
   :rect/size [60 60]
   :style/fill [:color/rgb 0 0 255]  ;; overrides with blue
   :node/opacity 0.5}]}"            ;; effective opacity: 0.8 * 0.5 = 0.4
              ]]]}

     {:id    "clipping"
      :title "Clipping"
      :content
      [:div
       [:p "Clipping restricts a group's visible area to a shape — like looking through a window. Only the parts of the children that fall inside the clip shape are drawn:"]
       [:pre [:code
              "{:node/type :group
 :group/clip {:node/type :shape/circle
              :circle/center [200 200]
              :circle/radius 80}
 :group/children
 [{:node/type :shape/rect
   :rect/xy [120 120]
   :rect/size [160 160]
   :style/fill [:color/rgb 255 0 0]}]}
;; Only the part of the rectangle inside the circle is visible"]]]}

     {:id    "compositing"
      :title "Compositing"
      :content
      [:div
       [:p "Blend modes control how overlapping shapes combine visually — like layer blend modes in Photoshop:"]
       [:pre [:code
              "{:node/type :group
 :group/children [...]
 :node/opacity 0.5
 :composite/blend :screen}  ;; :src-over, :multiply, :screen, etc."]]]}

     {:id    "transforms"
      :title "Transforms"
      :content
      [:div
       [:p "Move, rotate, scale, and skew any shape or group. Transforms are applied in order — translate first, then rotate, then scale:"]
       [:pre [:code
              "{:node/transform [[:transform/translate 100 50]
                  [:transform/rotate 0.785]      ;; angle in radians
                  [:transform/scale 1.5 1.5]
                  [:transform/shear-x 0.3]]}     ;; skew"]]
       [:p "Transforms compose through the tree — a shape inside a translated group that is itself translated will move by the sum of both translations."]]}]}

   {:category "Generative"
    :id       "generative"
    :sections
    [{:id    "scene-helpers"
      :title "Scene Helpers"
      :content
      [:div
       [:p "The " [:code "eido.scene"] " namespace provides functions that generate collections of shapes from a pattern — grids, radial layouts, distributions along a line:"]
       [:h4 "Grid"]
       [:p "Create a grid of shapes by providing columns, rows, and a function that receives the column and row:"]
       [:pre [:code
              "(scene/grid 10 10
  (fn [col row]
    {:node/type :shape/circle
     :circle/center [(+ 30 (* col 40)) (+ 30 (* row 40))]
     :circle/radius 15
     :style/fill [:color/rgb (* col 25) (* row 25) 128]}))"]]
       [:h4 "Distribute along a line"]
       [:pre [:code
              "(scene/distribute 8 [50 200] [750 200]
  (fn [x y t]   ;; t is progress 0 to 1
    {:node/type :shape/circle
     :circle/center [x y]
     :circle/radius (+ 5 (* 20 t))
     :style/fill [:color/rgb 0 0 0]}))"]]
       [:h4 "Radial arrangement"]
       [:pre [:code
              "(scene/radial 12 200 200 120  ;; 12 items around (200,200) radius 120
  (fn [x y angle]
    {:node/type :shape/circle
     :circle/center [x y]
     :circle/radius 15
     :style/fill [:color/rgb 200 0 0]}))"]]]}

     {:id    "contours"
      :title "Contour Lines"
      :content
      [:div
       [:p "Contour lines connect points of equal value — like elevation lines on a topographic map. Eido generates them from noise fields using the marching squares algorithm:"]
       [:pre [:code
              "(require '[eido.contour :as contour])

{:node/type :contour
 :contour/bounds [0 0 500 400]
 :contour/opts {:thresholds [0.0 0.2 0.4]
                :resolution 3
                :noise-scale 0.012
                :seed 42}
 :style/stroke {:color [:color/rgb 100 150 100] :width 1}}"]]]}

     {:id    "noise"
      :title "Noise"
      :content
      [:div
       [:p "Noise functions produce smooth, organic-looking randomness — like clouds, terrain, or flowing water. Unlike random numbers, nearby inputs give nearby outputs, creating natural gradients:"]
       [:pre [:code
              "(require '[eido.noise :as noise])

(noise/perlin2d x y)           ;; smooth 2D noise (-1 to 1)
(noise/perlin3d x y z)         ;; 3D noise (use z as time for animation)
(noise/fbm noise/perlin2d x y  ;; fractal noise — layered detail
  {:octaves 4 :seed 42})"]]]}

     {:id    "particles"
      :title "Particles"
      :content
      [:div
       [:p "Particle systems simulate many small objects (sparks, snowflakes, smoke) moving under physics forces. You configure the behavior as data — emitter position, lifetime, gravity, wind — and Eido simulates the result deterministically:"]
       [:pre [:code
              "(require '[eido.particle :as particle])

;; Pre-compute 60 frames of fire particles
(let [frames (vec (particle/simulate
                    (particle/with-position particle/fire [200 350])
                    60 {:fps 30}))]
  ;; Each frame is a vector of shape nodes — compose freely
  (eido/render
    (anim/frames 60
      (fn [t]
        {:image/size [400 400]
         :image/background [:color/rgb 20 15 10]
         :image/nodes (nth frames (int (* t 59)))}))
    {:output \"fire.gif\" :fps 30}))"]]
       [:p "Built-in presets: " [:code "particle/fire"] ", " [:code "particle/snow"]
        ", " [:code "particle/sparks"] ", " [:code "particle/confetti"]
        ", " [:code "particle/smoke"] ", " [:code "particle/fountain"]
        ". Customize any preset with " [:code "assoc"] "/" [:code "update"] "."]]}]}

   {:category "Animation"
    :id       "animation"
    :sections
    [{:id    "animation-basics"
      :title "Creating Animations"
      :content
      [:div
       [:p "An animation in Eido is just a sequence of scenes — one per frame. There's no timeline, no keyframe system, no mutable state. You write a function that takes a progress value "
        [:code "t"] " (from 0 to 1) and returns a scene. Eido calls it once per frame:"]
       [:pre [:code
              "(require '[eido.animate :as anim])

(def frames
  (anim/frames 60    ;; 60 frames total
    (fn [t]          ;; t goes from 0.0 to 1.0
      {:image/size [200 200]
       :image/background [:color/rgb 30 30 40]
       :image/nodes
       [{:node/type :shape/circle
         :circle/center [100 100]
         :circle/radius (* 80 t)    ;; grows over time
         :style/fill [:color/hsl (* 360 t) 0.8 0.5]}]})))

;; Render as animated GIF
(eido/render frames {:output \"grow.gif\" :fps 30})"]]
       [:p "Since frames are just data, you can manipulate them with all the usual tools — "
        [:code "map"] ", " [:code "filter"] ", " [:code "concat"]
        " — to build complex sequences from simple parts."]]}

     {:id    "easing"
      :title "Easing & Helpers"
      :content
      [:div
       [:p "Easing functions make motion feel natural. Instead of moving at a constant speed, things can accelerate, decelerate, bounce, or overshoot:"]
       [:pre [:code
              "(anim/ease-in t)            ;; slow start, fast finish
(anim/ease-out t)           ;; fast start, slow finish
(anim/ease-in-out t)        ;; slow start and finish
(anim/ease-in-cubic t)      ;; more dramatic
(anim/ease-out-elastic t)   ;; springy overshoot
(anim/ease-out-bounce t)    ;; bouncing ball"]]
       [:p "Other useful helpers:"]
       [:pre [:code
              "(anim/ping-pong t)          ;; oscillate: 0→1→0
(anim/cycle-n 3 t)          ;; repeat 3 times
(anim/lerp 0 100 t)         ;; interpolate between values
(anim/stagger 2 5 t 0.3)    ;; offset timing per element"]]]}]}

   {:category "3D"
    :id       "3d"
    :sections
    [{:id    "3d-scenes"
      :title "3D Scenes"
      :content
      [:div
       [:p "Eido can render 3D objects by projecting them onto 2D. You set up a camera (perspective or isometric), define lights, and place 3D meshes in the scene. The result is a regular 2D scene with shaded polygons — no GPU required:"]
       [:pre [:code
              "(require '[eido.scene3d :as s3d])

(let [proj (s3d/perspective
             {:scale 100 :origin [200 200]
              :yaw 0.5 :pitch -0.3 :distance 5})
      light {:light/direction [1 1 0.5]
             :light/ambient 0.2
             :light/intensity 0.8}]
  (s3d/sphere proj [0 0 0] 1.5
    {:style {:style/fill [:color/rgb 100 150 255]}
     :light light}))"]]
       [:p "Available primitives: " [:code "sphere"] ", " [:code "cube"] ", "
        [:code "cone"] ", " [:code "torus"] ", " [:code "cylinder"]
        ". Load arbitrary meshes from OBJ files with " [:code "eido.obj/load-obj"] "."]
       [:h4 "Camera types"]
       [:pre [:code
              ";; Perspective — objects shrink with distance
(s3d/perspective {:scale 100 :origin [200 200]
                  :yaw 0.3 :pitch -0.4 :distance 5})

;; Isometric — no perspective distortion
(s3d/isometric {:scale 40 :origin [200 200]})

;; Look-at — point camera at a target
(s3d/look-at {:eye [3 2 5] :target [0 0 0] :up [0 1 0]
              :scale 100 :origin [200 200]})"]]]}]}

   {:category "Output"
    :id       "output"
    :sections
    [{:id    "export"
      :title "Export"
      :content
      [:div
       [:p "Everything goes through one function — " [:code "eido/render"] ". The output format is determined by the file extension:"]
       [:pre [:code
              ";; Static images
(eido/render scene {:output \"out.png\"})          ;; PNG (default)
(eido/render scene {:output \"out.svg\"})          ;; SVG (vector)
(eido/render scene {:output \"out.jpg\" :quality 0.9})

;; Animations
(eido/render frames {:output \"anim.gif\" :fps 30})
(eido/render frames {:output \"anim.svg\" :fps 30})  ;; animated SVG
(eido/render frames {:output \"frames/\" :fps 30})   ;; PNG sequence"]]
       [:h4 "Options"]
       [:pre [:code
              ":scale 2                  ;; 2x resolution (retina)
:dpi 300                  ;; DPI metadata for print
:transparent-background true  ;; no background fill
:loop false               ;; GIF plays once (default: loops)"]]
       [:p "Render without an output path to get a BufferedImage back for further processing, or use "
        [:code ":format :svg"] " to get an SVG string."]]}

     {:id    "file-workflow"
      :title "File Workflow"
      :content
      [:div
       [:p "Scenes can be stored as " [:code ".edn"] " files and loaded for rendering:"]
       [:pre [:code
              "(eido/render (eido/read-scene \"my-scene.edn\") {:output \"out.png\"})

;; Watch a file — auto-reload the preview on every save
(watch-file \"my-scene.edn\")

;; Watch an atom for live coding
(def my-scene (atom {...}))
(watch-scene my-scene)

;; tap> integration — render by tapping
(install-tap!)
(tap> {:image/size [200 200] :image/nodes [...]})"]]]}

     {:id    "validation"
      :title "Validation"
      :content
      [:div
       [:p "Scenes are validated before rendering. If something is wrong, you get a clear error pointing to the exact problem:"]
       [:pre [:code
              "(eido/validate {:image/size [800 600]
                :image/background [:color/rgb 255 255 255]
                :image/nodes [{:node/type :shape/rect}]})
;; => [{:path [:image/nodes 0]
;;      :pred \"missing required key :rect/xy\" ...}]"]]
       [:p "Invalid scenes throw " [:code "ex-info"] " with " [:code ":errors"] " in the exception data, so you always know what went wrong."]]}]}])
