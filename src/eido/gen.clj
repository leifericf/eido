(ns eido.gen
  "Generative tools: noise, flow fields, contours, scatter, Voronoi,
  L-systems, particle systems, stipple, and hatch patterns.

  This namespace re-exports all public vars from:
    eido.gen.noise    — Perlin noise (2D/3D, FBM, turbulence, ridge)
    eido.gen.flow     — noise-based flow fields
    eido.gen.contour  — marching squares contour generation
    eido.gen.scatter  — distribution generators (grid, Poisson, noise-field)
    eido.gen.voronoi  — Voronoi diagrams and Delaunay triangulation
    eido.gen.lsystem  — L-system string rewriting & turtle graphics
    eido.gen.particle — particle systems with emitters & forces
    eido.gen.stipple  — Poisson disk sampling & stipple fills
    eido.gen.hatch    — hatch line pattern generation
    eido.gen.vary     — per-item variation (by-index, by-noise, by-gradient)

  Users can require this namespace for the full API, or require
  sub-namespaces directly for finer-grained imports."
  (:require
    [eido.gen.contour :as contour]
    [eido.gen.flow :as flow]
    [eido.gen.hatch :as hatch]
    [eido.gen.lsystem :as lsystem]
    [eido.gen.noise :as noise]
    [eido.gen.particle :as particle]
    [eido.gen.scatter :as scatter]
    [eido.gen.stipple :as stipple]
    [eido.gen.vary :as vary]
    [eido.gen.voronoi :as voronoi]))

;; --- re-export helper ---

(defmacro ^:private import-fn [target-sym]
  (let [local-name (symbol (name target-sym))]
    `(do (def ~local-name ~target-sym)
         (alter-meta! (var ~local-name) merge
           (dissoc (meta (var ~target-sym)) :name :ns)))))

;; noise
(import-fn noise/perlin2d)
(import-fn noise/perlin3d)
(import-fn noise/fbm)
(import-fn noise/turbulence)
(import-fn noise/ridge)

;; flow
(import-fn flow/flow-field)

;; contour
(import-fn contour/contour-lines)

;; scatter
(import-fn scatter/grid)
(import-fn scatter/poisson-disk)
(import-fn scatter/noise-field)
(import-fn scatter/scatter->nodes)

;; voronoi
(import-fn voronoi/voronoi-cells)
(import-fn voronoi/delaunay-edges)

;; lsystem
(import-fn lsystem/expand-string)
(import-fn lsystem/interpret)
(import-fn lsystem/lsystem->path-cmds)

;; particle
(import-fn particle/states)
(import-fn particle/render-frame)
(import-fn particle/simulate)
(import-fn particle/with-position)
(import-fn particle/with-seed)

;; stipple
(def stipple-poisson-disk stipple/poisson-disk)
(alter-meta! (var stipple-poisson-disk) merge
  (dissoc (meta (var stipple/poisson-disk)) :name :ns))
(import-fn stipple/stipple-fill->nodes)

;; hatch
(import-fn hatch/hatch-lines)
(import-fn hatch/hatch-fill->nodes)

;; vary
(import-fn vary/by-index)
(import-fn vary/by-position)
(import-fn vary/by-noise)
(import-fn vary/by-gradient)
(import-fn vary/apply-overrides)
