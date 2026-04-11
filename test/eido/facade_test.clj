(ns eido.facade-test
  "Verifies that facade namespaces re-export every public var from
  their sub-namespaces. Catches missed exports when new functions
  are added to sub-namespaces."
  (:require
    [clojure.set :as set]
    [clojure.test :refer [deftest is testing]]
    [eido.gen]
    [eido.path]
    [eido.scene3d]))

(defn- missing-exports
  "Returns the set of public var names in sub-namespaces that are not
  present in the facade namespace."
  [facade-sym sub-ns-syms]
  (let [facade-vars (set (keys (ns-publics (find-ns facade-sym))))
        sub-vars    (reduce (fn [acc ns-sym]
                              (into acc (keys (ns-publics (find-ns ns-sym)))))
                            #{} sub-ns-syms)]
    (set/difference sub-vars facade-vars)))

(deftest gen-facade-completeness-test
  (testing "eido.gen re-exports all public vars from sub-namespaces"
    (let [missing (missing-exports 'eido.gen
                    ['eido.gen.boids 'eido.gen.ca 'eido.gen.circle
                     'eido.gen.contour 'eido.gen.flow 'eido.gen.hatch
                     'eido.gen.lsystem 'eido.gen.noise 'eido.gen.particle
                     'eido.gen.prob 'eido.gen.scatter 'eido.gen.series
                     'eido.gen.stipple 'eido.gen.subdivide 'eido.gen.vary
                     'eido.gen.voronoi])]
      (is (empty? missing)
          (str "eido.gen is missing exports: " (sort missing))))))

(deftest path-facade-completeness-test
  (testing "eido.path re-exports all public vars from sub-namespaces"
    (let [missing (missing-exports 'eido.path
                    ['eido.path.aesthetic 'eido.path.decorate
                     'eido.path.distort 'eido.path.morph
                     'eido.path.stroke 'eido.path.warp])]
      (is (empty? missing)
          (str "eido.path is missing exports: " (sort missing))))))

(deftest scene3d-facade-completeness-test
  (testing "eido.scene3d re-exports all public vars from sub-namespaces"
    (let [missing (missing-exports 'eido.scene3d
                    ['eido.scene3d.camera 'eido.scene3d.mesh
                     'eido.scene3d.modeling 'eido.scene3d.render
                     'eido.scene3d.surface 'eido.scene3d.topology
                     'eido.scene3d.transform 'eido.scene3d.util])]
      (is (empty? missing)
          (str "eido.scene3d is missing exports: " (sort missing))))))
