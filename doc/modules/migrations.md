# Migrations

Migrations are currently not built-in to Gungnir. Eventually we'll create a data
driven solution but for now we'll have to use one of the other libraries
available.

[Ragtime](https://github.com/weavejester/ragtime) is the recommended migration
library which can serve this purpose. It's simple and straightforward. It isn't
included in Gungnir because we want to explore our options before 1.0 release.

## Installation

Add `ragtime` as a dependency to your project's `project.clj`.
```clojure
(defproject my-project "0.0.0"
 :dependencies [[kwrooijen/gungnir "0.0.1-SNAPSHOT"]
                [ragtime "0.8.0"]
                ,,,]
  ,,,)
```

## Defining Migrations

Next you can define migrations using [EDN](https://github.com/edn-format/edn)
files in your resource path. The EDN files will contain a map with an `:up` and
`:down` key, for migrating and rolling back respectively.

### Enabling `UUID` support in Postgres

If you'd like `UUID` support in your application, you can enable it with the
following migration.

```clojure
;; resources/migrations/000-uuid.edn

{:up ["CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";"]
 :down ["DROP EXTENSION \"uuid-ossp\";"]}
```

### Enabling auto updating `updated_at` in Postgres

It can be useful to keep track when rows are last updated. Maybe a bug was
introduced at a specific time and this could help you pinpoint the issue.

This example enables automatic updates for the `updated_at` column for all
tables.

```clojure
;; resources/migrations/001-auto-updated-at.edn

{:up
 ["CREATE OR REPLACE FUNCTION trigger_set_updated_at()
   RETURNS TRIGGER AS $$
   BEGIN
     NEW.updated_at = NOW();
     RETURN NEW;
   END;
   $$ LANGUAGE plpgsql;"]
 :down ["DROP FUNCTION trigger_set_updated_at();"]}
```

### Creating tables

Next you can start defining migrations for creating tables. For example you
could have a `user` table with a `UUID` as the primary key, and an `updated_at`
column which will automatically update.

```clojure
{:up ["CREATE TABLE \"user\"
      ( id uuid DEFAULT uuid_generate_v4 () PRIMARY KEY
      , email TEXT NOT NULL UNIQUE
      , password TEXT NOT NULL
      , cancellation_effective_date DATE
      , created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
      , updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
      );"]
 :down ["DROP TABLE \"user\";"]}
```

## Running migrations

For convenience you can add the Ragtime configuration in your `user.clj`. This
will allow you to access it whenever you enter the REPL in the user namespace.

```clojure
(ns user
  (:require 
    [ragtime.jdbc :as jdbc]
    [gungnir.database]))

(def config
  {:datastore (ragtime.jdbc/sql-database {:datasource gungnir.database/*datasource*})
   :migrations (jdbc/load-resources "migrations")})
```

Enter the REPL (e.g. `lein repl`). Require the `ragtime.repl` namespace and run
the migrations using the `ragtime.repl/migrate` function. All pending migrations
will be executed.

``` clojure
user=> (require '[ragtime.repl :as repl])
nil
user=> (repl/migrate config)
Applying 000-uuid
Applying 001-auto-updated-at
Applying 002-user
```

You can also rollback migrations one by one using the `ragtime.repl/rollback`
function.

```clojure
nil
user=> (repl/rollback config)
Rolling back 002-user
nil
user=> (repl/rollback config)
Rolling back 001-auto-updated-at
nil
user=> (repl/rollback config)
Rolling back 000-uuid
nil
```

---

<div class="footer-navigation">
<span>Previous: <a href="https://kwrooijen.github.io/gungnir/database.html">database</a></span>
<span>Next: <a href="https://kwrooijen.github.io/gungnir/model.html">model</a></span>
</div>
