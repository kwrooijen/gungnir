(ns gungnir.test.util
  "This namespace is used to setup a testing environment. This namespace
  should be included in all test namespaces. "
  (:require
   [orchestra.spec.test :as st]
   [gungnir.test.util.database :as database]
   [gungnir.test.util.migrations :as migrations]
   [gungnir.test.util.model :as model]
   [next.jdbc]))

(defn once-fixture [tests]
  (st/instrument)
  (database/init!)
  (migrations/init!)
  (model/init!)
  (tests))

(defn each-fixture [tests]
  (database/clear!)
  (tests))
