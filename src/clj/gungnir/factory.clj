(ns gungnir.factory
  (:require
   [gungnir.database]
   [gungnir.query]))

(defn make-datasource-map!
  "Creates a new datasource and returned a map of functions to query this
  datasource. Returned keys:
  * :close!-fn
  * :datasource
  * :find!-fn
  * :find-by!-fn
  * :all!-fn
  * :delete!-fn
  * :save!-fn

  The following options are supported for `?options`
  * DATABASE_URL - The universal database url used by services such as Heroku / Render
  * JDBC_DATABASE_URL - The standard Java Database Connectivity URL
  * HikariCP configuration map - https://github.com/tomekw/hikari-cp#configuration-options
  When both `url` and `options` are supplied:
  `url` - DATABSE_URL or JDBC_DATABASE_URL
  `options` - HikariCP options
  "
  [?options]
  (let [datasource (gungnir.database/build-datasource! ?options)]
    {:close!-fn    identity ;; TODO
     :datasource   datasource
     :find!-fn     (fn [& args] (-> (apply gungnir.query/find args)
                                    (gungnir.database/query-1! datasource)))
     :find-by!-fn  (fn [& args] (-> (apply gungnir.query/find-by args)
                                    (gungnir.database/query-1! datasource)))
     :all!-fn      (fn [& args] (-> (apply gungnir.query/all args)
                                    (gungnir.database/query! datasource)))
     :delete!-fn   (fn [record]    (gungnir.query/delete! record datasource))
     :save!-fn     (fn [changeset] (gungnir.query/save! changeset datasource))}))
