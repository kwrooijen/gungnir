(ns gungnir.util.malli
  (:require
   [gungnir.spec]
   [clojure.spec.alpha :as s]
   [malli.core :as m]
   [malli.util :as mu]))

(s/fdef update-childen
  :args (s/alt :arity-2 (s/cat :f fn? :schema m/schema?)
               :arity-3 (s/cat :f fn? :schema m/schema? :options map?))
  :ret m/schema?)
(defn update-children
  "Update all children of `schema` with function `f`. Uses the
  `malli.util/transform-entries` function. `options` can be passed as
  an argument for `malli.util/transform-entries`."
  ([f schema] (update-children f schema {}))
  ([f schema options]
   (mu/transform-entries schema (fn [children] (mapv f children)) options)))

(s/fdef update-child-properties
  :args  (s/cat :f fn? :child :gungnir.model/field)
  :ret :gungnir.model/field)
(defn update-child-properties
  "Apply the function `f` to all properties of `child`. If `child` has
  no properties then no modifications will be made."
  [f [k m? & xs :as child]]
  (if (map? m?)
    (conj xs (f m?) k)
    child))

(s/fdef child-properties
  :args  (s/cat :field :gungnir.model/field)
  :ret map?)
(defn child-properties
  "Get the property of a malli `:map` child. Or return an empty map."
  [[_k m?]]
  (if (map? m?) m? {}))
