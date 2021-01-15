(ns gungnir.migration
  (:require
   [clojure.string :as string]
   [gungnir.database :refer [*datasource*]]
   [honeysql-postgres.format]
   [honeysql-postgres.helpers :as psqlh]
   [honeysql.core :as sql]
   [honeysql.helpers]
   [ragtime.core]
   [ragtime.jdbc]
   [ragtime.reporter]
   [ragtime.strategy]))

  (s/def :gungnir.migration.action/name qualified-keyword?)
  (s/def :gungnir.migration.action/opts map?)
  (s/def :gungnir.migration.action/arg any?)

  (s/def :gungnir.migration/up
    (s/or :raw string?
          :action-2 (s/tuple :gungnir.migration.action/name
                             (s/or :opts :gungnir.migration.action/opts
                                   :args (s/* :gungnir.migration.action/arg)))
          :action-3 (s/tuple :gungnir.migration.action/name
                             :gungnir.migration.action/opts
                             (s/* :gungnir.migration.action/arg))))

  (s/def :gungnir/migration
    (s/keys :req-un [:gungnir.migration/up
                     :gungnir.migration/down]))

(defn- special-format [expr]
  (-> expr
      (sql/format :namespace-as-table? true
                  :quoting :ansi)
      (update 0 #(string/replace % "?" "%s"))
      (->> (apply format) (merge []))))

(defn- has-primary-key? [fields]
  (some (comp :primary-key second) fields))

(defn- pk-caller [opts]
  (when (:primary-key opts)
    (sql/call :primary-key)))

(defn- optional-caller [opts]
  (when-not (:optional opts)
    (sql/call :not nil)))

(defn- add-to-acc
  [acc v]
  (->> v
       (remove nil?)
       (conj acc)))

(defn- add-default-pk [fields]
  (if (has-primary-key? fields)
    fields
    (cons [:column/add [:id {:primary-key true} :bigserial]] fields)))

(defmulti process-table-add--column (fn [_acc [k field]] [k (last field)]))

(defmulti process-table-alter--column (fn [_acc [k field]]
                                        (if (#{:column/drop} k)
                                          k
                                          [k (last field)])))

(defmulti process-action first)

(defmethod process-table-add--column [:column/add :serial] [acc [_ [column opts _]]]
  (->> [column "SERIAL"
        (pk-caller opts)
        (optional-caller opts)]
       (add-to-acc acc)))

(defmethod process-table-add--column [:column/add :bigserial] [acc [_ [column opts _]]]
  (->> [column "BIGSERIAL"
        (pk-caller opts)
        (optional-caller opts)]
       (add-to-acc acc)))

(defmethod process-table-add--column [:column/add :uuid] [acc [_ [column opts _]]]
  (->> [column :uuid
        (sql/call :default "uuid_generate_v4()")
        (pk-caller opts)
        (optional-caller opts)]
       (add-to-acc acc)))

(defmethod process-table-add--column [:column/add :text] [acc [_ [column opts _]]]
  (->> [column :text
        (when-let [default (:default opts)]
          (sql/call :default (format "'%s'" default)))
        (pk-caller opts)
        (optional-caller opts)]
       (add-to-acc acc)))

(defmethod process-table-add--column [:column/add :timestamp] [acc [_ [column opts _]]]
  (let [defaults {:current-timestamp "CURRENT_TIMESTAMP"}]
    (->> [column "TIMESTAMP"
          (when-let [default (:default opts)]
            (sql/call :default (get defaults default default)))
          (pk-caller opts)
          (optional-caller opts)]
         (add-to-acc acc))))

(defmethod process-table-add--column [:column/add :ragnar/timestamps] [acc [_ [_]]]
  (conj acc
        [:created_at "TIMESTAMP" (sql/call :default "CURRENT_TIMESTAMP")]
        [:updated_at "TIMESTAMP" (sql/call :default "CURRENT_TIMESTAMP")]))


(defmethod process-table-alter--column [:column/add :uuid] [m [_ [column opts _]]]
  ;; TODO
  )

(defmethod process-table-alter--column [:column/add :text] [m [_ [column opts _]]]
  (->> [(when-let [default (:default opts)]
          (sql/call :default (format "'%s'" default)))
        (pk-caller opts)
        (optional-caller opts)]
       ;;
       (remove nil?)
       (apply psqlh/add-column m column :text)))

(defmethod process-table-alter--column :column/drop [m [_ & columns]]
  (reduce psqlh/drop-column m (flatten columns)))

(defmethod process-action :table/create [[_ {:keys [table]} & fields]]
  (-> (psqlh/create-table table)
      (psqlh/with-columns (reduce process-table-add--column [] (add-default-pk fields)))
      (special-format)))

(defmethod process-action :table/alter [[_ {:keys [table]} & fields]]
  (-> (reduce process-table-alter--column
              (psqlh/alter-table table)
              fields)
      (special-format)))

(defmethod process-action :table/drop [[_ & tables]]
  (->> (flatten tables)
       (apply psqlh/drop-table)
       (sql/format)))

(defn- raw-action? [action]
  (string? (first action)))

(defn- process-action-pre [action]
  (if (raw-action? action)
    action
    (process-action action)))

(defn ->migrations [migrations]
  (->> migrations
       (mapv #(update % :up process-action-pre))
       (mapv #(update % :down process-action-pre))
       (mapv ragtime.jdbc/sql-migration)))

(defn migrate-all
  "TODO"
  [migrations]
  (let [migrations (->migrations migrations)]
    (ragtime.core/migrate-all
     (ragtime.jdbc/sql-database {:datasource *datasource*})
     (ragtime.core/into-index {} migrations)
     migrations
     {:strategy ragtime.strategy/raise-error
      :reporter ragtime.reporter/print})))

(defn rollback
  "TODO"
  [migrations]
  (let [migrations (->migrations migrations)]
    (ragtime.core/rollback-last
     (ragtime.jdbc/sql-database {:datasource *datasource*})
     (ragtime.core/into-index {} migrations))))

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
  [:extension/add :uuid-ossp]

  (def uuid-m
    {:id "uuid"
     :up ["CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";"]
     :down ["DROP EXTENSION \"uuid-ossp\";"]}
    ;; TODO
    ;; {:id "uuid"
    ;;  :up [:extension/create :uuid-ossp]
    ;;  :down [:extension/drop :uuid-ossp]}
    )

  (def users-1
    {:id "users-1"
     :up
     [:table/create {:table :users}
      [:column/add [:email :text]]
      [:column/add [:ragnar/timestamps]]]
     :down [:table/drop :users]})

  (def users-2
    {:id "users-2"
     :up
     [:table/alter {:table :users}
      [:column/add [:plan {:default ":plan/free"} :text]]]
     :down
     [:table/alter {:table :users}
      [:column/drop :email]]})

  (migrate-all [uuid-m users-1 users-2])
  (rollback [uuid-m users-1 users-2])
  ;;
  )
