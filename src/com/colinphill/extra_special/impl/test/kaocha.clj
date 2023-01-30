(ns com.colinphill.extra-special.impl.test.kaocha
  (:require
   [com.colinphill.extra-special.impl.ns :as ns]
   [com.colinphill.extra-special.impl.test :as-alias test]))

(defn reporter
  "No-op. Only exists to cue Kaocha to load this namespace's `defmethod`s."
  [_]
  nil)
