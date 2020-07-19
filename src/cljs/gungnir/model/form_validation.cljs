(ns gungnir.model.form-validation
  (:require
   [malli.core :as m]))

(defmulti handle-validation (fn [k] k))

(defn get-attribute [el key]
  (when-let [key-value (-> el (.-attributes) (aget key))]
    (.-value key-value)))

(defn model-children [model]
  (->> (m/children model)
       (mapv (comp name first))
       (set)))

(defn model-children-full [model]
  (->> (m/children model)
       (mapv first)
       set))

(defn form-submit-button [form]
  (->> (.-elements form)
       (filter #(= (.-type %) "submit"))
       (first)))

(defn field-by-key [form key]
  (->> (.-elements form)
       (filter #(= (.-name %) (name key)))
       (first)))

(defn get-error-label [k]
  (js/document.querySelector (str "label[for=" (name k) "].error")))

(defn display-error-on-field? [form k]
  (and (field-by-key form k)
       (.-touched (field-by-key form k))))

(defn add-focus-event-handler! [^js input]
  (set! (.-touched input) false)
  (.addEventListener input "focus" #(set! (.. ^js % -target -touched) true)))

(defn handle-button-disable-status! [form changeset]
  (if (:changeset/errors changeset)
    (.setAttribute (form-submit-button form) "disabled" true)
    (.removeAttribute (form-submit-button form) "disabled")))

(defn input->model-field [input]
  (when-let [input-name (get-attribute input "name")]
    [(keyword input-name) (.-value input)]))

(defn form->map [form]
  (->> (.-elements form)
       (keep input->model-field)
       (into {})))

(defn validate-fields [form validation-k]
  (let [changeset (handle-validation validation-k (form->map form))]
    (handle-button-disable-status! form changeset)
    (doseq [k (model-children-full (:changeset/model changeset))]
      (when-let [el (and (display-error-on-field? form k)
                         (get-error-label k))]
        (set! (.-innerHTML el) (first (get (:changeset/errors changeset) k [""])))))))

(defn add-form-validation-event-handler! [form]
  (let [validation-k (keyword (get-attribute form "formvalidation"))]
    (.addEventListener form "input" #(validate-fields form validation-k))))

(defn init []
  (doseq [form (js/document.querySelectorAll "[formvalidation]")]
    (add-form-validation-event-handler! form)
    (doseq [^js child (.-elements form)]
      (add-focus-event-handler! child))))
