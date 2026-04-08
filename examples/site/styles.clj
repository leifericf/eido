(ns site.styles
  "Garden CSS definitions for the eido website."
  (:require
    [garden.core :as garden]
    [garden.stylesheet :refer [at-media]]))

;; --- Colors ---

(def colors
  {:bg         "#0a0a12"
   :bg-card    "#13131f"
   :bg-code    "#1a1a2e"
   :text       "#e0ddd5"
   :text-muted "#9090a0"
   :accent     "#c850c0"
   :accent-alt "#4158d0"
   :border     "#2a2a3e"
   :link       "#7eb8f0"
   :link-hover "#a0d0ff"})

;; --- Shared styles ---

(def base-styles
  [[:* {:box-sizing "border-box"
        :margin     0
        :padding    0}]
   [:html {:font-size "16px"}]
   [:body {:font-family "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
           :background  (:bg colors)
           :color       (:text colors)
           :line-height "1.6"}]
   [:a {:color           (:link colors)
        :text-decoration "none"}]
   ["a:hover" {:color (:link-hover colors)}]
   [:code {:font-family "'JetBrains Mono', 'Fira Code', monospace"
           :font-size   "0.875rem"}]
   [:pre {:background    (:bg-code colors)
          :border        (str "1px solid " (:border colors))
          :border-radius "8px"
          :padding       "1rem"
          :overflow-x    "auto"
          :line-height   "1.5"}]
   ["pre code" {:background "none"
                :padding    0}]
   [:code {:background    (:bg-code colors)
           :padding       "0.15rem 0.4rem"
           :border-radius "4px"}]
   ;; Global content flow — paragraphs and code blocks get breathing room
   [:p {:margin-bottom "0.75rem"
        :line-height   "1.7"}]
   ["p + pre" {:margin-top "0.5rem"}]
   ["pre + p" {:margin-top "1rem"}]
   ["pre + pre" {:margin-top "0.75rem"}]
   [:h4 {:margin-top    "1.25rem"
         :margin-bottom "0.5rem"}]])

;; --- Layout ---

(def layout-styles
  [[:.container {:max-width "1100px"
                 :margin    "0 auto"
                 :padding   "0 1.5rem"}]
   [:.nav {:display         "flex"
           :align-items     "center"
           :justify-content "space-between"
           :padding         "1rem 0"
           :border-bottom   (str "1px solid " (:border colors))}]
   [:.nav-logo {:font-size   "1.25rem"
                :font-weight "700"
                :color       (:text colors)}]
   [:.nav-links {:display  "flex"
                 :gap      "1.5rem"
                 :list-style "none"}]
   [:.nav-links>li>a {:color     (:text-muted colors)
                      :font-size "0.9rem"}]
   [".nav-links li a:hover" {:color (:text colors)}]
   [:.footer {:margin-top  "4rem"
              :padding     "2rem 0"
              :border-top  (str "1px solid " (:border colors))
              :text-align  "center"
              :color       (:text-muted colors)
              :font-size   "0.85rem"}]
   [:.alpha-banner {:text-align     "center"
                    :font-size      "0.8rem"
                    :color          "#b0a070"
                    :background     "rgba(180, 160, 80, 0.08)"
                    :border         "1px solid rgba(180, 160, 80, 0.2)"
                    :border-radius  "6px"
                    :padding        "0.5rem 1rem"
                    :margin-top     "1rem"}]])

;; --- Hero (landing page) ---

(def hero-styles
  [[:.hero {:text-align "center"
            :padding    "4rem 0 2rem"}]
   [:.hero-title {:font-size   "3rem"
                  :font-weight "800"
                  :background  (str "linear-gradient(135deg, " (:accent colors) ", " (:accent-alt colors) ")")
                  :-webkit-background-clip "text"
                  :-webkit-text-fill-color "transparent"
                  :background-clip         "text"}]
   [:.hero-tagline {:font-size  "1.25rem"
                    :color      (:text-muted colors)
                    :margin-top "0.5rem"}]
   [:.hero-images {:display         "flex"
                   :flex-wrap       "wrap"
                   :justify-content "center"
                   :gap             "0.75rem"
                   :margin-top      "2rem"}]
   [:.hero-images>img {:border-radius "8px"
                       :width         "140px"
                       :height        "140px"
                       :object-fit    "cover"}]
   [:.hero-links {:display         "flex"
                  :justify-content "center"
                  :gap             "1rem"
                  :margin-top      "2rem"}]
   [:.hero-link {:display       "inline-block"
                 :padding       "0.6rem 1.5rem"
                 :border-radius "6px"
                 :font-weight   "600"
                 :font-size     "0.95rem"}]
   [:.hero-link--primary {:background (:accent colors)
                          :color      "#fff"}]
   [:.hero-link--secondary {:border "1px solid"
                            :border-color (:border colors)
                            :color (:text colors)}]])

;; --- Features (landing page) ---

(def feature-styles
  [[:.features {:display               "grid"
                :grid-template-columns  "1fr 1fr"
                :gap                    "0"
                :margin                 "3rem 0"
                :border-top             (str "1px solid " (:border colors))}]
   (at-media {:max-width "600px"}
     [:.features {:grid-template-columns "1fr"}])
   [:.feature {:padding       "1.25rem 0"
               :border-bottom (str "1px solid " (:border colors))
               :display       "flex"
               :gap           "1rem"
               :align-items   "baseline"}]
   [".features > .feature:nth-child(odd)" {:padding-right "2rem"}]
   [".features > .feature:nth-child(even)" {:padding-left "2rem"
                                            :border-left  (str "1px solid " (:border colors))}]
   [:.feature-marker {:color       (:accent colors)
                      :font-size   "1.1rem"
                      :line-height "1"
                      :flex-shrink "0"}]
   [:.feature-body {:flex 1}]
   [:.feature-title {:font-size   "0.95rem"
                     :font-weight "600"
                     :margin-bottom "0.2rem"}]
   [:.feature-desc {:color     (:text-muted colors)
                    :font-size "0.85rem"
                    :line-height "1.5"}]])

;; --- Gallery ---

(def gallery-styles
  [[:.gallery-section {:margin-top "3rem"}]
   [:.gallery-section-title {:font-size     "1.5rem"
                             :font-weight   "700"
                             :margin-bottom "1.5rem"
                             :padding-bottom "0.5rem"
                             :border-bottom (str "1px solid " (:border colors))}]
   [:.gallery-grid {:display   "grid"
                    :grid-template-columns "repeat(auto-fill, minmax(280px, 1fr))"
                    :gap       "1.5rem"}]
   [:.gallery-card {:background    (:bg-card colors)
                    :border        (str "1px solid " (:border colors))
                    :border-radius "8px"
                    :overflow      "hidden"}]
   [:.gallery-card-img-wrap {:position "relative"
                             :cursor   "pointer"
                             :overflow "hidden"}]
   [".gallery-card-img-wrap img" {:width      "100%"
                                  :height     "220px"
                                  :object-fit "cover"
                                  :display    "block"
                                  :transition "transform 0.2s"}]
   [".gallery-card-img-wrap:hover img" {:transform "scale(1.03)"}]
   [:.gallery-card-expand {:position      "absolute"
                           :top           "0.5rem"
                           :right         "0.5rem"
                           :background    "rgba(0,0,0,0.55)"
                           :color         "#fff"
                           :border-radius "4px"
                           :padding       "0.3rem"
                           :display       "flex"
                           :opacity       0
                           :transition    "opacity 0.2s"}]
   [".gallery-card-img-wrap:hover .gallery-card-expand" {:opacity 1}]
   [:.gallery-card-body {:padding "1rem"}]
   [:.gallery-card-title {:font-size   "1rem"
                          :font-weight "600"}]
   [:.gallery-card-desc {:color     (:text-muted colors)
                         :font-size "0.85rem"
                         :margin-top "0.25rem"}]
   [:.gallery-card-tags {:display   "flex"
                         :flex-wrap "wrap"
                         :gap       "0.3rem"
                         :margin-top "0.5rem"}]
   ;; Tag pills (shared by cards and filter bar)
   [:.tag {:display       "inline-block"
           :padding       "0.15rem 0.5rem"
           :border-radius "10px"
           :font-size     "0.7rem"
           :border        (str "1px solid " (:border colors))
           :background    "transparent"
           :color         (:text-muted colors)
           :cursor        "pointer"
           :transition    "all 0.15s"
           :font-family   "inherit"
           :line-height   "1.4"}]
   [".tag:hover" {:border-color (:accent colors)
                  :color        (:text colors)}]
   [:.tag--active {:background   (:accent colors)
                   :border-color (:accent colors)
                   :color        "#fff"}]
   ;; Filter bar
   [:.gallery-filter {:display     "flex"
                      :flex-wrap   "wrap"
                      :align-items "center"
                      :gap         "0.4rem"
                      :margin-bottom "2rem"
                      :padding     "1rem"
                      :background  (:bg-card colors)
                      :border      (str "1px solid " (:border colors))
                      :border-radius "8px"}]
   [:.gallery-filter-label {:font-size   "0.8rem"
                            :color       (:text-muted colors)
                            :margin-right "0.5rem"}]
   [:details {:margin-top "0.75rem"}]
   [:summary {:cursor    "pointer"
              :color     (:link colors)
              :font-size "0.85rem"}]
   ["summary:hover" {:color (:link-hover colors)}]
   ["details pre" {:margin-top  "0.5rem"
                   :font-size   "0.8rem"
                   :max-height  "200px"
                   :overflow-y  "auto"}]
   [:.gallery-card>img {:cursor "pointer"}]
   ["#lightbox" {:display         "none"
                 :position        "fixed"
                 :top             0
                 :left            0
                 :width           "100vw"
                 :height          "100vh"
                 :background      "rgba(0,0,0,0.92)"
                 :z-index         1000
                 :cursor          "pointer"
                 :justify-content "center"
                 :align-items     "center"
                 :flex-direction  "column"}]
   ["#lightbox.active" {:display "flex"}]
   ["#lightbox-img" {:max-width  "90vw"
                     :max-height "85vh"
                     :object-fit "contain"
                     :border-radius "6px"}]
   ["#lightbox-caption" {:color      (:text-muted colors)
                         :margin-top "0.75rem"
                         :font-size  "0.9rem"}]
   ;; Code lightbox
   ["#code-lightbox" {:display         "none"
                      :position        "fixed"
                      :top             0
                      :left            0
                      :width           "100vw"
                      :height          "100vh"
                      :background      "rgba(0,0,0,0.92)"
                      :z-index         1000
                      :justify-content "center"
                      :align-items     "center"}]
   ["#code-lightbox.active" {:display "flex"}]
   ["#code-lightbox-inner" {:background    (:bg-code colors)
                            :border        (str "1px solid " (:border colors))
                            :border-radius "10px"
                            :width         "min(94vw, 1100px)"
                            :max-height    "85vh"
                            :display       "flex"
                            :flex-direction "column"
                            :overflow      "hidden"}]
   ["#code-lightbox-header" {:display         "flex"
                             :justify-content "space-between"
                             :align-items     "center"
                             :padding         "0.75rem 1rem"
                             :border-bottom   (str "1px solid " (:border colors))}]
   ["#code-lightbox-title" {:font-weight "600"
                            :font-size   "0.95rem"}]
   ["#code-lightbox-pre" {:margin     0
                          :border     "none"
                          :border-radius 0
                          :overflow-y "auto"
                          :flex       1
                          :padding    "1rem"}]
   ;; Syntax highlighting
   [:.clj-keyword {:color "#c792ea"}]
   [:.clj-string {:color "#c3e88d"}]
   [:.clj-comment {:color "#546e7a" :font-style "italic"}]
   [:.clj-number {:color "#f78c6c"}]
   [:.clj-special {:color "#89ddff"}]
   [:.clj-builtin {:color "#82aaff"}]])

;; --- Docs ---

(def docs-styles
  [[:.docs-layout {:display              "grid"
                   :grid-template-columns "220px 1fr"
                   :gap                   "2.5rem"
                   :margin-top            "2rem"}]
   (at-media {:max-width "768px"}
     [:.docs-layout {:grid-template-columns "1fr"}]
     [:.docs-sidebar {:display "none"}])
   ;; Sidebar
   [:.docs-sidebar {:position   "sticky"
                    :top        "1rem"
                    :max-height "calc(100vh - 2rem)"
                    :overflow-y "auto"
                    :padding-right "1rem"}]
   [:.docs-sidebar-category {:margin-bottom "1.25rem"}]
   [:.docs-sidebar-category-title {:font-size   "0.75rem"
                                   :font-weight "700"
                                   :text-transform "uppercase"
                                   :letter-spacing "0.06em"
                                   :color       (:accent colors)
                                   :margin-bottom "0.4rem"}]
   [".docs-sidebar-category-title a" {:color (:accent colors)
                                      :text-decoration "none"}]
   [".docs-sidebar-category-title a:hover" {:color (:accent-alt colors)}]
   [:.docs-sidebar-category>ul {:list-style "none"
                                :margin     0
                                :padding    0}]
   [".docs-sidebar-category ul li" {:margin-bottom "0.2rem"}]
   [".docs-sidebar-category ul li a" {:color       (:text-muted colors)
                                      :font-size   "0.85rem"
                                      :text-decoration "none"
                                      :padding     "0.15rem 0"
                                      :display     "block"
                                      :transition  "color 0.15s"}]
   [".docs-sidebar-category ul li a:hover" {:color (:text colors)}]
   ;; Content area
   [:.docs-content {:min-width 0}]
   [:.docs-category {:margin-bottom "4rem"}]
   [:.docs-category-title {:font-size      "1.5rem"
                           :font-weight    "700"
                           :padding-bottom "0.75rem"
                           :border-bottom  (str "1px solid " (:border colors))
                           :margin-bottom  "2rem"}]
   [:.docs-section {:margin-bottom  "2.5rem"
                    :background     (:bg-card colors)
                    :border         (str "1px solid " (:border colors))
                    :border-radius  "8px"
                    :padding        "1.5rem"}]
   [:.docs-section>h3 {:font-size     "1.15rem"
                       :font-weight   "600"
                       :margin-bottom "0.75rem"
                       :color         (:text colors)}]
   [:.docs-section>h4 {:font-size     "0.95rem"
                       :font-weight   "600"
                       :margin-top    "1.25rem"
                       :margin-bottom "0.5rem"
                       :color         (:text colors)}]
   [".docs-section p" {:color         (:text-muted colors)
                       :font-size     "0.95rem"
                       :line-height   "1.7"
                       :margin-bottom "0.75rem"}]
   [".docs-section pre" {:margin-bottom "1rem"}]
   ;; Docs code example with preview image
   [:.docs-code-example {:margin-bottom "1rem"}]
   [".docs-code-example pre" {:margin-bottom "0"}]
   [:.docs-preview {:max-width "100%"
                    :height "auto"
                    :border-radius "8px"
                    :border (str "1px solid " (:border colors))
                    :margin-top "0.75rem"}]])

;; --- API reference ---

(def api-styles
  [[:.api-ns {:margin-top "4rem"}]
   [:.api-ns-title {:font-size      "1.5rem"
                    :font-weight    "700"
                    :padding-bottom "0.75rem"
                    :border-bottom  (str "1px solid " (:border colors))
                    :margin-bottom  "1.5rem"}]
   [:.api-ns-doc {:color       (:text-muted colors)
                  :font-size   "0.9rem"
                  :line-height "1.6"
                  :margin-bottom "1.5rem"}]
   [:.api-var {:background    (:bg-card colors)
               :border        (str "1px solid " (:border colors))
               :border-radius "8px"
               :padding       "1.25rem"
               :margin-bottom "1rem"}]
   [:.api-var-name {:font-family "'JetBrains Mono', monospace"
                    :font-weight "600"
                    :color       (:accent colors)}]
   [:.api-var-args {:font-family "'JetBrains Mono', monospace"
                    :font-size   "0.85rem"
                    :color       (:text-muted colors)
                    :margin-left "0.5rem"}]
   [:.api-var-doc {:margin-top "0.5rem"
                   :font-size  "0.9rem"
                   :color      (:text-muted colors)
                   :white-space "pre-wrap"}]
   [:.api-sidebar {:position   "sticky"
                   :top        "1rem"
                   :max-height "calc(100vh - 2rem)"
                   :overflow-y "auto"
                   :font-size  "0.85rem"}]
   [:.api-sidebar-category {:margin-bottom "1.25rem"}]
   [:.api-sidebar-category-title {:font-size      "0.75rem"
                                  :font-weight    "700"
                                  :text-transform "uppercase"
                                  :letter-spacing "0.06em"
                                  :color          (:accent colors)
                                  :margin-bottom  "0.4rem"}]
   [".api-sidebar-category ul" {:list-style "none"
                                :margin     0
                                :padding    0}]
   [".api-sidebar-category ul li" {:margin-bottom "0.2rem"}]
   [".api-sidebar-category ul li a" {:color           (:text-muted colors)
                                     :font-size       "0.85rem"
                                     :text-decoration "none"
                                     :padding         "0.15rem 0"
                                     :display         "block"
                                     :transition      "color 0.15s"}]
   [".api-sidebar-category ul li a:hover" {:color (:text colors)}]
   [:.api-layout {:display               "grid"
                  :grid-template-columns  "200px 1fr"
                  :gap                    "2rem"}]
   (at-media {:max-width "768px"}
     [:.api-layout {:grid-template-columns "1fr"}]
     [:.api-sidebar {:display "none"}])])

;; --- Page title ---

(def page-title-styles
  [[:.page-title {:font-size   "2rem"
                  :font-weight "700"
                  :margin      "2rem 0 0.5rem"}]
   [:.page-subtitle {:color       (:text-muted colors)
                     :font-size   "1.1rem"
                     :margin-bottom "2rem"}]])

;; --- Combined CSS generation ---

(defn site-css []
  (garden/css
    (concat base-styles
            layout-styles
            hero-styles
            feature-styles
            gallery-styles
            docs-styles
            api-styles
            page-title-styles)))
