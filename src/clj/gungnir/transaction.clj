(ns gungnir.transaction
  (:require
   [clojure.spec.alpha :as s]
   [gungnir.database :refer [*datasource* *tx-datasource*]]
   [next.jdbc :as jdbc]))

(s/fdef error?
  :args (s/cat :?error any?)
  :ret boolean?)
(defn ^boolean error?
  "Returns true if `?error` contains a transaction error, otherwise
  returns false."
  [?error]
  (boolean (:transaction/error ?error)))

(s/fdef error
  :args (s/cat :e any?)
  :ret (s/map-of #{:transaction/error} any?))
(defn error
  "Returns a transaction error with the error `e`"
  [e]
  {:transaction/error e})

(s/fdef changeset->error
  :args (s/cat :?changeset (s/or :changeset :gungnir/changeset
                                 :any any?))
  :ret (s/nilable (s/map-of #{:transaction/error} any?)))
(defn changeset->error
  "Returns a transaction error if `changeset` has a `:changeset/error`
  key."
  [?changeset]
  (when-let [error (:changeset/error ?changeset)]
    {:transaction/error error}))

(defn- apply-pipeline [pipeline]
  (reduce
   (fn [{:transaction/keys [state] :as acc} [k f]]
     (let [result (f state)]
       (if (error? result)
         (reduced (-> acc
                      (assoc-in [:transaction/error :transaction.error/key] k)
                      (assoc-in [:transaction/error :transaction.error/data] (:transaction/error result))))
         (-> acc
             (update :transaction/pipeline conj [k result])
             (update :transaction/results assoc k result)
             (assoc :transaction/state result)))))
   {:transaction/state {}
    :transaction/error nil}
   pipeline))

(s/fdef execute-pipeline!
  :args
  (s/alt :arity-1 (s/cat :pipeline :transaction/pipeline)
         :arity-2 (s/cat :pipeline :transaction/pipeline
                         :datasource :sql/datasource))
  :ret any?)
(defn execute-pipeline!
  "Executes `pipeline` as arguments within a jdbc
  transaction using `datasource`. If no `datasource` is supplied then
  use the global `gungnir.database/*datasource*`."
  ([pipeline] (execute-pipeline! pipeline *datasource*))
  ([pipeline datasource]
   (jdbc/transact
    datasource
    (fn [connection]
      (binding [*tx-datasource* connection]
        (apply-pipeline pipeline))))))

(s/fdef execute
  :args
  (s/alt :arity-1 (s/cat :f fn?)
         :arity-2 (s/cat :f fn?
                         :datasource :sql/datasource))
  :ret any?)
(defn execute!
  "Executes `f` within a jdbc transaction using `datasource`. If no
  `datasource` is supplied then use the global
  `gungnir.database/*datasource*`."
  ([f] (execute! f *datasource*))
  ([f datasource]
   (jdbc/transact
    datasource
    (fn [connection]
      (binding [*tx-datasource* connection]
        (f))))))
