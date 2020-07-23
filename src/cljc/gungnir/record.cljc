(ns gungnir.record
  (:require
   [malli.core :as m]
   [clojure.spec.alpha :as s]
   [gungnir.model]))

(s/fdef model
  :args (s/cat :record map?)
  :ret (s/nilable :gungnir/model))
(defn model
  "Return the model`record`. If no model is found return nil."
  [record]
  (-> record
      (ffirst)
      (namespace)
      (keyword)
      (gungnir.model/find)))

(s/fdef table
  :args (s/cat :record map?)
  :ret simple-keyword?)
(defn table
  "Return the table name `record`."
  [record]
  (-> record
      (model)
      (m/properties)
      :table))

(s/fdef primary-key
  :args (s/cat :record map?)
  :ret (s/nilable any?))
(defn primary-key
  "Return the primary-key of `record`. If no primary-key is found return
  nil."
  [record]
  (->> (model record)
       (gungnir.model/primary-key)))

(s/fdef primary-key-value
  :args (s/cat :record map?)
  :ret (s/nilable any?))
(defn primary-key-value
  "Return the value of the primary-key of `record`. If no primary-key is
  found return nil."
  [record]
  (->> (primary-key record)
       (get record)))
