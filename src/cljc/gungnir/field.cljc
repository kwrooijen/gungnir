(ns gungnir.field
  (:require
   [clojure.spec.alpha :as s]
   [gungnir.spec]
   [gungnir.model]
   [gungnir.util.malli :as util.malli]))

(defn- ?field->key [?field]
  (if (coll? ?field)
    (first ?field)
    ?field))

(s/fdef model
  :args (s/cat :field :gungnir.model/field-or-key)
  :ret (s/nilable :gungnir/model))
(defn model [?field]
  (-> (?field->key ?field)
      (namespace)
      (keyword)
      (gungnir.model/find)))

(s/fdef properties
  :args (s/cat :field :gungnir.model/field-or-key)
  :ret map?)
(defn properties [?field]
  (let [field-key (?field->key ?field)]
    (-> (model field-key)
        (gungnir.model/child field-key)
        (util.malli/child-properties))))

(s/fdef before-read
  :args (s/cat :field :gungnir.model/field-or-key)
  :ret (s/nilable (s/coll-of keyword?)))
(defn before-read [?field]
  (-> (properties ?field)
      :before-read))
