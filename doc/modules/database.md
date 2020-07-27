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

---

<div class="footer-navigation">
<span>Previous: <a href="https://kwrooijen.github.io/gungnir/guide.html">guide</a></span>
<span>Next: <a href="https://kwrooijen.github.io/gungnir/migrations.html">migrations</a></span>
</div>
