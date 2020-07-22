(ns gungnir.decode
  (:require
   #?(:clj [clojure.instant])
   [malli.transform :as mt]
   [malli.core :as m]))

(def +uuid-decoders+
  {'uuid? (fn [x]
            (cond-> x
              (string? x) #?(:clj java.util.UUID/fromString
                             :cljs uuid)))})
(def +date-decoders+
  {'inst? (fn [x]
            (cond-> x
              (string? x) #?(:clj clojure.instant/read-instant-date
                             :cljs identity)))})

(defn uuid-transformer []
  (mt/transformer
   {:name :uuid-decoder
    :decoders +uuid-decoders+}))

(defn date-transformer []
  (mt/transformer
   {:name :date-decoder
    :decoders +date-decoders+}))

(defn advanced-decode-with-defaults [model params]
  (m/decode model params
            (mt/transformer
             mt/default-value-transformer
             mt/string-transformer
             uuid-transformer
             date-transformer)))

(defn advanced-decode [model params]
  (m/decode model params
            (mt/transformer
             mt/string-transformer
             uuid-transformer
             date-transformer)))
