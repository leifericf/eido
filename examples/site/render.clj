(ns site.render
  "Site builder — renders examples and generates the eido website."
  (:require
    [clojure.java.io :as io]
    [clojure.repl :as repl]
    [clojure.string :as str]
    [eido.core :as eido]
    [site.pages :as pages]
    [site.styles :as styles]
    [hiccup.page :as page]
    [hiccup2.core :as h]))

;; --- Configuration ---

(def site-url "https://eido.leifericf.com")

(def example-namespaces
  "Namespaces to scan for example functions."
  '[gallery.art
    gallery.scenes-2d
    gallery.scenes-3d
    gallery.mixed
    gallery.particles
    gallery.typography])

(def eido-namespaces
  "Eido source namespaces for API doc generation."
  '[eido.core
    eido.animate
    eido.color
    eido.contour
    eido.decorator
    eido.distort
    eido.flow
    eido.hatch
    eido.lsystem
    eido.morph
    eido.noise
    eido.palette
    eido.particle
    eido.path
    eido.scene
    eido.scene3d
    eido.scatter
    eido.stipple
    eido.stroke
    eido.vary
    eido.voronoi
    eido.warp])

;; --- Discovery ---

(defn find-examples
  "Finds all vars with :example metadata in the given namespace."
  [ns-sym]
  (require ns-sym)
  (->> (ns-publics (find-ns ns-sym))
       vals
       (filter #(:example (meta %)))
       (sort-by #(:title (:example (meta %))))
       (mapv (fn [v]
               (let [ex (:example (meta v))]
                 (merge ex {:var v :ns ns-sym}))))))

(defn all-examples
  "Returns all examples grouped by category."
  []
  (doseq [ns-sym example-namespaces]
    (require ns-sym))
  (->> example-namespaces
       (mapv (fn [ns-sym]
               {:ns       ns-sym
                :category (or (:category (meta (find-ns ns-sym)))
                              (-> (name ns-sym)
                                  (str/replace #"examples\.gallery\." "")
                                  (str/replace "-" " ")
                                  str/capitalize))
                :examples (find-examples ns-sym)}))
       (remove #(empty? (:examples %)))))

;; --- Rendering examples ---

(defn render-example!
  "Renders a single example to the given output directory."
  [{:keys [var output]} out-dir]
  (let [result  @var
        scene   (result)
        path    (str out-dir "/images/" output)]
    (io/make-parents path)
    (if (:frames scene)
      (eido/render (:frames scene) {:output path :fps (:fps scene 30)})
      (eido/render scene {:output path}))
    path))

(defn render-all-examples!
  "Renders all discovered examples. Returns a seq of rendered paths."
  [out-dir]
  (let [groups (all-examples)]
    (doall
      (for [{:keys [examples]} groups
            example examples]
        (do
          (println "  Rendering" (:output example) "...")
          (render-example! example out-dir))))))

;; --- Source code extraction ---

(defn example-source
  "Returns the source code string for an example var."
  [{:keys [var]}]
  (repl/source-fn (symbol (str (namespace (symbol var)))
                          (str (name (symbol var))))))

;; --- HTML generation ---

(defn html-page
  "Wraps content in a full HTML page with nav, footer, and styles."
  [{:keys [title active-page]} & body]
  (str
    "<!DOCTYPE html>\n"
    (h/html
      [:html {:lang "en"}
       [:head
        [:meta {:charset "utf-8"}]
        [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
        [:title (str title " — Eido")]
        [:style (h/raw (styles/site-css))]]
       [:body
        [:div.container
         [:nav.nav
          [:a.nav-logo {:href "/"} "Eido"]
          [:ul.nav-links
           [:li [:a {:href "/"
                     :style (when (= active-page :home) "color: #e0ddd5")}
                 "Home"]]
           [:li [:a {:href "/gallery/"
                     :style (when (= active-page :gallery) "color: #e0ddd5")}
                 "Gallery"]]
           [:li [:a {:href "/docs/"
                     :style (when (= active-page :docs) "color: #e0ddd5")}
                 "Docs"]]
           [:li [:a {:href "/api/"
                     :style (when (= active-page :api) "color: #e0ddd5")}
                 "API"]]
           [:li [:a {:href "https://github.com/leifericf/eido"} "GitHub"]]]]
         [:main body]
         [:footer.footer
          [:p "Eido — declarative, data-driven image language for Clojure"]]]]])))

;; --- Landing page ---

(defn generate-landing-html
  "Generates the landing page HTML."
  [examples-by-category]
  (let [hero-images (pages/hero-images)]
    (html-page {:title "Eido" :active-page :home}
      [:section.hero
       [:h1.hero-title "Eido"]
       [:p.hero-tagline "Describe what you see as plain data"]
       [:div.hero-images
        (for [img hero-images]
          [:img {:src (str "/images/" img) :alt "" :loading "lazy"}])]
       [:div.hero-links
        [:a.hero-link.hero-link--primary {:href "/gallery/"} "Browse Gallery"]
        [:a.hero-link.hero-link--secondary {:href "/docs/"} "Read the Docs"]]]
      [:section.features
       (for [{:keys [title desc]} (pages/features)]
         [:div.feature
          [:div.feature-title title]
          [:div.feature-desc desc]])]
      [:section {:style "margin-top: 3rem"}
       [:h2 {:style "font-size: 1.5rem; margin-bottom: 1rem"} "Quick Start"]
       [:pre [:code (h/raw (pages/quick-start-code))]]]
      [:section {:style "margin-top: 2rem"}
       [:h2 {:style "font-size: 1.5rem; margin-bottom: 1rem"} "Installation"]
       [:pre [:code (pages/install-code)]]])))

;; --- Gallery page ---

(defn gallery-card
  "Renders a single gallery card with image, title, desc, and collapsible source."
  [example]
  (let [src (example-source example)]
    [:div.gallery-card
     [:img {:src (str "/images/" (:output example))
            :alt (:title example)
            :loading "lazy"}]
     [:div.gallery-card-body
      [:div.gallery-card-title (:title example)]
      [:div.gallery-card-desc (:desc example)]
      (when src
        [:details
         [:summary "View source"]
         [:pre [:code src]]])]]))

(defn generate-gallery-html
  "Generates the gallery page HTML."
  [examples-by-category]
  (html-page {:title "Gallery" :active-page :gallery}
    [:h1.page-title "Gallery"]
    [:p.page-subtitle "Every example renders from code — what you see is what the data describes."]
    (for [{:keys [category examples]} examples-by-category]
      [:section.gallery-section
       [:h2.gallery-section-title category]
       [:div.gallery-grid
        (for [example examples]
          (gallery-card example))]])))

;; --- Docs page ---

(defn generate-docs-html
  "Generates the feature docs page HTML."
  []
  (let [sections (pages/docs-sections)]
    (html-page {:title "Docs" :active-page :docs}
      [:h1.page-title "Documentation"]
      [:p.page-subtitle "Feature reference for eido's declarative image language."]
      [:nav.docs-toc
       [:div.docs-toc-title "Contents"]
       [:ul
        (for [{:keys [id title]} sections]
          [:li [:a {:href (str "#" id)} title]])]]
      (for [{:keys [id title content]} sections]
        [:section.docs-section {:id id}
         [:h2 title]
         content]))))

;; --- API page ---

(defn api-var-info
  "Extracts API info from a var."
  [v]
  (let [m (meta v)]
    {:name     (str (:name m))
     :arglists (str/join " " (map pr-str (:arglists m)))
     :doc      (:doc m)
     :added    (:added m)}))

(defn generate-api-html
  "Generates the API reference page from eido namespace metadata."
  []
  (doseq [ns-sym eido-namespaces]
    (require ns-sym))
  (let [ns-data (->> eido-namespaces
                     (mapv (fn [ns-sym]
                             (let [ns-obj  (find-ns ns-sym)
                                   publics (->> (ns-publics ns-obj)
                                                vals
                                                (remove #(:private (meta %)))
                                                (sort-by #(str (:name (meta %))))
                                                (mapv api-var-info))]
                               {:ns-sym  ns-sym
                                :ns-name (str ns-sym)
                                :ns-doc  (:doc (meta ns-obj))
                                :vars    publics})))
                     (remove #(empty? (:vars %))))]
    (html-page {:title "API Reference" :active-page :api}
      [:h1.page-title "API Reference"]
      [:p.page-subtitle "Auto-generated from source metadata."]
      [:div.api-layout
       [:nav.api-sidebar
        [:ul
         (for [{:keys [ns-name]} ns-data]
           [:li [:a {:href (str "#" ns-name)} ns-name]])]]
       [:div
        (for [{:keys [ns-name ns-doc vars]} ns-data]
          [:section.api-ns {:id ns-name}
           [:h2.api-ns-title ns-name]
           (when ns-doc
             [:p.api-ns-doc ns-doc])
           (for [{:keys [name arglists doc]} vars]
             [:div.api-var
              [:span.api-var-name name]
              (when (seq arglists)
                [:span.api-var-args arglists])
              (when doc
                [:div.api-var-doc doc])])])]])))

;; --- Site builder ---

(defn write-page! [out-dir path html]
  (let [file (io/file out-dir path)]
    (io/make-parents file)
    (spit file html)))

(defn build-site!
  "Builds the complete eido website into the output directory.
  Run via: clj -X:gallery"
  [& {:keys [out-dir] :or {out-dir "_site"}}]
  (println "Building eido site into" out-dir "...")

  ;; Render all example images
  (println "Rendering examples...")
  (render-all-examples! out-dir)

  ;; Discover examples for gallery
  (let [examples-by-category (all-examples)]

    ;; Generate pages
    (println "Generating landing page...")
    (write-page! out-dir "index.html"
      (generate-landing-html examples-by-category))

    (println "Generating gallery...")
    (write-page! out-dir "gallery/index.html"
      (generate-gallery-html examples-by-category))

    (println "Generating docs...")
    (write-page! out-dir "docs/index.html"
      (generate-docs-html))

    (println "Generating API reference...")
    (write-page! out-dir "api/index.html"
      (generate-api-html))

    ;; CNAME file for custom domain
    (spit (io/file out-dir "CNAME") "eido.leifericf.com")

    (println "Site built successfully!")
    (println (str "  " (count (mapcat :examples examples-by-category)) " examples rendered"))
    (println (str "  Open " out-dir "/index.html to preview"))))
