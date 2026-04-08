(ns eido.ir.resource
  "Named resources for multi-pass rendering.

  A resource is an explicit input/output object used by passes.
  Resources are declared in the IR container and referenced by name.

  Resource kinds:
    :image          — raster image buffer (the main framebuffer)
    :mask           — single-channel mask
    :geometry       — vector geometry (paths, shapes)
    :points         — point set
    :field          — evaluated scalar/vector field
    :particle-state — particle simulation state
    :parameter-block — named parameter values")

;; --- resource constructors ---

(defn image
  "Declares an image resource."
  [name size & {:keys [color-space] :or {color-space :srgb}}]
  {name {:resource/kind        :image
         :resource/size        size
         :resource/color-space color-space}})

(defn mask
  "Declares a mask resource."
  [name size]
  {name {:resource/kind :mask
         :resource/size size}})

(defn geometry
  "Declares a geometry resource."
  [name]
  {name {:resource/kind :geometry}})

(defn point-set
  "Declares a point set resource."
  [name points]
  {name {:resource/kind   :points
         :resource/points points}})

(defn field-resource
  "Declares a field resource."
  [name field-desc]
  {name {:resource/kind  :field
         :resource/field field-desc}})

(defn particle-state
  "Declares a particle state resource."
  [name schema]
  {name {:resource/kind   :particle-state
         :resource/schema schema}})

(defn parameter-block
  "Declares a parameter block resource."
  [name params]
  {name {:resource/kind   :parameter-block
         :resource/params params}})

;; --- resource helpers ---

(defn merge-resources
  "Merges multiple resource declarations into one map."
  [& resource-maps]
  (apply merge resource-maps))

(defn validate-pass-resources
  "Validates that all resources referenced by a pass exist in the
  resource map. Returns nil if valid, or a vector of error strings."
  [pass resources]
  (let [referenced (cond-> #{}
                     (:pass/target pass) (conj (:pass/target pass))
                     (:pass/input pass)  (conj (:pass/input pass)))
        missing    (remove #(contains? resources %) referenced)]
    (when (seq missing)
      (mapv #(str "Pass " (:pass/id pass) " references undefined resource: " %)
            missing))))

(defn validate-pipeline-resources
  "Validates all passes in a pipeline reference declared resources.
  Returns nil if valid, or a vector of error strings."
  [ir-container]
  (let [resources (:ir/resources ir-container)
        errors    (into [] (mapcat #(validate-pass-resources % resources))
                         (:ir/passes ir-container))]
    (when (seq errors) errors)))
