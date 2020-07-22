(ns gungnir.core
  (:refer-clojure :exclude [keys cast])
  (:require
   #?(:clj [clojure.instant])
   [gungnir.spec]
   [gungnir.model]
   [malli.core :as m]
   [gungnir.util.malli :as util.malli]))

(defn get-child [model k]
  (reduce
   (fn [_ child]
     (when (#{k} (first child))
       (reduced child)))
   nil
   (m/children model)))

(defn apply-child? [child]
  (empty? (select-keys (util.malli/child-properties child) [:virtual :auto])))

(defn apply-keys [model]
  (keep
   (fn [child]
     (when (apply-child? child)
       (first child)))
   (m/children model)))

(defn belongs-to-key [k1 k2]
  (-> k2 gungnir.model/find m/properties :belongs-to (get k1)))

(defn column->model [column]
  (-> column
      (namespace)
      (keyword)
      (gungnir.model/find)))

(defn column->properties [column]
  (-> (column->model column)
      (get-child column)
      (util.malli/child-properties)))

(defn column->before-read [column]
  (-> (column->properties column)
      :before-read))
