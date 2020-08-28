# Changeset

Changesets are responsible for writing data. They manage all data going
**towards** the database. Changesets provide the following features.

* Cast and filter data (internal / external) to valid model maps.
* Use namespaced keywords to determine which model to use.
* Validate your data, and aggregate / format any errors.
* View the changes that will be saved to the database.

[Models](https://kwrooijen.github.io/gungnir/model.html) are the basis of your
changesets. They determine what is and isn't allowed to be stored in the
database.

## Structure of changesets

Changesets are namespaced maps with the `:changeset` namespace. They hold the
following information.

* `:changeset/model` - The model that is being changed, e.g. `:user`

* `:changeset/validators` - A vector of extra
  [validators](https://kwrooijen.github.io/gungnir/model.html#model-validators)
  which should be applied to the resulting map.

* `:changeset/diff` - The differences that will be applied. If a new record is
  being created, then all fields will be here. If a record is being updated,
  only the differences will be stored here.

* `:changeset/origin` - In case that a record is being **updated**, the original
  record will be stored here.

* `:changeset/transformed-origin` - In case that a record is being **updated**,
  the original record will be stored here after it has undergone any
  transformations.

* `:changeset/params` - The params that will be applied to the record, either
  for inserting or updating.

* `:changeset/result` - The resulting record. If validated and approved, this
  record will be stored in the database when executing the `gungnir.query/save!`
  function.

* `:changeset/errors` - Any errors returned from the validation check. Errors
  are represented by a map, where the key is the field (qualified-keyword) and
  the value is a vector of error messages.
  
## Changeset arguments 

Changesets accept 3 different types of arguments.

* `origin` - a map of the original record to be updated. If an `origin` map is
  provided, and the `primary-key` is not nil, it will update the row on
  `save!`. If no `origin` map is supplied, the changeset will create a new row on
  `save!`.
  
* `params` - a map which will update the record with the fields
  supplied. `params` must be cast to a proper model record
  (qualified-keywords). 
  
* `validators` - A vector of
  [validators](https://kwrooijen.github.io/gungnir/model.html#model-validators)
  which will perform extra checks on the `:changeset/result` record.
  
The order of arguments:

```clojure
gungnir.changeset/create

([params])
([origin, params])
([params, validators])
([origin, params, validators])
```

## Creating a changeset

Creating a changeset is easy. Simply run the `gungnir.changeset/changeset`
function, assuming we have a user
[model](https://kwrooijen.github.io/gungnir/model.html).

```clojure
;; New user

(gungnir.changeset/create {:user/email "user@test.com", :user/password "123456"})
;; => {:changeset/model :user
;; =>  :changeset/params {:user/email "user@test.com", :user/password "123456"}
;; =>  :changeset/errors nil
;; =>  ,,,}

;; Updating email of existing user

(gungnir.changeset/create existing-user {:user/email "user@test.com"})
;; => {:changeset/diff {:user/email "user@test.com"}
;; =>  ,,,}

;; Updating password of an existing user with extra validators

(gungnir.changeset/create existing-user 
                          {:user/password "123456",
                           :user/password-confirmation "12345"}
                          [:user/password-match?])
;; => {:changeset/errors {:user/password-confirmation ["Passwords don't match"]}
;; =>  ,,,}
```

## Casting data

In the real world you'll be working with external data, this data will most
likely not be represented as namespaced maps. In order to convert data to the
proper model maps you can use the `gungnir.changeset/cast` function.

```clojure
(-> {"email" "user@test.com"
     "password" "123456"
     "password_confirmation" "12345"
     "random_field" "this isn't a field defined in the model"}
    (gungnir.changeset/cast :user))
;; => {:user/email "user@test.com"
;; =>  :user/password "123456"
;; =>  :user/password-confirmation "12345"}
```

Using this function you'll be able to filter out any unnecessary fields based on
the model definition. Once the map has been cast, Gungnir will know which model
to use when you create a changeset because of the namespaced keywords.

## Saving a changeset

Once all validation checks pass, you'll be able to save a changeset to the
database using the `gungnir.query/save!` function. This function will either
insert or update the record depending if a `primary-key` is available. Gungnir
knows which field is the primary-key because it is defined in the model. If you
attempt to save the changeset when the `:changeset/errors` key is not `nil`, the
`gungnir.query/save!` function will have **no** side effects and return the
changeset as is.

If there are no errors, Gungnir will attempt to insert / update the record in
question. Applying this change however can also result in errors (e.g. `UNIQUE
CONSTRAINT` error). When this happens the changeset will be returned with new
errors supplied to the `:changeset/errors` key.

Here's a real-world example on how this might look like using a Ring handler.

```clojure
(defn attempt-register-user [request]
  (-> (:form-params request)
      (gungnir.changeset/cast :user)
      (gungnir.changeset/create [:user/password-match?])
      (gungnir.query/save!)))

(defn handler-user-registration [request]
  (if-let [errors (:errors (attempt-register-user request))]
    (-> (ring/redirect "/register")
        (assoc-in [:flash :errors] errors))
    (-> (ring/redirect "/")
        (assoc-in [:flash :success] "Successfully logged in."))))
```

## Helpers

Gungnir's also provide a few helper functions for manipulating / creating
changesets. These helpers can either be used on changesets or model
records. Every time one of these functions are applied, all validators are
re-evaluated.

* gungnir.changeset/assoc
* gungnir.changeset/update
* gungnir.changeset/merge

---

<div class="footer-navigation">
<span>Previous: <a href="https://kwrooijen.github.io/gungnir/model.html">model</a></span>
<span>Next: <a href="https://kwrooijen.github.io/gungnir/query.html">query</a></span>
</div>

