(ns gungnir.changeset
  (:refer-clojure :exclude [cast])
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as string]
   [differ.core :as differ]
   [gungnir.decode]
   [gungnir.model]
   [gungnir.spec]
   [gungnir.util.malli :as util.malli]
   [malli.core :as m]
   [malli.error :as me]))

(defn- validator->malli-fn [{:validator/keys [key message fn]}]
  [:fn {:error/message message
        :error/path [key]}
   fn])

(defn- ->malli-fns [model validators]
  (let [table (gungnir.model/table model)]
    (mapv (fn [validator]
            (validator->malli-fn (gungnir.model/validator table validator)))
          validators)))

(defn- auto-keys [model]
  (->> (m/children model)
       (filter (comp :auto util.malli/child-properties))
       (map first)))

(defn- remove-auto-keys [m model]
  (apply dissoc m (auto-keys model)))

(defn- virtual-keys [model]
  (->> (m/children model)
       (filter (comp :virtual util.malli/child-properties))
       (map first)))

(defn validate
  ([m model] (validate m model []))
  ([m model validators]
   (if (seq validators)
     (or (m/explain (reduce conj [:and model] (->malli-fns model validators)) m) m)
     (or (m/explain model m) m))))

(defn- remove-virtual-keys [m model]
  (apply dissoc m (virtual-keys model)))

(defn- map-select-keys [model f m]
  (-> (into {} (mapv f m))
      (select-keys (gungnir.model/keys model))))

(s/fdef changeset
  :args (s/alt :arity-1 (s/cat :params map?)
               :arity-2 (s/cat :?origin map?
                               :?params (s/or :origin map?
                                              :validators vector?))
               :arity-3 (s/cat :origin map?
                               :params map?
                               :validators (s/coll-of keyword?)))
  :ret :gungnir/changeset)
(defn changeset
  "Create a changeset to be inserted into the database.
  Changesets can either be used to create or update a row.


  `params` will update the record with the fields supplied. `params`
  must be cast to a proper model record. If no other map is supplied,
  the changeset will create a new row on `save!`.

  `origin` is a map of the original record to be updated. If an
  `origin` map is provided, and the `primary-key` is not nil, it will
  update the row on `save!`

  `validators` can also be supplied to perform extra checks on the
  entire resulting record.
  "
  ([params]
   (changeset {} params []))
  ([?origin ?params]
   (cond
     (map? ?params) (changeset ?origin ?params [])
     (vector? ?params) (changeset {} ?origin ?params)))
  ([origin params validators]
   (let [model-k (-> params clojure.core/keys first namespace keyword)
         model (gungnir.model/find model-k)
         sane-origin (gungnir.decode/advanced-decode-with-defaults model (select-keys origin (gungnir.model/keys model)))
         diff (-> (differ/diff sane-origin (gungnir.decode/advanced-decode model params))
                  (first)
                  (remove-auto-keys model))
         validated (validate (merge sane-origin diff) model validators)]
     {:changeset/model model
      :changeset/validators validators
      :changeset/diff diff
      :changeset/origin origin
      :changeset/sane-origin sane-origin
      :changeset/params params
      :changeset/result (remove-virtual-keys (or (:value validated) validated) model)
      :changeset/errors (me/humanize validated)})))

(s/fdef cast
  :args (s/cat :m map? :?model :gungnir/model-or-key)
  :ret (s/map-of qualified-keyword? any?))
(defn cast
  "Cast the map `m` to a record of `?model`. Any unknown keys will be
  discarded. Strings and keywords will be converted to
  qualified-keywords."
  [m ?model]
  (let [?model (if (keyword? ?model) (gungnir.model/find ?model) ?model)
        table (name (gungnir.model/table ?model))
        ->key (fn [k] (keyword table (-> (name k) (string/replace #"_" "-"))))]
    (map-select-keys ?model (fn [[k v]] [(->key k) v]) m)))
