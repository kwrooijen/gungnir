(ns gungnir.test.util.migrations
  (:require
   [gungnir.migration]
   [gungnir.database :refer [*datasource*]]
   [next.jdbc]))

(def uuid-extension-migration
  "Add the `uuid-ossp` extension for UUID support"
  {:id "uuid-ossp"
   :up [[:extension/create {:if-not-exists true} :uuid-ossp]]
   :down [[:extension/drop :uuid-ossp]]})

(def trigger-updated-at-migration
  "Add trigger for the `updated_at` field to set its value to `NOW()`
  whenever this row changes. This is so you don't have to do it
  manually, and can be useful information."
  {:id :trigger_set_updated_at
   :up [(str
          "CREATE OR REPLACE FUNCTION trigger_set_updated_at() "
          "RETURNS TRIGGER AS $$ "
          "BEGIN "
          "  NEW.updated_at = NOW(); "
          "  RETURN NEW; "
          "END; "
          "$$ LANGUAGE plpgsql;")]
   :down ["DROP FUNCTION IF EXISTS trigger_set_updated_at()"]})

(def user-table-migration
  " Create a `user` table.

  Relations
  * user has_many post
  * user has_many comment
  "
  {:id :user
   :up
   [[:table/create {:table :user :if-not-exists true}
     [:column/add [:id {:primary-key true :default true} :uuid]]
     [:column/add [:email {:unique true} :text]]
     [:column/add [:username {:unique true :optional true} :text]]
     [:column/add [:password :text]]
     [:column/add [:ragnar/timestamps]]]]
   :down []})

(def post-table-migration
  "Create a `post` table.

  Relations
  * post has_many comment
  * post belongs_to user
  "
  {:id :post
   :up
   [[:table/create {:table :post :if-not-exists true}
     [:column/add [:id {:default true :primary-key true} :uuid]]
     [:column/add [:title {:required false} :text]]
     [:column/add [:content {:required false} :text]]
     [:column/add [:user-id {:references :user/id} :uuid]]
     [:column/add [:ragnar/timestamps]]]]
   :down []})

(def comment-table-migration
  "Create a `comment` table.

  Relations
  * comment belongs_to post
  * comment belongs_to user
  "
  {:id :comment
   :up
   [[:table/create {:table :comment :if-not-exists true}
     [:column/add [:id {:default true :primary-key true} :uuid]]
     [:column/add [:content :text]]
     [:column/add [:user-id {:references :user/id} :uuid]]
     [:column/add [:post-id {:references :post/id} :uuid]]
     [:column/add [:rating {:default 0} :integer]]
     [:column/add [:ragnar/timestamps]]]]
   :down []})

(def token-table-migration
  "Create a `token` table.

  Relations
  * comment belongs_to user
  "
  {:id :token
   :up
   [[:table/create {:table :token :if-not-exists true}
     [:column/add [:id {:default true :primary-key true} :uuid]]
     [:column/add [:user-id {:references :user/id} :uuid]]
     [:column/add [:type :text]]
     [:column/add [:ragnar/timestamps]]]]
   :down []})

(def document-table-migration
  "Create a `document` table.

  Relations
  * author-id belongs_to user
  * reviewer-id belongs_to user
  "
  {:id :document
   :up
   [[:table/create {:table :document :if-not-exists true}
     [:column/add [:id {:default true :primary-key true} :uuid]]
     [:column/add [:author-id {:references :user/id} :uuid]]
     [:column/add [:reviewer-id {:references :user/id} :uuid]]
     [:column/add [:content :text]]
     [:column/add [:ragnar/timestamps]]]]
   :down []})

(def product-table-migration
  "Create a `product` table."
  {:id :product
   :up
   [[:table/create {:table :products :if-not-exists true}
     [:column/add [:id {:default true :primary-key true} :uuid]]
     [:column/add [:title :text]]
     [:column/add [:ragnar/timestamps]]]]
   :down []})

(def snippet-table-migration
  "Create a `snippet` table.

  Relations
  * snippet belongs_to user
  "
  {:id :snippet
   :up
   [[:table/create {:table :snippet :if-not-exists true}
     [:column/add [:id {:default true :primary-key true} :uuid]]
     [:column/add [:user-id {:references :user/id} :uuid]]
     [:column/add [:content :text]]
     [:column/add [:ragnar/timestamps]]]]
   :down []})

(def account-table-migration
  "Create a `account` table.

  Relations
  * snippet belongs_to user
  "
  {:id :account
   :up
   [[:table/create {:table :account :if-not-exists true}
     [:column/add [:id {:default true :primary-key true} :uuid]]
     [:column/add [:balance :integer]]
     [:column/add [:ragnar/timestamps]]]]
   :down []})

(def migrations
  [uuid-extension-migration
   trigger-updated-at-migration
   user-table-migration
   post-table-migration
   comment-table-migration
   token-table-migration
   document-table-migration
   product-table-migration
   snippet-table-migration
   account-table-migration])

(defn init!
  "Run migrations to create all tables. The migrations are idempotent,
  so they can be run multiple times."
  ([] (init! *datasource*))
  ([datasource]
   (with-out-str
     (gungnir.migration/migrate-all migrations datasource))))
