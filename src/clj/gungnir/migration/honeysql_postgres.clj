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

(def ^:private postgres-clause-priorities
  "Determines the order that clauses will be placed within generated SQL"
  (merge {} custom-additions))

(defn override-default-clause-priority
  "Override the default cluse priority set by honeysql"
  []
  (doseq [[k v] postgres-clause-priorities]
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
  (string/join ",\n"
               (for [field fields]
                 (str "ADD COLUMN "
                      (->> field
                           (map sqlf/to-sql)
                           sqlf/space-join)))))

(defhelper drop-column* [m fields]
  (update m :drop-column* (fnil conj []) (sqlh/collify fields)))


(defmethod format-clause :drop-column* [[_ fields] _]
  (string/join ",\n"
               (for [field fields]
                 (str "DROP COLUMN "
                      (->> field
                           (map sqlf/to-sql)
                           sqlf/space-join)))))

(override-default-clause-priority)
