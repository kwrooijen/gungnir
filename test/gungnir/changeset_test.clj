(ns gungnir.changeset-test
  (:require
   [clojure.test :refer :all]
   [gungnir.changeset :as changeset]
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
  (-> params (changeset/cast model) changeset/create :changeset/errors))

(defn- changeset+errors [params]
  (-> params changeset/create :changeset/errors))

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
            (changeset/create {:user/email "foo@bar.baz"})
            :changeset/errors
            nil?))
    (is (= "foo@bar.baz"
         (-> existing-user
             (changeset/create {:user/email "foo@bar.baz"})
             :changeset/result
             :user/email)))
    (is (= "123456"
         (-> existing-user
             (changeset/create {:user/email "foo@bar.baz"})
             :changeset/result
             :user/password)))))

(deftest test-auto-property
  (testing "auto should not be in result"
    (is (= (java.util.Date. 1495636054438)
         (-> existing-user
             (changeset/create {:user/created-at (java.util.Date. 123)})
             :changeset/result
             :user/created-at)))))

(deftest test-virtual-property
  (testing "virtual should not be in result"
    (is (-> existing-user
               (changeset/create {:user/password-confirmation "987654"})
               :changeset/result
               :user/password-confirmation
               nil?))
    (is (-> {:user/email "test@user.com"
             :user/password "987654"
             :user/password-confirmation "987654"}
            (changeset/create)
            :changeset/result
            :user/password-confirmation
            nil?))))

(deftest test-validators
  (testing "password confirmation validator"
    (let [user {:user/email "user@test.com"
                :user/password "123456"
                :user/password-confirmation "123456"}]
      (is (-> user
              (changeset/create [:user/password-match?])
              :changeset/errors
              nil?))

      (is (-> (assoc user :user/password-confirmation "123456+7")
              (changeset/create [:user/password-match?])
              :changeset/errors
              :user/password-confirmation
              some?))))

  (testing "password confirmation validator with invalid fields"
    (let [user {:user/email "user@test.com"
                :user/password "1234"
                :user/password-confirmation "1234"}]
      (is (-> user
              (changeset/create [:user/password-match?])
              :changeset/errors
              some?)))))

(deftest assoc-changeset
  (testing "add a new email to existing user"
    (let [new-email "bar@baz.bar"
          changeset
          (-> {:user/email "foo"
               :user/password "123456"}
              (changeset/assoc
               :user/email new-email))]
      (is (nil? (:changeset/errors changeset)))
      (is (= new-email (-> changeset :changeset/params :user/email)))
      (is (= new-email (-> changeset :changeset/result :user/email)))))

  (testing "add a new email to changeset"
    (let [new-email "bar@baz.bar"
          changeset (-> {:user/email "foo" :user/password "123456"}
                        (changeset/create)
                        (changeset/assoc :user/email new-email))]
      (is (nil? (:changeset/errors changeset)))
      (is (= new-email (-> changeset :changeset/params :user/email)))
      (is (= new-email (-> changeset :changeset/result :user/email))))))

(deftest update-changeset
  (let [comment
        {:comment/id (java.util.UUID/randomUUID)
         :comment/content ""
         :comment/user-id (java.util.UUID/randomUUID)
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
         :comment/user-id (java.util.UUID/randomUUID)
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
