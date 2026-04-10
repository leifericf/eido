(ns eido.manifest
  "Render manifest — machine-readable sidecar files for reproducibility.

  A manifest captures everything needed to reproduce a render: the full scene
  map, render options, version info, and optional provenance (seed, params).
  Write one alongside any render with {:emit-manifest? true}."
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as str])
  (:import
    [java.time Instant]))

;; --- version info ---

(defn eido-version
  "Returns {:tag \"...\" :sha \"...\"} from the classpath resource, or nil."
  []
  (some-> (io/resource "eido/version.edn") slurp edn/read-string))

(defn- git-sha
  "Returns the short git SHA of HEAD in the current directory, or nil."
  []
  (try
    (let [proc (.start (ProcessBuilder. ["git" "rev-parse" "--short" "HEAD"]))
          out  (slurp (.getInputStream proc))]
      (when (zero? (.waitFor proc))
        (str/trim out)))
    (catch Exception _ nil)))

;; --- manifest path ---

(defn manifest-path
  "Returns the .edn sidecar path for a given output path.
  \"out.png\" → \"out.edn\", \"dir/edition-0.tiff\" → \"dir/edition-0.edn\"."
  [output-path]
  (str/replace output-path #"\.[^.]+$" ".edn"))

;; --- write / read ---

(def ^:private manifest-internal-keys
  "Opts keys that are manifest-internal and should not appear in :render-opts."
  #{:emit-manifest? :seed :params :output})

(defn write-manifest!
  "Writes a manifest EDN sidecar file alongside a render output.
  m is a map with keys:
    :scene       — the full scene map (required)
    :output-path — path to the rendered file (required)
    :render-opts — the opts map passed to render (optional)
    :seed        — the seed used, if any (optional)
    :params      — the params map, if any (optional)"
  [{:keys [scene output-path render-opts seed params]}]
  (let [path (manifest-path output-path)]
    (spit path
      (pr-str
        {:eido/manifest-version 1
         :eido/version          (eido-version)
         :project/sha           (git-sha)
         :timestamp             (str (Instant/now))
         :scene                 scene
         :render-opts           (apply dissoc render-opts manifest-internal-keys)
         :output-paths          [output-path]
         :seed                  seed
         :params                params}))
    path))

(defn read-manifest
  "Reads a manifest EDN file and returns the manifest map."
  [path]
  (edn/read-string (slurp path)))

(defn render-from-manifest
  "Reads a manifest and re-renders the scene using stored options.
  Override keys in opts to change output path, format, etc.
  Returns the result of eido.core/render."
  ([manifest-path]
   (render-from-manifest manifest-path {}))
  ([mpath override-opts]
   (let [manifest (read-manifest mpath)
         scene    (:scene manifest)
         opts     (merge (:render-opts manifest) override-opts)]
     (when-let [mv (:eido/version manifest)]
       (let [current (eido-version)]
         (when (and current (:tag current) (:tag mv)
                    (not= (:tag mv) (:tag current)))
           (binding [*out* *err*]
             (println (str "Warning: manifest was created with Eido "
                           (:tag mv) " but current version is "
                           (:tag current)))))))
     ((requiring-resolve 'eido.core/render) scene opts))))
