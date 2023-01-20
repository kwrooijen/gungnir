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
  [[:table/create {:table :account :if-not-exists true}
    [:column/add [:name :string]]]])

(def migration-auto-uuid
  [[:table/create {:table :account :primary-key :uuid}
    [:column/add [:name :string]]]])

(def migration-no-primary-key
  [[:table/create {:table :account :primary-key false}
    [:column/add [:name :string]]]])

(def migration-types
  [[:table/create {:table :account :primary-key false}
    [:column/add
     [:name :string]
     [:last-name {:optional true} :string]
     [:code {:size 4 :default "ABCD"} :string]
     [:age :int]
     [:s :serial]
     [:b :bigserial]
     [:active :boolean]
     [:some-float :float]
     [:some-float-4 {:size 4} :float]
     [:created-at {:default :current-timestamp} :timestamp]]]])

(def migration-alter
  [[:table/alter {:table :account}
    [:column/add
     [:name :string]
     [:last-name {:optional true} :string]
     [:code {:size 4 :default "ABCD"} :string]
     [:age :int]
     [:s :serial]
     [:b :bigserial]
     [:active :boolean]
     [:created-at {:default :current-timestamp} :timestamp]]]])

(def migration-drop+add
  [[:table/alter {:table :account}
    [:column/drop :name :last-name :code]
    [:column/add [:gungnir/timestamps]]]])


(def migration-drop-gungnir-timestamps
  [[:table/alter {:table :account}
    [:column/drop :gungnir/timestamps]]])

(deftest testing-migrations
  (let [m-exists (up migration-create-table-if-not-exists)
        m-uuid (up migration-auto-uuid)
        m-no-pk (up migration-no-primary-key)]
    (is (string/includes? (string/lower-case m-exists) "create table if not exists \"account\""))
    (is (string/includes? (string/lower-case m-exists) "id bigserial primary key not null"))
    (is (string/includes? (string/lower-case m-uuid) "id uuid default uuid_generate_v4() primary key not null"))
    (is (not (string/includes? (string/lower-case m-no-pk) "id ")))))

(deftest testing-migration-types
  (let [m-types (up migration-types)]
    (is (string/includes? (string/lower-case m-types) "name text not null,"))
    (is (string/includes? (string/lower-case m-types) "last_name text"))
    (is (string/includes? (string/lower-case m-types) "code varchar(4) default 'abcd' not null"))
    (is (string/includes? (string/lower-case m-types) "s serial not null"))
    (is (string/includes? (string/lower-case m-types) "b bigserial not null"))
    (is (string/includes? (string/lower-case m-types) "active boolean not null"))
    (is (string/includes? (string/lower-case m-types) "some_float float(8) not null"))
    (is (string/includes? (string/lower-case m-types) "some_float_4 float(4) not null"))
    (is (string/includes? (string/lower-case m-types) "created_at timestamp default current_timestamp not null"))))

(deftest testing-migration-alter
  (let [m-types (up migration-alter)]
    (is (string/includes? (string/lower-case m-types) "name text not null"))
    (is (string/includes? (string/lower-case m-types) "last_name text"))
    (is (string/includes? (string/lower-case m-types) "code varchar(4) default 'abcd' not null"))
    (is (string/includes? (string/lower-case m-types) "s serial not null"))
    (is (string/includes? (string/lower-case m-types) "b bigserial not null"))
    (is (string/includes? (string/lower-case m-types) "active boolean not null"))
    (is (string/includes? (string/lower-case m-types) "created_at timestamp default current_timestamp not null"))))

(deftest testing-migration-drop+add
  (let [m-types (up migration-drop+add)]
    (is (string/includes? (string/lower-case m-types) "drop column name"))
    (is (string/includes? (string/lower-case m-types) "drop column last_name"))
    (is (string/includes? (string/lower-case m-types) "drop column code"))
    (is (string/includes? (string/lower-case m-types) " created_at timestamp default current_timestamp not null"))
    (is (string/includes? (string/lower-case m-types) " updated_at timestamp default current_timestamp not null"))))

(deftest testing-migration-drop-gungnir-timestamps
  (let [m-types (up migration-drop-gungnir-timestamps)]
    (is (string/includes? (string/lower-case m-types) "drop column created_at"))
    (is (string/includes? (string/lower-case m-types) "drop column updated_at"))))
