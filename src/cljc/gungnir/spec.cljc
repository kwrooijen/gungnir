(ns gungnir.spec
  (:require
   [clojure.spec.alpha :as s]
   [malli.core :as m]))

(s/def :gungnir.model/field
  (s/or :schema
        m/schema?

        :gungnir.model.field/without-props
        (s/cat :key qualified-keyword?
               :spec any?)

        :gungnir.model.field/with-props
        (s/cat :key qualified-keyword?
               :props (s/nilable map?)
               :spec any?)))

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
