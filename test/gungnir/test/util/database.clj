(ns gungnir.test.util.database
  (:require
   [gungnir.db :refer [make-datasource!]]))

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
