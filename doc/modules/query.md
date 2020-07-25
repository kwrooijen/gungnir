# Query

Gungnir provides an API for querying data based on your models. This API is
based on 3 functions. All of these functions are extendable with HoneySQL, and
you can also fall back to HoneySQL completely if necessary. Making Gungnir very
flexible. Additionally there's a convenience method to querying relations.

* `gungnir.query/find-by!` - Find a single record based on key value matches or
  return `nil`.

* `gungnir.query/find!` - Find a single record based on a primary-key or return
  `nil`.

* `gungnir.query/all!` - Find a collection of records based on key value matches
  or return an empty vector.

## gungnir.query/find-by!

Run a query and return a single record or nil, based on matching keys and
values.


```clojure
(find-by :user/email "user@test.com"
         :user/validated true)
```

Optionally extend the queries using HoneySQL

```clojure
(-> (select :user/username)
    (find-by :user/email "user@test.com"
             :user/validated true))
```

## gungnir.query/find!

Run a query and return a single record or nil.

Depending on the types of arguments provided, `find!` will have
different behaviors.

### Arity 1

Use `form` to query the database and return a single record or
nil. Will not find a record by it's primary-key.

`form` - HoneySQL form which will be used to query the database.

```clojure
(-> (select :*)
    (from :user)
    (where [:= :user/id user-id])
    (find!))
```

### Arity 2

Find a record by it's primary-key from the table represented by the
`model-key`.

`model-key` - Model key which will identify which table to read from.

`primary-key-value` - The value of the primary key to match with.

```clojure
(find! :user user-id)
```

### Arity 3

Find a record by it's primary-key from the table represented by the
`model-key`. Extended with the HoneySQL `form`.

`form` - HoneySQL form which will be used to query the database.

`model-key` - Model key which will identify which table to read from.

`primary-key-value` - The value of the primary key to match with.

Find a single record by its `primary-key-value` from `table`.
Optionally extend the query using a HoneySQL `form`."

```clojure
(-> (select :user/email)
    (where [:= :user/active false])
    (find! :user user-id))
```

---

## gungnir.query/all!

Run a query and return a collection of records.

Depending on the types of arguments provided, `all!` will have
different behaviors.

### Arity 1

`?table` - either a `simple-keyword` representing a Gungnir which
will return all rows of that table. Or it a `HoneySQL` form query
the database using that.

```clojure
(all! :user)

(-> (select :*)
    (from :user)
    (all!))
```

### Arity 2

`?form` - either a `HoneySQL` form which will extend the query. Or a
`qualified-keyword` representing a model field.

`args` - a collection of keys and values. Where the keys are
`qualified-keywords` representing a model fields which will be
matched with the values. If no key value pairs are provided then you
must supply a model name as a `simple-keyword`.

```clojure
(all! :user/validated false
      :user/type :user/pro)

(-> (where [:> :user/date expiration-date])
    (limit 30)
    (all! :user))
```

---

## Querying Relations




---

<div class="footer-navigation">
<span>Previous: <a href="https://kwrooijen.github.io/gungnir/changeset.html">changeset</a></span>
</div>
