# Eido

**Eido — describe what you see**

Eido is a declarative, EDN-based language for creating 2D images.

It treats images as **data**, not drawing instructions.

---

## Philosophy

Eido is built on a few core ideas:

- Images are **pure data (EDN)**
- Rendering is a **pure function**
- Composition is more important than primitives
- The REPL is your creative environment
- Focus is on **generative and procedural art**

---

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

---

## Core Concept

> Describe the image, not the steps to draw it.

---

## Status

Early-stage. Focused on defining a clean, minimal core language.
