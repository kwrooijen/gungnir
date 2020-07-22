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

(s/def :gungnir.model/field-or-key
  (s/or :field-key qualified-keyword?
        :field :gungnir.model/field))

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

(s/def :gungnir/model-or-key
  (s/or :model-key keyword?
        :model :gungnir/model))

;; Changeset

;; TODO change to keyword
(s/def :changeset/model :gungnir/model)

(s/def :changeset/validators (s/coll-of keyword?))

(s/def :changeset/diff map?)

(s/def :changeset/origin map?)

(s/def :changeset/sane-origin map?)

(s/def :changeset/params map?)

(s/def :changeset/result map?)

(s/def :changeset/errors (s/nilable map?))

(s/def :gungnir/changeset
  (s/keys
   :req [:changeset/model
         :changeset/validators
         :changeset/diff
         :changeset/origin
         :changeset/sane-origin
         :changeset/params
         :changeset/result
         :changeset/errors]))
