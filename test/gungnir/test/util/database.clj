(ns gungnir.test.util.database
  (:require
   [gungnir.test.util.migrations :refer [migrations]]
   [gungnir.migration]
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
