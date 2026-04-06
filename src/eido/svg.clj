(ns eido.svg
  (:require
    [clojure.string :as str]))

(defn- fmt
  "Formats a number, stripping unnecessary trailing zeros."
  [n]
  (let [s (format "%.4f" (double n))]
    (-> s
        (str/replace #"0+$" "")
        (str/replace #"\.$" ""))))

(defn- color->css
  "Converts a resolved color map to CSS rgb()/rgba() string."
  [{:keys [r g b a]}]
  (if (and a (not= a 1.0))
    (str "rgba(" r "," g "," b "," (fmt a) ")")
    (str "rgb(" r "," g "," b ")")))

(defn- transforms->svg
  "Converts IR transforms to SVG transform attribute string.
  IR rotate is radians; SVG rotate is degrees."
  [transforms]
  (when (seq transforms)
    (str/join " "
      (map (fn [[t & args]]
             (case t
               :translate (str "translate(" (first args) "," (second args) ")")
               :rotate    (str "rotate(" (fmt (* (first args) (/ 180.0 Math/PI))) ")")
               :shear-x   (str "skewX(" (first args) ")")
               :shear-y   (str "skewY(" (first args) ")")
               :scale     (str "scale(" (first args) "," (second args) ")")))
           transforms))))

(defn- commands->d
  "Converts IR path commands to SVG path d attribute string."
  [commands]
  (str/join " "
    (map (fn [[cmd & args]]
           (case cmd
             :move-to  (str "M " (first args) " " (second args))
             :line-to  (str "L " (first args) " " (second args))
             :curve-to (str "C " (str/join " " args))
             :quad-to  (str "Q " (str/join " " args))
             :close    "Z"))
         commands)))

(defn- gradient->svg-def
  "Converts a resolved gradient fill to an SVG gradient definition string.
  Returns {:id id :def svg-def-string}."
  [gradient id]
  (let [stops-str (str/join ""
                    (map (fn [[pos color]]
                           (str "<stop offset=\"" (fmt pos)
                                "\" stop-color=\"" (color->css color) "\""
                                (when (and (:a color) (not= (:a color) 1.0))
                                  (str " stop-opacity=\"" (fmt (:a color)) "\""))
                                "/>"))
                         (:gradient/stops gradient)))]
    {:id  id
     :def (case (:gradient/type gradient)
            :linear (let [[x1 y1] (:gradient/from gradient)
                          [x2 y2] (:gradient/to gradient)]
                      (str "<linearGradient id=\"" id "\""
                           " x1=\"" (fmt x1) "\" y1=\"" (fmt y1)
                           "\" x2=\"" (fmt x2) "\" y2=\"" (fmt y2)
                           "\" gradientUnits=\"userSpaceOnUse\">"
                           stops-str "</linearGradient>"))
            :radial (let [[cx cy] (:gradient/center gradient)
                          r       (:gradient/radius gradient)]
                      (str "<radialGradient id=\"" id "\""
                           " cx=\"" (fmt cx) "\" cy=\"" (fmt cy)
                           "\" r=\"" (fmt r)
                           "\" gradientUnits=\"userSpaceOnUse\">"
                           stops-str "</radialGradient>")))}))

(defn- style-attrs
  "Builds SVG style attribute string for an op.
  gradient-id, when provided, is used for fill instead of a solid color."
  ([op] (style-attrs op nil))
  ([{:keys [fill stroke-color stroke-width stroke-cap stroke-join
            stroke-dash opacity transforms]} gradient-id]
   (str (cond
          gradient-id    (str "fill=\"url(#" gradient-id ")\"")
          fill           (str "fill=\"" (color->css fill) "\"")
          :else          "fill=\"none\"")
        (when stroke-color
          (str " stroke=\"" (color->css stroke-color) "\""
               " stroke-width=\"" stroke-width "\""))
        (when stroke-cap
          (str " stroke-linecap=\"" (name stroke-cap) "\""))
        (when stroke-join
          (str " stroke-linejoin=\"" (name stroke-join) "\""))
        (when stroke-dash
          (str " stroke-dasharray=\"" (str/join " " stroke-dash) "\""))
        (when (and opacity (not= opacity 1.0))
          (str " opacity=\"" opacity "\""))
        (when-let [t (transforms->svg transforms)]
          (str " transform=\"" t "\"")))))

(defmulti op->svg
  "Converts a single IR op to an SVG element string.
  Dispatches on :op. Optional second arg is gradient-id for fill."
  (fn [op & _] (:op op)))

(defmethod op->svg :rect
  [{:keys [x y w h corner-radius] :as op} & [gradient-id]]
  (str "<rect x=\"" x "\" y=\"" y
       "\" width=\"" w "\" height=\"" h "\""
       (when corner-radius
         (str " rx=\"" corner-radius "\" ry=\"" corner-radius "\""))
       " " (style-attrs op gradient-id) "/>"))

(defmethod op->svg :circle
  [{:keys [cx cy r] :as op} & [gradient-id]]
  (str "<circle cx=\"" cx "\" cy=\"" cy
       "\" r=\"" r
       "\" " (style-attrs op gradient-id) "/>"))

(defn- arc->path-d
  "Converts arc parameters to SVG path d string.
  start/extent in degrees, mode is :open/:chord/:pie."
  [cx cy rx ry start extent mode]
  (let [start-rad (* start (/ Math/PI 180.0))
        end-rad   (* (+ start extent) (/ Math/PI 180.0))
        x1 (+ cx (* rx (Math/cos start-rad)))
        y1 (- cy (* ry (Math/sin start-rad)))
        x2 (+ cx (* rx (Math/cos end-rad)))
        y2 (- cy (* ry (Math/sin end-rad)))
        large-arc (if (> (Math/abs extent) 180) 1 0)
        sweep     (if (pos? extent) 0 1)]
    (case mode
      :pie   (str "M " (fmt cx) " " (fmt cy)
                  " L " (fmt x1) " " (fmt y1)
                  " A " rx " " ry " 0 " large-arc " " sweep
                  " " (fmt x2) " " (fmt y2) " Z")
      :chord (str "M " (fmt x1) " " (fmt y1)
                  " A " rx " " ry " 0 " large-arc " " sweep
                  " " (fmt x2) " " (fmt y2) " Z")
      ;; :open
      (str "M " (fmt x1) " " (fmt y1)
           " A " rx " " ry " 0 " large-arc " " sweep
           " " (fmt x2) " " (fmt y2)))))

(defmethod op->svg :arc
  [{:keys [cx cy rx ry start extent mode] :as op} & [gradient-id]]
  (str "<path d=\"" (arc->path-d cx cy rx ry start extent mode)
       "\" " (style-attrs op gradient-id) "/>"))

(defmethod op->svg :line
  [{:keys [x1 y1 x2 y2] :as op} & [gradient-id]]
  (str "<line x1=\"" x1 "\" y1=\"" y1
       "\" x2=\"" x2 "\" y2=\"" y2
       "\" " (style-attrs op gradient-id) "/>"))

(defmethod op->svg :ellipse
  [{:keys [cx cy rx ry] :as op} & [gradient-id]]
  (str "<ellipse cx=\"" cx "\" cy=\"" cy
       "\" rx=\"" rx "\" ry=\"" ry
       "\" " (style-attrs op gradient-id) "/>"))

(defmethod op->svg :path
  [{:keys [commands fill-rule] :as op} & [gradient-id]]
  (str "<path d=\"" (commands->d commands) "\""
       (when fill-rule
         (str " fill-rule=\"" (case fill-rule
                                :even-odd "evenodd"
                                :non-zero "nonzero") "\""))
       " " (style-attrs op gradient-id) "/>"))

(defn- clip-shape->svg
  "Converts a clip IR op to an SVG shape element string (no style)."
  [{:keys [op] :as clip}]
  (case op
    :rect    (let [{:keys [x y w h corner-radius]} clip]
               (str "<rect x=\"" x "\" y=\"" y
                    "\" width=\"" w "\" height=\"" h "\""
                    (when corner-radius
                      (str " rx=\"" corner-radius "\" ry=\"" corner-radius "\""))
                    "/>"))
    :circle  (let [{:keys [cx cy r]} clip]
               (str "<circle cx=\"" cx "\" cy=\"" cy "\" r=\"" r "\"/>"))
    :ellipse (let [{:keys [cx cy rx ry]} clip]
               (str "<ellipse cx=\"" cx "\" cy=\"" cy
                    "\" rx=\"" rx "\" ry=\"" ry "\"/>"))
    :path    (str "<path d=\"" (commands->d (:commands clip)) "\"/>")))

(defn- op-svg-with-clip
  "Renders an op to SVG, adding clip-path and/or gradient defs as needed."
  [op idx]
  (let [fill         (:fill op)
        has-gradient? (and (map? fill) (:gradient/type fill))
        gradient-id  (when has-gradient? (str "grad-" idx))
        gradient-def (when has-gradient?
                       (:def (gradient->svg-def fill gradient-id)))
        has-clip?    (:clip op)
        clip-id      (when has-clip? (str "clip-" idx))
        clip-def     (when has-clip?
                       (str "<clipPath id=\"" clip-id "\">"
                            (clip-shape->svg (:clip op)) "</clipPath>"))
        base         (op->svg op gradient-id)
        element      (if has-clip?
                       (str/replace base #"/>"
                         (str " clip-path=\"url(#" clip-id ")\"/>"))
                       base)
        defs         (str/join "" (remove nil? [gradient-def clip-def]))]
    {:defs    (when (seq defs) defs)
     :element element}))

(defn render
  "Renders IR to an SVG XML string."
  ([ir] (render ir {}))
  ([ir opts]
   (let [[w h]  (:ir/size ir)
         scale  (get opts :scale 1)
         sw     (int (* w scale))
         sh     (int (* h scale))
         bg     (:ir/background ir)
         ops-with-clips (map-indexed (fn [i op] (op-svg-with-clip op i))
                                     (:ir/ops ir))
         defs   (keep :defs ops-with-clips)
         lines  (cond-> [(str "<svg xmlns=\"http://www.w3.org/2000/svg\""
                              " width=\"" sw "\" height=\"" sh "\""
                              " viewBox=\"0 0 " w " " h "\">")]
                  (seq defs)
                  (conj (str "  <defs>" (str/join "" defs) "</defs>"))
                  (and bg (not (:transparent-background opts)))
                  (conj (str "  <rect x=\"0\" y=\"0\" width=\"" w
                             "\" height=\"" h "\" fill=\"" (color->css bg) "\"/>"))
                  true
                  (into (map #(str "  " (:element %)) ops-with-clips))
                  true
                  (conj "</svg>"))]
     (str/join "\n" lines))))

(defn- frame-key-times
  "Builds SMIL keyTimes and values for frame i of n.
  Frame i is visible during [i/n, (i+1)/n), hidden otherwise."
  [i n]
  (let [t-start (/ (double i) n)
        t-end   (/ (double (inc i)) n)]
    (cond
      ;; Single frame: always visible
      (= n 1)
      {:values "visible" :key-times "0"}

      ;; First frame: visible then hidden
      (zero? i)
      {:values   "visible;hidden"
       :key-times (str "0;" (fmt t-end))}

      ;; Last frame: hidden then visible
      (= i (dec n))
      {:values   "hidden;visible"
       :key-times (str "0;" (fmt t-start))}

      ;; Middle frame: hidden, visible, hidden
      :else
      {:values   "hidden;visible;hidden"
       :key-times (str "0;" (fmt t-start) ";" (fmt t-end))})))

(defn- frame-group
  "Wraps IR ops in a <g> with SMIL visibility animation."
  [ir frame-idx n fps transparent-bg? op-offset]
  (let [bg         (:ir/background ir)
        [w h]      (:ir/size ir)
        frame-dur  (/ 1.0 fps)
        total-dur  (fmt (* n frame-dur))
        {:keys [values key-times]} (frame-key-times frame-idx n)
        animate    (str "    <animate attributeName=\"visibility\""
                        " values=\"" values "\""
                        " dur=\"" total-dur "s\""
                        " repeatCount=\"indefinite\""
                        " keyTimes=\"" key-times "\""
                        " calcMode=\"discrete\"/>")
        bg-line    (when (and bg (not transparent-bg?))
                     (str "    <rect x=\"0\" y=\"0\" width=\"" w
                          "\" height=\"" h "\" fill=\"" (color->css bg) "\"/>"))
        ops-with-meta (map-indexed
                        (fn [j op] (op-svg-with-clip op (+ op-offset j)))
                        (:ir/ops ir))
        defs-str   (str/join "" (keep :defs ops-with-meta))
        op-lines   (map #(str "    " (:element %)) ops-with-meta)]
    {:defs defs-str
     :body (str/join "\n"
             (filter some?
               (concat
                 [(str "  <g visibility=\"hidden\">")
                  animate
                  bg-line]
                 op-lines
                 ["  </g>"])))}))

(defn render-animated
  "Renders a sequence of IRs to an animated SVG string using SMIL.
  fps: frames per second.
  Opts: :scale, :transparent-background."
  ([irs fps] (render-animated irs fps {}))
  ([irs fps opts]
   (let [[w h]   (:ir/size (first irs))
         scale   (get opts :scale 1)
         sw      (int (* w scale))
         sh      (int (* h scale))
         n       (count irs)
         t-bg?   (:transparent-background opts)
         header  (str "<svg xmlns=\"http://www.w3.org/2000/svg\""
                      " width=\"" sw "\" height=\"" sh "\""
                      " viewBox=\"0 0 " w " " h "\">")
         ;; Accumulate op-offset so gradient/clip IDs are unique across frames
         frame-results (loop [i 0, offset 0, results []]
                         (if (>= i n)
                           results
                           (let [ir     (nth irs i)
                                 result (frame-group ir i n fps t-bg? offset)]
                             (recur (inc i)
                                    (+ offset (count (:ir/ops ir)))
                                    (conj results result)))))
         all-defs (str/join "" (keep #(when (seq (:defs %)) (:defs %)) frame-results))
         lines    (cond-> [header]
                    (seq all-defs)
                    (conj (str "  <defs>" all-defs "</defs>"))
                    true
                    (into (map :body frame-results))
                    true
                    (conj "</svg>"))]
     (str/join "\n" lines))))

(comment
  ;; Static SVG
  (render {:ir/size [200 200]
           :ir/background {:r 255 :g 255 :b 255 :a 1.0}
           :ir/ops [{:op :circle :cx 100 :cy 100 :r 50
                     :fill {:r 200 :g 0 :b 0 :a 1.0}
                     :stroke-color nil :stroke-width nil
                     :opacity 1.0 :transforms []}]})

  ;; Animated SVG
  (render-animated
    [{:ir/size [100 100]
      :ir/background {:r 0 :g 0 :b 0 :a 1.0}
      :ir/ops [{:op :circle :cx 50 :cy 50 :r 20
                :fill {:r 255 :g 0 :b 0 :a 1.0}
                :stroke-color nil :stroke-width nil
                :opacity 1.0 :transforms []}]}
     {:ir/size [100 100]
      :ir/background {:r 0 :g 0 :b 0 :a 1.0}
      :ir/ops [{:op :circle :cx 50 :cy 50 :r 40
                :fill {:r 0 :g 255 :b 0 :a 1.0}
                :stroke-color nil :stroke-width nil
                :opacity 1.0 :transforms []}]}]
    10)
  )
