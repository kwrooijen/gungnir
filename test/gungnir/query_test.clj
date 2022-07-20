(ns gungnir.query-test
  (:require
   [clojure.string :as string]
   [clojure.test :refer :all]
   [gungnir.changeset :as changeset]
   [gungnir.query :as q]
   [gungnir.test.util :as util]))

(use-fixtures :once util/once-fixture)
(use-fixtures :each util/each-fixture)

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

(def account-3-email "account-3@test.com")

(def account-3-password "133456")

(def account-3-accountname "foobar")

(def account-3
  {:account/email account-3-email
   :account/accountname account-3-accountname
   :account/password account-3-password})

(def post-1-title "post-1 title")
(def post-1-content "post-1 content")

(def post-1
  {:post/title post-1-title
   :post/content post-1-content})

(def post-2-title "post-2 title")
(def post-2-content "post-2 content")

(def post-2
  {:post/title post-2-title
   :post/content post-2-content})

(def post-3-title "post-3 title")
(def post-3-content "post-3 content")

(def post-3
  {:post/title post-3-title
   :post/content post-3-content})

(def comment-1-content "comment-1 content")

(def comment-1
  {:comment/content comment-1-content})

(def comment-2-content "comment-2 content")

(def comment-2
  {:comment/content comment-2-content})


(def comment-3-content "comment-3 content")

(def comment-3
  {:comment/content comment-3-content
   :comment/rating 45})

(def comment-4-content "comment-4 content")

(def comment-4
  {:comment/content comment-4-content
   :comment/rating 80})

(def token-1
  {:token/type :token/verify})

(def token-2
  {:token/type :token/reset})

(def document-1-title "document-1 title")
(def document-1-content "document-1 content")

(def document-1
  {:document/content document-1-content})

(deftest test-find!
  (let [{:account/keys [id]} (-> account-1 changeset/create q/save!)]
    (testing "Find account by primary key"
      (is (= account-1-email (-> (q/find! :account id) :account/email))))

    (testing "Find account by primary key, automatic uuid translation"
      (is (= account-1-email (-> (q/find! :account (str id)) :account/email)))))

  (testing "Find unknown account by primary key returns nil"
    (is (nil? (-> (q/find! :account #uuid "1e626bf3-8fdf-4a66-b708-7aa35dafede9"))))
    (is (nil? (-> (q/find! :account "1e626bf3-8fdf-4a66-b708-7aa35dafede9"))))))

(deftest test-find-by!
  (let [{:account/keys [id] :as account} (-> account-1 changeset/create q/save!)
        token (-> token-1 (assoc :token/account-id (:account/id account)) changeset/create q/save!)
        _post (-> post-1 (assoc :post/account-id id) changeset/create q/save!)]
    (testing "Find account by email"
      (is (= account-1-email (-> (q/find-by! :account/email account-1-email) :account/email))))

    (testing "Find account by unknown email returns nil"
      (is (nil? (q/find-by! :account/email "random@email.com"))))

    (testing "Find post by title and account-id"
      (is (= post-1-title
             (-> (q/find-by! :post/title post-1-title
                             :post/account-id id)
                 :post/title))))
    (testing "Find post by account-id with auto uuid"
      (is (= post-1-title
             (-> (q/find-by! :post/account-id (str id))
                 :post/title))))
    (testing "keyword arguments"
      (is (= (:token/id token)
             (-> (q/find-by! :token/type :token/verify)
                 :token/id))))))

(deftest test-all!
  (let [{:account/keys [id] :as _account} (-> account-1 changeset/create q/save!)
        _post-1 (-> post-1 (assoc :post/account-id id) changeset/create q/save!)
        _post-2 (-> post-2 (assoc :post/account-id id) changeset/create q/save!)]
    (testing "Find posts by account id"
      (is (= #{post-1-title post-2-title}
             (-> (q/all! :post/account-id id) (->> (mapv :post/title)) set))))

    (testing "Find posts by account id with limit 1"
      (is (= 1
             (-> (q/limit 1)
                 (q/all! :post/account-id id)
                 (count)))))

    (testing "Find posts by account id and title"
      (is (= post-1-title
             (-> (q/limit 1)
                 (q/all! :post/account-id id
                         :post/title post-1-title)
                 first
                 :post/title))))
    (testing "Find posts by table name"
      (is (= #{post-1-title post-2-title}
             (-> (q/all! :post)
                 (->> (map :post/title))
                 set))))

    (testing "Find posts by table name and limit"
      (is (= 1
             (-> (q/limit 1)
                 (q/all! :post)
                 (count)))))))

(deftest test-insert-save!
  (testing "inserting a new account"
    (let [account (-> account-1 changeset/create q/save!)]
      (is (nil? (:changeset/errors account)))
      (is (uuid? (:account/id account)))
      (is (some? (q/find! :account (:account/id account))))))

  (testing "inserting an invalid account"
    (let [account (-> account-1 (assoc :account/password "123") changeset/create q/save!)]
      (is (some? (:changeset/errors account)))
      (is (nil? (:account/id account)))
      (is (nil? (q/find! :account (:account/id account)))))))

(deftest test-update-save!
  (let [account (-> account-1 changeset/create q/save!)
        account-2 (-> account-2 changeset/create q/save! (update :account/id str))]
    (testing "updating an existing account"
      (let [new-email "account-updated@test.com"
            new-account (q/save! (changeset/create account {:account/email new-email}))]
        (is (nil? (:changeset/errors new-account)))
        (is (uuid? (:account/id new-account)))
        (is (some? (q/find! :account (:account/id account))))))

    (testing "updating an existing account with str uuid"
      (let [new-email "account-updated-2@test.com"
            new-account (q/save! (changeset/create account-2 {:account/email new-email}))]
        (is (nil? (:changeset/errors new-account)))
        (is (uuid? (:account/id new-account)))
        (is (some? (q/find! :account (:account/id account))))))

    (testing "updating an invalid account"
      (let [new-account (q/save! (changeset/create account {:account/password "123"}))]
        (is (some? (:changeset/errors new-account)))
        (is (nil? (:account/id new-account)))
        (is (= account-1-password (:account/password (q/find! :account (:account/id account)))))))

    (testing "don't update if no diff"
      (let [new-account (q/save! (changeset/create account {:account/email account-1-email}))]
        (is (= account new-account))))))

(deftest test-delete!
  (testing "deleting existing account"
    (let [account (-> account-1 changeset/create q/save!)]
      (is (= true (q/delete! account)))
      (is (nil? (q/find! :account (:account/id account))))))

  (testing "deleting existing account with str uuid"
    (let [account (-> account-1 changeset/create q/save! (update :account/id str))]
      (is (= true (q/delete! account)))
      (is (nil? (q/find! :account (:account/id account))))))

  (testing "deleting non existing account"
    (let [uuid "1e626bf3-8fdf-4a66-b708-7aa35dafede9"]
      (is (= false (q/delete! {:account/id uuid})))
      (is (nil? (q/find! :account uuid)))))

  (testing "deleting reference atom"
    (let [account (-> account-1 changeset/create q/save!)
          token (-> token-1 (assoc :token/account-id (:account/id account)) changeset/create q/save!)]
      (is (-> (q/find! :account (:account/id account)) :account/token (q/delete!)))
      (is (nil? (q/find! :token (:token/id token)))))))

(deftest test-relation-has-one
  (let [account (-> account-1 changeset/create q/save!)
        token (-> token-1 (assoc :token/account-id (:account/id account)) changeset/create q/save!)]
    (testing "account has one token"
      (is (= (:token/id token)
             (-> account
                 :account/token
                 (deref)
                 :token/id))))

    (testing "find! account has one token"
      (is (= (:token/id token)
             (-> (q/find! :account (:account/id account))
                 :account/token
                 (deref)
                 :token/id))))

    (testing "account has one token, back to account"
      (is (= (:account/id account)
             (-> account
                 :account/token
                 (deref)
                 :token/account
                 (deref)
                 :account/id))))

    (testing "find! account has one token, back to account"
      (is (= (:account/id account)
             (-> (q/find! :account (:account/id account))
                 :account/token
                 (deref)
                 :token/account
                 (deref)
                 :account/id))))))

(deftest test-relation-has-many
  (let [account (-> account-1 changeset/create q/save!)
        post-1 (-> post-1 (assoc :post/account-id (:account/id account)) changeset/create q/save!)
        post-2 (-> post-2 (assoc :post/account-id (:account/id account)) changeset/create q/save!)
        comment-1 (-> comment-1 (assoc :comment/account-id (:account/id account)
                                       :comment/post-id (:post/id post-1))
                      changeset/create
                      q/save!)
        comment-2 (-> comment-2 (assoc :comment/account-id (:account/id account)
                                       :comment/post-id (:post/id post-2))
                      changeset/create
                      q/save!)]
    (testing "account has many posts"
      (is (= #{(:post/id post-1) (:post/id post-2)}
             (-> account
                 :account/posts
                 (deref)
                 (->> (map :post/id))
                 (set)))))

    (testing "find! account has many posts"
      (is (= #{(:post/id post-1) (:post/id post-2)}
             (-> (q/find! :account (:account/id account))
                 :account/posts
                 (deref)
                 (->> (map :post/id))
                 (set)))))

    (testing "all! posts have many comments"
      (is (= #{(:comment/id comment-1) (:comment/id comment-2)}
             (-> (q/all! :post)
                 (->> (map (comp deref :post/comments)))
                 (flatten)
                 (->> (map :comment/id))
                 (set)))))))


(deftest test-relation-belongs-to
  (let [account (-> account-1 changeset/create q/save!)
        post (-> post-1 (assoc :post/account-id (:account/id account)) changeset/create q/save!)
        comment (-> comment-1
                    (assoc :comment/account-id (:account/id account)
                           :comment/post-id (:post/id post))
                    changeset/create
                    q/save!)]

    (testing "comment belongs to post"
      (is (= (:post/id post)
             (-> comment
                 :comment/post
                 (deref)
                 :post/id))))

    (testing "comment belongs to post, belongs to account"
      (is (= (:account/id account)
             (-> comment
                 :comment/post
                 (deref)
                 :post/account
                 (deref)
                 :account/id))))

    (testing "comment belongs to post, belongs to account, back to comment"
      (is (= (:comment/id comment)
             (-> comment
                 :comment/post
                 (deref)
                 :post/account
                 (deref)
                 :account/posts
                 (deref)
                 (first)
                 :post/comments
                 (deref)
                 (first)
                 :comment/id))))))

(deftest test-relation-belongs-to-multiple
  (let [account-1 (-> account-1 changeset/create q/save!)
        account-2 (-> account-2 changeset/create q/save!)
        document (-> document-1
                     (assoc :document/author-id (:account/id account-1)
                            :document/reviewer-id (:account/id account-2))
                     changeset/create
                     q/save!)]
    (testing "different accounts"
      (is (not= (-> document
                    :document/author
                    (deref))
                (-> document
                    :document/reviewer
                    (deref))))
      (is (some? (-> document
                     :document/author
                     (deref))))
      (is (some? (-> document
                     :document/reviewer
                     (deref)))))))

(deftest test-before-save
  (let [_account (-> account-1 (update :account/email string/upper-case) changeset/create q/save!)]
    (testing "saving email as lowercase"
      (let [result (q/find-by! :account/email account-1-email)]
        (is (= (string/lower-case (:account/email account-1)) (:account/email result)))))))

(deftest test-before-read
  (let [_account (-> account-1 changeset/create q/save!)]
    (testing "finding account by case-insensitive email"
      (let [result-1 (q/find-by! :account/email account-1-email)
            result-2 (q/find-by! :account/email (string/upper-case account-1-email))
            result-3 (q/find-by! :account/email (string/lower-case account-1-email))]
        (is (some? result-1))
        (is (some? result-2))
        (is (some? result-3))))))

(deftest test-after-read
  (let [account (-> account-1 changeset/create q/save!)
        token (-> token-1 (assoc :token/account-id (:account/id account)) changeset/create q/save!)]
    (testing "reading keywords"
      (let [{:token/keys [type]} (q/find-by! :token/id (:token/id token))]
        (is (= (:token/type token) (:token/type token-1) type))))))

(deftest test-duplicate-key
  (let [_ (-> account-1 changeset/create q/save!)
        account-1 (-> account-1 changeset/create q/save!)
        _ (-> account-3 changeset/create q/save!)
        account-2 (-> account-3 (assoc :account/email "some@random.email") changeset/create q/save!)]
    (testing "uniqueness of email"
      (is (= [:duplicate-key] (-> account-1 :changeset/errors :account/email))))

    (testing "custom error message for duplicate-key"
      (is (= ["accountname taken"] (-> account-2 :changeset/errors :account/accountname))))))

(deftest test-custom-where-clause
  (let [account-1 (-> account-1 changeset/create q/save!)
        post-1 (-> post-1 (assoc :post/account-id (:account/id account-1)) changeset/create q/save!)
        post-2 (-> post-2 (assoc :post/account-id (:account/id account-1)) changeset/create q/save!)
        comment-3 (-> comment-3 (assoc :comment/account-id (:account/id account-1)
                                       :comment/post-id (:post/id post-1))
                      changeset/create
                      q/save!)
        comment-4 (-> comment-4
                      (assoc :comment/account-id (:account/id account-1)
                             :comment/post-id (:post/id post-2))
                      changeset/create
                      q/save!)]


    (testing "all! with custom where clause"
      (is (= [(:comment/id comment-3)]
             (-> (q/where [:<> :comment/id (:comment/id comment-4)])
                 (q/all! :comment)
                 (->> (mapv :comment/id))))))

    (testing "all! with custom where clause and nested conditionals"
      (is (= [(:comment/id comment-3)]
             (-> (q/where [:and
                           [:<> :comment/content (:comment/content comment-4)]
                           [:<> :comment/content "random"]])
                 (q/all! :comment)
                 (->> (mapv :comment/id))))))

    (testing "all! with custom where clause and nested conditionals equality"
      (is (= []
             (-> (q/where [:and
                           [:= :comment/content (:comment/content comment-4)]
                           [:= :comment/content  "random"]])
                 (q/all! :comment)
                 (->> (mapv :comment/id))))))

    (testing "all! with custom where clause gt"
      (is (= [(:comment/id comment-4)]
             (-> (q/where [:> :comment/rating 50])
                 (q/all! :comment)
                 (->> (mapv :comment/id))))))

    (testing "all! with custom where clause lt"
      (is (= [(:comment/id comment-3)]
             (-> (q/where [:< :comment/rating 50])
                 (q/all! :comment)
                 (->> (mapv :comment/id))))))

    (testing "all! with custom where clause lte"
      (is (= #{(:comment/id comment-3)
               (:comment/id comment-4)}
             (-> (q/where [:<= :comment/rating 80])
                 (q/all! :comment)
                 (->> (mapv :comment/id))
                 (set)))))

    (testing "all! with custom where clause gte"
      (is (= #{(:comment/id comment-3)
               (:comment/id comment-4)}
             (-> (q/where [:>= :comment/rating 45])
                 (q/all! :comment)
                 (->> (mapv :comment/id))
                 (set)))))))

(deftest test-only-honeysql-map
  (let [account-1 (-> account-1 changeset/create q/save!)
        post-1 (-> post-1 (assoc :post/account-id (:account/id account-1)) changeset/create q/save!)
        post-2 (-> post-2 (assoc :post/account-id (:account/id account-1)) changeset/create q/save!)]

    (testing "using find! with only a HoneySQL map"
      (is (= (:post/id post-1)
             (-> (q/select :*)
                 (q/from :post)
                 (q/where [:= :post/id (:post/id post-1)])
                 (q/find!)
                 (:post/id)))))
    (testing "using all! with only a HoneySQL map"
      (is (= #{(:post/id post-1)
               (:post/id post-2)}
             (-> (q/select :*)
                 (q/from :post)
                 (q/all!)
                 (->> (mapv :post/id))
                 (set)))))))

(deftest test-load!
  (let [account-1 (-> account-1 changeset/create q/save!)
        post-1 (-> post-1 (assoc :post/account-id (:account/id account-1)) changeset/create q/save!)
        post-2 (-> post-2 (assoc :post/account-id (:account/id account-1)) changeset/create q/save!)
        comment-1 (-> comment-1 (assoc :comment/account-id (:account/id account-1)
                                       :comment/post-id (:post/id post-1))
                      changeset/create
                      q/save!)]
    (testing "load! should deref a account's posts"
      (is (= #{(:post/id post-1)
               (:post/id post-2)}
             (-> (q/find! :account (:account/id account-1))
                 (q/load! :account/posts)
                 :account/posts
                 (->> (mapv :post/id))
                 (set)))))

    (testing "load! should deref a account's posts and comments"
      (is (= #{(:post/id post-1)
               (:post/id post-2)
               (:comment/id comment-1)}
             (-> (q/find! :account (:account/id account-1))
                 (q/load! :account/posts :account/comments)
                 ((juxt :account/posts :account/comments))
                 (flatten)
                 (->> (mapv #(or (:post/id %)
                                 (:comment/id %))))
                 (set)))))))

(deftest test-custom-table
  (let [{:product/keys [id]} (-> {:product/title "Orange"} changeset/create q/save!)]
    (testing "querying product"
      (is (some? (q/find! :product id)))
      (is (seq (-> (q/select :*)
                   (q/from :product)
                   (q/all!))))
      (is (seq (-> (q/select :product/*)
                   (q/from :product)
                   (q/all!))))
      (is (some? (q/find-by! :product/id id)))
      (is (seq (q/all! :product/id id)))
      (is (seq (q/all! :product))))))

(deftest test-with-registry
  (let [{account-id :account/id} (-> account-1 changeset/create q/save!)
        {:snippet/keys [id] :as s}
        (-> {:snippet/content "code"
             :snippet/account-id account-id}
            changeset/create
            q/save!)]
    (testing "querying snippet with registry"
      (is (some? (q/find! :snippet id)))
      (is (seq (-> (q/select :*)
                   (q/from :snippet)
                   (q/all!))))
      (is (seq (-> (q/select :snippet/*)
                   (q/from :snippet)
                   (q/all!))))
      (is (some? (q/find-by! :snippet/id id)))
      (is (seq (q/all! :snippet/id id)))
      (is (seq (q/all! :snippet))))))


(comment

  (def account-1 (-> account-1 changeset/create q/save!))
  (def post-1 (-> post-1 (assoc :post/account-id (:account/id account-1)) changeset/create q/save!))
  (def post-2 (-> post-2 (assoc :post/account-id (:account/id account-1)) changeset/create q/save!))
  (def comment-3 (-> comment-3 (assoc :comment/account-id (:account/id account-1)
                                      :comment/post-id (:post/id post-1))
                     changeset/create
                     q/save!))
  (def comment-4 (-> comment-4
                     (assoc :comment/account-id (:account/id account-1)
                            :comment/post-id (:post/id post-2))
                     changeset/create
                     q/save!))
  )
