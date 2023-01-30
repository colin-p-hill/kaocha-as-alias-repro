(ns com.colinphill.extra-special.impl.ns
  (:refer-clojure :exclude [require symbol])
  (:require [clojure.core :as c]
            [clojure.test :as t]
            [clojure.walk :as walk]
            [com.colinphill.extra-special.impl.test :as test])
  (:import (clojure.lang Namespace)))

(test/prepare-ns!)

(t/deftest trivial-preloaded-test
  (t/is true))
