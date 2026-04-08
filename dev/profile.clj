(ns profile
  "REPL-driven profiling with clj-async-profiler.
  Generates interactive flame graph SVGs for CPU analysis.

  Usage: start a REPL with :dev alias, load this file, run from comment block."
  (:require
    [clj-async-profiler.core :as prof]
    [eido.core :as eido]))

(defn profile-render
  "Profiles rendering a scene, returns the flame graph file path.
  Runs the scene render-count times to collect samples."
  [scene-fn & {:keys [render-count] :or {render-count 10}}]
  (let [scene (scene-fn)]
    ;; Warmup
    (dotimes [_ 3] (eido/render scene))
    ;; Profile
    (prof/profile
      (dotimes [_ render-count]
        (eido/render scene)))))

(defn profile-scene-gen
  "Profiles scene generation (not rendering)."
  [scene-fn & {:keys [count] :or {count 100}}]
  (dotimes [_ 10] (scene-fn))
  (prof/profile
    (dotimes [_ count]
      (scene-fn))))

(comment
  ;; Profile the slowest examples:

  ;; van-gogh-swirls — stroke outline + compile + render
  (require '[gallery.art])
  (profile-render gallery.art/van-gogh-swirls :render-count 5)

  ;; polka-pop — shadow blur
  (profile-render gallery.art/polka-pop :render-count 20)

  ;; utah-teapot — mesh compile + render
  (require '[gallery.scenes-3d])
  (profile-render gallery.scenes-3d/utah-teapot :render-count 20)

  ;; stipple-spheres — Poisson disk + many circle renders
  (profile-render gallery.art/stipple-spheres :render-count 10)

  ;; Open the flame graph viewer
  (prof/serve-ui 8080)

  ;; List saved profiles
  (prof/list-event-buffers)
  )
