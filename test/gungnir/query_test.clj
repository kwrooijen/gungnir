(ns gungnir.query-test
  (:require
   [clojure.test :refer :all]
   [gungnir.query :as q]
   [gungnir.core :refer [changeset]]
   [gungnir.test.util :as util]))

(use-fixtures :once util/once-fixture)
(use-fixtures :each util/each-fixture)

(def user-1-email "user@test.com")

(def user-1
  {:user/email user-1-email
   :user/password user-1-email})

(deftest test-find!
  (testing "Find user by primary key"
    (let [{:user/keys [id]} (-> user-1 changeset q/insert!)]
      (is (= user-1-email (-> (q/find! :user id) :user/email)))))

  ;; TODO fix failing tests
  ;; (testing "Find user by primary key, automatic uuid translation"
  ;;   (let [{:user/keys [id]} (-> user-1 changeset q/insert!)]
  ;;     (is (= user-1-email (-> (q/find! :user (str id)) :user/email)))))

  (testing "Find unknown user by primary key returns nil"
    (is (nil? (-> (q/find! :user #uuid "1e626bf3-8fdf-4a66-b708-7aa35dafede9"))))
    ;; (is (nil? (-> (q/find! :user "1e626bf3-8fdf-4a66-b708-7aa35dafede9"))))
    ))

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
