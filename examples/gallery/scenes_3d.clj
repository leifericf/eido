(ns gallery.scenes-3d
  "3D scenes gallery — showcasing eido's 3D projection and mesh pipeline."
  {:category "3D Scenes"}
  (:require
    [eido.animate :as anim]
    [eido.ir.material :as material]
    [eido.obj :as obj]
    [eido.scene3d :as s3d]))

;; --- 1. Utah Teapot (static) ---

(defn ^{:example {:output "3d-teapot.png"
                  :title  "Utah Teapot"
                  :desc   "Classic computer graphics test model loaded from OBJ, rendered with isometric projection."}}
  utah-teapot []
  (let [teapot (-> (obj/parse-obj (slurp "resources/teapot.obj") {})
                   (s3d/translate-mesh [-1.085 0.0 -7.875])
                   (s3d/scale-mesh 0.1)
                   (s3d/rotate-mesh :x (- (/ Math/PI 2))))]
    {:image/size [400 400]
     :image/background [:color/rgb 245 243 238]
     :image/nodes
     [(s3d/render-mesh
        (s3d/isometric {:scale 90 :origin [200 210]})
        (s3d/rotate-mesh teapot :y 0.8)
        {:style {:style/fill [:color/rgb 175 185 195]
                 :style/stroke {:color [:color/rgb 135 145 155] :width 0.15}}
         :light {:light/direction [1 2 1]
                 :light/ambient 0.3
                 :light/intensity 0.7}
         :cull-back false})]}))

;; --- 2. Utah Teapot (orbiting) ---

(defn ^{:example {:output "3d-teapot-spin.gif"
                  :title  "Utah Teapot Spin"
                  :desc   "Orbiting camera animation around the Utah teapot."}}
  utah-teapot-spin []
  (let [teapot (-> (obj/parse-obj (slurp "resources/teapot.obj") {})
                   (s3d/translate-mesh [-1.085 0.0 -7.875])
                   (s3d/scale-mesh 0.1)
                   (s3d/rotate-mesh :x (- (/ Math/PI 2))))]
    {:frames (anim/frames 90
               (fn [t]
                 (let [proj (s3d/orbit (s3d/orthographic {:scale 90 :origin [200 190]})
                                       (s3d/mesh-center teapot) 4
                                       (* t 2.0 Math/PI) -0.45)]
                   {:image/size [400 400]
                    :image/background [:color/rgb 245 243 238]
                    :image/nodes
                    [(s3d/render-mesh proj teapot
                       {:style {:style/fill [:color/rgb 175 185 195]
                                :style/stroke {:color [:color/rgb 135 145 155] :width 0.15}}
                        :light {:light/direction [1 2 1]
                                :light/ambient 0.3
                                :light/intensity 0.7}
                        :cull-back false})]})))
     :fps 30}))

;; --- 3. Rotating Torus ---

(defn ^{:example {:output "3d-rotating-torus.gif"
                  :title  "Rotating Torus"
                  :desc   "A parametric torus spinning on its axis with diffuse shading."}}
  rotating-torus []
  {:frames (anim/frames 60
             (fn [t]
               (let [proj (s3d/isometric {:scale 55 :origin [200 200]})
                     mesh (-> (s3d/torus-mesh 1.8 0.7 24 12)
                              (s3d/rotate-mesh :x 0.4)
                              (s3d/rotate-mesh :y (* t 2.0 Math/PI)))]
                 {:image/size [400 400]
                  :image/background [:color/rgb 20 20 30]
                  :image/nodes
                  [(s3d/render-mesh proj mesh
                     {:style {:style/fill [:color/rgb 220 160 60]
                              :style/stroke {:color [:color/rgb 160 110 30] :width 0.5}}
                      :light {:light/direction [1 2 1]
                              :light/ambient 0.2
                              :light/intensity 0.8}
                      :cull-back false})]})))
   :fps 30})

;; --- 4. Isometric Scene ---

(defn ^{:example {:output "3d-scene.png"
                  :title  "Isometric Scene"
                  :desc   "Three primitives — cube, cylinder, and sphere — with shared directional lighting."}}
  isometric-scene []
  (let [proj  (s3d/isometric {:scale 35 :origin [200 210]})
        light {:light/direction [1 2 0.5] :light/ambient 0.3 :light/intensity 0.7}]
    {:image/size [400 400]
     :image/background [:color/rgb 245 243 238]
     :image/nodes
     [(s3d/cube proj [-1.5 0 -1.5] 2
        {:style {:style/fill [:color/rgb 90 140 200]
                 :style/stroke {:color [:color/rgb 50 80 130] :width 0.5}}
         :light light})
      (s3d/cylinder proj [2 0 -1.5] 0.9 2.2
        {:style {:style/fill [:color/rgb 200 100 80]
                 :style/stroke {:color [:color/rgb 130 55 40] :width 0.5}}
         :light light :segments 20})
      (s3d/sphere proj [0 1.3 1.8] 1.0
        {:style {:style/fill [:color/rgb 100 180 100]
                 :style/stroke {:color [:color/rgb 50 110 50] :width 0.3}}
         :light light :segments 16 :rings 8})]}))

;; --- 5. Rotating Cube ---

(defn ^{:example {:output "3d-rotating-cube.gif"
                  :title  "Rotating Cube"
                  :desc   "A cube tumbling in space with directional lighting."}}
  rotating-cube []
  {:frames (anim/frames 60
             (fn [t]
               (let [proj  (s3d/isometric {:scale 70 :origin [200 200]})
                     mesh  (-> (s3d/cube-mesh [-1 -1 -1] 2)
                               (s3d/rotate-mesh :y (* t 2.0 Math/PI))
                               (s3d/rotate-mesh :x (* t 0.7 Math/PI)))]
                 {:image/size [400 400]
                  :image/background [:color/rgb 20 20 30]
                  :image/nodes
                  [(s3d/render-mesh proj mesh
                     {:style {:style/fill [:color/rgb 70 130 210]
                              :style/stroke {:color [:color/rgb 140 180 240] :width 1}}
                      :light {:light/direction [1 2 0.5]
                              :light/ambient 0.25
                              :light/intensity 0.75}})]})))
   :fps 30})

;; --- 6. Isometric City ---

(defn ^{:example {:output "3d-city.png"
                  :title  "Isometric City"
                  :desc   "An 8x8 grid of buildings with randomized heights and hue-shifted colors."}}
  isometric-city []
  (let [n 6  spacing 2.4  offset (* -0.5 n spacing)
        proj  (s3d/isometric {:scale 22 :origin [200 220]})
        light {:light/direction [1 3 0.5] :light/ambient 0.3 :light/intensity 0.7}
        rng   (java.util.Random. 42)
        mesh
        (into []
          (for [gx (range n) gz (range n)
                :let [x (+ offset (* gx spacing))
                      z (+ offset (* gz spacing))
                      h (+ 0.8 (* 4.0 (.nextDouble rng)))
                      hue (* 360.0 (/ (+ gx gz) (* 2.0 n)))
                      r (int (+ 110 (* 70 (Math/sin (* hue 0.0174)))))
                      g (int (+ 120 (* 50 (Math/sin (* (+ hue 120) 0.0174)))))
                      b (int (+ 140 (* 60 (Math/sin (* (+ hue 240) 0.0174)))))
                      building (-> (s3d/cube-mesh [0 0 0] 1)
                                   (s3d/scale-mesh [2.0 h 2.0])
                                   (s3d/translate-mesh [x 0 z]))]
                face building]
            (assoc face :face/style
              {:style/fill [:color/rgb r g b]
               :style/stroke {:color [:color/rgb (- r 35) (- g 35) (- b 35)]
                              :width 0.3}})))]
    {:image/size [400 400]
     :image/background [:color/rgb 225 230 238]
     :image/nodes [(s3d/render-mesh proj mesh {:light light})]}))

;; --- 7. Torus (static) ---

(defn ^{:example {:output "3d-torus.png"
                  :title  "Torus"
                  :desc   "A golden torus with fine mesh detail and smooth Lambertian shading."}}
  torus []
  (let [proj (s3d/isometric {:scale 55 :origin [200 200]})
        mesh (-> (s3d/torus-mesh 1.8 0.7 32 16)
                 (s3d/rotate-mesh :x 0.6))]
    {:image/size [400 400]
     :image/background [:color/rgb 245 243 238]
     :image/nodes
     [(s3d/render-mesh proj mesh
        {:style {:style/fill [:color/rgb 220 170 60]}
         :light {:light/direction [1 2 1]
                 :light/ambient 0.25
                 :light/intensity 0.75}
         :cull-back false})]}))

;; --- 8. Wireframe ---

(defn ^{:example {:output "3d-wireframe.png"
                  :title  "Wireframe"
                  :desc   "A torus rendered as wireframe with deduplicated, depth-sorted edges."}}
  wireframe []
  {:image/size [400 400]
   :image/background [:color/rgb 245 243 238]
   :image/nodes
   [(s3d/render-mesh
      (s3d/look-at (s3d/orthographic {:scale 55 :origin [200 200]})
                   [3 2.5 4] [0 0 0])
      (s3d/torus-mesh 1.5 0.6 20 10)
      {:wireframe true
       :style {:style/stroke {:color [:color/rgb 60 80 120] :width 0.5}}})]})

;; --- 9. Camera: look-at ---

(defn- camera-scene-light []
  {:light/direction [1 2 0.5] :light/ambient 0.3 :light/intensity 0.7})

(defn- camera-ground-mesh [size subdivisions]
  (let [half (/ (double size) 2.0)
        step (/ (double size) subdivisions)]
    (into []
      (for [i (range subdivisions) j (range subdivisions)
            :let [x0 (+ (- half) (* i step))
                  z0 (+ (- half) (* j step))
                  x1 (+ x0 step)
                  z1 (+ z0 step)]]
        (s3d/make-face [[x0 0.0 z1] [x1 0.0 z1] [x1 0.0 z0] [x0 0.0 z0]])))))

(defn- camera-ground-style []
  {:style/fill   [:color/rgb 180 175 165]
   :style/stroke {:color [:color/rgb 165 160 150] :width 0.2}})

(defn- camera-objects []
  (s3d/merge-meshes
    [(s3d/cube-mesh [-1.2 0 -1.2] 1.5)
     {:style/fill [:color/rgb 70 130 200]
      :style/stroke {:color [:color/rgb 40 80 140] :width 0.5}}]
    [(-> (s3d/cylinder-mesh 0.6 2.0 48)
         (s3d/translate-mesh [2.0 0.0 -0.5]))
     {:style/fill [:color/rgb 200 90 70]
      :style/stroke {:color [:color/rgb 200 90 70] :width 0.5}}]
    [(-> (s3d/sphere-mesh 0.7 16 8)
         (s3d/translate-mesh [0.0 0.7 2.0]))
     {:style/fill [:color/rgb 80 180 100]
      :style/stroke {:color [:color/rgb 40 110 50] :width 0.3}}]))

(defn- render-camera-scene [proj]
  (let [ground (camera-ground-mesh 7 12)
        objects (camera-objects)
        light (camera-scene-light)
        ground-style (camera-ground-style)]
    [(s3d/render-mesh proj ground  {:style ground-style :light light})
     (s3d/render-mesh proj objects {:light light})]))

(defn ^{:example {:output "3d-look-at.png"
                  :title  "Camera: look-at"
                  :desc   "Point the camera from a position toward a target, no manual yaw/pitch math."}}
  camera-look-at []
  {:image/size [400 400]
   :image/background [:color/rgb 245 243 238]
   :image/nodes
   (render-camera-scene
     (s3d/look-at (s3d/orthographic {:scale 55 :origin [200 210]})
                  [4 3.5 6] [0 0.5 0]))})

;; --- 10. Camera: orbit ---

(defn ^{:example {:output "3d-orbit.gif"
                  :title  "Camera: orbit"
                  :desc   "Camera on a sphere around a target, orbiting the scene."}}
  camera-orbit []
  {:frames (anim/frames 90
             (fn [t]
               {:image/size [400 400]
                :image/background [:color/rgb 245 243 238]
                :image/nodes
                (render-camera-scene
                  (s3d/orbit (s3d/orthographic {:scale 55 :origin [200 210]})
                             [0 0.5 0] 8 (* t 2.0 Math/PI) -0.4))}))
   :fps 30})

;; --- 11. Camera: perspective FOV ---

(defn ^{:example {:output "3d-perspective-fov.png"
                  :title  "Camera: Perspective FOV"
                  :desc   "Field-of-view control for perspective projection; closer objects appear larger."}}
  camera-perspective-fov []
  {:image/size [400 400]
   :image/background [:color/rgb 245 243 238]
   :image/nodes
   (render-camera-scene
     (s3d/look-at
       (s3d/perspective {:scale 55 :origin [200 210]
                         :distance (s3d/fov->distance (/ Math/PI 3) (/ 200.0 55))})
       [4 4 7] [0 0.5 0]))})

;; --- 12. New Primitives ---

(defn ^{:example {:output "3d-cone-torus.png"
                  :title  "New Primitives"
                  :desc   "Cone, torus, and sphere combined with merge-meshes and perspective projection."}}
  new-primitives []
  (let [light {:light/direction [1 2 0.5] :light/ambient 0.3 :light/intensity 0.7}
        proj  (s3d/look-at
                (s3d/perspective {:scale 55 :origin [200 210]
                                  :distance (s3d/fov->distance (/ Math/PI 3) (/ 200.0 55))})
                [3 3 5] [0 0.8 0])]
    {:image/size [400 400]
     :image/background [:color/rgb 245 243 238]
     :image/nodes
     [(s3d/render-mesh proj
        (s3d/merge-meshes
          [(s3d/cone-mesh 0.8 2.0 48)
           {:style/fill [:color/rgb 220 160 60]}]
          [(-> (s3d/torus-mesh 1.2 0.3 48 24)
               (s3d/translate-mesh [0 0.3 0]))
           {:style/fill [:color/rgb 70 130 200]}]
          [(-> (s3d/sphere-mesh 0.5 32 16)
               (s3d/translate-mesh [-2.0 0.5 0.5]))
           {:style/fill [:color/rgb 200 80 80]}])
        {:light light})]}))

;; --- 13. Wireframe Overlay ---

(defn ^{:example {:output "3d-torus-wireframe-overlay.gif"
                  :title  "Wireframe Overlay"
                  :desc   "Solid shading and wireframe combined; wireframe renders at 40% opacity over the solid torus."}}
  wireframe-overlay []
  {:frames (anim/frames 60
             (fn [t]
               (let [proj (s3d/orbit (s3d/orthographic {:scale 50 :origin [200 200]})
                                     [0 0 0] 5 (* t 2.0 Math/PI) -0.3)
                     mesh (s3d/torus-mesh 1.5 0.6 20 10)]
                 {:image/size [400 400]
                  :image/background [:color/rgb 20 20 30]
                  :image/nodes
                  [(s3d/render-mesh proj mesh
                     {:style {:style/fill [:color/rgb 60 100 160]}
                      :light {:light/direction [1 2 1]
                              :light/ambient 0.2
                              :light/intensity 0.8}
                      :cull-back false})
                   {:node/type :group
                    :group/composite :src-over
                    :node/opacity 0.4
                    :group/children
                    [(s3d/render-mesh proj mesh
                       {:wireframe true
                        :style {:style/stroke {:color [:color/rgb 180 210 255]
                                               :width 0.4}}})]}]})))
   :fps 30})

;; --- 14. Specular Spheres ---

(defn ^{:example {:output "3d-specular-spheres.png"
                  :title  "Specular Spheres"
                  :desc   "Three spheres with increasing specular highlights demonstrating Blinn-Phong materials."}}
  specular-spheres []
  (let [proj  (s3d/perspective {:scale 80 :origin [300 200]
                                :yaw 0.2 :pitch -0.25 :distance 10})
        light {:light/direction [1 2 1]
               :light/ambient 0.12
               :light/intensity 0.88}
        mesh  (s3d/sphere-mesh 1.2 20 12)]
    {:image/size [600 400]
     :image/background [:color/rgb 18 20 28]
     :image/nodes
     [;; Matte sphere (no specular)
      (s3d/render-mesh proj
        (s3d/translate-mesh mesh [-3 0 0])
        {:style {:style/fill [:color/rgb 180 60 60]
                 :material (material/phong
                             :specular 0.0 :shininess 1.0)}
         :light light})
      ;; Medium specular
      (s3d/render-mesh proj mesh
        {:style {:style/fill [:color/rgb 60 120 180]
                 :material (material/phong
                             :specular 0.4 :shininess 32.0)}
         :light light})
      ;; High specular (glossy)
      (s3d/render-mesh proj
        (s3d/translate-mesh mesh [3 0 0])
        {:style {:style/fill [:color/rgb 200 180 60]
                 :material (material/phong
                             :specular 0.8 :shininess 128.0)}
         :light light})]}))

;; --- 15. Glossy Torus ---

(defn ^{:example {:output "3d-glossy-torus.gif"
                  :title  "Glossy Torus"
                  :desc   "A rotating torus with specular highlights catching the light as it turns."}}
  glossy-torus []
  {:frames (anim/frames 60
             (fn [t]
               (let [proj (s3d/orbit (s3d/orthographic {:scale 55 :origin [200 200]})
                                     [0 0 0] 5 (* t 2.0 Math/PI) -0.35)
                     mesh (-> (s3d/torus-mesh 1.5 0.6 24 12)
                              (s3d/rotate-mesh :x 0.3))]
                 {:image/size [400 400]
                  :image/background [:color/rgb 15 15 22]
                  :image/nodes
                  [(s3d/render-mesh proj mesh
                     {:style {:style/fill [:color/rgb 160 80 200]
                              :material (material/phong
                                          :specular 0.5
                                          :shininess 48.0)
                              :style/stroke {:color [:color/rgb 100 40 140]
                                             :width 0.3}}
                      :light {:light/direction [1 2 0.5]
                              :light/ambient 0.15
                              :light/intensity 0.85}
                      :cull-back false})]})))
   :fps 30})

;; --- 16. Material Showcase ---

(defn ^{:example {:output "3d-material-showcase.png"
                  :title  "Material Showcase"
                  :desc   "Four primitives with different Blinn-Phong material properties — matte, satin, glossy, and mirror-like."}}
  material-showcase []
  (let [proj  (s3d/isometric {:scale 30 :origin [300 220]})
        light {:light/direction [1 1.5 0.8]
               :light/ambient 0.15
               :light/intensity 0.85}]
    {:image/size [600 400]
     :image/background [:color/rgb 30 32 38]
     :image/nodes
     [(s3d/render-mesh proj
        (s3d/cube-mesh [-5 0 -1] 2.5)
        {:style {:style/fill [:color/rgb 200 80 60]
                 :material (material/phong :specular 0.0 :shininess 1.0)}
         :light light})
      (s3d/render-mesh proj
        (s3d/sphere-mesh 1.4 16 10)
        {:style {:style/fill [:color/rgb 60 160 120]
                 :material (material/phong :specular 0.3 :shininess 16.0)}
         :light light})
      (s3d/render-mesh proj
        (-> (s3d/cylinder-mesh 1.0 2.5 16)
            (s3d/translate-mesh [4 0 -1]))
        {:style {:style/fill [:color/rgb 80 120 200]
                 :material (material/phong :specular 0.6 :shininess 64.0)}
         :light light})
      (s3d/render-mesh proj
        (-> (s3d/torus-mesh 1.2 0.5 20 10)
            (s3d/translate-mesh [0 0 4])
            (s3d/rotate-mesh :x 0.5))
        {:style {:style/fill [:color/rgb 200 180 60]
                 :material (material/phong :specular 0.9 :shininess 256.0)}
         :light light})]}))

;; --- 17. Colored Point Lights ---

(defn ^{:example {:output "3d-colored-lights.png"
                  :title  "Colored Point Lights"
                  :desc   "A sphere lit by warm and cool omni lights with hemisphere ambient."}}
  colored-point-lights []
  (let [mesh (s3d/sphere-mesh 1.5 24 16)
        proj (s3d/perspective {:scale 120 :origin [200 200]
                               :yaw 0.3 :pitch -0.25 :distance 6})]
    {:image/size [400 400]
     :image/background [:color/rgb 15 15 20]
     :image/nodes
     [(s3d/render-mesh proj mesh
        {:style {:style/fill [:color/rgb 200 200 200]
                 :material (material/phong :ambient 0.05 :diffuse 0.8
                                           :specular 0.5 :shininess 48.0)}
         :lights [(material/omni [3 2 2]
                    :color [:color/rgb 255 180 100]
                    :multiplier 1.5
                    :decay :inverse :decay-start 2.0)
                  (material/omni [-3 1 -1]
                    :color [:color/rgb 80 130 255]
                    :multiplier 1.2
                    :decay :inverse :decay-start 2.0)
                  (material/hemisphere
                    [:color/rgb 40 50 80]
                    [:color/rgb 15 10 5]
                    :multiplier 0.15)]})]}))

;; --- 18. Spotlight Scene ---

(defn ^{:example {:output "3d-spotlight.png"
                  :title  "Spotlight"
                  :desc   "A spot light with visible hotspot and falloff on a sphere and floor."}}
  spotlight-scene []
  (let [sphere (s3d/sphere-mesh 1.0 20 12)
        floor  (s3d/cube-mesh [-3 -1.5 -3] 6)
        proj   (s3d/perspective {:scale 80 :origin [200 220]
                                 :yaw 0.4 :pitch -0.35 :distance 8})]
    {:image/size [400 400]
     :image/background [:color/rgb 10 10 15]
     :image/nodes
     [(s3d/render-mesh proj
        (s3d/scale-mesh floor [1.0 0.05 1.0])
        {:style {:style/fill [:color/rgb 180 180 180]
                 :material (material/phong :ambient 0.02 :diffuse 0.8 :specular 0.1)}
         :lights [(material/spot [0 8 0] [0 -1 0]
                    :color [:color/rgb 255 240 200]
                    :multiplier 2.0
                    :hotspot 20 :falloff 30
                    :decay :inverse :decay-start 3.0)
                  (material/hemisphere
                    [:color/rgb 20 25 40]
                    [:color/rgb 5 5 5]
                    :multiplier 0.1)]})
      (s3d/render-mesh proj sphere
        {:style {:style/fill [:color/rgb 200 60 60]
                 :material (material/phong :ambient 0.02 :diffuse 0.7
                                           :specular 0.6 :shininess 64.0)}
         :lights [(material/spot [0 8 0] [0 -1 0]
                    :color [:color/rgb 255 240 200]
                    :multiplier 2.0
                    :hotspot 20 :falloff 30
                    :decay :inverse :decay-start 3.0)
                  (material/hemisphere
                    [:color/rgb 20 25 40]
                    [:color/rgb 5 5 5]
                    :multiplier 0.1)]})]}))

(comment
  ;; Evaluate individual examples at the REPL:
  (utah-teapot)
  (utah-teapot-spin)
  (rotating-torus)
  (isometric-scene)
  (rotating-cube)
  (isometric-city)
  (torus)
  (wireframe)
  (camera-look-at)
  (camera-orbit)
  (camera-perspective-fov)
  (new-primitives)
  (wireframe-overlay)
  (specular-spheres)
  (glossy-torus)
  (material-showcase)
  (colored-point-lights)
  (spotlight-scene))
