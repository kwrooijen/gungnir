(ns gungnir.migration.honeysql-postgres
  (:require
   [clojure.string :as string]
   [honeysql.format :as sqlf :refer [format-clause]]
   [honeysql-postgres.util :as util]
   [honeysql-postgres.format]
   [honeysql.helpers :as sqlh :refer [defhelper]]
   [ragtime.core]
   [ragtime.jdbc]
   [ragtime.reporter]
   [ragtime.strategy]))

(def ^:private custom-additions
  {:create-extension 10
   :drop-extension 10
   :add-column* 30
   :drop-column* 30})

(defn override-default-clause-priority
  "Override the default cluse priority set by honeysql"
  []
  (doseq [[k v] custom-additions]
    (sqlf/register-clause! k v)))

;; TODO defhelpers can be removed once honeysql-postgres pull-request#55 is
;; merged https://github.com/nilenso/honeysql-postgres/pull/55
(defhelper create-extension [m extension-name]
  (assoc m :create-extension (sqlh/collify extension-name)))

(defmethod format-clause :create-extension [[_ [ extension-name if-not-exists]] _]
  (str "CREATE EXTENSION "
       (when if-not-exists "IF NOT EXISTS ")
       (-> extension-name
           util/get-first
           sqlf/to-sql)))

(defhelper drop-extension [m extension-name]
  (assoc m :drop-extension (sqlh/collify extension-name)))

(defmethod format-clause :drop-extension [[_ [extension-name]] _]
  (str "DROP EXTENSION "
       (-> extension-name
           util/get-first
           sqlf/to-sql)))

(defhelper add-column* [m fields]
  (update m :add-column* (fnil conj []) (sqlh/collify fields)))

(defmethod format-clause :add-column* [[_ fields] _]
  (->> fields
       (map #(str "ADD COLUMN " (sqlf/space-join (map sqlf/to-sql %))))
       (string/join ",\n")))

(defhelper drop-column* [m field]
  (update m :drop-column* concat (flatten field)))

(defmethod format-clause :drop-column* [[_ fields] _]
  (->> fields
       (map #(str "DROP COLUMN " (sqlf/to-sql %)))
       (string/join ",\n")))

(override-default-clause-priority)
