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

---

<div class="footer-navigation">
<span>Previous: <a href="https://kwrooijen.github.io/gungnir/guide.html">guide</a></span>
<span>Next: <a href="https://kwrooijen.github.io/gungnir/migrations.html">migrations</a></span>
</div>
