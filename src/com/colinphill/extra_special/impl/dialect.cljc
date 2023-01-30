(ns com.colinphill.extra-special.impl.dialect
  (:import (clojure.lang MapEntry)))

(defn map-entry
  "Builds a map entry which supports `key` and `val`."
  [k v]
  (MapEntry. k v))
