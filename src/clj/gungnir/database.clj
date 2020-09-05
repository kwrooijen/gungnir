(ns gungnir.database
  (:require
   ;; NOTE [next.jdbc.date-time] Must be included to prevent date errors
   ;; https://cljdoc.org/d/seancorfield/next.jdbc/1.0.13/api/next.jdbc.date-time
   [clojure.spec.alpha :as s]
   [clj-database-url.core]
   [clojure.pprint]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [clojure.walk :as walk]
   [gungnir.database.builder]
   [gungnir.field]
   [gungnir.model]
   [gungnir.record]
   [hikari-cp.core :as hikari-cp]
   [honeysql.core :as sql]
   [honeysql.helpers :as q]
   [honeysql.types]
   [malli.core :as m]
   [next.jdbc :as jdbc]
   [next.jdbc.date-time]
   [next.jdbc.result-set :as result-set])
  (:import (java.sql SQLException)
           (org.postgresql.jdbc PgArray)))

(s/def :sql/datasource
  (partial instance? javax.sql.DataSource))

(defonce ^:dynamic *database* nil)

(declare query!)
(declare query-1!)

;; TODO can this be moved to a new namespace? gungnir.database.relation ?
(defrecord RelationAtom [type state datasource]
  clojure.lang.IAtom
  (reset [this f]
    (reset! (.-state this) f)
    this)
  (swap [this a]
    (swap! (.-state this) a)
    this)
  (swap [this a b]
    (swap! (.-state this) a b)
    this)
  (swap [this a b c]
    (swap! (.-state this) a b c)
    this)
  (swap [this a b c d]
    (swap! (.-state this) a b c d)
    this)
  (compareAndSet [this a b]
    (compare-and-set! (.-state this) a b)
    this)

  clojure.lang.IDeref
  (deref [this]
    (cond
      (#{:has-one} type) (-> this .-state deref (query-1! datasource))
      (#{:has-many} type) (-> this .-state deref (query! datasource))
      (#{:belongs-to} type) (-> this .-state deref (query-1! datasource)))))

(defmethod clojure.pprint/simple-dispatch RelationAtom [o]
  ((get-method clojure.pprint/simple-dispatch clojure.lang.IPersistentMap) o))

(defmethod print-method RelationAtom [_ ^java.io.Writer w]
  (.write w "<relation-atom>"))

(defn- relation-atom [type {:keys [model through]} primary-key datasource]
  (RelationAtom.
   type
   (atom {:select (list :*)
          :from (list (keyword model))
          :where [:= through primary-key]})
   datasource))

(defn- add-has-one [datasource primary-key record [k v]]
  (assoc record k (relation-atom :has-one v (get record primary-key) datasource)))

(defn- add-has-many [datasource primary-key record [k v]]
  (assoc record k (relation-atom :has-many v (get record primary-key) datasource)))

(defn- add-belongs-to [datasource _ record [k v]]
  (assoc record k (relation-atom
                   :belongs-to
                   (assoc v :through (gungnir.model/primary-key (:model v)))
                   (get record (:through v)) datasource)))

(defn- apply-relations
  [record {:keys [has-one has-many belongs-to primary-key]} datasource]
  (as-> record $
    (reduce (partial add-has-one datasource primary-key) $ has-one)
    (reduce (partial add-has-many datasource primary-key) $ has-many)
    (reduce (partial add-belongs-to datasource primary-key) $ belongs-to)))

(defn- get-relation [^clojure.lang.PersistentHashSet select
                    ^clojure.lang.PersistentArrayMap properties
                    ^clojure.lang.Keyword type]
  (when (= #{:*} select)
    (get properties type {})))

(defn- record->relation-data [form table]
  (let [model (get @gungnir.model/models (get @gungnir.model/table->model table))
        primary-key (gungnir.model/primary-key model)
        properties (m/properties model)
        select (set (:select form))]
    {:has-one (get-relation select properties :has-one)
     :has-many (get-relation select properties :has-many)
     :belongs-to (get-relation select properties :belongs-to)
     :table table
     :primary-key primary-key}))

(defn- process-query-row [form datasource row]
  (let [table (gungnir.record/table row)
        {:keys [has-one has-many belongs-to] :as relation-data}
        (record->relation-data form table)]
    (if (or (seq has-one)
            (seq has-many)
            (seq belongs-to))
      (apply-relations row relation-data datasource)
      row)))

(extend-protocol result-set/ReadableColumn
  PgArray
  (result-set/read-column-by-label ^PgArray [^PgArray v _]
    (vec (.getArray v)))
  (result-set/read-column-by-index ^PgArray [^PgArray v _2 _3]
    (vec (.getArray v))))

(defn- map-kv [f m]
  (into {} (map f m)))

(defn- transform-model-alias [?field]
  (cond
    (qualified-keyword? ?field)
    (get @gungnir.model/field->column ?field ?field)
    (simple-keyword? ?field)
    (get @gungnir.model/model->table ?field ?field)
    :else
    ?field))

(defn- honey->sql
  ([m] (honey->sql m {}))
  ([m opts]
   (sql/format
    (walk/postwalk transform-model-alias m)
    :namespace-as-table? (:namespace-as-table? opts true)
    :quoting :ansi)))

(defn- remove-quotes [s]
  (string/replace s #"\"" ""))

(defmulti exception->map
  (fn [^SQLException e]
    (.getSQLState e)))

(defn- sql-key->keyword [sql-key]
  (-> sql-key
      (string/replace #"_key$" "")
      (string/replace-first #"_" "/")
      (string/replace #"_" "-")
      (keyword)))

(defmethod exception->map "23505" [^SQLException e]
  (let [error (.getMessage e)
        sql-key (remove-quotes (re-find #"\".*\"" error))
        record-key (sql-key->keyword sql-key)]
    {record-key [(gungnir.model/format-error record-key :duplicate-key)]}))

(defmethod exception->map "42P01" [^SQLException e]
  (let [error (.getMessage e)
        sql-key (remove-quotes (re-find #"\".*\"" error))
        table-key (sql-key->keyword sql-key)]
    {table-key [(gungnir.model/format-error table-key :undefined-table)]}))

(defmethod exception->map :default [^SQLException e]
  (log/warn e (str "Unhandled SQL execption "
                (.getSQLState e) "\n "
                (.getMessage e)))
  {:unknown [(.getSQLState e)]})

(defn- execute-one!
  ([form changeset datasource] (execute-one! form changeset datasource {}))
  ([form changeset datasource opts]
   (try
     (jdbc/execute-one! datasource (honey->sql form opts)
                        {:return-keys true
                         :builder-fn gungnir.database.builder/column-builder})
     (catch SQLException e
       (log/log "gungnir.sql" :debug nil (honey->sql form))
       (update changeset :changeset/errors merge (exception->map e))))))

(defn- apply-before-save [field-k field-v]
  (reduce (fn [acc before-save-k]
            (gungnir.model/before-save before-save-k acc))
          field-v
          (gungnir.field/before-save field-k)))

(defn- parse-insert-value [k value]
  (cond
    (keyword? value)
    (str value)

    (vector? value)
    (honeysql.types/array (mapv (partial parse-insert-value k) value))

    :else
    value))

(defn- field->insert-value [[field-k field-v]]
  [field-k (->> (apply-before-save field-k field-v)
                (parse-insert-value field-k))])

(defn- record->insert-values [record]
  (map-kv field->insert-value record))

(defn- maybe-deref [record]
  (if (#{RelationAtom} (type record))
    @record
    record))

(def ^:private query-opts
  {:builder-fn gungnir.database.builder/column-builder})

(s/fdef try-uuid!
  :args (s/cat :?uuid any?)
  :ret any?)
(defn try-uuid
  "Try to convert `?uuid` to a `java.util.UUID` if it is a
  string. Otherwise return `?uuid` as supplied."
  [?uuid]
  (if (string? ?uuid)
    (try (java.util.UUID/fromString ?uuid)
         (catch Exception _ ?uuid))
    ?uuid))

(s/fdef insert!
  :args (s/alt
         :arity-1 (s/cat :changeset :gungnir/changeset)
         :arity-2 (s/cat :changeset :gungnir/changeset
                         :datasource :sql/datasource))
  :ret (s/or :changeset :gungnir/changeset
        :record map?))
(defn insert!
  "Insert a row based on the `changeset` provided. This function assumes
  that the `:changeset/result` key does not have a primary-key with a
  values. Returns the inserted row on succes. On failure return the
  `changeset` with an updated `:changeset/errors` key."
  ([changeset] (insert! changeset *database*))
  ([{:changeset/keys [model errors result] :as changeset} datasource]
   (if errors
     changeset
     (let [result (-> (q/insert-into (gungnir.model/table model))
                      (q/values [(record->insert-values result)])
                      (execute-one! changeset datasource))]
       (if (:changeset/errors result)
         result
         (process-query-row {:select '(:*)} datasource result))))))

(s/fdef update!
  :args (s/alt
         :arity-1 (s/cat :changeset :gungnir/changeset)
         :arity-2 (s/cat :changeset :gungnir/changeset
                         :datasource :sql/datasource))
  :ret (s/or :changeset :gungnir/changeset
             :record map?))
(defn update!
  "Update a row based on the `changeset` provided. This function assumes
  that the `:changeset/result` key has a primary-key with a
  values. Returns the updated row on succes. On failure return the
  `changeset` with an updated `:changeset/errors` key."
  ([changeset] (update! changeset *database*))
  ([{:changeset/keys [model errors diff origin transformed-origin] :as changeset} datasource]
   (cond
     errors changeset
     (empty? diff) origin
     :else
     (let [primary-key (gungnir.model/primary-key model)]
       (-> (q/update (gungnir.model/table model))
           (q/sset (record->insert-values diff))
           (q/where [:= primary-key (get transformed-origin primary-key)])
           (execute-one! changeset datasource {:namespace-as-table? false}))))))

(s/fdef delete!
  :args (s/alt
         :arity-1 (s/cat :form map?)
         :arity-2 (s/cat :form map? :datasource :sql/datasource))
  :ret boolean?)
(defn delete!
  "Delete a row from the database based on `record` which can either be
  a namespaced map or relational atom. The row will be deleted based
  on it's `primary-key`. Return `true` on deletion. If no match is
  found return `false`."
  ([record] (delete! record *database*))
  ([record datasource]
   (when-let [record (maybe-deref record)]
     (let [table (gungnir.record/table record)
           primary-key (gungnir.record/primary-key record)
           primary-key-value (gungnir.record/primary-key-value record)]
       (-> (q/delete-from table)
           (q/where [:= primary-key (try-uuid primary-key-value)])
           (honey->sql)
           (as-> sql (jdbc/execute-one! datasource sql {:builder-fn gungnir.database.builder/kebab-map-builder}))
           :next.jdbc/update-count
           (= 1))))))

(s/fdef query!
  :args (s/alt
         :arity-1 (s/cat :form map?)
         :arity-2 (s/cat :form map? :datasource :sql/datasource))
  :ret (s/coll-of map?))
(defn query!
  "Execute a query based on the HoneySQL `form` and return a collection
  of maps. If no result is found return an empty vector."
  ([form] (query! form *database*))
  ([form datasource]
   (reduce (fn [acc row]
             (->> (next.jdbc.result-set/datafiable-row row datasource query-opts)
                  (process-query-row form datasource)
                  (conj acc)))
           []
           (jdbc/plan datasource (honey->sql form) query-opts))))

(s/fdef query-1!
  :args (s/alt
         :arity-1 (s/cat :form map?)
         :arity-2 (s/cat :form map? :datasource :sql/datasource))
  :ret (s/nilable map?))
(defn query-1!
  "Execute a query based on the HoneySQL `form` and return a map. If no
  result is found return `nil`."
  ([form] (query-1! form *database*))
  ([form datasource]
   (when-let [row (jdbc/execute-one! datasource (honey->sql form) query-opts)]
     (process-query-row form datasource row))))

(s/fdef set-datasource!
  :args (s/cat :datasource :sql/datasource)
  :ret nil?)
(defn set-datasource!
  "Set the `datasource` to be used by Gungnir."
  [datasource]
  (when *database*
    (hikari-cp/close-datasource *database*))
  (alter-var-root #'gungnir.database/*database* (fn [_] datasource))
  nil)

(s/fdef build-datasource!
  :args
  (s/alt :arity-1
         (s/cat :?options (s/or :url string?
                                :options map?))
         :arity-2
         (s/cat :url string?
                :options map?))
  :ret :sql/datasource)
(defn build-datasource!
  "The following options are supported for `?options`
  * DATABASE_URL - The universal database url used by services such as Heroku / Render
  * JDBC_DATABASE_URL - The standard Java Database Connectivity URL
  * HikariCP configuration map - https://github.com/tomekw/hikari-cp#configuration-options
  When both `url` and `options` are supplied:
  `url` - DATABSE_URL or JDBC_DATABASE_URL
  `options` - HikariCP options
  "
  ([?options]
   (cond
     (map? ?options)
     (hikari-cp/make-datasource ?options)
     (string? ?options)
     (build-datasource! ?options {})))
  ([url options]
   (hikari-cp/make-datasource
    (merge options {:jdbc-url (clj-database-url.core/jdbc-database-url url)}))))
(s/fdef make-datasource!
  :args
  (s/alt :arity-1
         (s/cat :?options (s/or :url string?
                                :options map?))
         :arity-2
         (s/cat :url string?
                :options map?))
  :ret nil?)
(defn make-datasource!
  "Same as `build-datasource!` but also sets the created datasource globally."
  ([?options]
   (set-datasource! (build-datasource! ?options)))
  ([url options]
   (set-datasource! (build-datasource! url options))))
