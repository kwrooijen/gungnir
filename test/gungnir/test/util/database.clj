(ns gungnir.test.util.database
  (:require
   [gungnir.database :refer [*datasource* make-datasource!]]
   [next.jdbc]))

(def ^:private datasource-opts-1
  {:adapter       "postgresql"
   :username      "postgres"
   :password      "postgres"
   :database-name "postgres"
   :server-name   "localhost"
   :port-number   9724})

(def datasource-opts-2
  {:adapter       "postgresql"
   :username      "postgres"
   :password      "postgres"
   :database-name "postgres"
   :server-name   "localhost"
   :port-number   9725})

(defn init!
  "Initialize the database connection for testing."
  []
  (make-datasource! datasource-opts-1))

(defn drop!
  "Clear the database from any rows in the database."
  ([] (drop! *datasource*))
  ([datasource]
   (next.jdbc/execute!
    datasource
    [(str
      "DROP TABLE IF EXISTS \"ragtime_migrations\";"
      "DROP TABLE IF EXISTS \"account\";"
      "DROP TABLE IF EXISTS \"snippet\";"
      "DROP TABLE IF EXISTS \"products\";"
      "DROP TABLE IF EXISTS \"document\";"
      "DROP TABLE IF EXISTS \"token\";"
      "DROP TABLE IF EXISTS \"comment\";"
      "DROP TABLE IF EXISTS \"post\";"
      "DROP TABLE IF EXISTS \"user\";")])))
