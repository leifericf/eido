(ns eido.obj-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.obj :as obj]))

;; --- MTL parsing ---

(def sample-mtl
  "newmtl red
Kd 1.0 0.0 0.0
d 0.8

newmtl blue
Kd 0.0 0.0 1.0
")

(deftest parse-mtl-basic-test
  (let [materials (obj/parse-mtl sample-mtl)]
    (is (= 2 (count materials)))
    (is (= [:color/rgb 255 0 0] (get-in materials ["red" :style/fill])))
    (is (= 0.8 (get-in materials ["red" :node/opacity])))
    (is (= [:color/rgb 0 0 255] (get-in materials ["blue" :style/fill])))
    (testing "default opacity is 1.0"
      (is (= 1.0 (get-in materials ["blue" :node/opacity]))))))

(deftest parse-mtl-empty-test
  (is (= {} (obj/parse-mtl ""))))

;; --- OBJ parsing ---

(def cube-obj
  "# Simple cube
v 0 0 0
v 1 0 0
v 1 1 0
v 0 1 0
v 0 0 1
v 1 0 1
v 1 1 1
v 0 1 1

f 1 2 3 4
f 5 6 7 8
f 1 2 6 5
f 2 3 7 6
f 3 4 8 7
f 4 1 5 8
")

(deftest parse-obj-basic-test
  (let [mesh (obj/parse-obj cube-obj {})]
    (is (= 6 (count mesh)))
    (testing "each face has 4 vertices"
      (doseq [face mesh]
        (is (= 4 (count (:face/vertices face))))))
    (testing "each face has a computed normal"
      (doseq [face mesh]
        (is (some? (:face/normal face)))))))

(deftest parse-obj-vertex-values-test
  (let [mesh (obj/parse-obj cube-obj {})
        first-face (first mesh)
        verts (:face/vertices first-face)]
    (is (= [0.0 0.0 0.0] (first verts)))
    (is (= [1.0 0.0 0.0] (second verts)))))

(def triangle-with-normals-obj
  "v 0 0 0
v 1 0 0
v 0 1 0
vn 0 0 1
f 1//1 2//1 3//1
")

(deftest parse-obj-vertex-normal-refs-test
  (let [mesh (obj/parse-obj triangle-with-normals-obj {})]
    (is (= 1 (count mesh)))
    (is (= [0.0 0.0 1.0] (:face/normal (first mesh))))))

(def triangle-full-refs-obj
  "v 0 0 0
v 1 0 0
v 0 1 0
vt 0 0
vt 1 0
vt 0 1
vn 0 0 1
f 1/1/1 2/2/1 3/3/1
")

(deftest parse-obj-full-refs-test
  (testing "vertex/texture/normal format (texture coords ignored)"
    (let [mesh (obj/parse-obj triangle-full-refs-obj {})]
      (is (= 1 (count mesh)))
      (is (= 3 (count (:face/vertices (first mesh))))))))

(def negative-index-obj
  "v 0 0 0
v 1 0 0
v 1 1 0
v 0 1 0
f -4 -3 -2 -1
")

(deftest parse-obj-negative-indices-test
  (let [mesh (obj/parse-obj negative-index-obj {})]
    (is (= 1 (count mesh)))
    (is (= [0.0 0.0 0.0] (first (:face/vertices (first mesh)))))))

(def obj-with-materials
  "usemtl red
v 0 0 0
v 1 0 0
v 0 1 0
f 1 2 3

usemtl blue
v 0 0 1
v 1 0 1
v 0 1 1
f 4 5 6
")

(deftest parse-obj-with-materials-test
  (let [materials {"red"  {:style/fill [:color/rgb 255 0 0]}
                   "blue" {:style/fill [:color/rgb 0 0 255]}}
        mesh (obj/parse-obj obj-with-materials {:materials materials})]
    (is (= 2 (count mesh)))
    (is (= [:color/rgb 255 0 0] (get-in (first mesh) [:face/style :style/fill])))
    (is (= [:color/rgb 0 0 255] (get-in (second mesh) [:face/style :style/fill])))))

(deftest parse-obj-default-style-test
  (let [mesh (obj/parse-obj "v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n"
               {:default-style {:style/fill [:color/rgb 128 128 128]}})]
    (is (= [:color/rgb 128 128 128]
           (get-in (first mesh) [:face/style :style/fill])))))

(deftest parse-obj-comments-and-blanks-test
  (testing "comments and blank lines are ignored"
    (let [mesh (obj/parse-obj "# comment\n\nv 0 0 0\nv 1 0 0\nv 0 1 0\n\nf 1 2 3\n" {})]
      (is (= 1 (count mesh))))))

(def obj-with-objects
  "o Cube1
v 0 0 0
v 1 0 0
v 1 1 0
f 1 2 3

o Cube2
v 2 0 0
v 3 0 0
v 3 1 0
f 4 5 6
")

(deftest parse-obj-objects-test
  (testing "object directives don't break parsing"
    (let [mesh (obj/parse-obj obj-with-objects {})]
      (is (= 2 (count mesh))))))
