(ns hooks.com.colinphill.extra-special.impl.ns
  (:refer-clojure :exclude [require])
  (:require
   [clj-kondo.hooks-api :as api]))

#_(defn export-as [{:keys [node]}]
    (let [[_ old-sym new-sym] (:children node)]
      {:node (api/list-node
              (list (api/token-node 'clojure.core/def)
                    new-sym
                    (api/string-node "A complete sentence docstring.")
                    old-sym))}))

#_(defn rc [{:keys [node]}]
    (let [[_rc-sym sym & args] (:children node)]
      {:node (api/list-node (list* sym args))}))

(defn require [{:keys [node]}]
  (let [[_ & args] (:children node)
        quote-token (api/token-node 'quote)]
    {:node (api/list-node
            (list* (api/token-node 'clojure.core/require)
                   (map #(api/list-node (list quote-token %))
                        ;; Strictly speaking, we should include the map's values
                        ;; in each libspec, since that's what the real macro
                        ;; does. This is unlikely to ever be necessary, though.
                        (if (api/map-node? (first args))
                          (rest args)
                          args))))}))
