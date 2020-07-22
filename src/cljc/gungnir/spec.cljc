(ns gungnir.spec
  (:require
   [clojure.spec.alpha :as s]
   [malli.core :as m]))

(s/def :gungnir.model/field
  (s/or :gungnir.model.field/without-props
        (s/tuple qualified-keyword?
                 (comp not map?))
        :gungnir.model.field/with-props
        (s/tuple qualified-keyword?
                 map?
                 any?)))

(s/def :gungnir/model
  (s/or :schema
        (s/and m/schema?
               (comp #{:map} m/type))
        :raw
        (s/or
         :gungnir.model/without-props
         (s/cat :map #{:map}
                :rest (s/+ :gungnir.model/field))
         :gungnir.model/with-props
         (s/cat :map #{:map}
                :props map?
                :rest (s/+ :gungnir.model/field)))))
