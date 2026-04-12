(ns ^{:stability :provisional} eido.paint.substrate
  "Substrate field evaluators for the paint engine.

  A substrate describes the surface the paint is applied onto —
  paper tooth, canvas weave, absorbency patterns. The substrate
  modulates how much paint deposits at each pixel."
  (:require
    [eido.gen.noise :as noise]))

;; --- substrate evaluation ---

(defn evaluate-tooth
  "Evaluates paper/canvas tooth at surface coordinates (sx, sy).
  Returns a value in [0, 1] where 1.0 = full deposition,
  values < 1.0 reduce deposition (paint skips the valleys).

  substrate-spec: {:substrate/tooth 0.4,
                   :substrate/grain {:field/type :field/noise ...}}
  Higher tooth = more paint skips valleys."
  ^double [substrate-spec ^double sx ^double sy]
  (if (nil? substrate-spec)
    1.0
    (let [tooth (double (get substrate-spec :substrate/tooth 0.0))]
      (if (<= tooth 0.0)
        1.0
        (let [;; Default grain: medium-scale fBm noise
              seed (get substrate-spec :substrate/seed 0)
              scale (double (get substrate-spec :substrate/scale 0.15))
              ;; Generate surface height from noise
              height (noise/fbm noise/perlin2d (* sx scale) (* sy scale)
                       {:octaves 3 :seed seed})
              ;; Normalize to [0, 1]
              normalized (* 0.5 (+ height 1.0))
              ;; Tooth threshold: below threshold, paint skips
              threshold tooth]
          (if (> normalized threshold)
            1.0
            ;; Partial deposition in valleys
            (/ normalized threshold)))))))

(defn evaluate-absorbency
  "Evaluates substrate absorbency at surface coordinates.
  Returns a multiplier in [0, 1] for how much paint the surface absorbs.
  Higher absorbency = more paint deposited."
  ^double [substrate-spec ^double sx ^double sy]
  (if (nil? substrate-spec)
    1.0
    (double (get substrate-spec :substrate/absorbency 1.0))))

(defn evaluate-substrate
  "Combined substrate evaluation.
  Returns a deposition multiplier in [0, 1] combining tooth and absorbency."
  ^double [substrate-spec ^double sx ^double sy]
  (if (nil? substrate-spec)
    1.0
    (* (evaluate-tooth substrate-spec sx sy)
       (evaluate-absorbency substrate-spec sx sy))))

(comment
  (evaluate-tooth {:substrate/tooth 0.4 :substrate/scale 0.15} 100.0 50.0)
  (evaluate-substrate {:substrate/tooth 0.3 :substrate/absorbency 0.8} 50.0 50.0))
