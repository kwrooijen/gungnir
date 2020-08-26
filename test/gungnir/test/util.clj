(ns gungnir.test.util
  "This namespace is used to setup a testing environment. This namespace
  should be included in all test namespaces. "
  (:require
   [gungnir.database :refer [*database*]]
   [orchestra.spec.test :as st]
   [gungnir.test.util.database :as database]
   [gungnir.test.util.migrations :as migrations]
   [gungnir.test.util.model :as model]
   [next.jdbc]))

(defn database-setup-once
  ([]
   (database/init!)
   (database-setup-once *database*))
  ([datasource]
   (st/instrument)
   (migrations/init! datasource)
   (model/init!)))

(defn database-setup-each
  ([] (database-setup-each *database*))
  ([datasource]
   (database/clear! datasource)))

(defn once-fixture [tests]
  (database-setup-once)
  (tests))

(defn each-fixture [tests]
  (database-setup-each)
  (tests))
