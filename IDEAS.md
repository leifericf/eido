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

### Logic programming for generative art (`core.logic`)

Integrate `clojure.core.logic` to unlock a fundamentally different mode of generative art: **constraint-based generation**, where the artist defines what properties a valid output must satisfy, and a solver searches the possibility space.

**The conceptual tradition runs deep.** Across art history, constraints have functioned as creative engines — not limitations:

- **Oulipo** (1960s–present): treated formal constraints as generative methods. Perec wrote an entire novel without the letter E. Queneau retold one story 99 ways. Their thesis: *constraint liberates by eliminating the tyranny of infinite choice.* They called themselves "rats who build the labyrinth from which they plan to escape."
- **Sol LeWitt**: separated conception from execution — wall drawings are written instructions that anyone can execute. The *rule system* is the work.
- **Islamic geometric art**: five girih tile shapes with matching rules produce inexhaustible, quasi-crystalline patterns. Artisans discovered aperiodic tiling centuries before Penrose.
- **Mondrian / De Stijl**: only primary colors, only right angles, only horizontal and vertical lines. The constraint *is* the aesthetic — removing it destroys the art.
- **Vera Molnár**: rule systems let her "go where she wouldn't have dared go alone" — systematic variation of geometric parameters with controlled disorder.
- **Serialism**: 12-tone rows and total serialism applied logical constraints across multiple musical dimensions simultaneously, producing emergent complexity.
- **John Cage / Brian Eno**: randomness bounded by rules — structured chance as a generative method.
- **Bridget Riley**: optical paintings built from strict repetition rules with controlled parameter variation.

**In programming specifically:**

- Krishnamurti implemented Stiny's *shape grammars* in Prolog (1986) — one of the clearest direct uses of logic programming for visual generation.
- Christian Jendreiko teaches Prolog as "generative logic" in art & design programs (see: youtube.com/watch?v=tHh9zjNazz4).
- Adam Smith & Mike Mateas used Answer Set Programming (a Prolog descendant) for procedural game content generation (2011).
- WFC (Wave Function Collapse) was shown to be "constraint solving in the wild" (Karth & Smith, 2017) — arc consistency constraint propagation, the same mechanism underlying CLP solvers.
- CHORAL (Ebcioglu, 1986) harmonized Bach chorales using ~350 rules in a custom logic programming language.

**What core.logic provides:**

- Declarative constraint specification — describe *what* you want, not *how* to get it
- Search with backtracking — explore possibility spaces automatically
- CLP(FD) — finite domain constraint solving (integers, inequalities, distinctness)
- Relations that run backwards — given output properties, find inputs
- Unification — structural pattern matching

**Candidate generators (ordered by feasibility):**

1. **Graph coloring** — Take Voronoi cells or subdivision regions with their adjacency graph, assign colors from a palette such that no adjacent regions share a color. Classic CSP, composes directly with existing generators, the constraint IS the visual aesthetic. Supports balance constraints (max N of any color), fixed pins, chromatic number minimization. *Best "hello world" for the integration.*

2. **Constraint tiling** — Define tiles with labeled edges (Wang tiles, Truchet tiles). Place on a grid such that adjacent edges match. The tile set definition IS the artistic input — different tiles produce Islamic patterns, Truchet curves, quilting patterns, circuit-board aesthetics. CLP(FD) handles grids up to ~50×50. Excellent for plotter output.

3. **Celtic knot patterns** — Generate knotwork on a crossing grid. Each crossing is over or under; strands must alternate and form closed loops. The topological constraint (closed loops + alternation) is extremely hard imperatively but falls out naturally from backtracking search. Visually stunning, plotter-ready. Symmetry constraints (2-fold, 4-fold) as optional parameters.

4. **Constrained shape grammar** — Extend L-systems with constraints: grow but stay inside a frame, branch but don't self-intersect, maintain visual balance. core.logic searches derivation sequences where the geometry satisfies bounds. Most ambitious — requires bridging geometric predicates into logic goals.

**What NOT to build with core.logic:**

- WFC: implement directly with specialized data structures (bitsets, priority queues) — core.logic's overhead isn't worth it for the inner loop
- Continuous layout: CLP(FD) only handles integers; continuous placement needs different tools
- Euclidean rhythms: direct O(n) algorithm exists
- Inverse generation: impractical with real generators (noise, RNG)

**Architecture sketch:**

- Optional dependency behind a deps.edn alias (`:csp`)
- Namespaces: `eido.gen.tiling`, `eido.gen.coloring`, `eido.gen.knot`, `eido.gen.grammar`
- Each generator follows the existing two-phase pattern: `solve` (where core.logic runs) → `->nodes` (pure data transformation)
- Seeded determinism via domain ordering (shuffle FD variable options with seed)
- Conditional re-export through `eido.gen` so the main namespace works without the alias

**The thesis:** Authoring a constraint system is a fundamentally different creative act from writing an imperative generator. You define the space of valid outcomes and let the solver surprise you. This is the Oulipo principle made computational — building the labyrinth, then escaping from it.

---

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
