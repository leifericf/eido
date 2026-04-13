# Eido

**An end-to-end Clojure toolkit for generative artists — from REPL sketch to screen, print, and plotter.**

Eido is a declarative, data-first library for generative art. You
describe images as plain data — maps, vectors, keywords — and render
the same scene to whatever medium you're working in: raster PNG,
animated GIF or SVG, archival TIFF for print, or stroke-only SVG for
pen plotters and CNC.

Eido is an art tool, not a charting library — it was designed around
the practice of generative artists, plotter artists, and edition
makers. Data visualization is possible, but a dedicated charting
library will serve you better there.

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
- **3D sculpting pipeline.** Composable mesh→mesh operations: deform, extrude, subdivide, mirror — all pure data, all chainable via `->`.
- **2D↔3D bridge.** The same field/noise/palette vocabulary works in both dimensions. UV-mapped procedural textures, normal maps, and specular maps connect 2D generative tools to 3D surfaces.
- **Non-photorealistic rendering.** Hatch and stipple patterns from 2D applied to 3D faces with lighting-driven density.
- **Particle simulation.** Physics-based effects configured as data — emitters, forces, and lifetime curves.
- **Typography as paths.** Text compiled to vector paths — compatible with gradients, transforms, 3D extrusion.
- **Procedural fills.** Noise-driven, field-based, and programmatic fills described entirely as data — no shaders, no GPU.
- **Semantic IR.** A rich middle layer preserves visual intent — fills, effects, transforms, generators — before lowering to drawing instructions.
- **No state, no framework.** Every function takes data and returns data. You bring your own workflow.
- **Zero dependencies.** Just Clojure and the standard library.

If it cannot be represented as plain data, it probably should not be in the library.

## Installation

See the **[Getting Started guide](https://eido.leifericf.com/guide/)** on the website.

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

The **[gallery](https://eido.leifericf.com/gallery/)** showcases examples across several categories — 2D scenes, 3D scenes, mixed 2D/3D, particles, typography, and artistic expression — each with source code.

All examples live in `examples/gallery/` as pure functions. Build the gallery locally:

```sh
clj -X:gallery   # renders all examples + generates site into _site/
```

## Running Tests

```sh
clj -X:test
```

## Status

**Beta** — The core API is stabilizing. Breaking changes may still occur between releases, but the fundamentals are in place.
