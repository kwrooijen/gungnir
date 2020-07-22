(ns gungnir.test.util.model
  (:require
   [gungnir.core :as gungnir]))

(defmethod gungnir/model :user [_]
  [:map
   {:has-many {:post :user/posts
               :comment :user/comments}
    :has-one {:token :user/token}}
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

(defmethod gungnir/model :token [_]
  [:map
   {:belongs-to {:user :token/user-id}}
   [:token/id {:primary-key true} uuid?]
   [:token/type {:after-read [:edn/read-string]} [:enum :token/reset :token/verify]]
   [:token/user-id uuid?]
   [:token/created-at {:auto true} inst?]
   [:token/updated-at {:auto true} inst?]])

(defn- password-match? [m]
  (= (:user/password m)
     (:user/password-confirmation m)))

(defmethod gungnir/validator [:user :register/password-match?] [_ _]
  {:validator/key :user/password-confirmation
   :validator/fn password-match?
   :validator/message "Passwords don't match"})
