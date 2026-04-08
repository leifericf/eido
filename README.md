# Eido

**Eido — describe what you see**

Eido is a declarative, data-driven image language for Clojure.
Describe what you see as plain data — not drawing instructions.

<p align="center">
  <img src="https://eido.leifericf.com/images/galaxy.gif" width="200" alt="Particle galaxy" />
  <img src="https://eido.leifericf.com/images/op-art.gif" width="200" alt="Op art" />
  <img src="https://eido.leifericf.com/images/art-ink-landscape.png" width="200" alt="Ink landscape" />
</p>
<p align="center">
  <img src="https://eido.leifericf.com/images/tentacles.gif" width="200" alt="Rainbow tentacles" />
  <img src="https://eido.leifericf.com/images/spiral-grid.gif" width="200" alt="Spiral rainbow grid" />
  <img src="https://eido.leifericf.com/images/art-stained-glass.png" width="200" alt="Stained glass" />
</p>

**[Gallery](https://eido.leifericf.com/gallery/)** · **[Docs](https://eido.leifericf.com/docs/)** · **[API Reference](https://eido.leifericf.com/api/)**

## Design

This project has been on my mind since I discovered Clojure in 2020. As a graphics nerd, describing images as plain data — not issuing drawing commands — felt like the natural thing to build.

The approach is inspired by Christian Johansen's [Replicant](https://github.com/cjohansen/replicant), which showed how far a minimal, data-first approach to rendering can go. Eido applies that thinking to image generation — 2D shapes, 3D scenes, and animations alike.

- **Images are values.** A scene is a plain Clojure map — printable, serializable, diffable. Nothing opaque.
- **One function.** `render` takes a scene (or a sequence of scenes) and produces output. That's the API.
- **Description, not instruction.** You declare what the image contains; eido decides how to draw it.
- **Animations are sequences.** 60 frames = 60 maps in a vector. No timeline, no keyframes, no mutation.
- **Particle simulation.** Physics-based effects configured as data — emitters, forces, and lifetime curves.
- **Typography as paths.** Text compiled to vector paths — compatible with gradients, transforms, 3D extrusion.
- **Procedural fills.** Noise-driven, field-based, and programmatic fills described entirely as data — no shaders, no GPU.
- **No state, no framework.** Every function takes data and returns data. You bring your own workflow.
- **Zero dependencies.** Just Clojure and the standard library.

If it cannot be represented as plain data, it probably should not be in the library.

## Installation

Add eido as a git dependency in your `deps.edn`:

```clojure
io.github.leifericf/eido {:git/tag "v1.0.0-alpha7" :git/sha "375c717"}
```

## Quick Start

Requires Clojure 1.12+ and Java 11+.

```clojure
(require '[eido.core :as eido])

(eido/render
  {:image/size [400 400]
   :image/background [:color/rgb 245 243 238]
   :image/nodes
   [{:node/type :shape/circle
     :circle/center [200 200]
     :circle/radius 120
     :style/fill [:color/rgb 200 50 50]}]}
  {:output "circle.png"})
```

See the **[full documentation](https://eido.leifericf.com/docs/)** for shapes, text, colors, gradients, transforms, animation, particles, 3D, and more.

## Gallery

The **[gallery](https://eido.leifericf.com/gallery/)** showcases 62 examples across six categories — 2D scenes, 3D scenes, mixed 2D/3D, particles, typography, and artistic expression — each with source code.

All examples live in `examples/gallery/` as pure functions. Build the gallery locally:

```sh
clj -X:gallery   # renders all examples + generates site into _site/
```

## Running Tests

```sh
clj -X:test
```

## Status

v1.0.0-alpha7 — The API is still evolving and may change without notice.
