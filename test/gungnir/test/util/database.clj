(ns gungnir.test.util.database
  (:require
   [gungnir.database :refer [*database* make-datasource!]]
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

(defn clear!
  "Clear the database from any rows in the database."
  ([] (clear! *database*))
  ([datasource]
   (next.jdbc/execute!
    datasource
    [(str
      "DELETE from \"products\";"
      "DELETE from \"document\";"
      "DELETE from \"token\";"
      "DELETE from \"comment\";"
      "DELETE from \"post\";"
      "DELETE from \"user\";")])))

(defn drop!
  "Clear the database from any rows in the database."
  ([] (drop! *database*))
  ([datasource]
   (next.jdbc/execute!
    datasource
    [(str
      "DROP TABLE \"products\";"
      "DROP TABLE \"document\";"
      "DROP TABLE \"token\";"
      "DROP TABLE \"comment\";"
      "DROP TABLE \"post\";"
      "DROP TABLE \"user\";")])))
