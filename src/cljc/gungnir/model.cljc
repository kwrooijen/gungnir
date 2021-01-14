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

;; TODO Might want to turn these into dynamic vars for performance benefits
(defonce models (atom {}))
(defonce table->model (atom {}))
(defonce model->table (atom {}))
(defonce field->column (atom {}))
(defonce column->field (atom {}))

(def ^:private optional-keys #{:virtual :primary-key :auto})

(defn- ->snake-keyword [s]
  (keyword (string/replace (name s) #"-" "_")))

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
  (if (:table (m/properties v))
    [k (mu/update-properties v update :table ->snake-keyword)]
    [k (mu/update-properties v assoc :table (->snake-keyword k))]))

(defn- add-model-key [[k v]]
  [k (mu/update-properties v assoc :model-key k)])

(defn- add-primary-key [[k v]]
  (let [primary-key (->> (m/children v)
                         (filter (comp :primary-key util.malli/child-properties))
                         (ffirst))]
    [k (mu/update-properties v assoc :primary-key primary-key)]))

(defn- map-kv [f m]
  (into {} (map f m)))

(defn- set-default-foreign-key [v n k]
  (assoc v :foreign-key (keyword (name n) (str (name k) "-id"))))

(defn- add-default-foreign-key* [p field kf]
  (update p field
          (partial map-kv
                   (fn [[k v]]
                     (if (:foreign-key v)
                       [k v]
                       [k (kf v)])))))

(defn- add-default-belongs-to
  [kk p]
  (add-default-foreign-key* p :belongs-to #(set-default-foreign-key % kk (:model %))))

(defn- add-default-has-many
  [kk p]
  (add-default-foreign-key* p :has-many #(set-default-foreign-key % (:model %) kk)))

(defn- add-default-has-one
  [kk p]
  (add-default-foreign-key* p :has-one #(set-default-foreign-key % (:model %) kk)))

(defn- add-foreign-keys [[k v]]
  (let [{:keys [belongs-to has-one has-many]} (m/properties v)]
    [k
     (cond-> v
       belongs-to (mu/update-properties (partial add-default-belongs-to k))
       has-one (mu/update-properties (partial add-default-has-one k))
       has-many (mu/update-properties (partial add-default-has-many k)))]))

(defn- register-table-names! []
  (doseq [[model opts] @models]
    (swap! table->model assoc (-> opts m/properties :table) model)
    (swap! model->table assoc model (-> opts m/properties :table))))

(defn- register-field-column-mappers! []
  (doseq [[table model fields] (map (juxt (comp :table m/properties second)
                                          first
                                          (comp m/children second))
                                    @models)
          [field] fields]
    (let [column (keyword (name table) (name field))]
      (swap! field->column assoc
             (keyword (name model) "*")
             (keyword (name table) "*"))
      (swap! field->column assoc field column)
      (swap! column->field assoc column field))))

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

(defmulti before-read (fn [k _v] (get @column->field k k)))

(defmethod before-read :string/lower-case [_ v]
  (string/lower-case v))

#?(:clj
   (defmethod before-read :uuid [_ v]
     (cond-> v
       (not (uuid? v)) java.util.UUID/fromString)))

(defmethod before-read :default [_ v] v)

(defmulti format-error (fn [k e] [k e]))

(defmethod format-error :default [_k e] e)

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
       (mapv add-model-key)
       (mapv update-table)
       (mapv (fn [[k v]] [k (mu/closed-schema v)]))
       (mapv update-children-add-optional)
       (mapv add-primary-key)
       (mapv add-foreign-keys)
       (into {})
       (swap! models merge))
  (register-table-names!)
  (register-field-column-mappers!))
