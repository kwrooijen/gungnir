(ns gungnir.test.util.model
  (:require
   [gungnir.model]))

(def model-user
  [:map
   {:has-many
    {:user/posts {:model :post :through :post/user-id}
     :user/comments {:model :comment :through :comment/user-id}}
    :has-one {:user/token {:model :token :through :token/user-id}}}
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
   {:belongs-to {:post/user {:model :user :through :post/user-id}}
    :has-many {:post/comments {:model :comment :through :comment/post-id}}}
   [:post/id {:primary-key true} uuid?]
   [:post/title string?]
   [:post/content string?]
   [:post/user-id uuid?]
   [:post/created-at {:auto true} inst?]
   [:post/updated-at {:auto true} inst?]])

(def model-comment
  [:map
   {:belongs-to
    {:comment/user {:model :user :through :comment/user-id}
     :comment/post {:model :post :through :comment/post-id}}}
   [:comment/id {:primary-key true} uuid?]
   [:comment/content string?]
   [:comment/user-id uuid?]
   [:comment/post-id uuid?]
   [:comment/rating {:optional true} int?]
   [:comment/created-at {:auto true} inst?]
   [:comment/updated-at {:auto true} inst?]])

(def model-token
  [:map
   {:belongs-to {:token/user {:model :user :through :token/user-id}}}
   [:token/id {:primary-key true} uuid?]
   [:token/type {:after-read [:edn/read-string]} [:enum :token/reset :token/verify]]
   [:token/user-id uuid?]
   [:token/created-at {:auto true} inst?]
   [:token/updated-at {:auto true} inst?]])

(def model-document
  [:map
   {:belongs-to {:document/author {:model :user :through :document/author-id}
                 :document/reviewer {:model :user :through :document/reviewer-id}}}
   [:document/id {:primary-key true} uuid?]
   [:document/author-id uuid?]
   [:document/reviewer-id uuid?]
   [:document/content string?]
   [:document/created-at {:auto true} inst?]
   [:document/updated-at {:auto true} inst?]])

(defn- password-match? [m]
  (= (:user/password m)
     (:user/password-confirmation m)))

(defmethod gungnir.model/validator :user/password-match? [_]
  {:validator/key :user/password-confirmation
   :validator/fn password-match?
   :validator/message "Passwords don't match"})

(defmethod gungnir.model/format-error [:user/username :duplicate-key] [_ _]
  "username taken")

(defn init!
  "Initializes the models and saves them to Gungnir."
  []
  (gungnir.model/register!
   {:user model-user
    :post model-post
    :comment model-comment
    :token model-token
    :document model-document}))
