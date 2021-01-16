(ns gungnir.migration
  (:require
   [honeysql.format :as sqlf]
   [gungnir.migration.honeysql-postgres :refer [create-extension drop-extension]]
   [clojure.string :as string]
   [gungnir.database :refer [*datasource*]]
   [honeysql-postgres.format]
   [honeysql-postgres.helpers :as psqlh]
   [honeysql.core :as sql]
   [ragtime.core]
   [ragtime.jdbc]
   [ragtime.reporter]
   [ragtime.strategy]))

(defn special-format [expr]
  (-> expr
      (sql/format :namespace-as-table? true
                  :quoting :ansi
                  :allow-dashed-names? sqlf/*allow-dashed-names?*)
      (update 0 #(string/replace % "?" "%s"))
      (->> (apply format))))

(defn- has-primary-key? [fields]
  (some (comp :primary-key second) fields))

(defn- pk-caller [opts]
  (when (:primary-key opts)
    (sql/call :primary-key)))

(defn- optional-caller [opts]
  (when-not (:optional opts)
    (sql/call :not nil)))

(defn- default-caller [opts]
  (when-let [default (:default opts)]
    (sql/call :default default)))

(defn- add-default-pk [fields]
  (if (has-primary-key? fields)
    fields
    (cons [:column/add [:id {:primary-key true} :bigserial]] fields)))

(defn- add-column [acc expr]
  (apply psqlh/add-column acc (remove nil? expr)))

(defn- add-create-column [acc expr]
  (conj acc (remove nil? expr)))

(defmulti process-table-column
  (fn [tk _acc [k field]]
    (if (coll? field)
      [tk k (last field)]
      [tk k])))

(defmulti process-action first)

(defn- column-serial [column opts]
  [column "SERIAL"
   (pk-caller opts)
   (optional-caller opts)])

(defmethod process-table-column [:table/create :column/add :serial]
  [_ acc [_ [column opts _]]]
  (add-create-column acc (column-serial column opts)))

(defmethod process-table-column [:table/alter :column/add :serial]
  [_ acc [_ [column opts _]]]
  (add-column acc (column-serial column opts)))

(defn- column-bigserial [column opts]
  [column "BIGSERIAL"
   (pk-caller opts)
   (optional-caller opts)])

(defmethod process-table-column [:table/create :column/add :bigserial]
  [_ acc [_ [column opts _]]]
  (add-create-column acc (column-bigserial column opts)))

(defmethod process-table-column [:table/alter :column/add :bigserial]
  [_ acc [_ [column opts _]]]
  (add-column acc (column-bigserial column opts)))

(defn- column-uuid [column opts]
  [column :uuid
   (when-let [default (:default opts)]
     (if (true? default)
       (sql/call :default "uuid_generate_v4()")
       (sql/call :default default)))
   (pk-caller opts)
   (optional-caller opts)])

(defmethod process-table-column [:table/create :column/add :uuid]
  [_ acc [_ [column opts _]]]
  (add-create-column acc (column-uuid column opts)))

(defmethod process-table-column [:table/alter :column/add :uuid]
  [_ acc [_ [column opts _]]]
  (add-column acc (column-uuid column opts)))

(defn- column-text [column opts]
  [column :text
   (when-let [default (:default opts)]
     (sql/call :default (format "'%s'" (string/escape default {\' "\\'"}))))
   (pk-caller opts)
   (optional-caller opts)])

(defmethod process-table-column [:table/create :column/add :text]
  [_ acc [_ [column opts _]]]
  (add-create-column acc (column-text column opts)))

(defmethod process-table-column [:table/alter :column/add :text]
  [_ acc [_ [column opts _]]]
  (add-column acc (column-text column opts)))

(defn- column-integer [column opts]
  [column :integer
   (default-caller opts)
   (pk-caller opts)
   (optional-caller opts)])

(defmethod process-table-column [:table/create :column/add :integer]
  [_ acc [_ [column opts _]]]
  (add-create-column acc (column-integer column opts)))

(defmethod process-table-column [:table/alter :column/add :integer]
  [_ acc [_ [column opts _]]]
  (add-column acc (column-integer column opts)))

(defn- column-boolean [column opts]
  [column :boolean
   (default-caller opts)
   (pk-caller opts)
   (optional-caller opts)])

(defmethod process-table-column [:table/create :column/add :boolean]
  [_ acc [_ [column opts _]]]
  (add-create-column acc (column-boolean column opts)))

(defmethod process-table-column [:table/alter :column/add :boolean]
  [_ acc [_ [column opts _]]]
  (add-column acc (column-boolean column opts)))

(defn- column-timestamp [column opts]
  (let [defaults {:current-timestamp "CURRENT_TIMESTAMP"}]
    [column "TIMESTAMP"
     (when-let [default (:default opts)]
       (sql/call :default (get defaults default default)))
     (pk-caller opts)
     (optional-caller opts)]))

(defmethod process-table-column [:table/create :column/add :timestamp]
  [_ acc [_ [column opts _]]]
  (add-create-column acc (column-timestamp column opts)))

(defmethod process-table-column [:table/alter :column/add :timestamp]
  [_ acc [_ [column opts _]]]
  (add-column acc (column-timestamp column opts)))

(defn- column-ragnar-timestamps []
  [[:created_at "TIMESTAMP" (sql/call :default "CURRENT_TIMESTAMP")]
   [:updated_at "TIMESTAMP" (sql/call :default "CURRENT_TIMESTAMP")]])

(defmethod process-table-column [:table/create :column/add :ragnar/timestamps]
  [_ acc [_ [_]]]
  (reduce add-create-column acc (column-ragnar-timestamps)))

(defmethod process-table-column [:table/alter :column/add :ragnar/timestamps]
  [_ acc [_ [_]]]
  (apply psqlh/add-column acc (flatten (column-ragnar-timestamps))))

(defmethod process-table-column [:table/alter :column/drop] [_ acc [_ & columns]]
  (apply psqlh/drop-column acc (flatten columns)))

(defmethod process-action :table/create [[_ {:keys [table]} & fields]]
  (let [columns (reduce (partial process-table-column :table/create) []
                        (add-default-pk fields))]
    (-> (psqlh/create-table table)
        (psqlh/with-columns columns)
        (special-format))))

(defmethod process-action :table/alter [[_ {:keys [table]} & fields]]
  (-> (reduce (partial process-table-column :table/alter)
              (psqlh/alter-table table)
              fields)
      (special-format)))

(defmethod process-action :table/drop [[_ & tables]]
  (->> (flatten tables)
       (apply psqlh/drop-table)
       (sql/format)
       (first)))

(defmethod process-action :extension/create [[_ {:keys [if-not-exists]} extension]]
  (binding [sqlf/*allow-dashed-names?* true]
    (-> extension
        (create-extension :if-not-exists if-not-exists)
        (special-format))))

(defmethod process-action :extension/drop [[_ extension]]
  (binding [sqlf/*allow-dashed-names?* true]
    (-> extension
        (drop-extension)
        (special-format))))

(defn- raw-action? [action]
  (string? action))

(defn- process-action-pre [action]
  (if (raw-action? action)
    action
    (process-action action)))

(defn ->migration [migration]
  (-> migration
      (update :up   (partial mapv process-action-pre))
      (update :down (partial mapv process-action-pre))
      ragtime.jdbc/sql-migration))

(defn migrate-all
  "TODO"
  ([migrations] (migrate-all migrations *datasource*))
  ([migrations datasource]
   (let [migrations (mapv ->migration migrations)]
     (ragtime.core/migrate-all
      (ragtime.jdbc/sql-database {:datasource datasource})
      (ragtime.core/into-index {} migrations)
      migrations
      {:strategy ragtime.strategy/raise-error
       :reporter ragtime.reporter/print}))))

(defn rollback
  "TODO"
  ([migrations] (rollback migrations *datasource*))
  ([migrations datasource]
   (let [migrations (mapv ->migration migrations)]
     (ragtime.core/rollback-last
      (ragtime.jdbc/sql-database {:datasource datasource})
      (ragtime.core/into-index {} migrations)))))

(comment
  (def ^:private datasource-opts-1
    {:adapter       "postgresql"
     :username      "postgres"
     :password      "postgres"
     :database-name "postgres"
     :server-name   "localhost"
     :port-number   5432})

  (gungnir.database/make-datasource! datasource-opts-1)

  (gungnir.database/close!)

  (def uuid-m
    {:id "uuid"
     :up [[:extension/create {:if-not-exists true} :uuid-ossp]]
     :down [[:extension/drop :uuid-ossp]]})

  (def users-1
    {:id "users-1"
     :up
     [[:table/create {:table :users}
       [:column/add [:email :text]]
       [:column/add [:ragnar/timestamps]]]]
     :down [[:table/drop :users]]})

  (def users-2
    {:id "users-2"
     :up
     [[:table/alter {:table :users}
       [:column/add [:plan {:default ":plan/free"} :text]]]]
     :down
     [[:table/alter {:table :users}
       [:column/drop :plan]]]})

  (def users-1+2
    {:id "users-1+2"
     :up
     [[:table/create {:table :users}
       [:column/add [:email :text]]
       [:column/add [:ragnar/timestamps]]]
      [:table/alter {:table :users}
       [:column/add [:plan {:default ":plan/free"} :text]]]]
     :down [[:table/drop :users]
            [:table/alter {:table :users}
             [:column/drop :plan]]]})

  (comment
    (mapv process-action (:up users-1))
    (process-action (:down users-1))
    (process-action (:up users-2))
    (process-action (:down users-2))
    (process-action (:up uuid-m))
    (->migration users-1)
    ;;
    )

  (migrate-all [uuid-m users-1 users-2])

  (migrate-all [uuid-m users-1+2])
  (rollback [uuid-m users-1 users-2])
  ;;
  )
