(ns eido.core
  "Rendering and authoring entry point for Eido.

  Eido describes scenes as plain data; the native Phane backend renders them.
  `render` translates a scene (or animation) to Phane's grammar and renders it
  through the bridge in `eido.phane`, returning the encoded artifact bytes or
  writing them to :output. Authoring lives in eido.scene, eido.gen.*,
  eido.path.*, eido.animate, eido.color, and eido.scene3d."
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]))

(defn read-scene
  "Reads an EDN file and returns the scene map."
  [path]
  (try
    (edn/read-string (slurp path))
    (catch Exception e
      (throw (ex-info "Failed to read scene file"
                      {:path path}
                      e)))))

(defn- animation?
  "True if input is a sequence of scenes rather than a single scene map."
  [input]
  (and (sequential? input) (not (map? input))))

(defn render
  "Render a scene or animation through the native Phane backend.

    (render scene)                              → {:status :width :height
                                                   :media-type :diagnostics :bytes}
    (render scene {:output \"out.png\"})          → writes the PNG, returns the path
    (render frames {:output \"a.gif\" :fps 30})   → writes an animated GIF

  A sequence of scenes renders to an animated GIF (:fps, default 12). :base-dir
  resolves asset paths (default \".\"). The scene is translated to Phane's frozen
  grammar first (see eido.phane.translate). eido.phane is resolved lazily, so
  pure authoring needs neither the bridge nor a zig toolchain."
  ([input] (render input {}))
  ([input opts]
   (let [base-dir (or (:base-dir opts) ".")
         result   (if (animation? input)
                    ((requiring-resolve 'eido.phane/render-animation)
                     input {:fps (or (:fps opts) 12) :base-dir base-dir})
                    ((requiring-resolve 'eido.phane/render-eido) input base-dir))]
     (when (not= :ok (:status result))
       (throw (ex-info "phane render failed"
                       (select-keys result [:status :diagnostics]))))
     (if-let [output (:output opts)]
       (do (with-open [o (io/output-stream output)]
             (.write o ^bytes (:bytes result)))
           output)
       result))))
