(ns com.colinphill.extra-special.impl.extra-spectral
  (:require [com.colinphill.extra-special.impl.dialect :as dialect]
            [com.rpl.specter :as sr]))

(sr/defnav entry [tag]
           (select* [this structure next-fn]
                    (let [[k v] structure]
                      (if (= tag k)
                        (next-fn v)
                        sr/NONE)))
           (transform* [this structure next-fn]
                       (let [[k v] structure]
                         (if (= tag k)
                           (dialect/map-entry k (next-fn v))
                           structure))))
