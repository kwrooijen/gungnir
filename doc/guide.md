# Guide

Gungnir tries to supply all the tools you need to easily manage communication
between your Clojure datastructures and SQL. There are a few modules that serve
this purpose.

* [database](https://kwrooijen.github.io/gungnir/database.html) - Establishing a
  database connection, thread pooling, and handling database errors.

* [migration](https://kwrooijen.github.io/gungnir/migrations.html) - Migrations
  are important to make sure you properly transform your database and have the
  ability to roll back in case something goes wrong.

* [model](https://kwrooijen.github.io/gungnir/model.html) - Models describe your
  Clojure datastructures. Validations, transformations, error formatting,
  and relational mapping between maps are handled here.

* [changeset](https://kwrooijen.github.io/gungnir/changeset.html) - Changesets
  are used to prepare data to be saved in the database and cast external data
  to valid Clojure maps.

* [query](https://kwrooijen.github.io/gungnir/query.html) - Query provides an
  API to read from the database and is based on
  [HoneySQL](https://github.com/seancorfield/honeysql).

* [transaction](https://kwrooijen.github.io/gungnir/transactions.html) - Write transactions
  to batch multiple queries.

---

<div class="footer-navigation">
<span></span>
<span>Next: <a href="https://kwrooijen.github.io/gungnir/database.html">database</a></span>
</div>
