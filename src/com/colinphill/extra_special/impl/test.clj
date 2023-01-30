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


(prepare-ns!)


(defn- sample-distinct [gen n]
  (sequence (comp (distinct)
                  (take n))
            ((requiring-resolve `gen/sample) gen (* 10 n))))

(defn calisthenics
  "Like `s/exercise`, but includes unform and ensures samples are distinct."
  ([pred] (calisthenics pred 10))
  ([pred n]
   (for [input (sample-distinct (s/gen pred) n)
         :let [conf (s/conform pred input)]]
     [input conf (s/unform pred conf)])))

(defn- deref-fallibly [fut timeout-ms]
  (let [sentinel (gensym)
        res      (deref fut timeout-ms sentinel)]
    (if (= res sentinel)
      (throw (ex-info "Execution timed out"
                      {::anom/category ::anom/interrupted}))
      res)))

(defn fail-fn-after
  "Calls `f`, but throws an exception after `timeout-ms` milliseconds."
  [^long timeout-ms f]
  (let [fut (future-call f)]
    (try
      (deref-fallibly fut timeout-ms)
      (catch ExecutionException e (throw (ex-cause e)))
      (finally
        (future-cancel fut)))))

(defmacro fail-after
  "Executes its `body` as `do`, but throws an exception after `n` milliseconds."
  [n & body]
  `(fail-fn-after ~n (fn [] ~@body)))

(defn test-pred
  "Runs tests for a pred."
  [pred]
  (doseq [[input _ unf] (t/is (fail-after 3000 (calisthenics pred))
                              "Generator produces valid values")]
    (t/is (= input unf)
          "`s/conform` and `s/unform` round-trip to an equal value")))

(defmacro assert-pattern
  "Validates that `x` matches `pattern`, as `core.match`."
  ([pattern x]
   `(assert-pattern ~pattern ~x nil))
  ([pattern x msg]
   `(try
      (let [result# ~x]
        (m/match result#
          ~pattern (t/do-report {:type     :pass
                                 :message  ~msg
                                 :expected '~pattern
                                 :actual   result#})
          :else (t/do-report {:type     :fail
                              :message  ~msg
                              :expected '~pattern
                              :actual   result#
                              ::diff    true})))
      (catch Throwable t#
        (t/do-report {:type     :error
                      :message  ~msg
                      :expected '~pattern
                      :actual   t#})))))

(defmacro assert-conform
  "Validates that `(s/conform pred x)` matches `pattern`, as `core.match`."
  ([pred pattern x]
   `(assert-conform ~pred ~pattern ~x nil))
  ([pred pattern x msg]
   `(assert-pattern [::es/conformation ~pattern]
                    ;; FIXME: Circular dependency.
                    (es/consplain ~pred ~x)
                    ~msg)))

(defmacro assert-explain
  "Validates that `(s/explain-data pred x)` matches `pattern`, as `core.match`."
  ([pred pattern x]
   `(assert-explain ~pred ~pattern ~x nil))
  ([pred pattern x msg]
   `(assert-pattern [::es/explanation ~pattern]
                    ;; FIXME: Circular dependency.
                    (es/consplain ~pred ~x)
                    ~msg)))
