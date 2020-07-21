(ns gungnir.query-test
  (:require
   [clojure.spec.test.alpha :as stest]
   [clojure.test :refer :all]
   [gungnir.test.util :as util]))

(defn once-fixture [tests]
  (tests))

(defn each-fixture [tests]
  (stest/instrument)
  (util/init!)
  (util/clear-database!)
  (tests))

(use-fixtures :once once-fixture)
(use-fixtures :each each-fixture)

(deftest some-test)
