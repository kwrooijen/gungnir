(ns gungnir.migration-test
  (:require
   [clojure.string :as string]
   [clojure.test :refer :all]
   [gungnir.migration :as migration]))

(defn- up
  [m]
  (first
   (:up
    (migration/->migration
     {:id :migrationz
      :up m
      :down [[:table/drop :account]]}))))

(def migration-create-table-if-not-exists
  [[:table/create {:table :user :if-not-exists true}
    [:column/add [:name :string]]]])

(def migration-auto-uuid
  [[:table/create {:table :user :primary-key :uuid}
    [:column/add [:name :string]]]])

(def migration-no-primary-key
  [[:table/create {:table :user :primary-key false}
    [:column/add [:name :string]]]])

(def migration-types
  [[:table/create {:table :user :primary-key false}
    [:column/add [:name :string]]
    [:column/add [:last-name {:optional true} :string]]
    [:column/add [:code {:size 4 :default "ABCD"} :string]]
    [:column/add [:age :int]]
    [:column/add [:s :serial]]
    [:column/add [:b :bigserial]]
    [:column/add [:active :boolean]]
    [:column/add [:created-at {:default :current-timestamp} :timestamp]]]])

(def migration-alter
  [[:table/alter {:table :user}
    [:column/add [:name :string]]
    [:column/add [:last-name {:optional true} :string]]
    [:column/add [:code {:size 4 :default "ABCD"} :string]]
    [:column/add [:age :int]]
    [:column/add [:s :serial]]
    [:column/add [:b :bigserial]]
    [:column/add [:active :boolean]]
    [:column/add [:created-at {:default :current-timestamp} :timestamp]]]])

(def migration-drop+add
  [[:table/alter {:table :user}
    [:column/drop :name]
    [:column/drop :last-name]
    [:column/drop :code]
    [:column/add [:gungnir/timestamps]]]])

(deftest testing-migrations
  (let [m-exists (up migration-create-table-if-not-exists)
        m-uuid (up migration-auto-uuid)
        m-no-pk (up migration-no-primary-key)]
    (is (string/includes? m-exists "CREATE TABLE IF NOT EXISTS \"user\""))
    (is (string/includes? m-exists "\"id\" BIGSERIAL PRIMARY KEY NOT NULL"))
    (is (string/includes? m-uuid "\"id\" \"uuid\" DEFAULT uuid_generate_v4() PRIMARY KEY NOT NULL"))
    (is (not (string/includes? m-no-pk "\"id\"")))))

(deftest testing-migration-types
  (let [m-types (up migration-types)]
    (is (string/includes? m-types "\"name\" \"text\" NOT NULL,"))
    (is (string/includes? m-types "\"last_name\" \"text\","))
    (is (string/includes? m-types "\"code\" varchar(4) DEFAULT 'ABCD' NOT NULL,"))
    (is (string/includes? m-types "\"s\" SERIAL NOT NULL,"))
    (is (string/includes? m-types "\"b\" BIGSERIAL NOT NULL,"))
    (is (string/includes? m-types "\"active\" \"boolean\" NOT NULL,"))
    (is (string/includes? m-types "\"created_at\" \"timestamp\" DEFAULT CURRENT_TIMESTAMP NOT NULL"))))

(deftest testing-migration-alter
  (let [m-types (up migration-alter)]
    (is (string/includes? m-types "\"name\" \"text\" NOT NULL,"))
    (is (string/includes? m-types "\"last_name\" \"text\","))
    (is (string/includes? m-types "\"code\" varchar(4) DEFAULT 'ABCD' NOT NULL,"))
    (is (string/includes? m-types "\"s\" SERIAL NOT NULL,"))
    (is (string/includes? m-types "\"b\" BIGSERIAL NOT NULL,"))
    (is (string/includes? m-types "\"active\" \"boolean\" NOT NULL,"))
    (is (string/includes? m-types "\"created_at\" \"timestamp\" DEFAULT CURRENT_TIMESTAMP NOT NULL"))))

(deftest testing-migration-drop
  (let [m-types (up migration-drop+add)]
    (is (string/includes? m-types "DROP COLUMN \"name\""))
    (is (string/includes? m-types "DROP COLUMN \"last_name\""))
    (is (string/includes? m-types "DROP COLUMN \"code\""))
    (is (string/includes? m-types "\"created_at\" \"timestamp\" DEFAULT CURRENT_TIMESTAMP NOT NULL"))
    (is (string/includes? m-types "\"updated_at\" \"timestamp\" DEFAULT CURRENT_TIMESTAMP NOT NULL"))))
