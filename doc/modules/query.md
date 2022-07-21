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
(find-by! :account/email "account@test.com"
          :account/validated true)
```

Optionally extend the queries using HoneySQL. `gungnir.query` aliases the
HoneySQL helper functions, so you don't have to require that separately.

```clojure
(-> (select :account/accountname)
    (find-by! :account/email "account@test.com"
              :account/validated true))
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
    (from :account)
    (where [:= :account/id account-id])
    (find!))
```

### Arity 2

Find a record by it's primary-key from the table represented by the
`model-key`.

`model-key` - Model key which will identify which table to read from.

`primary-key-value` - The value of the primary key to match with.

```clojure
(find! :account account-id)
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
(-> (select :account/email)
    (where [:= :account/active false])
    (find! :account account-id))
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
(all! :account)

(-> (select :*)
    (from :account)
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
(all! :account/validated false
      :account/type :account/pro)

(-> (where [:> :account/date expiration-date])
    (limit 30)
    (all! :account))
```

---

## Querying Relations

Gungnir provides an atom for accessing relations. If you've defined relations in
the [model](https://kwrooijen.github.io/gungnir/model.html) these atoms will
become available when you query records.

### Relation atom

The way these atoms work is you don't query the relations immediately. Instead
the query will only be executed once you `deref` the atom. Additionally you can
modify the query this atom will execute beforehand by using `swap!` and any
`HoneySQL` formatting function.

### Relation atom - deref

A very simple example would be to find a account by their email. If in the `:account` model
has a `has-many` posts definition:

```clojure
{:has-many {:account/posts {model :post :foreign-key :post/account-id}}}

;; NOTE: If no `:foreign-key` is provided, Gungnir will fill it in. As long as
;; the column has the form `{model}-id` it will work. When the column name is
;; not the same as the model name (e.g. `author-id` for the account model) you'll
;; have to specify it yourself.

{:has-many {:account/posts {model :post}}}
```

You'll be able to query a account's posts:

```clojure
(-> (q/find-by! :account/email "account@test.com")
    :account/posts
    (deref)) ;; Query all posts belong to the account "account@test.com"
```

If posts also have many comments, you can deref those relations as well simply
by following the path.

```clojure
(-> (q/find-by! :account/email "account@test.com")
    :account/posts
    (deref)
    (first)
    :post/comments
    (deref)) ;; Results in the first post's comments
```

Relations are a two way street, technically speaking you could go back to the
origin record using `deref`.

```clojure
(-> (q/find-by! :account/email "account@test.com")
    :account/posts
    (deref)
    (first)
    :post/account
    (deref)) ;; Back to the origin account record
```

### Relation atom - swap!

If we don't want all the account's posts, but only their top five, you can modify
the atom with `swap!` before executing `deref`.

```clojure
(-> (q/find-by! :account/email "account@test.com")
    :account/posts
    (swap! q/limit 5)
    (swap! q/order-by [:post/score :desc])
    (deref))
```

### gungnir.query/load!

Another helper function provided by Gungnir is the `gungnir.query/load!`
function. This allows you to deref relations in a record without having to
remove them from the record itself.

```clojure
(-> (q/find-by! :account/email "account@test.com")
    (q/load! :account/posts :account/comments))

;; => #:account{:id #uuid ",,,"
;; =>           :posts [#:post{,,,}
;; =>                   ,,,]
;; =>           :comments [#:comment{,,,}
;; =>                      ,,,]}
```

Combined with this you can use the `update` core function to modify the atoms
before loading.

```clojure
(-> (q/find-by! :account/email "account@test.com")
    (update :account/posts q/limit 1)
    (update :account/comment q/limit 1)
    (q/load! :account/posts :account/comments))

;; Notice only 1 element in the vectors
;; => #:account{:id #uuid ",,,"
;; =>           :posts [#:post{,,,}]
;; =>           :comments [#:comment{,,,}]
```

---

<div class="footer-navigation">
<span>Previous: <a href="https://kwrooijen.github.io/gungnir/changeset.html">changeset</a></span>
<span>Next: <a href="https://kwrooijen.github.io/gungnir/transactions.html">transaction</a></span>
</div>
