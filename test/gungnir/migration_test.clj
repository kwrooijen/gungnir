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
    [:column/add [:name :text]]]])

(def migration-auto-uuid
  [[:table/create {:table :user :primary-key :uuid}
    [:column/add [:name :text]]]])

(def migration-no-primary-key
  [[:table/create {:table :user :primary-key false}
    [:column/add [:name :text]]]])

(def migration-types
  [[:table/create {:table :user :primary-key false}
    [:column/add [:name :text]]
    [:column/add [:last-name {:optional true} :text]]
    [:column/add [:age :integer]]
    [:column/add [:created_at {:default :current-timestamp} :timestamp]]]])

(deftest testing-migrations
  (let [m-exists (up migration-create-table-if-not-exists)
        m-uuid (up migration-auto-uuid)
        m-no-pk (up migration-no-primary-key)
        m-types (up migration-types)]
    (is (string/includes? m-exists "CREATE TABLE IF NOT EXISTS \"user\""))
    (is (string/includes? m-exists "\"id\" BIGSERIAL PRIMARY KEY NOT NULL"))
    (is (string/includes? m-uuid "\"id\" \"uuid\" DEFAULT uuid_generate_v4() PRIMARY KEY NOT NULL"))
    (is (not (string/includes? m-no-pk "\"id\"")))
    (is (string/includes? m-types "\"name\" \"text\" NOT NULL,"))
    (is (string/includes? m-types "\"last_name\" \"text\","))
    (is (string/includes? m-types "\"created_at\" \"timestamp\" DEFAULT CURRENT_TIMESTAMP NOT NULL"))))
