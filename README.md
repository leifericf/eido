# Eido

**Eido — describe what you see**

Eido is a small, declarative language for creating 2D images using EDN.

## Philosophy
- Images are data
- Rendering is pure
- Composition over commands
- Focus on generative art

## Example
```clojure
{:eido/version "1.0"
 :image/size [800 600]
 :image/background [:color/rgb 245 243 238]
 :image/nodes
 [{:node/type :shape/circle
   :circle/center [400 300]
   :circle/radius 120
   :style/fill {:color [:color/rgb 200 50 50]}}]}
```

## Quick Start
```clojure
(eido/show! scene)
(eido/save! scene "out.png")
```
