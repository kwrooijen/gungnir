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
(defn model
  "Get the model which belongs to `?field`. Return `nil` if not found."
  [?field]
  (-> (?field->key ?field)
      (namespace)
      (keyword)
      (gungnir.model/find)))

(s/fdef properties
  :args (s/cat :field :gungnir.model/field-or-key)
  :ret map?)
(defn properties
  "Get the properties of `?field`. Return an empty map if not found."
  [?field]
  (let [field-key (?field->key ?field)]
    (-> (model field-key)
        (gungnir.model/child field-key)
        (util.malli/child-properties))))

(s/fdef before-read
  :args (s/cat :field :gungnir.model/field-or-key)
  :ret (s/coll-of keyword?))
(defn before-read
  "Get the `:before-read` keywords from `?field`. Return an empty vector
  if not found."
  [?field]
  (-> (properties ?field)
      (get :before-read [])))

(s/fdef before-save
  :args (s/cat :field :gungnir.model/field-or-key)
  :ret (s/coll-of keyword?))
(defn before-save
  "Get the `:before-save` keywords from `?field`. Return an empty vector if not
  found."
  [?field]
  (-> (properties ?field)
      (get :before-save [])))
