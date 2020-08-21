(ns gungnir.factory
  (:require
   [gungnir.database]
   [gungnir.query]))

(defn make-datasource-map! [?options]
  (let [datasource (gungnir.database/build-datasource! ?options)]
    {:close-fn     identity ;; TODO
     :datasource   datasource
     :find!-fn     gungnir.query/find!
     :find-by!-fn  gungnir.query/find-by!
     :all!-fn      gungnir.query/all!
     :delete!-fn   gungnir.query/delete!
     :save!-fn     gungnir.query/save!}))
