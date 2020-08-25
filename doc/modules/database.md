# Database

The `gungnir.database` namespace is responsible for managing the database
connection. By default it uses the
[HikariCP](https://github.com/brettwooldridge/HikariCP) connection pool.


## Establishing a connection

Use the `gungnir.database/make-datasource!` function to establish a database
connection. Generally you'd want your system state manager to manage
this. E.g. Integrant, Component, Mount.

This function accepts any of the following arguments:

* `JDBC_DATABASE_URL` - The standard format used to create a database connection
  in Java.
* `DATABASE_URL` - The universal format used to create a database
  connection. Used by services such as Heroku or Render.
* `HikariCP` configuration - A [HikariCP configuration
  map](https://github.com/tomekw/hikari-cp#configuration-options). Optionally
  can also be given as a second argument, where the first argument is either a
  `JDBC_DATABASE_URL` or `DATABASE_URL`.

```clojure
(gungnir.database/make-datasource!
  "jdbc:postgresql://localhost:5432/postgres?user=postgres&password=postgres")
  
(gungnir.database/make-datasource!
  "postgres://postgres:postgres@localhost:5432/postgres")

(gungnir.database/make-datasource!
  {:adapter       "postgresql"
   :username      "postgres"
   :password      "postgres"
   :database-name "postgres"
   :server-name   "localhost"
   :port-number   5432})
```

Once established, the datasource will be stored in
`gungnir.database/*database*`. If you're using the Gungnir
[query](https://kwrooijen.github.io/gungnir/query.html) API you won't have to
access this yourself. Currently Gungnir only supports creating a single
datasource.

## Setting a custom connection pool

A custom connection pool can also be set using the
`gungnir.database/set-datasource!` function.


## Exception handling

When JDBC runs into an error it will raise an exception. This isn't something
that we want. Instead we would like to add errors to our changeset. This can be
done using the `gungnir.database/exception->map` multimethod which takes a
SQLException and matches by the Postgres error. It should then return a map with
a field key, and a keyword representing the error.


In this example we catch the `"23505"` Postgres error.

This does a few things:

* Parse the `SQLException` and find the related sql key
* Convert the sql key to a valid model record key
* Apply the `gungnir.model/format-error` multimethod in case of custom error messages
* Return `{record-key [error-message]}` (which by default will be `{record-key [:duplicate-key]}`)

```clojure
(defmethod exception->map "23505" [^SQLException e]
  (let [error (.getMessage e)
        sql-key (remove-quotes (re-find #"\".*\"" error))
        record-key (sql-key->keyword sql-key)]
    {record-key [(gungnir.model/format-error record-key :duplicate-key)]}))
```

In the event that an SQLException does not have an `exception->map`
implementation, it will return `{:unknown [sql-exception-code]}`. The next step
is to open an issue or create a pull request to Gungnir. You can implement the
`exception->map` clause yourself as well.

## Multiple datasources

By default Gungnir has a global datasource. If you need to access multiple
datasources we can use the `gungnir.factory` namespace. A local datasource will
be bound to a specific namespace that you create. Inside of this namespace we
will call `gungnir.factory/make-datasource-map!` and bind the resulting
functions that are returned.

```clojure
(ns my.datasource
  (:require
   [gungnir.factory]))

(def datasource-opts
  {:adapter       "postgresql"
   :username      "postgres"
   :password      "postgres"
   :database-name "postgres"
   :server-name   "localhost"
   :port-number   5432})

(let [datasource-map (gungnir.factory/make-datasource-map! datasource-opts)]
  (def close      (:close!-fn datasource-map))
  (def datasource (:datasource datasource-map))
  (def find!      (:find!-fn datasource-map))
  (def find-by!   (:find-by!-fn datasource-map))
  (def all!       (:all!-fn datasource-map))
  (def delete!    (:delete!-fn datasource-map))
  (def save!      (:save!-fn datasource-map)))
```

Now when you call `my.datasource/find!` which will query it's local datasource
instead of the global datasource with `q/find!`.

## Logging

Gungnir uses [clojure/tools.logging](https://github.com/clojure/tools.logging)
to log errors and debug information. You can plug in your own logging backend,
whether that be timbre, slf4j, log4j, apache commons logging, etc.

In some error cases Gungnir might print out SQL or HoneySQL forms. These are
always logged under the `gungnir.sql` ns at `:debug` level. To enable or disable
these, see your logging backend configuration documentation.

---

<div class="footer-navigation">
<span>Previous: <a href="https://kwrooijen.github.io/gungnir/guide.html">guide</a></span>
<span>Next: <a href="https://kwrooijen.github.io/gungnir/migrations.html">migrations</a></span>
</div>
