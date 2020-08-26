(ns gungnir.transaction
  (:require
   [next.jdbc :as jdbc]
   [gungnir.database :refer [*datasource* *tx-datasource*]]))

(defmulti pipeline-handler
  "TODO"
  (fn [k _ _] k))

(defn ^boolean error?
  ""
  [?error]
  (boolean (:transaction/error ?error)))

(defn error
  "TODO"
  [v]
  {:transaction/error v})

(defn changeset->error
  "TODO"
  [changeset]
  (when-let [error (:changeset/error changeset)]
    {:transaction/error error}))

(defn- apply-pipeline [pipeline state]
  (vec
   (reduce
    (fn [{:transaction/keys [state] :as acc} [k function
                                              ;; & args
                                              ]]
      (let [result (function state)
            ;; (pipeline-handler k state args)
            ]
        (if (error? result)
          (reduced (assoc acc :transaction/error [k result]))
          (-> acc
              (update :transaction/pipeline conj [k result])
              (update :transaction/results assoc k result)
              (assoc :transaction/state result)))))
    {:transaction/state state}
    pipeline)))

(defn execute-pipeline!
  "TODO"
  ([pipeline] (execute-pipeline! pipeline {} *datasource*))
  ([pipeline args] (execute-pipeline! pipeline args *datasource*))
  ([pipeline args datasource]
   (jdbc/transact
    datasource
    (fn [connection]
      (binding [*tx-datasource* connection]
        (apply-pipeline pipeline args))))))

(defn execute!
  "TODO"
  ([function] (execute! function *datasource*))
  ([function datasource]
   (jdbc/transact
    datasource
    (fn [connection]
      (binding [*tx-datasource* connection]
        (function))))))

(comment
  (require '[gungnir.query :as q])
  (require '[gungnir.changeset :refer [changeset]])


  ;; With multimethods

  (defmethod pipeline-handler :retrieve-accounts [_ state [sender-id recipient-id]]
    (let [sender (q/find-by! :account/id sender-id)
          recipient (q/find-by! :account/id recipient-id)
          ids #{(:account/id sender) (:account/id recipient)}]
      (if (and sender recipient)
        (assoc state
               :sender sender
               :recipient recipient)
        (gungnir.transaction/error [:account-not-found (remove ids [sender-id recipient-id])]))))

  (defmethod pipeline-handler :verify-balance [_ {:keys [sender] :as state} [amount]]
    (if (>= (:account/amount sender) amount)
      state
      (gungnir.transaction/error [:balance-too-low sender])))

  (defmethod pipeline-handler :subtract-from-sender [_ {:keys [sender] :as state} [amount]]
    (-> (update sender :account/amount - amount)
        (changeset)
        (q/save!)
        (changeset->error)
        (or state)))

  (defmethod pipeline-handler :add-to-recipient
    [_ {:keys [recipient] :as state} [amount]]
    (-> (update recipient :account/amount - amount)
        (changeset)
        (q/save!)
        (changeset->error)
        (or state)))

  (defn pipeline--transfer-money [sender-id recipient-id amount]
    [[:retrieve-accounts sender-id recipient-id]
     [:verify-balance amount]
     [:subtract-from-sender amount]
     [:add-to-recipient amount]])








;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  ;; With basic function








  (defn retrieve-accounts [sender-id recipient-id]
    (fn [state]
      (let [sender (q/find-by! :account/id sender-id)
            recipient (q/find-by! :account/id recipient-id)
            ids #{(:account/id sender) (:account/id recipient)}]
        (if (and sender recipient)
          (assoc state
                 :sender sender
                 :recipient recipient)
          (gungnir.transaction/error [:account-not-found (remove ids [sender-id recipient-id])])))))

  (defn verify-balance [amount]
    (fn [{:keys [sender] :as state}]
      (if (>= (:account/balance sender) amount)
        state
        (gungnir.transaction/error [:balance-too-low sender]))))

  (defn subtract-from-sender [amount]
    (fn [{:keys [sender] :as state}]
      (-> sender
          (changeset {:account/balance (- (:account/balance sender) amount)})
          (q/save!)
          (changeset->error)
          (or state))))

  (defn add-to-recipient [amount]
    (fn [{:keys [recipient] :as state}]
      (-> (update recipient :account/balance - amount)
          (changeset {:account/balance (+ (:account/balance recipient) amount)})
          (q/save!)
          (changeset->error)
          (or state))))

  (defn pipeline--transfer-money [sender-id recipient-id amount]
    [[:retrieve-accounts (retrieve-accounts sender-id recipient-id)]
     [:verify-balance (verify-balance amount)]
     [:subtract-from-sender (subtract-from-sender amount)]
     [:add-to-recipient (add-to-recipient amount)]])


  (:transaction/error
   (execute-pipeline!
    (pipeline--transfer-money (:account/id (first  (q/all! :account)))
                              (:account/id (second (q/all! :account)))
                              20)))

  ;; (->
  ;;  (second (q/all! :account))
  ;;  (update :account/balance - 20)
  ;;  ;; (changeset)
  ;;  ;; (q/save!)
  ;;  )
  ;;
  )
