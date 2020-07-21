(ns gungnir.test.util
  "This namespace is used to setup a testing environment. This namespace
  should be included in all test namespaces. "
  (:require
   [clojure.spec.test.alpha :as stest]
   [gungnir.test.util.database :as database]
   [gungnir.test.util.migrations :as migrations]
   [gungnir.test.util.model]
   [next.jdbc]))

(defn once-fixture [tests]
  (database/init!)
  (migrations/init!)
  (tests))

(defn each-fixture [tests]
  (stest/instrument)
  (database/clear!)
  (tests))
