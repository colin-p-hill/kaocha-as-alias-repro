(ns com.colinphill.extra-special.impl.ns
  (:refer-clojure :exclude [require symbol])
  (:require [clojure.core :as c]
            [clojure.core.specs.alpha :as core.specs]
            [clojure.pprint :as pp]
            [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [clojure.test :as t]
            [clojure.walk :as walk]
            [com.colinphill.extra-special.impl.dialect :as dialect]
            [com.colinphill.extra-special.impl.extra-spectral :as esr]
            [com.colinphill.extra-special.impl.test :as test]
            [com.rpl.specter :as sr])
  (:import (clojure.lang Namespace)
           (java.awt Toolkit)
           (java.awt.datatransfer Clipboard StringSelection)
           (java.io StringWriter)))

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

(def ^:private require-args (s/cat :defaults (s/? (s/keys))
                                   :libspecs (s/* ::core.specs/libspec)
                                   :flags (s/* keyword?)))
;; TODO: find a better home for these
#_{:clj-kondo/ignore [:unused-private-var]}
(defn- ->>prn [x]
  (prn x)
  x)
#_{:clj-kondo/ignore [:unused-private-var]}
(defn- prn-true [x]
  (prn x)
  true)
(s/fdef process-require-args
  :args (s/cat :args (s/spec require-args)))
(defn- process-require-args [args]
  (s/assert require-args args)
  ;; TODO: valid-> and valid->>, like some-> and some->>
  (let [conf     (s/conform require-args args)
        _        (when (s/invalid? conf)
                   (throw (ex-info (s/explain-str require-args args)
                                   (s/explain-data require-args args))))
        defaults (->> (:defaults conf)
                      (sr/transform (sr/must ::when) eval))]
    (->> conf
         ;; normalize all `my.namespace` to `[my.namespace]`
         (sr/transform [:libspecs
                        sr/ALL
                        (sr/selected? (esr/entry :lib))]
                       (fn [[_ lib]]
                         (dialect/map-entry :lib+opts {:lib lib})))
         ;; disable loading where `::when` is present and falsy
         (sr/transform [:libspecs
                        sr/ALL
                        (esr/entry :lib+opts)
                        :options
                        (sr/transformed (sr/must ::when) eval)
                        (sr/view (partial merge
                                          defaults))
                        #(not (::when % true))]
                       #(set/rename-keys % {:as :as-alias}))
         ;; remove requires with no alias and loading disabled
         (sr/setval [:libspecs
                     sr/ALL
                     (sr/selected? (esr/entry :lib+opts)
                                   :options
                                   #(not (::when % true))
                                   #(not-any? % [:as :as-alias]))]
                    sr/NONE)
         (sr/setval :defaults sr/NONE)
         (s/unform require-args)
         ;; TODO: conform/unform replacements that preserve coll type
         ;;       (via metadata where needed, e.g. for `cat`)
         (map #(if (seq? %) (vec %) %)))))
(comment
  (s/check-asserts true)
  (process-require-args '(my.ns))
  (process-require-args '([my.ns ::when false]))
  (process-require-args '([my.ns :as mn ::when false]))
  (process-require-args '([my.ns :as mn ::when (not true)]))
  (process-require-args '([my.ns :as-alias mn ::when false]))
  (process-require-args '({::when false} my.ns))
  (process-require-args '({::when (not true)} my.ns))
  (process-require-args (list {::when t/*load-tests*}
                              'clojure.core
                              ['clojure.test :as 't ::when (not t/*load-tests*)]
                              ['clojure.test.check :as-alias 'tc]
                              ['clojure.test.check.generators])))
(t/deftest process-require-args-test
  (let [normalize (fn [x]
                    (->> x
                         (s/conform require-args)
                         (walk/postwalk #(if (map? %)
                                           (into (sorted-map) %)
                                           %))
                         (s/unform require-args)))]
    (t/are [in exp] (= (normalize exp)
                       (normalize (process-require-args in)))
      ;; normalize to lib+opts
      '(my.ns)
      '([my.ns])
      ;; no-op
      '([my.ns])
      '([my.ns])
      ;; no-op
      '([my.ns :as mn])
      '([my.ns :as mn])
      ;; no-op
      '([my.ns :as-alias mn])
      '([my.ns :as-alias mn])
      ;; no-op
      '([my.ns ::when true])
      '([my.ns ::when true])
      ;; no-op
      '([my.ns :as mn ::when true])
      '([my.ns :as mn ::when true])
      ;; no-op
      '([my.ns :as-alias mn ::when true])
      '([my.ns :as-alias mn ::when true])
      ;; remove
      '([my.ns ::when false])
      '()
      ;; :as -> :as-alias
      '([my.ns :as mn ::when false])
      '([my.ns :as-alias mn ::when false])
      ;; no-op
      '([my.ns :as-alias mn ::when false])
      '([my.ns :as-alias mn ::when false])
      ;; no-op
      '([my.ns ::when :truthy])
      '([my.ns ::when :truthy])
      ;; no-op
      '([my.ns :as mn ::when :truthy])
      '([my.ns :as mn ::when :truthy])
      ;; no-op
      '([my.ns :as-alias mn ::when :truthy])
      '([my.ns :as-alias mn ::when :truthy])
      ;; remove
      '([my.ns ::when nil])
      '()
      ;; :as -> :as-alias
      '([my.ns :as mn ::when nil])
      '([my.ns :as-alias mn ::when nil])
      ;; no-op
      '([my.ns :as-alias mn ::when nil])
      '([my.ns :as-alias mn ::when nil])
      ;; apply default, :as -> :as-alias
      '({::when false} [my.ns :as mn])
      '([my.ns :as-alias mn ::when false])
      ;; apply default, remove
      '({::when false} my.ns)
      '()
      ;; eval ::when, remove
      '([my.ns ::when (not true)])
      '()
      ;; eval and apply default, remove
      '({::when (not true)} my.ns)
      '()
      )))
(comment
  ;; TODO: Move this to user file
  (def ^:private ^Clipboard sys-cb (.getSystemClipboard (Toolkit/getDefaultToolkit)))
  (defn set-cb! [x]
    (let [sw (StringWriter.)
          _  (if (string? x) x (pp/write x
                                         :stream sw
                                         :dispatch pp/code-dispatch))
          ss (StringSelection. (str sw))]
      (.setContents sys-cb ss ss)))
  (set-cb! :foo))
(defn ^:skip-wiki require-impl
  "Implementation detail."
  [args]
  (apply c/require (process-require-args args)))
(defmacro require
  "Like `clojure.core/require`, but with additional libspec options.

  Extra libspec options:
  - `::when` – When present and evaluates to a falsy value, replaces `:as` with
               `:as-alias`."
  [& args]
  `(require-impl '~args))
