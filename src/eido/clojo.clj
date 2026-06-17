(ns eido.clojo
  "Bridge to the Clojo native renderer.

  `render-edn` takes a graph in Clojo's EDN grammar and returns its encoded
  artifact bytes plus diagnostics, rendering in memory through a Zig boundary
  that imports the clojo module from the JVM (clj-zig, ADR 34). The boundary
  returns one owned byte buffer framing the status, dimensions, media type,
  diagnostics text, and payload; this namespace unframes it into a map.

  In development the clojo module resolves from the CLOJO_MODULE_ROOT override
  or a sibling checkout (../clojo/src/root.zig); a baked release carries the
  compiled boundary for every platform and needs no zig toolchain."
  (:require
    [clojure.java.io :as io]
    [clj-zig.core :refer [defnz zig-deps]])
  (:import
    [java.nio ByteBuffer ByteOrder]
    [java.util Arrays]))

(defn- clojo-module-root
  "Absolute path to clojo's module root: CLOJO_MODULE_ROOT, or the sibling
  checkout beside this repository."
  []
  (or (System/getenv "CLOJO_MODULE_ROOT")
      (.getAbsolutePath (io/file ".." "clojo" "src" "root.zig"))))

(zig-deps {:zig/modules {"clojo" {:path (clojo-module-root)}}})

;; The boundary packs clojo.render's result into one owned slice:
;; [status:u8][width:u32][height:u32][media-len:u32][diag-len:u32]
;; [media][diagnostics-text][payload], integers little-endian. A single
;; owned-bytes return keeps the FFM boundary to one copy and one free.
(defnz ^:private render-edn-raw
  [graphEdn [:slice :const :u8]
   baseDir  [:slice :const :u8]
   :ret [:bytes [:slice :u8]]]
  "const clojo = @import(\"clojo\");
   const alloc = std.heap.c_allocator;
   var threaded = std.Io.Threaded.init(alloc, .{});
   defer threaded.deinit();
   const cio = threaded.io();
   var r = clojo.render(alloc, cio, graphEdn, baseDir);
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
  "Render a graph in Clojo's EDN grammar to its encoded artifact. Returns
  {:status :width :height :media-type :diagnostics :bytes}: :status is one of
  :ok :invalid :no-output :out-of-memory; :diagnostics is the rendered text,
  empty when none; :bytes is the encoded artifact, empty unless :status is
  :ok. `base-dir` resolves asset paths and defaults to the current directory."
  ([graph-edn] (render-edn graph-edn "."))
  ([graph-edn base-dir]
   (let [framed    (render-edn-raw (.getBytes ^String graph-edn "UTF-8")
                                   (.getBytes ^String base-dir "UTF-8"))
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
