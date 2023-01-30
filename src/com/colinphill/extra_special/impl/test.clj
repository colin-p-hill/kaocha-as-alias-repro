(ns com.colinphill.extra-special.impl.test
  (:require
   [clojure.test :as t]
   [clojure.test.check.generators :as-alias gen]
   [com.colinphill.extra-special :as-alias es]))


;; Declare aliases unconditionally to satisfy reader
(require '[expound.alpha :as-alias expound]
         '[orchestra.spec.test :as-alias o])
(when t/*load-tests*
  (require 'expound.alpha
           'orchestra.spec.test
           ;; Loaded for side effect of registering specs
           'speculative.instrument))
