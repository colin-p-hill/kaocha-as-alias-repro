(ns com.colinphill.extra-special.impl.test.kaocha
  (:require
   [com.colinphill.extra-special.impl.ns :as ns]
   [com.colinphill.extra-special.impl.test :as-alias test]
   [kaocha.report :as report]))

(defmacro ^:private with-print-opts [opts & body]
  `(ns/rc lambdaisland.deep-diff2.puget.printer/with-options
          ~opts
          ~@body))
(defonce ^:private orig (get-method report/print-expr :default))
(defmethod report/print-expr :default
  [{:as m :keys [::test/diff]}]
  (if diff
    (with-print-opts {:map-coll-separator :line}
      (report/print-expression m))
    (orig m)))

(defn reporter
  "No-op. Only exists to cue Kaocha to load this namespace's `defmethod`s."
  [_]
  nil)
