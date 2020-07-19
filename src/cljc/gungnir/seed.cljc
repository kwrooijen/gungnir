(ns gungnir.seed
  (:require
   [malli.generator :as mg]
   [gungnir.core :as gungnir]))

(defn optional-child? [child]
  (and (vector? child)
       (map? (second child))
       (seq (select-keys (second child) gungnir/optional-keys))))

(defn seed
  ([model] (seed model {}))
  ([model opts]
   (take
    (:count opts 1)
    (repeatedly
     #(mg/generate
       (reduce (fn [acc ?child]
                 (if (optional-child? ?child)
                   acc
                   (conj acc ?child)))
               []
               (gungnir/model model)))))))
