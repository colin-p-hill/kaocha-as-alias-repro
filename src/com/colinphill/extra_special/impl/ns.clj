(ns com.colinphill.extra-special.impl.ns
  (:require [clojure.test :as t]
            [com.colinphill.extra-special.impl.test :as test]))

(test/prepare-ns!)

(t/deftest trivial-preloaded-test
  (t/is true))
