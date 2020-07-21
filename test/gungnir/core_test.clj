(ns gungnir.core-test
  (:require
   [clojure.test :refer :all]
   [gungnir.test.util :as util]))

(use-fixtures :once util/once-fixture)
(use-fixtures :each util/each-fixture)
