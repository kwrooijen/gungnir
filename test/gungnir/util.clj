(ns gungnir.util
  "This namespace is used to setup a testing environment. Models and
  validators are defined here to be used in test. This namespace
  should be included in all test namespaces."
  (:require
   [gungnir.util.model]
   [gungnir.db :refer [*database*]]
   [next.jdbc]
   [gungnir.util.database :as database]
   [gungnir.util.migrations :as migrations]))

(defn init! []
  (database/init!)
  (migrations/init!))

(defn clear-database! []
  (next.jdbc/execute!
   *database*
   [(str "DELETE from \"user\";"
         "DELETE from \"post\";"
         "DELETE from \"comment\";")]))
