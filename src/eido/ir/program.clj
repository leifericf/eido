(ns eido.ir.program
  "Minimal expression evaluator for vector-form programs.

  Programs are pure data: nested vectors describing computations.
  They are evaluated over a domain (e.g. image grid) with named bindings.

  Expression forms:
    Literals:     42, 0.5, [1 2 3]
    References:   :uv, :time, :seed (keywords resolve in env)
    Arithmetic:   [:+ a b], [:- a b], [:* a b], [:/ a b]
    Math:         [:abs x], [:sqrt x], [:pow x n], [:mod x n]
                  [:sin x], [:cos x], [:floor x], [:ceil x]
    Vector:       [:vec2 x y], [:vec3 x y z], [:vec4 x y z w]
    Access:       [:x v], [:y v] (first/second of a vector)
    Interpolate:  [:mix a b t], [:clamp x lo hi]
    Conditional:  [:select pred a b]
    Fields:       [:field/noise {field-desc} pos-expr]
    Color:        [:color/rgb r g b]"
  (:require
    [eido.ir.domain :as domain]
    [eido.ir.field :as field]))

;; --- evaluation ---

(declare evaluate)

(defn- eval-binary [op env a b]
  (let [va (evaluate env a)
        vb (evaluate env b)]
    (case op
      :+ (+ (double va) (double vb))
      :- (- (double va) (double vb))
      :* (* (double va) (double vb))
      :/ (/ (double va) (double vb)))))

(defn- eval-unary [op env x]
  (let [v (double (evaluate env x))]
    (case op
      :abs   (Math/abs v)
      :sqrt  (Math/sqrt v)
      :sin   (Math/sin v)
      :cos   (Math/cos v)
      :floor (Math/floor v)
      :ceil  (Math/ceil v))))

(defn evaluate
  "Evaluates an expression in the given environment.
  env is a map of keyword → value (e.g. {:uv [0.5 0.5] :time 0.0})."
  [env expr]
  (cond
    ;; Keyword reference → look up in env
    (keyword? expr)
    (if-let [v (get env expr)]
      v
      (throw (ex-info (str "Unbound variable: " expr)
                      {:variable expr :env-keys (keys env)})))

    ;; Number literal
    (number? expr)
    expr

    ;; Vector expression (operation or literal vector)
    (vector? expr)
    (let [op (first expr)]
      (if (keyword? op)
        (case op
          ;; Arithmetic
          (:+ :- :* :/)
          (eval-binary op env (nth expr 1) (nth expr 2))

          ;; Math
          (:abs :sqrt :sin :cos :floor :ceil)
          (eval-unary op env (nth expr 1))

          :pow (Math/pow (double (evaluate env (nth expr 1)))
                         (double (evaluate env (nth expr 2))))
          :mod (mod (double (evaluate env (nth expr 1)))
                    (double (evaluate env (nth expr 2))))

          ;; Vector constructors
          :vec2 [(evaluate env (nth expr 1))
                 (evaluate env (nth expr 2))]
          :vec3 [(evaluate env (nth expr 1))
                 (evaluate env (nth expr 2))
                 (evaluate env (nth expr 3))]
          :vec4 [(evaluate env (nth expr 1))
                 (evaluate env (nth expr 2))
                 (evaluate env (nth expr 3))
                 (evaluate env (nth expr 4))]

          ;; Component access
          :x (first (evaluate env (nth expr 1)))
          :y (second (evaluate env (nth expr 1)))

          ;; Interpolation
          :mix (let [a (double (evaluate env (nth expr 1)))
                     b (double (evaluate env (nth expr 2)))
                     t (double (evaluate env (nth expr 3)))]
                 (+ a (* t (- b a))))

          :clamp (let [x  (double (evaluate env (nth expr 1)))
                       lo (double (evaluate env (nth expr 2)))
                       hi (double (evaluate env (nth expr 3)))]
                   (Math/max lo (Math/min hi x)))

          ;; Conditional
          :select (let [pred (evaluate env (nth expr 1))]
                    (if (and (number? pred) (> (double pred) 0.0))
                      (evaluate env (nth expr 2))
                      (evaluate env (nth expr 3))))

          ;; Field sampling
          :field/noise
          (let [field-desc (nth expr 1)
                pos        (evaluate env (nth expr 2))
                [px py]    (if (vector? pos) pos [pos 0.0])]
            (field/evaluate field-desc (double px) (double py)))

          ;; Color construction
          :color/rgb
          {:r (long (evaluate env (nth expr 1)))
           :g (long (evaluate env (nth expr 2)))
           :b (long (evaluate env (nth expr 3)))
           :a 1.0}

          ;; Unknown op
          (throw (ex-info (str "Unknown program op: " op)
                          {:op op :expr expr})))

        ;; Plain vector literal (not an op)
        (mapv #(evaluate env %) expr)))

    ;; Map literal (e.g. already-resolved color)
    (map? expr)
    expr

    :else
    (throw (ex-info "Cannot evaluate expression" {:expr expr}))))

;; --- program evaluation ---

(defn run
  "Evaluates a program map over a single point.
  program: {:program/inputs {:uv :vec2 ...}
            :program/domain {:domain/kind :image-grid ...}  ;; optional
            :program/body   <expr>}
  env: {:uv [0.5 0.5] :time 0.0 ...}

  If the program has a :program/domain, validates that the env contains
  the expected bindings for that domain kind."
  [program env]
  (when-let [dom (:program/domain program)]
    (let [expected (domain/bindings-for (:domain/kind dom))
          missing  (remove #(contains? env %) expected)]
      (when (seq missing)
        (throw (ex-info (str "Program domain " (:domain/kind dom)
                             " expects bindings " (pr-str expected)
                             " but env is missing: " (pr-str (set missing)))
                        {:domain   dom
                         :expected expected
                         :missing  (set missing)
                         :env-keys (set (keys env))})))))
  (evaluate env (:program/body program)))
