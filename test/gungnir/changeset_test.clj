(ns gungnir.changeset-test
  (:require
   [clojure.test :refer :all]
   [gungnir.changeset :as changeset]
   [gungnir.test.util :as util]))

(use-fixtures :once util/once-fixture)
(use-fixtures :each util/each-fixture)

(def existing-account
  {:account/id "e52c518c-6d3e-4e75-87f1-ff08bdc933be"
   :account/email "account@test.com"
   :account/password "123456"
   :account/created-at (java.util.Date. 1495636054438)
   :account/updated-at (java.util.Date. 1495636054438)})

(defn- cast+errors [params model]
  (-> params (changeset/cast model) changeset/create :changeset/errors))

(defn- changeset+errors [params]
  (-> params changeset/create :changeset/errors))

(deftest valid-account-tests
  (testing "valid changesets"
    (let [account {:account/email "test@account.com" :account/password "123456"}]
      (is (-> account changeset+errors nil?)))))

(deftest invalid-valid-account-tests
  (testing "invalid email"
    (let [account {:account/email "@account.com" :account/password "123456"}]
      (is (-> account changeset+errors :account/email some?))))

  (testing "invalid password"
    (let [account {:account/email "test@account.com" :account/password "..."}]
      (is (-> account changeset+errors :account/password some?))))

  (testing "invalid email and password"
    (let [account {:account/email "test@account" :account/password "..."}]
      (is (= #{:account/email :account/password} (-> account changeset+errors keys set))))))

(deftest casting-to-changeset
  (testing "casting strings to changeset"
    (let [params {"email" "test@account.com" "password" "123456"}]
      (is (-> params (cast+errors :account) nil?)))
    (let [params {"email" "test@account" "password" "123456"}]
      (is (-> params (cast+errors :account) :account/email some?)))
    (let [params {"email" "test@account.com" "password" "..."}]
      (is (-> params (cast+errors :account) :account/password some?))))

  (testing "casting strings and keywords with formatting to changeset"
    (let [params {"email" "test@account.com" :password "123456"}]
      (is (-> params (cast+errors :account) nil?)))
    (let [params {"email" "test@account" "password" "123456"}]
      (is (-> params (cast+errors :account) :account/email some?)))
    (let [params {"email" "test@account.com" "password" "..."}]
      (is (-> params (cast+errors :account) :account/password some?))))

  (testing "casting with dash in name"
    (let [params {"content" "some content"}]
      (is (-> params (changeset/cast :with-dash) seq some?))))

  (testing "casting with underscore in name"
    (let [params {"content" "some content"}]
      (is (-> params (changeset/cast :with_underscore) seq some?)))))

(deftest test-diffing-changeset
  (testing "updating email"
    (is (-> existing-account
            (changeset/create {:account/email "foo@bar.baz"})
            :changeset/errors
            nil?))
    (is (= "foo@bar.baz"
         (-> existing-account
             (changeset/create {:account/email "foo@bar.baz"})
             :changeset/result
             :account/email)))
    (is (= "123456"
         (-> existing-account
             (changeset/create {:account/email "foo@bar.baz"})
             :changeset/result
             :account/password)))))

(deftest test-auto-property
  (testing "auto should not be in result"
    (is (= (java.util.Date. 1495636054438)
         (-> existing-account
             (changeset/create {:account/created-at (java.util.Date. 123)})
             :changeset/result
             :account/created-at)))))

(deftest test-virtual-property
  (testing "virtual should not be in result"
    (is (-> existing-account
               (changeset/create {:account/password-confirmation "987654"})
               :changeset/result
               :account/password-confirmation
               nil?))
    (is (-> {:account/email "test@account.com"
             :account/password "987654"
             :account/password-confirmation "987654"}
            (changeset/create)
            :changeset/result
            :account/password-confirmation
            nil?))))

(deftest test-validators
  (testing "password confirmation validator"
    (let [account {:account/email "account@test.com"
                :account/password "123456"
                :account/password-confirmation "123456"}]
      (is (-> account
              (changeset/create [:account/password-match?])
              :changeset/errors
              nil?))

      (is (-> (assoc account :account/password-confirmation "123456+7")
              (changeset/create [:account/password-match?])
              :changeset/errors
              :account/password-confirmation
              some?))))

  (testing "password confirmation validator with invalid fields"
    (let [account {:account/email "account@test.com"
                :account/password "1234"
                :account/password-confirmation "1234"}]
      (is (-> account
              (changeset/create [:account/password-match?])
              :changeset/errors
              some?)))))

(deftest assoc-changeset
  (testing "add a new email to existing account"
    (let [new-email "bar@baz.bar"
          changeset
          (-> {:account/email "foo"
               :account/password "123456"}
              (changeset/assoc
               :account/email new-email))]
      (is (nil? (:changeset/errors changeset)))
      (is (= new-email (-> changeset :changeset/params :account/email)))
      (is (= new-email (-> changeset :changeset/result :account/email)))))

  (testing "add a new email to changeset"
    (let [new-email "bar@baz.bar"
          changeset (-> {:account/email "foo" :account/password "123456"}
                        (changeset/create)
                        (changeset/assoc :account/email new-email))]
      (is (nil? (:changeset/errors changeset)))
      (is (= new-email (-> changeset :changeset/params :account/email)))
      (is (= new-email (-> changeset :changeset/result :account/email))))))

(deftest update-changeset
  (let [comment
        {:comment/id (java.util.UUID/randomUUID)
         :comment/content ""
         :comment/account-id (java.util.UUID/randomUUID)
         :comment/post-id (java.util.UUID/randomUUID)
         :comment/rating 999}]
    (testing "incrementing rating on comment"
      (let [changeset (changeset/update comment :comment/rating inc)]
        (is (nil? (:changeset/errors changeset)))
        (is (= 1000 (-> changeset :changeset/params :comment/rating)))
        (is (= 1000 (-> changeset :changeset/result :comment/rating)))))

    (testing "incrementing rating on comment"
      (let [changeset (changeset/update (changeset/create comment) :comment/rating inc)]
        (is (nil? (:changeset/errors changeset)))
        (is (= 1000 (-> changeset :changeset/params :comment/rating)))
        (is (= 1000 (-> changeset :changeset/result :comment/rating)))))))

(deftest merge-changeset
  (let [comment
        {:comment/id (java.util.UUID/randomUUID)
         :comment/content ""
         :comment/account-id (java.util.UUID/randomUUID)
         :comment/post-id (java.util.UUID/randomUUID)
         :comment/rating 999}]
    (testing "merge rating on comment"
      (let [changeset (changeset/merge comment {:comment/rating 1000})]
        (is (nil? (:changeset/errors changeset)))
        (is (= 1000 (-> changeset :changeset/params :comment/rating)))
        (is (= 1000 (-> changeset :changeset/result :comment/rating)))))

    (testing "merge rating on comment"
      (let [changeset (changeset/merge (changeset/create comment) {:comment/rating 1000})]
        (is (nil? (:changeset/errors changeset)))
        (is (= 1000 (-> changeset :changeset/params :comment/rating)))
        (is (= 1000 (-> changeset :changeset/result :comment/rating)))))))
