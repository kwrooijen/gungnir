(ns gungnir.model
  (:refer-clojure :exclude [find])
  (:require
   [malli.util :as mu]
   [gungnir.util.malli :as util.malli]
   [malli.core :as m]
   [gungnir.spec]
   [clojure.spec.alpha :as s]))

(defonce models (atom {}))

(def ^:private optional-keys #{:virtual :primary-key :auto})

(defn- model? [?model]
  (or (vector? ?model)
      (m/schema? ?model)))

(defn- ?model->model [?model]
  (if (model? ?model)
    ?model
    (gungnir.model/find ?model)))

(defn- add-optional [properties]
  (if (seq (select-keys properties optional-keys))
    (assoc properties :optional true)
    properties))

(defn- update-children-add-optional [[k v]]
  [k (util.malli/update-children (partial util.malli/update-child-properties add-optional) v)])

(defn- update-table [[k v]]
  (if-not (:table (m/properties v))
    [k (mu/update-properties v assoc :table k)]
    [k v]))

(defn- add-primary-key [[k v]]
  (let [primary-key (->> (m/children v)
                         (filter (comp :primary-key util.malli/child-properties))
                         (ffirst))]
    [k (mu/update-properties v assoc :primary-key primary-key)]))

(s/fdef register!
  :args (s/cat :model-map (s/map-of
                           (s/and keyword?
                                  (comp not qualified-keyword?))
                           :gungnir/model))
  :ret nil?)
(defn register!
  "Adds the `model-map` to the current available models for Gungnir. You
  can add multiple models at once, or add new ones over time.

  The following format is accepted. Keys are the name of model, and
  the value should be a Malli `:map`
  ```clojure {:user [:map ,,,]
   :post [:map ,,,]
   :comment [:map ,,,]}
  ```
  "
  [model-map]
  (->> model-map
       (mapv update-table)
       (mapv update-children-add-optional)
       (mapv add-primary-key)
       (into {})
       (swap! models merge))
  nil)

(s/fdef find
  :args (s/cat :k keyword?)
  :ret (s/nilable :gungnir/model))
(defn find
  "Find a model by `key`. Returns `nil` if not found."
  [key]
  (get @models key))

(s/fdef primary-key
  :args (s/cat :?model (s/or :model-key keyword?
                             :model :gungnir/model))
  :ret qualified-keyword?)
(defn primary-key
  "Get the primary-key of `?model`. `?model` can either be a keyword or
  a model."
  [?model]
  (let [model (?model->model ?model)]
    (:primary-key (m/properties model))))
