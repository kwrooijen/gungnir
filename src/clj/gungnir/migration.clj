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



;; (defmethod fmt/format-modifiers :uuid [[_ x] _]
;;   ""
;;   )

;; (sql/format (sql/call :uuid ))

(def migrations-path
  (.getCanonicalPath (clojure.java.io/file "./gungnir/migrations")))


(def formatter (java.time.format.DateTimeFormatter/ofPattern "yyyyMMddHHmmss") )

(defn current-timestamp [] (str (.format (java.time.LocalDateTime/now) formatter)))


(defn create-migration
  ([name]
   (create-migration name ""))
  ([name contents]
   (let [file-name (str (current-timestamp) "-" name ".edn")
         file-path (str migrations-path "/" file-name )]

     (println (str "Creating migration " file-name " at " file-path))
     (io/make-parents file-path)
     (spit file-path contents))))

(defn migrations []
  (sort
   (filter
    #(= "edn" (last (str/split %  #"\.")))
    (map #(.getCanonicalPath %) (.listFiles(io/file migrations-path))))))

(defmulti process-migration first )

(defmethod process-migration :raw [raw-vector]
  (let [[_ raw-sql] raw-vector]
    [raw-sql]))

(defmethod process-migration :table/create [create-vector]
  (let [[action table-name & columns] create-vector]
    (-> (psqlh/create-table (symbol table-name))
        (psqlh/with-columns (mapv process-migration columns))
        (sql/format))))

(defn process-column-options [options]
  (remove nil?
          [

           (:type options) ;NOTE this should be (apply sql/call (:type options)) if it's an seq, for data types like varchar that need a parameter (varchar(5))
           (if (:primary-key options) (sql/call :primary-key))
           ; More options here later
           ]
          ))
;  Still need to process arguments for type better for example varchar(12)

(defmethod process-migration :table/drop [drop-vector]
  (sql/format (apply psqlh/drop-table (map symbol (drop 1 drop-vector)))))


(defmethod process-migration :table/column [column-vector]
  (let [[action options column-name] column-vector]
    (concat [(symbol column-name)] (process-column-options options))
    ))

(def modification-vector [:table/modify "user"
                          [:table.remove/column "password"]
                          [:table.add/column {:type :text} "email"]])

(defmethod process-migration :table.add/column [column-vector]
  (let [[_ options column-name] column-vector]
    (psqlh/add-column (symbol column-name) (:type options))))

(defmethod process-migration :table.remove/column [column-vector]
  (psqlh/drop-column (symbol (nth column-vector 1))))


(defmethod process-migration :table/modify [modification-vector]
  (let [[_ table-name & modifications] modification-vector]
    [(str/join " "
               (concat (sql/format (psqlh/alter-table (symbol table-name)))
                       [(str/join ", " (mapcat (comp sql/format process-migration) modifications ))]))]
    ))

(def read-file (comp edn/read-string slurp))

(defn file->sql-migration [file]
  (let [name (.getName file)

        id (last (re-find #"^(\d*)-" name))
        content (read-file (.getCanonicalPath file))]
    (rj/sql-migration {:id id
                      :up (process-migration (:up content) )
                      :down (process-migration (:down content) )})))


(defn sql-migrations []
  (mapv (comp file->sql-migration io/file) (migrations)))

(def n (nth (sql-migrations) 2))

(sql-migrations)

(gungnir.database/make-datasource! "postgres://risk@localhost:5432/postgres")

(defn migrate-all []
  (rc/migrate-all
   (rj/sql-database
    {:datasource gungnir.database/*database*})
   (rc/into-index {} (sql-migrations))
   (sql-migrations)
   {:strategy ragtime.strategy/raise-error
    :reporter ragtime.reporter/print}))


(migrate-all)


(comment


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

  (defn extract-sql [edn]
    (sql/format (process-migration edn)))


  (process-migration (:up (edn/read-string(slurp (first (migrations))))))

  (map (comp edn/read-string slurp) (migrations))

  (defmethod r/load-files ".clj" [files]
    (for [file files]
      ;; Create an `SqlMigration` record for each file
      ))

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

  (sql/format )

  (create-migration "remove-user-email-add-password"
                   )

  (current-timestamp)

  (def x 2)

  (def e (+ 2 1))

  (s/valid? even? e)

  (s/valid? nil? nil)

  (s/def ::greater_than_3 #(> % 3))
  (s/valid? ::greater_than_3 2)


  )
