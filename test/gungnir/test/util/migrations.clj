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
     [:column/add [:email {:unique true} :string]]
     [:column/add [:username {:unique true :optional true} :string]]
     [:column/add [:password :string]]
     [:column/add [:gungnir/timestamps]]]]
   :down [[:table/drop :user]]})

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
     [:column/add [:title {:optional true} :string]]
     [:column/add [:content {:optional true} :string]]
     [:column/add [:user-id {:references :user/id} :uuid]]
     [:column/add [:gungnir/timestamps]]]]
   :down [[:table/drop :post]]})

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
     [:column/add [:content :string]]
     [:column/add [:user-id {:references :user/id} :uuid]]
     [:column/add [:post-id {:references :post/id} :uuid]]
     [:column/add [:rating {:default 0} :int]]
     [:column/add [:gungnir/timestamps]]]]
   :down [[:table/drop :comment]]})

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
     [:column/add [:type :string]]
     [:column/add [:gungnir/timestamps]]]]
   :down [[:table/drop :token]]})

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
     [:column/add [:content :string]]
     [:column/add [:gungnir/timestamps]]]]
   :down [[:table/drop :document]]})

(def products-table-migration
  "Create a `products` table."
  {:id :products
   :up
   [[:table/create {:table :products :if-not-exists true}
     [:column/add [:id {:default true :primary-key true} :uuid]]
     [:column/add [:title :string]]
     [:column/add [:gungnir/timestamps]]]]
   :down [[:table/drop :products]]})

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
     [:column/add [:content :string]]
     [:column/add [:gungnir/timestamps]]]]
   :down [[:table/drop :snippet]]})

(def account-table-migration
  "Create a `account` table.

  Relations
  * snippet belongs_to user
  "
  {:id :account
   :up
   [[:table/create {:table :account :if-not-exists true}
     [:column/add [:id {:default true :primary-key true} :uuid]]
     [:column/add [:balance :int]]
     [:column/add [:gungnir/timestamps]]]]
   :down [[:table/drop :account]]})

(def migrations
  [uuid-extension-migration
   trigger-updated-at-migration
   user-table-migration
   post-table-migration
   comment-table-migration
   token-table-migration
   document-table-migration
   products-table-migration
   snippet-table-migration
   account-table-migration])

(defn migrate!
  "Run migrations to create all tables. The migrations are idempotent,
  so they can be run multiple times."
  ([] (migrate! *datasource*))
  ([datasource]
   (with-out-str
     (gungnir.migration/migrate! migrations {} datasource))))

(defn rollback!
  "Clear the database from any rows in the database."
  ([] (rollback! *datasource*))
  ([datasource]
   (doseq [_ migrations]
     (gungnir.migration/rollback! migrations datasource))))
