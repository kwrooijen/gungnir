(ns gungnir.db
  (:refer-clojure)
  (:require
   ;; NOTE [next.jdbc.date-time] Must be included to prevent date errors
   ;; https://cljdoc.org/d/seancorfield/next.jdbc/1.0.13/api/next.jdbc.date-time
   [clojure.spec.alpha :as s]
   [gungnir.record]
   [gungnir.field]
   [gungnir.util.malli :as util.malli]
   [next.jdbc.date-time]
   [clojure.pprint]
   [clojure.string :as string]
   [gungnir.model]
   [hikari-cp.core :as hikari-cp]
   [honeysql.core :as sql]
   [honeysql.helpers :as q]
   [honeysql.types]
   [malli.core :as m]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as result-set])
  (:import (java.sql SQLException ResultSet)
           (org.postgresql.jdbc PgArray)))

(s/def :sql/datasource
  (partial instance? javax.sql.DataSource))

(defonce ^:dynamic *database* nil)

(s/fdef set-datasource!
  :args (s/cat :datasource :sql/datasource)
  :ret nil?)
(defn set-datasource!
  "Set the `datasource` to be used by Gungnir."
  [datasource]
  (when *database*
    (hikari-cp/close-datasource *database*))
  (alter-var-root #'gungnir.db/*database* (fn [_] datasource))
  nil)

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
     (set-datasource! (hikari-cp/make-datasource ?options))
     (string? ?options)
     ;; TODO support DATABASE_URL
     (set-datasource! (hikari-cp/make-datasource {:jdbc-url ?options}))))
  ([url options]
   (set-datasource! (hikari-cp/make-datasource (merge options {:jdbc-url url})))))

(declare query!)
(declare query-1!)

(defrecord RelationAtom [type state]
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
      (#{:has-one} type) (-> this .-state deref query-1!)
      (#{:has-many} type) (-> this .-state deref query!)
      (#{:belongs-to} type) (-> this .-state deref query-1!))))

(defmethod clojure.pprint/simple-dispatch RelationAtom [o]
  ((get-method clojure.pprint/simple-dispatch clojure.lang.IPersistentMap) o))

(defmethod print-method RelationAtom [_ ^java.io.Writer w]
  (.write w "<relation-atom>"))

(defn has-one-atom [t1 t2 primary-key]
  (RelationAtom.
   :has-one
   (atom {:select (list :*)
          :from (list t2)
          :where [:= (gungnir.model/belongs-to-relation-table t1 t2) primary-key]})))

(defn add-has-one [{:keys [table primary-key]} record [k v]]
  (assoc record v (has-one-atom table k primary-key)))

(defn has-many-atom [t1 t2 primary-key]
  (RelationAtom.
   :has-many
   (atom {:select (list :*)
          :from (list t2)
          :where [:= (gungnir.model/belongs-to-relation-table t1 t2) primary-key]})))

(defn add-has-many [{:keys [table primary-key]} record [k v]]
  (assoc record v (has-many-atom table k primary-key)))

(defn belongs-to-atom [t2 foreign-key]
  (RelationAtom.
   :belongs-to
   (atom {:select (list :*)
          :from (list (gungnir.model/table t2))
          :where [:= (gungnir.model/primary-key t2) foreign-key]})))

(defn add-belongs-to [{:keys [table]} record [k v]]
  (assoc record (keyword (name table) (name k)) (belongs-to-atom k (get record v))))

(defn apply-relations
  [record {:keys [has-one has-many belongs-to primary-key] :as relation-data}]
  (let [relation-data (assoc relation-data :primary-key (get record primary-key))]
    (as-> record $
      (reduce (partial add-has-one relation-data) $ has-one)
      (reduce (partial add-has-many relation-data) $ has-many)
      (reduce (partial add-belongs-to relation-data) $ belongs-to))))

(defn get-relation [^clojure.lang.PersistentHashSet select
                    ^clojure.lang.PersistentArrayMap properties
                    ^clojure.lang.Keyword type]
  (when (= #{:*} select)
    (get properties type {})))

(defn record->relation-data [form table]
  (let [model (gungnir.model/find table)
        primary-key (gungnir.model/primary-key model)
        properties (m/properties model)
        select (set (:select form))
        table (:table properties)]
    {:has-one (get-relation select properties :has-one)
     :has-many (get-relation select properties :has-many)
     :belongs-to (get-relation select properties :belongs-to)
     :table table
     :primary-key primary-key}))

(defn process-query-row [form row]
  (let [table (gungnir.record/table row)
        {:keys [has-one has-many belongs-to] :as relation-data}
        (record->relation-data form table)]
    (if (or (seq has-one)
            (seq has-many)
            (seq belongs-to))
      (apply-relations row relation-data)
      row)))

(extend-protocol result-set/ReadableColumn
  PgArray
  (result-set/read-column-by-label ^PgArray [^PgArray v _]
    (vec (.getArray v)))
  (result-set/read-column-by-index ^PgArray [^PgArray v _2 _3]
    (vec (.getArray v))))

(defn try-uuid [?uuid]
  (if (string? ?uuid)
    (try (java.util.UUID/fromString ?uuid)
         (catch Exception _ ?uuid))
    ?uuid))

(defn map-kv [f m]
  (into {} (map f m)))

(defn ->kebab [s]
  (string/replace s #"_" "-"))

(defn ->snake [s]
  (string/replace s #"-" "_"))

(defn- as-kebab-maps [rs opts]
  (let [opts (assoc opts :qualifier-fn ->kebab :label-fn ->kebab)]
    (result-set/as-modified-maps rs opts)))

(defn column-reader [builder ^ResultSet rs i]
  (let [column (nth (:cols builder) (dec i))
        after-read (:after-read (gungnir.field/properties column))]
    (when-let [value (.getObject rs i)]
      (if (seq after-read)
        (reduce (fn [v f] (gungnir.model/after-read f v))
                (if (#{PgArray} (type value)) (vec (.getArray value)) value)
                after-read)
        (result-set/read-column-by-index value (:rsmeta builder) i)))))

(defn- honey->sql
  ([m] (honey->sql m {}))
  ([m opts]
   (sql/format m
               :namespace-as-table? (:namespace-as-table? opts true)
               :quoting :ansi)))

(def ^:private execute-opts
  {:return-keys true
   :builder-fn (result-set/builder-adapter
                as-kebab-maps
                column-reader)})

(defn- remove-quotes [s]
  (string/replace s #"\"" ""))

(defmulti exception->map
  (fn [^SQLException e]
    [(.getErrorCode e)
     (.getSQLState e)]))

(defn sql-key->keyword [sql-key]
  (-> sql-key
      (string/replace #"_key$" "")
      (string/replace-first #"_" "/")
      (string/replace #"_" "-")
      (keyword)))

(defmethod exception->map [0 "23505"] [^SQLException e]
  (let [error (.getMessage e)
        sql-key (remove-quotes (re-find #"\".*\"" error))
        record-key (sql-key->keyword sql-key)]
    {record-key [(gungnir.model/format-error record-key :duplicate-key)]}))

(defmethod exception->map [0 "42P01"] [^SQLException e]
  (let [error (.getMessage e)
        sql-key (remove-quotes (re-find #"\".*\"" error))
        table-key (sql-key->keyword sql-key)]
    {table-key [(gungnir.model/format-error table-key :undefined-table)]}))

(defmethod exception->map :default [^SQLException e]
  (println "Unhandled SQL execption "
           (.getSQLState e) "\n "
           (.getMessage e))
  {:unknown [(.getSQLState e)]})

(defn execute-one!
  ([form changeset] (execute-one! form changeset {}))
  ([form changeset opts]
   (try
     (jdbc/execute-one! *database* (honey->sql form opts) execute-opts)
     (catch Exception e
       (println (honey->sql form))
       (update changeset :changeset/errors merge (exception->map e))))))

(defn before-save-keys [model k]
  (-> model
      (gungnir.model/child k)
      (util.malli/child-properties)
      (get :before-save [])))

(defn apply-before-save [model k v]
  (reduce (fn [acc before-save-k]
            (gungnir.model/before-save before-save-k acc))
          v
          (before-save-keys model k)))

(defn values-before-save [model values]
  (map-kv (fn [[k v]] [k (apply-before-save model k v)])
          values))

(defn parse-insert-value [model k value]
  (cond
    (keyword? value)
    (str value)

    (vector? value)
    (honeysql.types/array (mapv (partial parse-insert-value model k) value))

    :else
    value))

(defn model->insert-values [model result]
  (->> result
       (values-before-save model)
       (map-kv (fn [[k v]] [k (parse-insert-value model k v)]))))

(defn maybe-deref [record]
  (if (#{RelationAtom} (type record))
    @record
    record))

(defn insert! [{:changeset/keys [model errors result] :as changeset}]
  (if errors
    changeset
    (let [result (-> (q/insert-into (gungnir.model/table model))
                     (q/values [(model->insert-values model result)])
                     (execute-one! changeset))]
      (if (:changeset/errors result)
        result
        (process-query-row {:select '(:*)} result)))))

(defn update! [{:changeset/keys [model errors diff origin sane-origin] :as changeset}]
  (cond
    errors changeset
    (empty? diff) origin
    :else
    (let [primary-key (gungnir.model/primary-key model)]
      (-> (q/update (gungnir.model/table model))
          (q/sset (model->insert-values model diff))
          (q/where [:= primary-key (get sane-origin primary-key)])
          (execute-one! changeset {:namespace-as-table? false})))))

(defn delete! [record]
  (when-let [record (maybe-deref record)]
    (let [table (gungnir.record/table record)
          primary-key (gungnir.record/primary-key record)
          primary-key-value (gungnir.record/primary-key-value record)]
      (-> (q/delete-from table)
          (q/where [:= primary-key (try-uuid primary-key-value)])
          (honey->sql)
          (as-> sql (jdbc/execute-one! *database* sql {:builder-fn as-kebab-maps}))
          :next.jdbc/update-count
          (= 1)))))

(def ^:private query-opts
  {:builder-fn (result-set/builder-adapter
                as-kebab-maps
                column-reader)})


(defn query! [form]
  (reduce (fn [acc row]
            (->> (next.jdbc.result-set/datafiable-row row *database* query-opts)
                 (process-query-row form)
                 (conj acc)))
          []
          (jdbc/plan *database* (honey->sql form) query-opts)))

(defn query-1! [form]
  (when-let [row (jdbc/execute-one! *database* (honey->sql form) query-opts)]
    (process-query-row form row)))
