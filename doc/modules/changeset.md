# Gungnir Changesets

## Malli Schemas

## Model Field Properties

### `:primary-key` (required)

Describe which key is the `PRIMARY KEY` in your database. This is required for
Gungnir to be able to make use of the full querying API, as well as the
relational mapping.

```clojure
[:user/id {:primary-key true} uuid?]
```

### `:auto`

Tell Gungnir that this key is automatically managed by the database, and Gungir
should never make an attempt to modify it. This is useful for e.g. `TIMESTAMP`
columns which might update automatically.

```sql
created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
```

```clojure
[:user/created-at {:auto true} inst?]
```

### `:virtual`

Tell Gungnir that you want this key to be part of your Model, but it does not
exist as a field in the database. Gungnir will make no attempt to query, or save
this key. Useful for things such as password-confirmation fields.

```clojure
[:user/password-confirmation {:virtual true} string?]
```

### `:before-save`

Add hooks to a column to be executed before you save to the database. This is
useful to keep data transformations consistent. For example you always want to
encrypt passwords before saving them to the database. Passwords could be set
during creating, updated on the profile page, changed through a password
reset. In all of these cases you must encrypt the user's inputted password
before inserting to the database.

Add a `:bcrypt` key to the `:before-save` vector for `:user/password`.

```clojure
[:user/password {:before-save [:bcrypt} string?]
```

And define the `:bycrypt` `:before-save` handler. Handlers take the key and
value of field in question. The resulting value of `gungnir.model/before-save`
will be saved in the database.

```clojure
(defmethod gungnir.model/before-save :bcrypt [_k v]
  (hashers/derive v))
```

### `:before-read`

Add hooks to a column to be executed before you read it from database. This is
useful if want to sanitize any query paramaters before reading. For example you
could save all emails as lowercase (with the `gungnir.model/before-save`
hook). Then add a `:before-read` hook to lowercase the email field when you
query the database. That way you'll be able to deal case sensitive data.

Add the `:before-save` and `:before-read` hooks.

```clojure
[:user/email 
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
it yourself.

### `:after-read`

Add hooks to a column to be executed after you read it from database. This is
useful if want to transform any data after you read it from the database. You
could encrypt data before saving it, and decrypt it after reading it for extra
security. Another use case is saving keywords to the database as strings, and
parsing it as EDN after reading it.


In this case, user has an `:user/option` key, which is a qualified-keyword. You
can't store keywords in SQL, so it's converted to a string.

```clojure
[:user/option 
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
