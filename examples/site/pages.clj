(ns site.pages
  "Static content for eido website pages — landing page, features, docs, architecture.")

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
   {:title "3D sculpting pipeline"
    :desc  "Composable mesh operations — deform, extrude, subdivide, mirror — all pure data, chainable via ->."}
   {:title "2D↔3D bridge"
    :desc  "Same fields, palettes, and programs work across both domains. UV-mapped procedural textures on 3D meshes."}
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
 :image/background [:color/name \"linen\"]      ;; warm off-white
 :image/nodes
 [{:node/type     :shape/circle
   :circle/center [200 200]                  ;; center of the canvas
   :circle/radius 120
   :style/fill    [:color/name \"crimson\"]}]}  ;; red fill"]]
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
   [:p "Convenience helpers let you write the same thing more concisely:"]
   [:pre [:code
          ";; Shorthand — same circle, fewer keystrokes
(require '[eido.scene :as scene])
(require '[eido.color :as color])

(scene/circle-node [200 200] 120 (color/hsl 0 0.8 0.5))"]]
   [:p "Low-level control when you need it, high-level convenience when you don't. Every example in the "
    [:a {:href "./gallery/"} "gallery"]
    " works this way — pure data in, image out. See the "
    [:a {:href "./guide/"} "Guide"]
    " to get started."]])

(defn install-content
  "Installation walkthrough for the landing page."
  []
  [:div
   [:p "Eido runs on the JVM and uses Clojure's " [:code "deps.edn"]
    " for dependency management. If you're new to Clojure, here's how to get started:"]
   [:h4 "1. Install Clojure"]
   [:p "Follow the official "
    [:a {:href "https://clojure.org/guides/install_clojure" :target "_blank"} "Clojure install guide"]
    " for your platform. This gives you the " [:code "clj"] " command-line tool."]
   [:h4 "2. Create a project"]
   [:p "Make a new directory and create a " [:code "deps.edn"] " file with Eido as a dependency:"]
   [:pre [:code
          ";; deps.edn
{:deps
 {io.github.leifericf/eido
  {:git/tag \"v1.0.0-beta1\" :git/sha \"65fcfac\"}}}"]]
   [:h4 "3. Start a REPL and render"]
   [:p "Launch a REPL with " [:code "clj"] ", then try rendering your first image:"]
   [:pre [:code
          "$ clj
Clojure 1.12.0

user=> (require '[eido.core :as eido])

user=> (eido/render
         {:image/size [400 400]
          :image/background [:color/name \"linen\"]
          :image/nodes
          [{:node/type     :shape/circle
            :circle/center [200 200]
            :circle/radius 120
            :style/fill    [:color/name \"coral\"]}]}
         {:output \"my-first-image.png\"})

\"my-first-image.png\""]]
   [:p "That's it — " [:code "my-first-image.png"]
    " is now on disk. No build tools, no project scaffolding, no configuration. Just a REPL and a map."]])

;; --- Docs page ---
;; Docs are organized into categories, each containing sections.
;; {:category "Name" :id "anchor" :sections [{:id :title :content}]}

(defn docs-categories
  "Feature documentation organized by category."
  []
  [{:category "Drawing"
    :id       "drawing"
    :intro    [:div
               [:p "Everything in Eido starts with shapes. You describe " [:em "what"] " something looks like — not " [:em "how"] " to draw it. There's no canvas, no drawing loop, no mutable state. Just data that says \"here's a circle at this position with this color.\" Eido reads that description and produces the image."]]
    :sections
    [{:id    "shapes"
      :title "Shapes"
      :content
      [:div
       [:p "You describe a shape as a map — a collection of key-value pairs that says "
        [:em "what"] " the shape is, " [:em "where"] " it goes, and " [:em "how"] " it looks:"]
       [:h4 "Rectangle"]
       [:pre {:data-img "docs-rect.png"} [:code
              "{:node/type :shape/rect
 :rect/xy [50 50]          ;; top-left corner position
 :rect/size [200 100]      ;; width and height
 :style/fill [:color/name \"dodgerblue\"]}"]]
       [:p "Add rounded corners with " [:code ":rect/corner-radius"] ":"]
       [:pre {:data-img "docs-rect-rounded.png"} [:code
              "{:node/type :shape/rect
 :rect/xy [50 50]
 :rect/size [200 100]
 :rect/corner-radius 16
 :style/fill [:color/name \"dodgerblue\"]}"]]
       [:h4 "Circle"]
       [:pre {:data-img "docs-circle.png"} [:code
              "{:node/type :shape/circle
 :circle/center [200 200]  ;; center point
 :circle/radius 80         ;; radius in pixels
 :style/stroke {:color [:color/name \"black\"] :width 2}}"]]
       [:h4 "Ellipse"]
       [:pre {:data-img "docs-ellipse.png"} [:code
              "{:node/type :shape/ellipse
 :ellipse/center [200 200]
 :ellipse/rx 120            ;; horizontal radius
 :ellipse/ry 60             ;; vertical radius
 :style/fill [:color/name \"indianred\"]}"]]
       [:h4 "Arc"]
       [:p "A partial ellipse — like a pie slice or an open curve:"]
       [:pre {:data-img "docs-arc.png"} [:code
              "{:node/type :shape/arc
 :arc/center [200 200]
 :arc/rx 80 :arc/ry 80
 :arc/start 0 :arc/extent 270   ;; degrees
 :arc/mode :pie                  ;; :open, :chord, or :pie
 :style/fill [:color/name \"gold\"]}"]]
       [:h4 "Line"]
       [:pre {:data-img "docs-line.png"} [:code
              "{:node/type :shape/line
 :line/from [50 50]
 :line/to [350 250]
 :style/stroke {:color [:color/name \"black\"] :width 2}}"]]
       [:h4 "Path"]
       [:p "For anything that isn't a basic shape, use a path. Paths are sequences of drawing commands — move to a point, draw a line, draw a curve, and close the shape:"]
       [:pre {:data-img "docs-path.png"} [:code
              "{:node/type :shape/path
 :path/commands [[:move-to [100 200]]          ;; pick up the pen
                 [:line-to [200 50]]           ;; draw a straight line
                 [:curve-to [250 0] [300 100]  ;; cubic bezier curve
                            [300 200]]         ;;   (two control points + end)
                 [:quad-to [250 250]           ;; quadratic bezier curve
                           [200 200]]          ;;   (one control point + end)
                 [:close]]                     ;; connect back to start
 :style/fill [:color/name \"gold\"]}"]]
       [:h4 "Convenience helpers"]
       [:p "The " [:code "eido.scene"] " namespace provides shortcuts for common shapes:"]
       [:pre {:data-img "docs-helpers.png"} [:code
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
       [:pre {:data-img "docs-text.png"} [:code
              "{:node/type   :shape/text
 :text/content \"Hello\"
 :text/font    {:font/family \"Serif\" :font/size 48 :font/weight :bold}
 :text/origin  [50 100]       ;; baseline-left anchor
 :text/align   :center        ;; :left (default), :center, :right
 :style/fill   [:color/name \"black\"]}"]]
       [:h4 "Per-glyph control"]
       [:p "Style each character independently — great for rainbow text, animated reveals, or creative typography:"]
       [:pre {:data-img "docs-text-glyphs.png"} [:code
              "{:node/type    :shape/text-glyphs
 :text/content \"COLOR\"
 :text/font    {:font/family \"SansSerif\" :font/size 64}
 :text/origin  [50 100]
 :text/glyphs  [{:glyph/index 0 :style/fill [:color/name \"red\"]}
                {:glyph/index 1 :style/fill [:color/name \"limegreen\"]}]
 :style/fill   [:color/name \"gray\"]}  ;; default for unlisted glyphs"]]
       [:h4 "Text on a path"]
       [:p "Make text follow any curve:"]
       [:pre {:data-img "docs-text-on-path.png"} [:code
              "{:node/type    :shape/text-on-path
 :text/content \"ALONG A CURVE\"
 :text/font    {:font/family \"SansSerif\" :font/size 24}
 :text/path    [[:move-to [50 200]]
                [:curve-to [150 50] [350 50] [450 200]]]
 :text/offset  10             ;; start distance along path
 :text/spacing 1              ;; extra inter-glyph spacing
 :style/fill   [:color/name \"black\"]}"]]
       [:p "Fonts reference system fonts by name. Java's built-in fonts — "
        [:code "\"Serif\""] ", " [:code "\"SansSerif\""] ", " [:code "\"Monospaced\""]
        " — work on every system."]]}]}

   {:category "Styling"
    :id       "styling"
    :intro    [:div
               [:p "Shapes on their own are just geometry. Styling is what makes them visible — fills, strokes, gradients, and textures. Eido gives you a wide range of options, from flat colors to procedural hatching, all specified as data in the same shape map."]]
    :sections
    [{:id    "colors"
      :title "Colors"
      :content
      [:div
       [:p "Eido understands several color formats. Use whichever feels natural — they all work everywhere:"]
       [:pre {:data-img "docs-color-formats.png"} [:code
              "[:color/name \"coral\"]         ;; 148 CSS named colors
[:color/rgb 255 127 80]       ;; red, green, blue (0-255)
[:color/rgba 255 127 80 0.5]  ;; with transparency (0-1)
[:color/hsl 16 1.0 0.66]     ;; hue, saturation, lightness
[:color/hex \"#FF7F50\"]       ;; hex notation"]]
       [:p "All five lines above describe the same color — coral. Use whichever format suits your workflow."]
       [:h4 "Color manipulation"]
       [:p "Adjust colors programmatically — lighten, darken, blend, or shift the hue:"]
       [:pre {:data-img "docs-color-manip.png"} [:code
              "(require '[eido.color :as color])

(color/lighten    [:color/name \"red\"] 0.2) ;; lighter
(color/darken     [:color/name \"red\"] 0.2) ;; darker
(color/saturate   [:color/name \"red\"] 0.3) ;; more vivid
(color/rotate-hue [:color/name \"red\"] 120) ;; shift hue
(color/lerp color-a color-b 0.5)             ;; blend 50/50"]]]}

     {:id    "stroke-styling"
      :title "Strokes"
      :content
      [:div
       [:p "Strokes are the outlines around shapes. You can control the width, the shape of line endings (caps), and add dashed patterns:"]
       [:pre {:data-img "docs-strokes.png"} [:code
              ";; Thick rounded caps (top left) vs. butt caps (top right)
{:style/stroke {:color [:color/name \"black\"]
                :width 6
                :cap :round}}    ;; :butt, :round, or :square

;; Dashed lines in different patterns
{:style/stroke {:color [:color/name \"royalblue\"]
                :width 3
                :dash [15 8]}}   ;; alternating dash and gap lengths"]]
       [:p "A shape can have both a fill and a stroke — the stroke is drawn on top."]]}

     {:id    "gradients"
      :title "Gradients"
      :content
      [:div
       [:p "Instead of a flat color, fill a shape with a smooth color transition. Eido supports two kinds:"]
       [:h4 "Linear gradient"]
       [:p "Colors transition along a line from one point to another:"]
       [:pre {:data-img "docs-gradient-linear.png"} [:code
              "{:style/fill {:gradient/type :linear
               :gradient/from [0 0]       ;; start point
               :gradient/to [200 0]       ;; end point
               :gradient/stops [[0.0 [:color/rgb 255 0 0]]    ;; red at start
                                [1.0 [:color/rgb 0 0 255]]]}}" ;; blue at end
              ]]
       [:h4 "Radial gradient"]
       [:p "Colors radiate outward from a center point:"]
       [:pre {:data-img "docs-gradient-radial.png"} [:code
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
       [:pre {:data-img "docs-hatch.png"} [:code
              "{:style/fill {:fill/type :hatch
               :hatch/angle 45            ;; line angle in degrees
               :hatch/spacing 4           ;; distance between lines
               :hatch/stroke-width 1
               :hatch/color [:color/rgb 0 0 0]}}"]]
       [:h4 "Stippling"]
       [:p "Random dots packed inside a shape — like pointillism:"]
       [:pre {:data-img "docs-stipple.png"} [:code
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
    :intro    [:div
               [:p "Once you have shapes, you'll want to combine them — layer them, group them, clip one inside another, or transform them as a unit. Composition tools let you build complex images from simple pieces without losing control."]]
    :sections
    [{:id    "groups"
      :title "Groups"
      :content
      [:div
       [:p "Groups let you treat multiple shapes as one unit. Any style, transform, or effect applied to the group affects all its children. Styles "
        [:em "inherit"] " — children get the group's fill color unless they specify their own. Opacity " [:em "multiplies"] " through the tree."]
       [:pre {:data-img "docs-group.png"} [:code
              "{:node/type :group
 :node/transform [[:transform/translate 200 200]]
 :style/fill [:color/name \"red\"]
 :node/opacity 0.8
 :group/children
 [{:node/type :shape/circle        ;; inherits red fill
   :circle/center [0 0]
   :circle/radius 80}
  {:node/type :shape/rect
   :rect/xy [-30 -30]
   :rect/size [60 60]
   :style/fill [:color/name \"blue\"]  ;; overrides with blue
   :node/opacity 0.5}]}"              ;; effective opacity: 0.8 * 0.5 = 0.4
              ]]]}

     {:id    "clipping"
      :title "Clipping"
      :content
      [:div
       [:p "Clipping restricts a group's visible area to a shape — like looking through a circular window. Here, three overlapping colored rectangles are clipped to a circle:"]
       [:pre {:data-img "docs-clipping.png"} [:code
              "{:node/type :group
 :group/clip {:node/type :shape/circle
              :circle/center [150 150]
              :circle/radius 100}
 :group/children
 [{:node/type :shape/rect
   :rect/xy [50 50] :rect/size [100 200]
   :style/fill [:color/name \"red\"]}
  {:node/type :shape/rect
   :rect/xy [150 50] :rect/size [100 200]
   :style/fill [:color/name \"royalblue\"]}
  {:node/type :shape/rect
   :rect/xy [50 50] :rect/size [200 100]
   :style/fill [:color/rgba 255 220 0 0.5]}]}"]]]}

     {:id    "compositing"
      :title "Compositing"
      :content
      [:div
       [:p "Control how overlapping shapes blend together. Opacity makes shapes see-through, and blend modes combine colors in different ways — like layer modes in Photoshop:"]
       [:pre {:data-img "docs-compositing.png"} [:code
              ";; Two overlapping circles — the blue one is 60% transparent
{:node/type :shape/circle
 :circle/center [110 100] :circle/radius 70
 :style/fill [:color/name \"red\"]}
{:node/type :shape/circle
 :circle/center [190 100] :circle/radius 70
 :style/fill [:color/name \"royalblue\"]
 :node/opacity 0.6}"]]
       [:p "Available blend modes: " [:code ":src-over"] " (default), " [:code ":multiply"]
        ", " [:code ":screen"] ", " [:code ":overlay"] ", and more."]]}

     {:id    "transforms"
      :title "Transforms"
      :content
      [:div
       [:p "Move, rotate, scale, and skew any shape or group. Here, five squares are translated to different positions and progressively rotated:"]
       [:pre {:data-img "docs-transforms.png"} [:code
              ";; Each square is translated and rotated a bit more than the last
{:node/transform [[:transform/translate 100 80]
                  [:transform/rotate 0.3]]}     ;; angle in radians"]]
       [:p "Transforms compose through the tree — a shape inside a translated group inherits the group's transform, then applies its own on top."]]}]}

   {:category "Generative"
    :id       "generative"
    :intro    [:div
               [:p "This is where Eido really shines for artists. Instead of placing every shape by hand, you describe " [:em "rules and parameters"] " — and the system generates complex, organic compositions from them. Every generative tool is deterministic: give it the same " [:code "seed"] " number and you get the exact same output, every time. Change the seed and you get a fresh variation. This is how artists create long-form series of unique but related works."]
               [:p "If you're coming from Processing, p5.js, or similar tools, the concepts will feel familiar — noise, particles, flow fields — but in Eido they're all " [:em "data in, data out"] ". No draw loop, no mutable state. You describe what you want, and Eido produces it."]]
    :sections
    [{:id    "scene-helpers"
      :title "Layouts: Grids, Lines & Circles"
      :content
      [:div
       [:p "Before diving into algorithms, you'll want ways to arrange shapes in patterns. These layout helpers take a rule (a function) and apply it at every position in a grid, along a line, or around a circle:"]
       [:h4 "Grid"]
       [:p "Place something at every cell in a grid. Your function receives the column and row numbers — use them to vary size, color, or anything else:"]
       [:pre {:data-img "docs-grid.png"} [:code
              "(scene/grid 10 10
  (fn [col row]
    {:node/type :shape/circle
     :circle/center [(+ 30 (* col 40)) (+ 30 (* row 40))]
     :circle/radius 15
     :style/fill [:color/rgb (* col 25) (* row 25) 128]}))"]]
       [:h4 "Distribute along a line"]
       [:p "Spread shapes evenly between two points. The " [:code "t"] " parameter goes from 0 at the start to 1 at the end — use it for gradual size or color changes:"]
       [:pre {:data-img "docs-distribute.png"} [:code
              "(scene/distribute 8 [50 200] [750 200]
  (fn [x y t]   ;; t is progress 0 to 1
    {:node/type :shape/circle
     :circle/center [x y]
     :circle/radius (+ 5 (* 20 t))
     :style/fill [:color/rgb 0 0 0]}))"]]
       [:h4 "Radial arrangement"]
       [:p "Arrange shapes in a circle — like numbers on a clock face:"]
       [:pre {:data-img "docs-radial.png"} [:code
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
       [:p "Contour lines connect points of equal value — like elevation lines on a topographic map. Think of it as slicing through a noise landscape at different heights and tracing where each slice hits. The result is those organic, flowing lines you see in terrain maps and generative posters:"]
       [:pre {:data-img "docs-contour.png"} [:code
              "(require '[eido.gen.contour :as contour])

{:node/type :contour
 :contour/bounds [0 0 500 400]
 :contour/opts {:thresholds [0.0 0.2 0.4]  ;; which \"heights\" to trace
                :resolution 3               ;; detail level
                :noise-scale 0.012          ;; smaller = smoother hills
                :seed 42}                   ;; change for a new landscape
 :style/stroke {:color [:color/rgb 100 150 100] :width 1}}"]]]}

     {:id    "noise"
      :title "Noise"
      :content
      [:div
       [:p "Noise is the secret ingredient behind organic-looking generative art. Unlike plain random numbers (which look like TV static), noise produces " [:em "smooth"] " randomness — nearby points get similar values, creating natural-looking gradients, hills, and flows:"]
       [:pre {:data-img "docs-noise-field.png"} [:code
              "(require '[eido.gen.noise :as noise])

;; Smooth 2D noise: feed in a position, get a value from -1 to 1
(noise/perlin2d x y)

;; 3D noise: use the third dimension as time for animated effects
(noise/perlin3d x y z)

;; Fractal noise: layer multiple scales for richer detail
(noise/fbm noise/perlin2d x y
  {:octaves 4 :seed 42})"]]
       [:p "The " [:code ":seed"] " controls which particular landscape you get. Same seed, same landscape. The " [:code ":octaves"] " parameter in " [:code "fbm"] " adds layers of detail — like zooming into a coastline where you see detail at every scale."]]}

     {:id    "particles"
      :title "Particles"
      :content
      [:div
       [:p "Particle systems simulate many small objects — sparks, snowflakes, smoke, confetti — moving under physics forces. You describe the behavior (where particles spawn, how long they live, what forces act on them) and Eido simulates the result. Same seed, same simulation, every time."]
       [:pre {:data-img "docs-particles.gif"} [:code
              "(require '[eido.gen.particle :as particle])

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
        ". Start from a preset and tweak it — change gravity, lifetime, colors — using "
        [:code "assoc"] " and " [:code "update"] "."]]}

     {:id    "probability"
      :title "Controlling Randomness"
      :content
      [:div
       [:p "Plain randomness gives you chaos. " [:em "Shaped"] " randomness gives you art. Instead of \"pick any number,\" you can say things like \"pick a size, but most should be small with occasional large ones\" or \"choose a color, but make red rare.\" That's what this module is for."]
       [:p "The key idea: every function takes a " [:code "seed"] " — a number that locks the result. Same seed, same output, always. Change the seed and you get a fresh variation. This is how you explore, then freeze a result you like."]
       [:h4 "Spread evenly vs. cluster naturally"]
       [:p [:code "uniform"] " scatters values evenly across a range (top row). "
        [:code "gaussian"] " clusters them around a center with natural falloff (bottom row) — the \"bell curve\" shape you see everywhere in nature:"]
       [:pre {:data-img "docs-uniform-vs-gaussian.png"} [:code
              "(require '[eido.gen.prob :as prob])

;; Top: 80 dots spread evenly between 20 and 380
(prob/uniform 80 20.0 380.0 42)

;; Bottom: 80 dots clustered around 200 (mean=200, sd=50)
(prob/gaussian 80 200.0 50.0 99)"]]
       [:h4 "Weighted choice — controlling frequency"]
       [:p "This is how you control what appears most often. Give each option a weight — higher weight means more likely. Here, circles appear 6x more often than triangles:"]
       [:pre {:data-img "docs-weighted-shapes.png"} [:code
              ";; 60% circles (blue), 30% squares (gold), 10% triangles (red)
(prob/pick-weighted [:circle :square :triangle]
                    [6 3 1] seed)"]]
       [:p "Try changing the weights to see how the balance shifts. Weights of "
        [:code "[1 1 1]"] " give equal frequency; " [:code "[10 1 1]"]
        " makes the first option dominant."]
       [:h4 "Coin flips and shuffling"]
       [:pre [:code
              ";; Should this element be fancy? 30% chance
(prob/coin 0.3 seed)

;; Shuffle a list in a repeatable way
(prob/shuffle-seeded [1 2 3 4 5] seed)"]]
       [:p "These tools feed naturally into palette sampling, series parameters, and per-item variation — giving you precise artistic control over what would otherwise be pure chance."]]}

     {:id    "circle-packing"
      :title "Circle Packing"
      :content
      [:div
       [:p "Fill a region with circles of varying sizes, packed tightly without overlapping — like bubbles in a glass or cells under a microscope. It's one of the most visually striking generative techniques and appears constantly in contemporary generative art."]
       [:pre {:data-img "docs-circle-pack.png"} [:code
              "(require '[eido.gen.circle :as circle])

;; Pack circles into a region, color them with a weighted palette
(let [circles (circle/circle-pack 0 0 400 400
                {:min-radius  3       ;; smallest circle
                 :max-radius  35      ;; largest circle
                 :padding     2       ;; gap between circles
                 :max-circles 200     ;; stop after this many
                 :seed        42})    ;; change for a new arrangement
      colors (palette/weighted-sample
               (:sunset palette/palettes)
               [3 2 2 1 5] (count circles) 42)]
  ;; Draw each circle with its sampled color
  ...)"]]
       [:p "Tweak " [:code ":min-radius"] " and " [:code ":max-radius"]
        " to control the size range. Lower " [:code ":padding"]
        " for tighter packing. Increase " [:code ":max-circles"]
        " to fill more space."]
       [:h4 "Packing into shapes"]
       [:p "Pack circles inside any closed shape — stars, text outlines, hand-drawn blobs:"]
       [:pre {:data-img "docs-circle-pack-star.png"} [:code
              ";; Pack circles inside a star — each one a different hue
(circle/circle-pack-in-path
  (:path/commands (scene/star [200 200] 180 70 5))
  {:min-radius 2 :max-radius 15 :seed 42})"]]
       [:p "The result is always plain data — a list of positions and radii. You choose how to draw them."]]}

     {:id    "subdivision"
      :title "Rectangular Subdivision"
      :content
      [:div
       [:p "Start with one big rectangle and split it again and again into smaller cells — like a Mondrian painting, a newspaper layout, or an abstract quilt. Each split chooses a random direction and position, creating organic-looking grids that feel structured but not mechanical."]
       [:pre {:data-img "docs-subdivide.png"} [:code
              "(require '[eido.gen.subdivide :as sub])

(sub/subdivide 0 0 400 400
  {:depth       4          ;; how many times to split
   :min-size    35         ;; don't make cells smaller than this
   :split-range [0.3 0.7]  ;; how uneven splits can be
   :padding     5          ;; gap between cells
   :seed        77})"]]
       [:p "Increase " [:code ":depth"] " for finer divisions. Widen " [:code ":split-range"]
        " (e.g. " [:code "[0.15 0.85]"] ") for more dramatic size differences. Set "
        [:code ":h-bias"] " to " [:code "0.0"] " for only vertical splits or "
        [:code "1.0"] " for only horizontal."]
       [:p "Each cell knows its " [:code ":depth"]
        " — use that to vary color, texture, or content. The real power comes from filling each cell with something different: a circle pack in one, a flow field in another, a flat color in a third."]]}

     {:id    "weighted-palettes"
      :title "Weighted Palettes"
      :content
      [:div
       [:p "Real generative art uses color with intention — 60% neutral, 30% primary, 10% accent. Weighted palettes give you explicit control over how often each color appears:"]
       [:pre {:data-img "docs-weighted-palette.png"} [:code
              "(require '[eido.color.palette :as palette])

;; Sample 100 colors from a palette with weights
;; Neutral dominates, accent is rare
(palette/weighted-sample
  [[:color/rgb 240 235 225]    ;; neutral  — weight 5
   [:color/rgb 200 50 50]      ;; primary  — weight 2
   [:color/rgb 50 120 200]     ;; secondary — weight 2
   [:color/rgb 255 200 0]]     ;; accent   — weight 1
  [5 2 2 1]
  100 seed)"]]
       [:p "The bar chart above shows the sampled colors in order — notice how the neutral cream dominates, while the gold accent appears sparingly. Change the weights to shift the balance."]
       [:p [:code "weighted-gradient"] " creates gradient stops where each color occupies proportional space — feed into " [:code "gradient-map"] " for smooth interpolation. "
        [:code "shuffle-palette"] " randomizes color order with a seed — great for giving each edition a different arrangement from the same palette."]]}

     {:id    "path-aesthetics"
      :title "Path Aesthetics"
      :content
      [:div
       [:p "Three helpers that transform any path into something that looks more organic, hand-drawn, or stylized. They work on path commands and return path commands, so you can chain them freely."]
       [:h4 "Smoothing"]
       [:p "Turn angular polylines (gray) into flowing curves (red). The " [:code ":samples"] " option controls how many points to fit — more points means a tighter fit:"]
       [:pre {:data-img "docs-smooth-vs-raw.png"} [:code
              "(require '[eido.path.aesthetic :as aesthetic])

(aesthetic/smooth-commands path-cmds {:samples 40})"]]
       [:h4 "Jitter — hand-drawn wobble"]
       [:p "Add organic irregularity to any path. Control the intensity with " [:code ":amount"] " — subtle (blue, amount 4) vs. dramatic (red, amount 12):"]
       [:pre {:data-img "docs-jitter.png"} [:code
              "(aesthetic/jittered-commands path-cmds
  {:amount 4.0   ;; displacement intensity
   :seed 42})    ;; change seed for different wobble"]]
       [:h4 "Dashing"]
       [:p "Break a continuous path into dash segments. Three different dash patterns on the same line:"]
       [:pre {:data-img "docs-dashes.png"} [:code
              ";; Top: 15px on, 8px off
(aesthetic/dash-commands path-cmds {:dash [15.0 8.0]})

;; Middle: long dashes
(aesthetic/dash-commands path-cmds {:dash [30.0 5.0]})

;; Bottom: dots
(aesthetic/dash-commands path-cmds {:dash [5.0 15.0]})"]]
       [:h4 "Combining them — dashed flow field"]
       [:p "The real power is chaining: smooth a flow field, then dash it. Each streamline becomes a series of short, flowing strokes:"]
       [:pre {:data-img "docs-dashed-flow.png"} [:code
              "(let [paths (flow/flow-field 20 20 460 360
                {:density 30 :steps 35 :seed 42})]
  (mapcat (fn [path]
            (-> (:path/commands path)
                (aesthetic/smooth-commands {:samples 30})
                (aesthetic/dash-commands {:dash [10.0 6.0]})))
          paths))"]]
       [:p "Try different " [:code ":dash"] " ratios, " [:code ":density"]
        " values, and " [:code ":seed"] "s to find the feel you want."]]}

     {:id    "series"
      :title "Long-Form Series"
      :content
      [:div
       [:p "For Art Blocks / fxhash-style workflows: one algorithm, many outputs, each keyed by an edition number. You define a parameter spec — which values vary and how — and the series module generates independent, deterministic parameters for each edition:"]
       [:pre {:data-img "docs-series-grid.png"} [:code
              "(require '[eido.gen.series :as series])

;; Define what varies across editions
(def spec
  {:hue {:type :uniform :lo 0 :hi 360}
   :r   {:type :gaussian :mean 20 :sd 8}})

;; Generate parameters for editions 0-8
(series/series-range spec master-seed 0 9)
;; Each edition gets different hue and radius values"]]
       [:p "The grid above shows 9 editions from the same spec — each with a unique hue and size, all derived deterministically from the master seed. Edition 41 and edition 42 are completely uncorrelated despite being neighbors."]
       [:p "Available parameter types: " [:code ":uniform"] " (even spread), "
        [:code ":gaussian"] " (clustered), " [:code ":choice"] " (pick from a list), "
        [:code ":weighted-choice"] " (pick with weights), and "
        [:code ":boolean"] " (coin flip with probability)."]]}

     {:id    "cellular-automata"
      :title "Cellular Automata & Reaction-Diffusion"
      :content
      [:div
       [:p "Some of the most mesmerizing organic patterns come from simple rules applied to a grid, over and over. Cells interact with their neighbors, and complex behavior " [:em "emerges"] " — coral-like growth, dividing cells, rippling waves."]
       [:h4 "Cellular Automata"]
       [:p "The classic Game of Life — and any custom rule set. Start with a random grid, run it forward, and render the result. Each generation, cells are born or die based on how many living neighbors they have:"]
       [:pre {:data-img "docs-ca-life.png"} [:code
              "(require '[eido.gen.ca :as ca])

(let [grid    (ca/ca-grid 50 50 :random 42)    ;; random starting state
      evolved (ca/ca-run grid :life 50)]        ;; run 50 generations
  (ca/ca->nodes evolved 10                      ;; 10px per cell
    {:style/fill [:color/rgb 30 30 30]}))"]]
       [:p "Try " [:code ":highlife"] " for a different flavor, or define your own rules with "
        [:code "{:birth #{3 6} :survive #{2 3}}"] " — specify exactly how many neighbors cause birth or survival."]
       [:h4 "Reaction-Diffusion"]
       [:p "Two invisible chemicals spread across a surface and react with each other, creating organic spots, stripes, and coral-like growth. This is the math behind animal skin patterns and mineral formations. Eido includes named presets so you can jump right in:"]
       [:pre {:data-img "docs-rd-coral.png"} [:code
              ";; Grow coral-like patterns from a center seed
(let [grid   (ca/rd-grid 80 80 :center-seed 42)
      result (ca/rd-run grid (:coral ca/rd-presets) 400)]
  (ca/rd->nodes result 5
    (fn [a b]  ;; a and b are the two chemical concentrations
      [:color/rgb
       (int (+ 10 (* 80 (min 1.0 (* b 4)))))
       (int (+ 20 (* 120 (min 1.0 (* b 4)))))
       (int (+ 40 (* 180 (- 1.0 (* a 0.3)))))])))"]]
       [:p "Presets: " [:code ":coral"] " (branching growth), "
        [:code ":mitosis"] " (dividing cells), "
        [:code ":waves"] " (rippling patterns), "
        [:code ":spots"] " (leopard-like dots). For animation, call "
        [:code "rd-step"] " once per frame."]]}

     {:id    "boids"
      :title "Boids & Flocking"
      :content
      [:div
       [:p "Ever watched a flock of starlings twist through the sky? Each bird follows three simple rules: don't crowd your neighbors (separation), fly the same direction as them (alignment), and stay close to the group (cohesion). From these three rules, beautiful swirling patterns emerge — no leader, no choreography."]
       [:pre {:data-img "docs-boids.gif"} [:code
              "(require '[eido.gen.boids :as boids])

;; Create and simulate a flock
(let [frames (boids/simulate-flock boids/classic 80 {})]
  ;; Render each frame as oriented triangles
  (anim/frames (count frames)
    (fn [t]
      (let [flock (nth frames (int (* t (dec (count frames)))))]
        {:image/size [500 350]
         :image/nodes
         (boids/flock->nodes flock
           {:shape :triangle :size 7
            :style {:style/fill [:color/rgb 40 45 55]}})}))))"]]
       [:p "Presets: " [:code "boids/classic"] " (balanced, natural flocking) and "
        [:code "boids/murmuration"] " (tight starling-like swarming). Add optional behaviors like " [:code ":seek"] " (steer toward a point), " [:code ":flee"] " (steer away), or " [:code ":wander"] " (noise-based drifting) by adding them to the config."]]}]}

   {:category "Animation"
    :id       "animation"
    :intro    [:div
               [:p "Animation in Eido is just a sequence of scenes — one per frame. There's no timeline, no keyframe system, no mutable state. You write a function that turns a progress value into a scene, and Eido calls it once per frame to produce a GIF or video."]]
    :sections
    [{:id    "animation-basics"
      :title "Creating Animations"
      :content
      [:div
       [:p "An animation in Eido is just a sequence of scenes — one per frame. There's no timeline, no keyframe system, no mutable state. You write a function that takes a progress value "
        [:code "t"] " (from 0 to 1) and returns a scene. Eido calls it once per frame:"]
       [:pre {:data-img "docs-animation.gif"} [:code
              "(require '[eido.animate :as anim])

(def frames
  (anim/frames 40    ;; 40 frames total
    (fn [t]          ;; t goes from 0.0 to 1.0
      {:image/size [250 250]
       :image/background [:color/rgb 30 30 40]
       :image/nodes
       [{:node/type :shape/circle
         :circle/center [125 125]
         :circle/radius (* 90 t)    ;; grows over time
         :style/fill [:color/hsl (* 360 t) 0.8 0.5]}]})))

;; Render as animated GIF
(eido/render frames {:output \"grow.gif\" :fps 20})"]]
       [:p "Since frames are just data, you can manipulate them with all the usual tools — "
        [:code "map"] ", " [:code "filter"] ", " [:code "concat"]
        " — to build complex sequences from simple parts."]]}

     {:id    "easing"
      :title "Easing & Helpers"
      :content
      [:div
       [:p "Easing functions make motion feel natural. Instead of moving at a constant speed (gray), things can accelerate and decelerate smoothly (blue):"]
       [:pre {:data-img "docs-easing.png"} [:code
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
       [:pre {:data-img "docs-3d-sphere.png"} [:code
              "(require '[eido.scene3d :as s3d])

(let [proj (s3d/perspective
             {:scale 120 :origin [200 200]
              :yaw 0.5 :pitch -0.3 :distance 5})
      light {:light/direction [1 1 0.5]
             :light/ambient 0.25
             :light/intensity 0.8}]
  (s3d/sphere proj [0 0 0] 1.5
    {:style {:style/fill [:color/name \"cornflowerblue\"]}
     :light light
     :subdivisions 3}))  ;; higher = smoother sphere"]]
       [:p "Available primitives: " [:code "sphere"] ", " [:code "cube"] ", "
        [:code "cone"] ", " [:code "torus"] ", " [:code "cylinder"]
        ". Load arbitrary meshes from OBJ files with " [:code "eido.io.obj/load-obj"] "."]
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

   {:category "Visual Computation"
    :id       "visual-computation"
    :sections
    [{:id    "procedural-fills"
      :title "Procedural Fills"
      :content
      [:div
       [:p "Procedural fills evaluate a program expression per pixel over a shape's bounds. The program is pure data — no functions, no macros — just nested vectors describing the computation."]
       [:pre [:code
              "(require '[eido.ir :as ir])
(require '[eido.ir.fill :as fill])
(require '[eido.ir.lower :as lower])

;; A rect filled with noise-driven color
(def scene
  (let [semantic
        (ir/container [400 400]
          {:r 20 :g 20 :b 30 :a 1.0}
          [(ir/draw-item
             (ir/rect-geometry [0 0] [400 400])
             :fill (fill/procedural
                     {:program/body
                      [:color/rgb
                       [:* 255
                           [:clamp [:+ 0.5
                                        [:* 0.5
                                            [:field/noise
                                             {:field/type :field/noise
                                              :field/scale 4.0
                                              :field/variant :fbm
                                              :field/seed 42}
                                             :uv]]]
                            0.0 1.0]]
                       100 200]}))])]
    {:ir (lower/lower semantic)}))"]]
       [:p "The program receives " [:code ":uv"] " as normalized [0..1] coordinates. It can use arithmetic, math functions, field sampling, color construction, and conditional logic."]
       [:h4 "Expression Language"]
       [:pre [:code
              ";; Arithmetic: [:+ a b], [:- a b], [:* a b], [:/ a b]
;; Math:       [:abs x], [:sqrt x], [:sin x], [:cos x], [:pow x n]
;; Vectors:    [:vec2 x y], [:vec3 x y z]
;; Access:     [:x v], [:y v]
;; Mixing:     [:mix a b t], [:clamp x lo hi]
;; Conditional: [:select pred a b]
;; Fields:     [:field/noise {field-desc} position]
;; Colors:     [:color/rgb r g b]"]]]}

     {:id    "fields"
      :title "Fields"
      :content
      [:div
       [:p "A field is a function over 2D space that returns a value. Fields are reusable descriptors that can be consumed by procedural fills, generators, and programs."]
       [:pre [:code
              "(require '[eido.ir.field :as field])

;; Noise field — wraps eido.gen.noise with configurable parameters
(def f (field/noise-field :scale 0.02 :variant :fbm
                          :seed 42 :octaves 6))

;; Evaluate at a point
(field/evaluate f 10.0 20.0)  ;; => -0.234...

;; Other field types
(field/constant-field 0.5)        ;; same value everywhere
(field/distance-field [100 100])  ;; distance from center"]]
       [:h4 "Noise Variants"]
       [:p [:code ":raw"] " — plain Perlin noise, "
        [:code ":fbm"] " — fractal Brownian motion (default), "
        [:code ":turbulence"] " — absolute-value fbm, "
        [:code ":ridge"] " — ridged multifractal."]]}

     {:id    "semantic-fills"
      :title "Semantic Fills"
      :content
      [:div
       [:p "The semantic IR preserves fill intent as data instead of expanding to geometry immediately. Fill constructors create descriptors that are lowered to concrete drawing operations at render time."]
       [:pre [:code
              "(require '[eido.ir.fill :as fill])

;; Solid color
(fill/solid [:color/rgb 200 50 50])

;; Gradient
(fill/gradient :linear
               [[0.0 [:color/rgb 255 0 0]]
                [1.0 [:color/rgb 0 0 255]]]
               :from [0 0] :to [200 0])

;; Hatch — preserved as semantic data through the pipeline
(fill/hatch {:hatch/angle 45 :hatch/spacing 5
             :hatch/color [:color/rgb 0 0 0]})

;; Stipple
(fill/stipple {:stipple/density 0.6 :stipple/radius 2
               :stipple/seed 42 :stipple/color [:color/rgb 0 0 0]})

;; Procedural — per-pixel program evaluation
(fill/procedural {:program/body [:color/rgb 255 0 0]})"]]]}

     {:id    "semantic-effects"
      :title "Semantic Effects"
      :content
      [:div
       [:p "Effects are explicit descriptors attached to draw items. They are lowered to buffer compositing operations at render time."]
       [:pre [:code
              "(require '[eido.ir :as ir])
(require '[eido.ir.effect :as effect])
(require '[eido.ir.fill :as fill])

(ir/draw-item
  (ir/rect-geometry [50 50] [200 150])
  :fill (fill/solid [:color/rgb 60 120 200])
  :effects [(effect/shadow :dx 5 :dy 5 :blur 10
                           :color [:color/rgb 0 0 0]
                           :opacity 0.5)
            (effect/glow :blur 12
                         :color [:color/rgb 100 200 255]
                         :opacity 0.6)])"]]
       [:h4 "Filter Effects"]
       [:p "Filter effects apply image-space processing: "
        [:code "blur"] ", " [:code "grain"] ", " [:code "posterize"] ", "
        [:code "duotone"] ", " [:code "halftone"] "."]
       [:pre [:code
              "(effect/grain :amount 40 :seed 42)
(effect/posterize :levels 4)
(effect/duotone :color-a [:color/rgb 20 20 60]
                :color-b [:color/rgb 255 230 180])
(effect/halftone :dot-size 8 :angle 45)"]]]}

     {:id    "transforms"
      :title "Transforms"
      :content
      [:div
       [:p "Semantic transforms modify geometry before rendering — distortion, warping, and path morphing."]
       [:pre [:code
              "(require '[eido.ir.transform :as transform])

;; Noise distortion on a path
(ir/draw-item
  (ir/path-geometry [[:move-to [0 100]] [:line-to [200 100]]])
  :fill (fill/solid [:color/rgb 0 0 0])
  :pre-transforms [(transform/distortion :noise
                     {:amplitude 10 :frequency 0.05 :seed 42})])

;; Warp a rect with wave deformation
(ir/draw-item
  (ir/rect-geometry [20 20] [160 160])
  :fill (fill/solid [:color/rgb 100 150 200])
  :pre-transforms [(transform/warp-transform :wave
                     {:axis :y :amplitude 15 :wavelength 40})])

;; Morph between two paths
(transform/morph-transform target-path 0.5)"]]]}

     {:id    "generators"
      :title "Generators"
      :content
      [:div
       [:p "Generators produce geometry procedurally — flow fields, contours, scatter distributions, Voronoi tessellation, decorators, and particles."]
       [:pre [:code
              "(require '[eido.ir.generator :as gen])

;; Flow field from noise
(gen/flow-field [0 0 400 300]
  :opts {:density 20 :steps 50 :seed 42}
  :style {:stroke {:color [:color/rgb 0 0 0] :width 1}})

;; Contour lines at thresholds
(gen/contour [0 0 400 300]
  :opts {:thresholds [-0.2 0.0 0.2] :resolution 5})

;; Scatter shapes at positions
(gen/scatter-gen shape-node [[50 50] [150 150]]
  :overrides (vary/by-gradient 2 [[0 red] [1 blue]]))

;; Voronoi tessellation
(gen/voronoi-gen points [0 0 400 300]
  :style {:stroke {:color [:color/rgb 0 0 0] :width 1}})

;; Particle snapshot at frame 30
(gen/particle-gen fire-config 30 60)"]]]}

     {:id    "materials"
      :title "3D Materials"
      :content
      [:div
       [:p "Material descriptors add specular highlights to 3D meshes using Blinn-Phong shading."]
       [:pre [:code
              "(require '[eido.ir.material :as material])
(require '[eido.scene3d :as s3d])

;; Phong material with specular highlights
(s3d/render-mesh projection mesh
  {:style {:style/fill [:color/rgb 150 100 200]
           :material (material/phong
                       :specular 0.4
                       :shininess 32.0)}
   :light {:light/direction [1 2 1]
           :light/ambient 0.2
           :light/intensity 0.8}})"]]
       [:h4 "Light Types"]
       [:pre [:code
              ";; Directional — parallel rays (like the sun)
(material/directional [1 2 1] :multiplier 0.8 :ambient 0.2)

;; Omni — point light radiating in all directions
(material/omni [100 50 200]
  :color [:color/rgb 255 200 150]
  :decay :inverse-square :decay-start 10.0)

;; Spot — cone with hotspot/falloff angles
(material/spot [0 200 0] [0 -1 0]
  :hotspot 25 :falloff 35 :decay :inverse)

;; Hemisphere — sky/ground ambient
(material/hemisphere
  [:color/rgb 135 180 220] [:color/rgb 40 30 20]
  :multiplier 0.3)"]]
       [:h4 "Multi-Light"]
       [:p "Use " [:code ":lights"] " to combine multiple lights. Each light's color tints its contribution."]
       [:pre [:code
              "(s3d/render-mesh proj mesh
  {:style {:style/fill [:color/rgb 200 200 200]
           :material (material/phong :specular 0.5)}
   :lights [(material/omni [3 2 2]
              :color [:color/rgb 255 180 100]
              :multiplier 1.5 :decay :inverse)
            (material/hemisphere
              [:color/rgb 40 50 80] [:color/rgb 15 10 5]
              :multiplier 0.2)]})"]]]}

     {:id    "multi-pass"
      :title "Multi-Pass Rendering"
      :content
      [:div
       [:p "Pipelines chain multiple passes — draw geometry, then apply effects."]
       [:pre [:code
              "(require '[eido.ir :as ir])
(require '[eido.ir.effect :as effect])

;; Draw shapes, then apply grain to the whole image
(ir/pipeline [400 400]
  background
  [{:pass/id :draw :pass/type :draw-geometry
    :pass/items [rect-item circle-item]}
   (ir/effect-pass :grain (effect/grain :amount 30 :seed 42))])"]]]}

     {:id    "domains"
      :title "Domains"
      :content
      [:div
       [:p "A domain describes the coordinate space over which a program or field is evaluated. Domains declare what bindings are available in the evaluation environment."]
       [:pre [:code
              "(require '[eido.ir.domain :as domain])

;; Image grid — pixel coordinates with UV
(domain/image-grid [800 600])
;; Bindings: :uv [0..1, 0..1], :px, :py, :size

;; World 2D — scene coordinates within bounds
(domain/world-2d [0 0 400 300])
;; Bindings: :pos [x y], :x, :y

;; Other domains: shape-local, path-param, mesh-faces,
;; points, particles, timeline"]]
       [:p "Programs with a " [:code ":program/domain"] " validate that the evaluation environment provides the expected bindings."]]}

     {:id    "resources"
      :title "Resources"
      :content
      [:div
       [:p "Resources are named objects that passes read and write. They make multi-pass data flow explicit."]
       [:pre [:code
              "(require '[eido.ir.resource :as resource])

;; Declare resources
(resource/image :buffer [400 300])
(resource/mask :alpha-mask [400 300])
(resource/parameter-block :params {:time 0.5 :seed 42})

;; Pipeline with explicit resources
(ir/pipeline [400 300] background
  [{:pass/id :draw :pass/type :draw-geometry
    :pass/target :framebuffer :pass/items [...]}
   {:pass/id :process :pass/type :effect-pass
    :pass/input :framebuffer :pass/target :output
    :pass/effect (effect/grain :amount 30)}]
  {:resources (resource/image :output [400 300])})

;; Validate that all passes reference declared resources
(resource/validate-pipeline-resources pipeline)"]]]}]}

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
       [:p "For a quick overview at the REPL, use " [:code "explain"] " to print formatted errors:"]
       [:pre [:code
              "(eido/explain {:image/size [800 600]
                :image/background [:color/rgb 255 0]
                :image/nodes [{:node/type :shape/polygon}]})
;; 2 validation errors:
;;
;;   1. at [:image/background]: integer in 0..255, got: ()
;;
;;   2. at [:image/nodes 0]: unknown node type; valid types are:
;;      :group, :shape/arc, :shape/circle, ..."]]
       [:p "You can also format error data with " [:code "format-errors"] ":"]
       [:pre [:code "(eido/format-errors (eido/validate scene))"]]
       [:p "Invalid scenes throw " [:code "ex-info"] " with " [:code ":errors"] " in the exception data and a human-readable message, so you always know what went wrong."]
       [:h4 "Validation in the REPL"]
       [:p "The dev helpers " [:code "show"] ", " [:code "watch-file"] ", and " [:code "watch-scene"] " validate the first render, then skip validation on subsequent re-renders for faster iteration. This gives you error checking when starting up while keeping the feedback loop fast."]
       [:p "To control validation explicitly, bind " [:code "eido/*validate*"] ":"]
       [:pre [:code
              ";; Skip validation for fast re-renders
(binding [eido/*validate* false]
  (eido/render scene))

;; Or disable per-scene with a key
(eido/render (assoc scene :eido/validate false))"]]
       [:p [:code "*validate*"] " defaults to " [:code "true"] ". Validation adds roughly 7% overhead per render, so skipping it in tight iteration loops makes a noticeable difference."]]}]}])

;; --- Architecture page ---

(def github-base "https://github.com/leifericf/eido/blob/main/")

(defn arch-src-link
  "Creates a GitHub source link for a file path."
  [path label]
  [:a.arch-src-link {:href (str github-base path) :target "_blank"} label])

(defn architecture-sections
  "Content for the 'How Eido Works' architecture page."
  []
  [;; --- The Big Picture ---
   {:id "big-picture"
    :title "The Big Picture"
    :content
    [:div
     [:p "A scene in Eido is a plain Clojure map. It goes through a pipeline of pure data transformations — validation, compilation, lowering, rendering — and comes out as pixels. No GPU, no OpenGL, no mutable state. Just functions that turn data into data, until the last step paints it onto a "
      [:code "BufferedImage"] " using Java2D."]
     [:div.arch-pipeline
      [:div.arch-step [:div.arch-step-label "Scene Map"] [:div.arch-step-desc "your data"]]
      [:div.arch-arrow "\u2192"]
      [:div.arch-step [:div.arch-step-label "Validate"] [:div.arch-step-desc "spec check"]]
      [:div.arch-arrow "\u2192"]
      [:div.arch-step [:div.arch-step-label "Semantic IR"] [:div.arch-step-desc "draw items"]]
      [:div.arch-arrow "\u2192"]
      [:div.arch-step [:div.arch-step-label "Lower"] [:div.arch-step-desc "expand generators"]]
      [:div.arch-arrow "\u2192"]
      [:div.arch-step [:div.arch-step-label "Concrete Ops"] [:div.arch-step-desc "flat records"]]
      [:div.arch-arrow "\u2192"]
      [:div.arch-step.arch-step--final [:div.arch-step-label "Render"] [:div.arch-step-desc "Java2D \u2192 pixels"]]]
     [:p "Each step is a pure function. The scene map goes in one end, a "
      [:code "BufferedImage"] " comes out the other. Every intermediate result is inspectable data — you can print it, diff it, serialize it. The rendering backend (currently Java2D) is isolated behind the concrete ops layer, so a future WebGL or Skia backend would only need to implement the final step."]]}

   ;; --- Step 1: Scene Map ---
   {:id "scene-map"
    :title "Step 1: The Scene Map"
    :content
    [:div
     [:p "Everything starts here. A scene is a map with three keys — the canvas size, a background color, and a vector of nodes. Each node is itself a map describing a shape, a group, or a generator:"]
     [:pre {:data-img "docs-arch-input.png"} [:code
            "{:image/size [400 300]
 :image/background [:color/name \"linen\"]
 :image/nodes
 [{:node/type     :shape/circle
   :circle/center [200 150]
   :circle/radius 80
   :style/fill    [:color/name \"coral\"]}
  {:node/type     :shape/rect
   :rect/xy       [50 50]
   :rect/size     [100 60]
   :style/fill    [:color/name \"steelblue\"]}]}"]]
     [:p "That's the entire input. No classes, no builder patterns, no inheritance. Nodes can be shapes ("
      [:code ":shape/circle"] ", " [:code ":shape/rect"] ", " [:code ":shape/path"]
      "), groups (" [:code ":group"] " with " [:code ":group/children"]
      "), or generators (" [:code ":flow-field"] ", " [:code ":contour"] ", " [:code ":scatter"]
      ") that produce shapes during compilation."]
     [:p (arch-src-link "src/eido/core.clj" "View eido.core on GitHub")]]}

   ;; --- Step 2: Validation ---
   {:id "validation"
    :title "Step 2: Validation"
    :content
    [:div
     [:p "Before any work happens, the scene is validated against a comprehensive spec. Color formats, node types, transform syntax, path commands — all checked. If something's wrong, you get a clear error pointing to exactly where:"]
     [:pre.arch-error [:code
            "Invalid scene
3 validation errors:

  1. at [:image/nodes 0 :circle/radius]: positive number, got: -5
  2. at [:image/nodes 1 :style/fill 0]: known color format, got: :color/invalid
  3. at [:image/nodes 1 :rect/size]: vector of [w h], got: 100"]]
     [:p "Each error tells you the path into the data structure, what was expected, and what was found. This catches mistakes at the boundary — before they become mysterious rendering glitches deep in the pipeline."]
     [:p "Validation uses " [:code "clojure.spec.alpha"]
      " with multimethod dispatch on " [:code ":node/type"]
      ". It's optional — bind " [:code "eido/*validate*"]
      " to " [:code "false"] " for faster REPL iteration once your scene structure is stable."]
     [:p (arch-src-link "src/eido/validate.clj" "View validation on GitHub") " · "
      (arch-src-link "src/eido/spec.clj" "View spec definitions on GitHub")]]}

   ;; --- Step 3: Semantic IR ---
   {:id "semantic-ir"
    :title "Step 3: Semantic IR"
    :content
    [:div
     [:p "The scene map is compiled into a " [:em "semantic intermediate representation"]
      " — a structured container that preserves your intent. Shapes become draw items with separate slots for geometry, fill, stroke, effects, and transforms. Generators and procedural fills are kept as-is — they haven't been expanded yet."]
     [:pre [:code
            ";; A circle node becomes a draw item:
{:item/geometry {:geometry/type :circle
                 :geometry/cx 200 :geometry/cy 150
                 :geometry/r 80}
 :item/fill     {:r 255 :g 127 :b 80 :a 1.0}
 :item/stroke   nil
 :item/opacity  1.0
 :item/transforms []}

;; A flow-field generator is preserved, not yet expanded:
{:item/generator {:generator/type :flow-field
                  :flow-field/bounds [0 0 400 300]
                  :flow-field/opts {:density 20 :steps 30 :seed 42}}
 :item/fill {:r 0 :g 0 :b 0 :a 1.0}}"]]
     [:p "Why two layers? Because generators like flow fields produce " [:em "hundreds"] " of path nodes when expanded. Keeping them as compact descriptions in the semantic IR means you can inspect, serialize, and diff scenes efficiently. The expansion happens in the next step — lowering."]
     [:p "The IR container wraps everything in a rendering pass structure:"]
     [:pre [:code
            "{:ir/version 1
 :ir/size [400 300]
 :ir/background {:r 250 :g 240 :b 230 :a 1.0}
 :ir/passes [{:pass/id :draw-main
              :pass/type :draw-geometry
              :pass/items [draw-item-1 draw-item-2 ...]}]
 :ir/outputs {:default :framebuffer}}"]]
     [:p (arch-src-link "src/eido/engine/compile.clj" "View compilation on GitHub")]]}

   ;; --- Step 4: Lowering ---
   {:id "lowering"
    :title "Step 4: Lowering"
    :content
    [:div
     [:p "This is where the magic happens. Lowering walks the semantic IR and expands everything into concrete drawing operations. Generators become shapes. Procedural fills become clipped lines or dots. Effects become offscreen buffer operations."]
     [:h4 "Generator expansion"]
     [:p "A flow-field description becomes hundreds of actual path nodes:"]
     [:pre {:data-img "docs-arch-flowfield.png"} [:code
            ";; Before lowering (semantic IR):
{:generator/type :flow-field
 :flow-field/bounds [0 0 400 300]
 :flow-field/opts {:density 25 :steps 30 :seed 42}}

;; After lowering (concrete ops):
[PathOp{:commands [[:move-to [23 45]] [:line-to [25 47]] ...]}
 PathOp{:commands [[:move-to [67 12]] [:line-to [69 14]] ...]}
 PathOp{:commands [[:move-to [112 89]] [:line-to [114 91]] ...]}
 ... ;; ~80 path ops from one generator
]"]]
     [:p "Each generator type calls its corresponding " [:code "eido.gen.*"]
      " module — flow fields call " [:code "eido.gen.flow/flow-field"]
      ", scatter calls " [:code "eido.gen.scatter/scatter->nodes"]
      ", and so on. The lowering step bridges the gap between the artist's intent and the renderer's needs."]
     [:h4 "Fill expansion"]
     [:p "Procedural fills like hatching and stippling are expanded into actual geometry, clipped to the shape they fill:"]
     [:pre {:data-img "docs-arch-hatch.png"} [:code
            ";; Before: a circle with a hatch fill (semantic IR)
{:geometry/type :circle :geometry/r 80
 :fill {:fill/type :hatch
        :hatch/angle 45 :hatch/spacing 4}}

;; After: concrete line ops clipped to the circle
[PathOp{:commands [...] :clip circle-area}
 PathOp{:commands [...] :clip circle-area}
 ...]"]]
     [:h4 "Effect wrapping"]
     [:p "Effects like shadows and glows become offscreen buffer operations — the shape is painted to a temporary image, the effect is applied, then composited onto the main canvas:"]
     [:pre [:code
            ";; Shadow effect → duplicate shape + blur + offset
BufferOp{:composite :src-over
         :filter {:type :blur :radius 8}
         :transforms [[:translate 4 4]]
         :ops [CircleOp{...shadow-color...}]}
CircleOp{...original-shape...}"]]
     [:p (arch-src-link "src/eido/ir/lower.clj" "View lowering on GitHub") " · "
      (arch-src-link "src/eido/ir/generator.clj" "View generator expansion on GitHub") " · "
      (arch-src-link "src/eido/ir/fill.clj" "View fill expansion on GitHub")]]}

   ;; --- Step 5: Concrete Ops ---
   {:id "concrete-ops"
    :title "Step 5: Concrete Ops"
    :content
    [:div
     [:p "After lowering, the entire scene is a flat vector of records — one per visible shape. No more nesting, no more generators, no more deferred computation. Just a sequence of drawing instructions:"]
     [:pre [:code
            "[CircleOp {:cx 200 :cy 150 :r 80
            :fill {:r 255 :g 127 :b 80 :a 1.0}
            :stroke-color nil :opacity 1.0
            :transforms [] :clip nil}
 RectOp   {:x 50 :y 50 :w 100 :h 60
            :fill {:r 70 :g 130 :b 180 :a 1.0}
            :stroke-color nil :opacity 1.0
            :transforms [] :clip nil}]"]]
     [:p "Each op is a Clojure record — a compiled JVM class with O(1) field access, but still implementing " [:code "IPersistentMap"] " so you can use " [:code "(:cx op)"] " like a regular map. The op types are:"]
     [:ul
      [:li [:code "RectOp"] " — rectangles (with optional corner radius)"]
      [:li [:code "CircleOp"] " — circles"]
      [:li [:code "EllipseOp"] " — ellipses"]
      [:li [:code "ArcOp"] " — arcs and pie slices"]
      [:li [:code "LineOp"] " — line segments"]
      [:li [:code "PathOp"] " — arbitrary paths (bezier curves, polygons, freeform)"]
      [:li [:code "BufferOp"] " — compositing groups (contains child ops, rendered to offscreen buffer)"]]
     [:p "This flat structure is what the renderer consumes. It's also what the SVG exporter reads — both backends work from the same concrete ops, just painting to different targets."]
     [:p (arch-src-link "src/eido/engine/compile.clj" "View op records on GitHub")]]}

   ;; --- Step 6: Rendering ---
   {:id "rendering"
    :title "Step 6: Rendering"
    :content
    [:div
     [:p "The renderer walks the op vector top to bottom, painting each shape onto a " [:code "BufferedImage"] " using Java2D's " [:code "Graphics2D"] " API. For each op:"]
     [:pre [:code
            ";; Pseudocode for the rendering loop:
(for-each op in ops
  1. Save Graphics2D state
  2. Apply transforms (translate, rotate, scale)
  3. Set clip region (if present)
  4. Set opacity via AlphaComposite
  5. Convert geometry to Java2D Shape
  6. Fill the shape (solid, gradient, or texture)
  7. Stroke the shape (if stroke specified)
  8. Restore Graphics2D state)"]]
     [:p [:code "BufferOp"] " groups get special handling — their children are rendered to a temporary offscreen image, post-processing filters (blur, grain, posterize) are applied, then the result is composited onto the main canvas using the specified blend mode (" [:code ":src-over"] ", " [:code ":multiply"] ", " [:code ":screen"] ", etc.)."]
     [:p "Java2D handles antialiasing, sub-pixel positioning, and bezier curve rasterization. Eido doesn't implement a software rasterizer — it leans on the JVM's mature 2D graphics stack."]
     [:p (arch-src-link "src/eido/engine/render.clj" "View renderer on GitHub")]]}

   ;; --- Step 7: Output ---
   {:id "output"
    :title "Step 7: Output"
    :content
    [:div
     [:p "The " [:code "BufferedImage"] " is the universal intermediate. Every raster output format reads from it:"]
     [:ul
      [:li [:strong "PNG"] " — via " [:code "ImageIO.write"] " (with optional DPI metadata for print)"]
      [:li [:strong "JPEG"] " — ARGB composited onto white, then written with quality setting"]
      [:li [:strong "GIF"] " — single frame via ImageIO, animated via a custom GIF encoder that writes frame delays and loop flags"]
      [:li [:strong "BMP"] " — via ImageIO (RGB)"]]
     [:p "SVG takes a completely different path — it reads the concrete ops directly and emits XML elements (" [:code "<rect>"] ", " [:code "<circle>"] ", " [:code "<path>"] ") instead of painting pixels. Same ops, different output."]
     [:p "Animations are just sequences of scenes. Eido renders each frame independently, then stitches them together:"]
     [:pre [:code
            ";; 60 scenes → 60 BufferedImages → animated GIF
(eido/render
  (anim/frames 60
    (fn [t] {:image/size [400 300] ...}))
  {:output \"animation.gif\" :fps 30})"]]
     [:p (arch-src-link "src/eido/engine/gif.clj" "View GIF encoder on GitHub") " · "
      (arch-src-link "src/eido/engine/svg.clj" "View SVG exporter on GitHub")]]}

   ;; --- Design Decisions ---
   {:id "design"
    :title "Design Decisions"
    :content
    [:div
     [:h4 "Why two IR layers?"]
     [:p "The semantic IR keeps the artist's intent intact — a flow field is one compact description, not 200 path nodes. This makes scenes diffable, serializable, and inspectable. The concrete IR is optimized for rendering — flat, no generators, every shape fully resolved. Separating these concerns means you can work with scenes at the right level of abstraction for each task."]
     [:h4 "Why CPU rendering?"]
     [:p "Java2D runs everywhere the JVM runs — no GPU drivers, no platform-specific shader compilation, no WebGL context limits. The output is deterministic (same input → same pixels, always), which matters for reproducible generative art. For the image sizes generative artists typically work with (up to ~4K), CPU rendering is fast enough. A GPU backend could be added later by implementing the concrete ops → pixels step without changing anything else."]
     [:h4 "Why records for concrete ops?"]
     [:p [:code "defrecord"] " gives O(1) field access (compiled JVM class) while still acting as an immutable map. The renderer touches " [:code ":cx"] ", " [:code ":cy"] ", " [:code ":fill"]
      " etc. on every op — fast field access matters in the inner loop."]
     [:h4 "Why expand generators during lowering?"]
     [:p "Generators depend on geometry for their output (a flow field needs to know its bounds, a hatch fill needs the shape it's filling). By the time lowering runs, geometry is resolved. Expanding earlier would require passing incomplete information; expanding later would force the renderer to understand generators. Lowering is the natural boundary."]
     [:h4 "Data all the way down"]
     [:p "Every intermediate result in the pipeline is printable, serializable Clojure data. No opaque objects, no hidden state. You can " [:code "prn"]
      " the semantic IR, " [:code "prn"] " the concrete ops, save them to a file, load them back, or write tests against them. Hell, even store them in a "
      [:a {:href "https://www.datomic.com" :target "_blank"} "Datomic"]
      " database if you want. This is the core design principle — the image is a value."]]}

   ;; --- Source Map ---
   {:id "source-map"
    :title "Source Map"
    :content
    [:div
     [:p "Key namespaces and what they do:"]
     [:table.arch-source-table
      [:thead [:tr [:th "Namespace"] [:th "Role"] [:th "Source"]]]
      [:tbody
       [:tr [:td [:code "eido.core"]] [:td "Entry point — " [:code "render"] ", file I/O, format detection"] [:td (arch-src-link "src/eido/core.clj" "core.clj")]]
       [:tr [:td [:code "eido.validate"]] [:td "Scene validation with detailed error messages"] [:td (arch-src-link "src/eido/validate.clj" "validate.clj")]]
       [:tr [:td [:code "eido.spec"]] [:td "Spec definitions for nodes, colors, transforms"] [:td (arch-src-link "src/eido/spec.clj" "spec.clj")]]
       [:tr [:td [:code "eido.engine.compile"]] [:td "Scene → Semantic IR, concrete op records"] [:td (arch-src-link "src/eido/engine/compile.clj" "compile.clj")]]
       [:tr [:td [:code "eido.ir.lower"]] [:td "Semantic IR → Concrete ops (generator expansion, fill resolution)"] [:td (arch-src-link "src/eido/ir/lower.clj" "lower.clj")]]
       [:tr [:td [:code "eido.ir.generator"]] [:td "Expands flow-field, scatter, voronoi, contour, etc."] [:td (arch-src-link "src/eido/ir/generator.clj" "generator.clj")]]
       [:tr [:td [:code "eido.ir.fill"]] [:td "Expands hatch and stipple fills into geometry"] [:td (arch-src-link "src/eido/ir/fill.clj" "fill.clj")]]
       [:tr [:td [:code "eido.ir.effect"]] [:td "Wraps effects (shadow, glow, blur) as buffer ops"] [:td (arch-src-link "src/eido/ir/effect.clj" "effect.clj")]]
       [:tr [:td [:code "eido.engine.render"]] [:td "Concrete ops → BufferedImage via Java2D"] [:td (arch-src-link "src/eido/engine/render.clj" "render.clj")]]
       [:tr [:td [:code "eido.engine.svg"]] [:td "Concrete ops → SVG XML string"] [:td (arch-src-link "src/eido/engine/svg.clj" "svg.clj")]]
       [:tr [:td [:code "eido.engine.gif"]] [:td "Animated GIF encoder"] [:td (arch-src-link "src/eido/engine/gif.clj" "gif.clj")]]
       [:tr [:td [:code "eido.gen.*"]] [:td "Generative modules (noise, flow, circle packing, boids, etc.)"] [:td (arch-src-link "src/eido/gen/" "gen/")]]
       [:tr [:td [:code "eido.color"]] [:td "Color parsing, conversion, and manipulation"] [:td (arch-src-link "src/eido/color.clj" "color.clj")]]
       [:tr [:td [:code "eido.scene"]] [:td "Layout helpers and node constructors"] [:td (arch-src-link "src/eido/scene.clj" "scene.clj")]]]]]}])
