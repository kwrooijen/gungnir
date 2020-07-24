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

(def delete! gungnir.database/delete!)

(def query! gungnir.database/query!)

(def query-1! gungnir.database/query-1!)

(s/fdef save!
  :args (s/cat :changeset :gungnir/changeset)
  :ret (s/or :changeset :gungnir/changeset
             :record map?))
(defn save! [{:changeset/keys [result] :as changeset}]
     (if (some? (gungnir.record/primary-key-value result))
       (gungnir.database/update! changeset)
       (gungnir.database/insert! changeset)))

(s/fdef all!
  :args
  (s/alt :arity-1
         (s/cat :table simple-keyword?)

         :arity-2
         (s/cat :form (s/or :form map?
                            :field qualified-keyword?)
                :args (s/* any?)))
  :ret (s/coll-of map?))
(defn all!
  "Find multiple records from `table`, where `args` are a key value pair of
  columns and values. Optionally extend the query using a HoneySQL `form`."
  ([table]
   (-> (q/select :*)
       (q/from table)
       (query!)))
  ([form & args]
   (if (or (and (map? form)
                (> (count args) 1))
           (and (keyword? form)
                (= 1 (count args))))
     (let [[form args] (process-arguments form args)
           model (gungnir.record/model args)]
       (cond-> form
         (not (:select form)) (q/select :*)
         true (q/from (gungnir.record/table args))
         true (q/merge-where (args->where model args))
         true (query!)))

     ;; If only table is given, and no conditionals
     (cond-> (if (map? form) form {})
       (not (:select form)) (q/select :*)
       true (q/from (first args))
       true (query!)))))

(s/fdef find-by!
  :args (s/cat :form (s/or :form map?
                           :field qualified-keyword?)
               :args (s/* any?))
  :ret (s/nilable map?))
(defn find-by!
  "Find a single record from `table`, where `args` are a key value pair of
  columns and values. Optionally extend the query using a HoneySQL `form`."
  ([form & args]
   (let [[form args] (process-arguments form args)
         model (gungnir.record/model args)]
     (cond-> form
       (not (:select form)) (q/select :*)
       true (q/from (gungnir.model/table model))
       true (q/merge-where (args->where model args))
       true (gungnir.database/query-1!)))))

(s/fdef find!
  :args
  (s/alt :arity-2
         (s/cat :model-k simple-keyword?
                :primary-key-value any?)

         :arity-3
         (s/cat :form map?
                :model-k simple-keyword?
                :primary-key-value any?))
  :ret (s/nilable map?))
(defn find!
  "Find a single record by its `primary-key-value` from `table`.
  Optionally extend the query using a HoneySQL `form`. "
  ([model-k primary-key-value] (find! {} model-k primary-key-value))
  ([form model-k primary-key-value]
   (cond-> form
     (not (:select form)) (q/select :*)
     true (q/from (gungnir.model/table model-k))
     true (q/merge-where [:= (gungnir.model/primary-key model-k)
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
