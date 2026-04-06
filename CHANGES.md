# Changes

## Unreleased

## v0.12.0 — Shapes & Styles

- Add `:shape/ellipse` primitive with independent x/y radii (`:ellipse/center`, `:ellipse/rx`, `:ellipse/ry`)
- Add `:shape/arc` primitive for partial ellipses (`:arc/start`, `:arc/extent`, `:arc/mode` — `:open`/`:chord`/`:pie`)
- Add `:shape/line` primitive for direct line segments (`:line/from`, `:line/to`)
- Add `:rect/corner-radius` for rounded rectangles
- Add `:quad-to` path command for quadratic bezier curves
- Add `:path/fill-rule` (`:even-odd`/`:non-zero`) for path contours and holes
- Add stroke `:cap` (`:butt`/`:round`/`:square`), `:join` (`:miter`/`:round`/`:bevel`), and `:dash` pattern support
- Add `:transform/shear-x` and `:transform/shear-y` for skew transforms
- Add `:color/hsb` and `:color/hsba` color spaces (hue/saturation/brightness)
- Add `:group/clip` for masking groups to a shape (rect, circle, ellipse, or path)
- Add `:antialias` render option for deliberately aliased output
- Add `eido.scene/polygon`, `eido.scene/triangle`, and `eido.scene/smooth-path` helpers
- Fix SVG renderer dropping color alpha channel — `rgba()` now emitted when alpha < 1.0
- Fix animated SVG SMIL timing so frames actually alternate instead of all appearing simultaneously

## v0.11.0 — Frames

- Add `eido.animate/frames` higher-order function for building frame sequences without boilerplate
- Update all README animation examples to use `anim/frames`
- Add inline example images throughout README
- Add gallery with advanced animated grid patterns (spiral, sine field, breathing wave)
- Add dancing bars and tentacles animations to gallery
- Add 7 advanced gallery examples (galaxy, pendulum wave, op art, Lissajous, cellular automaton, kaleidoscope, tree)
- Add fractal examples (blooming tree, Sierpinski triangle, Koch snowflake)

## v0.10.0 — Polish

- Move `->awt-color` to render namespace as private function (no longer public API)
- Add optional `:eido/version` key to scene spec (validates format `"X.Y"` when present)
- Add friendly error message for invalid version strings
- Expand GIF test coverage (no-loop, many frames, short delay, return value)
- Add edge case tests for single-frame animations and version key roundtrip
- Add REPL comment blocks to `spec.clj`, `validate.clj`, and `svg.clj`
- Update v1.0 spec document with full language coverage (all color formats, animation, export, API)
- Add `eido.color/rgb->hsl` to README API table
- Document animation options (`:loop`, `:prefix`) in README examples
- Update roadmap with phase completion status

## v0.9.0 — SVG

- Add `:scale` option to SVG rendering (multiplies width/height, preserves viewBox)
- Pass `:scale` through `render-to-svg` and `render-to-file` for SVG output
- Add animated SVG rendering with SMIL visibility toggling (`eido.svg/render-animated`)
- Add `eido.core/render-to-animated-svg` for exporting scene sequences as animated SVG files
- Add `eido.core/render-to-animated-svg-str` for getting animated SVG as a string

## v0.8.0 — Animation

- Add `eido.animate` namespace with temporal helpers: `progress`, `ping-pong`, `cycle-n`, `lerp`, `ease-in`, `ease-out`, `ease-in-out`, `stagger`
- Add `eido.core/render-animation` for exporting scene sequences to numbered PNG files
- Add `eido.gif` namespace for animated GIF encoding via Java ImageIO (no external dependencies)
- Add `eido.core/render-to-gif` for exporting scene sequences as animated GIFs
- Add `play` and `stop` functions in dev/user.clj for REPL animation preview

## v0.7.0 — Validation

- Add `eido.spec` namespace with clojure.spec definitions for the full scene structure
- Add specs for primitives, colors (RGB, RGBA, HSL, HSLA, hex), transforms, path commands, styles, nodes, and scenes
- Add recursive group validation via multi-spec
- Add `eido.validate` namespace with human-readable error translation
- Add `eido.core/validate` for checking scenes without rendering
- Integrate validation into `eido.core/render` (throws `ex-info` with `:errors` on invalid input)

## v0.6.0 — Export

- Add SVG backend (`eido.svg`) for pure IR-to-string vector rendering
- Add `eido.core/render-to-svg` for getting SVG strings directly
- Add multi-format raster export: JPEG (with quality), GIF, BMP
- Add render options: `:scale` for resolution multiplier, `:transparent-background`
- Add PNG DPI metadata support via `:dpi` option
- Add SVG transparency support
- Add `eido.core/render-batch` for rendering multiple scenes to files

## v0.5.0 — Color

- Add HSL and HSLA color format support
- Add hex color parsing (3, 4, 6, and 8-digit formats)
- Add RGB to HSL conversion (`eido.color/rgb->hsl`)
- Add color manipulation helpers: `lighten`, `darken`, `saturate`, `desaturate`, `rotate-hue`, `lerp`

## v0.4.0 — Workflow

- Add `eido.scene/grid` for generating nodes in a grid pattern
- Add `eido.scene/distribute` for distributing nodes along a line
- Add `eido.scene/radial` for distributing nodes around a circle
- Add `eido.core/read-scene` and `eido.core/render-file` for EDN file workflow
- Add file watching with auto-reload preview (`user/watch-file`)
- Add atom watching for live coding (`user/watch-scene`)
- Add `tap>` integration for rendering tapped scenes (`user/install-tap!`)

## v0.3.0 — Paths

- Add `:shape/path` node type with arbitrary shapes via path commands
- Path commands: `move-to`, `line-to`, `curve-to` (cubic bezier), `close`
- Add path compilation to IR
- Add path rendering via Java2D `GeneralPath`

## v0.2.0 — Composition

- Add `:group` node type for composing shapes
- Add style inheritance from parent groups to child nodes
- Add multiplicative opacity inheritance through the node tree
- Add transform accumulation (translate, rotate, scale) through groups
- Add transform application in the Java2D renderer

## v0.1.0 — Basic shapes

- Initial release
- Add `eido.core` with `render`, `render-to-file`
- Add `eido.compile` for scene-to-IR compilation with style resolution
- Add `eido.render` for Java2D raster rendering
- Add `eido.color` with `resolve-color` for RGB/RGBA color vectors
- Add `:shape/rect` and `:shape/circle` primitives
- Add `dev/user.clj` with `show` for REPL preview
