(ns nrt-sc.core
  "Generates NRT scores for the SuperCollider server from maps of sequences of
  OSC messages specs.

  It aims to allow sequences of OSC Bundles to specified in a manner similar to
  SuperCollider's Score class.

  Keys in map represent time in seconds at which each OSC message will be
  interpreted.

  OSC message specs are sequence's with OSC path as first element followed by a
  sequence of arguments. Long and Double type arguments are automatically
  converted to int and float arguments."
  (:require [overtone.osc.util :refer [osc-msg-infer-types mk-osc-bundle]]
            [overtone.osc.encode :refer [osc-encode-msg encode-string]]
            [clojure.java.io :as io])
  (:import [java.nio ByteBuffer]))

(defn seconds->osc-time
  "OSC times are a bit crazy! Smallest unit of time very small."
  [seconds-n] (* seconds-n 4294967296.))

;;;; Lifted from overtone.osc.encode and modified

(defn encode-timetag
  "Encode timetag into buf. Overtone's implementation is dependant on network
  time and produces invalid times for SuperCollider score files. This doesn't."
  [buf timestamp]
  (.putLong buf (seconds->osc-time timestamp)))

(defn osc-encode-bundle
  "Encode bundle into buf."
  [buf bundle]
  (encode-string buf "#bundle")
  (encode-timetag buf (:timestamp bundle))
  (doseq [item (:items bundle)]
    (let [start-pos (.position buf)]
      (.putInt buf (int 0))
      (osc-encode-msg buf item)
      (let [end-pos (.position buf)]
        (.position buf start-pos)
        (.putInt buf (- end-pos start-pos 4))
        (.position buf end-pos))))
  buf)

;;;; Score map parsing

(defn use-sc-msg-types
  "SC doesn't respond to any messages that use Double or Long types. We convert
  to float or long if specified."
  [arg] (condp = (type arg)
          Double (float arg)
          Long (int arg)
          arg))

(defn make-msgs
  "Helper takes list of OSC message specs and converts them to OSC messages."
  [msgs] (map #(apply osc-msg-infer-types (map use-sc-msg-types %)) msgs))

(defn score
  "Return's list of OSC bundles from map of time/bundle contents value pairs."
  [m] (->> (vals m)
           (map make-msgs)
           (zipmap (keys m))
           (into (sorted-map))
           (map #(apply mk-osc-bundle %))))

(defn append-bundle-to-byte-buf
  "Adds an OSC bundle to a byte buffer. Prefixes each bundle with it's size."
  [^ByteBuffer bb bundle]
  (let [bundle-start (.position bb)]
    (doto bb
      (.putInt (int 0))
      (osc-encode-bundle bundle))
    (let [bundle-end (.position bb)]
      (doto bb
        (.position bundle-start)
        (.putInt (int (- bundle-end bundle-start 4)))
        (.position bundle-end)))))

(defn score->byte-buffer
  "Encodes score in byte buffer."
  ([score] (score->byte-buffer score 32768))
  ([score max-size] (reduce append-bundle-to-byte-buf
                            (ByteBuffer/allocate max-size)
                            score)))

(defn byte-buffer->byte-array
  "Take byte buffer contents and place in a byte-array."
  [^ByteBuffer byte-buffer]
  (let [length (.position byte-buffer)
        ba     (byte-array length)]
    (doto byte-buffer (.flip) (.get ba))
    ba))

(defn write-file
  "Write score to file at path."
  [score path]
  (with-open [file (io/output-stream (io/file path))]
    (.write file (-> score score->byte-buffer byte-buffer->byte-array))))

(comment
  ;; Score that produce 5 seconds of 1000hz sine wave
  (-> (score {0 [["/g_new" 1000 0 0]
                 ["/s_new" "NRTsine" 1001 0 1000 "freq" 1000]]
              5 [["/n_free" 1001 1000]]})
      (write-file "/home/tris/score.osc")))

