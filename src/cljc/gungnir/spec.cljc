(ns gungnir.spec
  (:require
   [clojure.spec.alpha :as s]
   [malli.core :as m]))

(s/def :gungnir.model/field
  (s/or :schema
        m/schema?

        :field-key
        qualified-keyword?

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
  (s/or :model-key simple-keyword?
        :model :gungnir/model))

;; Changeset

(s/def :changeset/model simple-keyword?)

(s/def :changeset/validators (s/coll-of qualified-keyword?))

(s/def :changeset/diff map?)

(s/def :changeset/origin map?)

(s/def :changeset/transformed-origin map?)

(s/def :changeset/params map?)

(s/def :changeset/result map?)

(s/def :changeset/errors (s/nilable map?))

(s/def :gungnir/changeset
  (s/keys
   :req [:changeset/model
         :changeset/validators
         :changeset/diff
         :changeset/origin
         :changeset/transformed-origin
         :changeset/params
         :changeset/result
         :changeset/errors]))

;; Transaction

(s/def :transaction.error/data any?)
(s/def :transaction.error/key keyword?)

(s/def :transaction/pipeline vector?)
(s/def :transaction/results map?)
(s/def :transaction/state map?)
(s/def :transaction/error
  (s/nilable
   (s/keys
    :req [:transaction.error/data
          :transaction.error/key])))

(s/def :gungnir/transaction
  (s/keys
   :req [:transaction/pipeline
         :transaction/results
         :transaction/state
         :transaction/error]))
;; Migration

(s/def :gungnir.migration.action/name qualified-keyword?)
(s/def :gungnir.migration.action/opts map?)
(s/def :gungnir.migration.action/arg any?)

(s/def :gungnir.migration/up
  (s/or :raw string?
        :action-2 (s/tuple :gungnir.migration.action/name
                           (s/or :opts :gungnir.migration.action/opts
                                 :args (s/* :gungnir.migration.action/arg)))
        :action-3 (s/tuple :gungnir.migration.action/name
                           :gungnir.migration.action/opts
                           (s/* :gungnir.migration.action/arg))))

(s/def :gungnir/migration
  (s/keys :req-un [:gungnir.migration/up
                   :gungnir.migration/down]))
