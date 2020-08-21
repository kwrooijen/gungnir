(ns gungnir.factory
  (:require
   [gungnir.database]
   [gungnir.query]))

(defn make-datasource-map! [?options]
  (let [datasource (gungnir.database/build-datasource! ?options)]
    {:close-fn     identity ;; TODO
     :datasource   datasource
     :find!-fn     (fn [& args] (-> (apply gungnir.query/find args)
                                    (gungnir.database/query-1! datasource)))
     :find-by!-fn  (fn [& args] (-> (apply gungnir.query/find-by args)
                                    (gungnir.database/query-1! datasource)))
     :all!-fn      (fn [& args] (-> (apply gungnir.query/all args)
                                    (gungnir.database/query! datasource)))
     :delete!-fn   (fn [record]    (gungnir.query/delete! record datasource))
     :save!-fn     (fn [changeset] (gungnir.query/save! changeset datasource))}))
