(ns gungnir.test.util.model
  (:require
   [gungnir.model]))

(def model-account
  [:map
   {:has-many
    {:account/posts {:model :post}
     :account/comments {:model :comment}}
    :has-one {:account/token {:model :token}}}
   [:account/id {:primary-key true} uuid?]
   [:account/accountname {:optional true} [:maybe string?]]
   [:account/email {:before-save [:string/lower-case]
                 :before-read [:string/lower-case]}
    [:re {:error/message "Invalid email"} #".+@.+\..+"]]
   [:account/password [:string {:min 6}]]
   [:account/password-confirmation {:virtual true} [:string {:min 6}]]
   [:account/created-at {:auto true} inst?]
   [:account/updated-at {:auto true} inst?]])

(def model-post
  [:map
   {:belongs-to {:post/account {:model :account}}
    :has-many {:post/comments {:model :comment}}}
   [:post/id {:primary-key true} uuid?]
   [:post/title string?]
   [:post/content string?]
   [:post/account-id uuid?]
   [:post/created-at {:auto true} inst?]
   [:post/updated-at {:auto true} inst?]])

(def model-comment
  [:map
   {:belongs-to
    {:comment/account {:model :account}
     :comment/post {:model :post}}}
   [:comment/id {:primary-key true} uuid?]
   [:comment/content string?]
   [:comment/account-id uuid?]
   [:comment/post-id uuid?]
   [:comment/rating {:optional true} int?]
   [:comment/created-at {:auto true} inst?]
   [:comment/updated-at {:auto true} inst?]])

(def model-token
  [:map
   {:belongs-to {:token/account {:model :account}}}
   [:token/id {:primary-key true} uuid?]
   [:token/type {:after-read [:edn/read-string]} [:enum :token/reset :token/verify]]
   [:token/account-id uuid?]
   [:token/created-at {:auto true} inst?]
   [:token/updated-at {:auto true} inst?]])

(def model-document
  [:map
   {:belongs-to {:document/author {:model :account :foreign-key :document/author-id}
                 :document/reviewer {:model :account :foreign-key :document/reviewer-id}}}
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
   :snippet/account-id uuid?
   :snippet/content string?
   :snippet/created-at inst?
   :snippet/updated-at inst?})

(def model-snippet
  [:map
   {:registry snippet-registry
    :belongs-to {:snippet/account {:model :account}}}
   [:snippet/id {:primary-key true}]
   :snippet/account-id
   :snippet/content
   [:snippet/created-at {:auto true}]
   [:snippet/updated-at {:auto true}]])

(def model-bank
  [:map
   [:bank/id {:primary-key true} uuid?]
   [:bank/balance {:default 0} int?]
   [:bank/created-at {:auto true} inst?]
   [:bank/updated-at {:auto true} inst?]])

(defn- password-match? [m]
  (= (:account/password m)
     (:account/password-confirmation m)))

(defmethod gungnir.model/validator :account/password-match? [_]
  {:validator/key :account/password-confirmation
   :validator/fn password-match?
   :validator/message "Passwords don't match"})

(defmethod gungnir.model/format-error [:account/accountname :duplicate-key] [_ _]
  "accountname taken")

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
   {:account model-account
    :post model-post
    :comment model-comment
    :token model-token
    :document model-document
    :product model-product
    :snippet model-snippet
    :with-dash model-with-dash
    :with_underscore model-with-underscore
    :bank model-bank}))
