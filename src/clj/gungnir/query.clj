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
   [honeysql.format :as fmt]
   [honeysql.helpers :as q]))

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

(def ^{:doc "Delete a row from the database based on `record` which can either
  be a namespaced map or relational atom. The row will be deleted based on it's
  `primary-key`. Return `true` on deletion. If no match is found return
  `false`."}
  delete! gungnir.database/delete!)

(s/fdef save!
  :args (s/cat :changeset :gungnir/changeset)
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
  [{:changeset/keys [result] :as changeset}]
  (if (some? (gungnir.record/primary-key-value result))
    (gungnir.database/update! changeset)
    (gungnir.database/insert! changeset)))

(s/fdef all!
  :args
  (s/alt :arity-1
         (s/cat :?table (s/or :table simple-keyword?
                              :form map?))
         :arity-2
         (s/cat :?form (s/or :form map?
                             :field qualified-keyword?)
                :args (s/* any?)))
  :ret (s/coll-of map?))
(defn all!
  "Run a query and return a collection of records.

  Depending on the types of arguments provided, `all!` will have
  different behaviors.

  ## Arity 1

  `?table` - either a `simple-keyword` representing a Gungnir which
  will return all rows of that table. Or it a `HoneySQL` form query
  the database using that.

  ```clojure
  (all! :user)

  (-> (select :*)
      (from :user)
      (all!))
  ```

  ## Arity 2

  `?form` - either a `HoneySQL` form which will extend the query. Or a
  `qualified-keyword` representing a model field.

  `args` - a collection of keys and values. Where the keys are
  `qualified-keywords` representing a model fields which will be
  matched with the values. If no key value pairs are provided then you
  must supply a model name as a `simple-keyword`.

  ```clojure
  (all! :user/validated false
        :user/type :user/pro)

  (-> (where [:> :user/date expiration-date])
      (all! :user))
  ```
  "
  ([?table]
   (if (map? ?table)
     (gungnir.database/query! ?table)
     (-> (q/select :*)
         (q/from ?table)
         (gungnir.database/query!))))
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
         true (q/merge-where (args->where model args))
         true (gungnir.database/query!)))

     ;; If only table is given, and no conditionals
     (cond-> (if (map? ?form) ?form {})
       (not (:select ?form)) (q/select :*)
       true (q/from (first args))
       true (gungnir.database/query!)))))

(s/fdef find-by!
  :args (s/cat :?form (s/or :form map?
                            :field qualified-keyword?)
               :args (s/* any?))
  :ret (s/nilable map?))
(defn find-by!
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
       true (q/merge-where (args->where model args))
       true (gungnir.database/query-1!)))))

(s/fdef find!
  :args
  (s/alt
   :arity-1
   (s/cat :form map?)

   :arity-2 (s/cat :model-k simple-keyword?
                   :primary-key-value any?)

   :arity-3
   (s/cat :form map?
          :model-k simple-keyword?
          :primary-key-value any?))
  :ret (s/nilable map?))
(defn find!
  "Run a query and return a single record or nil.

  Depending on the types of arguments provided, `find!` will have
  different behaviors.

  ## Arity 1

  Use `form` to query the database and return a single record or
  nil. Will not find a record by it's primary-key.

  `form` - HoneySQL form which will be used to query the database.

  ```clojure
  (-> (select :*)
      (from :user)
      (where [:= :user/id user-id])
      (find!))
  ```
  ## Arity 2

  Find a record by it's primary-key from the table represented by the
  `model-key`.

  `model-key` - Model key which will identify which table to read from.

  `primary-key-value` - The value of the primary key to match with.

  ```clojure
  (find! :user user-id)
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
      (find! :user user-id))
  ```
  "
  ([form] (gungnir.database/query-1! form))
  ([model-key primary-key-value] (find! {} model-key primary-key-value))
  ([form model-key primary-key-value]
   (cond-> form
     (not (:select form)) (q/select :*)
     true (q/from (gungnir.model/table model-key))
     true (q/merge-where [:= (gungnir.model/primary-key model-key)
                          (gungnir.database/try-uuid primary-key-value)])
     true (gungnir.database/query-1!))))

;; HoneySQL Overrides

(def ^{:dynamic true
       :private true
       :doc "Gugnir's `before-save` hook should only be applied to values once.
This dynamic variable keeps track if a conditional check is being recurred. This
happens when you have more than 1 value to compare to.
e.g. `[:= :user/age 20 20]`"}
  recurred? false)

(defn- expand-binary-ops [op & args]
  (binding [recurred? true]
    (str "("
         (string/join " AND "
                      (for [[a b] (partition 2 1 args)]
                        (fmt/fn-handler op a b)))
         ")")))

(defn- apply-before-read-fns [b before-read-fns]
  (reduce #(gungnir.model/before-read %2 %1) b before-read-fns))

(defn- handle-before-read [a b more]
  (if recurred?
    [b more]
    (let [before-read-fns (gungnir.field/before-read a)]
      [(apply-before-read-fns b before-read-fns)
       (map #(apply-before-read-fns % before-read-fns) more)])))

(defmethod fmt/fn-handler "=" [_ a b & more]
  (let [[b more] (handle-before-read a b more)]
    (if (seq more)
      (apply expand-binary-ops "=" a b more)
      (cond
        (nil? a) (str (fmt/to-sql-value b) " IS NULL")
        (nil? b) (str (fmt/to-sql-value a) " IS NULL")
        :else (str (fmt/to-sql-value a) " = " (fmt/to-sql-value b))))))

(defmethod fmt/fn-handler "<>" [_ a b & more]
  (let [[b more] (handle-before-read a b more)]
    (if (seq more)
      (apply expand-binary-ops "<>" a b more)
      (cond
        (nil? a) (str (fmt/to-sql-value b) " IS NOT NULL")
        (nil? b) (str (fmt/to-sql-value a) " IS NOT NULL")
        :else (str (fmt/to-sql-value a) " <> " (fmt/to-sql-value b))))))

(defmethod fmt/fn-handler "<" [_ a b & more]
  (let [[b more] (handle-before-read a b more)]
    (if (seq more)
      (apply expand-binary-ops "<" a b more)
      (str (fmt/to-sql-value a) " < " (fmt/to-sql-value b)))))

(defmethod fmt/fn-handler "<=" [_ a b & more]
  (let [[b more] (handle-before-read a b more)]
    (if (seq more)
      (apply expand-binary-ops "<=" a b more)
      (str (fmt/to-sql-value a) " <= " (fmt/to-sql-value b)))))

(defmethod fmt/fn-handler ">" [_ a b & more]
  (let [[b more] (handle-before-read a b more)]
    (if (seq more)
      (apply expand-binary-ops ">" a b more)
      (str (fmt/to-sql-value a) " > " (fmt/to-sql-value b)))))

(defmethod fmt/fn-handler ">=" [_ a b & more]
  (let [[b more] (handle-before-read a b more)]
    (if (seq more)
      (apply expand-binary-ops ">=" a b more)
      (str (fmt/to-sql-value a) " >= " (fmt/to-sql-value b)))))

;; HoneySQL Aliases

(def build-clause q/build-clause)
(def collify q/collify)
(def columns q/columns)
(def composite q/composite)
(def cross-join q/cross-join)
(def delete q/delete)
(def delete-from q/delete-from)
(def from q/from)
(def full-join q/full-join)
(def group q/group)
(def having q/having)
(def insert-into q/insert-into)
(def join q/join)
(def left-join q/left-join)
(def limit q/limit)
(def lock q/lock)
(def merge-columns q/merge-columns)
(def merge-cross-join q/merge-cross-join)
(def merge-from q/merge-from)
(def merge-full-join q/merge-full-join)
(def merge-group-by q/merge-group-by)
(def merge-having q/merge-having)
(def merge-join q/merge-join)
(def merge-left-join q/merge-left-join)
(def merge-modifiers q/merge-modifiers)
(def merge-order-by q/merge-order-by)
(def merge-right-join q/merge-right-join)
(def merge-select q/merge-select)
(def merge-values q/merge-values)
(def merge-where q/merge-where)
(def modifiers q/modifiers)
(def offset q/offset)
(def order-by q/order-by)
(def plain-map? q/plain-map?)
(def query-values q/query-values)
(def right-join q/right-join)
(def select q/select)
(def set0 q/set0)
(def set1 q/set1)
(def sset q/sset)
(def truncate q/truncate)
(def un-select q/un-select)
(def update q/update)
(def values q/values)
(def where q/where)
(def with q/with)
(def with-recursive q/with-recursive)
