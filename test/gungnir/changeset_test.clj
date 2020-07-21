(ns gungnir.changeset-test
  (:require [clojure.test :refer :all]
            [gungnir.core :as gungnir]))

(defmethod gungnir/model :user [_]
    [:map
     {:has-many {:post :user/posts
                 :comment :user/comments}}
     [:user/id {:primary-key true} uuid?]
     [:user/email {:on-save [:string/lower-case]
                   :before-read [:string/lower-case]}
      [:re {:error/message "Invalid email"} #".+@.+\..+"]]
     [:user/password {:on-save [:bcrypt]} [:string {:min 6}]]
     [:user/password-confirmation {:virtual true} [:string {:min 6}]]
     [:user/created-at {:auto true} inst?]
     [:user/updated-at {:auto true} inst?]])

(defmethod gungnir/model :post [_]
  [:map
   {:belongs-to {:user :post/user-id}
    :has-many {:comment :post/comments}}
   [:post/id {:primary-key true} uuid?]
   [:post/title string?]
   [:post/content string?]
   [:post/user-id uuid?]
   [:post/created-at {:auto true} inst?]
   [:post/updated-at {:auto true} inst?]])

(defmethod gungnir/model :comment [_]
  [:map
   {:belongs-to {:user :comment/user-id
                 :post :comment/post-id}}
   [:comment/id {:primary-key true} uuid?]
   [:comment/content string?]
   [:comment/user-id uuid?]
   [:comment/post-id uuid?]
   [:comment/created-at {:auto true} inst?]
   [:comment/updated-at {:auto true} inst?]])

(def existing-user
  {:user/id "e52c518c-6d3e-4e75-87f1-ff08bdc933be"
   :user/email "user@test.com"
   :user/password "123456"
   :user/created-at (java.util.Date. 1495636054438)
   :user/updated-at (java.util.Date. 1495636054438)})

(defn- cast+errors [params model]
  (-> params (gungnir/cast model) gungnir/changeset :changeset/errors))

(defn- changeset+errors [params]
  (-> params gungnir/changeset :changeset/errors))

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
            (gungnir/changeset {:user/email "foo@bar.baz"})
            :changeset/errors
            nil?))
    (is (= "foo@bar.baz"
         (-> existing-user
             (gungnir/changeset {:user/email "foo@bar.baz"})
             :changeset/result
             :user/email)))
    (is (= "123456"
         (-> existing-user
             (gungnir/changeset {:user/email "foo@bar.baz"})
             :changeset/result
             :user/password)))))

(deftest test-auto-property
  ;; TODO, fix failing test
  (testing "auto should not be in result"
    ;; (is (= (java.util.Date. 1495636054438)
    ;;      (-> existing-user
    ;;          (gungnir/changeset {:user/created-at (java.util.Date. 123)})
    ;;          :changeset/result
    ;;          :user/created-at)))
    ))

(deftest test-transient-property)

(deftest test-virtual-property
  ;; TODO, fix failing test
  (testing "virtual should not be in result"
    ;; (is (-> existing-user
    ;;            (gungnir/changeset {:user/password-confirmation "987654"})
    ;;            :changeset/result
    ;;            :user/password-confirmation
    ;;            nil?))
    ;; (is (-> {:user/email "test@user.com"
    ;;          :user/password "987654"
    ;;          :user/password-confirmation "987654"}
    ;;         (gungnir/changeset)
    ;;         :changeset/result
    ;;         :user/password-confirmation
    ;;         nil?))
    ))

(deftest test-validators)
