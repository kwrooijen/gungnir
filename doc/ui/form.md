# Form

Gungnir Models and Changesets can be used for form validation. A real world
example would be to have a user registration form. We can share our models with
Clojure and Clojurescript.

## Account Model + password match validation

A basic model we can use for both frontend validation and before save
validation.

```clojure
(gungnir.model/register!
 {:account
  [:map
   [:account/id {:primary-key true} uuid?]
   [:account/email
    [:re {:error/message "Invalid email"} #".+@.+\..+"]]
   [:account/password {:before-save [:bcrypt]} [:string {:min 6}]]
   [:account/password-confirmation {:virtual true} [:string {:min 6}]]]})

(defn password-match? [m]
  (= (:account/password m)
     (:account/password-confirmation m)))

(defmethod gungnir.model/validator :register/password-match? [_]
  {:validator/key :account/password-confirmation
   :validator/fn password-match?
   :validator/message "Passwords don't match"})
```

## Clojurescript form

In this example we create a form using Hiccup syntax. This is a very simplified
example with no styling. The following points are important in this example:

* Add the `:data-gungnir-form` property to the form with the key `:register`.

* Next we add labels to each input field with the `.gungnir-error` class.

```clojure
;; Simplified form with no styling
[:form
 {:data-gungnir-form :register}

 [:div
  [:label.gungnir-error {:for :account/email}]
  [:input {:name :account/email}]]

 [:div
  [:label.gungnir-error {:for :account/password}]
  [:input {:name :account/password}]]

 [:div
  [:label.gungnir-error {:for :account/password-confirmation}]
  [:input {:name :account/password-confirmation}]]]
```

## Clojurescript validation handler

Next we can handle the validation through the
`gungnir.ui.form/handle-validation` multimethod. This handler matches on the
form key we defined (`:register`). And expect a map to be returned. If that map
contains a `:changeset/errors` key which is not `nil`, it will inject the error
messages in the proper labels.

```clojure
(defmethod gungnir.ui.form/handle-validation :register [_ params]
  (-> params
      (gungnir.changeset/cast :account)
      (gungnir.changeset/create [:register/password-match?])))
```

After the page has loaded, you will need to initialize Gungnir's form validation.


```clojure
(gungnir.ui.form/init)
```

The final result, with some styling.

![form-validation](https://raw.githubusercontent.com/kwrooijen/gungnir.ui/master/assets/form-validation.gif)

--- 

<div class="footer-navigation">
<span>Previous: <a href="https://kwrooijen.github.io/gungnir/ui.html">ui</a></span>
</div>
