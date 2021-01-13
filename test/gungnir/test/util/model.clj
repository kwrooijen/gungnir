(ns gungnir.test.util.model
  (:require
   [gungnir.model]))

(def model-user
  [:map
   {:has-many
    {:user/posts {:model :post}
     :user/comments {:model :comment}}
    :has-one {:user/token {:model :token}}}
   [:user/id {:primary-key true} uuid?]
   [:user/username {:optional true} [:maybe string?]]
   [:user/email {:before-save [:string/lower-case]
                 :before-read [:string/lower-case]}
    [:re {:error/message "Invalid email"} #".+@.+\..+"]]
   [:user/password [:string {:min 6}]]
   [:user/password-confirmation {:virtual true} [:string {:min 6}]]
   [:user/created-at {:auto true} inst?]
   [:user/updated-at {:auto true} inst?]])

(def model-post
  [:map
   {:belongs-to {:post/user {:model :user}}
    :has-many {:post/comments {:model :comment}}}
   [:post/id {:primary-key true} uuid?]
   [:post/title string?]
   [:post/content string?]
   [:post/user-id uuid?]
   [:post/created-at {:auto true} inst?]
   [:post/updated-at {:auto true} inst?]])

(def model-comment
  [:map
   {:belongs-to
    {:comment/user {:model :user}
     :comment/post {:model :post}}}
   [:comment/id {:primary-key true} uuid?]
   [:comment/content string?]
   [:comment/user-id uuid?]
   [:comment/post-id uuid?]
   [:comment/rating {:optional true} int?]
   [:comment/created-at {:auto true} inst?]
   [:comment/updated-at {:auto true} inst?]])

(def model-token
  [:map
   {:belongs-to {:token/user {:model :user}}}
   [:token/id {:primary-key true} uuid?]
   [:token/type {:after-read [:edn/read-string]} [:enum :token/reset :token/verify]]
   [:token/user-id uuid?]
   [:token/created-at {:auto true} inst?]
   [:token/updated-at {:auto true} inst?]])

(def model-document
  [:map
   {:belongs-to {:document/author {:model :user :foreign-key :document/author-id}
                 :document/reviewer {:model :user :foreign-key :document/reviewer-id}}}
   [:document/id {:primary-key true} uuid?]
   [:document/author-id uuid?]
   [:document/reviewer-id uuid?]
   [:document/content string?]
   [:document/created-at {:auto true} inst?]
   [:document/updated-at {:auto true} inst?]])

(def model-product
  [:map
   {:table "products"}
   [:product/id {:primary-key true} uuid?]
   [:product/title string?]
   [:product/created-at {:auto true} inst?]
   [:product/updated-at {:auto true} inst?]])

(def snippet-registry
  {:snippet/id uuid?
   :snippet/user-id uuid?
   :snippet/content string?
   :snippet/created-at inst?
   :snippet/updated-at inst?})

(def model-snippet
  [:map
   {:registry snippet-registry
    :belongs-to {:snippet/user {:model :user}}}
   [:snippet/id {:primary-key true}]
   :snippet/user-id
   :snippet/content
   [:snippet/created-at {:auto true}]
   [:snippet/updated-at {:auto true}]])

(def model-account
  [:map
   [:account/id {:primary-key true} uuid?]
   [:account/balance {:default 0} int?]
   [:account/created-at {:auto true} inst?]
   [:account/updated-at {:auto true} inst?]])

(defn- password-match? [m]
  (= (:user/password m)
     (:user/password-confirmation m)))

(defmethod gungnir.model/validator :user/password-match? [_]
  {:validator/key :user/password-confirmation
   :validator/fn password-match?
   :validator/message "Passwords don't match"})

(defmethod gungnir.model/format-error [:user/username :duplicate-key] [_ _]
  "username taken")

(def model-with-dash
  [:map
   [:with-dash/id {:auto true :primary-key true} uuid?]
   [:with-dash/content string?]])

(def model-with-underscore
  [:map
   [:with_underscore/id {:auto true :primary-key true} uuid?]
   [:with_underscore/content string?]])

(defn init!
  "Initializes the models and saves them to Gungnir."
  []
  (gungnir.model/register!
   {:user model-user
    :post model-post
    :comment model-comment
    :token model-token
    :document model-document
    :product model-product
    :snippet model-snippet
    :with-dash model-with-dash
    :with_underscore model-with-underscore
    :account model-account}))
