(ns gungnir.model
  (:refer-clojure :exclude [find keys])
  (:require
   [clojure.string :as string]
   [clojure.edn :as edn]
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
    (get @models ?model)))

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

(defmulti validator (fn [validator] validator))
(defmethod validator :default [k]
  (throw (ex-info "Unknown validator" {:validator k})))

(defmulti before-save (fn [k v] k))
(defmethod before-save :default [_ v] v)

(defmethod before-save :string/lower-case [_ v]
  (string/lower-case v))

(defmulti after-read (fn [k _v] k))
(defmethod after-read :default [_ v] v)

(defmethod after-read :edn/read-string [_ v]
  (if (vector? v)
    (mapv edn/read-string v)
    (edn/read-string v)))

(defmulti before-read (fn [k _v] k))

(defmethod before-read :string/lower-case [_ v]
  (string/lower-case v))

#?(:clj
   (defmethod before-read :uuid [_ v]
     (cond-> v
       (not (uuid? v)) java.util.UUID/fromString)))

(defmethod before-read :default [_ v] v)

(defmulti format-error (fn [k e] [k e]))

(defmethod format-error :default [k e] e)

(defmulti format-key (fn [k] k))

(defmethod format-key :default [k]
  (-> (name k)
      (string/replace #"-" " ")
      (string/capitalize)))

(s/fdef register!
  :args (s/cat :model-map (s/map-of simple-keyword? :gungnir/model))
  :ret nil?)
(defn register!
  "Adds the `model-map` to the current available models for Gungnir. You
  can add multiple models at once, or add new ones over time.

  The following format is accepted. Keys are the name of model, and
  the value should be a Malli `:map`

  ```clojure
  (gungnir.model/register!
   {:user [:map ,,,]
    :post [:map ,,,]
    :comment [:map ,,,]})
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
  :args (s/cat :k simple-keyword?)
  :ret (s/nilable :gungnir/model))
(defn find
  "Find a model by `key`. Returns `nil` if not found."
  [key]
  (get @models key))

(s/fdef primary-key
  :args (s/cat :?model :gungnir/model-or-key)
  :ret qualified-keyword?)
(defn primary-key
  "Get the primary-key of `?model`. `?model` can either be a keyword or
  a model."
  [?model]
  (let [model (?model->model ?model)]
    (:primary-key (m/properties model))))

(s/fdef table
  :args (s/cat :?model :gungnir/model-or-key)
  :ret simple-keyword?)
(defn table
  "Get the table of `?model`. `?model` can either be a keyword or
  a model."
  [?model]
  (let [model (?model->model ?model)]
    (:table (m/properties model))))

(s/fdef keys
  :args (s/cat :?model :gungnir/model-or-key)
  :ret (s/coll-of qualified-keyword?))
(defn keys
  "Get all keys from `model` as qualified-keywords."
  [?model]
  (->> (?model->model ?model)
       (m/children)
       (mapv first)))

(s/fdef child
  :args (s/cat :?model :gungnir/model-or-key :k qualified-keyword?)
  :ret (s/nilable :gungnir.model/field))
(defn child
  "Get the child `k` from `?model`. Returns `nil` if not found."
  [?model k]
  (->> (?model->model ?model)
       (m/children)
       (reduce (fn [_ child] (when (#{k} (first child))
                               (reduced child)))
               nil)))

(s/fdef properties
  :args (s/cat :?model :gungnir/model-or-key)
  :ret map?)
(defn properties
  "Get the properties of model `?model`."
  [?model]
  (m/properties (?model->model ?model)))

(s/fdef belongs-to-relation-table
  :args (s/cat :model-1-key simple-keyword?
               :model-2 :gungnir/model-or-key)
  :ret (s/nilable qualified-keyword?))
(defn belongs-to-relation-table
  "Get the foreign-key of `model-2`, where the foreign-key points to
  `model-1-key`. Return `nil` of not found."
  [model-1-key model-2]
  (-> model-2 properties :belongs-to (get model-1-key)))
