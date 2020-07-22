(ns gungnir.database.builder
  (:require
   [gungnir.field]
   [clojure.string :as string]
   [gungnir.model]
   [next.jdbc.result-set :as result-set])
  (:import (java.sql ResultSet)
           (org.postgresql.jdbc PgArray)))

(defn- ->kebab [s]
  (string/replace s #"_" "-"))

(defn- column-reader [builder ^ResultSet rs i]
  (let [column (nth (:cols builder) (dec i))
        after-read (:after-read (gungnir.field/properties column))]
    (when-let [value (.getObject rs i)]
      (if (seq after-read)
        (reduce (fn [v f] (gungnir.model/after-read f v))
                (if (#{PgArray} (type value)) (vec (.getArray value)) value)
                after-read)
        (result-set/read-column-by-index value (:rsmeta builder) i)))))

(defn kebab-map-builder [rs opts]
  (let [opts (assoc opts :qualifier-fn ->kebab :label-fn ->kebab)]
    (result-set/as-modified-maps rs opts)))

(def column-builder
  (result-set/builder-adapter
   kebab-map-builder
   column-reader))
