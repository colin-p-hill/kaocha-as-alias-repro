(ns hooks.com.colinphill.extra-special
  #_(:require
     [clj-kondo.hooks-api :as api]))

#_(defn defer-fdef [{:keys [node]}]
    (let [[_fdef spec-name & args] (:children node)
          arg-pairs      (partition 2 args)
          sanitized-args (->> arg-pairs
                              (filter #(-> %
                                           first
                                           api/sexpr
                                           #{:clojure.core/deftest
                                             :clojure.core/spec}
                                           not))
                              (apply concat))]
      {:node (api/list-node (list* (api/token-node 'clojure.spec.alpha/fdef)
                                   spec-name
                                   sanitized-args))}))
