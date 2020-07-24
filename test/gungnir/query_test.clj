(ns gungnir.query-test
  (:require
   [clojure.string :as string]
   [clojure.test :refer :all]
   [gungnir.changeset :refer [changeset]]
   [gungnir.query :as q]
   [gungnir.test.util :as util]))

(use-fixtures :once util/once-fixture)
(use-fixtures :each util/each-fixture)

(def user-1-email "user@test.com")

(def user-1-password "123456")

(def user-1
  {:user/email user-1-email
   :user/password user-1-password})

(def user-2-email "user-2@test.com")

(def user-2-password "123456")

(def user-2
  {:user/email user-2-email
   :user/password user-2-password})

(def user-3-email "user-3@test.com")

(def user-3-password "133456")

(def user-3-username "foobar")

(def user-3
  {:user/email user-3-email
   :user/username user-3-username
   :user/password user-3-password})

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

(deftest test-find!
  (let [{:user/keys [id]} (-> user-1 changeset q/save!)]
    (testing "Find user by primary key"
      (is (= user-1-email (-> (q/find! :user id) :user/email))))

    (testing "Find user by primary key, automatic uuid translation"
      (is (= user-1-email (-> (q/find! :user (str id)) :user/email)))))

  (testing "Find unknown user by primary key returns nil"
    (is (nil? (-> (q/find! :user #uuid "1e626bf3-8fdf-4a66-b708-7aa35dafede9"))))
    (is (nil? (-> (q/find! :user "1e626bf3-8fdf-4a66-b708-7aa35dafede9"))))))

(deftest test-find-by!
  (let [{:user/keys [id] :as user} (-> user-1 changeset q/save!)
        token (-> token-1 (assoc :token/user-id (:user/id user)) changeset q/save!)
        _post (-> post-1 (assoc :post/user-id id) changeset q/save!)]
    (testing "Find user by email"
      (is (= user-1-email (-> (q/find-by! :user/email user-1-email) :user/email))))

    (testing "Find user by unknown email returns nil"
      (is (nil? (q/find-by! :user/email "random@email.com"))))

    (testing "Find post by title and user-id"
      (is (= post-1-title
             (-> (q/find-by! :post/title post-1-title
                             :post/user-id id)
                 :post/title))))
    (testing "Find post by user-id with auto uuid"
      (is (= post-1-title
             (-> (q/find-by! :post/user-id (str id))
                 :post/title))))
    (testing "keyword arguments"
      (is (= (:token/id token)
             (-> (q/find-by! :token/type :token/verify)
                 :token/id))))))

(deftest test-all!
  (let [{:user/keys [id] :as _user} (-> user-1 changeset q/save!)
        _post-1 (-> post-1 (assoc :post/user-id id) changeset q/save!)
        _post-2 (-> post-2 (assoc :post/user-id id) changeset q/save!)]
    (testing "Find posts by user id"
      (is (= #{post-1-title post-2-title}
             (-> (q/all! :post/user-id id) (->> (mapv :post/title)) set))))

    (testing "Find posts by user id with limit 1"
      (is (= 1
             (-> (q/limit 1)
                 (q/all! :post/user-id id)
                 (count)))))

    (testing "Find posts by user id and title"
      (is (= post-1-title
             (-> (q/limit 1)
                 (q/all! :post/user-id id
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
  (testing "inserting a new user"
    (let [user (-> user-1 changeset q/save!)]
      (is (nil? (:changeset/errors user)))
      (is (uuid? (:user/id user)))
      (is (some? (q/find! :user (:user/id user))))))

  (testing "inserting an invalid user"
    (let [user (-> user-1 (assoc :user/password "123") changeset q/save!)]
      (is (some? (:changeset/errors user)))
      (is (nil? (:user/id user)))
      (is (nil? (q/find! :user (:user/id user)))))))

(deftest test-update-save!
  (let [user (-> user-1 changeset q/save!)
        user-2 (-> user-2 changeset q/save! (update :user/id str))]
    (testing "updating an existing user"
      (let [new-email "user-updated@test.com"
            new-user (q/save! (changeset user {:user/email new-email}))]
        (is (nil? (:changeset/errors new-user)))
        (is (uuid? (:user/id new-user)))
        (is (some? (q/find! :user (:user/id user))))))

    (testing "updating an existing user with str uuid"
      (let [new-email "user-updated-2@test.com"
            new-user (q/save! (changeset user-2 {:user/email new-email}))]
        (is (nil? (:changeset/errors new-user)))
        (is (uuid? (:user/id new-user)))
        (is (some? (q/find! :user (:user/id user))))))

    (testing "updating an invalid user"
      (let [new-user (q/save! (changeset user {:user/password "123"}))]
        (is (some? (:changeset/errors new-user)))
        (is (nil? (:user/id new-user)))
        (is (= user-1-password (:user/password (q/find! :user (:user/id user)))))))

    (testing "don't update if no diff"
      (let [new-user (q/save! (changeset user {:user/email user-1-email}))]
        (is (= user new-user))))))

(deftest test-delete!
  (testing "deleting existing user"
    (let [user (-> user-1 changeset q/save!)]
      (is (= true (q/delete! user)))
      (is (nil? (q/find! :user (:user/id user))))))

  (testing "deleting existing user with str uuid"
    (let [user (-> user-1 changeset q/save! (update :user/id str))]
      (is (= true (q/delete! user)))
      (is (nil? (q/find! :user (:user/id user))))))

  (testing "deleting non existing user"
    (let [uuid "1e626bf3-8fdf-4a66-b708-7aa35dafede9"]
      (is (= false (q/delete! {:user/id uuid})))
      (is (nil? (q/find! :user uuid)))))

  (testing "deleting reference atom"
    (let [user (-> user-1 changeset q/save!)
          token (-> token-1 (assoc :token/user-id (:user/id user)) changeset q/save!)]
      (is (-> (q/find! :user (:user/id user)) :user/token (q/delete!)))
      (is (nil? (q/find! :token (:token/id token)))))))

(deftest test-relation-has-one
  (let [user (-> user-1 changeset q/save!)
        token (-> token-1 (assoc :token/user-id (:user/id user)) changeset q/save!)]
    (testing "user has one token"
      (is (= (:token/id token)
             (-> user
                 :user/token
                 (deref)
                 :token/id))))

    (testing "find! user has one token"
      (is (= (:token/id token)
             (-> (q/find! :user (:user/id user))
                 :user/token
                 (deref)
                 :token/id))))

    (testing "user has one token, back to user"
      (is (= (:user/id user)
             (-> user
                 :user/token
                 (deref)
                 :token/user
                 (deref)
                 :user/id))))

    (testing "find! user has one token, back to user"
      (is (= (:user/id user)
             (-> (q/find! :user (:user/id user))
                 :user/token
                 (deref)
                 :token/user
                 (deref)
                 :user/id))))))

(deftest test-relation-has-many
  (let [user (-> user-1 changeset q/save!)
        post-1 (-> post-1 (assoc :post/user-id (:user/id user)) changeset q/save!)
        post-2 (-> post-2 (assoc :post/user-id (:user/id user)) changeset q/save!)
        comment-1 (-> comment-1 (assoc :comment/user-id (:user/id user)
                                       :comment/post-id (:post/id post-1))
                      changeset
                      q/save!)
        comment-2 (-> comment-2 (assoc :comment/user-id (:user/id user)
                                       :comment/post-id (:post/id post-2))
                      changeset
                      q/save!)]
    (testing "user has many posts"
      (is (= #{(:post/id post-1) (:post/id post-2)}
             (-> user
                 :user/posts
                 (deref)
                 (->> (map :post/id))
                 (set)))))

    (testing "find! user has many posts"
      (is (= #{(:post/id post-1) (:post/id post-2)}
             (-> (q/find! :user (:user/id user))
                 :user/posts
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
  (let [user (-> user-1 changeset q/save!)
        post (-> post-1 (assoc :post/user-id (:user/id user)) changeset q/save!)
        comment (-> comment-1
                    (assoc :comment/user-id (:user/id user)
                           :comment/post-id (:post/id post))
                    changeset
                    q/save!)]

    (testing "comment belongs to post"
      (is (= (:post/id post)
             (-> comment
                 :comment/post
                 (deref)
                 :post/id))))

    (testing "comment belongs to post, belongs to user"
      (is (= (:user/id user)
             (-> comment
                 :comment/post
                 (deref)
                 :post/user
                 (deref)
                 :user/id))))

    (testing "comment belongs to post, belongs to user, back to comment"
      (is (= (:comment/id comment)
             (-> comment
                 :comment/post
                 (deref)
                 :post/user
                 (deref)
                 :user/posts
                 (deref)
                 (first)
                 :post/comments
                 (deref)
                 (first)
                 :comment/id))))))

(deftest test-before-save
  (let [_user (-> user-1 (update :user/email string/upper-case) changeset q/save!)]
    (testing "saving email as lowercase"
      (let [result (q/find-by! :user/email user-1-email)]
        (is (= (string/lower-case (:user/email user-1)) (:user/email result)))))))

(deftest test-before-read
  (let [_user (-> user-1 changeset q/save!)]
    (testing "finding user by case-insensitive email"
      (let [result-1 (q/find-by! :user/email user-1-email)
            result-2 (q/find-by! :user/email (string/upper-case user-1-email))
            result-3 (q/find-by! :user/email (string/lower-case user-1-email))]
        (is (some? result-1))
        (is (some? result-2))
        (is (some? result-3))))))

(deftest test-after-read
  (let [user (-> user-1 changeset q/save!)
        token (-> token-1 (assoc :token/user-id (:user/id user)) changeset q/save!)]
    (testing "reading keywords"
      (let [{:token/keys [type]} (q/find-by! :token/id (:token/id token))]
        (is (= (:token/type token) (:token/type token-1) type))))))

(deftest test-duplicate-key
  (let [_ (-> user-1 changeset q/save!)
        user-1 (-> user-1 changeset q/save!)
        _ (-> user-3 changeset q/save!)
        user-2 (-> user-3 (assoc :user/email "some@random.email") changeset q/save!)]
    (testing "uniqueness of email"
      (is (= [:duplicate-key] (-> user-1 :changeset/errors :user/email))))

    (testing "custom error message for duplicate-key"
      (is (= ["username taken"] (-> user-2 :changeset/errors :user/username))))))

(deftest test-custom-where-clause
  (let [user-1 (-> user-1 changeset q/save!)
        post-1 (-> post-1 (assoc :post/user-id (:user/id user-1)) changeset q/save!)
        post-2 (-> post-2 (assoc :post/user-id (:user/id user-1)) changeset q/save!)
        comment-3 (-> comment-3 (assoc :comment/user-id (:user/id user-1)
                                       :comment/post-id (:post/id post-1))
                      changeset
                      q/save!)
        comment-4 (-> comment-4 (assoc :comment/user-id (:user/id user-1)
                                       :comment/post-id (:post/id post-2))
                      changeset
                      q/save!)]

    (testing "all! with custom where clause"
      (is (= [(:comment/id comment-3)]
             (-> (q/where [:<> :comment/id (:comment/id comment-4)])
                 (q/all! :comment)
                 (->> (mapv :comment/id))))))

    (testing "all! with custom where clause and nested conditionals"
      (is (= [(:comment/id comment-3)]
             (-> (q/where [:<> :comment/content (:comment/content comment-4) "random"])
                 (q/all! :comment)
                 (->> (mapv :comment/id))))))

    (testing "all! with custom where clause and nested conditionals equality"
      (is (= []
             (-> (q/where [:= :comment/content (:comment/content comment-4) "random"])
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
  (let [user-1 (-> user-1 changeset q/save!)
        post-1 (-> post-1 (assoc :post/user-id (:user/id user-1)) changeset q/save!)
        post-2 (-> post-2 (assoc :post/user-id (:user/id user-1)) changeset q/save!)]

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
