(ns gungnir.test.util
  "This namespace is used to setup a testing environment. This namespace
  should be included in all test namespaces. "
  (:require
   [gungnir.db :refer [*database*]]
   [gungnir.test.util.database :as database]
   [gungnir.test.util.migrations :as migrations]
   [gungnir.test.util.model]
   [next.jdbc]))

(defn clear-database! []
  (next.jdbc/execute!
   *database*
   [(str "DELETE from \"user\";"
         "DELETE from \"post\";"
         "DELETE from \"comment\";")]))

(defn init! []
  (database/init!)
  (migrations/init!))
