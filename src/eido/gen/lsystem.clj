(ns eido.gen.lsystem)

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
  Symbols: F=draw, G=move, +=turn right, -=turn left,
           [=push, ]=pop, |=reverse."
  [expanded angle-deg length origin heading-deg]
  (let [angle-rad (* (double angle-deg) (/ Math/PI 180.0))
        heading   (* (double heading-deg) (/ Math/PI 180.0))
        length    (double length)
        [ox oy]   origin]
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
  Returns path commands."
  [axiom rules iterations angle length origin heading]
  (let [expanded (expand-string axiom rules iterations)]
    (interpret expanded angle length origin heading)))

(comment
  ;; Simple bush
  (lsystem->path-cmds "F" {"F" "FF+[+F-F-F]-[-F+F+F]"} 3 22.5 4.0 [200 400] -90.0)
  ;; Koch curve
  (lsystem->path-cmds "F" {"F" "F+F-F-F+F"} 3 90.0 3.0 [50 300] 0.0)
  ;; Sierpinski
  (lsystem->path-cmds "F-G-G" {"F" "F-G+F+G-F" "G" "GG"} 4 120.0 4.0 [50 50] 0.0)
  )
