(ns gungnir.changeset
  (:refer-clojure :exclude [cast])
  (:require
   [malli.error :as me]
   [differ.core :as differ]
   [malli.core :as m]
   [gungnir.util.malli :as util.malli]
   [gungnir.model]
   [gungnir.decode]
   [clojure.string :as string]))

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

(defn changeset
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

(defn cast [m ?model]
  (let [?model (if (keyword? ?model) (gungnir.model/find ?model) ?model)
        table (name (gungnir.model/table ?model))
        ->key (fn [k] (keyword table (-> (name k) (string/replace #"_" "-"))))]
    (map-select-keys ?model (fn [[k v]] [(->key k) v]) m)))
