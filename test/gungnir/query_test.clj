(ns gungnir.query-test
  (:require [clojure.test :refer :all]
            [gungnir.test.util :as util]
            [gungnir.core :as gungnir]))

;; (defn once-fixture [tests]
;;   (binding [util/system (util/init-system)]
;;     (try (tests)
;;          (catch Exception _ nil)
;;          (finally (util/halt-system)))))

;; (defn each-fixture [tests]
;;   (stest/instrument)
;;   (util/seed-database)
;;   (tests))

;; (use-fixtures :once once-fixture)
;; (use-fixtures :each each-fixture)

(deftest some-test)
