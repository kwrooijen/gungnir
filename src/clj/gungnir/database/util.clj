(ns gungnir.database.util
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as string]))

(def ^:private default-postgres-port 5432)

(defn- create-uri [url] (java.net.URI. url))

(defn- parse-username-and-password [database-uri]
  (string/split (.getUserInfo database-uri) #":"))

(defn- jdbc-url? [s]
  (some? (re-matches #"^jdbc.*" s)))

(defn- postgres-url? [s]
  (some? (re-matches #"^postgres.*" s)))

(defn- uri->port [uri]
  (let [port (.getPort uri)]
    (if (= -1 port)
      default-postgres-port
      port)))

(defn- subname [database-uri]
  (format "//%s:%s%s" (.getHost database-uri) (uri->port database-uri) (.getPath database-uri)))

(defn- postgres->jdbc [database-url]
  (let [database-uri (create-uri database-url)
        [username password] (parse-username-and-password database-uri)]
    (format "jdbc:postgresql:%s?user=%s&password=%s"
            (subname database-uri)
            username
            password)))

(s/fdef ->jdbc-database-url
  :args (s/cat :?database-url string?)
  :ret string?)
(defn ->jdbc-database-url
  "Convert `?database-url` to a valid Postgres JDBC_DATABASE_URL. If
  `?databse-url` is already a JDBC_DATA_URL return that."
  [?database-url]
  (cond
    (jdbc-url? ?database-url)
    ?database-url
    (postgres-url? ?database-url)
    (postgres->jdbc ?database-url)))
