(ns gungnir.core
  (:refer-clojure :exclude [keys cast])
  (:require
   #?(:clj [clojure.instant])
   [clojure.edn :as edn]
   [clojure.string :as string]
   [differ.core :as differ]
   [malli.core :as m]
   [malli.error :as me]
   [malli.transform :as mt]
   [malli.util :as mu]))

(defmulti model (fn [k] k))
(defmethod model :default [k]
  (throw (ex-info (str "Unknown model" k) {:model k})))

(defmulti validator (fn [model validator] [model validator]))
(defmethod validator :default [k]
  (throw (ex-info "Unknown validator" {:validator k})))

(defmulti before-save (fn [k v] k))
(defmethod before-save :default [_ v] v)

(defmethod before-save :string/lower-case [_ v]
  (string/lower-case v))

(defmulti before-read (fn [k v] k))
(defmethod before-read :default [_ v] v)

(defmethod before-read :edn/read-string [_ v]
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

(def optional-keys #{:virtual :primary-key :auto})

(defn- validator->malli-fn [{:validator/keys [key message fn]}]
  [:fn {:error/message message
        :error/path [key]}
   fn])

(defn table [model]
  (-> model m/properties :table))

(defn- ->malli-fns [model validators]
  (let [table (table model)]
    (mapv (fn [validator]
            (validator->malli-fn (gungnir.core/validator table validator)))
          validators)))

(defn child-properties [[k m?]]
  (if (map? m?) m? {}))

(defn get-child [model k]
  (reduce
   (fn [_ child]
     (when (#{k} (first child))
       (reduced child)))
   nil
   (m/children model)))

(defn update-children
  ([f schema] (update-children f schema {}))
  ([f schema options]
   (mu/transform-entries schema (fn [children] (mapv f children)) options)))

(defn update-child-properties [f [k m? & xs :as child]]
  (if (map? m?)
    (conj xs (f m?) k)
    child))

(defn keys [model]
  (mapv first (m/children model)))

(defn apply-child? [child]
  (empty? (select-keys (child-properties child) [:virtual :auto])))

(defn apply-keys [model]
  (keep
   (fn [child]
     (when (apply-child? child)
       (first child)))
   (m/children model)))

(defn map-select-keys [model f m]
  (-> (into {} (mapv f m))
      (select-keys (gungnir.core/keys model))))

(defn add-optional [properties]
  (if (seq (select-keys properties optional-keys))
    (assoc properties :optional true)
    properties))

(defn model-k->model [model-k]
  (-> (model model-k)
      (->> (update-children (partial update-child-properties add-optional)))
      (mu/update-properties assoc :table model-k)))

(defn cast [m ?model]
  (let [?model (if (keyword? ?model) (model-k->model ?model) ?model)
        table (name (table ?model))
        ->key (fn [k] (keyword table (-> (name k) (string/replace #"_" "-"))))]
    (map-select-keys ?model (fn [[k v]] [(->key k) v]) m)))

(defn validate
  ([m model] (validate m model []))
  ([m model validators]
   (if (seq validators)
     (or (m/explain (reduce conj [:and model] (->malli-fns model validators)) m) m)
     (or (m/explain model m) m))))

(def +uuid-decoders+
  {'uuid? (fn [x]
            (cond-> x
              (string? x) #?(:clj java.util.UUID/fromString
                             :cljs uuid)))})
(def +date-decoders+
  {'inst? (fn [x]
            (cond-> x
              (string? x) #?(:clj clojure.instant/read-instant-date
                             :cljs identity)))})

(defn uuid-transformer []
  (mt/transformer
   {:name :uuid-decoder
    :decoders +uuid-decoders+}))

(defn date-transformer []
  (mt/transformer
   {:name :date-decoder
    :decoders +date-decoders+}))

(defn advanced-decode-with-defaults [model params]
  (m/decode model params
            (mt/transformer
             mt/default-value-transformer
             mt/string-transformer
             uuid-transformer
             date-transformer)))

(defn advanced-decode [model params]
  (m/decode model params
            (mt/transformer
             mt/string-transformer
             uuid-transformer
             date-transformer)))

(defn changeset
  ([params]
   (changeset {} params []))
  ([?origin ?params]
   (cond
     (map? ?params) (changeset ?origin ?params [])
     (vector? ?params) (changeset {} ?origin ?params)))
  ([origin params validators]
   (let [model-k (-> params clojure.core/keys first namespace keyword)
         model (model-k->model model-k)
         origin (advanced-decode-with-defaults model (select-keys origin (gungnir.core/keys model)))
         diff (first (differ/diff origin (advanced-decode model params)))
         validated (validate (merge origin diff) model validators)]
     {:changeset/model model
      :changeset/validators validators
      :changeset/diff diff
      :changeset/origin origin
      :changeset/params params
      :changeset/result (or (:value validated) validated)
      :changeset/errors (me/humanize validated)})))

(defn primary-key [model]
  (let [model (if (or (vector? model)
                      (m/schema? model))
                model
                (model-k->model model))]
    (reduce (fn [_ child]
              (when (-> child child-properties :primary-key)
                (reduced (first child))))
            nil
            (m/children model))))

(defn record->table [record]
  (-> record
   (ffirst)
   (namespace)
   (keyword)))

(defn record->model [record]
  (-> record
      (record->table)
      (model-k->model)))

(defn record->primary-key [record]
  (-> (record->model record)
      (primary-key)))

(defn belongs-to-key [k1 k2]
  (-> k2 model-k->model m/properties :belongs-to (get k1)))

(defn column->model [column]
  (-> column
      namespace
      keyword
      model-k->model))

(defn column->properties [column]
  (-> (column->model column)
      (get-child column)
      (child-properties)))

(defn column->before-read [column]
  (-> (column->properties column)
      :before-read))
