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

(defn- cast+errors [params model]
  (-> params (gungnir/cast model) gungnir/changeset :changeset/errors))

(defn- changeset+errors [params]
  (-> params gungnir/changeset :changeset/errors))

(deftest valid-user-tests
  (testing "valid changesets"
    (let [user-1 {:user/email "test@user.com" :user/password "123456"}]
      (is (-> user-1 changeset+errors nil?)))))

(deftest invalid-valid-user-tests
  (testing "invalid email"
    (let [user-1 {:user/email "@user.com" :user/password "123456"}]
      (is (-> user-1 changeset+errors :user/email some?))))

  (testing "invalid password"
    (let [user-1 {:user/email "test@user.com" :user/password "..."}]
      (is (-> user-1 changeset+errors :user/password some?))))

  (testing "invalid email and password"
    (let [user-1 {:user/email "test@user" :user/password "..."}]
      (is (= #{:user/email :user/password} (-> user-1 changeset+errors keys set))))))

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

(deftest test-diffing-changeset)

(deftest test-auto-property)

(deftest test-transient-property)

(deftest test-virtual-property)

(deftest test-validators)
