(ns gungnir.factory-test
  (:require
   [gungnir.test.util.database :as database :refer [datasource-opts-2]]
   [clojure.test :refer :all]
   [gungnir.query :as q]
   [gungnir.database :refer [*datasource*]]
   [gungnir.factory]
   [gungnir.test.util :as util]
   [gungnir.changeset :as changeset]))

(use-fixtures :once (fn [tests]
                      (let [datasource (:datasource (gungnir.factory/make-datasource-map! datasource-opts-2))]
                        (util/database-setup-once)
                        (util/database-setup-once datasource)
                        (.close datasource)
                        (tests))))

(use-fixtures :each (fn [tests]
                      (let [datasource (:datasource (gungnir.factory/make-datasource-map! datasource-opts-2))]
                        (util/database-setup-each)
                        (util/database-setup-each datasource)
                        (.close datasource)
                        (tests))))

(def account-1-email "account@test.com")

(def account-1-password "123456")

(def account-1
  {:account/email account-1-email
   :account/password account-1-password})

(def account-2-email "account-2@test.com")

(def account-2-password "123456")

(def account-2
  {:account/email account-2-email
   :account/password account-2-password})

(deftest local-datasource
  (let [{:keys [datasource all!-fn find!-fn save!-fn delete!-fn find-by!-fn close!-fn]}
        (gungnir.factory/make-datasource-map! datasource-opts-2)]
    (testing "creating datasource map"
      (is (not= datasource *datasource*) )
      (is (instance? javax.sql.DataSource datasource))
      (is (instance? javax.sql.DataSource *datasource*)))

    (testing "create account with local datasource map"
      (let [account (-> account-1 changeset/create save!-fn)]
        (is (nil? (:changeset/errors account)))
        (is (uuid? (:account/id account)))
        (is (some? (find!-fn :account (:account/id account))))
        ;; Should not be findable in global datasource
        (is (nil? (q/find! :account (:account/id account))))))

    (testing "deleting account locally"
      (let [account-1-2 (-> account-2 changeset/create q/save!)
            account-2-2 (-> account-2 changeset/create save!-fn)]
        (delete!-fn account-1-2)
        (delete!-fn account-2-2)
        (is (some? (q/find! :account (:account/id account-1-2))))))

    (testing "all posts locally"
      (let [{:account/keys [id]} (-> account-2 (assoc :account/email "foo@bar.baz") changeset/create save!-fn)]
        (save!-fn (changeset/create {:post/account-id id :post/title "" :post/content ""}))
        (save!-fn (changeset/create {:post/account-id id :post/title "" :post/content ""}))
        (is (seq (all!-fn :post)))
        (is  (not (seq (q/all! :post))))))

    (testing "find-by account locally"
      (let [{:account/keys [email]} (-> account-2 (assoc :account/email "foo@bar2.baz") changeset/create save!-fn)]
        (is (some? (find-by!-fn :account/email email)))
        (is (nil? (q/find-by! :account/email email)))))

    (testing "close datasource"
      (close!-fn)
      (is (.isClosed datasource)))))
