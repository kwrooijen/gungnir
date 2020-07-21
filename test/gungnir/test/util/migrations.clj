(ns gungnir.test.util.migrations
  (:require
   [gungnir.db :refer [*database*]]
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
   " , created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL "
   " , updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL "
   " );"))

(defn init!
  "Run migrations to create all tables. The migrations are idempotent,
  so they can be run multiple times."
  []
  (next.jdbc/execute-one! *database* [uuid-extension-migration])
  (next.jdbc/execute-one! *database* [trigger-updated-at-migration])
  (next.jdbc/execute-one! *database* [user-table-migration])
  (next.jdbc/execute-one! *database* [post-table-migration])
  (next.jdbc/execute-one! *database* [comment-table-migration]))
