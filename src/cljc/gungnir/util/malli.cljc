(ns gungnir.util.malli
  (:require
   [malli.util :as mu]))

(defn update-children
  ([f schema] (update-children f schema {}))
  ([f schema options]
   (mu/transform-entries schema (fn [children] (mapv f children)) options)))

(defn update-child-properties [f [k m? & xs :as child]]
  (if (map? m?)
    (conj xs (f m?) k)
    child))
