(ns eido.phane
  "Bridge to the Phane native renderer.

  `render-edn` takes a graph in Phane's EDN grammar and returns its encoded
  artifact bytes plus diagnostics, rendering in memory through a Zig boundary
  that imports the phane module from the JVM (clj-zig, ADR 34). The boundary
  returns one owned byte buffer framing the status, dimensions, media type,
  diagnostics text, and payload; this namespace unframes it into a map.

  In development the phane module resolves from the PHANE_MODULE_ROOT override
  or a sibling checkout (../phane/src/root.zig); a baked release carries the
  compiled boundary for every platform and needs no zig toolchain."
  (:require
    [clojure.java.io :as io]
    [clj-zig.core :refer [defnz zig-deps]]
    [eido.phane.translate :as translate])
  (:import
    [java.nio ByteBuffer ByteOrder]
    [java.nio.file Files]
    [java.nio.file.attribute FileAttribute]
    [java.util Arrays]))

;; The Phane grammar is frozen, so the module is pinned by commit: the pin is
;; the reproducible identity a consumer reuses to resolve the baked library
;; with no zig and no Phane source (clj-zig ADR 36). A local checkout supplies
;; the compile source for dev and bake; it is absent on a consumer. Bump the
;; sha deliberately when targeting a newer frozen Phane.
(def ^:private phane-sha "a2382ecf6024bc4ed6a89361f64651ce89796606")

(defn- phane-checkout
  "Absolute path to a local phane module root for compilation: from
  PHANE_MODULE_ROOT, or a sibling checkout when present. nil on a consumer
  that resolves the baked library instead of compiling."
  []
  (or (System/getenv "PHANE_MODULE_ROOT")
      (let [sibling (io/file ".." "phane" "src" "root.zig")]
        (when (.exists sibling) (.getAbsolutePath sibling)))))

(zig-deps {:zig/modules
           {"phane" (cond-> {:git/sha phane-sha :root "src/root.zig"}
                      (phane-checkout) (assoc :path (phane-checkout)))}})

;; The boundary packs phane.render's result into one owned slice:
;; [status:u8][width:u32][height:u32][media-len:u32][diag-len:u32]
;; [media][diagnostics-text][payload], integers little-endian. A single
;; owned-bytes return keeps the FFM boundary to one copy and one free.
(defnz ^:private render-edn-raw
  [graphEdn :string
   baseDir  :string
   :ret [:bytes [:slice :u8]]]
  "const phane = @import(\"phane\");
   const alloc = std.heap.c_allocator;
   var threaded = std.Io.Threaded.init(alloc, .{});
   defer threaded.deinit();
   const cio = threaded.io();
   var r = phane.render(alloc, cio, graphEdn, baseDir);
   defer r.deinit();
   const media = r.media_type;
   const diag = r.diagnostics_text;
   const payload = r.bytes;
   const out = alloc.alloc(u8, 17 + media.len + diag.len + payload.len) catch @panic(\"oom\");
   out[0] = @intFromEnum(r.status);
   std.mem.writeInt(u32, out[1..][0..4], r.width, .little);
   std.mem.writeInt(u32, out[5..][0..4], r.height, .little);
   std.mem.writeInt(u32, out[9..][0..4], @intCast(media.len), .little);
   std.mem.writeInt(u32, out[13..][0..4], @intCast(diag.len), .little);
   var o: usize = 17;
   @memcpy(out[o..][0..media.len], media);
   o += media.len;
   @memcpy(out[o..][0..diag.len], diag);
   o += diag.len;
   @memcpy(out[o..][0..payload.len], payload);
   return out;")

(def ^:private status->kw
  {0 :ok 1 :invalid 2 :no-output 3 :out-of-memory})

(defn- slice
  [^bytes buf off len]
  (Arrays/copyOfRange buf (int off) (int (+ off len))))

(defn render-edn
  "Render a graph in Phane's EDN grammar to its encoded artifact. Returns
  {:status :width :height :media-type :diagnostics :bytes}: :status is one of
  :ok :invalid :no-output :out-of-memory; :diagnostics is the rendered text,
  empty when none; :bytes is the encoded artifact, empty unless :status is
  :ok. `base-dir` resolves asset paths and defaults to the current directory."
  ([graph-edn] (render-edn graph-edn "."))
  ([graph-edn base-dir]
   (let [framed    (render-edn-raw graph-edn base-dir)
         bb        (.order (ByteBuffer/wrap framed) ByteOrder/LITTLE_ENDIAN)
         status    (bit-and (int (.get bb 0)) 0xFF)
         width     (.getInt bb 1)
         height    (.getInt bb 5)
         media-len (.getInt bb 9)
         diag-len  (.getInt bb 13)
         media-off 17
         diag-off  (+ media-off media-len)
         pay-off   (+ diag-off diag-len)]
     {:status      (status->kw status)
      :width       width
      :height      height
      :media-type  (String. ^bytes (slice framed media-off media-len) "UTF-8")
      :diagnostics (String. ^bytes (slice framed diag-off diag-len) "UTF-8")
      :bytes       (slice framed pay-off (- (alength ^bytes framed) pay-off))})))

(defn scene->graph
  "Wrap a Phane canvas scene in a minimal render-to-output graph: a
  `:canvas/render` node feeding an `:image/write` sink. The in-memory core
  captures the sink's bytes, writing no file; the path only fixes the output
  format (PNG by extension)."
  [scene]
  {:phane/version 1
   :graph/id      :eido
   :graph/nodes   {:art {:op/id        :canvas/render
                         :canvas/scene scene}
                   :out {:op/id       :image/write
                         :graph/input :art
                         :asset/path  "out.png"}}
   :graph/output  :out})

(defn graph->edn
  "Print a graph as EDN for the Phane reader. Namespace-map syntax
  (`#:image{...}`) is disabled: Phane's reader takes fully-qualified keys."
  [graph]
  (binding [*print-namespace-maps* false]
    (pr-str graph)))

(defn paint->graph
  "Wrap a Phane paint program in a `:paint/render` to `:image/write` graph."
  [program]
  {:phane/version 1
   :graph/id      :eido
   :graph/nodes   {:art {:op/id         :paint/render
                         :paint/program program}
                   :out {:op/id       :image/write
                         :graph/input :art
                         :asset/path  "out.png"}}
   :graph/output  :out})

(defn render-scene
  "Render a Phane canvas scene to its encoded artifact through the native
  backend. Returns the same map as `render-edn`."
  ([scene] (render-scene scene "."))
  ([scene base-dir]
   (render-edn (graph->edn (scene->graph scene)) base-dir)))

(defn render-eido
  "Translate an Eido scene to Phane grammar and render it through the native
  backend. A paint surface renders as a paint program, any other scene as a
  canvas. Returns the same map as `render-edn`."
  ([scene] (render-eido scene "."))
  ([scene base-dir]
   (let [{:keys [kind] :as t} (translate/translate scene)
         graph (case kind
                 :canvas (scene->graph (:scene t))
                 :paint  (paint->graph (:program t)))]
     (render-edn (graph->edn graph) base-dir))))

(defn- render-animation-framewise
  "Render each frame to a PNG, then assemble them with `gif/encode` (the
  host-rendered path). Only one frame is resident at a time during rendering,
  so this stays memory-bounded for large animations, and it carries paint
  surfaces (which the batched canvas path cannot). The proven default."
  [frames fps base-dir]
  (let [dir (.toFile (Files/createTempDirectory "eido-phane-anim"
                                                (make-array FileAttribute 0)))]
    (try
      (let [paths (vec (map-indexed
                         (fn [i frame]
                           (let [res (render-eido frame base-dir)]
                             (when (not= :ok (:status res))
                               (throw (ex-info "phane frame render failed"
                                               {:frame i :status (:status res)
                                                :diagnostics (:diagnostics res)})))
                             (let [p (.getPath (io/file dir (format "frame-%05d.png" i)))]
                               (with-open [o (io/output-stream p)]
                                 (.write o ^bytes (:bytes res)))
                               p)))
                         frames))
            graph {:phane/version 1
                   :graph/id      :eido-anim
                   :graph/nodes   {:out {:op/id      :gif/encode
                                         :gif/fps    fps
                                         :gif/frames paths
                                         :asset/path "anim.gif"}}
                   :graph/output  :out}]
        (render-edn (graph->edn graph) base-dir))
      (finally
        (doseq [f (.listFiles dir)] (.delete ^java.io.File f))
        (.delete dir)))))

(defn- canvas-scenes
  "Translate every frame to a Phane canvas scene, returning the vector of
  scenes, or nil if any frame is not a canvas (e.g. a paint surface) — which
  the batched `anim/sequence` path cannot carry."
  [frames]
  (reduce (fn [acc frame]
            (let [{:keys [kind scene]} (translate/translate frame)]
              (if (= :canvas kind) (conj acc scene) (reduced nil))))
          [] frames))

;; The batched path carries every frame's scene in one graph and holds all
;; frames resident to encode the GIF, so a large or complex animation peaks far
;; higher than rendering one frame at a time. These caps are deliberately
;; conservative: batch only short, modest animations, where the win (one call,
;; no temp files) is safe; everything larger renders frame by frame. The frame
;; count bounds scene-form complexity (trails, particles), the pixel volume the
;; resident frames.
(def ^:private batch-max-frames 32)
(def ^:private batch-max-pixels 8000000)

(defn- fits-batch?
  "True when the animation is small enough to batch safely."
  [frames scenes]
  (let [n     (count scenes)
        [w h] (:image/size (first frames) [0 0])]
    (and (<= n batch-max-frames)
         (<= (* (long n) (long w) (long h)) batch-max-pixels))))

(defn render-animation
  "Render an Eido animation — a sequence of frame scenes — to an animated GIF
  through the native backend. A short, modest animation renders in one batched
  call (`anim/sequence`: no temp files, no per-frame round-trip); a larger or
  more complex animation, or one whose frames are paint surfaces, falls back to
  a memory-bounded per-frame path. Returns the same map as `render-edn`, with an
  image/gif artifact."
  ([frames] (render-animation frames {}))
  ([frames {:keys [fps base-dir] :or {fps 12 base-dir "."}}]
   (let [scenes (canvas-scenes frames)]
     (if (and scenes (fits-batch? frames scenes))
       (render-edn (graph->edn {:phane/version 1
                                :graph/id      :eido-anim
                                :graph/nodes   {:out {:op/id       :anim/sequence
                                                      :anim/fps    fps
                                                      :anim/scenes scenes
                                                      :asset/path  "anim.gif"}}
                                :graph/output  :out})
                   base-dir)
       (render-animation-framewise frames fps base-dir)))))
