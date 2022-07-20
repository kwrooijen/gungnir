(ns gungnir.query
  (:refer-clojure :exclude [update find])
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as string]
   [gungnir.database]
   [gungnir.decode]
   [gungnir.field]
   [gungnir.model]
   [gungnir.record]
   [honey.sql.helpers :as q]
   [honey.sql :as sql]))

(s/def :honeysql/map map?)

(s/def ::args.all
  (s/alt :arity-1
         (s/cat :?table (s/or :table simple-keyword?
                              :form map?))
         :arity-2
         (s/cat :?form (s/or :form map?
                             :field qualified-keyword?)
                :args (s/* any?))))

(s/def ::args.find-by
  (s/cat :?form (s/or :form map?
                      :field qualified-keyword?)
         :args (s/* any?)))

(s/def ::args.find
  (s/alt
   :arity-1
   (s/cat :form map?)

   :arity-2 (s/cat :model-k simple-keyword?
                   :primary-key-value any?)

   :arity-3
   (s/cat :form map?
          :model-k simple-keyword?
          :primary-key-value any?)))

(defn- args->map [args]
  (->> args
       (partition 2)
       flatten
       (apply hash-map)))

(defn- process-arguments [form args]
  (if (map? form)
    [form (args->map args)]
    [{} (args->map (conj args form))]))

(defn- args->where [model args]
  (into [:and] (mapv (fn [[k v]]
                       (if (keyword? v)
                         [:= k (str v)]
                         [:= k v]))
                     (gungnir.decode/advanced-decode model args))))

(defn load!
  "Load the relations `field-keys` of `record`, but retain the structure."
  [record & field-keys]
  (reduce (fn [acc k]
            (if (get acc k)
              (clojure.core/update acc k deref)
              acc))
          record
          field-keys))

(s/fdef delete!
  :args (s/alt
         :arity-1 (s/cat :form map?)
         :arity-2 (s/cat :form map? :datasource :sql/datasource))
  :ret boolean?)
(defn delete!
  "Delete a row from the database based on `record` which can either
  be a namespaced map or relational atom. The row will be deleted based on it's
  `primary-key`. Return `true` on deletion. If no match is found return
  `false`."
  ([form] (delete! form gungnir.database/*datasource*))
  ([form datasource]
   (gungnir.database/delete! form datasource)))

(s/fdef insert!
  :args (s/alt
         :arity-1 (s/cat :changeset :gungnir/changeset)
         :arity-2 (s/cat :changeset :gungnir/changeset
                         :datasource :sql/datasource))
  :ret (s/or :changeset :gungnir/changeset
             :record map?))
(defn insert!
  "Insert the value of `:changeset/result` in `changeset`.

  If `:changeset/errors` is not `nil` this function will have **no**
  side effects. Instead it will return the changeset as is.

  If during insertion an error occurs, the changeset will be
  returned with the errors inserted in the `:changeset/errors` key.
  "
  ([changeset] (insert! changeset gungnir.database/*datasource*))
  ([changeset datasource]
   (gungnir.database/insert! changeset datasource)))

(s/fdef update!
  :args (s/alt
         :arity-1 (s/cat :changeset :gungnir/changeset)
         :arity-2 (s/cat :changeset :gungnir/changeset
                         :datasource :sql/datasource))
  :ret (s/or :changeset :gungnir/changeset
             :record map?))
(defn update!
  "Insert the value of `:changeset/result` in `changeset`.

  If `:changeset/errors` is not `nil` this function will have **no**
  side effects. Instead it will return the changeset as is.

  If during the update an error occurs, the changeset will be
  returned with the errors updated in the `:changeset/errors` key.
  "
  ([changeset] (update! changeset gungnir.database/*datasource*))
  ([changeset datasource]
   (gungnir.database/update! changeset datasource)))

(s/fdef save!
  :args (s/alt
         :arity-1 (s/cat :changeset :gungnir/changeset)
         :arity-2 (s/cat :changeset :gungnir/changeset
                         :datasource :sql/datasource))
  :ret (s/or :changeset :gungnir/changeset
             :record map?))
(defn save!
  "Insert or update the value of `:changeset/result` in `changeset`. If
  no `primary-key` is present, the record will be inserted, otherwise
  the existing record will be updated based on the `:changeset/diff`
  fields.

  If `:changeset/errors` is not `nil` this function will have **no**
  side effects. Instead it will return the changeset as is.

  If during insert / update an error occurs, the changeset will be
  returned with the errors inserted in the `:changeset/errors` key.
  "
  ([changeset]
   (save! changeset gungnir.database/*datasource*))
  ([{:changeset/keys [result] :as changeset} datasource]
   (if (some? (gungnir.record/primary-key-value result))
     (gungnir.database/update! changeset datasource)
     (gungnir.database/insert! changeset datasource))) )

(s/fdef all
  :args ::args.all
  :ret :honeysql/map)
(defn all
  "Run a query and return a collection of records.

  Depending on the types of arguments provided, `all!` will have
  different behaviors.

  ## Arity 1

  `?table` - either a `simple-keyword` representing a Gungnir which
  will return all rows of that table. Or it a `HoneySQL` form query
  the database using that.

  ```clojure
  (all :user)

  (-> (select :*)
      (from :user)
      (all))
  ```

  ## Arity 2

  `?form` - either a `HoneySQL` form which will extend the query. Or a
  `qualified-keyword` representing a model field.

  `args` - a collection of keys and values. Where the keys are
  `qualified-keywords` representing a model fields which will be
  matched with the values. If no key value pairs are provided then you
  must supply a model name as a `simple-keyword`.

  ```clojure
  (all :user/validated false
       :user/type :user/pro)

  (-> (where [:> :user/date expiration-date])
      (all :user))
  ```
  "
  ([?table]
   (if (map? ?table)
     ?table
     (-> (q/select :*)
         (q/from ?table))))
  ([?form & args]
   (if (or (and (map? ?form)
                (> (count args) 1))
           (and (keyword? ?form)
                (= 1 (count args))))
     (let [[form args] (process-arguments ?form args)
           model (gungnir.record/model args)]
       (cond-> form
         (not (:select form)) (q/select :*)
         true (q/from (gungnir.record/table args))
         true (q/where (args->where model args))))

     ;; If only table is given, and no conditionals
     (cond-> (if (map? ?form) ?form {})
       (not (:select ?form)) (q/select :*)
       true (q/from (first args))))))

(s/fdef all!
  :args ::args.all
  :ret (s/coll-of map?))
(defn all!
  "Same as `gungnir.query/all` but executes the query with the global
  datasource."
  ([& args]
   (gungnir.database/query! (apply all args))))


(s/fdef find-by
  :args ::args.find-by
  :ret :honeysql/map)
(defn find-by
  "Run a query and return a single record or nil, based on matching keys and
  values.

  ```clojure
  (find-by :user/email \"user@test.com\"
          :user/validated true)
  ```

  Optionally extend the queries using HoneySQL

  ```clojure
  (-> (select :user/username)
      (find-by :user/email \"user@test.com\"
               :user/validated true))
  ```
  "
  ([?form & args]
   (let [[form args] (process-arguments ?form args)
         model (gungnir.record/model args)]
     (cond-> form
       (not (:select form)) (q/select :*)
       true (q/from (gungnir.model/table model))
       true (q/where (args->where model args))))))

(s/fdef find-by!
  :args ::args.find-by
  :ret (s/nilable map?))
(defn find-by!
  "Same as `gungnir.query/find-by` but executes the query with the
  global datasource."
  ([& args]
   (gungnir.database/query-1! (apply find-by args))))

(s/fdef find
  :args ::args.find
  :ret (s/nilable map?))
(defn find
  "Run a query and return a single record or nil.

  Depending on the types of arguments provided, `find` will have
  different behaviors.

  ## Arity 1

  Use `form` to query the database and return a single record or
  nil. Will not find a record by it's primary-key.

  `form` - HoneySQL form which will be used to query the database.

  ```clojure
  (-> (select :*)
      (from :user)
      (where [:= :user/id user-id])
      (find))
  ```
  ## Arity 2

  Find a record by it's primary-key from the table represented by the
  `model-key`.

  `model-key` - Model key which will identify which table to read from.

  `primary-key-value` - The value of the primary key to match with.

  ```clojure
  (find :user user-id)
  ```

  ## Arity 3

  Find a record by it's primary-key from the table represented by the
  `model-key`. Extended with the HoneySQL `form`.

  `form` - HoneySQL form which will be used to query the database.

  `model-key` - Model key which will identify which table to read from.

  `primary-key-value` - The value of the primary key to match with.


  Find a single record by its `primary-key-value` from `table`.
  Optionally extend the query using a HoneySQL `form`.

  ```clojure
  (-> (select :user/email)
      (where [:= :user/active false])
      (find :user user-id))
  ```
  "
  ([form] form)
  ([model-key primary-key-value] (find {} model-key primary-key-value))
  ([form model-key primary-key-value]
   (cond-> form
     (not (:select form)) (q/select :*)
     true (q/from model-key)
     true (q/where [:= (gungnir.model/primary-key model-key)
                          (gungnir.database/try-uuid primary-key-value)]))))

(s/fdef find!
  :args ::args.find
  :ret (s/nilable map?))
(defn find!
  "Same as `gungnir.query/find` but executes the query with the global
  datasource."
  [& args]
  (gungnir.database/query-1!
   (apply find args)))

;; HoneySQL Overrides
;; TODO override q/where with custom before-read method

(defmacro alias-honey-sql-functions []
  `(do
     ~@(for [[n# f#] (ns-publics (the-ns 'honey.sql.helpers))]
         `(def ~n# ~f#))))

(alias-honey-sql-functions)
