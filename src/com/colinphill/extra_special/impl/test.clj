(ns com.colinphill.extra-special.impl.test
  (:require
   [clojure.spec.alpha :as s]
   [clojure.test :as t]
   [clojure.test.check.generators :as-alias gen]
   [cognitect.anomalies :as anom]
   [com.colinphill.extra-special :as-alias es])
  (:import (java.util.concurrent ExecutionException)))


;; Declare aliases unconditionally to satisfy reader
(require '[expound.alpha :as-alias expound]
         '[orchestra.spec.test :as-alias o])
(when t/*load-tests*
  (require 'expound.alpha
           'orchestra.spec.test
           ;; Loaded for side effect of registering specs
           'speculative.instrument))
