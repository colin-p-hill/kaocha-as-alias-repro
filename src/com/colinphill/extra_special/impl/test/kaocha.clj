(ns com.colinphill.extra-special.impl.test.kaocha
  (:require
   [com.colinphill.extra-special.impl.ns]))

(defn reporter
  "No-op. Only exists to cue Kaocha to load this namespace's `defmethod`s."
  [_]
  nil)
