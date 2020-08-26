(ns gungnir.test.util.migrations
  (:require
   [gungnir.database :refer [*datasource*]]
   [next.jdbc]))

(def uuid-extension-migration
  "Add the `uuid-ossp` extension for UUID support"
  "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";")

(def trigger-updated-at-migration
  "Add trigger for the `updated_at` field to set its value to `NOW()`
  whenever this row changes. This is so you don't have to do it
  manually, and can be useful information."
  (str
   "CREATE OR REPLACE FUNCTION trigger_set_updated_at() "
   "RETURNS TRIGGER AS $$ "
   "BEGIN "
   "  NEW.updated_at = NOW(); "
   "  RETURN NEW; "
   "END; "
   "$$ LANGUAGE plpgsql;"))

(def user-table-migration
  " Create a `user` table.

  Relations
  * user has_many post
  * user has_many comment
  "
  (str
   "CREATE TABLE IF NOT EXISTS \"user\" "
   " ( id uuid DEFAULT uuid_generate_v4 () PRIMARY KEY "
   " , email TEXT NOT NULL UNIQUE "
   " , username TEXT UNIQUE "
   " , password TEXT NOT NULL "
   " , created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL "
   " , updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL "
   " );"))

(def post-table-migration
  "Create a `post` table.

  Relations
  * post has_many comment
  * post belongs_to user
  "
  (str
   "CREATE TABLE IF NOT EXISTS post "
   " ( id uuid DEFAULT uuid_generate_v4 () PRIMARY KEY "
   " , title TEXT "
   " , content TEXT "
   " , user_id uuid references \"user\"(id)"
   " , created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL "
   " , updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL "
   " );"))

(def comment-table-migration
  "Create a `comment` table.

  Relations
  * comment belongs_to post
  * comment belongs_to user
  "
  (str
   "CREATE TABLE IF NOT EXISTS comment "
   " ( id uuid DEFAULT uuid_generate_v4 () PRIMARY KEY "
   " , content TEXT "
   " , user_id uuid references \"user\"(id)"
   " , post_id uuid references post(id)"
   " , rating INT DEFAULT 0"
   " , created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL "
   " , updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL "
   " );"))

(def token-table-migration
  "Create a `token` table.

  Relations
  * comment belongs_to user
  "
  (str
   "CREATE TABLE IF NOT EXISTS token "
   " ( id uuid DEFAULT uuid_generate_v4 () PRIMARY KEY "
   " , user_id uuid references \"user\"(id)"
   " , type TEXT"
   " , created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL "
   " , updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL "
   " );"))

(def document-table-migration
  "Create a `document` table.

  Relations
  * author-id belongs_to user
  * reviewer-id belongs_to user
  "
  (str
   "CREATE TABLE IF NOT EXISTS document "
   " ( id uuid DEFAULT uuid_generate_v4 () PRIMARY KEY "
   " , author_id uuid references \"user\"(id)"
   " , reviewer_id uuid references \"user\"(id)"
   " , content TEXT"
   " , created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL "
   " , updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL "
   " );"))

(def product-table-migration
  "Create a `product` table."
  (str
   "CREATE TABLE IF NOT EXISTS products "
   " ( id uuid DEFAULT uuid_generate_v4 () PRIMARY KEY "
   " , title TEXT NOT NULL"
   " , created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL "
   " , updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL "
   " );"))

(def snippet-table-migration
  "Create a `snippet` table.

  Relations
  * snippet belongs_to user
  "
  (str
   "CREATE TABLE IF NOT EXISTS snippet "
   " ( id uuid DEFAULT uuid_generate_v4 () PRIMARY KEY "
   " , content TEXT "
   " , user_id uuid references \"user\"(id)"
   " , created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL "
   " , updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL "
   " );"))

(defn init!
  "Run migrations to create all tables. The migrations are idempotent,
  so they can be run multiple times."
  ([] (init! *datasource*))
  ([datasource]
   (next.jdbc/execute-one! datasource [uuid-extension-migration])
   (next.jdbc/execute-one! datasource [trigger-updated-at-migration])
   (next.jdbc/execute-one! datasource [user-table-migration])
   (next.jdbc/execute-one! datasource [post-table-migration])
   (next.jdbc/execute-one! datasource [comment-table-migration])
   (next.jdbc/execute-one! datasource [token-table-migration])
   (next.jdbc/execute-one! datasource [document-table-migration])
   (next.jdbc/execute-one! datasource [product-table-migration])
   (next.jdbc/execute-one! datasource [snippet-table-migration])))
