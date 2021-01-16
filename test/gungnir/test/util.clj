(ns gungnir.test.util
  "This namespace is used to setup a testing environment. This namespace
  should be included in all test namespaces. "
  (:require
   [gungnir.database :refer [*datasource*]]
   [orchestra.spec.test :as st]
   [gungnir.test.util.database :as database]
   [gungnir.test.util.migrations :as migrations]
   [gungnir.test.util.model :as model]
   [next.jdbc]))

(defn database-setup-once
  ([]
   (database/init!)
   (database-setup-once *datasource*))
  ([datasource]
   (st/instrument)
   (migrations/migrate! datasource)
   (model/init!)))

(defn database-setup-each
  ([] (database-setup-each *datasource*))
  ([datasource]
   (migrations/rollback! datasource)
   (migrations/migrate! datasource)))

(defn once-fixture [tests]
  (database-setup-once)
  (tests))

(defn each-fixture [tests]
  (database-setup-each)
  (tests))
