(ns eido.ir.resource-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.ir :as ir]
    [eido.ir.effect :as effect]
    [eido.ir.resource :as resource]))

(deftest resource-constructors-test
  (testing "image resource"
    (let [r (resource/image :my-buffer [400 300])]
      (is (= :image (get-in r [:my-buffer :resource/kind])))
      (is (= [400 300] (get-in r [:my-buffer :resource/size])))))

  (testing "mask resource"
    (let [r (resource/mask :alpha-mask [800 600])]
      (is (= :mask (get-in r [:alpha-mask :resource/kind])))))

  (testing "point set resource"
    (let [r (resource/point-set :seeds [[10 10] [50 50]])]
      (is (= :points (get-in r [:seeds :resource/kind])))))

  (testing "parameter block"
    (let [r (resource/parameter-block :params {:time 0.5 :seed 42})]
      (is (= :parameter-block (get-in r [:params :resource/kind]))))))

(deftest merge-resources-test
  (let [merged (resource/merge-resources
                 (resource/image :buf1 [400 300])
                 (resource/mask :mask1 [400 300])
                 (resource/parameter-block :params {:time 0.0}))]
    (is (= 3 (count merged)))
    (is (contains? merged :buf1))
    (is (contains? merged :mask1))
    (is (contains? merged :params))))

(deftest validate-pass-resources-test
  (testing "valid pass returns nil"
    (let [pass {:pass/id :draw :pass/type :draw-geometry
                :pass/target :framebuffer}
          resources {:framebuffer {:resource/kind :image}}]
      (is (nil? (resource/validate-pass-resources pass resources)))))

  (testing "missing resource returns errors"
    (let [pass {:pass/id :blur :pass/type :effect-pass
                :pass/input :framebuffer
                :pass/target :output-buffer}
          resources {:framebuffer {:resource/kind :image}}]
      (is (= 1 (count (resource/validate-pass-resources pass resources)))))))

(deftest validate-pipeline-resources-test
  (testing "valid pipeline returns nil"
    (let [pipe (ir/pipeline [400 300]
                 {:r 0 :g 0 :b 0 :a 1.0}
                 [{:pass/id :draw :pass/type :draw-geometry
                   :pass/target :framebuffer :pass/items []}
                  (ir/effect-pass :blur (effect/blur :radius 3))])]
      (is (nil? (resource/validate-pipeline-resources pipe)))))

  (testing "pipeline with extra resources is valid"
    (let [pipe (ir/pipeline [400 300]
                 {:r 0 :g 0 :b 0 :a 1.0}
                 [{:pass/id :draw :pass/type :draw-geometry
                   :pass/target :framebuffer :pass/items []}
                  {:pass/id :process :pass/type :effect-pass
                   :pass/input :framebuffer :pass/target :output
                   :pass/effect (effect/grain :amount 30)}]
                 {:resources (resource/image :output [400 300])})]
      (is (nil? (resource/validate-pipeline-resources pipe)))))

  (testing "pipeline with missing resource returns errors"
    (let [pipe (ir/pipeline [400 300]
                 {:r 0 :g 0 :b 0 :a 1.0}
                 [{:pass/id :draw :pass/type :draw-geometry
                   :pass/target :framebuffer :pass/items []}
                  {:pass/id :process :pass/type :effect-pass
                   :pass/input :framebuffer :pass/target :missing-buf
                   :pass/effect (effect/grain :amount 30)}])]
      (is (some? (resource/validate-pipeline-resources pipe))))))
