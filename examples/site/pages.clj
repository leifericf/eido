(ns site.pages
  "Static content for eido website pages — landing page, features, docs, architecture, workflows.")

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
  "Getting started walkthrough for the landing page."
  []
  [:div
   [:p "New to Clojure? Follow the "
    [:a {:href "https://calva.io/getting-started/" :target "_blank"} "Calva Getting Started guide"]
    " — it walks you through installing VS Code, Clojure, and the Calva extension, which lets you evaluate code directly from your editor."]
   [:h4 "1. Add Eido to your project"]
   [:p "Create a " [:code "deps.edn"] " file with Eido as a dependency:"]
   [:pre [:code
          ";; deps.edn
{:deps
 {io.github.leifericf/eido
  {:git/tag \"v1.0.0-beta4\" :git/sha \"b942c7f\"}}}"]]
   [:h4 "2. Render your first image"]
   [:p "Start a REPL via Calva (\"Jack-in\"), then evaluate:"]
   [:pre [:code
          "(require '[eido.core :as eido])

(eido/render
  {:image/size [400 400]
   :image/background [:color/name \"linen\"]
   :image/nodes
   [{:node/type     :shape/circle
     :circle/center [200 200]
     :circle/radius 120
     :style/fill    [:color/name \"coral\"]}]}
  {:output \"my-first-image.png\"})
;; => \"my-first-image.png\""]]
   [:p "That's it — " [:code "my-first-image.png"]
    " is now on disk. Change the color, re-evaluate, see the new image. This interactive loop is how generative artists work with Eido."]])

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
(color/lerp color-a color-b 0.5)             ;; blend 50/50"]]
       [:h4 "Perceptually uniform color (OKLAB / OKLCH)"]
       [:p "RGB and HSL are device-oriented — equal numerical steps don't produce equal "
        "visual steps. OKLAB and OKLCH are perceptually uniform: interpolation stays vivid "
        "instead of passing through muddy grays."]
       [:pre [:code
              "[:color/oklab 0.63 0.22 0.13]  ;; L (lightness), a, b
[:color/oklch 0.63 0.26 29]   ;; L, C (chroma), h (hue degrees)

;; Convenience constructors:
(color/oklab 0.63 0.22 0.13)
(color/oklch 0.7 0.15 200)"]]
       [:p "The real power is in OKLAB interpolation — blending red and cyan in RGB "
        "produces gray, but in OKLAB the midpoint stays chromatic:"]
       [:pre {:data-img "docs-oklab-lerp.png"} [:code
              ";; RGB interpolation (passes through gray):
(color/lerp :red :cyan 0.5)

;; OKLAB interpolation (stays vivid):
(color/lerp :red :cyan 0.5 {:space :oklab})

;; Convenience shorthand:
(color/lerp-oklab :red :cyan 0.5)"]]
       [:p "Use " [:code "rgb->oklab"] " and " [:code "rgb->oklch"]
        " to inspect any color in perceptual coordinates:"]
       [:pre [:code
              "(color/rgb->oklab 255 0 0)   ;; => [0.628 0.225 0.126]
(color/rgb->oklch 255 0 0)   ;; => [0.628 0.258 29.2]"]]
       [:h4 "Color contrast and distance"]
       [:p "Check whether two colors have enough visual separation — for readability, plotter ink on paper, or accessibility:"]
       [:pre [:code
              ";; WCAG luminance contrast ratio (1 = identical, 21 = max)
(color/contrast :black :white)        ;; => 21.0
(color/contrast :red :darkred)        ;; => ~2.1

;; Perceptual distance in OKLAB space
(color/perceptual-distance :red :blue)  ;; => ~0.52

;; Minimum contrast in a palette (all pairs)
(palette/min-contrast [:red :coral :gold :navy])

;; Sort palette from dark to light (perceptual lightness)
(palette/sort-by-lightness my-palette)"]]
       [:h4 "Extracting palettes from images"]
       [:p "Photograph a landscape, scan a painting, or screenshot a reference — then extract its dominant colors as a palette:"]
       [:pre [:code
              "(require '[eido.color.palette :as palette])

;; Extract 5 dominant colors from a photograph:
(palette/from-image \"photo.jpg\" 5 {:seed 42})

;; Preview the result:
(show (palette/swatch (palette/from-image \"sunset.jpg\" 6)))"]]
       [:p "Uses k-means clustering in OKLAB perceptual color space — colors that "
        [:em "look"] " similar get grouped together. Result is sorted dark to light."]]}

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
        ", " [:code ":screen"] ", " [:code ":overlay"] ", and more."]
       [:h4 "Margin control"]
       [:p "Clip all artwork to an inset rectangle — a clean way to enforce margins:"]
       [:pre [:code
              "(require '[eido.scene :as scene])

;; 30px margin on all sides:
(scene/with-margin my-scene 30)"]]
       [:p "Uses existing " [:code ":group/clip"] " infrastructure — wraps all nodes in a clipped group. Works with any content."
       ]]}

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
              "(scene/radial 12 [200 200] 120  ;; 12 items around [200,200] radius 120
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
       [:p "The " [:code ":seed"] " controls which particular landscape you get. Same seed, same landscape. The " [:code ":octaves"] " parameter in " [:code "fbm"] " adds layers of detail — like zooming into a coastline where you see detail at every scale."]
       [:h4 "Simplex noise"]
       [:p "OpenSimplex2 has fewer directional artifacts than Perlin — smoother, more organic patterns. Same API, drop-in replacement:"]
       [:pre [:code
              "(noise/simplex2d x y)                  ;; 2D simplex noise
(noise/simplex3d x y z)                ;; 3D simplex noise
(noise/simplex2d x y {:seed 42})       ;; seeded

;; Works with all fractal variants:
(noise/fbm noise/simplex2d x y {:octaves 6})
(noise/turbulence noise/simplex2d x y {:octaves 4})"]]
       [:h4 "4D noise and seamless loops"]
       [:p "4D noise is essential for seamlessly looping animated noise. Use the 3rd and 4th dimensions as a circular time parameter:"]
       [:pre [:code
              ";; 4D Perlin and simplex noise:
(noise/perlin4d x y z w)
(noise/simplex4d x y z w {:seed 42})

;; Seamless loop trick — walk a circle in the z/w plane:
(let [r 1.0
      t (/ frame total-frames)]
  (noise/simplex4d x y
    (* r (Math/cos (* t 2 Math/PI)))
    (* r (Math/sin (* t 2 Math/PI)))))"]]
       [:p "The loop radius " [:code "r"] " controls how different each frame is from its neighbors — smaller = smoother transitions, larger = more variation."]
       [:h4 "Noise preview"]
       [:p "Tweak noise parameters and see the result instantly at the REPL:"]
       [:pre [:code
              ";; Preview any noise function as a grayscale image:
(show (noise/preview noise/perlin2d))

;; Preview FBM with custom parameters:
(show (noise/preview
  (fn [x y] (noise/fbm noise/perlin2d x y {:octaves 6 :seed 42}))
  {:width 512 :height 512 :scale 0.01}))"]]]}

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
       [:p "These tools feed naturally into palette sampling, series parameters, and per-item variation — giving you precise artistic control over what would otherwise be pure chance."]
       [:h4 "Shaped distributions"]
       [:p "Beyond uniform and Gaussian, three more distribution shapes for sculpting randomness:"]
       [:pre [:code
              ";; Pareto (heavy-tailed): most values small, occasional giants
;; Great for natural size variation (city sizes, star brightness)
(prob/pareto 50 2.0 1.0 seed)  ;; 50 values, alpha=2, min=1

;; Triangular: bounded bell curve with explicit min/max/peak
;; \"Mostly around 0.3 but never below 0 or above 1\"
(prob/sample {:type :triangular :min 0 :max 1 :mode 0.3} seed)

;; Eased: shape any distribution with an easing curve
;; Pass any (fn [t] -> t) to skew the output
(require '[eido.animate :as anim])
(prob/sample {:type :eased :easing anim/ease-in :lo 5 :hi 50} seed)"]]
       [:h4 "Geometric sampling"]
       [:p "Sample points on or inside circles and spheres — useful for scatter patterns, particle emission, and radial layouts:"]
       [:pre [:code
              ";; Single point on a circle's circumference
(prob/on-circle 100.0 seed)  ;; => [x y]

;; 50 points uniformly inside a disc (no center clustering)
(prob/scatter-in-circle 50 100.0 [200 200] seed)

;; Point on a sphere surface (3D — uniform, no polar clustering)
(prob/on-sphere 10.0 seed)  ;; => [x y z]

;; Point uniformly inside a sphere volume
(prob/in-sphere 10.0 [0 0 0] seed)  ;; => [x y z]

;; n points on a circle (e.g. for radial layouts)
(prob/scatter-on-circle 12 100.0 [200 200] seed)"]]
       [:h4 "Jittering point positions"]
       [:p "Displace any set of points by a Gaussian offset — turns a regular grid into something organic:"]
       [:pre [:code
              "(require '[eido.gen.scatter :as scatter])

;; Break the regularity of a grid
(scatter/jitter (scatter/grid [0 0 400 400] 10 10) {:amount 3.0 :seed seed})"]]]}

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
        [:code "shuffle-palette"] " randomizes color order with a seed — great for giving each edition a different arrangement from the same palette."]
       [:h4 "Palette adjustments"]
       [:p "Shift an entire palette's mood in one call:"]
       [:pre [:code
              ";; Make a palette warmer (shift hues toward orange)
(palette/warmer my-palette 10)

;; Multiple adjustments at once:
(palette/adjust my-palette {:darker 0.1 :muted 0.2})

;; All available: warmer, cooler, muted, vivid, darker, lighter"]]
       [:h4 "Non-linear gradients"]
       [:p "Apply easing functions to gradient interpolation for more natural transitions:"]
       [:pre [:code
              "(require '[eido.animate :as anim])

;; Linear gradient (default):
(palette/gradient-map stops 0.5)

;; Ease-in: slow start, fast end
(palette/gradient-map stops 0.5 {:easing anim/ease-in})"]]
       [:h4 "Palette preview"]
       [:p "See a palette instantly at the REPL without building a scene:"]
       [:pre [:code
              ";; Preview any palette as a color swatch:
(show (palette/swatch [:red :coral :gold :teal :navy]))

;; Custom dimensions:
(show (palette/swatch my-palette {:width 600 :height 80}))"]]]}

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
       [:h4 "Flow field collision detection"]
       [:p "By default, flow field streamlines can cross and overlap. Add "
        [:code ":collision-distance"] " to enforce minimum spacing — the result is "
        "gallery-ready even density:"]
       [:pre [:code
              "(flow/flow-field [0 0 500 400]
  {:density 15 :steps 50 :seed 42
   :collision-distance 8.0})  ;; streamlines stop when approaching others"]]
       [:h4 "Combining them — dashed flow field"]
       [:p "The real power is chaining: smooth a flow field, then dash it. Each streamline becomes a series of short, flowing strokes:"]
       [:pre {:data-img "docs-dashed-flow.png"} [:code
              "(let [paths (flow/flow-field [20 20 460 360]
                {:density 30 :steps 35 :seed 42})]
  (mapcat (fn [path]
            (-> (:path/commands path)
                (aesthetic/smooth-commands {:samples 30})
                (aesthetic/dash-commands {:dash [10.0 6.0]})))
          paths))"]]
       [:p "Try different " [:code ":dash"] " ratios, " [:code ":density"]
        " values, and " [:code ":seed"] "s to find the feel you want."]
       [:h4 "Media presets"]
       [:p "Named preset pipelines for common physical media aesthetics — inspect, modify, or combine them:"]
       [:pre [:code
              ";; Ink strokes: moderate smoothing + organic jitter
(aesthetic/stylize path-cmds (aesthetic/ink-preset seed))

;; Pencil lines: light smoothing + fine jitter + sketch dashes
(aesthetic/stylize path-cmds (aesthetic/pencil-preset seed))

;; Watercolor edges: heavy smoothing + pronounced bleeding
(aesthetic/stylize path-cmds (aesthetic/watercolor-preset seed))

;; Presets are just data — inspect and modify:
(aesthetic/ink-preset 42)
;; => [{:op :chaikin, :iterations 2}
;;     {:op :jitter, :amount 1.2, :density 1.5, :seed 42}]"]]
       [:h4 "Chaikin smoothing"]
       [:p "Chaikin corner-cutting produces rounder, more uniform curves than Catmull-Rom — a different aesthetic feel:"]
       [:pre [:code
              ";; Chaikin smoothing (iterative corner-cutting)
(aesthetic/chaikin-commands path-cmds {:iterations 3})

;; Via stylize pipeline:
(aesthetic/stylize cmds [{:op :chaikin :iterations 3}
                          {:op :dash :dash [10 5]}])"]]
       [:h4 "Path simplification"]
       [:p "Reduce point count while preserving shape — essential for plotter optimization and cleaning up noisy paths:"]
       [:pre [:code
              "(require '[eido.path :as path])

;; Douglas-Peucker on raw points (epsilon controls aggressiveness):
(path/simplify [[0 0] [10 1] [20 0] [30 1] [40 0]] 2.0)

;; On path commands:
(path/simplify-commands cmds 2.0)"]]
       [:h4 "Point-in-polygon"]
       [:p "Test whether a point falls inside a polygon — useful for constraining scatter, clipping, and spatial queries:"]
       [:pre [:code
              ";; Is the point inside this irregular shape?
(path/contains-point? [[0 0] [100 0] [80 100] [20 80]] [50 50])
;; => true"]]
       [:h4 "Polygon inset"]
       [:p "Shrink a polygon inward — useful for margin control and nested compositions:"]
       [:pre [:code
              ";; Shrink a square by 10 pixels on all sides:
(path/inset [[0 0] [100 0] [100 100] [0 100]] 10.0)
;; => [[10 10] [90 10] [90 90] [10 90]]"]]
       [:p "Works correctly for convex polygons. Concave polygons may produce self-intersecting results."]
       [:h4 "Curve splitting"]
       [:p "Divide a path into equal-length segments — essential for plotter optimization and dashed effects:"]
       [:pre [:code
              ";; Split a path into segments of ~25px arc-length:
(path/split-at-length path-cmds 25.0)
;; => [[[:move-to [0 0]] [:line-to [25 0]]]
;;     [[:move-to [25 0]] [:line-to [50 0]]] ...]"]]
       [:h4 "Path interpolation"]
       [:p "Blend between two matching paths at parameter t:"]
       [:pre [:code
              ";; Morph between two shapes (must have same command count):
(path/interpolate path-a path-b 0.5)  ;; midpoint between A and B"]]
       [:h4 "Clipping to bounds"]
       [:p "Clip a path to a bounding rectangle — useful for constraining generated paths:"]
       [:pre [:code
              ";; Clip to a 100x100 rectangle:
(path/trim-to-bounds path-cmds [0 0 100 100])
;; => vector of clipped path-command vectors"]]]}

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
        [:code ":boolean"] " (coin flip with probability)."]
       [:h4 "Trait distribution analysis"]
       [:p "Before releasing a series, verify that your trait distribution is what you intended:"]
       [:pre [:code
              "(series/trait-summary spec 42 1000
  {:density [[30 \"sparse\"] [70 \"medium\"] [100 \"dense\"]]
   :speed   [[5 \"slow\"] [10 \"fast\"]]})
;; => {:density {\"sparse\" 312, \"medium\" 398, \"dense\" 290}
;;     :speed   {\"slow\" 487, \"fast\" 513}}"]]
       [:h4 "Focused exploration"]
       [:p "After a broad sweep, compare favorites side by side:"]
       [:pre [:code
              ";; Render specific editions you liked:
(show (series/seed-grid
  {:spec spec :master-seed 42
   :seeds [12 47 103 256 891]
   :scene-fn make-scene :cols 5}))"]]
       [:p "Start wide with " [:code ":start/:end"] ", find interesting editions, "
        "then use " [:code ":seeds"] " to narrow down."]
       [:h4 "Parameter sweeps"]
       [:p "Isolate a single parameter to see its effect across a range of values:"]
       [:pre [:code
              ";; Sweep density across rows, color-count across columns
(show (series/param-grid
  {:base-params {:density 0.5 :color-count 3 :seed 42}
   :row-param   {:key :density :values [0.2 0.5 0.8]}
   :col-param   {:key :color-count :values [2 3 5]}
   :scene-fn    (fn [params] (make-scene params))
   :thumb-size  [160 160]}))"]]
       [:p [:code "param-grid"] " is the complement to " [:code "seed-grid"]
        ": where seed-grid varies randomness, param-grid varies design decisions."]]}

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
(let [grid   (ca/rd-grid 80 80 :center 42)
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
  (s3d/sphere proj [0 0 0]
    {:radius 1.5
     :style {:style/fill [:color/name \"cornflowerblue\"]}
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

   {:category "Recipes"
    :id       "recipes"
    :intro    "Common creative workflows assembled from Eido's modules. Each recipe is a complete pattern you can adapt — not a single function, but a method."
    :sections
    [{:id    "recipe-edition"
      :title "Long-Form Edition"
      :content
      [:div
       [:p "The canonical workflow for seed-driven generative art (Art Blocks / fxhash style). One algorithm, many unique outputs."]
       [:pre [:code
              "(require '[eido.gen.series :as series])
(require '[eido.gen.prob :as prob])
(require '[eido.core :as eido])

;; 1. Define what varies across editions
(def spec
  {:hue      {:type :uniform :lo 0.0 :hi 360.0}
   :density  {:type :gaussian :mean 20.0 :sd 5.0}
   :palette  {:type :choice :options [:sunset :ocean :forest]}
   :bold?    {:type :boolean :probability 0.3}})

;; 2. Build a scene from sampled parameters
(defn make-scene [params edition]
  {:image/size [800 800]
   :image/background [:color/hsl (:hue params) 0.15 0.95]
   :image/nodes
   [{:node/type     :shape/circle
     :circle/center [400 400]
     :circle/radius (* 300 (/ (:density params) 40.0))
     :style/fill    [:color/hsl (:hue params) 0.7 0.5]}]})

;; 3. Render a batch with metadata
(series/render-editions
  {:spec spec :master-seed 42
   :start 0 :end 50
   :scene-fn make-scene
   :output-dir \"editions/\"
   :traits {:density [[15 \"sparse\"] [25 \"medium\"] [100 \"dense\"]]}})"]]
       [:p "Each edition gets a deterministic, uncorrelated seed. The same master-seed + edition-number always produces the same output. The metadata.edn file records parameters and derived traits for every edition."]]}

     {:id    "recipe-watercolor"
      :title "Watercolor / Ink Wash"
      :content
      [:div
       [:p "Simulate translucent media by layering many low-opacity, slightly deformed copies of a shape. The overlapping layers create organic depth."]
       [:pre [:code
              "(require '[eido.texture :as texture])

;; Quick watercolor effect on a shape:
(texture/watercolor my-polygon
  {:layers 30 :opacity 0.04 :amount 3.0 :seed 42})

;; Full control with custom deformation:
(texture/layered my-polygon
  {:layers 40 :opacity 0.03 :seed 42
   :deform-fn (fn [node _layer-index seed]
                (update node :path/commands
                  distort/distort-commands {:type :jitter :amount 4 :seed seed}))})"]]
       [:p "Each layer is independently deformed, creating the characteristic uneven edge of physical watercolor. 30-50 layers works well for interactive use; up to 100 for final renders."]]}

     {:id    "recipe-paper-grain"
      :title "Paper Grain / Texture"
      :content
      [:div
       [:p "Simulate the texture of physical media (watercolor paper, canvas) using the built-in grain effect and compositing:"]
       [:pre [:code
              "(require '[eido.ir.effect :as effect])

;; Paper grain overlay — applies to any artwork:
{:image/size [600 400]
 :image/background [:color/rgb 245 240 230]
 :image/nodes
 [{:node/type :group
   :group/composite :overlay
   :group/children
   [;; Paper texture layer
    {:node/type :shape/rect
     :rect/xy [0 0] :rect/size [600 400]
     :style/fill [:color/rgb 245 240 230]
     :node/effects [(effect/grain {:amount 40 :seed 42})]}
    ;; Your artwork goes here
    ,,,your nodes,,,]}]}"]]
       [:p "The grain effect adds film-grain noise to a shape. Compositing via "
        [:code ":overlay"] " or " [:code ":multiply"]
        " blends it with the artwork underneath, simulating paper texture."]]}

     {:id    "recipe-subdivide-pack"
      :title "Subdivide → Pack → Stylize"
      :content
      [:div
       [:p "A recipe for geometric abstraction: subdivide the canvas, pack circles into cells, assign colors by depth."]
       [:pre [:code
              "(require '[eido.gen.subdivide :as subdivide])
(require '[eido.gen.circle :as circle])
(require '[eido.color.palette :as palette])

;; 1. Subdivide canvas into cells
(let [cells (subdivide/subdivide
              {:x 0 :y 0 :w 800 :h 800}
              {:max-depth 4 :min-size 80 :seed 42})
      pal   (:sunset palette/palettes)]
  ;; 2. Pack circles into each cell
  (->> cells
       (mapcat (fn [{:keys [x y w h depth]}]
                 (let [circles (circle/circle-pack
                                 {:x x :y y :w w :h h}
                                 {:min-r 3 :max-r (/ (min w h) 4)
                                  :max-attempts 500 :seed (hash [x y])})]
                   ;; 3. Style by subdivision depth
                   (map (fn [c]
                          {:node/type     :shape/circle
                           :circle/center [(:x c) (:y c)]
                           :circle/radius (:r c)
                           :style/fill    (nth pal (mod depth (count pal)))
                           :style/stroke  {:color :black :width 0.5}})
                        circles))))
       vec))"]]
       [:p "This pattern composes three modules (subdivide, circle, palette) into a single visual method. Swap the subdivision for noise-driven regions, or replace circles with hatched rectangles — the structure stays the same."]]}

     {:id    "recipe-flow-path"
      :title "Flow Field → Path → Texture"
      :content
      [:div
       [:p "Organic line work from noise fields: trace streamlines, smooth them, add texture."]
       [:pre [:code
              "(require '[eido.gen.noise :as noise])
(require '[eido.gen.flow :as flow])
(require '[eido.path.aesthetic :as aes])

;; 1. Build a flow field from noise
(let [field (flow/flow-field
              {:bounds [0 0 800 800]
               :resolution 20
               :noise-fn (fn [x y] (noise/fbm x y {:octaves 4 :scale 0.003}))})
      ;; 2. Trace streamlines
      paths (:paths field)]
  ;; 3. Smooth and jitter each path
  (->> paths
       (map (fn [path-cmds]
              {:node/type     :shape/path
               :path/commands (-> path-cmds
                                  (aes/smooth-commands {:tension 0.4})
                                  (aes/jittered-commands {:amount 1.5 :seed 42}))
               :style/stroke  {:color :black :width 0.8}}))
       vec))"]]
       [:p "For plotter output, render with " [:code "{:stroke-only true :group-by-stroke true}"]
        " to get clean, single-pen SVG layers."]]}

     {:id    "recipe-ca-contour"
      :title "CA / Reaction-Diffusion → Contour → Palette"
      :content
      [:div
       [:p "Field-driven biological abstraction: simulate, extract contours, map to color."]
       [:pre [:code
              "(require '[eido.gen.ca :as ca])
(require '[eido.gen.contour :as contour])
(require '[eido.color.palette :as palette])

;; 1. Run Gray-Scott reaction-diffusion
(let [grid    (ca/rd-grid 200 200)
      result  (ca/rd-run grid 3000 {:preset :coral})
      ;; 2. Extract concentration field as a scalar function
      field-fn (fn [x y]
                 (let [col (int (/ (* x 200) 800))
                       row (int (/ (* y 200) 800))]
                   (aget ^doubles (nth (:v result) (min row 199))
                         (min col 199))))
      ;; 3. Contour at threshold levels
      pal     (:ocean palette/palettes)
      levels  [0.1 0.2 0.3 0.4 0.5]]
  (->> levels
       (map-indexed
         (fn [i level]
           (let [contours (contour/contour-lines
                            {:bounds [0 0 800 800]
                             :fn field-fn
                             :level level
                             :resolution 4})]
             {:node/type     :group
              :group/children
              (mapv (fn [path-cmds]
                      {:node/type     :shape/path
                       :path/commands path-cmds
                       :style/fill    (nth pal (mod i (count pal)))
                       :style/stroke  {:color :black :width 0.3}})
                    contours)})))
       vec))"]]
       [:p "The reaction-diffusion presets (:coral, :mitosis, :ripple, :spots) each produce distinctive field patterns. Contour extraction turns continuous fields into drawable regions."]]}]}

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
(eido/render scene {:output \"out.tiff\" :dpi 300})  ;; archival TIFF

;; Animations
(eido/render frames {:output \"anim.gif\" :fps 30})
(eido/render frames {:output \"anim.svg\" :fps 30})  ;; animated SVG
(eido/render frames {:output \"frames/\" :fps 30})   ;; PNG sequence"]]
       [:h4 "Options"]
       [:pre [:code
              ":scale 2                     ;; 2x resolution (retina)
:dpi 300                     ;; DPI metadata (PNG/TIFF)
:transparent-background true ;; no background fill
:loop false                  ;; GIF plays once (default: loops)
:tiff/compression :lzw       ;; TIFF: :lzw (default), :deflate, :none
:quality 0.9                 ;; JPEG quality (0-1)"]]
       [:p "Render without an output path to get a BufferedImage back for further processing, or use "
        [:code ":format :svg"] " to get an SVG string."]
       [:p "If the scene includes " [:code ":image/dpi"] ", PNG and TIFF output "
        "automatically embed DPI metadata — no need to pass " [:code ":dpi"] " separately."]]}

     {:id    "print-ready"
      :title "Print-Ready Output"
      :content
      [:div
       [:p "Artists producing physical output — prints, plotter work, fine art editions — need to think in centimeters or inches, not pixels. Eido provides resolution-independent coordinates and paper size presets."]
       [:h4 "Paper size presets"]
       [:pre [:code
              "(require '[eido.scene :as scene])

;; Standard paper sizes — returns a base scene map
(scene/paper :a4)
;=> {:image/size [21.0 29.7] :image/units :cm :image/dpi 300}

(scene/paper :letter :landscape true)
;=> {:image/size [11.0 8.5] :image/units :in :image/dpi 300}

(scene/paper :a3 :dpi 600)
;=> {:image/size [29.7 42.0] :image/units :cm :image/dpi 600}

;; Available sizes: :a3 :a4 :a5 :letter :legal :tabloid :square-8"]]
       [:h4 "Unit conversion"]
       [:p [:code "scene/with-units"] " converts a scene described in real-world units to pixel coordinates. "
        "It walks the entire scene tree, scaling all spatial values (coordinates, radii, stroke widths, "
        "dash patterns, font sizes) while leaving non-spatial values (opacity, angles, colors) untouched:"]
       [:pre [:code
              ";; Describe your scene in centimeters
(-> (scene/paper :a4)
    (assoc :image/background :white
           :image/nodes
           [{:node/type :shape/circle
             :circle/center [10.5 14.85]  ;; center of A4 in cm
             :circle/radius 5.0           ;; 5 cm radius
             :style/stroke {:color :black :width 0.1}}])  ;; 1mm stroke
    scene/with-units  ;; converts to pixels
    (eido/render {:output \"print.tiff\"}))
;; Output: 2480×3508 px TIFF at 300 DPI with embedded DPI metadata"]]
       [:p "Supported units: " [:code ":cm"] " (centimeters), "
        [:code ":mm"] " (millimeters), " [:code ":in"] " (inches)."]
       [:p [:code "with-units"] " is a pure function — it takes a data map and returns a data map. "
        "You can inspect the converted scene at the REPL before rendering."]]}

     {:id    "polyline-export"
      :title "Polyline Data Export"
      :content
      [:div
       [:p "For CNC mills, laser cutters, and custom plotter software, raw coordinate data is more useful than rendered images. "
        "Use " [:code ":format :polylines"] " to extract geometry as EDN:"]
       [:pre [:code
              ";; Extract polylines from any scene
(eido/render scene {:format :polylines})
;=> {:polylines [[[x1 y1] [x2 y2] ...] ...]
;    :bounds [800 600]}

;; Write to file
(eido/render scene {:format :polylines :output \"paths.edn\"})

;; Control curve resolution
(eido/render scene {:format :polylines :flatness 0.5 :segments 64})"]]
       [:p "All geometry is converted to polylines: curves are flattened via de Casteljau subdivision, "
        "circles and ellipses are approximated as polygons. Groups are recursively traversed."]
       [:p "Options: " [:code ":flatness"] " controls curve subdivision tolerance (default 0.5, lower = more points). "
        [:code ":segments"] " controls circle/ellipse polygon resolution (default 64)."]]}

     {:id    "plotter-svg"
      :title "Plotter-Safe SVG"
      :content
      [:div
       [:p "For pen plotters, use the plotter options to produce clean, stroke-only SVG:"]
       [:pre [:code
              ";; Stroke-only: removes all fills, suppresses background
(eido/render scene {:output \"plotter.svg\" :stroke-only true})

;; Group by stroke color: one <g> per pen/color
(eido/render scene {:output \"plotter.svg\"
                    :stroke-only true
                    :group-by-stroke true})

;; Full plotter pipeline: deduplicate, optimize pen travel
(eido/render scene {:output \"plotter.svg\"
                    :stroke-only      true
                    :group-by-stroke  true
                    :deduplicate      true
                    :optimize-travel  true})"]]
       [:p "With " [:code ":group-by-stroke"] ", each stroke color gets its own "
        [:code "<g>"] " element with an id like " [:code "pen-rgb-0-0-0"]
        ". Load the SVG in your plotter software and assign each group to a pen."]
       [:p [:code ":deduplicate"] " removes identical paths (common with overlapping geometry). "
        [:code ":optimize-travel"] " reorders drawing operations to minimize pen-up travel distance "
        "using greedy nearest-neighbor — can significantly reduce total plot time."]]}

     {:id    "batch-editions"
      :title "Batch Edition Rendering"
      :content
      [:div
       [:p "Render many editions at once with " [:code "series/render-editions"] ":"]
       [:pre [:code
              "(require '[eido.gen.series :as series])

(series/render-editions
  {:spec        {:hue {:type :uniform :lo 0 :hi 360}}
   :master-seed 42
   :start       0
   :end         100
   :scene-fn    (fn [params edition] (make-scene params))
   :output-dir  \"editions/\"
   :format      :png          ;; or :svg
   :render-opts {:scale 2}    ;; passed to eido/render
   :traits      {:hue [[120 \"cool\"] [240 \"warm\"] [360 \"hot\"]]}})"]]
       [:p "This writes one file per edition plus a " [:code "metadata.edn"]
        " containing the parameter values and derived traits for every edition."]]}

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
       [:p [:code "*validate*"] " defaults to " [:code "true"] ". Validation adds roughly 7% overhead per render, so skipping it in tight iteration loops makes a noticeable difference."]]}

     {:id    "stability"
      :title "Stability"
      :content
      [:div
       [:p "Functions in Eido have one of two stability levels:"]
       [:ul
        [:li [:strong "Stable"] " (default) — the function signature and behavior are settled. Breaking changes require a major version bump and migration guidance."]
        [:li [:strong "Provisional"] " — the function works and is tested, but the API surface may change based on real-world usage. Provisional functions are marked with a badge in the "
         [:a {:href "../api/"} "API Reference"] "."]]
       [:p "Most of Eido's API is stable. Provisional status is reserved for newer subsystems whose configuration surface is still being refined:"]
       [:ul
        [:li [:code "eido.gen.particle"] " — particle system configuration (emitters, forces, lifetime curves) may simplify as usage patterns emerge."]
        [:li [:code "eido.texture"] " — texture and material helpers are new and may expand or restructure."]]
       [:p "Provisional does not mean broken — it means function names, argument shapes, or option keys might change in a future release. Pin a specific Eido version in your " [:code "deps.edn"] " to avoid surprises."]]}]}])

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
     [:p "For example, if you pass a negative radius, a made-up color format, and a number where a vector was expected:"]
     [:pre.arch-error [:code
            ";; This scene has three deliberate mistakes:
;; {:image/nodes [{:circle/radius -5}
;;                {:style/fill [:color/invalid ...]}
;;                {:rect/size 100}]}
;;
;; Eido catches all of them before rendering:

Invalid scene — 3 validation errors:

  1. at [:image/nodes 0 :circle/radius]: positive number, got: -5
  2. at [:image/nodes 1 :style/fill 0]: known color format, got: :color/invalid
  3. at [:image/nodes 1 :rect/size]: vector of [w h], got: 100"]]
     [:p "Each error tells you the path into the data structure, what was expected, and what was found. Mistakes are caught at the boundary — before they become mysterious rendering glitches deep in the pipeline."]
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

;; --- Workflow pages ---

(defn- workflow-sketching-sections []
  [{:id "repl-loop"
    :title "The REPL Loop"
    :content
    [:div
     [:p "Eido's primary workflow is REPL-driven: edit a scene, evaluate it, see the result, adjust. No compile step, no build tool, no save-and-refresh cycle."]
     [:pre {:data-img "docs-wf-sketch-circle.png"} [:code
            "(require '[eido.core :as eido])

;; Define a scene
(def scene
  {:image/size [400 400]
   :image/background [:color/name \"linen\"]
   :image/nodes
   [{:node/type     :shape/circle
     :circle/center [200 200]
     :circle/radius 120
     :style/fill    [:color/name \"crimson\"]}]})

;; Render and display in the REPL
(eido/show scene)"]]
     [:p "Change the radius, re-evaluate, see the result instantly. This tight loop is how most Eido work happens."]]}

   {:id "watch-workflow"
    :title "Watch Workflows"
    :content
    [:div
     [:p "For continuous feedback, watch a scene atom or a file. Every change triggers a re-render in a preview window:"]
     [:pre [:code
            ";; Watch an atom — updates on every swap!/reset!
(def my-scene (atom scene))
(eido/watch-scene my-scene)

;; Now just change the atom:
(swap! my-scene assoc-in [:image/nodes 0 :circle/radius] 80)
;; Preview updates automatically

;; Or watch an EDN file — updates on every save
(eido/watch-file \"sketch.edn\")"]]
     [:p "The first render validates the scene; subsequent re-renders skip validation for speed."]]}

   {:id "seed-exploration"
    :title "Exploring Seeds"
    :content
    [:div
     [:p [:code "seed-grid"] " renders a grid of variations from different seeds — a visual overview of what your algorithm produces:"]
     [:pre [:code
            "(require '[eido.gen.series :as series])

(series/seed-grid
  {:spec      {:hue {:type :uniform :lo 0.0 :hi 360.0}
               :density {:type :gaussian :mean 20.0 :sd 5.0}}
   :master-seed 42
   :scene-fn  (fn [params _edition]
                (make-scene params))
   :cols 5 :rows 3
   :thumb-size [160 160]})"]]
     [:p "Each cell is a different edition with different sampled parameters. Spot interesting ones, note the edition number, and explore further."]]}

   {:id "param-sweeps"
    :title "Parameter Sweeps"
    :content
    [:div
     [:p [:code "param-grid"] " isolates a single parameter, rendering a sweep across a range of values:"]
     [:pre [:code
            "(series/param-grid
  {:scene-fn    (fn [v] (make-scene {:density v}))
   :param-name  \"density\"
   :values      (range 5 50 5)
   :thumb-size  [120 120]})"]]
     [:p "This makes design decisions concrete — you can see exactly where a parameter transitions from \"too sparse\" to \"just right\" to \"too dense.\""]]}

   {:id "capturing-seeds"
    :title "Capturing Interesting Seeds"
    :content
    [:div
     [:p "When you find a combination you like, use " [:code "save-seed!"] " to bookmark it:"]
     [:pre [:code
            "(require '[eido.gen.series :as series])

;; Bookmark a discovery
(series/save-seed! \"keepers.edn\"
  {:seed 4217 :params params :note \"nice organic density\"})

;; Later, load all bookmarks
(series/load-seeds \"keepers.edn\")
;=> [{:seed 4217 :params {...} :note \"nice organic density\"
;     :timestamp \"2026-04-10T...\"}]"]]
     [:p "Each entry is timestamped automatically. The file accumulates entries — one per " [:code "save-seed!"] " call."]
     [:p "For maximum reliability, save the scene map directly — it's a complete, self-contained description that can be re-rendered anytime:"]
     [:pre [:code "(spit \"keeper-4217.edn\" (pr-str scene))"]]]}])

(defn- workflow-editions-sections []
  [{:id "overview"
    :title "Overview"
    :content
    [:div
     [:p "A long-form edition series is one algorithm that produces many unique outputs — each driven by a deterministic seed. The same seed always produces the same image. This is the workflow behind Art Blocks, fxhash, and numbered print editions."]
     [:p "Eido provides this pipeline:"]
     [:ol
      [:li "Define a " [:strong "parameter spec"] " — what varies across editions"]
      [:li "Write a " [:strong "scene function"] " — turns parameters into a scene"]
      [:li "Render a " [:strong "batch"] " with metadata and optional manifests"]
      [:li "Analyze " [:strong "trait distribution"] " across the series"]]]}

   {:id "param-spec"
    :title "Parameter Specs"
    :content
    [:div
     [:p "A parameter spec defines what varies across editions using data-driven distributions:"]
     [:pre [:code
            "(def spec
  {:hue      {:type :uniform :lo 0.0 :hi 360.0}
   :density  {:type :gaussian :mean 20.0 :sd 5.0}
   :palette  {:type :choice :options [:sunset :ocean :forest]}
   :bold?    {:type :boolean :probability 0.3}
   :weight   {:type :weighted-choice
              :options [:light :medium :heavy]
              :weights [0.5 0.35 0.15]}})"]]
     [:p "Each edition samples from these distributions using a deterministic seed derived from the master seed + edition number. Distribution types: " [:code ":uniform"] ", " [:code ":gaussian"] ", " [:code ":choice"] ", " [:code ":weighted-choice"] ", " [:code ":boolean"] ", " [:code ":pareto"] ", " [:code ":triangular"] ", " [:code ":eased"] "."]]}

   {:id "scene-fn"
    :title "Scene Function"
    :content
    [:div
     [:p "The scene function takes sampled parameters and an edition number, and returns a scene map:"]
     [:pre [:code
            "(defn make-scene [params edition]
  {:image/size [800 800]
   :image/background [:color/hsl (:hue params) 0.1 0.95]
   :image/nodes
   [(build-artwork params)]})"]]
     [:p "This is where your creative algorithm lives. The parameters control variation; the algorithm defines the visual language."]]}

   {:id "batch-render"
    :title "Batch Rendering"
    :content
    [:div
     [:pre [:code
            "(require '[eido.gen.series :as series])

(series/render-editions
  {:spec        spec
   :master-seed 42
   :start       0
   :end         50
   :scene-fn    make-scene
   :output-dir  \"editions/\"
   :format      :png
   :render-opts {:scale 2}
   :traits      {:density [[15 \"sparse\"] [25 \"medium\"] [100 \"dense\"]]}
   :emit-manifest? true})"]]
     [:p "This renders 50 editions, writing:"]
     [:ul
      [:li [:code "editions/edition-0.png"] " through " [:code "edition-49.png"]]
      [:li [:code "editions/metadata.edn"] " — parameters and traits for every edition"]
      [:li [:code "editions/edition-0.edn"] " through " [:code "edition-49.edn"] " — per-edition manifests (with " [:code ":emit-manifest? true"] ")"]]
     [:p "Each manifest contains the full scene map, seed, parameters, and Eido version — everything needed to reproduce that exact output."]]}

   {:id "complete-package"
    :title "Complete Package"
    :content
    [:div
     [:p "For a full archival package — images, manifests, and a contact sheet in one call — use " [:code "export-edition-package"] ":"]
     [:pre [:code
            "(series/export-edition-package
  {:spec        spec
   :master-seed 42
   :start       0
   :end         50
   :scene-fn    make-scene
   :output-dir  \"editions/\"
   :contact-cols 10
   :thumb-size  [160 160]})"]]
     [:p "This produces everything " [:code "render-editions"] " does (with manifests enabled), plus a " [:code "contact-sheet.png"] " — a grid of thumbnails useful for proofing, exhibition planning, and social media."]]}

   {:id "traits"
    :title "Trait Analysis"
    :content
    [:div
     [:p "Traits categorize continuous parameters into named labels for metadata and marketplace display:"]
     [:pre [:code
            "(series/trait-summary
  {:spec spec :master-seed 42
   :start 0 :end 100
   :traits {:density [[15 \"sparse\"] [25 \"medium\"] [100 \"dense\"]]
            :palette identity}})"]]
     [:p "Returns frequency counts — how many editions fall into each trait bucket. Useful for verifying that your parameter spec produces a good distribution."]]}

   {:id "reproducibility"
    :title "Reproducibility"
    :content
    [:div
     [:p "Every render can produce a manifest — a machine-readable EDN sidecar file:"]
     [:pre [:code
            ";; Re-render from a manifest
(eido/render-from-manifest \"editions/edition-42.edn\")

;; Or re-render to a different format
(eido/render-from-manifest \"editions/edition-42.edn\"
  {:output \"edition-42-print.tiff\" :dpi 300})"]]
     [:p "The manifest stores the complete scene map — the same data you'd pass to " [:code "render"] ". This is the primary reproduction mechanism. The Eido version is recorded for diagnostics, but the scene map alone is usually sufficient."]]}])

(defn- workflow-plotter-sections []
  [{:id "stroke-only"
    :title "Stroke-Only SVG"
    :content
    [:div
     [:p "Plotters draw lines, not fills. Use " [:code ":stroke-only true"] " to strip fills and backgrounds:"]
     [:pre [:code
            "(eido/render scene {:output \"plotter.svg\"
                        :stroke-only true})"]]
     [:p "This removes all " [:code ":style/fill"] " values and the " [:code ":image/background"] ", leaving only stroked paths."]]}

   {:id "pen-grouping"
    :title "Grouping by Pen"
    :content
    [:div
     [:p "Multi-pen plotters need paths grouped by stroke color. Use " [:code ":group-by-stroke"] ":"]
     [:pre [:code
            "(eido/render scene {:output \"plotter.svg\"
                        :stroke-only     true
                        :group-by-stroke true})"]]
     [:p "Each stroke color gets its own " [:code "<g>"] " element with an id like " [:code "pen-rgb-0-0-0"] ". Load the SVG in your plotter software and assign each group to a physical pen."]]}

   {:id "travel-optimization"
    :title "Travel Optimization"
    :content
    [:div
     [:p "Minimize pen-up travel distance with " [:code ":optimize-travel"] ":"]
     [:pre {:data-img "docs-wf-plotter-strokes.png"} [:code
            "(eido/render scene {:output \"plotter.svg\"
                        :stroke-only     true
                        :group-by-stroke true
                        :deduplicate     true
                        :optimize-travel true})"]]
     [:p [:code ":deduplicate"] " removes identical overlapping paths. " [:code ":optimize-travel"] " reorders drawing operations using greedy nearest-neighbor, which can significantly reduce total plot time."]]}

   {:id "path-aesthetics"
    :title "Path Aesthetics"
    :content
    [:div
     [:p "Plotter output benefits from path-level treatment — smoothing, jitter, and dashing:"]
     [:pre [:code
            "(require '[eido.path.aesthetic :as aes])

;; Smooth jagged paths
(aes/smooth-commands cmds {:tension 0.4})

;; Add organic wobble
(aes/jittered-commands cmds {:amount 1.5 :seed 42})

;; Break into dashes
(aes/dash-commands cmds {:dash [10 5]})

;; Chain transforms with stylize
(aes/stylize cmds
  [[:smooth {:tension 0.3}]
   [:jitter {:amount 1.0 :seed 42}]])"]]
     [:p "These transforms work on path commands, not scene nodes — apply them before building the final scene."]]}

   {:id "per-layer"
    :title "Per-Layer Export"
    :content
    [:div
     [:p "For multi-pen plotters, export one SVG file per stroke color with " [:code "export-layers"] ":"]
     [:pre [:code
            "(require '[eido.io.plotter :as plotter])

(plotter/export-layers scene \"output/plotter/\"
  {:optimize-travel true})
;; => [{:pen \"pen-rgb-255-0-0-\" :file \"output/plotter/pen-rgb-255-0-0-.svg\"}
;;     {:pen \"pen-rgb-0-0-255-\" :file \"output/plotter/pen-rgb-0-0-255-.svg\"}]
;; Also writes output/plotter/preview.svg with all layers"]]
     [:p "Each layer SVG is stroke-only with deduplicated, travel-optimized paths. Load each file in your plotter software and assign to a physical pen. Disable the preview with " [:code "{:preview false}"] "."]]}

   {:id "polylines"
    :title "Beyond Plotters: Polyline Export"
    :content
    [:div
     [:p "For CNC mills, laser cutters, and custom plotter software, export raw coordinate data:"]
     [:pre [:code
            "(eido/render scene {:format :polylines})
;=> {:polylines [[[x1 y1] [x2 y2] ...] ...]
;    :bounds [800 600]}

;; Or write to file
(eido/render scene {:format :polylines
                    :output \"paths.edn\"
                    :flatness 0.5
                    :segments 64})"]]
     [:p "All geometry is converted to polylines: curves are flattened via de Casteljau subdivision, circles and ellipses are approximated as polygons."]]}])

(defn- workflow-print-sections []
  [{:id "paper-presets"
    :title "Paper Presets"
    :content
    [:div
     [:p "Start with a standard paper size — the preset gives you dimensions, units, and DPI:"]
     [:pre [:code
            "(require '[eido.scene :as scene])

(scene/paper :a4)
;=> {:image/size [21.0 29.7] :image/units :cm :image/dpi 300}

(scene/paper :letter :landscape true)
;=> {:image/size [11.0 8.5] :image/units :in :image/dpi 300}

(scene/paper :a3 :dpi 600)
;=> {:image/size [29.7 42.0] :image/units :cm :image/dpi 600}"]]
     [:p "Available sizes: " [:code ":a3"] ", " [:code ":a4"] ", " [:code ":a5"] ", " [:code ":letter"] ", " [:code ":legal"] ", " [:code ":tabloid"] ", " [:code ":square-8"] "."]]}

   {:id "units"
    :title "Real-World Units"
    :content
    [:div
     [:p "Describe geometry in centimeters, millimeters, or inches. " [:code "with-units"] " converts to pixels before rendering:"]
     [:pre {:data-img "docs-wf-print-paper.png"} [:code
            "(-> (scene/paper :a4)
    (assoc :image/background :white
           :image/nodes
           [{:node/type     :shape/circle
             :circle/center [10.5 14.85]   ;; center of A4 in cm
             :circle/radius 5.0            ;; 5 cm radius
             :style/stroke  {:color :black :width 0.1}}])  ;; 1mm stroke
    scene/with-units
    (eido/render {:output \"print.tiff\"}))"]]
     [:p [:code "with-units"] " walks the entire scene tree, scaling all spatial values (coordinates, radii, stroke widths, dash patterns, font sizes) while leaving non-spatial values (opacity, angles, colors) untouched."]]}

   {:id "margins"
    :title "Margins"
    :content
    [:div
     [:p "Add margins to any scene with " [:code "with-margin"] ":"]
     [:pre [:code
            "(-> (scene/paper :a4)
    (assoc :image/nodes [,,,your artwork,,,])
    (scene/with-margin 2.0)   ;; 2cm margin on all sides
    scene/with-units
    (eido/render {:output \"print.tiff\"}))"]]
     [:p "The margin clips artwork to the inset rectangle, giving you a clean border. Apply it before " [:code "with-units"] "."]]}

   {:id "export-formats"
    :title "Print-Ready Export"
    :content
    [:div
     [:p "For archival output, use TIFF with DPI metadata:"]
     [:pre [:code
            ";; TIFF with embedded DPI — the standard for print shops
(eido/render scene {:output \"print.tiff\" :dpi 300})

;; High-DPI PNG
(eido/render scene {:output \"print.png\" :dpi 300})

;; Or set DPI on the scene and it propagates automatically
(eido/render (assoc scene :image/dpi 300)
  {:output \"print.tiff\"})"]]
     [:p "TIFF supports LZW compression (default), deflate, or none. DPI metadata is embedded in the file header — print software reads it automatically."]]}

   {:id "manifest"
    :title "Archival Manifests"
    :content
    [:div
     [:p "For print editions, attach a manifest to every render:"]
     [:pre [:code
            "(eido/render scene {:output \"edition-01.tiff\"
                        :dpi 300
                        :emit-manifest? true
                        :seed 42
                        :params params})"]]
     [:p "The manifest records the full scene, parameters, Eido version, and timestamp — everything needed to reproduce the exact print years later."]]}])

(defn- workflow-animation-sections []
  [{:id "basics"
    :title "Frames as Sequences"
    :content
    [:div
     [:p "An animation is a sequence of scenes — one map per frame. No timeline, no keyframes, no mutation:"]
     [:pre [:code
            "(require '[eido.animate :as anim])

(def frames
  (anim/frames 60
    (fn [t]   ;; t goes from 0.0 to 1.0
      {:image/size [400 400]
       :image/background [:color/rgb 30 30 40]
       :image/nodes
       [{:node/type     :shape/circle
         :circle/center [200 200]
         :circle/radius (* 150 t)
         :style/fill    [:color/hsl (* 360 t) 0.8 0.5]}]})))"]]
     [:p "60 frames = 60 maps in a vector. Each frame is an independent scene — no shared mutable state between frames."]]}

   {:id "easing"
    :title "Easing and Helpers"
    :content
    [:div
     [:p "The animation helpers shape how values change over time:"]
     [:pre [:code
            ";; Sine pulse — 0→1→0 over the cycle
(anim/pulse t)

;; Linear fade — 1→0
(anim/fade-linear t)

;; Quadratic fade — softer tail
(anim/fade-out t)

;; Quadratic ease-in
(anim/fade-in t)"]]
     [:p "Combine these with your own math. Since " [:code "t"] " is just a number, any function works."]]}

   {:id "gif-export"
    :title "GIF Export"
    :content
    [:div
     [:pre [:code
            ";; Animated GIF — loops by default
(eido/render frames {:output \"anim.gif\" :fps 30})

;; Non-looping GIF
(eido/render frames {:output \"anim.gif\" :fps 30 :loop false})

;; Higher resolution
(eido/render frames {:output \"anim.gif\" :fps 30 :scale 2})"]]]}

   {:id "svg-animation"
    :title "Animated SVG"
    :content
    [:div
     [:pre [:code
            ";; Animated SVG using SMIL
(eido/render frames {:output \"anim.svg\" :fps 30})

;; Or get the SVG string directly
(eido/render frames {:format :svg :fps 30})"]]
     [:p "Animated SVGs are resolution-independent and work in modern browsers."]]}

   {:id "png-sequence"
    :title "PNG Sequence"
    :content
    [:div
     [:p "For video production, render a numbered PNG sequence and encode with ffmpeg:"]
     [:pre [:code
            ";; Write frame-0000.png through frame-0059.png
(eido/render frames {:output \"frames/\" :fps 30})

;; Then encode:
;; ffmpeg -framerate 30 -i frames/frame-%04d.png -c:v libx264 -pix_fmt yuv420p out.mp4"]]]}

   {:id "looping"
    :title "Seamless Loops"
    :content
    [:div
     [:p "Use 4D noise for seamless looping — sample a circle in the noise space:"]
     [:pre [:code
            "(require '[eido.gen.noise :as noise])

;; Seamless 1-second loop at 30fps
(anim/frames 30
  (fn [t]
    (let [angle (* 2 Math/PI t)
          nx    (Math/cos angle)
          ny    (Math/sin angle)]
      ;; perlin4d produces values that loop seamlessly
      ;; as (nx, ny) trace a circle
      (make-scene (noise/perlin4d x y nx ny)))))"]]
     [:p [:code "perlin4d"] " is specifically designed for this — the cos/sin trick traces a circle in the 3rd and 4th dimensions, producing a seamless loop in the first two."]]}])

(defn- workflow-3d-sections []
  [{:id "meshes"
    :title "Building Meshes"
    :content
    [:div
     [:p "3D scenes start with mesh primitives — pure data maps describing vertices and faces:"]
     [:pre [:code
            "(require '[eido.scene3d :as s3d])

;; Primitive constructors
(s3d/sphere-mesh 1.0)
(s3d/sphere-mesh 1.0 {:segments 32 :rings 20})
(s3d/cube-mesh 1.0)
(s3d/cylinder-mesh 0.5 2.0)
(s3d/torus-mesh 1.5 0.5)
(s3d/plane-mesh 2.0 2.0)

;; Platonic solids
(s3d/icosahedron-mesh 1.0)
(s3d/dodecahedron-mesh 1.0)"]]
     [:p "Every mesh is a map with " [:code ":vertices"] " and " [:code ":faces"] ". You can inspect, transform, and combine them as data."]]}

   {:id "transforms"
    :title "Mesh Transforms"
    :content
    [:div
     [:p "All transforms are pure functions — mesh in, mesh out:"]
     [:pre [:code
            "(-> (s3d/sphere-mesh 1.0)
    (s3d/translate [2 0 0])
    (s3d/rotate-y 0.5)
    (s3d/scale [1 2 1])
    (s3d/subdivide-mesh)
    (s3d/deform (fn [v] (update v 1 + (* 0.2 (noise/perlin3d (v 0) (v 1) (v 2)))))))"]]
     [:p "Chain transforms with " [:code "->"] ". Deform takes a function from vertex to vertex — use noise for organic shapes."]]}

   {:id "camera"
    :title "Camera and Projection"
    :content
    [:div
     [:pre [:code
            ";; Perspective projection
(def proj (s3d/perspective {:fov 60 :near 0.1 :far 100
                            :width 800 :height 600}))

;; Position the camera
(def cam (s3d/orbit proj [0 0 0]
           {:radius 5 :yaw 0.3 :pitch -0.2}))"]]
     [:p "The projection transforms 3D coordinates to 2D screen space. " [:code "orbit"] " positions the camera looking at a target point."]]}

   {:id "rendering"
    :title "Rendering to 2D"
    :content
    [:div
     [:p "3D meshes render into regular 2D scene nodes:"]
     [:pre {:data-img "docs-wf-3d-sphere.png"} [:code
            ";; Render a mesh into 2D nodes
(def nodes (s3d/render-mesh mesh cam
             {:style {:fill :white
                      :stroke {:color :black :width 0.5}}}))

;; Compose into a scene
{:image/size [800 600]
 :image/background [:color/rgb 30 30 40]
 :image/nodes nodes}"]]
     [:p "The output is standard Eido scene nodes — polygons with fills and strokes. Everything downstream (export, plotter output, animation) works normally."]]}

   {:id "textures"
    :title "Procedural Textures"
    :content
    [:div
     [:p "The 2D↔3D bridge lets you use noise, palettes, and fields as surface textures:"]
     [:pre [:code
            ";; UV-mapped noise texture
(s3d/paint-mesh mesh
  (fn [u v face-normal]
    (let [n (noise/fbm u v {:octaves 4 :scale 3.0})]
      [:color/hsl (* 360 n) 0.6 0.5])))"]]
     [:p "The paint function receives UV coordinates and the face normal — use them to drive color, pattern, or density."]]}

   {:id "npr"
    :title "Non-Photorealistic Rendering"
    :content
    [:div
     [:p "Apply 2D hatching and stippling to 3D faces, with density driven by lighting:"]
     [:pre [:code
            ";; Hatch lines whose density follows the light direction
(s3d/render-mesh mesh cam
  {:style :hatch
   :hatch {:angle 45 :spacing 3 :light-dir [1 1 1]}})"]]
     [:p "Lit faces get sparse hatching; shadowed faces get dense hatching. This produces a hand-drawn look that works especially well for plotter output."]]}])

(defn- workflow-color-sections []
  [{:id "color-spaces"
    :title "Perceptual Color"
    :content
    [:div
     [:p "Eido works in OKLAB/OKLCH — perceptually uniform color spaces where mathematical distance matches visual difference:"]
     [:pre [:code
            "(require '[eido.color :as color])

;; Convert and manipulate
(color/rgb->oklch [255 100 50])
(color/oklch 0.7 0.15 30)

;; Perceptual interpolation
(color/lerp-oklab color-a color-b 0.5)

;; Contrast checking (WCAG 2.0)
(color/contrast :white :black)   ;=> 21.0
(color/contrast :white :yellow)  ;=> 1.07"]]
     [:p "OKLCH uses lightness, chroma, and hue — intuitive axes for artistic color decisions."]]}

   {:id "palettes"
    :title "Palette Generation"
    :content
    [:div
     [:pre [:code
            "(require '[eido.color.palette :as palette])

;; Built-in palettes
(:sunset palette/palettes)
(:ocean palette/palettes)

;; Generate from color theory
(palette/complementary base-color)
(palette/analogous base-color 5)
(palette/triadic base-color)

;; Assign semantic roles
(palette/with-roles pal
  {:background 0 :primary 1 :accent 2 :text 3})"]]]}

   {:id "manipulation"
    :title "Palette Manipulation"
    :content
    [:div
     [:p "Transform entire palettes while preserving relationships:"]
     [:pre [:code
            ";; Temperature and saturation
(palette/warmer pal)
(palette/cooler pal)
(palette/muted pal)
(palette/vivid pal)

;; Lightness
(palette/darker pal)
(palette/lighter pal)

;; General adjustment
(palette/adjust pal {:lightness 0.1 :chroma -0.05})"]]]}

   {:id "analysis"
    :title "Palette Analysis"
    :content
    [:div
     [:p "Check that your palette works before committing to it:"]
     [:pre [:code
            ";; Minimum pairwise contrast (WCAG)
(palette/min-contrast pal)

;; Sort by perceptual lightness
(palette/sort-by-lightness pal)

;; Visual preview at the REPL
(palette/swatch pal)"]]
     [:p [:code "min-contrast"] " returns the lowest WCAG contrast ratio between any two colors in the palette — useful for ensuring text readability or visual separation."]]}

   {:id "extraction"
    :title "Extracting from Images"
    :content
    [:div
     [:p "Pull a palette from an existing image using k-means clustering in OKLAB space:"]
     [:pre {:data-img "docs-wf-color-swatch.png"} [:code
            "(palette/from-image \"reference.jpg\" 5)
;=> [[:color/rgb 42 38 35]
;    [:color/rgb 180 140 90]
;    [:color/rgb 220 200 170]
;    [:color/rgb 80 100 60]
;    [:color/rgb 150 50 40]]"]]
     [:p "The number is how many dominant colors to extract. Clustering in OKLAB produces perceptually balanced palettes."]]}

   {:id "application"
    :title "Applying Color"
    :content
    [:div
     [:p "Use palette and noise together for organic color distribution:"]
     [:pre [:code
            "(require '[eido.gen.vary :as vary])

;; Assign palette colors by index
(vary/by-palette 10 pal {:seed 42})

;; Noise-driven palette mapping
(vary/by-noise-palette positions pal
  {:noise-scale 0.01 :seed 42})

;; Weighted random palette — some colors appear more often
(palette/weighted-pick pal [0.4 0.3 0.2 0.1] {:seed 42})"]]
     [:p "The " [:code "vary"] " functions take seeds for deterministic results — same seed, same color assignment every time."]]}])

(def workflow-pages
  "Registry of all workflow guide pages."
  [{:slug "sketching"  :title "Sketching & Iteration"
   :desc "REPL-driven exploration, watch workflows, seed grids, and rapid prototyping."
   :sections-fn workflow-sketching-sections}
  {:slug "editions"   :title "Long-Form Editions"
   :desc "Deterministic series, seed management, trait distribution, and batch rendering."
   :sections-fn workflow-editions-sections}
  {:slug "plotter"    :title "Plotter Art"
   :desc "Stroke-only SVG, pen grouping, travel optimization, and polyline export."
   :sections-fn workflow-plotter-sections}
  {:slug "print"      :title "Print Production"
   :desc "Paper presets, real-world units, margins, DPI, and archival TIFF output."
   :sections-fn workflow-print-sections}
  {:slug "animation"  :title "Animation & Screen"
   :desc "Frame sequences, GIF export, animated SVG, easing, and seamless loops."
   :sections-fn workflow-animation-sections}
  {:slug "3d"         :title "3D Generative Art"
   :desc "Mesh construction, transforms, camera, textures, and non-photorealistic rendering."
   :sections-fn workflow-3d-sections}
  {:slug "color"      :title "Color & Palette Development"
   :desc "Perceptual color, palette generation, analysis, image extraction, and application."
   :sections-fn workflow-color-sections}])
