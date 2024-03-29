# Model

## Malli Schemas

Models in Gungnir are defined using [malli](https://github.com/metosin/malli/).
These models define your Clojure data structures, and how they interact with
your database.

* Only data you describe as valid will be saved to the database.
* Perform transformations to your data when reading / writing to the database.
* Create descriptive error messages for your end users.

```clojure
;; Define a account model

(def account-model
 [:map
  {:has-many {:account/posts {:model :post :foreign-key :post/account-id}
              :account/comments {:model :comment :foreign-key :comment/account-id}}}
  [:account/id {:primary-key true} uuid?]
  [:account/email {:before-save [:string/lower-case]
                :before-read [:string/lower-case]}
   [:re {:error/message "Invalid email"} #".+@.+\..+"]]
  [:account/password {:before-save [:bcrypt]} [:string {:min 6}]]
  [:account/password-confirmation {:virtual true} [:string {:min 6}]]
  [:account/created-at {:auto true} inst?]
  [:account/updated-at {:auto true} inst?]])
```

## Registering Models

Models can be registered using the `gungnir.model/register!` function. Generally
you'd want your system state manager to manage this. E.g. Integrant, Component,
Mount.

```clojure
(gungnir.model/register!
 {:account account-model
  :post post-model
  :comment comment-model})
```

## Model properties

### `:table`

Specify the table you'd like to use for this model. By default the model name
will be used as the table. For example you might have a `:account` model, but you
want to target the "accounts" table.

```clojure
{:account
 [:map
  {:table :accounts}
  [:account/email string?]}
```

### `:has-many`

Describe a `:has-many` relation which can be queried through the current
model. This relational query will return a vector of maps.

```clojure
[:map
 {:has-many {:account/posts {:model :post :foreign-key :post/account-id}}}
 ,,,]
```

### `:has-one`

Describe a `:has-one` relation which can be queried through the current
model. This relational query will return a single map or `nil`.

```clojure
[:map
 {:has-one {:account/reset-token {:model :reset-token :foreign-key :reset-token/account-id}}}
 ,,,]
```

### `:belongs-to`

Describe a `:belongs-to` relation which can be queried through the current
model. This relational query will return a single map or `nil`.

```clojure
[:map
 {:belongs-to {:post/account {:model :account :foreign-key :post/account-id}}}
 ,,,]
```

## Model Field properties

Malli schemas support adding properties. Gungnir has a few custom properties
that can be used.

### `:primary-key` (required)

Describe which key is the `PRIMARY KEY` in your table. This is required for
Gungnir to be able to make use of the querying API, as well as the relational
mapping.

```clojure
[:account/id {:primary-key true} uuid?]
```

### `:auto`

Tell Gungnir that this key is automatically managed by the database, and Gungnir
should never make an attempt to modify it. This is useful for e.g. `TIMESTAMP`
columns which might be updated automatically.

```sql
created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
```

```clojure
[:account/created-at {:auto true} inst?]
```

### `:virtual`

Tell Gungnir that you want this key to be part of your Model, but it does not
exist as a column in the database. Gungnir will make no attempt to query, or
save this key. Useful for things such as password-confirmation fields.

```clojure
[:account/password-confirmation {:virtual true} string?]
```

### `:before-save`

Add hooks to a column to be executed before you save them to the database. This
is useful to keep data transformations consistent. For example you always want
to encrypt passwords before saving them to the database. Passwords could be set
during creating, updated on the profile page, changed through a password
reset. In all of these cases you must encrypt the user's inputted password
before inserting it to the database.

Add a `:bcrypt` key to the `:before-save` vector for `:account/password`.

```clojure
[:account/password {:before-save [:bcrypt]} string?]
```

And define the `:bcrypt` `:before-save` handler. Handlers take the key and
value of field in question. The resulting value of `gungnir.model/before-save`
will be saved in the database.

```clojure
(defmethod gungnir.model/before-save :bcrypt [_k v]
  (buddy.hashers/derive v))
```

### `:before-read`

Add hooks to a column to be executed before you read it from database. This is
useful if want to sanitize any query parameters before reading. For example you
could save all emails as lowercase (with the `gungnir.model/before-save`
hook). Then add a `:before-read` hook to lowercase the email field when you
query the database. That way you'll be able to deal with case sensitive data.

Add the `:before-save` and `:before-read` hooks.

```clojure
[:account/email 
 {:before-save [:string/lower-case]
  :before-read [:string/lower-case]}
 [:re #".+@.+\..+"]]
```

Define the hooks to be used

```clojure
(defmethod gungnir.model/before-save :string/lower-case [_k v]
  (clojure.string/lower-case v))

(defmethod gungnir.model/before-read :string/lower-case [_k v]
  (clojure.string/lower-case v))
```

Gungnir has the `:string/lower-case` hooks built-in, so you don't have to define
them yourself.

### `:after-read`

Add hooks to a column to be executed after you read it from database. You could
encrypt data before saving it, and decrypt it after reading it for extra
security. Another use case is saving keywords to the database as strings, and
parsing it as EDN after reading it.

In this case, account has an `:account/option` key, which is a qualified-keyword. You
can't store keywords in SQL, so they're converted to strings.

```clojure
[:account/option 
 {:after-read [:edn/read-string]}
 [:enum :option/one :option/two :option/three]]
```

Define the `:edn/read-string` hook to convert the keywords. `:edn/read-string`
is also built-in Gungnir, so you don't have to defined it yourself.

```clojure
(defmethod after-read :edn/read-string [_ v]
  (if (vector? v)
    (mapv edn/read-string v)
    (edn/read-string v)))
```

## Model Relation Definitions

Gungnir can handle relational mapping for you. This is done by adding relation
definitions to the models properties. For more information regarding querying
relations visit the [query](https://kwrooijen.github.io/gungnir/query.html)
page.

### Example

In the example below we define the following relations:

* account **has_many** comments, through `:account/comments`
* account **has_many** posts, through `:account/posts`
* post **belongs_to** account, through `:post/account`
* post **has_many** comments, through `:post/comments`
* comment **belongs_to** post, through `:comment/account`
* comment **belongs_to** account, through `:comment/post`

```clojure
(def model-account
 [:map
  {:has-many {:account/posts {:model :post :foreign-key :post/account-id}
              :account/comments {:model :comment :foreign-key :comment/account-id}}}
  [:account/id {:primary-key true} uuid?]
  ,,,])

(def model-post
 [:map
  {:belongs-to {:post/account {:model :account :foreign-key :post/account-id}}
   :has-many {:post/comments {:model :comment :foreign-key :comment/post-id}}}
  [:post/id {:primary-key true} uuid?]
  [:post/account-id uuid?]
  ,,,])

(def model-comment
 [:map
  {:belongs-to {:comment/account {:model :account :foreign-key :comment/account-id}
                :comment/post {:model :post :foreign-key :comment/post-id}}}
  [:comment/id {:primary-key true} uuid?]
  [:comment/account-id uuid?]
  [:comment/post-id uuid?]
  ,,,])
```

## Model Validators

In some situations you will want to have extra validations. Visit the
[changeset](https://kwrooijen.github.io/gungnir/changeset.html) page to learn
how to use validators.

Validators are defined using the `gungnir.model/validator`
multimethod. Which is matched with a `qualified-keyword`.

The `gungnir.model/validator` multimethod should return the following map.

* `:validator/key` - The key this validator is related to. If the validator
  check fails it will mark this key as the failing key.
* `:validator/fn` - The function to be run to check the validation. This
  function takes a single argument, which is the map that is being validated.
* `:validator/message` - The error message to be displayed when the validation
  check fails. This will be assigned to the `:validation/key` as its error.

### Example

Check if the `:account/password` and `:account/password-validation` match during
registration. Since `:map` keys are isolated from each other this would be a
good solution.

```clojure
(defn password-match? [m]
  (= (:account/password m)
     (:account/password-confirmation m)))

(defmethod gungnir.model/validator :account/password-match? [_]
  {:validator/key :account/password-confirmation
   :validator/fn password-match?
   :validator/message "Passwords don't match"})
```

## Model Database Error Formatting

Sometimes even with the perfect model you can still gets errors from the
database. This can happen when you try to insert a row which contains an
existing key with a `UNIQUE CONSTRAINT`. Normally JDBC would throw an exception
for these cases. Gungnir will instead catch them and place them in your
[changesets](https://kwrooijen.github.io/gungnir/changeset.html)
`:changeset/errors` key. This error will be identified with a unique keyword and
can be modified per field using the `gungnir.model/format-error` multimethod.

### Example

During registration, you won't know if an email exists until you hit the
database. If an email exists (assuming you have a `UNIQUE CONSTRAINT` on the
email column) Gungnir will return a `:duplicate-key` error. This error can
transformed to make it more understandable for your end user.

```clojure
(defmethod gungnir.model/format-error [:account/email :duplicate-key] [_ _]
  "Email already exists")
```

Note: Gungnir is in its early stages, and only few errors are handled. If an
unhandled error occurs gungnir will instead of keyword return a Postgresql
exception code. Read more at the
[database](https://kwrooijen.github.io/gungnir/database.html) section.

---

<div class="footer-navigation">
<span>Previous: <a href="https://kwrooijen.github.io/gungnir/migrations.html">migrations</a></span>
<span>Next: <a href="https://kwrooijen.github.io/gungnir/changeset.html">changeset</a></span>
</div>
