(ns ^{:stability :provisional} eido.io.dxf
  "DXF R12 ASCII writer for polyline/plotter-style export.

  Emits compiled IR as layered 2D geometry suitable for CAD tools
  (LibreCAD, QCAD, AutoCAD) and downstream fabrication (laser
  cutters, CNC routers, vinyl cutters, plasma tables) via DXF
  interchange.

  Each stroke-color group becomes a DXF LAYER. Colors are mapped to
  AutoCAD Color Index (ACI) via nearest-neighbor against a small
  fixed palette — R12 does not support truecolor group codes. For
  exact color preservation use pure red/yellow/green/cyan/blue/
  magenta/white/dark-gray/light-gray in your scenes.

  Coordinate units are emitted as millimeters ($INSUNITS = 4).
  Eido's IR is in scene units; pass :scale to convert if needed.

  See also eido.io.polyline/extract-grouped-polylines, which
  produces the substrate this writer consumes."
  (:require
    [clojure.string :as str]
    [eido.io.polyline :as polyline]))

;; --- ACI palette (fixed entries 1-9) ---

(def ^:private aci-palette
  "DXF AutoCAD Color Index entries 1-9 mapped to [r g b].
  Indices 10-255 follow a systematic HSV pattern in the full AutoCAD
  palette; this MVP keeps only the named colors and nearest-matches
  against them. Expand later if users need finer matching."
  {1 [255 0   0]      ; red
   2 [255 255 0]      ; yellow
   3 [0   255 0]      ; green
   4 [0   255 255]    ; cyan
   5 [0   0   255]    ; blue
   6 [255 0   255]    ; magenta
   7 [255 255 255]    ; white (or black on light backgrounds)
   8 [65  65  65]     ; dark gray
   9 [128 128 128]})  ; medium gray

(defn rgb->aci
  "Maps an RGB triple [r g b] (0-255) to the nearest ACI index.
  Uses squared-Euclidean distance against the fixed palette."
  [[r g b]]
  (let [d2 (fn [[pr pg pb]]
             (let [dr (- r pr) dg (- g pg) db (- b pb)]
               (+ (* dr dr) (* dg dg) (* db db))))]
    (->> aci-palette
         (sort-by (fn [[_ rgb]] (d2 rgb)))
         ffirst)))

;; --- layer naming ---

(defn- color->layer-name
  "Converts a stroke color map to a DXF layer name.
  Opaque strokes → 'pen-R-G-B'; semi-transparent → 'pen-R-G-B-aNN'
  (alpha as 0-99 percent); nil → 'pen-none'.

  The alpha suffix is required for uniqueness: DXF R12 does not
  support per-layer transparency, so same-RGB strokes with
  different alpha values need distinct layer names to avoid
  colliding table entries."
  [stroke]
  (if stroke
    (let [a (double (:a stroke 1.0))]
      (if (< a 1.0)
        (format "pen-%d-%d-%d-a%d"
                (:r stroke) (:g stroke) (:b stroke)
                (int (Math/round (* 100.0 a))))
        (format "pen-%d-%d-%d" (:r stroke) (:g stroke) (:b stroke))))
    "pen-none"))

(defn- stroke->aci
  "Returns the ACI index for a stroke color map, or 7 (white) for nil."
  [stroke]
  (if stroke
    (rgb->aci [(:r stroke) (:g stroke) (:b stroke)])
    7))

;; --- DXF line emission ---
;;
;; DXF ASCII alternates group-code lines and value lines. We build
;; flat vectors of strings and str/join them at the end. Every line
;; corresponds to either a code number or its value.

(defn- header-lines []
  ["0" "SECTION"
   "2" "HEADER"
   "9" "$ACADVER" "1" "AC1009"
   "9" "$INSUNITS" "70" "4"
   "0" "ENDSEC"])

(defn- layer-entry-lines
  [{:keys [name aci]}]
  ["0" "LAYER"
   "2" name
   "70" "0"
   "62" (str aci)
   "6" "CONTINUOUS"])

(defn- tables-lines
  [layer-entries]
  (into ["0" "SECTION"
         "2" "TABLES"
         "0" "TABLE"
         "2" "LAYER"
         "70" (str (count layer-entries))]
        (concat
          (mapcat layer-entry-lines layer-entries)
          ["0" "ENDTAB"
           "0" "ENDSEC"])))

(defn- fmt-coord ^String [scale x]
  (format "%.6f" (double (* (double scale) (double x)))))

(defn- vertex-lines
  [layer scale [x y]]
  ["0" "VERTEX"
   "8" layer
   "10" (fmt-coord scale x)
   "20" (fmt-coord scale y)])

(defn- polyline-lines
  [layer aci poly scale]
  (let [closed? (and (>= (count poly) 3)
                     (= (first poly) (last poly)))
        verts   (if closed? (butlast poly) poly)]
    (into ["0" "POLYLINE"
           "8" layer
           "66" "1"
           "70" (if closed? "1" "0")
           "62" (str aci)]
          (concat
            (mapcat #(vertex-lines layer scale %) verts)
            ["0" "SEQEND"
             "8" layer]))))

(defn- entities-lines
  [groups layer-entries scale]
  (into ["0" "SECTION"
         "2" "ENTITIES"]
        (concat
          (mapcat (fn [{:keys [polylines]} {:keys [name aci]}]
                    (mapcat #(polyline-lines name aci % scale) polylines))
                  groups
                  layer-entries)
          ["0" "ENDSEC"])))

;; --- public API ---

(defn write-dxf
  "Writes a compiled IR's stroke geometry to a DXF R12 ASCII string.

  Returns a string; callers are responsible for writing it to disk
  (e.g. via `spit`). Each unique stroke color becomes a DXF LAYER;
  ops without a stroke go onto a 'pen-none' layer.

  Options:
    :flatness        — curve subdivision tolerance (default 0.5)
    :segments        — circle/ellipse/arc segments (default 64)
    :optimize-travel — reorder polylines within each layer to
                       minimize pen travel (default true)
    :scale           — coordinate multiplier applied to every
                       x/y (default 1.0). Useful when scene units
                       differ from millimeters."
  ([ir] (write-dxf ir {}))
  ([ir opts]
   (let [flatness  (get opts :flatness 0.5)
         segments  (get opts :segments 64)
         optimize? (get opts :optimize-travel true)
         scale     (get opts :scale 1.0)
         grouped   (polyline/extract-grouped-polylines
                     ir {:flatness flatness :segments segments})
         groups    (if optimize?
                     (mapv #(update % :polylines
                                    polyline/optimize-travel-polylines)
                           (:groups grouped))
                     (:groups grouped))
         entries   (mapv (fn [{:keys [stroke]}]
                           {:name (color->layer-name stroke)
                            :aci  (stroke->aci stroke)})
                         groups)]
     (str (str/join "\n"
            (concat (header-lines)
                    (tables-lines entries)
                    (entities-lines groups entries scale)
                    ["0" "EOF"]))
          "\n"))))

(comment
  (require '[eido.engine.compile :as c])
  (def ir (c/compile
            {:image/size       [200 200]
             :image/background [:color/rgb 255 255 255]
             :image/nodes
             [{:node/type :shape/rect
               :rect/xy [20 20] :rect/size [60 60]
               :style/stroke {:color [:color/rgb 255 0 0] :width 1}}
              {:node/type :shape/circle
               :circle/center [140 140] :circle/radius 30
               :style/stroke {:color [:color/rgb 0 0 255] :width 1}}]}))
  (spit "/tmp/eido-smoke.dxf" (write-dxf ir {}))
  ;; Open /tmp/eido-smoke.dxf in LibreCAD to verify.
  )
