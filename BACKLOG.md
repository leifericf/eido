# Backlog

Ideas worth exploring but not yet scheduled.

## Scene archive with Datomic Local

Use Datomic Local as an optional, embedded database for versioning and querying scenes over time. Eido scenes are plain maps — naturally fit an immutable, value-oriented database.

**Why:** Artists accumulate experiments. Queryable history ("show me all scenes tagged :sunset") and time travel ("what did this look like last week?") are things they can't trivially build themselves. Fits the REPL workflow perfectly.

**Design constraints:**
- Must remain an optional dependency — core Eido stays zero-dep, purely Clojure/JVM
- Archive code lives in a separate source path (`src-archive/`) behind an `:archive` deps.edn alias
- Default classpath never sees Datomic classes
- Schema as EDN on classpath, following [noumenon](https://github.com/leifericf/noumenon) patterns

**Sketch:**
```clojure
(require '[eido.archive :as archive])
(def conn (archive/connect "~/.eido/archive"))

;; Save scene + metadata
(archive/save! conn scene {:seed 42 :tags [:geometric :sunset] :params params})

;; Query
(archive/find conn {:tags [:sunset]})

;; Retrieve and re-render at the REPL
(show (archive/load conn entity-id))

;; Time travel
(show (archive/load-at conn entity-id #inst "2026-04-08"))
```

**Schema:** Index metadata (seed, tags, name, edition, dimensions) as proper Datomic attributes. Store scene data as pr-str string (scenes can be large). Query metadata, get back scene values.

**Reference:** noumenon's `db.clj` and `schema.clj` for connection caching, schema-as-EDN, and Datomic Local setup patterns.

## llm.txt / machine discoverability

A structured text file at the project/site root that gives LLMs a summary of Eido: what it is, key concepts, namespace map, pointers to examples and API docs. Low effort, some value for AI-assisted discovery. Not art-facing — defer until core art features are complete.

## Onboarding-by-intent docs section

Add an "I want to..." section to the Guide linking artistic goals to recipes/examples:
- "plotter-friendly line work" → flow recipe + plotter SVG
- "painterly fields" → noise/flow + palette
- "geometric grids" → subdivide + circle packing
- "animated patterns" → animation + boids/CA
- "editions for a collection" → edition recipe + series

Docs-only, no code. Could be done alongside any release.
