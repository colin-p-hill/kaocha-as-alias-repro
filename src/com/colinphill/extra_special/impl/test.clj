(ns com.colinphill.extra-special.impl.test
  {:clj-kondo/config '{:linters {:aliased-namespace-var-usage {:level :off}}}}
  (:require
   [clojure.core.match :as m]
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


(when t/*load-tests*
  (defn- spec-assert-fixture
    "Test fixture that enables spec asserts."
    [f]
    (let [orig-check (s/check-asserts?)]
      (try
        (s/check-asserts true)
        (f)
        (finally
          (s/check-asserts orig-check)))))
  (defn- instrument-fixture
    "Test fixture that enables Orchestra instrumentation."
    [f]
    (let [instrumented (o/instrument)]
      (try
        (f)
        (finally
          (o/unstrument instrumented)))))
  (defn- augment-message
    "Adds Expound explanation to the message of a spec validation exception."
    [ex]
    (let [msg     (ex-message ex)
          data    (ex-data ex)
          explain (with-out-str (expound/printer data))
          cause   (ex-cause ex)
          new-ex  (ex-info (str msg \newline explain) data cause)
          trace   (.getStackTrace ex)]
      (.setStackTrace new-ex trace)
      new-ex))
  (defn- expound-report [f m]
    (if (= :error (:type m))
      (let [actual (:actual m)
            ed     (ex-data actual)]
        (if (::s/failure ed)
          (f (update m :actual augment-message))
          (f m)))
      (f m)))
  (defn- expound-fixture
    "Test fixture that sets Expound as the explain printer."
    [f]
    (binding [s/*explain-out* expound/printer
              t/report        (partial expound-report t/report)]
      (f))))

(defn prepare-ns!
  "Sets up fixtures if tests are enabled."
  []
  (when t/*load-tests*
    (t/use-fixtures :once
                    expound-fixture
                    instrument-fixture
                    spec-assert-fixture)))
