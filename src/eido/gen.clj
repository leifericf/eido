(ns eido.gen
  "Generative tools: noise, flow fields, contours, scatter, Voronoi,
  L-systems, particle systems, stipple, hatch patterns, probability,
  circle packing, subdivision, series, cellular automata, and boids.

  This namespace re-exports all public vars from:
    eido.gen.noise     — Perlin & simplex noise (2D/3D, FBM, turbulence, ridge)
    eido.gen.flow      — noise-based flow fields
    eido.gen.contour   — marching squares contour generation
    eido.gen.scatter   — distribution generators (grid, Poisson, noise-field)
    eido.gen.voronoi   — Voronoi diagrams and Delaunay triangulation
    eido.gen.lsystem   — L-system string rewriting & turtle graphics
    eido.gen.particle  — particle systems with emitters & forces
    eido.gen.stipple   — Poisson disk sampling & stipple fills
    eido.gen.hatch     — hatch line pattern generation
    eido.gen.vary      — per-item variation (by-index, by-noise, by-gradient)
    eido.gen.prob      — seeded probability distributions & sampling
    eido.gen.circle    — variable-radius circle packing
    eido.gen.subdivide — recursive rectangular subdivision
    eido.gen.series    — long-form generative series utilities
    eido.gen.ca        — cellular automata & reaction-diffusion
    eido.gen.boids     — flocking simulation with steering behaviors

  Users can require this namespace for the full API, or require
  sub-namespaces directly for finer-grained imports."
  (:require
    [eido.gen.boids :as boids]
    [eido.gen.ca :as ca]
    [eido.gen.circle :as circle]
    [eido.gen.contour :as contour]
    [eido.gen.flow :as flow]
    [eido.gen.hatch :as hatch]
    [eido.gen.lsystem :as lsystem]
    [eido.gen.noise :as noise]
    [eido.gen.particle :as particle]
    [eido.gen.prob :as prob]
    [eido.gen.scatter :as scatter]
    [eido.gen.series :as series]
    [eido.gen.stipple :as stipple]
    [eido.gen.subdivide :as subdivide]
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
(import-fn noise/simplex2d)
(import-fn noise/simplex3d)
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
(import-fn scatter/jitter)

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

;; prob
(import-fn prob/make-rng)
(import-fn prob/uniform)
(import-fn prob/gaussian)
(import-fn prob/weighted-choice)
(import-fn prob/weighted-sample)
(import-fn prob/shuffle-seeded)
(import-fn prob/coin)
(import-fn prob/pick)
(import-fn prob/pick-weighted)
(import-fn prob/sample)
(import-fn prob/sample-n)
(import-fn prob/pareto)
(import-fn prob/triangular)
(import-fn prob/on-circle)
(import-fn prob/in-circle)
(import-fn prob/on-sphere)
(import-fn prob/in-sphere)
(import-fn prob/scatter-on-circle)
(import-fn prob/scatter-in-circle)

;; circle packing
(import-fn circle/circle-pack)
(import-fn circle/circle-pack-in-path)
(import-fn circle/pack->nodes)

;; subdivide
(import-fn subdivide/subdivide)
(import-fn subdivide/subdivide->nodes)

;; series
(import-fn series/edition-seed)
(import-fn series/series-params)
(import-fn series/series-range)
(import-fn series/seed-grid)
(import-fn series/param-grid)

;; cellular automata & reaction-diffusion
(import-fn ca/ca-grid)
(import-fn ca/ca-step)
(import-fn ca/ca-run)
(import-fn ca/ca->nodes)
(import-fn ca/rd-grid)
(import-fn ca/rd-step)
(import-fn ca/rd-run)
(import-fn ca/rd->nodes)

;; boids
(import-fn boids/init-flock)
(import-fn boids/step-flock)
(import-fn boids/simulate-flock)
(import-fn boids/flock->nodes)

;; convenience helpers
(import-fn prob/mixture)
(import-fn vary/by-palette)
(import-fn vary/by-noise-palette)
(import-fn circle/pack->colored-nodes)
(import-fn subdivide/subdivide->palette-nodes)
(import-fn series/derive-traits)
(import-fn series/trait-summary)
