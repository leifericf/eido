# Eido Unified Visual Computation IR
## Detailed design spec, rationale, and implementation context

Version: draft 1  
Audience: implementation-focused, intended to be handed to Claude Code  
Project: Eido  

---

## 1. Executive summary

This spec proposes a new **unified visual computation layer** inside Eido.

The goal is **not** to bolt a GPU-centric shader language onto Eido, and it is **not** to turn Eido into a game engine. The goal is to evolve Eido's existing data-driven design into a richer, more reusable, and more composable system for **offline generative art**.

In practical terms, this means introducing a deeper internal representation that preserves higher-level visual intent longer in the pipeline instead of expanding many features early into plain shapes, paths, groups, clips, buffers, and renderer-specific behavior.

The core idea is:

> Unify many currently separate feature families into a smaller set of general parts:
> **domains, resources, fields, transforms, materials/fills, effects, passes, and programs**.

This gives Eido:

- a more coherent internal model
- more cross-feature composition
- more reusable implementation pieces
- fewer one-off feature pipelines over time
- a better path for future backends
- a better artist experience through conceptual consistency

This is primarily an **architecture and capability upgrade**, not a promise of instant performance gains. It should reduce duplication and technical debt over time, but only if scope and abstraction boundaries stay disciplined.

---

## 2. Current Eido context

This section captures the current project context so the implementation work stays aligned with the actual codebase and project identity.

### 2.1 Project identity

Eido currently describes itself as a **declarative, data-driven image language for Clojure**, centered on the idea: **“describe what you see as plain data — not drawing instructions.”** Its README emphasizes that images are values, `render` is the API, animations are sequences of scene maps, typography is compiled to paths, and the library aims to avoid opaque state and framework-like machinery.

That identity must remain intact.

### 2.2 Current public API shape

The docs show a broad but coherent surface area already exists, grouped roughly into:

- Core: `eido.core`
- Drawing: `eido.path`, `eido.scene`
- Styling: `eido.color`, `eido.hatch`, `eido.palette`, `eido.stipple`, `eido.stroke`
- Effects: `eido.decorator`, `eido.distort`, `eido.flow`, `eido.morph`, `eido.warp`
- Generative: `eido.contour`, `eido.lsystem`, `eido.noise`, `eido.particle`, `eido.scatter`, `eido.vary`, `eido.voronoi`
- Animation: `eido.animate`
- 3D: `eido.scene3d`

This confirms that Eido already has many of the concepts we have been discussing. The proposal here is therefore **not** to add a whole new conceptual universe from scratch. It is to **unify and generalize** what is already present.

### 2.3 Current internal architecture

The current internal IR is intentionally small. In `src/eido/ir.clj`, the compiled scene representation consists mainly of leaf records such as:

- `RectOp`
- `CircleOp`
- `ArcOp`
- `LineOp`
- `EllipseOp`
- `PathOp`
- `BufferOp`

This is a very lean backend-oriented IR.

The compiler in `src/eido/compile.clj` currently performs a lot of **early expansion** from high-level concepts into plain nodes and ops. Examples visible in the source and docs include:

- hatch fill specs becoming line path nodes
- stipple fill specs becoming circle nodes
- text becoming path nodes
- path distortions being applied directly to path commands
- shadow/glow becoming filtered buffered groups
- pattern tiles compiling to lower-level ops
- 3D meshes projecting into 2D path groups

The docs reinforce this pattern:

- `hatch-fill->nodes` converts hatch fill specs into scene path nodes
- `stipple-fill->nodes` generates circle nodes
- `flow-field` returns path nodes
- `voronoi-cells` returns `:shape/path` nodes
- particle `render-frame` returns vectors of Eido node maps
- `render-mesh` in `scene3d` projects faces into 2D path nodes

So Eido already has rich features, but many of them ultimately become **shapes all the way down** fairly early.

### 2.4 What this means

Eido already has:

- fields, implicitly and explicitly
- procedural generation
- deformations
- simulations
- styling systems
- image/buffer effects
- offline animation workflows
- projected 3D

What it lacks is a **shared, deeper middle layer** that lets more of these concepts reuse one another before collapsing into backend-specific drawing operations.

---

## 3. Problem statement

The current architecture is effective and elegant, but it increasingly pushes many features into separate pipelines that each “bottom out” into concrete node expansion or renderer special cases.

That leads to several pressures:

### 3.1 Feature isolation

Many similar ideas are expressed through different APIs and implementation strategies:

- hatch vs stipple vs pattern
- noise vs contour vs flow vs scatter
- distort vs warp vs morph
- particles vs animation time vs procedural noise over time
- style systems vs generated geometry vs buffered image effects

These are artistically related, but not always represented through one common substrate.

### 3.2 Early loss of semantic intent

A hatch fill ceases to be “a hatch fill” quite early and becomes lines. A stipple fill becomes circles. Text becomes paths. Some image-like effects become buffer wrappers. This makes it harder to:

- optimize later
- retarget later
- reason about feature composition at a higher level
- share implementation logic across features

### 3.3 Growing maintenance surface

As new features arrive, there is a risk of adding more one-off expansion code and more feature-specific execution paths instead of consolidating concepts.

### 3.4 Reduced composability

When feature families are implemented as separate transformations into plain nodes, cross-feature composition becomes harder than it should be.

Examples of combinations that ought to feel native:

- hatch density driven by a field
- stipple radius driven by noise
- contour lines used as masks
- particles writing into a pass that a later effect consumes
- text clipping combined with procedural fills and post-processing
- shared time input across multiple systems

---

## 4. Vision

The vision is to evolve Eido into a **pure-data visual computation and rendering system for offline generative art**.

The important phrase is **visual computation**, not merely **shader language**.

This system should unify existing features into more general reusable parts while preserving Eido’s core philosophy:

- scenes are data
- images are values
- rendering remains centered on `render`
- the system stays offline-first
- artist-facing APIs remain friendly and declarative
- the internal core becomes more semantically expressive and composable

In short:

> Keep the surface artist-friendly.
> Make the middle layer much richer.
> Keep the backends pluggable.

---

## 5. Key decisions already made

This section records the design decisions and rationale accumulated through the discussion so they are explicit rather than implied.

### 5.1 Eido is offline-first

This is a foundational constraint.

We are **not** optimizing first for real-time interactivity, Vulkan conventions, or GPU execution models. We care first about:

- expressive offline rendering
- deterministic output
- composability
- pure-data authoring
- frame/image/animation generation
- future extensibility

GPU support is a possible future backend, not the current architectural center.

### 5.2 This is not primarily a “general shader language” project

We do **not** want to build a universal free-form shader language with:

- arbitrary mutable state
- full imperative control flow
- unbounded loops everywhere
- synchronization primitives as first-class public concepts
- backend-specific semantics in the core authoring model

That path would turn Eido into a compiler project first and an art system second.

Instead, we want a **domain-specific visual computation IR** that can later compile to shader languages where appropriate.

### 5.3 The public API should remain high-level and artist-friendly

We do **not** want artists to think in terms of:

- vertex shaders
- fragment shaders
- descriptor sets
- WGSL bindings
- HLSL registers
- Vulkan pipeline layouts

Instead, artists should continue thinking in terms of:

- fills
- masks
- materials
- fields
- generators
- transforms
- compositing
- passes
- animation/time

### 5.4 The system should preserve semantic intent longer

Instead of compiling many concepts immediately into ordinary shapes and path nodes, we want more features to survive longer as first-class semantic objects.

Examples:

- hatch as a fill/program/pattern, not only lines
- stipple as a density/distribution process, not only circles
- blur as an effect/pass, not only an immediate filtered buffer wrapper
- flow as a field/generator, not only path output
- projected 3D lighting as materials and lighting semantics where possible, not only face-shaded path output

### 5.5 The future GPU door should remain open, but passive

The middle layer should be designed so it can later lower to:

- WGSL
- HLSL
- GLSL
- SPIR-V-oriented backends

But this should happen because the IR is:

- typed
- pure
- resource-aware
- domain-aware
- pass-aware

—not because the public model is defined in GPU terms.

### 5.6 Java2D and SVG remain valid execution backends

This work must still pay off even if Java2D and SVG remain the only shipping backends for a while.

That means the value proposition is not “we can do shaders one day.” The value proposition is:

- more expressive combinations now
- less duplication now
- more reusable internals now
- better offline rendering structure now

### 5.7 Performance is secondary to architecture and expressiveness, but still important

This work is not expected to magically make everything faster.

Expected outcome:

- more core infrastructure code in the short term
- less duplicated feature code over time
- some workflows faster through reuse/caching/later optimization
- some new procedural/image effects slower if evaluated generically on CPU

The implementation must preserve a hybrid strategy:

- use native Java2D/SVG paths when they are a good fit
- use richer IR evaluation only where it adds meaningful capability

---

## 6. Goals

### 6.1 Primary goals

1. **Unify feature families** under a smaller set of general concepts.
2. **Increase composability** across styling, generation, transformation, simulation, and post-processing.
3. **Reduce conceptual duplication** in implementation.
4. **Preserve semantic intent longer** in the pipeline.
5. **Support richer offline generative workflows**.
6. **Keep artist-facing APIs approachable**.
7. **Keep future backend options open**.
8. **Reduce long-term technical debt and maintenance burden** by consolidating core concepts.

### 6.2 Secondary goals

1. Enable a typed middle layer for optimization and analysis.
2. Make multi-pass rendering explicit.
3. Make resources and intermediate images/buffers explicit.
4. Allow procedural fills/effects/materials to be represented as plain data.
5. Eventually allow partial lowering to GPU shader languages without changing the authoring model.

---

## 7. Non-goals

1. Do **not** replace all existing public helpers with a low-level DSL.
2. Do **not** expose GPU jargon as the default user experience.
3. Do **not** build a full general-purpose programming language inside Eido.
4. Do **not** force simple scenes through a heavyweight IR path if a direct path is simpler and faster.
5. Do **not** attempt a full real-time rendering engine as part of this work.
6. Do **not** require Vulkan/WebGPU support before this work is worthwhile.
7. Do **not** break Eido’s core value proposition of plain data + one `render` API.

---

## 8. Proposed conceptual model

The unification should be built around a small set of reusable core parts.

### 8.1 Domains

A **domain** is the space over which something is evaluated.

Examples:

- image grid
- shape-local UV space
- world/object space
- path parameter space
- particle collection
- point set
- mesh vertices
- mesh faces
- animation time

This gives us a common language for many existing features.

Examples of current Eido concepts that can map to domains:

- noise over image/space
- flow fields over bounds
- stipple distributions in a region
- contour generation over scalar fields
- 3D face shading over projected geometry
- particle updates over collections

### 8.2 Resources

A **resource** is an explicit input/output object used across passes and programs.

Examples:

- image
- buffer
- mask
- geometry
- mesh
- point set
- particle state
- palette
- field
- parameter block

Resources are important because they make multi-pass offline rendering explicit and later map naturally to GPU concepts if needed.

### 8.3 Fields

A **field** is a function over a domain that yields a value.

Examples:

- scalar density field
- vector flow field
- noise field
- distance field
- color field
- normal-like field
- thickness field
- opacity field

Many current Eido features already behave like fields, even when they are not represented that way internally.

### 8.4 Transforms

A **transform** maps a domain or resource into another form.

Examples:

- geometric warp
- path distortion
- UV transform
- time remap
- field remap
- color remap
- mask transform

Current examples already present in Eido include distort, warp, morph, and various path/scene transforms.

### 8.5 Materials / fills / appearances

A **material** or **fill** describes how something should look, not how it should be mechanically drawn.

Examples:

- solid fill
- gradient fill
- hatch fill
- stipple fill
- procedural fill
- lit surface material
- translucent material
- image-based fill

Current Eido already has many appearance concepts; the proposal is to unify them under a more general appearance model.

### 8.6 Effects

An **effect** describes image-space or layer-space processing.

Examples:

- blur
- posterize
- grain
- duotone
- halftone
- shadow
- glow
- threshold
- color matrix
- displacement

Current Eido already has effect-like behavior in decorators, filters, and buffered groups. This should become more explicit and general.

### 8.7 Passes

A **pass** is a unit of evaluation that reads resources, performs work, and writes resources.

Examples:

- draw geometry pass
- fill evaluation pass
- effect/filter pass
- procedural image generation pass
- particle update pass
- buffer composite pass

This is central for offline generative art because many interesting results come from layered pipelines, not single draw steps.

### 8.8 Programs

A **program** is a pure-data expression tree or structured kernel that computes values within a domain.

This is the closest thing in the design to a “shader language,” but it must remain:

- pure
- typed
- constrained
- declarative
- domain-oriented

Programs are **not** arbitrary host-language code blocks.

---

## 9. Architectural model

The architecture should be layered.

### 9.1 Layer A: authoring data

This is the existing Eido-friendly user-facing scene/data model.

Examples:

- shape nodes
- text nodes
- style maps
- group composition
- procedural helper outputs
- scene maps
- animation frames as scene sequences

This layer should remain welcoming and high-level.

### 9.2 Layer B: normalized semantic scene graph

Normalize public scene data into a more explicit semantic structure.

Responsibilities:

- resolve defaults
- normalize style/material/effect declarations
- resolve inheritance and group-level context
- preserve semantic constructs longer
- avoid immediate expansion to plain geometry unless truly necessary

### 9.3 Layer C: typed visual computation IR

This is the new core.

Responsibilities:

- typed values
- explicit domains
- explicit resources
- explicit passes
- explicit programs/expressions
- explicit materials/effects/fills
- backend-neutral semantics

This is the layer that unifies features.

### 9.4 Layer D: backend lowering

This layer turns the typed IR into execution plans for:

- Java2D
- SVG
- future CPU raster/image evaluators
- future WGSL/HLSL/GLSL backends

### 9.5 Layer E: execution

Actual rendering/export happens here.

This preserves the outward model:

- `render` still renders
- scenes are still data
- the public API does not need to expose the lower layers directly unless the user wants advanced control

---

## 10. Core IR specification

This section specifies the proposed middle layer.

The exact Clojure data representation can evolve, but the semantics should remain stable.

## 10.1 IR design principles

The IR must be:

- plain data
- typed
- immutable
- backend-neutral
- serializable
- deterministic
- composable
- smaller than the combined complexity of the features it replaces

## 10.2 Top-level IR container

Suggested shape:

```clojure
{:ir/version 1
 :ir/resources {...}
 :ir/passes    [...]
 :ir/outputs   {...}}
```

### 10.2.1 `:ir/resources`

Declares named resources used by passes.

Examples:

```clojure
{:framebuffer {:resource/kind :image
               :resource/size [1200 1200]
               :resource/color-space :srgb}

 :mask-1 {:resource/kind :mask
          :resource/size [1200 1200]}

 :particles {:resource/kind :particle-buffer
             :resource/schema {:position :vec2
                               :velocity :vec2
                               :life :float}}

 :noise-field {:resource/kind :field
               :resource/value-type :float}}
```

### 10.2.2 `:ir/passes`

A sequence of pass maps evaluated in order, with explicit resource reads/writes.

### 10.2.3 `:ir/outputs`

Names final outputs to export or hand to later stages.

---

## 10.3 Types

The IR should use a small explicit type system.

Minimum types:

- scalar: `:bool`, `:int`, `:float`
- vectors: `:vec2`, `:vec3`, `:vec4`
- matrices: `:mat3`, `:mat4`
- color: `:color`
- image: `:image`
- mask: `:mask`
- geometry: `:geometry`
- path commands: `:path`
- point set: `:points`
- particle buffer: `:particle-buffer`
- field: `:field/<value-type>`
- material/fill/effect descriptors as tagged data structures

This type system is primarily for internal correctness and later lowering.

---

## 10.4 Expressions and programs

Programs should be represented as pure data.

### 10.4.1 Expression forms

Use compact vector forms rather than verbose map AST nodes.

Examples:

```clojure
[:+ :a :b]
[:* :uv 4.0]
[:dot :n :l]
[:normalize :v]
[:sample :src :uv]
[:mix :a :b :t]
[:clamp :x 0.0 1.0]
[:vec4 :rgb 1.0]
[:swizzle :color :xyz]
[:select [:> :x 0.5] 1.0 0.0]
```

### 10.4.2 Allowed expression classes

- literals
- named parameter/resource references
- arithmetic
- vector/matrix math
- constructors
- swizzles/field access
- sampling
- interpolation/mixing
- conditionals via `:select`
- small domain-specific semantic forms

### 10.4.3 Semantic forms

High-level semantic forms are encouraged when they replace repetitive low-level logic.

Examples:

```clojure
[:field/noise {:scale 0.02 :seed 42} :pos]
[:field/distance-to-path :path :pos]
[:material/lambert {:normal :n :light-dir :l :base-color :base}]
[:effect/gaussian-blur {:src :img :radius 6}]
```

This keeps the IR focused on Eido’s problem space instead of pretending to be a raw shader ISA.

### 10.4.4 Program map

Suggested program shape:

```clojure
{:program/domain :image-grid
 :program/inputs {:src :image
                  :time :float
                  :seed :int}
 :program/output-type :vec4
 :program/body [:vec4
                [:* [:swizzle [:sample :src :uv] :xyz]
                    [:field/noise {:scale 0.02 :seed :seed} :uv]]
                1.0]}
```

### 10.4.5 Control flow discipline

Public programs should avoid free-form statement-heavy control flow.

Allowed early:

- `:select`
- small local bindings if useful
- reduction combinators where clearly semantic

Avoid early:

- mutation
- arbitrary loops
- raw memory model
- synchronization semantics

If iteration is needed, prefer **semantic transforms/reductions** rather than general loops.

Example:

```clojure
{:op/type :reduce-neighborhood
 :op/neighborhood {:kind :square :radius 3}
 :op/init [:vec4 0 0 0 0]
 :op/step [:+ :acc [:sample :src :item]]
 :op/final [:/ :acc [:neighbor-count]]}
```

---

## 10.5 Domains

Suggested domain kinds:

- `:image-grid`
- `:shape-local`
- `:path-param`
- `:world-2d`
- `:world-3d`
- `:mesh-vertices`
- `:mesh-faces`
- `:points`
- `:particles`
- `:timeline`

Examples:

```clojure
{:domain/kind :image-grid
 :domain/size [1200 1200]}
```

```clojure
{:domain/kind :particles
 :domain/source :particles}
```

---

## 10.6 Resources

Resources should be explicitly declared, referenced by name, and read/written by passes.

Suggested common resource kinds:

- `:image`
- `:mask`
- `:geometry`
- `:path`
- `:mesh`
- `:points`
- `:particle-buffer`
- `:field`
- `:parameter-block`

Resources may be transient or named depending on pass planning.

---

## 10.7 Passes

A pass is an explicit unit of work.

Suggested common pass families:

### 10.7.1 Draw pass

Draw geometry to an image target with a material/fill.

```clojure
{:pass/id :draw-main
 :pass/type :draw-geometry
 :pass/target :framebuffer
 :pass/geometry :shape-1
 :pass/material :material-1
 :pass/blend :source-over}
```

### 10.7.2 Effect pass

Read one or more images/masks and write another.

```clojure
{:pass/id :blur-mask
 :pass/type :effect
 :pass/read {:src :mask-1}
 :pass/write {:dst :mask-2}
 :pass/effect {:effect/type :gaussian-blur
               :effect/radius 8}}
```

### 10.7.3 Program pass

Evaluate a pure-data program over a domain into a target resource.

```clojure
{:pass/id :generate-noise
 :pass/type :program
 :pass/domain {:domain/kind :image-grid
               :domain/size [1200 1200]}
 :pass/read {:seed :seed-block}
 :pass/write {:dst :noise-image}
 :pass/program {...}}
```

### 10.7.4 Transform pass

Transform geometry, fields, or points.

```clojure
{:pass/id :warp-paths
 :pass/type :transform
 :pass/read {:src :path-set-1
             :field :warp-field}
 :pass/write {:dst :path-set-2}
 :pass/transform {:transform/type :path-warp
                  :transform/amount 0.35}}
```

### 10.7.5 Simulation/update pass

Useful for particles or other stateful offline iteration.

```clojure
{:pass/id :update-particles
 :pass/type :update
 :pass/domain {:domain/kind :particles
               :domain/source :particles-a}
 :pass/read {:src :particles-a}
 :pass/write {:dst :particles-b}
 :pass/program {...}}
```

---

## 10.8 Materials / fills / appearances

These should become first-class semantic objects.

### 10.8.1 Fill kinds

Suggested kinds:

- `:fill/solid`
- `:fill/gradient`
- `:fill/pattern`
- `:fill/hatch`
- `:fill/stipple`
- `:fill/image`
- `:fill/procedural`
- `:fill/material`

### 10.8.2 Example

```clojure
{:fill/type :fill/procedural
 :fill/domain :shape-local
 :fill/program
 {:program/inputs {:uv :vec2
                   :time :float}
  :program/output-type :color
  :program/body [:mix
                 [:color/rgb 255 100 0]
                 [:color/rgb 0 120 255]
                 [:field/noise {:scale 6.0 :seed 42} :uv]]}}
```

### 10.8.3 Hatch and stipple rationale

Hatch and stipple should remain valid public ideas. The change is that they become specializations of a more general appearance system rather than permanently separate pipelines.

Possible internal representations:

- hatch as line-pattern field + clipping/fill semantics
- stipple as density/distribution field + shape primitive semantics

This does **not** mean hatch/stipple must stop being able to lower to explicit geometry when that is the best backend strategy.

---

## 10.9 Effects

Effects should become first-class semantic descriptors.

Suggested effect families:

- blur
- sharpen
- posterize
- duotone
- halftone
- color-matrix
- grain
- threshold
- displacement
- glow
- shadow
- edge-detect
- palette-map

Example:

```clojure
{:effect/type :effect/glow
 :effect/color [:color/rgb 255 120 0]
 :effect/radius 12
 :effect/opacity 0.65}
```

These may lower to:

- direct Java2D path where possible
- buffered CPU image processing
- future shader code

---

## 10.10 Fields

Fields should become reusable descriptors and/or programs.

Suggested field families:

- noise field
- flow field
- distance field
- scalar mask field
- normal-like field
- density field
- thickness field
- palette lookup field

Example:

```clojure
{:field/type :field/noise
 :field/noise-type :perlin
 :field/scale 0.015
 :field/octaves 4
 :field/seed 42}
```

Fields should be usable by:

- fills
- generators
- transforms
- effects
- simulation/update passes

---

## 10.11 Generators

Generators should be representable as domain-to-resource or domain-to-geometry transformations.

Examples:

- contour from scalar field
- streamlines from vector field
- Voronoi cells from points
- scatter from density field
- stipple from density field
- L-system into path set

This unifies many current generative namespaces without deleting their high-level APIs.

---

## 11. Mapping current Eido features into the new model

This section is important because the proposal is primarily a **unification**, not a replacement.

## 11.1 Styling

### `eido.hatch`

Current role:

- converts hatch fill specs into line/path nodes

Future role:

- high-level wrapper over a generalized fill/pattern/program abstraction
- may still lower to geometry for Java2D/SVG
- should be representable semantically as hatch-like appearance data longer

### `eido.stipple`

Current role:

- Poisson disk and stipple node generation

Future role:

- wrapper over point-distribution/density-field fill/generator logic
- may still lower to circles/point geometry later

### `eido.palette`

Future role:

- should plug into materials, fills, fields, and effects as reusable palette maps

### `eido.stroke`

Future role:

- remains specialized, but can share deeper path/material/geometry semantics

## 11.2 Effects and transforms

### `eido.distort`, `eido.warp`, `eido.morph`

Current role:

- various geometry/path transformations

Future role:

- wrappers over a shared transform system
- common treatment of source domain, control field, transform semantics, and result resource

### `eido.decorator`

Current role:

- effect-like embellishments

Future role:

- should map into explicit effect/material/pass semantics where possible

## 11.3 Generative

### `eido.noise`

Future role:

- first-class field source

### `eido.flow`

Current role:

- streamlines from a noise-based flow field

Future role:

- generator consuming a vector field and producing geometry/path resources

### `eido.contour`

Future role:

- generator from scalar field to path resource

### `eido.scatter`

Future role:

- distribution/generator system over point sets and density fields

### `eido.voronoi`

Future role:

- generator from point resources to cell geometry resources

### `eido.vary`

Future role:

- parameter modulation layer that should be more uniformly applicable across materials, effects, generators, and transforms

### `eido.particle`

Future role:

- explicit simulation/update + render pipeline over particle resources
- should share time/domain/resource/pass semantics with the rest of the system

## 11.4 Animation

### `eido.animate`

Future role:

- time utilities remain useful, but time becomes a more universal explicit input across the IR

## 11.5 3D

### `eido.scene3d`

Current role:

- projected 3D scene helpers, mesh projection, simple diffuse lighting and culling

Future role:

- should gradually map into deeper material/light/pass abstractions where useful
- still may lower to projected 2D paths in current backends
- future GPU/offline advanced lighting support should build on this more semantic middle layer

---

## 12. Public API impact

The public API should change carefully.

## 12.1 What should remain stable

- scenes are plain maps
- `render` remains the main entry point
- existing high-level helpers remain valid where possible
- output modes remain offline-oriented: images, sequences, GIF, SVG

## 12.2 What may be added

Likely new advanced concepts:

- `:style/fill` forms for procedural/programmatic fills
- `:node/effects` or equivalent explicit effect declarations
- explicit reusable material/fill/effect descriptors
- pass/pipeline-style helper APIs for advanced users
- reusable field descriptors

## 12.3 What should not happen

Do **not** make users replace:

```clojure
(eido/render scene)
```

with a compiler-style workflow as the default.

The low-level machinery should remain mostly internal or optional.

---

## 13. Artist experience goals

From an artist’s perspective, the intended result is:

- more consistency across features
- more ways to combine features
- less need to learn separate mental models
- more reuse of style logic
- easier movement between static, animated, and procedural work
- no requirement to think in shader-stage terms

The desired user impression is:

> “The system has a more unified visual language.”

—not:

> “Now I have to write shaders.”

---

## 14. Performance expectations

This work should be justified primarily by **capability and architecture**, not by guaranteed immediate speedups.

## 14.1 Realistic performance outcomes

### Likely improved

- cases where unified passes and resources enable caching
- cases where repeated work can be shared
- cases where preserving semantic intent allows better lowering later
- cases where geometry explosion can be avoided

### Potentially worse

- generic CPU evaluation of image-space procedural programs
- per-pixel effects that bypass efficient Java2D-native capabilities
- overuse of the richer IR where a simpler direct path would be better

## 14.2 Required strategy

Use a hybrid execution model:

- direct Java2D/SVG lowering for simple, native-friendly operations
- richer IR evaluation for things that genuinely need it
- explicit intermediate resources for expensive offline passes
- caching/memoization for reusable program results and tiles where appropriate

---

## 15. Technical debt and maintenance expectations

The expectation is:

### Short term

- more core code
- more migration complexity
- more internal machinery

### Medium term

- partial payoff
- some duplicated logic disappears
- some features become thinner wrappers

### Long term

- less repeated one-off feature plumbing
- more central lowering/execution logic
- more coherent architecture
- lower maintenance burden if the IR stays disciplined

This only works if the IR remains smaller and clearer than the collection of special cases it replaces.

---

## 16. Keeping the GPU door open

This section is intentionally secondary, but important.

The typed visual computation IR should be designed so that some subsets can later lower to GPU shader languages.

That requires:

- explicit types
- explicit inputs/outputs
- explicit domains
- explicit resources
- pure programs
- no Java2D objects leaking into the middle layer
- no backend-specific syntax in the public source of truth

### 16.1 What belongs in the IR

- materials
- fills
- effects
- domains
- resources
- passes
- typed expressions/programs
- sampling/composition semantics

### 16.2 What does not belong in the core IR

- HLSL register assignments
- WGSL `@group` / `@binding`
- Vulkan descriptor set numbers as authoring concepts
- explicit shader stage structs as public API
- backend-only conventions as source-of-truth fields

### 16.3 Practical consequence

Later, some parts of the IR may lower to:

- WGSL for WebGPU
- HLSL or GLSL through later toolchains
- CPU evaluators for offline rendering

But this must remain a backend concern, not the core public model.

---

## 17. Implementation strategy

This should be phased, not revolutionary.

## 17.1 Phase 1: introduce the typed middle layer

Deliverables:

- typed IR container
- resources
- passes
- minimal program expression core
- fill/material/effect descriptors
- field descriptors

Do not migrate everything at once.

## 17.2 Phase 2: unify low-risk feature families

Recommended first targets:

- procedural fills
- hatch/stipple unification points
- explicit effect descriptors for existing image/buffer effects
- field abstraction for noise-driven features

## 17.3 Phase 3: route current feature helpers through the new core

Turn current APIs into thinner wrappers where practical.

Good candidates:

- hatch
- stipple
- flow
- contour
- scatter
- vary interactions with materials/effects

## 17.4 Phase 4: explicit pass pipelines

Add internal support for multi-pass graphs and intermediate resources.

Use this to consolidate:

- blur/glow/shadow behavior
- mask workflows
- composite chains
- generated images/resources

## 17.5 Phase 5: simulation/resource unification

Bring particles and related update/render workflows into the same pass/resource model.

## 17.6 Phase 6: optional future backend experiments

Only after the IR has proven itself in offline CPU/Java2D/SVG contexts should GPU lowering be explored.

---

## 18. Acceptance criteria

The design is successful if most of the following become true.

### 18.1 Architectural

- There is a real backend-neutral middle layer richer than the current leaf-op IR.
- More semantic intent survives deeper into the pipeline.
- Fewer features require bespoke expansion logic.
- More feature families share the same implementation pieces.

### 18.2 User-facing

- Existing `render`-centric workflows still feel natural.
- Artists can combine features more freely.
- New procedural fills/effects/materials feel like part of the same language.
- The API does not become obviously more compiler-like for ordinary usage.

### 18.3 Maintenance

- New feature additions increasingly look like data + lowering, not entirely new rendering subsystems.
- Similar concepts stop being reimplemented in isolated ways.

### 18.4 Performance

- Simple scenes still lower efficiently to Java2D/SVG.
- Expensive procedural/image workflows are explicit and manageable.
- Caching opportunities exist at the IR/pass/resource level.

---

## 19. Risks

### 19.1 Overgeneralization

If the IR becomes too abstract or too low-level, it will be harder to understand and maintain than the current feature-specific code.

### 19.2 Public API drift toward compiler complexity

If artist-facing APIs become too generic or too “programmable,” Eido may lose its appeal.

### 19.3 Performance regressions

If too many things are forced through generic CPU program evaluation instead of direct Java2D lowering, performance may suffer.

### 19.4 Scope explosion

If this becomes “build a full general shader language,” the project will likely lose focus.

---

## 20. Design guardrails

These should be treated as implementation guardrails.

1. **Prefer semantic forms over low-level instruction sets.**
2. **Prefer expressions over statements.**
3. **Prefer transforms/reductions over general loops.**
4. **Prefer fields/domains/resources/passes as the shared substrate.**
5. **Keep the public API high-level.**
6. **Keep direct backend lowering paths for simple cases.**
7. **Do not let Java2D/SVG implementation details leak into the core IR.**
8. **Do not let future GPU backend concerns dictate today’s public model.**
9. **Treat unification as the goal, not novelty.**
10. **The IR must stay smaller than the sum of the special cases it replaces.**

---

## 21. Concrete initial MVP recommendation

A realistic MVP should include only enough machinery to prove the architecture.

### 21.1 MVP scope

Implement:

- typed IR container
- image and geometry resources
- pass list
- minimal expression/program system
- procedural fill support
- explicit effect descriptors for a few existing effects
- field abstraction for noise and simple scalar/vector fields
- hatch/stipple integration points

### 21.2 Explicitly defer

Defer for later:

- arbitrary imperative kernels
- advanced mutable simulation semantics
- GPU code generation
- full 3D material system rewrite
- full path boolean unification
- text-system rewrite

### 21.3 Why this MVP

This is enough to validate the thesis:

- more composability
- better unification
- better architecture
- value even with Java2D/SVG only

without turning the project into a full compiler effort too early.

---

## 22. Final framing

The most accurate way to frame this work is:

> Eido should evolve from “high-level scene data compiled quickly into draw ops” toward “high-level scene data normalized into a richer visual computation IR, then lowered to backends.”

This is a **unification project**.

It is about turning many existing specific features into more general, reusable, composable parts while preserving Eido’s core identity as a pure-data, offline-first generative art system.

The goal is not to abandon shapes, paths, groups, buffers, or existing APIs. The goal is to make them **one possible lowering target** of a more expressive shared middle layer.

That should lead, over time, to:

- more possibilities and affordances
- greater creative freedom and artistic expression
- better cross-feature composition
- less duplicated logic
- a healthier long-term architecture
- a realistic path to future backends without rewriting the authoring model

---

## 23. Source context used for this spec

This spec was written in the context of the current Eido repository and docs, especially:

- GitHub README and repository structure
- `src/eido/ir.clj`
- `src/eido/compile.clj`
- API docs for `eido.core`, `eido.hatch`, `eido.stipple`, `eido.flow`, `eido.voronoi`, `eido.particle`, and `eido.scene3d`

Relevant URLs:

- https://github.com/leifericf/eido
- https://eido.leifericf.com/api/index.html
- https://github.com/leifericf/eido/blob/main/src/eido/ir.clj
- https://github.com/leifericf/eido/blob/main/src/eido/compile.clj
- https://github.com/leifericf/eido/blob/main/src/eido/render.clj
- https://github.com/leifericf/eido/blob/main/src/eido/core.clj

