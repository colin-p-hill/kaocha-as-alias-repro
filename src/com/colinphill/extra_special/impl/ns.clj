(ns com.colinphill.extra-special.impl.ns
  (:refer-clojure :exclude [require symbol])
  (:require [clojure.test :as t]
            [com.colinphill.extra-special.impl.test :as test]))

(test/prepare-ns!)

(t/deftest trivial-preloaded-test
  (t/is true))
