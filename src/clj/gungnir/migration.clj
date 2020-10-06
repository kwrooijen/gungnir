(ns gungnir.migration
  (:require
   [gungnir.spec]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clojure.edn :as edn]
   [malli.core :as m]
   [malli.util :as mu]
   [ragtime.jdbc :as rj]
   [ragtime.core :as rc]
   [honeysql.core :as sql]
   [honeysql.format :as fmt]
   [honeysql.helpers :refer :all]
   [honeysql-postgres.format :refer :all]
   [honeysql-postgres.helpers :as psqlh]))


(def migrations-path
  (.getCanonicalPath (clojure.java.io/file "./gungnir/migrations")))


(def formatter (java.time.format.DateTimeFormatter/ofPattern "yyyyMMddHHmmss") )

(def read-file (comp edn/read-string slurp))

(defn- current-timestamp [] (str (.format (java.time.LocalDateTime/now) formatter)))

(defn- migrations []
  (sort
   (filter
    #(= "edn" (last (str/split %  #"\.")))
    (map #(.getCanonicalPath %) (.listFiles(io/file migrations-path))))))

(defn- sql-migrations []
  (mapv (comp file->sql-migration io/file) (migrations)))

(defmulti process-field-add (fn [_m [k opts _column]] [k (:type opts)]))
(defmulti process-field-modify (fn [_m [k opts _column]] [k (:type opts)]))

;;
;; TABLE/ADD FIELDS
;;
(defmethod process-field-add [:column/add :uuid] [m [_ opts column]]
  ;; Here we modify `m` to add a UUID column with name `column`
  )

(defmethod process-field-add [:column/add :text] [m [_ opts column]]
  ;; Here we modify `m` to add a text column with name `column`,
  ;; optionally :size in `opts'
  )

;;
;; TABLE/MODIFY FIELDS
;;
(defmethod process-field-modify [:column/add :uuid] [m [_ opts column]]
  ;; Here we modify `m` to add a UUID column with name `column`
  )
(defmethod process-field-modify [:column/add :text] [m [_ opts column]]
  ;; Here we modify `m` to add a text column with name `column`,
  ;; optionally :size in `opts'
  )


(defmulti process-migration first )

(defmethod process-migration :raw [raw-vector]
  (let [[_ raw-sql] raw-vector]
    [raw-sql]))

(defmethod process-migration :table/create [create-vector]
  (let [[action table-name & columns] create-vector]
    (-> (psqlh/create-table (symbol table-name))
        (psqlh/with-columns (mapv process-migration columns))
        (sql/format))))


(defmethod process-migration :table/drop [drop-vector]
  (sql/format (apply psqlh/drop-table (map symbol (drop 1 drop-vector)))))


(defmethod process-migration :table/column [column-vector]
  (let [[action options column-name] column-vector]
    (concat [(symbol column-name)] (process-column-options options))
    ))

(defmethod process-migration :table.add/column [column-vector]
  (let [[_ options column-name] column-vector]
    (psqlh/add-column (symbol column-name) (:type options))))

(defmethod process-migration :table.remove/column [column-vector]
  (psqlh/drop-column (symbol (nth column-vector 1))))


(defmethod process-migration :table/modify [modification-vector]
  (let [[_ table-name & modifications] modification-vector]
    [(str/join " "
               (concat (sql/format (psqlh/alter-table (symbol table-name)))
                       [(str/join ", " (mapcat
                                        (comp sql/format process-migration)
                                        modifications ))]))]
    ))


(defn create-migration
  ([name]
   (create-migration name ""))
  ([name contents]
   (let [file-name (str (current-timestamp) "-" name ".edn")
         file-path (str migrations-path "/" file-name )]

     (println (str "Creating migration " file-name " at " file-path))
     (io/make-parents file-path)
     (spit file-path contents))))



(defn file->sql-migration [file]
  (let [name (.getName file)
        id (last (re-find #"^(\d*)-" name))
        content (read-file (.getCanonicalPath file))]
    (rj/sql-migration {:id id
                       :up (process-migration (:up content) )
                       :down (process-migration (:down content))
                       })))

(defn migrate-all []
  (rc/migrate-all
   (rj/sql-database
    {:datasource gungnir.database/*database*})
   (rc/into-index {} (sql-migrations))
   (sql-migrations)
   {:strategy ragtime.strategy/raise-error
    :reporter ragtime.reporter/print}))


(comment

  (def modification-vector [:table/modify "user"
                            [:table.remove/column "password"]
                            [:table.add/column {:type :text} "email"]])

  (gungnir.database/make-datasource! "postgres://risk@localhost:5432/postgres")

  (migrate-all)

  (sql-migrations)

  (def n (nth (sql-migrations) 2))

  ; NOTE postgres does not like making a table user unless you put it in quotations because it's also a function (I think)
  (sql/format (psqlh/alter-table "user"))
  (sql/format (psqlh/alter-table :user))
                                        ;(-> (psqlh/alter-table :pandas) (psqlh/add-column :address :text)  (psqlh/add-column :address2 :text)  (psqlh/add-column :address3 :text) (sql/format))
  (str/join ", " (mapcat sql/format [ (psqlh/add-column :address :text)  (psqlh/add-column :address2 :text)  (psqlh/add-column :address3 :text)]))
                                        ;this shows why I couldn't do it the way it says in the documentation
  (sql/format (psqlh/add-column :address :text))
  (sql/format (psqlh/alter-table :pandas))
                                        ;NOTE I should probably move sql/format out of the progress-migration multimethods

  (file->sql-migration  (io/file (nth (migrations) 2)))

  (process-migration (:up (edn/read-string(slurp (first (migrations))))))

  (map (comp edn/read-string slurp) (migrations))

  (def migrations
    [(r/sql-migration
      {:id (str :uuid)
       :up ["CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";"]
       :down ["DROP EXTENSION \"uuid-ossp\";"]})

     (r/sql-migration
      {:id (str :comment) ;; NOTE `:id` must be a string
       :up
       ["CREATE TABLE IF NOT EXISTS comment
        ( id uuid DEFAULT uuid_generate_v4 () PRIMARY KEY
        , content TEXT
        );"]
       :down
       ["DROP TABLE comment;"]})])

  (create-migration "remove-user-email-add-password"  )

  (current-timestamp)
)
