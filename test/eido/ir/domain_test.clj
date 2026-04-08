(ns eido.ir.domain-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.ir.domain :as domain]
    [eido.ir.program :as program]))

(deftest domain-constructors-test
  (testing "image-grid domain"
    (let [d (domain/image-grid [800 600])]
      (is (= :image-grid (:domain/kind d)))
      (is (= [800 600] (:domain/size d)))))

  (testing "world-2d domain"
    (let [d (domain/world-2d [0 0 400 300])]
      (is (= :world-2d (:domain/kind d)))
      (is (= [0 0 400 300] (:domain/bounds d)))))

  (testing "timeline domain"
    (let [d (domain/timeline :fps 30 :frames 60)]
      (is (= :timeline (:domain/kind d)))
      (is (= 30 (:domain/fps d))))))

(deftest bindings-for-test
  (is (= #{:uv :px :py :size}
         (domain/bindings-for :image-grid)))
  (is (= #{:uv}
         (domain/bindings-for :shape-local)))
  (is (= #{:pos :x :y}
         (domain/bindings-for :world-2d)))
  (is (= #{:pos :vel :age :life :index}
         (domain/bindings-for :particles))))

(deftest program-domain-validation-test
  (testing "program with domain validates bindings"
    (let [prog {:program/domain {:domain/kind :image-grid}
                :program/body   [:x :uv]}]
      ;; Should work with correct bindings
      (is (some? (program/run prog {:uv [0.5 0.5] :px 100 :py 100 :size [200 200]})))
      ;; Should throw with missing bindings
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"missing"
                            (program/run prog {:uv [0.5 0.5]}))))))

(deftest program-without-domain-skips-validation-test
  (testing "program without domain doesn't validate"
    (let [prog {:program/body [:+ 1 2]}]
      (is (= 3.0 (program/run prog {}))))))
