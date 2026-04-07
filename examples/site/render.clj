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

(def highlight-clj-js
  "function highlightClj(code) {
  // HTML-escape first
  code = code.replace(/&/g, '&amp;').replace(/\\x3c/g, '&lt;').replace(/>/g, '&gt;');
  // Extract comments and strings into placeholders so they don't interfere
  var tokens = [];
  code = code.replace(/(;;[^\\n]*)/g, function(m) { tokens.push('\\x3cspan class=\"clj-comment\">' + m + '\\x3c/span>'); return '\\x00T' + (tokens.length-1) + 'T\\x00'; });
  code = code.replace(/(\"(?:[^\"\\\\]|\\\\.)*\")/g, function(m) { tokens.push('\\x3cspan class=\"clj-string\">' + m + '\\x3c/span>'); return '\\x00T' + (tokens.length-1) + 'T\\x00'; });
  // Highlight remaining tokens
  code = code.replace(/(:[a-zA-Z][a-zA-Z0-9_\\-.*+!?\\/<>]*)/g, '\\x3cspan class=\"clj-keyword\">$1\\x3c/span>');
  code = code.replace(/\\b(\\d+\\.?\\d*)\\b/g, '\\x3cspan class=\"clj-number\">$1\\x3c/span>');
  code = code.replace(/(?<=\\()\\b(defn-?|def|let|fn|if|when|cond|do|loop|recur|for|doseq|mapv|map|filter|reduce|into|concat|vec|assoc|merge|require|ns)\\b/g, '\\x3cspan class=\"clj-special\">$1\\x3c/span>');
  // Restore placeholders
  code = code.replace(/\\x00T(\\d+)T\\x00/g, function(_, i) { return tokens[parseInt(i)]; });
  return code;
}")


(def example-namespaces
  "Namespaces to scan for example functions."
  '[gallery.art
    gallery.scenes-2d
    gallery.scenes-3d
    gallery.mixed
    gallery.particles
    gallery.typography
    gallery.showcase])

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
  "Wraps content in a full HTML page with nav, footer, and styles.
  :depth controls relative path prefix (0 = root, 1 = one dir deep)."
  [{:keys [title active-page depth] :or {depth 0}} & body]
  (let [prefix (if (zero? depth) "." "..")]
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
            [:a.nav-logo {:href (str prefix "/index.html")} "Eido"]
            [:ul.nav-links
             [:li [:a {:href (str prefix "/index.html")
                       :style (when (= active-page :home) "color: #e0ddd5")}
                   "Home"]]
             [:li [:a {:href (str prefix "/gallery/index.html")
                       :style (when (= active-page :gallery) "color: #e0ddd5")}
                   "Gallery"]]
             [:li [:a {:href (str prefix "/docs/index.html")
                       :style (when (= active-page :docs) "color: #e0ddd5")}
                   "Docs"]]
             [:li [:a {:href (str prefix "/api/index.html")
                       :style (when (= active-page :api) "color: #e0ddd5")}
                   "API"]]
             [:li [:a {:href "https://github.com/leifericf/eido"} "GitHub"]]]]
           [:main body]
           [:footer.footer
            [:p "Eido (from Greek " [:em "eido"] ", \"I see\") — describe what you see as plain data"]]
           ]]]))))

;; --- Landing page ---

(defn generate-landing-html
  "Generates the landing page HTML."
  [examples-by-category]
  (let [all-images (->> examples-by-category
                       (mapcat :examples)
                       (mapv :output))]
    (html-page {:title "Eido" :active-page :home :depth 0}
      [:div.alpha-banner
       "Early alpha — under heavy development. Expect some breaking changes between releases."]
      [:section.hero
       [:h1.hero-title "Eido"]
       [:p.hero-tagline "Describe what you see as plain data"]
       [:p {:style "color: #6a6a7a; font-size: 0.85rem; margin-top: 0.3rem; font-style: italic;"} "From Greek " [:em "eido"] " \u2014 \"I see\""]
       [:div#hero-images.hero-images]
       [:div.hero-links
        [:a.hero-link.hero-link--primary {:href "./gallery/index.html"} "Browse Gallery"]
        [:a.hero-link.hero-link--secondary {:href "./docs/index.html"} "Read the Docs"]]]
      [:section.features
       (for [{:keys [title desc]} (pages/features)]
         [:div.feature
          [:div.feature-marker "\u2022"]
          [:div.feature-body
           [:div.feature-title title]
           [:div.feature-desc desc]]])]
      [:section {:style "margin-top: 3rem"}
       [:h2 {:style "font-size: 1.5rem; margin-bottom: 1rem"} "How it works"]
       (pages/quick-start-content)]
      [:section {:style "margin-top: 2rem"}
       [:h2 {:style "font-size: 1.5rem; margin-bottom: 1rem"} "Installation"]
       [:pre [:code (pages/install-code)]]]
      [:script (h/raw (str highlight-clj-js "
document.querySelectorAll('pre code').forEach(function(el) {
  el.innerHTML = highlightClj(el.textContent);
});
var allImages = [" (str/join ", " (map #(str "\"" % "\"") all-images)) "];
var shuffled = allImages.sort(function() { return 0.5 - Math.random(); });
var container = document.getElementById('hero-images');
shuffled.slice(0, 6).forEach(function(img) {
  var el = document.createElement('img');
  el.src = './images/' + img;
  el.alt = '';
  el.loading = 'lazy';
  el.style.cursor = 'pointer';
  el.onclick = function() { openLightbox(this.src, this.alt); };
  container.appendChild(el);
});
function openLightbox(src, alt) {
  var lb = document.getElementById('lightbox');
  document.getElementById('lightbox-img').src = src;
  lb.classList.add('active');
  document.body.style.overflow = 'hidden';
}
function closeLightbox() {
  document.getElementById('lightbox').classList.remove('active');
  document.body.style.overflow = '';
}
document.addEventListener('keydown', function(e) { if (e.key === 'Escape') closeLightbox(); });
"))]
      [:div#lightbox {:onclick "closeLightbox()"}
       [:img#lightbox-img]])))

;; --- Gallery page ---

(defn gallery-card
  "Renders a single gallery card with image, title, desc, and collapsible source."
  [example]
  (let [src (example-source example)
        card-id (str "src-" (hash (:output example)))]
    [:div.gallery-card
     [:div.gallery-card-img-wrap {:onclick "openLightbox(this.querySelector('img').src, this.querySelector('img').alt)"}
      [:img {:src (str "../images/" (:output example))
             :alt (:title example)
             :loading "lazy"}]
      [:div.gallery-card-expand
       [:svg {:width "18" :height "18" :viewBox "0 0 24 24" :fill "none"
              :stroke "currentColor" :stroke-width "2" :stroke-linecap "round" :stroke-linejoin "round"}
        [:polyline {:points "15 3 21 3 21 9"}]
        [:polyline {:points "9 21 3 21 3 15"}]
        [:line {:x1 "21" :y1 "3" :x2 "14" :y2 "10"}]
        [:line {:x1 "3" :y1 "21" :x2 "10" :y2 "14"}]]]]
     [:div.gallery-card-body
      [:div.gallery-card-title (:title example)]
      [:div.gallery-card-desc (:desc example)]
      (when src
        [:div
         [:a.view-source {:href "#"
                          :onclick (str "openCodeLightbox('" card-id "'); return false;")}
          "View source"]
         [:pre {:id card-id :style "display:none"} [:code src]]])]]))

(defn generate-gallery-html
  "Generates the gallery page HTML."
  [examples-by-category]
  (html-page {:title "Gallery" :active-page :gallery :depth 1}
    [:h1.page-title "Gallery"]
    [:p.page-subtitle "Every example renders from code — what you see is what the data describes."]
    (for [{:keys [category examples]} examples-by-category]
      [:section.gallery-section
       [:h2.gallery-section-title category]
       [:div.gallery-grid
        (for [example examples]
          (gallery-card example))]])
    ;; Image lightbox
    [:div#lightbox {:onclick "closeLightbox()"}
     [:img#lightbox-img]
     [:div#lightbox-caption]]
    ;; Code lightbox
    [:div#code-lightbox {:onclick "closeCodeLightbox()"}
     [:div#code-lightbox-inner {:onclick "event.stopPropagation()"}
      [:div#code-lightbox-header
       [:span#code-lightbox-title]
       [:div {:style "display: flex; gap: 0.75rem; align-items: center;"}
        [:a#copy-btn {:href "#" :onclick "copyCode(); return false;"
                      :title "Copy to clipboard"
                      :style "color: #9090a0; font-size: 0.85rem; text-decoration: none; display: flex; align-items: center; gap: 0.3rem; line-height: 1;"}
         [:svg {:width "14" :height "14" :viewBox "0 0 24 24" :fill "none"
                :stroke "currentColor" :stroke-width "2" :stroke-linecap "round" :stroke-linejoin "round"}
          [:rect {:x "9" :y "9" :width "13" :height "13" :rx "2" :ry "2"}]
          [:path {:d "M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"}]]
         "Copy"]
        [:a {:href "#" :onclick "closeCodeLightbox(); return false;"
             :style "color: #9090a0; font-size: 1.2rem; text-decoration: none; line-height: 1; display: flex; align-items: center;"} "\u00d7"]]]
      [:pre#code-lightbox-pre [:code#code-lightbox-code]]]]
    [:script (h/raw (str highlight-clj-js "
function openLightbox(src, alt) {
  var lb = document.getElementById('lightbox');
  document.getElementById('lightbox-img').src = src;
  document.getElementById('lightbox-caption').textContent = alt;
  lb.classList.add('active');
  document.body.style.overflow = 'hidden';
}
function closeLightbox() {
  document.getElementById('lightbox').classList.remove('active');
  document.body.style.overflow = '';
}
function openCodeLightbox(id) {
  var src = document.getElementById(id);
  var code = src.querySelector('code').textContent;
  var title = src.closest('.gallery-card').querySelector('.gallery-card-title').textContent;
  document.getElementById('code-lightbox-code').innerHTML = highlightClj(code);
  document.getElementById('code-lightbox-code').dataset.raw = code;
  document.getElementById('code-lightbox-title').textContent = title;
  document.getElementById('copy-btn').querySelector('span') || null;
  document.getElementById('code-lightbox').classList.add('active');
  document.body.style.overflow = 'hidden';
}
function copyCode() {
  var code = document.getElementById('code-lightbox-code').dataset.raw;
  navigator.clipboard.writeText(code).then(function() {
    var btn = document.getElementById('copy-btn');
    var orig = btn.lastChild.textContent;
    btn.lastChild.textContent = 'Copied!';
    setTimeout(function() { btn.lastChild.textContent = orig; }, 1500);
  });
}
function closeCodeLightbox() {
  document.getElementById('code-lightbox').classList.remove('active');
  document.body.style.overflow = '';
}
document.addEventListener('keydown', function(e) {
  if (e.key === 'Escape') { closeLightbox(); closeCodeLightbox(); }
});
"))]))

;; --- Docs page ---

(defn generate-docs-html
  "Generates the feature docs page HTML."
  []
  (let [categories (pages/docs-categories)]
    (html-page {:title "Docs" :active-page :docs :depth 1}
      [:h1.page-title "Documentation"]
      [:p.page-subtitle "Feature reference for Eido's declarative image language."]
      [:div.docs-layout
       [:nav.docs-sidebar
        (for [{:keys [category id sections]} categories]
          [:div.docs-sidebar-category
           [:div.docs-sidebar-category-title
            [:a {:href (str "#" id)} category]]
           [:ul
            (for [{sec-id :id sec-title :title} sections]
              [:li [:a {:href (str "#" sec-id)} sec-title]])]])]
       [:div.docs-content
        (for [{:keys [category id sections]} categories]
          [:div.docs-category {:id id}
           [:h2.docs-category-title category]
           (for [{sec-id :id sec-title :title content :content} sections]
             [:section.docs-section {:id sec-id}
              [:h3 sec-title]
              content])])]
       [:script (h/raw (str highlight-clj-js "
document.querySelectorAll('pre code').forEach(function(el) {
  el.innerHTML = highlightClj(el.textContent);
});
"))]])))

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
    (html-page {:title "API Reference" :active-page :api :depth 1}
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
