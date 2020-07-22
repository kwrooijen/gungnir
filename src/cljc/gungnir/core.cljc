(ns gungnir.core
  (:refer-clojure :exclude [keys cast])
  (:require
   #?(:clj [clojure.instant])
   [gungnir.spec]
   [gungnir.model]
   [malli.core :as m]
   [gungnir.util.malli :as util.malli]))

(defn apply-child? [child]
  (empty? (select-keys (util.malli/child-properties child) [:virtual :auto])))

(defn apply-keys [model]
  (keep
   (fn [child]
     (when (apply-child? child)
       (first child)))
   (m/children model)))
