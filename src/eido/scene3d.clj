(ns eido.scene3d
  "3D scene construction: mesh generation, transforms, and projection to 2D.

  This namespace re-exports all public vars from:
    eido.scene3d.camera    — projections & camera positioning
    eido.scene3d.mesh      — mesh constructors (primitives, platonics, parametric)
    eido.scene3d.transform — translate, rotate, scale, deform, mirror
    eido.scene3d.topology  — subdivision, auto-smooth, adjacency, normals
    eido.scene3d.surface   — UV projection, coloring, painting, material maps
    eido.scene3d.modeling  — extrude/inset/bevel faces, L-system, instancing
    eido.scene3d.render    — shading, render-mesh, depth-sort, convenience, text
    eido.scene3d.util      — make-face, mesh-bounds, mesh-center, merge-meshes, palette-color, lerp-color

  Users can require this namespace for the full API, or require
  sub-namespaces directly for finer-grained imports."
  (:require
    [eido.scene3d.camera :as camera]
    [eido.scene3d.mesh :as mesh]
    [eido.scene3d.modeling :as modeling]
    [eido.scene3d.render :as render3d]
    [eido.scene3d.surface :as surface]
    [eido.scene3d.topology :as topology]
    [eido.scene3d.transform :as xform]
    [eido.scene3d.util :as u]))

;; --- re-export helper ---

(defmacro ^:private import-fn
  "Defines a var in this namespace that delegates to another var,
  preserving its metadata (docstring, arglists, etc.)."
  [target-sym]
  (let [local-name (symbol (name target-sym))]
    `(do (def ~local-name ~target-sym)
         (alter-meta! (var ~local-name) merge
           (dissoc (meta (var ~target-sym)) :name :ns)))))

;; --- camera ---

(import-fn camera/isometric)
(import-fn camera/orthographic)
(import-fn camera/perspective)
(import-fn camera/look-at)
(import-fn camera/orbit)
(import-fn camera/fov->distance)

;; --- util ---

(import-fn u/make-face)
(import-fn u/merge-meshes)
(import-fn u/mesh-bounds)
(import-fn u/mesh-center)
(import-fn u/axis-component)
(import-fn u/axis-range)
(import-fn u/edge-key)
(import-fn u/lerp-color)
(import-fn u/make-face-selector)
(import-fn u/palette-color)

;; --- mesh ---

(import-fn mesh/cube-mesh)
(import-fn mesh/prism-mesh)
(import-fn mesh/cylinder-mesh)
(import-fn mesh/sphere-mesh)
(import-fn mesh/extrude-mesh)
(import-fn mesh/torus-mesh)
(import-fn mesh/cone-mesh)
(import-fn mesh/platonic-mesh)
(import-fn mesh/heightfield-mesh)
(import-fn mesh/revolve-mesh)
(import-fn mesh/sweep-mesh)

;; --- transform ---

(import-fn xform/translate-mesh)
(import-fn xform/rotate-mesh)
(import-fn xform/scale-mesh)
(import-fn xform/deform-mesh)
(import-fn xform/mirror-mesh)

;; --- topology ---

(import-fn topology/subdivide)
(import-fn topology/auto-smooth-edges)
(import-fn topology/build-face-adjacency)
(import-fn topology/compute-vertex-normals)

;; --- surface ---

(import-fn surface/uv-project)
(import-fn surface/color-mesh)
(import-fn surface/paint-mesh)
(import-fn surface/normal-map-mesh)
(import-fn surface/specular-map-mesh)

;; --- modeling ---

(import-fn modeling/extrude-faces)
(import-fn modeling/inset-faces)
(import-fn modeling/bevel-faces)
(import-fn modeling/detail-faces)
(import-fn modeling/lsystem-mesh)
(import-fn modeling/instance-mesh)

;; --- render ---

(import-fn render3d/render-mesh)
(import-fn render3d/depth-sort)
(import-fn render3d/cube)
(import-fn render3d/prism)
(import-fn render3d/cylinder)
(import-fn render3d/sphere)
(import-fn render3d/torus)
(import-fn render3d/cone)
(import-fn render3d/text-mesh)
(import-fn render3d/text-3d)
