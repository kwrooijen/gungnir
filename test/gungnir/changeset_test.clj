(ns gungnir.changeset-test
  (:require
   [clojure.test :refer :all]
   [gungnir.changeset :as gungnir :refer [changeset]]
   [gungnir.test.util :as util]))

(use-fixtures :once util/once-fixture)
(use-fixtures :each util/each-fixture)

(def existing-user
  {:user/id "e52c518c-6d3e-4e75-87f1-ff08bdc933be"
   :user/email "user@test.com"
   :user/password "123456"
   :user/created-at (java.util.Date. 1495636054438)
   :user/updated-at (java.util.Date. 1495636054438)})

(defn- cast+errors [params model]
  (-> params (gungnir/cast model) changeset :changeset/errors))

(defn- changeset+errors [params]
  (-> params changeset :changeset/errors))

(deftest valid-user-tests
  (testing "valid changesets"
    (let [user {:user/email "test@user.com" :user/password "123456"}]
      (is (-> user changeset+errors nil?)))))

(deftest invalid-valid-user-tests
  (testing "invalid email"
    (let [user {:user/email "@user.com" :user/password "123456"}]
      (is (-> user changeset+errors :user/email some?))))

  (testing "invalid password"
    (let [user {:user/email "test@user.com" :user/password "..."}]
      (is (-> user changeset+errors :user/password some?))))

  (testing "invalid email and password"
    (let [user {:user/email "test@user" :user/password "..."}]
      (is (= #{:user/email :user/password} (-> user changeset+errors keys set))))))

(deftest casting-to-changeset
  (testing "casting strings to changeset"
    (let [params {"email" "test@user.com" "password" "123456"}]
      (is (-> params (cast+errors :user) nil?)))
    (let [params {"email" "test@user" "password" "123456"}]
      (is (-> params (cast+errors :user) :user/email some?)))
    (let [params {"email" "test@user.com" "password" "..."}]
      (is (-> params (cast+errors :user) :user/password some?))))

  (testing "casting strings and keywords with formatting to changeset"
    (let [params {"email" "test@user.com" :password "123456"}]
      (is (-> params (cast+errors :user) nil?)))
    (let [params {"email" "test@user" "password" "123456"}]
      (is (-> params (cast+errors :user) :user/email some?)))
    (let [params {"email" "test@user.com" "password" "..."}]
      (is (-> params (cast+errors :user) :user/password some?)))))

(deftest test-diffing-changeset
  (testing "updating email"
    (is (-> existing-user
            (changeset {:user/email "foo@bar.baz"})
            :changeset/errors
            nil?))
    (is (= "foo@bar.baz"
         (-> existing-user
             (changeset {:user/email "foo@bar.baz"})
             :changeset/result
             :user/email)))
    (is (= "123456"
         (-> existing-user
             (changeset {:user/email "foo@bar.baz"})
             :changeset/result
             :user/password)))))

(deftest test-auto-property
  (testing "auto should not be in result"
    (is (= (java.util.Date. 1495636054438)
         (-> existing-user
             (changeset {:user/created-at (java.util.Date. 123)})
             :changeset/result
             :user/created-at)))))

(deftest test-virtual-property
  (testing "virtual should not be in result"
    (is (-> existing-user
               (changeset {:user/password-confirmation "987654"})
               :changeset/result
               :user/password-confirmation
               nil?))
    (is (-> {:user/email "test@user.com"
             :user/password "987654"
             :user/password-confirmation "987654"}
            (changeset)
            :changeset/result
            :user/password-confirmation
            nil?))))

(deftest test-validators
  (testing "password confirmation validator"
    (let [user {:user/email "user@test.com"
                :user/password "123456"
                :user/password-confirmation "123456"}]
      (is (-> user
              (changeset [:register/password-match?])
              :changeset/errors
              nil?))

      (is (-> (assoc user :user/password-confirmation "123456+7")
              (changeset [:register/password-match?])
              :changeset/errors
              :user/password-confirmation
              some?))))

  (testing "password confirmation validator with invalid fields"
    (let [user {:user/email "user@test.com"
                :user/password "1234"
                :user/password-confirmation "1234"}]
      (is (-> user
              (changeset [:register/password-match?])
              :changeset/errors
              some?)))))
