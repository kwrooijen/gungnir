# Migrations

Migrations are defined using data in EDN files. They are based on the
[Ragtime](https://github.com/weavejester/ragtime) format and 100% backwards
compatible. So you can choose to use raw SQL if you prefer. Every migration file
contains a map with an `:up` and `:down` key. These keys contain a vector of SQL
statements.

## Creating a table

### Raw SQL

If you want to use raw SQL you can use the same format that Ragtime uses. You
can add multiple statements in the `:up` and `:down` keys to run multiple SQL
commands.

```clojure
;; resources/migrations/000-products.edn
{:up ["CREATE TABLE products
      ( id BIGSERIAL PRIMARY KEY
      , name TEXT NOT NULL UNIQUE
      , visible BOOLEAN NOT NULL DEFAULT false
      , created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
      , updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
      );"]
 :down ["DROP TABLE products"]}
```

### Clojure Data

You can use Clojure datastructures to define your migrations. The format is
similar to how Malli defines maps. Generally a vector where the first value is a
`qualified-keyword`, second value is optionally a map with options, and then a
finite amount of values.

We can recreate the same statement with the following data:

```clojure
;; resources/migrations/000-products.edn
{:up [[:table/create {:table :products}
       [:column/add
        [:id {:primary-key true} :bigserial]
        [:name {:unique true} :string]
        [:visible {:default false} :boolean]
        [:created-at {:default :current-timestamp} :timestamp]
        [:updated-at {:default :current-timestamp} :timestamp]]]]
 :down [[:table/drop :products]]}
```

We use the `:table/create` key to create a new table and define the name using
the `:table` key in the options map. The following values are actions. The
`:column/add` action allows us to add all the columns we need with type and
additional options.

If no primary-key is defined in our migration, Gungnir will by default
automatically add an `id` column the the `BIGSERIAL` type as a
primary-key. Gungnir also provides a special column action called
`:gungnir/timestamps` which adds a `created_at` and `updated_at` column. We can
rewrite our previous migration to the following:

```clojure
;; resources/migrations/000-products.edn
{:up [[:table/create {:table :products}
       [:column/add
        [:name {:unique true} :string]
        [:visible {:default false} :boolean]
        [:gungnir/timestamps]]]]
 :down [[:table/drop :products]]}
```

## Extending 

Sometimes you might need more control for your migrations. For example you might
want to add data to your database, generated with Clojure. Or Gungnir is missing
a migration feature that should be implemented in a data-driven action (In that
case, feel free to open a pull request!).

To create your own migration action, you need to implement the
`gungnir.migration/format-action` multimethod. It matches on a qualified-keyword
representing the action, the first value of the vector. The second value of the
vector is an optional map. The rest of the values is just a collection of
input. The return value of this multimethod should be a string representing a
raw SQL query.

```clojure
;; 001-some-migration.edn

{:up [[:my/migration-action
       {:some-option 123}
       [:my/field 1]
       [:my/field 2]
       [:my/field 3]]]

 :down [[:my/migration-reverse 1 2 3]]}

;; my-app/core.clj
(defmethod format-action :my/migration-action [[_key opts & fields]]
  ;; _key   : :my/migration-action
  ;; opts   : {:some-option 123}
  ;; fields : '([:my/field 1] [:my/field 2] [:my/field 3])
  ;;
  ;; Implement your query
  "INSERT INTO account ...")

(defmethod format-action :my/migration-reverse [[_key opts & fields]]
  ;; _key   : :my/migration-reverse
  ;; opts   : {}
  ;; fields : '(1 2 3)
  ;;
  ;; Implement your query
  "DELETE from account ...")
```

## Keys


### Table

| key             | description             |
|-----------------|-------------------------|
| `:table/create` | Create a new table      |
| `:table/alter`  | Alter an existing table |
| `:table/drop`   | Drop an existing table  |

### Table options

| key            | description                                                                                          |
|----------------|------------------------------------------------------------------------------------------------------|
| `:table`       | Table name to create or alter                                                                        |
| `:primary-key` | Set to `false` to disable automatic primary-key generation. Set to `:uuid` use a uuid as primary-key |

### Column

| key            | description             |
|----------------|-------------------------|
| `:column/add`  | Create a new table      |
| `:column/drop` | Alter an existing table |

### Column types

| key          | postgres type                       |
|--------------|-------------------------------------|
| `:string`    | TEXT or VARCHAR(:size)              |
| `:int`       | INTEGER                             |
| `:float`     | FLOAT(:size) (:size default 8)      |
| `:boolean`   | BOOLEAN                             |
| `:serial`    | SERIAL                              |
| `:bigserial` | BIGSERIAL                           |
| `:timestamp` | TIMESTAMP                           |
| `:uuid`      | UUID (requires uuid-ossp extension) |

### Column type options

| key            | description                                                                          |
|----------------|--------------------------------------------------------------------------------------|
| `:primary-key` | Makes column primary key                                                             |
| `:size`        | Sets the size of the value (:string / :float)                                        |
| `:default`     | Set the default value on creation                                                    |
| `:optional`    | Make a column optional. By default every column is required                          |
| `:unique`      | Column should be unique                                                              |
| `:references`  | Column is a foreign referencing a :table/column (qualified keyword e.g. :account/id) |

## Running migrations

For convenience you can add the Ragtime configuration in your `user.clj`. This
will allow you to access it whenever you enter the REPL in the user namespace.

```clojure
(ns user
  (:require 
    [gungnir.migration]))

(def migrations (gungnir.migration/load-resources "migrations"))
```

Enter the REPL (e.g. `lein repl`). Require the `gungnir.migration` namespace and run
the migrations using the `gungnir.migration/migrate!` function. All pending migrations
will be executed.

``` clojure
user=> (require '[gungnir.migration :as migration])
nil
user=> (migration/migrate! migrations)
Applying 000-uuid
Applying 001-auto-updated-at
Applying 002-account
```

You can also rollback migrations one by one using the `gungnir.migration/rollback!`
function.

```clojure
nil
user=> (migration/rollback! config)
Rolling back 002-account
nil
user=> (migration/rollback! config)
Rolling back 001-auto-updated-at
nil
user=> (migration/rollback! config)
Rolling back 000-uuid
nil
```

---

<div class="footer-navigation">
<span>Previous: <a href="https://kwrooijen.github.io/gungnir/database.html">database</a></span>
<span>Next: <a href="https://kwrooijen.github.io/gungnir/model.html">model</a></span>
</div>
