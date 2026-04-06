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
             :close    "Z"))
         commands)))

(defn- style-attrs
  "Builds SVG style attribute string for an op."
  [{:keys [fill stroke-color stroke-width opacity transforms]}]
  (str (if fill
         (str "fill=\"" (color->css fill) "\"")
         "fill=\"none\"")
       (when stroke-color
         (str " stroke=\"" (color->css stroke-color) "\""
              " stroke-width=\"" stroke-width "\""))
       (when (and opacity (not= opacity 1.0))
         (str " opacity=\"" opacity "\""))
       (when-let [t (transforms->svg transforms)]
         (str " transform=\"" t "\""))))

(defmulti op->svg
  "Converts a single IR op to an SVG element string."
  :op)

(defmethod op->svg :rect
  [{:keys [x y w h] :as op}]
  (str "<rect x=\"" x "\" y=\"" y
       "\" width=\"" w "\" height=\"" h
       "\" " (style-attrs op) "/>"))

(defmethod op->svg :circle
  [{:keys [cx cy r] :as op}]
  (str "<circle cx=\"" cx "\" cy=\"" cy
       "\" r=\"" r
       "\" " (style-attrs op) "/>"))

(defmethod op->svg :path
  [{:keys [commands] :as op}]
  (str "<path d=\"" (commands->d commands)
       "\" " (style-attrs op) "/>"))

(defn render
  "Renders IR to an SVG XML string."
  ([ir] (render ir {}))
  ([ir opts]
   (let [[w h]  (:ir/size ir)
         scale  (get opts :scale 1)
         sw     (int (* w scale))
         sh     (int (* h scale))
         bg     (:ir/background ir)
         lines  (cond-> [(str "<svg xmlns=\"http://www.w3.org/2000/svg\""
                              " width=\"" sw "\" height=\"" sh "\""
                              " viewBox=\"0 0 " w " " h "\">")]
                  (and bg (not (:transparent-background opts)))
                  (conj (str "  <rect x=\"0\" y=\"0\" width=\"" w
                             "\" height=\"" h "\" fill=\"" (color->css bg) "\"/>"))
                  true
                  (into (map #(str "  " (op->svg %)) (:ir/ops ir)))
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
  [ir i n fps transparent-bg?]
  (let [bg         (:ir/background ir)
        [w h]      (:ir/size ir)
        frame-dur  (/ 1.0 fps)
        total-dur  (fmt (* n frame-dur))
        {:keys [values key-times]} (frame-key-times i n)
        animate    (str "    <animate attributeName=\"visibility\""
                        " values=\"" values "\""
                        " dur=\"" total-dur "s\""
                        " repeatCount=\"indefinite\""
                        " keyTimes=\"" key-times "\""
                        " calcMode=\"discrete\"/>")
        bg-line    (when (and bg (not transparent-bg?))
                     (str "    <rect x=\"0\" y=\"0\" width=\"" w
                          "\" height=\"" h "\" fill=\"" (color->css bg) "\"/>"))
        op-lines   (map #(str "    " (op->svg %)) (:ir/ops ir))]
    (str/join "\n"
      (filter some?
        (concat
          [(str "  <g visibility=\"hidden\">")
           animate
           bg-line]
          op-lines
          ["  </g>"])))))

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
         frames  (map-indexed (fn [i ir] (frame-group ir i n fps t-bg?)) irs)]
     (str/join "\n"
       (concat [header] frames ["</svg>"])))))

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
