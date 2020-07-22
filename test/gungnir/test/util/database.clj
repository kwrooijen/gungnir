(ns gungnir.test.util.database
  (:require
   [gungnir.db :refer [*database* make-datasource!]]
   [next.jdbc]))

(def ^:private datasource-opts
  {:adapter       "postgresql"
   :username      "postgres"
   :password      "postgres"
   :database-name "postgres"
   :server-name   "localhost"
   :port-number   9724})

(defn init!
  "Initialize the database connection for testing."
  []
  (make-datasource! datasource-opts))

(defn clear!
  "Clear the database from any rows in the database."
  []
  (next.jdbc/execute!
   *database*
   [(str
     "DELETE from \"token\";"
     "DELETE from \"comment\";"
     "DELETE from \"post\";"
     "DELETE from \"user\";")]))

(defn drop!
  "Clear the database from any rows in the database."
  []
  (next.jdbc/execute!
   *database*
   [(str
     "DROP TABLE \"token\";"
     "DROP TABLE \"comment\";"
     "DROP TABLE \"post\";"
     "DROP TABLE \"user\";")]))
