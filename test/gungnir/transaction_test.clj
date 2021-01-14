(ns gungnir.transaction-test
  (:require
   [clojure.test :refer :all]
   [gungnir.changeset :as changeset]
   [gungnir.transaction :as transaction]
   [gungnir.query :as q]
   [gungnir.test.util :as util]))

(use-fixtures :once util/once-fixture)
(use-fixtures :each util/each-fixture)

(defn retrieve-accounts [sender-id recipient-id]
  (fn [_state]
    (let [sender (q/find-by! :account/id sender-id)
          recipient (q/find-by! :account/id recipient-id)
          ids #{(:account/id sender) (:account/id recipient)}]
      (if (and sender recipient)
        {:sender sender
         :recipient recipient}
        (transaction/error
         {:account-not-found (remove ids [sender-id recipient-id])})))))

(defn verify-balance [amount]
  (fn [{:keys [sender] :as state}]
    (if (>= (:account/balance sender) amount)
      state
      (transaction/error {:balance-too-low sender}))))

(defn subtract-from-sender [amount]
  (fn [{:keys [sender] :as state}]
    (-> (changeset/update sender :account/balance #(- % amount))
        (q/save!)
        (transaction/changeset->error)
        (or state))))

(defn add-to-recipient [amount]
  (fn [{:keys [recipient] :as state}]
    (-> (changeset/update recipient :account/balance #(+ % amount))
        (q/save!)
        (transaction/changeset->error)
        (or state))))

(defn pipeline--transfer-money [sender-id recipient-id amount]
  [[:retrieve-accounts (retrieve-accounts sender-id recipient-id)]
   [:verify-balance (verify-balance amount)]
   [:subtract-from-sender (subtract-from-sender amount)]
   [:add-to-recipient (add-to-recipient amount)]])

(defn run-pipeline [id1 id2 amount]
  (transaction/execute-pipeline!
   (pipeline--transfer-money id1 id2 amount)))

(defn transfer-balance-fn
  [sender-id recipient-id amount]
  (let [account1 (q/find! :account sender-id)
        account2 (q/find! :account recipient-id)]

    (assert account1 :account-not-found)
    (assert (> (:account/balance account1) amount) :balance-too-low)
    (-> (changeset/create account1 {:account/balance (- (:account/balance account1) amount)}) q/save!)

    (assert account2 :account-not-found)
    (-> (changeset/create account2 {:account/balance (+ (:account/balance account1) amount)}) q/save!)))

(deftest test-transaction-pipeline!
  (let [id1 (-> {:account/balance 100} changeset/create q/save! :account/id)
        id2 (-> {:account/balance 100} changeset/create q/save! :account/id)]
    (testing "pipeline - Balance transaction"
      (run-pipeline id1 id2 20)
      (is (->> id1 (q/find! :account) :account/balance (= 80)))
      (is (->> id2 (q/find! :account) :account/balance (= 120))))

    (testing "pipeline - Not enough balance"
      (let [balance-too-low-error (-> (run-pipeline id1 id2 200)
                                      :transaction/error
                                      :transaction.error/data
                                      :balance-too-low)]
        (is (some? balance-too-low-error))
        (is (->> id1 (q/find! :account) :account/balance (= 80)))
        (is (->> id2 (q/find! :account) :account/balance (= 120)))))

    (testing "pipeline - Account not found"
      (let [balance-too-low-error (-> (run-pipeline (java.util.UUID/randomUUID) id2 200)
                                      :transaction/error
                                      :transaction.error/data
                                      :account-not-found)]
        (is (some? balance-too-low-error))
        (is (->> id1 (q/find! :account) :account/balance (= 80)))
        (is (->> id2 (q/find! :account) :account/balance (= 120)))))

    (testing "pipeline - Exception testing"
      (is (-> [[:do-something (fn [_] (assert false "WRONG"))]]
              (transaction/execute-pipeline!)
              :transaction/error
              :transaction.error/key
              (= :do-something))))))

(deftest test-transaction!
  (let [id1 (-> {:account/balance 100} changeset/create q/save! :account/id)
        id2 (-> {:account/balance 100} changeset/create q/save! :account/id)]
    (testing "transaction - Balance transaction"
      (transaction/execute! (fn [] (transfer-balance-fn id1 id2 20)))
      (is (->> (q/find! :account id1) :account/balance (= 80)))
      (is (->> (q/find! :account id2) :account/balance (= 120))))

    (testing "transaction - Not enough balance"
      (is (thrown? java.lang.AssertionError
                   (transaction/execute! (fn [] (transfer-balance-fn id1 id2 200)))))
      (is (->> (q/find! :account id1) :account/balance (= 80)))
      (is (->> (q/find! :account id2) :account/balance (= 120))))

    (testing "transaction - Account not found"
      (is (thrown? java.lang.AssertionError
                   (transaction/execute! (fn [] (transfer-balance-fn (java.util.UUID/randomUUID) id2 20)))))
      (is (->> (q/find! :account id1) :account/balance (= 80)))
      (is (->> (q/find! :account id2) :account/balance (= 120))))))
