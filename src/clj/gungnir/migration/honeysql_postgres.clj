(ns gungnir.migration.honeysql-postgres
  (:require
   [honeysql.format :as sqlf]
   [honeysql-postgres.util :as util]
   [honeysql-postgres.format]
   [honeysql.helpers :as sqlh :refer [defhelper]]
   [ragtime.core]
   [ragtime.jdbc]
   [ragtime.reporter]
   [ragtime.strategy]))

;; TODO defhelpers can be removed once honeysql-postgres pull-request#55 is
;; merged https://github.com/nilenso/honeysql-postgres/pull/55
(defhelper create-extension [m extension-name]
  (assoc m :create-extension (sqlh/collify extension-name)))

(defmethod sqlf/format-clause :create-extension [[_ [ extension-name if-not-exists]] _]
  (str "CREATE EXTENSION "
       (when if-not-exists "IF NOT EXISTS ")
       (-> extension-name
           util/get-first
           sqlf/to-sql)))

(defhelper drop-extension [m extension-name]
  (assoc m :drop-extension (sqlh/collify extension-name)))

(defmethod sqlf/format-clause :drop-extension [[_ [extension-name]] _]
  (str "DROP EXTENSION "
       (-> extension-name
           util/get-first
           sqlf/to-sql)))
