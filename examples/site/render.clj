(ns site.render
  "Site builder — renders examples and generates the eido website."
  (:require
    [clojure.java.io :as io]
    [clojure.repl :as repl]
    [clojure.string :as str]
    [eido.animate :as anim]
    [eido.color :as color]
    [eido.color.palette :as palette]
    [eido.core :as eido]
    [eido.gen.boids :as boids]
    [eido.gen.ca :as ca]
    [eido.gen.circle :as circle]
    [eido.gen.flow :as flow]
    [eido.gen.noise :as noise]
    [eido.gen.particle :as particle]
    [eido.gen.prob :as prob]
    [eido.gen.series :as series]
    [eido.gen.subdivide :as subdivide]
    [eido.path.aesthetic :as aesthetic]
    [eido.scene :as scene]
    [eido.scene3d :as s3d]
    [site.pages :as pages]
    [site.styles :as styles]
    [hiccup.page :as page]
    [hiccup2.core :as h]))

;; --- Configuration ---

(def site-url "https://eido.leifericf.com")

(def highlight-clj-js
  "function highlightClj(code) {
  // HTML-escape first
  code = code.replace(/&/g, '&amp;').replace(/\\x3c/g, '&lt;').replace(/>/g, '&gt;');
  // Extract comments and strings into placeholders so they don't interfere
  var tokens = [];
  code = code.replace(/(;;[^\\n]*)/g, function(m) { tokens.push('\\x3cspan class=\"clj-comment\">' + m + '\\x3c/span>'); return '\\x00T' + (tokens.length-1) + 'T\\x00'; });
  code = code.replace(/(\"(?:[^\"\\\\]|\\\\.)*\")/g, function(m) { tokens.push('\\x3cspan class=\"clj-string\">' + m + '\\x3c/span>'); return '\\x00T' + (tokens.length-1) + 'T\\x00'; });
  // Highlight remaining tokens
  code = code.replace(/(:[a-zA-Z][a-zA-Z0-9_\\-.*+!?\\/<>]*)/g, '\\x3cspan class=\"clj-keyword\">$1\\x3c/span>');
  code = code.replace(/\\b(\\d+\\.?\\d*)\\b/g, '\\x3cspan class=\"clj-number\">$1\\x3c/span>');
  code = code.replace(/(?<=\\()\\b(defn-?|def|let|fn|if|when|cond|do|loop|recur|for|doseq|mapv|map|filter|reduce|into|concat|vec|assoc|merge|require|ns)\\b/g, '\\x3cspan class=\"clj-special\">$1\\x3c/span>');
  // Restore placeholders
  code = code.replace(/\\x00T(\\d+)T\\x00/g, function(_, i) { return tokens[parseInt(i)]; });
  return code;
}")


(def example-namespaces
  "Namespaces to scan for example functions."
  '[gallery.generative
    gallery.showcase
    gallery.art
    gallery.artisan
    gallery.scenes-2d
    gallery.scenes-3d
    gallery.mixed
    gallery.particles
    gallery.typography
    gallery.paint])

(def api-namespace-groups
  "API namespaces organized by category for sidebar display."
  [{:category "Core"
    :namespaces '[eido.core]}
   {:category "Drawing"
    :namespaces '[eido.path eido.scene eido.text]}
   {:category "Path Operations"
    :namespaces '[eido.path.stroke eido.path.distort eido.path.warp
                  eido.path.morph eido.path.decorate eido.path.aesthetic]}
   {:category "Color"
    :namespaces '[eido.color eido.color.palette]}
   {:category "Generative"
    :namespaces '[eido.gen.noise eido.gen.flow eido.gen.contour
                  eido.gen.scatter eido.gen.voronoi eido.gen.lsystem
                  eido.gen.particle eido.gen.stipple eido.gen.hatch
                  eido.gen.vary eido.gen.prob eido.gen.circle
                  eido.gen.subdivide eido.gen.series eido.gen.ca
                  eido.gen.boids eido.gen.coloring]}
   {:category "Texture"
    :namespaces '[eido.texture]}
   {:category "Paint"
    :namespaces '[eido.paint]}
   {:category "Animation"
    :namespaces '[eido.animate]}
   {:category "3D"
    :namespaces '[eido.scene3d eido.scene3d.camera eido.scene3d.mesh
                  eido.scene3d.transform eido.scene3d.topology
                  eido.scene3d.surface eido.scene3d.modeling
                  eido.scene3d.render]}
   {:category "I/O"
    :namespaces '[eido.io.obj eido.io.polyline]}
   {:category "Math"
    :namespaces '[eido.math]}
   {:category "Visual Computation"
    :namespaces '[eido.ir eido.ir.domain eido.ir.resource
                  eido.ir.fill eido.ir.effect eido.ir.field
                  eido.ir.program eido.ir.transform eido.ir.generator
                  eido.ir.vary eido.ir.material eido.ir.lower]}])

(def eido-namespaces
  "Eido source namespaces for API doc generation."
  (vec (mapcat :namespaces api-namespace-groups)))

;; --- Discovery ---

(defn find-examples
  "Finds all vars with :example metadata in the given namespace."
  [ns-sym]
  (require ns-sym)
  (->> (ns-publics (find-ns ns-sym))
       vals
       (filter #(:example (meta %)))
       (sort-by #(:title (:example (meta %))))
       (mapv (fn [v]
               (let [ex (:example (meta v))]
                 (merge ex {:var v :ns ns-sym}))))))

(defn all-examples
  "Returns all examples grouped by category."
  []
  (doseq [ns-sym example-namespaces]
    (require ns-sym))
  (->> example-namespaces
       (mapv (fn [ns-sym]
               {:ns       ns-sym
                :category (or (:category (meta (find-ns ns-sym)))
                              (-> (name ns-sym)
                                  (str/replace #"examples\.gallery\." "")
                                  (str/replace "-" " ")
                                  str/capitalize))
                :examples (find-examples ns-sym)}))
       (remove #(empty? (:examples %)))))

;; --- Rendering examples ---

(defn render-example!
  "Renders a single example to the given output directory."
  [{:keys [var output]} out-dir]
  (let [result  @var
        scene   (result)
        path    (str out-dir "/images/" output)]
    (io/make-parents path)
    (if (:frames scene)
      (eido/render (:frames scene) {:output path :fps (:fps scene 30)})
      (eido/render scene {:output path}))
    path))

(defn render-all-examples!
  "Renders all discovered examples. Returns a seq of rendered paths."
  [out-dir]
  (let [groups (all-examples)]
    (doall
      (for [{:keys [examples]} groups
            example examples]
        (do
          (println "  Rendering" (:output example) "...")
          (render-example! example out-dir))))))

;; --- Source code extraction ---

(defn example-source
  "Returns the source code string for an example var."
  [{:keys [var]}]
  (repl/source-fn (symbol (str (namespace (symbol var)))
                          (str (name (symbol var))))))

;; --- Docs preview rendering ---

(defn- insert-doc-previews
  "Walks Hiccup content, inserting preview images after code blocks
  that have a :data-img attribute on their :pre element.
  img-prefix controls the relative path to the images directory
  (default \"../images/\" for depth-1 pages)."
  ([content] (insert-doc-previews content "../images/"))
  ([content img-prefix]
   (cond
     (not (vector? content)) content
     (not (keyword? (first content))) (mapv #(insert-doc-previews % img-prefix) content)

     ;; [:pre {:data-img "file.png"} [:code "..."]]
     (and (= :pre (first content))
          (map? (second content))
          (:data-img (second content)))
     (let [img-file (:data-img (second content))
           clean-attrs (dissoc (second content) :data-img)
           clean-pre (if (seq clean-attrs)
                       (into [:pre clean-attrs] (drop 2 content))
                       (into [:pre] (drop 2 content)))]
       [:div.docs-code-example
        clean-pre
        [:img.docs-preview {:src (str img-prefix img-file)
                            :alt "Rendered output"
                            :loading "lazy"}]])

     ;; Recurse into children of Hiccup elements
     :else
     (let [has-attrs? (and (> (count content) 1) (map? (second content)))
           tag (first content)
           attrs (when has-attrs? (second content))
           children (if has-attrs? (drop 2 content) (rest content))]
       (into (if attrs [tag attrs] [tag])
             (map #(insert-doc-previews % img-prefix) children))))))

(defn docs-scenes
  "Returns a map of {filename -> scene} for docs code example previews.
  Each scene matches or illustrates its corresponding code block."
  []
  (let [bg [:color/rgb 245 243 238]
        helper-nodes [(assoc (scene/regular-polygon [100 125] 70 6)
                             :style/fill [:color/rgb 100 150 255])
                      (assoc (scene/star [280 125] 70 30 5)
                             :style/fill [:color/rgb 255 100 100])
                      (merge (scene/triangle [410 210] [510 40] [610 210])
                             {:style/fill [:color/rgb 100 200 100]})
                      (merge (scene/smooth-path [[660 200] [740 50] [820 200] [900 50]])
                             {:style/stroke {:color [:color/rgb 200 100 50] :width 3}})]
        grid-nodes (vec (scene/grid 10 10
                          (fn [col row]
                            {:node/type :shape/circle
                             :circle/center [(+ 30 (* col 40)) (+ 30 (* row 40))]
                             :circle/radius 15
                             :style/fill [:color/rgb (* col 25) (* row 25) 128]})))
        dist-nodes (vec (scene/distribute 8 [50 200] [750 200]
                          (fn [x y t]
                            {:node/type :shape/circle
                             :circle/center [x y]
                             :circle/radius (+ 5 (* 20 t))
                             :style/fill [:color/rgb 0 0 0]})))
        radial-nodes (vec (scene/radial 12 [200 200] 120
                            (fn [x y _angle]
                              {:node/type :shape/circle
                               :circle/center [x y]
                               :circle/radius 15
                               :style/fill [:color/rgb 200 0 0]})))
        ]
    {"docs-rect.png"
     {:image/size [300 200] :image/background bg
      :image/nodes [{:node/type :shape/rect
                     :rect/xy [50 50] :rect/size [200 100]
                     :style/fill [:color/rgb 0 128 255]}]}

     "docs-rect-rounded.png"
     {:image/size [300 200] :image/background bg
      :image/nodes [{:node/type :shape/rect
                     :rect/xy [50 50] :rect/size [200 100]
                     :rect/corner-radius 16
                     :style/fill [:color/rgb 0 128 255]}]}

     "docs-circle.png"
     {:image/size [400 400] :image/background bg
      :image/nodes [{:node/type :shape/circle
                     :circle/center [200 200] :circle/radius 80
                     :style/stroke {:color [:color/rgb 0 0 0] :width 2}}]}

     "docs-ellipse.png"
     {:image/size [400 400] :image/background bg
      :image/nodes [{:node/type :shape/ellipse
                     :ellipse/center [200 200]
                     :ellipse/rx 120 :ellipse/ry 60
                     :style/fill [:color/rgb 200 50 50]}]}

     "docs-arc.png"
     {:image/size [400 400] :image/background bg
      :image/nodes [{:node/type :shape/arc
                     :arc/center [200 200]
                     :arc/rx 80 :arc/ry 80
                     :arc/start 0 :arc/extent 270
                     :arc/mode :pie
                     :style/fill [:color/rgb 255 200 50]}]}

     "docs-line.png"
     {:image/size [400 300] :image/background bg
      :image/nodes [{:node/type :shape/line
                     :line/from [50 50] :line/to [350 250]
                     :style/stroke {:color [:color/rgb 0 0 0] :width 2}}]}

     "docs-path.png"
     {:image/size [400 300] :image/background bg
      :image/nodes [{:node/type :shape/path
                     :path/commands [[:move-to [100 200]]
                                     [:line-to [200 50]]
                                     [:curve-to [250 0] [300 100] [300 200]]
                                     [:quad-to [250 250] [200 200]]
                                     [:close]]
                     :style/fill [:color/rgb 255 200 50]}]}

     "docs-helpers.png"
     {:image/size [950 250] :image/background bg
      :image/nodes helper-nodes}

     "docs-text.png"
     {:image/size [250 120] :image/background bg
      :image/nodes [{:node/type :shape/text
                     :text/content "Hello"
                     :text/font {:font/family "Serif" :font/size 48
                                 :font/weight :bold}
                     :text/origin [125 80]
                     :text/align :center
                     :style/fill [:color/rgb 0 0 0]}]}

     "docs-text-glyphs.png"
     {:image/size [400 150] :image/background bg
      :image/nodes [{:node/type :shape/text-glyphs
                     :text/content "COLOR"
                     :text/font {:font/family "SansSerif" :font/size 64}
                     :text/origin [50 100]
                     :text/glyphs [{:glyph/index 0
                                    :style/fill [:color/rgb 255 0 0]}
                                   {:glyph/index 1
                                    :style/fill [:color/rgb 0 255 0]}]
                     :style/fill [:color/rgb 100 100 100]}]}

     "docs-text-on-path.png"
     {:image/size [400 200] :image/background bg
      :image/nodes [{:node/type :shape/text-on-path
                     :text/content "ALONG A CURVE"
                     :text/font {:font/family "SansSerif" :font/size 24}
                     :text/path [[:move-to [30 170]]
                                 [:curve-to [120 30] [280 30] [370 170]]]
                     :text/offset 10
                     :text/spacing 1
                     :style/fill [:color/rgb 0 0 0]}]}

     "docs-gradient-linear.png"
     {:image/size [300 200] :image/background bg
      :image/nodes [{:node/type :shape/rect
                     :rect/xy [50 50] :rect/size [200 100]
                     :rect/corner-radius 8
                     :style/fill {:gradient/type :linear
                                  :gradient/from [0 0]
                                  :gradient/to [200 0]
                                  :gradient/stops
                                  [[0.0 [:color/rgb 255 0 0]]
                                   [1.0 [:color/rgb 0 0 255]]]}}]}

     "docs-gradient-radial.png"
     {:image/size [300 250] :image/background bg
      :image/nodes [{:node/type :shape/circle
                     :circle/center [150 125] :circle/radius 100
                     :style/fill {:gradient/type :radial
                                  :gradient/center [100 100]
                                  :gradient/radius 100
                                  :gradient/stops
                                  [[0.0 [:color/name "white"]]
                                   [1.0 [:color/name "black"]]]}}]}

     "docs-hatch.png"
     {:image/size [300 250] :image/background bg
      :image/nodes [{:node/type :shape/circle
                     :circle/center [150 125] :circle/radius 100
                     :style/fill {:fill/type :hatch
                                  :hatch/angle 45
                                  :hatch/spacing 4
                                  :hatch/stroke-width 1
                                  :hatch/color [:color/rgb 0 0 0]}}]}

     "docs-stipple.png"
     {:image/size [300 250] :image/background bg
      :image/nodes [{:node/type :shape/circle
                     :circle/center [150 125] :circle/radius 100
                     :style/fill {:fill/type :stipple
                                  :stipple/density 0.6
                                  :stipple/radius 1.0
                                  :stipple/seed 42
                                  :stipple/color [:color/rgb 0 0 0]}}]}

     "docs-group.png"
     {:image/size [400 400] :image/background bg
      :image/nodes [{:node/type :group
                     :node/transform [[:transform/translate 200 200]]
                     :style/fill [:color/rgb 255 0 0]
                     :node/opacity 0.8
                     :group/children
                     [{:node/type :shape/circle
                       :circle/center [0 0] :circle/radius 80}
                      {:node/type :shape/rect
                       :rect/xy [-30 -30] :rect/size [60 60]
                       :style/fill [:color/rgb 0 0 255]
                       :node/opacity 0.5}]}]}

     "docs-grid.png"
     {:image/size [420 420] :image/background bg
      :image/nodes grid-nodes}

     "docs-distribute.png"
     {:image/size [800 400] :image/background bg
      :image/nodes dist-nodes}

     "docs-radial.png"
     {:image/size [400 400] :image/background bg
      :image/nodes radial-nodes}

     "docs-contour.png"
     {:image/size [500 400] :image/background bg
      :image/nodes [{:node/type :contour
                     :contour/bounds [0 0 500 400]
                     :contour/opts {:thresholds [0.0 0.2 0.4]
                                    :resolution 3
                                    :noise-scale 0.012
                                    :seed 42}
                     :style/stroke {:color [:color/rgb 100 150 100]
                                    :width 1}}]}

     ;; --- Generative docs scenes ---

     "docs-uniform-vs-gaussian.png"
     (let [uniform-pts (prob/uniform 80 20.0 380.0 42)
           gaussian-pts (prob/gaussian 80 200.0 50.0 99)]
       {:image/size [400 300] :image/background bg
        :image/nodes
        (into
          (mapv (fn [x] {:node/type :shape/circle
                         :circle/center [x 80] :circle/radius 3
                         :style/fill [:color/rgb 80 120 200]})
                uniform-pts)
          (mapv (fn [x] {:node/type :shape/circle
                         :circle/center [(max 20 (min 380 x)) 200] :circle/radius 3
                         :style/fill [:color/rgb 200 80 80]})
                gaussian-pts))})

     "docs-weighted-shapes.png"
     (let [shapes (mapv #(prob/pick-weighted [:circle :square :triangle] [6 3 1] %)
                        (range 60))]
       {:image/size [500 120] :image/background bg
        :image/nodes
        (vec (map-indexed
               (fn [i shape]
                 (let [x (+ 15 (* i 8)) y 60
                       color (case shape
                               :circle   [:color/rgb 80 140 220]
                               :square   [:color/rgb 220 160 40]
                               :triangle [:color/rgb 200 60 80])]
                   (case shape
                     :circle   {:node/type :shape/circle
                                :circle/center [x y] :circle/radius 3.5
                                :style/fill color}
                     :square   {:node/type :shape/rect
                                :rect/xy [(- x 3) (- y 3)] :rect/size [6 6]
                                :style/fill color}
                     :triangle {:node/type :shape/path
                                :path/commands [[:move-to [x (- y 4)]]
                                                [:line-to [(+ x 4) (+ y 3)]]
                                                [:line-to [(- x 4) (+ y 3)]]
                                                [:close]]
                                :style/fill color})))
               shapes))})

     "docs-circle-pack.png"
     (let [circles (circle/circle-pack [20 20 360 360]
                     {:min-radius 3 :max-radius 35 :padding 2
                      :max-circles 200 :seed 42})
           pal (:sunset palette/palettes)
           colors (palette/weighted-sample pal [3 2 2 1 5] (count circles) 42)]
       {:image/size [400 400] :image/background bg
        :image/nodes
        (mapv (fn [{[x y] :center r :radius} c]
                {:node/type :shape/circle
                 :circle/center [x y] :circle/radius r
                 :style/fill c
                 :style/stroke {:color [:color/rgba 40 30 20 0.25] :width 0.5}})
              circles colors)})

     "docs-circle-pack-star.png"
     (let [star-cmds (:path/commands (scene/star [200 200] 180 70 5))
           circles (circle/circle-pack-in-path star-cmds
                     {:min-radius 2 :max-radius 15 :padding 1 :seed 42})]
       {:image/size [400 400] :image/background bg
        :image/nodes
        (mapv (fn [{[x y] :center r :radius} i]
                {:node/type :shape/circle
                 :circle/center [x y] :circle/radius r
                 :style/fill [:color/hsl (mod (* i 17) 360) 0.6 0.55]})
              circles (range))})

     "docs-subdivide.png"
     (let [rects (subdivide/subdivide [15 15 370 370]
                   {:depth 4 :min-size 35 :padding 5 :seed 77})
           colors [[:color/rgb 245 245 240] [:color/rgb 245 245 240]
                   [:color/rgb 245 245 240] [:color/rgb 220 30 30]
                   [:color/rgb 30 60 180] [:color/rgb 245 220 40]]]
       {:image/size [400 400] :image/background [:color/rgb 20 20 20]
        :image/nodes
        (mapv (fn [{[x y w h] :rect :as cell}]
                {:node/type :shape/rect :rect/xy [x y] :rect/size [w h]
                 :style/fill (prob/pick colors (+ (hash cell) 42))
                 :style/stroke {:color [:color/rgb 20 20 20] :width 3}})
              rects)})

     "docs-weighted-palette.png"
     (let [pal [[:color/rgb 240 235 225] [:color/rgb 200 50 50]
                [:color/rgb 50 120 200] [:color/rgb 255 200 0]]
           weights [5 2 2 1]
           colors (palette/weighted-sample pal weights 100 42)]
       {:image/size [500 80] :image/background bg
        :image/nodes
        (mapv (fn [color i]
                {:node/type :shape/rect
                 :rect/xy [(+ 5 (* i 4.9)) 10] :rect/size [4 60]
                 :style/fill color})
              colors (range))})

     "docs-smooth-vs-raw.png"
     (let [pts [[30 180] [100 40] [180 160] [260 30] [340 150] [400 60] [470 170]]
           raw-cmds (into [[:move-to (first pts)]]
                          (mapv (fn [p] [:line-to p]) (rest pts)))
           smooth (aesthetic/smooth-commands raw-cmds {:samples 60})]
       {:image/size [500 220] :image/background bg
        :image/nodes
        [{:node/type :shape/path :path/commands raw-cmds
          :style/stroke {:color [:color/rgba 180 180 180 0.7] :width 2}}
         {:node/type :shape/path :path/commands smooth
          :style/stroke {:color [:color/rgb 200 60 60] :width 2.5}}]})

     "docs-jitter.png"
     (let [cmds [[:move-to [30 100]] [:line-to [470 100]]]
           j1 (aesthetic/jittered-commands cmds {:amount 4.0 :seed 42})
           j2 (aesthetic/jittered-commands cmds {:amount 12.0 :seed 42})]
       {:image/size [500 200] :image/background bg
        :image/nodes
        [{:node/type :shape/path :path/commands cmds
          :style/stroke {:color [:color/rgba 180 180 180 0.7] :width 1.5}}
         {:node/type :shape/path :path/commands j1
          :node/transform [[:transform/translate 0 -30]]
          :style/stroke {:color [:color/rgb 60 120 200] :width 1.5}}
         {:node/type :shape/path :path/commands j2
          :node/transform [[:transform/translate 0 30]]
          :style/stroke {:color [:color/rgb 200 60 80] :width 1.5}}]})

     "docs-dashes.png"
     (let [cmds [[:move-to [30 40]] [:line-to [470 40]]]
           d1 (aesthetic/dash-commands cmds {:dash [15.0 8.0]})
           d2 (aesthetic/dash-commands cmds {:dash [30.0 5.0]})
           d3 (aesthetic/dash-commands cmds {:dash [5.0 15.0]})]
       {:image/size [500 180] :image/background bg
        :image/nodes
        (vec (concat
               (mapv (fn [d] {:node/type :shape/path :path/commands d
                              :style/stroke {:color [:color/rgb 40 40 40] :width 2}})
                     (or d1 []))
               (mapv (fn [d] {:node/type :shape/path :path/commands d
                              :node/transform [[:transform/translate 0 50]]
                              :style/stroke {:color [:color/rgb 60 120 200] :width 2.5}})
                     (or d2 []))
               (mapv (fn [d] {:node/type :shape/path :path/commands d
                              :node/transform [[:transform/translate 0 100]]
                              :style/stroke {:color [:color/rgb 200 60 80] :width 2}})
                     (or d3 []))))})

     "docs-dashed-flow.png"
     (let [paths (flow/flow-field [20 20 460 360]
                   {:density 30 :steps 35 :step-size 3 :seed 42})
           pal (:ocean palette/palettes)]
       {:image/size [500 400] :image/background bg
        :image/nodes
        (vec (mapcat
               (fn [path-node i]
                 (let [cmds (:path/commands path-node)
                       smoothed (aesthetic/smooth-commands cmds {:samples 30})
                       dashes (aesthetic/dash-commands smoothed {:dash [10.0 6.0]})]
                   (mapv (fn [d] {:node/type :shape/path :path/commands d
                                  :style/stroke {:color (nth pal (mod i (count pal)))
                                                 :width 1.5}})
                         (or dashes []))))
               paths (range)))})

     "docs-ca-life.png"
     (let [g (ca/ca-run (ca/ca-grid 40 40 :random 42) :life 50)]
       {:image/size [400 400] :image/background bg
        :image/nodes (ca/ca->nodes g 10
                       {:style/fill [:color/rgb 30 30 30]})})

     "docs-rd-coral.png"
     (let [g (ca/rd-run (ca/rd-grid 80 80 :center 42)
               (:coral ca/rd-presets) 400)]
       {:image/size [400 400] :image/background [:color/rgb 10 20 40]
        :image/nodes (ca/rd->nodes g 5
                       (fn [a b]
                         (let [v (min 1.0 (* b 4))]
                           [:color/rgb (int (+ 10 (* 80 v)))
                            (int (+ 20 (* 120 v)))
                            (int (+ 40 (* 180 (- 1.0 (* a 0.3)))))])))})

     "docs-boids.gif"
     (let [config (assoc boids/classic :count 60 :bounds [0 0 500 350] :seed 42)
           frames (boids/simulate-flock config 80 {})]
       {:frames
        (anim/frames (count frames)
          (fn [t]
            (let [i (min (int (* t (dec (count frames)))) (dec (count frames)))]
              {:image/size [500 350]
               :image/background [:color/rgb 230 235 240]
               :image/nodes
               (boids/flock->nodes (nth frames i)
                 {:shape :triangle :size 7
                  :style {:style/fill [:color/rgb 40 45 55]}})})))
        :fps 24})

     "docs-series-grid.png"
     (let [spec {:hue {:type :uniform :lo 0.0 :hi 360.0}
                 :r   {:type :gaussian :mean 20.0 :sd 8.0}}
           editions (mapv #(assoc (series/series-params spec 42 %)
                                  :edition %)
                          (range 9))]
       {:image/size [400 400] :image/background [:color/rgb 30 30 35]
        :image/nodes
        (vec (mapcat
               (fn [{:keys [hue r edition]}]
                 (let [col (mod edition 3) row (quot edition 3)
                       cx (+ 70 (* col 130)) cy (+ 70 (* row 130))]
                   [{:node/type :shape/circle
                     :circle/center [cx cy]
                     :circle/radius (max 8 (min 55 r))
                     :style/fill [:color/hsl hue 0.7 0.55]}
                    {:node/type :shape/text
                     :text/content (str "#" edition)
                     :text/origin [(- cx 8) (+ cy (max 8 (min 55 r)) 14)]
                     :text/font {:font/family "SansSerif" :font/size 10}
                     :style/fill [:color/rgb 140 140 150]}]))
               editions))})

     ;; --- Color docs scenes ---

     "docs-color-formats.png"
     {:image/size [400 100] :image/background bg
      :image/nodes
      (vec (map-indexed
             (fn [i [label color]]
               {:node/type :group
                :group/children
                [{:node/type :shape/rect
                  :rect/xy [(+ 10 (* i 78)) 10] :rect/size [70 50]
                  :rect/corner-radius 6
                  :style/fill color}
                 {:node/type :shape/text
                  :text/content label
                  :text/origin [(+ 45 (* i 78)) 80]
                  :text/font {:font/family "SansSerif" :font/size 9}
                  :text/align :center
                  :style/fill [:color/rgb 100 100 100]}]})
             [["name" [:color/name "coral"]]
              ["rgb" [:color/rgb 255 127 80]]
              ["rgba" [:color/rgba 255 127 80 0.5]]
              ["hsl" [:color/hsl 16 1.0 0.66]]
              ["hex" [:color/hex "#FF7F50"]]]))}

     "docs-oklab-lerp.png"
     (let [steps 10
           sw    (/ 380.0 steps)]
       {:image/size [400 120] :image/background bg
        :image/nodes
        (vec
          (concat
            ;; RGB row (top)
            [{:node/type :shape/text
              :text/content "RGB"
              :text/origin [200 15]
              :text/font {:font/family "SansSerif" :font/size 9}
              :text/align :center
              :style/fill [:color/rgb 100 100 100]}]
            (for [i (range steps)]
              (let [t (/ (double i) (dec steps))]
                {:node/type :shape/rect
                 :rect/xy [(+ 10 (* i sw)) 20] :rect/size [sw 35]
                 :style/fill (color/lerp [:color/name "red"]
                                         [:color/name "cyan"] t)}))
            ;; OKLAB row (bottom)
            [{:node/type :shape/text
              :text/content "OKLAB"
              :text/origin [200 75]
              :text/font {:font/family "SansSerif" :font/size 9}
              :text/align :center
              :style/fill [:color/rgb 100 100 100]}]
            (for [i (range steps)]
              (let [t (/ (double i) (dec steps))]
                {:node/type :shape/rect
                 :rect/xy [(+ 10 (* i sw)) 80] :rect/size [sw 35]
                 :style/fill (color/lerp-oklab [:color/name "red"]
                                               [:color/name "cyan"] t)}))))})

     "docs-color-manip.png"
     (let [base [:color/name "red"]]
       {:image/size [400 80] :image/background bg
        :image/nodes
        (vec (map-indexed
               (fn [i [label color]]
                 {:node/type :group
                  :group/children
                  [{:node/type :shape/rect
                    :rect/xy [(+ 10 (* i 65)) 10] :rect/size [55 40]
                    :rect/corner-radius 5
                    :style/fill color}
                   {:node/type :shape/text
                    :text/content label
                    :text/origin [(+ 37 (* i 65)) 68]
                    :text/font {:font/family "SansSerif" :font/size 8}
                    :text/align :center
                    :style/fill [:color/rgb 100 100 100]}]})
               [["original" base]
                ["lighten" (color/lighten base 0.2)]
                ["darken" (color/darken base 0.2)]
                ["saturate" (color/saturate base 0.3)]
                ["hue+120" (color/rotate-hue base 120)]
                ["blend" (color/lerp base [:color/name "blue"] 0.5)]]))})

     "docs-particles.gif"
     (let [fire-frames (vec (particle/simulate
                              (particle/with-position
                                particle/fire [200 330])
                              50 {:fps 25}))]
       {:frames
        (anim/frames (count fire-frames)
          (fn [t]
            (let [i (min (int (* t (dec (count fire-frames))))
                         (dec (count fire-frames)))]
              {:image/size [400 380]
               :image/background [:color/rgb 20 15 10]
               :image/nodes (nth fire-frames i)})))
        :fps 25})

     ;; --- Architecture page scenes ---

     "docs-arch-input.png"
     {:image/size [400 300] :image/background [:color/name "linen"]
      :image/nodes
      [{:node/type :shape/circle
        :circle/center [200 150] :circle/radius 80
        :style/fill [:color/name "coral"]}
       {:node/type :shape/rect
        :rect/xy [50 50] :rect/size [100 60]
        :style/fill [:color/name "steelblue"]}]}

     "docs-arch-flowfield.png"
     (let [paths (flow/flow-field [20 20 360 260]
                   {:density 25 :steps 30 :step-size 3 :seed 42})]
       {:image/size [400 300] :image/background bg
        :image/nodes
        (mapv (fn [path-node i]
                (assoc path-node
                  :style/stroke {:color [:color/hsl (mod (* i 15) 360) 0.5 0.45]
                                 :width 1.2}))
              paths (range))})

     "docs-arch-hatch.png"
     {:image/size [300 250] :image/background bg
      :image/nodes [{:node/type :shape/circle
                     :circle/center [150 125] :circle/radius 100
                     :style/fill {:fill/type :hatch
                                  :hatch/angle 45
                                  :hatch/spacing 5
                                  :hatch/stroke-width 1
                                  :hatch/color [:color/rgb 60 50 40]}}]}

     ;; --- Additional docs scenes ---

     "docs-strokes.png"
     {:image/size [400 200] :image/background bg
      :image/nodes
      [{:node/type :shape/path
        :path/commands [[:move-to [30 50]] [:line-to [180 50]]]
        :style/stroke {:color [:color/rgb 40 40 40] :width 6 :cap :round}}
       {:node/type :shape/path
        :path/commands [[:move-to [220 50]] [:line-to [370 50]]]
        :style/stroke {:color [:color/rgb 40 40 40] :width 6 :cap :butt}}
       {:node/type :shape/path
        :path/commands [[:move-to [30 120]] [:line-to [370 120]]]
        :style/stroke {:color [:color/rgb 80 120 200] :width 3 :dash [15 8]}}
       {:node/type :shape/path
        :path/commands [[:move-to [30 160]] [:line-to [370 160]]]
        :style/stroke {:color [:color/rgb 200 80 80] :width 3 :dash [5 12]}}]}

     "docs-clipping.png"
     {:image/size [300 300] :image/background bg
      :image/nodes
      [{:node/type :group
        :group/clip {:node/type :shape/circle
                     :circle/center [150 150]
                     :circle/radius 100}
        :group/children
        [{:node/type :shape/rect
          :rect/xy [50 50] :rect/size [100 200]
          :style/fill [:color/rgb 220 50 50]}
         {:node/type :shape/rect
          :rect/xy [150 50] :rect/size [100 200]
          :style/fill [:color/rgb 50 100 220]}
         {:node/type :shape/rect
          :rect/xy [50 50] :rect/size [200 100]
          :style/fill [:color/rgba 255 220 0 0.5]}]}]}

     "docs-compositing.png"
     {:image/size [300 200] :image/background bg
      :image/nodes
      [{:node/type :shape/circle
        :circle/center [110 100] :circle/radius 70
        :style/fill [:color/rgb 220 50 50]}
       {:node/type :shape/circle
        :circle/center [190 100] :circle/radius 70
        :style/fill [:color/rgb 50 100 220]
        :node/opacity 0.6}]}

     "docs-transforms.png"
     {:image/size [400 200] :image/background bg
      :image/nodes
      (vec (for [i (range 5)]
             {:node/type :shape/rect
              :rect/xy [0 0] :rect/size [40 40]
              :node/transform [[:transform/translate (+ 50 (* i 75)) 80]
                               [:transform/rotate (* i 0.3)]]
              :style/fill [:color/hsl (* i 60) 0.6 0.5]
              :style/stroke {:color [:color/rgb 40 40 40] :width 1}}))}

     "docs-noise-field.png"
     {:image/size [400 300] :image/background [:color/rgb 20 20 30]
      :image/nodes
      (vec (for [x (range 0 400 6)
                 y (range 0 300 6)]
             (let [v (noise/perlin2d (* x 0.015) (* y 0.015) {:seed 42})
                   brightness (+ 0.5 (* 0.5 v))]
               {:node/type :shape/rect
                :rect/xy [x y] :rect/size [5 5]
                :style/fill [:color/rgb
                             (int (* 255 brightness 0.3))
                             (int (* 255 brightness 0.7))
                             (int (* 255 brightness))]})))}

     "docs-animation.gif"
     {:frames
      (anim/frames 40
        (fn [t]
          {:image/size [250 250]
           :image/background [:color/rgb 30 30 40]
           :image/nodes
           [{:node/type :shape/circle
             :circle/center [125 125]
             :circle/radius (max 1 (* 90 t))
             :style/fill [:color/hsl (* 360 t) 0.8 0.5]}]}))
      :fps 20}

     "docs-easing.png"
     {:image/size [400 200] :image/background bg
      :image/nodes
      (vec (concat
             ;; Linear (gray dots)
             (for [i (range 20)]
               (let [t (/ i 19.0)]
                 {:node/type :shape/circle
                  :circle/center [(+ 30 (* 340 t)) (- 180 (* 150 t))]
                  :circle/radius 3
                  :style/fill [:color/rgb 180 180 180]}))
             ;; Ease-in-out (colored dots)
             (for [i (range 20)]
               (let [t (/ i 19.0)
                     et (anim/ease-in-out t)]
                 {:node/type :shape/circle
                  :circle/center [(+ 30 (* 340 t)) (- 180 (* 150 et))]
                  :circle/radius 4
                  :style/fill [:color/rgb 80 140 220]}))))}

     "docs-3d-sphere.png"
     (let [proj (s3d/perspective
                  {:scale 120 :origin [200 200]
                   :yaw 0.5 :pitch -0.3 :distance 5})
           light {:light/direction [1 1 0.5]
                  :light/ambient 0.25
                  :light/intensity 0.8}
           result (s3d/sphere proj [0 0 0]
                    {:radius 1.5
                     :style {:style/fill [:color/rgb 100 150 255]}
                     :light light
                     :subdivisions 4
                     :smooth true})]
       {:image/size [400 400] :image/background bg
        :image/nodes (if (sequential? result) (vec result) [result])})

     ;; --- Workflow previews ---

     "docs-wf-sketch-circle.png"
     {:image/size [400 400]
      :image/background [:color/name "linen"]
      :image/nodes
      [{:node/type     :shape/circle
        :circle/center [200 200]
        :circle/radius 120
        :style/fill    [:color/name "crimson"]}]}

     "docs-wf-plotter-strokes.png"
     (let [paths (flow/flow-field [0 0 400 300]
                   {:density 15 :steps 40 :noise-scale 0.005 :seed 7})]
       {:image/size [400 300]
        :image/background [:color/rgb 245 243 238]
        :image/nodes
        (mapv #(assoc % :style/stroke {:color [:color/rgb 30 30 30] :width 0.8})
              paths)})

     "docs-wf-print-paper.png"
     (let [paper (scene/paper :a4)]
       (-> paper
           (assoc :image/background :white
                  :image/nodes
                  [{:node/type :shape/rect
                    :rect/xy [1.0 1.0]
                    :rect/size [19.0 27.7]
                    :style/stroke {:color [:color/rgb 200 200 200] :width 0.02}}
                   {:node/type     :shape/circle
                    :circle/center [10.5 14.85]
                    :circle/radius 5.0
                    :style/fill    [:color/rgb 200 50 50]
                    :style/stroke  {:color [:color/rgb 30 30 30] :width 0.05}}
                   {:node/type     :shape/circle
                    :circle/center [10.5 14.85]
                    :circle/radius 3.0
                    :style/fill    [:color/rgb 50 100 200]
                    :style/stroke  {:color [:color/rgb 30 30 30] :width 0.05}}])
           scene/with-units))

     "docs-wf-color-swatch.png"
     {:image/size [400 80]
      :image/background [:color/rgb 245 243 238]
      :image/nodes
      (vec (map-indexed
             (fn [i color]
               {:node/type :shape/rect
                :rect/xy [(+ 10 (* i 78)) 10]
                :rect/size [68 60]
                :rect/corner-radius 6
                :style/fill color})
             [[:color/rgb 42 38 35]
              [:color/rgb 180 140 90]
              [:color/rgb 220 200 170]
              [:color/rgb 80 100 60]
              [:color/rgb 150 50 40]]))}

     "docs-wf-3d-sphere.png"
     (let [proj (s3d/perspective {:scale 120 :origin [200 200]
                                  :yaw 0.6 :pitch -0.3 :distance 5})
           light {:light/direction [1 1 0.5]
                  :light/ambient 0.2
                  :light/intensity 0.8}
           result (s3d/sphere proj [0 0 0]
                    {:radius 1.5
                     :style {:style/fill [:color/rgb 100 150 255]
                             :style/stroke {:color [:color/rgb 40 60 120]
                                            :width 0.5}}
                     :light light
                     :subdivisions 3
                     :smooth true})]
       {:image/size [400 400]
        :image/background [:color/rgb 30 30 40]
        :image/nodes (if (sequential? result) (vec result) [result])})

     }))

(defn render-docs-examples!
  "Renders preview images for docs code examples.
  Supports both static scenes and animated scenes with :frames."
  [out-dir]
  (doseq [[filename scene-data] (docs-scenes)]
    (let [path (str out-dir "/images/" filename)]
      (println "  Rendering" filename "...")
      (io/make-parents path)
      (if (:frames scene-data)
        (eido/render (:frames scene-data) {:output path :fps (:fps scene-data 24)})
        (eido/render scene-data {:output path})))))

;; --- HTML generation ---

(defn html-page
  "Wraps content in a full HTML page with nav, footer, and styles.
  :depth controls relative path prefix (0 = root, 1 = one dir deep)."
  [{:keys [title active-page depth] :or {depth 0}} & body]
  (let [prefix (if (zero? depth) "." (str/join "/" (repeat depth "..")))]
    (str
      "<!DOCTYPE html>\n"
      (h/html
        [:html {:lang "en"}
         [:head
          [:meta {:charset "utf-8"}]
          [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
          [:title (str title " — Eido")]
          [:style (h/raw (styles/site-css))]]
         [:body
          [:div.container
           [:nav.nav
            [:a.nav-logo {:href (str prefix "/")} "Eido"]
            [:ul.nav-links
             [:li [:a {:href (str prefix "/")
                       :style (when (= active-page :home) "color: #e0ddd5")}
                   "Home"]]
             [:li [:a {:href (str prefix "/gallery/")
                       :style (when (= active-page :gallery) "color: #e0ddd5")}
                   "Gallery"]]
             [:li [:a {:href (str prefix "/guide/")
                       :style (when (= active-page :docs) "color: #e0ddd5")}
                   "Guide"]]
             [:li [:a {:href (str prefix "/workflows/")
                       :style (when (= active-page :workflows) "color: #e0ddd5")}
                   "Workflows"]]
             [:li [:a {:href (str prefix "/api/")
                       :style (when (= active-page :api) "color: #e0ddd5")}
                   "API"]]
             [:li [:a {:href (str prefix "/architecture/")
                       :style (when (= active-page :architecture) "color: #e0ddd5")}
                   "How It Works"]]
             [:li [:a {:href (str prefix "/limitations/")
                       :style (when (= active-page :limitations) "color: #e0ddd5")}
                   "Scope"]]
             [:li [:a {:href "https://github.com/leifericf/eido"} "GitHub"]]]]
           [:main body]
           [:footer.footer
            [:p "Eido (from Greek " [:em "eido"] ", \"I see\") — describe what you see as plain data"]]
           ]]]))))

;; --- Landing page ---

(defn generate-landing-html
  "Generates the landing page HTML."
  [examples-by-category]
  (let [all-images (->> examples-by-category
                       (mapcat :examples)
                       (mapv :output))]
    (html-page {:title "Eido" :active-page :home :depth 0}
      [:div.beta-banner
       "Beta — The core API is stabilizing. Breaking changes may still occur between releases, but the fundamentals are in place."]
      [:section.hero
       [:h1.hero-title "Eido"]
       [:p.hero-tagline "Describe what you see as plain data"]
       [:p {:style "color: #6a6a7a; font-size: 0.85rem; margin-top: 0.3rem; font-style: italic;"} "From Greek " [:em "eido"] " \u2014 \"I see\""]
       [:div#hero-images.hero-images]
       [:div.hero-links
        [:a.hero-link.hero-link--primary {:href "./gallery/"} "Browse Gallery"]
        [:a.hero-link.hero-link--secondary {:href "./guide/"} "Read the Guide"]
        [:a.hero-link.hero-link--secondary {:href "./architecture/"} "How It Works"]]]
      [:section.features
       (for [{:keys [title desc]} (pages/features)]
         [:div.feature
          [:div.feature-marker "\u2022"]
          [:div.feature-body
           [:div.feature-title title]
           [:div.feature-desc desc]]])]
      [:section {:style "margin-top: 3rem"}
       [:h2 {:style "font-size: 1.5rem; margin-bottom: 1rem"} "How it works"]
       (pages/quick-start-content)]
      [:section {:style "margin-top: 2rem"}
       [:h2 {:style "font-size: 1.5rem; margin-bottom: 1rem"} "Getting Started"]
       (pages/install-content)]
      [:script (h/raw (str highlight-clj-js "
document.querySelectorAll('pre code').forEach(function(el) {
  el.innerHTML = highlightClj(el.textContent);
});
var allImages = [" (str/join ", " (map #(str "\"" % "\"") all-images)) "];
var shuffled = allImages.sort(function() { return 0.5 - Math.random(); });
var container = document.getElementById('hero-images');
shuffled.slice(0, 6).forEach(function(img) {
  var el = document.createElement('img');
  el.src = './images/' + img;
  el.alt = '';
  el.loading = 'lazy';
  el.style.cursor = 'pointer';
  el.onclick = function() { openLightbox(this.src, this.alt); };
  container.appendChild(el);
});
function openLightbox(src, alt) {
  var lb = document.getElementById('lightbox');
  document.getElementById('lightbox-img').src = src;
  lb.classList.add('active');
  document.body.style.overflow = 'hidden';
}
function closeLightbox() {
  document.getElementById('lightbox').classList.remove('active');
  document.body.style.overflow = '';
}
document.addEventListener('keydown', function(e) { if (e.key === 'Escape') closeLightbox(); });
"))]
      [:div#lightbox {:onclick "closeLightbox()"}
       [:img#lightbox-img]])))

;; --- Gallery page ---

(defn gallery-card
  "Renders a single gallery card with image, title, desc, and collapsible source."
  [example]
  (let [src (example-source example)
        card-id (str "src-" (hash (:output example)))]
    (let [tags (or (:tags example) [])]
      [:div.gallery-card {:data-tags (str/join "," tags)}
       [:div.gallery-card-img-wrap {:onclick "openLightbox(this.querySelector('img').src, this.querySelector('img').alt)"}
        [:img {:src (str "../images/" (:output example))
               :alt (:title example)
               :loading "lazy"}]
        [:div.gallery-card-expand
         [:svg {:width "18" :height "18" :viewBox "0 0 24 24" :fill "none"
                :stroke "currentColor" :stroke-width "2" :stroke-linecap "round" :stroke-linejoin "round"}
          [:polyline {:points "15 3 21 3 21 9"}]
          [:polyline {:points "9 21 3 21 3 15"}]
          [:line {:x1 "21" :y1 "3" :x2 "14" :y2 "10"}]
          [:line {:x1 "3" :y1 "21" :x2 "10" :y2 "14"}]]]]
       [:div.gallery-card-body
        [:div.gallery-card-title (:title example)]
        [:div.gallery-card-desc (:desc example)]
        (when (seq tags)
          [:div.gallery-card-tags
           (for [tag tags]
             [:span.tag tag])])
        (when src
          [:div
           [:a.view-source {:href "#"
                            :onclick (str "openCodeLightbox('" card-id "'); return false;")}
            "View source"]
           [:pre {:id card-id :style "display:none"} [:code src]]])]])))

(defn generate-gallery-html
  "Generates the gallery page HTML."
  [examples-by-category]
  (let [all-tags (->> examples-by-category
                      (mapcat :examples)
                      (mapcat :tags)
                      (remove nil?)
                      distinct
                      sort
                      vec)]
    (html-page {:title "Gallery" :active-page :gallery :depth 1}
      [:h1.page-title "Gallery"]
      [:p.page-subtitle "Every image on this page was rendered from code."]
      ;; Tag filter bar
      (when (seq all-tags)
        [:div.gallery-filter
         [:span.gallery-filter-label "Filter by feature:"]
         [:button.tag.tag--active {:onclick "filterGallery('all')" :data-tag "all"} "All"]
         (for [tag all-tags]
           [:button.tag {:onclick (str "filterGallery('" tag "')") :data-tag tag} tag])])
      (for [{:keys [category examples]} examples-by-category]
        [:section.gallery-section
         [:h2.gallery-section-title category]
         [:div.gallery-grid
          (for [example examples]
            (gallery-card example))]])
    ;; Image lightbox
    [:div#lightbox {:onclick "closeLightbox()"}
     [:img#lightbox-img]
     [:div#lightbox-caption]]
    ;; Code lightbox
    [:div#code-lightbox {:onclick "closeCodeLightbox()"}
     [:div#code-lightbox-inner {:onclick "event.stopPropagation()"}
      [:div#code-lightbox-header
       [:span#code-lightbox-title]
       [:div {:style "display: flex; gap: 0.75rem; align-items: center;"}
        [:a#copy-btn {:href "#" :onclick "copyCode(); return false;"
                      :title "Copy to clipboard"
                      :style "color: #9090a0; font-size: 0.85rem; text-decoration: none; display: flex; align-items: center; gap: 0.3rem; line-height: 1;"}
         [:svg {:width "14" :height "14" :viewBox "0 0 24 24" :fill "none"
                :stroke "currentColor" :stroke-width "2" :stroke-linecap "round" :stroke-linejoin "round"}
          [:rect {:x "9" :y "9" :width "13" :height "13" :rx "2" :ry "2"}]
          [:path {:d "M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"}]]
         "Copy"]
        [:a {:href "#" :onclick "closeCodeLightbox(); return false;"
             :style "color: #9090a0; font-size: 1.2rem; text-decoration: none; line-height: 1; display: flex; align-items: center;"} "\u00d7"]]]
      [:pre#code-lightbox-pre [:code#code-lightbox-code]]]]
    [:script (h/raw (str highlight-clj-js "
function openLightbox(src, alt) {
  var lb = document.getElementById('lightbox');
  document.getElementById('lightbox-img').src = src;
  document.getElementById('lightbox-caption').textContent = alt;
  lb.classList.add('active');
  document.body.style.overflow = 'hidden';
}
function closeLightbox() {
  document.getElementById('lightbox').classList.remove('active');
  document.body.style.overflow = '';
}
function openCodeLightbox(id) {
  var src = document.getElementById(id);
  var code = src.querySelector('code').textContent;
  var title = src.closest('.gallery-card').querySelector('.gallery-card-title').textContent;
  document.getElementById('code-lightbox-code').innerHTML = highlightClj(code);
  document.getElementById('code-lightbox-code').dataset.raw = code;
  document.getElementById('code-lightbox-title').textContent = title;
  document.getElementById('copy-btn').querySelector('span') || null;
  document.getElementById('code-lightbox').classList.add('active');
  document.body.style.overflow = 'hidden';
}
function copyCode() {
  var code = document.getElementById('code-lightbox-code').dataset.raw;
  navigator.clipboard.writeText(code).then(function() {
    var btn = document.getElementById('copy-btn');
    var orig = btn.lastChild.textContent;
    btn.lastChild.textContent = 'Copied!';
    setTimeout(function() { btn.lastChild.textContent = orig; }, 1500);
  });
}
function closeCodeLightbox() {
  document.getElementById('code-lightbox').classList.remove('active');
  document.body.style.overflow = '';
}
document.addEventListener('keydown', function(e) {
  if (e.key === 'Escape') { closeLightbox(); closeCodeLightbox(); }
});
function filterGallery(tag) {
  // Update active button
  document.querySelectorAll('.gallery-filter .tag').forEach(function(btn) {
    btn.classList.toggle('tag--active', btn.dataset.tag === tag);
  });
  // Show/hide cards
  document.querySelectorAll('.gallery-card').forEach(function(card) {
    var tags = card.dataset.tags || '';
    var show = (tag === 'all') || tags.split(',').indexOf(tag) >= 0;
    card.style.display = show ? '' : 'none';
  });
  // Hide empty sections
  document.querySelectorAll('.gallery-section').forEach(function(sec) {
    var visible = sec.querySelectorAll('.gallery-card:not([style*=\"display: none\"])').length;
    sec.style.display = visible > 0 ? '' : 'none';
  });
}
"))])))

;; --- Guide page ---

(defn generate-docs-html
  "Generates the user guide page HTML."
  []
  (let [categories (pages/docs-categories)]
    (html-page {:title "Guide" :active-page :docs :depth 1}
      [:h1.page-title "Guide"]
      [:p.page-subtitle "A hands-on tour of Eido — from first shapes to generative art."]
      [:div.intent-grid
       (for [{:keys [intent links]} (pages/intent-cards)]
         [:div.intent-card
          [:div.intent-label intent]
          [:div.intent-links
           (for [{:keys [label href]} links]
             [:a {:href href} label])]])]
      [:div.docs-layout
       [:nav.docs-sidebar
        (for [{:keys [category id sections]} categories]
          [:div.docs-sidebar-category
           [:div.docs-sidebar-category-title
            [:a {:href (str "#" id)} category]]
           [:ul
            (for [{sec-id :id sec-title :title} sections]
              [:li [:a {:href (str "#" sec-id)} sec-title]])]])]
       [:div.docs-content
        (for [{:keys [category id sections intro]} categories]
          [:div.docs-category {:id id}
           [:h2.docs-category-title category]
           (when intro
             [:div.docs-category-intro (insert-doc-previews intro)])
           (for [{sec-id :id sec-title :title content :content} sections]
             [:section.docs-section {:id sec-id}
              [:h3 sec-title]
              (insert-doc-previews content)])])]
       [:script (h/raw (str highlight-clj-js "
document.querySelectorAll('pre code').forEach(function(el) {
  el.innerHTML = highlightClj(el.textContent);
});
"))]])))

;; --- API page ---

(defn api-var-info
  "Extracts API info from a var."
  [v]
  (let [m (meta v)]
    {:name          (str (:name m))
     :arglists      (:arglists m)
     :doc           (:doc m)
     :added         (:added m)
     :convenience?  (:convenience m)
     :wraps         (when-let [s (:convenience-for m)]
                      (name s))
     :stability     (or (:stability m)
                        (:stability (meta (:ns m))))}))

(defn generate-api-html
  "Generates the API reference page from eido namespace metadata."
  []
  (doseq [ns-sym eido-namespaces]
    (require ns-sym))
  (let [ns-data (->> eido-namespaces
                     (mapv (fn [ns-sym]
                             (let [ns-obj  (find-ns ns-sym)
                                   publics (->> (ns-publics ns-obj)
                                                vals
                                                (remove #(:private (meta %)))
                                                (sort-by #(str (:name (meta %))))
                                                (mapv api-var-info))]
                               {:ns-sym  ns-sym
                                :ns-name (str ns-sym)
                                :ns-doc  (:doc (meta ns-obj))
                                :vars    publics})))
                     (remove #(empty? (:vars %))))
        ns-by-name (into {} (map (juxt :ns-name identity) ns-data))]
    (html-page {:title "API Reference" :active-page :api :depth 1}
      [:h1.page-title "API Reference"]
      [:p.page-subtitle "Auto-generated from source metadata. Functions marked "
       [:span.api-var-badge.api-var-badge--provisional "Provisional"]
       " may have API changes in future releases."]
      [:div.api-search
       [:input#api-search {:type "text"
                           :placeholder "Search functions, namespaces, or keywords..."
                           :autocomplete "off"
                           :oninput "filterAPI(this.value)"}]]
      [:div.api-layout
       [:nav.api-sidebar
        (for [{:keys [category namespaces]} api-namespace-groups]
          (let [group-ns (keep #(ns-by-name (str %)) namespaces)]
            (when (seq group-ns)
              [:div.api-sidebar-category
               [:div.api-sidebar-category-title category]
               [:ul
                (for [{:keys [ns-name]} group-ns]
                  [:li [:a {:href (str "#" ns-name)} ns-name]])]])))]
       [:div
        (for [{:keys [namespaces]} api-namespace-groups
              :let [group-ns (keep #(ns-by-name (str %)) namespaces)]
              :when (seq group-ns)
              {:keys [ns-name ns-doc vars]} group-ns]
          [:section.api-ns {:id ns-name}
           [:h2.api-ns-title ns-name]
           (when ns-doc
             [:p.api-ns-doc ns-doc])
           (for [{:keys [name arglists doc convenience? wraps stability]} vars]
             [:div.api-var
              ;; Badges in top-right corner
              (when (or convenience? (= :provisional stability))
                [:div.api-var-meta
                 (when convenience?
                   [:span.api-var-badge "Helper"])
                 (when (= :provisional stability)
                   [:span.api-var-badge.api-var-badge--provisional
                    "Provisional"])
                 (when wraps
                   [:div.api-var-wraps "Wraps " [:code wraps]])])
              ;; Signature block — one line per arity
              [:div.api-var-sig
               (if (seq arglists)
                 (for [arglist arglists]
                   [:div.api-var-arity
                    [:code "(" [:span.api-var-name name]
                     (when (seq arglist)
                       [:span.api-var-args
                        " " (str/join " " (map str arglist))])
                     ")"]])
                 [:div.api-var-arity
                  [:code [:span.api-var-name name]]])]
              ;; Docstring with code formatting
              (when doc
                [:div.api-var-doc
                 (h/raw
                   (-> (str/replace doc #"`([^`]+)`" "<code>$1</code>")
                       (str/replace #":[\w/\-\.]+" "<code>$0</code>")
                       (str/replace #"\n" "<br>")))])])])]]
      [:script (h/raw (str highlight-clj-js "
document.querySelectorAll('.api-var-sig code').forEach(function(el) {
  el.innerHTML = highlightClj(el.textContent);
});

function filterAPI(query) {
  var q = query.toLowerCase().trim();
  document.querySelectorAll('.api-var').forEach(function(card) {
    if (!q) { card.style.display = ''; return; }
    var text = card.textContent.toLowerCase();
    card.style.display = text.indexOf(q) >= 0 ? '' : 'none';
  });
  document.querySelectorAll('.api-ns').forEach(function(sec) {
    if (!q) { sec.style.display = ''; return; }
    var visible = sec.querySelectorAll('.api-var:not([style*=\"display: none\"])').length;
    var nsText = sec.querySelector('.api-ns-title').textContent.toLowerCase();
    sec.style.display = (visible > 0 || nsText.indexOf(q) >= 0) ? '' : 'none';
  });
}
"))])))


;; --- Architecture page ---

(defn generate-architecture-html
  "Generates the 'How Eido Works' architecture page."
  []
  (let [sections (pages/architecture-sections)]
    (html-page {:title "How Eido Works" :active-page :architecture :depth 1}
      [:h1.page-title "How Eido Works"]
      [:p.page-subtitle "From data to pixels — a tour of the rendering pipeline"]
      [:div.arch-layout
       [:nav.arch-sidebar
        (for [{:keys [id title]} sections]
          [:div [:a {:href (str "#" id)} title]])]
       [:div.arch-content
        (for [{:keys [id title content]} sections]
          [:section.arch-section {:id id}
           [:h2 title]
           (insert-doc-previews content)])]]
      [:script (h/raw (str highlight-clj-js "
document.querySelectorAll('.arch-content pre code').forEach(function(el) {
  el.innerHTML = highlightClj(el.textContent);
});
"))])))

;; --- Scope & Limitations page ---

(defn generate-limitations-html
  "Generates the 'Scope & Limitations' page."
  []
  (let [sections (pages/limitations-sections)]
    (html-page {:title "Scope & Limitations" :active-page :limitations :depth 1}
      [:h1.page-title "Scope & Limitations"]
      [:p.page-subtitle "What Eido does, what it doesn't, and why"]
      [:div.arch-layout
       [:nav.arch-sidebar
        (for [{:keys [id title]} sections]
          [:div [:a {:href (str "#" id)} title]])]
       [:div.arch-content
        (for [{:keys [id title content]} sections]
          [:section.arch-section {:id id}
           [:h2 title]
           content])]])))

;; --- Workflow pages ---

(defn generate-workflows-index-html
  "Generates the workflows landing page with cards for each workflow."
  []
  (html-page {:title "Workflows" :active-page :workflows :depth 1}
    [:h1.page-title "Workflows"]
    [:p.page-subtitle "End-to-end guides for common generative art workflows"]
    [:div.workflow-grid
     (for [{:keys [slug title desc]} pages/workflow-pages]
       [:a.workflow-card {:href (str slug "/")}
        [:div.workflow-card-title title]
        [:div.workflow-card-desc desc]])]))

(defn generate-workflow-page-html
  "Generates a single workflow page with sidebar navigation."
  [{:keys [title sections-fn]}]
  (let [sections (sections-fn)]
    (html-page {:title title :active-page :workflows :depth 2}
      [:h1.page-title title]
      [:div.arch-layout
       [:nav.arch-sidebar
        (for [{:keys [id title]} sections]
          [:div [:a {:href (str "#" id)} title]])]
       [:div.arch-content
        (for [{:keys [id title content]} sections]
          [:section.arch-section {:id id}
           [:h2 title]
           (insert-doc-previews content "../../images/")])]]
      [:script (h/raw (str highlight-clj-js "
document.querySelectorAll('.arch-content pre code').forEach(function(el) {
  el.innerHTML = highlightClj(el.textContent);
});
"))])))

;; --- Site builder ---

(defn write-page! [out-dir path html]
  (let [file (io/file out-dir path)]
    (io/make-parents file)
    (spit file html)))

(defn build-site!
  "Builds the complete eido website into the output directory.
  Run via: clj -X:gallery"
  [& {:keys [out-dir] :or {out-dir "_site"}}]
  (println "Building eido site into" out-dir "...")

  ;; Render all example images
  (println "Rendering examples...")
  (render-all-examples! out-dir)

  ;; Render docs example previews
  (println "Rendering docs examples...")
  (render-docs-examples! out-dir)

  ;; Discover examples for gallery
  (let [examples-by-category (all-examples)]

    ;; Generate pages
    (println "Generating landing page...")
    (write-page! out-dir "index.html"
      (generate-landing-html examples-by-category))

    (println "Generating gallery...")
    (write-page! out-dir "gallery/index.html"
      (generate-gallery-html examples-by-category))

    (println "Generating guide...")
    (write-page! out-dir "guide/index.html"
      (generate-docs-html))

    (println "Generating API reference...")
    (write-page! out-dir "api/index.html"
      (generate-api-html))

    (println "Generating architecture page...")
    (write-page! out-dir "architecture/index.html"
      (generate-architecture-html))

    (println "Generating limitations page...")
    (write-page! out-dir "limitations/index.html"
      (generate-limitations-html))

    (println "Generating workflows...")
    (write-page! out-dir "workflows/index.html"
      (generate-workflows-index-html))
    (doseq [{:keys [slug] :as page} pages/workflow-pages]
      (println "  Generating workflow:" slug "...")
      (write-page! out-dir (str "workflows/" slug "/index.html")
        (generate-workflow-page-html page)))

    ;; CNAME file for custom domain
    (spit (io/file out-dir "CNAME") "eido.leifericf.com")

    (println "Site built successfully!")
    (println (str "  " (count (mapcat :examples examples-by-category)) " examples rendered"))
    (println (str "  Open " out-dir "/index.html to preview"))))
