(ns eido.gen.lsystem
  "Lindenmayer systems: string rewriting and turtle-graphics interpretation.
  Generates branching, fractal, and botanical structures from simple rules.")

;; --- string rewriting ---

(defn expand-string
  "Rewrites axiom by applying rules for n iterations.
  Characters not in rules pass through unchanged."
  [axiom rules iterations]
  (loop [s axiom i 0]
    (if (>= i iterations)
      s
      (recur (apply str (map #(get rules (str %) (str %)) s))
             (inc i)))))

;; --- turtle interpreter ---

(defn interpret
  "Interprets an L-system string via turtle graphics.
  Returns scene-format path commands.
  opts: :angle (degrees), :length, :origin [x y], :heading (degrees).
  Symbols: F=draw, G=move, +=turn right, -=turn left,
           [=push, ]=pop, |=reverse."
  [expanded opts]
  (let [angle-rad (* (double (:angle opts)) (/ Math/PI 180.0))
        heading   (* (double (get opts :heading 0.0)) (/ Math/PI 180.0))
        length    (double (:length opts))
        [ox oy]   (:origin opts [0 0])]
    (loop [chars (seq expanded)
           x     (double ox)
           y     (double oy)
           dir   heading
           stack []
           cmds  [[:move-to [x y]]]
           drawing? false]
      (if-not (seq chars)
        cmds
        (let [c (first chars)
              rest-chars (rest chars)]
          (case c
            \F (let [nx (+ x (* length (Math/cos dir)))
                     ny (+ y (* length (Math/sin dir)))]
                 (recur rest-chars nx ny dir stack
                        (conj cmds [:line-to [nx ny]])
                        true))
            \G (let [nx (+ x (* length (Math/cos dir)))
                     ny (+ y (* length (Math/sin dir)))]
                 (recur rest-chars nx ny dir stack
                        (conj cmds [:move-to [nx ny]])
                        false))
            \+ (recur rest-chars x y (+ dir angle-rad) stack cmds drawing?)
            \- (recur rest-chars x y (- dir angle-rad) stack cmds drawing?)
            \| (recur rest-chars x y (+ dir Math/PI) stack cmds drawing?)
            \[ (recur rest-chars x y dir
                      (conj stack [x y dir])
                      cmds drawing?)
            \] (let [[sx sy sd] (peek stack)]
                 (recur rest-chars sx sy sd
                        (pop stack)
                        (conj cmds [:move-to [sx sy]])
                        false))
            ;; Unknown symbol — skip
            (recur rest-chars x y dir stack cmds drawing?)))))))

;; --- convenience ---

(defn lsystem->path-cmds
  "Full pipeline: expand axiom with rules, then interpret via turtle.
  Returns path commands.
  opts: :iterations (required), :angle (degrees), :length,
        :origin [x y] (default [0 0]), :heading (degrees, default 0)."
  [axiom rules opts]
  (let [expanded (expand-string axiom rules (get opts :iterations 3))]
    (interpret expanded opts)))

(comment
  ;; Simple bush
  (lsystem->path-cmds "F" {"F" "FF+[+F-F-F]-[-F+F+F]"}
    {:iterations 3 :angle 22.5 :length 4.0 :origin [200 400] :heading -90.0})
  ;; Koch curve
  (lsystem->path-cmds "F" {"F" "F+F-F-F+F"}
    {:iterations 3 :angle 90.0 :length 3.0 :origin [50 300]})
  ;; Sierpinski
  (lsystem->path-cmds "F-G-G" {"F" "F-G+F+G-F" "G" "GG"}
    {:iterations 4 :angle 120.0 :length 4.0 :origin [50 50]})
  )
