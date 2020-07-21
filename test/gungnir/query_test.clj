(ns gungnir.query-test
  (:require
   [clojure.test :refer :all]
   [gungnir.test.util :as util]))

(use-fixtures :once util/once-fixture)
(use-fixtures :each util/each-fixture)

(deftest test-find!)

(deftest test-find-by!)

(deftest test-all!)

(deftest test-insert!)

(deftest test-update!)

(deftest test-delete!)

(deftest test-relation-has-one)

(deftest test-relation-has-many)

(deftest test-relation-belongs-to)

(deftest test-before-save)

(deftest test-before-read)

(deftest test-after-read)

(deftest test-auto-uuid)
