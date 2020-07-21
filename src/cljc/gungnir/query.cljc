(ns gungnir.query
  (:refer-clojure :exclude [update find])
  (:require
   #?(:clj gungnir.db)
   [malli.core :as m]
   [gungnir.core :as gungnir]
   [honeysql.format :as fmt]
   [honeysql.helpers :as q]
   [clojure.string :as string]))

#?(:clj (def insert! gungnir.db/insert!)
   :cljs (def insert! identity))

#?(:clj (def update! gungnir.db/update!)
   :cljs (def update! identity))

#?(:clj (def delete! gungnir.db/delete!)
   :cljs (def delete! identity))

#?(:clj (def query! gungnir.db/query!)
   :cljs (def query! identity))

#?(:clj (def query-1! gungnir.db/query-1!)
   :cljs (def query-1! identity))

;; TODO Save should look if the changeset has a primary-key. If it does, then it
;; should run the `update!` function. Otherwise it runs the `insert!` function.
;; (defn save! [changeset])

(defn- process-arguments [form args]
  (if (map? form)
    [form (partition 2 args)]
    [{} (partition 2 (conj args form))]))

(defn- args->where [model args]
  (into [:and] (mapv (fn [[k v]]
                       (if (keyword? v)
                         [:= k (str v)]
                         [:= k v]))
                     (->> args
                          flatten
                          (apply hash-map)
                          (gungnir/advanced-decode model)))))

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
         model (gungnir/record->model args)]
       (cond-> form
         (not (:select form)) (q/select :*)
         true (q/from (gungnir/record->table args))
         true (q/merge-where (args->where model args))
         true (query!)))

     ;; If only table is given, and no conditionals
     (cond-> (if (map? form) form {})
       (not (:select form)) (q/select :*)
       true (q/from (first args))
       true (query!)))))

(defn find-by!
  "Find a single record from `table`, where `args` are a key value pair of
  columns and values. Optionally extend the query using a HoneySQL `form`."
  ([form & args]
   (let [[form args] (process-arguments form args)
         model (gungnir/record->model args)]
     (cond-> form
       (not (:select form)) (q/select :*)
       true (q/from (:table (m/properties model)))
       true (q/merge-where (args->where model args))
       true (query-1!)))))

(defn try-uuid [?uuid]
  #?(:clj
     (if (string? ?uuid)
       (try (java.util.UUID/fromString ?uuid)
            (catch Exception _ ?uuid))
       ?uuid)
     :cljs ?uuid))

(defn find!
  "Find a single record by its `primary-key` from `table`.
  Optionally extend the query using a HoneySQL `form`. "
  ([table primary-key] (find! {} table primary-key))
  ([form table primary-key]
   (cond-> form
     (not (:select form)) (q/select :*)
     true (q/from table)
     true (q/merge-where [:= (gungnir/primary-key table) (try-uuid primary-key)])
     true (query-1!))))

;; HoneySQL Overrides

(def ^{:dynamic true
       :private true
       :doc "Gugnir's `before-save` hook should only be applied to values once.
This dynamic variable keeps track if a conditional check is being recurred. This
happens when you have more than 1 value to compare to.
e.g. `[:= :user/age 20 20]`"}
  recurred? false)

(declare recurred?)

(defn expand-binary-ops [op & args]
  (binding [recurred? true]
    (str "("
         (string/join " AND "
                      (for [[a b] (partition 2 1 args)]
                        (fmt/fn-handler op a b)))
         ")")))

(defn apply-before-read-fns [b before-read-fns]
  (reduce #(gungnir/before-read %2 %1) b before-read-fns))

(defn handle-before-read [a b more]
  (let [before-read-fns (gungnir/column->before-read a)]
    (if (or recurred? (empty? before-read-fns))
      [b more]
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
