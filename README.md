# nrt-sc

A Clojure library designed to ease the generation of non-realtime scores for
offline rendering by the SuperCollider server.

## Usage

You'll need to add the following dependency to your `project.clj` in order to
utilise this library:
```
[org.clojars.triss/nrt-sc "0.1.0-SNAPSHOT"]
```

[SuperCollider][1] requires to two types of files in order to render scores in
[non-realtime mode][2]: 

- `.scsyndef` files containing descriptions of the synths used by your score
- A `.osc` file containing details of how to manage memory (buffers/bus
  allocation) and when to instantiate and remove these synths from the server

[Overtone][3] provides facilities for generating `.scsyndef` files in
`overtone.sc.machinery.synthdef` via `synthdef-write`.

This library provides facilities for generating the required `.osc` score file.
Score's are specified as bundles of OSC message specs describing [SuperCollider
commands][4] in a map with keys representing the times they should be executed in
seconds. This should be familiar to users of SuperCollider's [Score][5] class.

The following example will create a sound file with 5 seconds of a 1000hz sine
wave in it:

```clojure
(ns offline.core
  (:use overtone.live)
  (:require [overtone.sc.machinery.synthdef :refer [synthdef-write]]
            [nrt-sc.core :as nrt]))

;;;; Define a test synth

(defsynth nrt-test
  [bus 0 freq 440 amp 0.1]
  (out bus (sin-osc freq 0 amp)))

;;;; Pull out a description of it's synthdef with :sdef and write it to a file

(synthdef-write (:sdef nrt-test) "/home/tris/nrt-test.scsyndef")

;;;; Create a score that starts a new group and instance of the test synth at 0
;;;; seconds, and frees both of them after 5.
;;;; When executed this 

(-> (nrt/score {0 [["/g_new" 1000 0 0]
                   ["/s_new" "nrt-test" 1001 0 1000 "freq" 1000]]
                5 [["/n_free" 1001 1000]]})
    (nrt/write-file "score.osc"))
```

This can then be rendered at the command line as follows:
```
scsynth -N score.osc _ 5-sec-sine.wav 44100 WAV int16 -o 1
```

## License

Copyright © 2016 Tristan Strange

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[1]: http://supercollider.github.io/
[2]: http://doc.sccode.org/Guides/Non-Realtime-Synthesis.html
[3]: http://overtone.github.io/
[4]: http://doc.sccode.org/Reference/Server-Command-Reference.html
[5]: http://doc.sccode.org/Classes/Score.html
