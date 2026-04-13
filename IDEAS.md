# Ideas

Ideas under consideration for future Eido releases. This is a living document — nothing here is promised, but everything here is something we think would genuinely help artists make better work.

Organized by what matters to practicing generative and computational artists, grounded in published needs from the community.

---

---

## Exploration and iteration

### Visual seed browser (separate project)

A standalone GUI application for browsing seeds interactively — outside Eido's core REPL-driven workflow. This would be a separate project that consumes Eido as a library.

**Concept:**
- Desktop window with keyboard controls: arrow keys to step through seeds, 's' to bookmark, 'r' to render at full resolution
- Could be built with Swing, JavaFX, or a web UI served from a local server
- The REPL workflow (seed-grid + watch-scene + show) already covers this use case for REPL-comfortable users; a GUI would serve artists who prefer a more visual exploration tool

**Why separate:**
- Eido's core is a data-oriented library, not a GUI application
- A visual browser introduces state management and event handling that don't belong in the core
- Different release cadence and dependency profile (UI frameworks)

---

## Infrastructure

### Scene archive with queryable history

An optional, embedded database for versioning and querying scenes over time. Eido scenes are plain maps — a natural fit for an immutable, value-oriented database.

**Why:** Artists accumulate experiments over months and years. Queryable history ("show me everything tagged :sunset") and time travel ("what did this look like last week?") support the long-term practice that distinguishes serious generative work.

**Design constraints:**
- Must remain an optional dependency — core Eido stays zero-dep, purely Clojure/JVM
- Archive code in a separate source path behind an alias
- Default classpath never sees database classes

**Sketch:**
```clojure
;; Save scene + metadata
(archive/save! conn scene {:seed 42 :tags [:geometric :sunset] :params params})

;; Query past work
(archive/find conn {:tags [:sunset]})

;; Retrieve and re-render at the REPL
(show (archive/load conn entity-id))
```

---

## Outreach and tooling

### Dedicated website (separate repo)

Move the marketing/docs site out of this repo and GitHub Pages into a
purpose-built static site in its own repository.

**Why:**
- The current site is assembled by `examples/site/render.clj` and
  served from GitHub Pages — practical during early beta, but coupled
  to the library's build and CI cycle.
- A separate site repo lets the visual design, navigation, and content
  strategy iterate on their own schedule without triggering library
  CI or version bumps.
- The site could host richer content than static HTML allows —
  searchable API reference, live scene playground, MDX-style long-form
  articles — without bloating the library repo.

**Design constraints:**
- The library remains the source of truth for API docs and code
  examples; the site pulls from tagged library releases rather than
  tracking `main`.
- A small build hook in the library can continue to emit a machine-
  readable snapshot (docs scenes, gallery manifests, API index) that
  the site consumes.

### Visual editor for non-programmers (separate project)

A standalone GUI application that lets artists who don't write
Clojure build Eido scenes through a visual editor instead of authoring
EDN by hand.

**Concept:**
- Tree/outliner view of the scene graph — add shapes, groups, fills,
  generators with buttons, not keystrokes.
- Inspector panels for each node type with sliders, color pickers, and
  numeric fields. Changes preview in real time.
- Serializes to the same plain-data scene maps Eido already renders,
  so artwork made in the editor remains portable — export as `.edn`
  and render with `clj -X eido.core/render`.
- Could import existing scene files so REPL-driven artists hand off
  work to collaborators who prefer a visual workflow.

**Why separate:**
- Eido's core is a data-oriented library; a visual editor introduces
  UI state, event handling, and platform concerns that don't belong
  in the core.
- Independent release cadence and dependency profile (UI toolkit,
  bundling, code signing on macOS/Windows).
- Overlaps with the Visual Seed Browser idea above but aims at a
  wider audience — the seed browser assumes you've already written
  a scene function; this editor is for people who haven't.

**Design constraints:**
- Must round-trip: what the editor saves is exactly what a programmer
  would write by hand, so the two populations can share files.
- Features in the editor never gate features in the library — Eido
  stays usable without it.

### Content-addressed render cache for the docs/gallery build

Eido is deterministic: for a given scene map and library version, the
rendered bytes are fixed. Today the website build re-renders every
gallery and docs scene on every deploy, even when nothing about the
scene or the renderer changed — slow and wasteful.

**Concept:**
- Compute `hash = sha256(pr-str(scene) + eido-version)` for each scene
  before rendering.
- Maintain a small EDN manifest (e.g. `_site/images/.manifest.edn`)
  mapping `"scene-name.png" → hash`.
- Before rendering a scene: if the output file exists and its recorded
  hash matches the current hash, skip the render; otherwise render and
  update the manifest.
- In CI, wrap `_site/images/` (and the manifest) in an `actions/cache`
  step keyed on a coarse hash of `examples/site/render.clj`,
  `resources/eido/version.edn`, and `src/**/*.clj`. Cache hit = almost
  all scenes skip rendering; source change = only affected scenes
  rebuild.

**Design tradeoffs:**
- Hashing a `:frames` GIF spec hashes the full frame vector — a few ms
  per scene, but tiny next to the render cost being saved.
- The coarse CI cache key invalidates on any `src/**/*.clj` change,
  even unrelated ones. That's correct (safer than incorrect) but
  conservative. If in practice we see a lot of full rebuilds from
  unrelated edits, switch to hashing only the namespaces the render
  actually reaches.
- Content-addressed storage (`images/by-hash/<hash>.png` with named
  symlinks) is cleaner long-term but complicates Pages serving;
  the manifest approach is simpler and sufficient.

**Why hold off:**
- Current build is ~9 min on CI — slow, but not yet a release
  bottleneck. Implement when it starts blocking iteration, or when
  the gallery grows enough that the per-deploy cost is clearly felt.
- Must preserve a "force full rebuild" path for when the renderer
  changes in ways the version bump doesn't capture (e.g. a bug fix
  that shouldn't alter output but could) — likely a manifest-wipe
  CI input or a bump in an explicit `:cache-version` constant.
