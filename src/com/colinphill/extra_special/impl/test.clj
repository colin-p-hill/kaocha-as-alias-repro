(ns com.colinphill.extra-special.impl.test
  (:require
   [clojure.test :as t]
   [com.colinphill.extra-special :as-alias es]))


;; Declare aliases unconditionally to satisfy reader
(require '[expound.alpha :as-alias expound])
(when t/*load-tests*
  (require 'expound.alpha))
