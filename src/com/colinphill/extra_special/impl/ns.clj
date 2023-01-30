(ns com.colinphill.extra-special.impl.ns
  (:refer-clojure :exclude [require symbol])
  (:require [clojure.core :as c]
            [clojure.test :as t]
            [clojure.walk :as walk]
            [com.colinphill.extra-special.impl.test :as test])
  (:import (clojure.lang Namespace)))

(test/prepare-ns!)

;; TODO: get rid of this forward declaration
(declare symbol)
(defmulti ->sym
  "Coerce into a symbol. Used to extend `symbol`."
  type :default ::default)
(defmethod ->sym ::default [x]
  (try
    (c/symbol x)
    (catch IllegalArgumentException _
      (c/symbol (str x)))))
(defmethod ->sym nil [_] nil)
(defmethod ->sym Namespace [x] (ns-name x))
(defmethod ->sym Class [^Class x] (-> x .getName symbol))

(defn- ->name [x]
  (if (string? x)
    x
    (str (->sym x))))

(defn symbol
  "Like `clojure.core/symbol`, but accepts more types."
  ([sym-name] (->sym sym-name))
  ([ns sym-name]
   (when sym-name
     (c/symbol (->name ns) (->name sym-name)))))

(defn- postwalk-when [f p form]
  (if (p form)
    (walk/walk (partial postwalk-when f p) f form)
    form))

(defn explicate-sym
  "Attaches a full namespace to `sym` if not already present.

  Expands aliases. Ignores special symbols and bindings in `env`."
  ([ns sym] (explicate-sym ns nil sym))
  ([ns env sym]
   (if (or (special-symbol? sym) (contains? env sym))
     sym
     (let [ns         (the-ns ns)
           sym-prefix (some-> sym namespace symbol)
           sym-name   (some-> sym name symbol)]
       (if sym-prefix
         (symbol (or (-> (ns-aliases ns)
                         (get sym-prefix))
                     sym-prefix)
                 sym-name)
         (or (-> (ns-map ns)
                 (get sym-name)
                 symbol)
             sym))))))
(when t/*load-tests*
  (def ^:private this-ns *ns*)
  ;; This namespace does not exist
  (c/require '[com.example.totally-unreal :as-alias unreal]))
(t/deftest explicate-sym-test
  (let [do-test (fn [exp sym msg]
                  (t/is (= exp (explicate-sym this-ns sym))
                        msg))]
    (do-test 'clojure.core/and 'and
             "simple symbol from referred ns")
    (do-test 'com.example.totally-unreal/foo 'unreal/foo
             "alias-qualified symbol from as-alias ns")
    (do-test 'clojure.core/or 'clojure.core/or
             "fully qualified symbol")
    (do-test 'def 'def "special form symbol")))

(defn- sexp? [x]
  ;; All `ISeq`s evaluate by calling their head, except empty ones.
  (and (seq? x) (seq x)))
(defn- quoted? [x]
  (and (sexp? x)
       (= 'quote (first x))))

(defmacro rc
  "Requiring Call. Call `sym`, requiring its namespace if necessary."
  [sym & args]
  (let [locals        (keys &env)
        form          (postwalk-when #(if (and (symbol? %)
                                               ;; TODO: Is this redundant?
                                               (not (special-symbol? %)))
                                        (explicate-sym *ns* &env %)
                                        %)
                                     #(not (quoted? %))
                                     (list* sym args))
        gensyms       (map gensym locals)
        local->gensym (zipmap locals gensyms)
        ;; TODO: This should probably ignore quoted forms
        form-sub      (walk/postwalk-replace local->gensym form)]
    `(do
       ;; Macro vars in call position expand too late. E.g., binding vectors
       ;; will cause compilation errors due to unresolved symbols. Therefore, we
       ;; require the namespace and then separately perform the call. We use
       ;; `requiring-resolve` anyway because, unlike `require`, it guarantees
       ;; thread safety.
       (requiring-resolve '~(first form))
       ;; Use `eval` to delay compilation until after `requiring-resolve` has
       ;; executed. This prevents "Unable to resolve symbol" errors, normally
       ;; avoided by using the return value of `requiring-resolve` in the call
       ;; position. Wrapping the form in a function lets us use local binding
       ;; symbols, which would fail to compile if handed directly to `eval`.
       ((eval '(fn [~@gensyms] ~form-sub))
        ~@locals))))
