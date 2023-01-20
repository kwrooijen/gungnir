# Gungnir

A fully featured, data-driven database library for Clojure.

[![Build Status](https://travis-ci.org/kwrooijen/gungnir.svg?branch=master)](https://travis-ci.org/kwrooijen/gungnir)
[![codecov](https://codecov.io/gh/kwrooijen/gungnir/branch/master/graph/badge.svg)](https://codecov.io/gh/kwrooijen/gungnir)
[![Dependencies Status](https://versions.deps.co/kwrooijen/gungnir/status.svg)](https://versions.deps.co/kwrooijen/gungnir)
[![Clojars Project](https://img.shields.io/clojars/v/gungnir.svg)](https://clojars.org/kwrooijen/gungnir)
[![Slack](https://img.shields.io/badge/clojurians-gungnir-blue.svg?logo=slack)](https://clojurians.slack.com/messages/gungnir/)

> It is said that Gungnir could strike any target, regardless of the wielder's
> skill.
>
> \- Developer, speaking to the database admin.

[Read the guide](https://kwrooijen.github.io/gungnir/guide.html)

[Gungnir code playground](https://github.com/kwrooijen/gungnir-playground)

[Dutch Clojure Meetup - Gungnir](https://www.youtube.com/watch?v=9Sr_-Vk9wBw)

```clojure
(gungnir.database/make-datasource!
 {:adapter       "postgresql"
  :username      "postgres"
  :password      "postgres"
  :database-name "postgres"
  :server-name   "localhost"
  :port-number   5432})

(def account-model
  [:map
   [:account/id {:primary-key true} uuid?]
   [:account/email {:before-save [:string/lower-case]
                    :before-read [:string/lower-case]}
    [:re {:error/message "Invalid email"} #".+@.+\..+"]]
   [:account/password {:before-save [:bcrypt]} [:string {:min 6}]]
   [:account/password-confirmation {:virtual true} [:string {:min 6}]]
   [:account/created-at {:auto true} inst?]
   [:account/updated-at {:auto true} inst?]])

(gungnir.model/register!
 {:account account-model})

(defn password-match? [m]
  (= (:account/password m)
     (:account/password-confirmation m)))

(defmethod gungnir.model/validator :account/password-match? [_]
  {:validator/key :account/password-confirmation
   :validator/fn password-match?
   :validator/message "Passwords don't match"})

(defmethod gungnir.model/before-save :bcrypt [_k v]
  (buddy.hashers/derive v))

(defn attempt-register-account [request]
  (-> (:form-params request)
      (gungnir.changeset/cast :account)
      (gungnir.changeset/create [:account/password-match?])
      (gungnir.query/save!)))

(gungnir.query/find-by! :account/email "some@email.com") ;; => {:account/email "some@email.com",,,}

(-> (gungnir.query/limit 5)
    (gungnir.query/select :account/id :account/email)
    (gungnir.query/all! :account)) ;; => [{:account/email "..." :account/id "..."},,,]
```

## Installation

Gungnir is still in its design phase and can result in breaking changes while on
the SNAPSHOT version. Any breaking changes will be reflected in the updated
documentation.

Add the following dependencies to your `project.clj`

### Versions
* [gungnir versions](http://repo.clojars.org/kwrooijen/gungnir/0.0.2-SNAPSHOT/)
* [gungnir.ui versions](http://repo.clojars.org/kwrooijen/gungnir.ui/0.0.2-SNAPSHOT/)

```clojure
:dependencies [[kwrooijen/gungnir "0.0.2-xxxxxxxx.yyyyyy-z"]
               ;; Optionally for frontend validation
               [kwrooijen/gungnir.ui "0.0.2-xxxxxxxx.yyyyyy-z"]
               ,,,]
```

## Rationale

### Plug & Play setup with quality of life

The Clojure community tends to lean towards the "pick the libraries that you
need" method rather than using a "framework" when building an application. This
can make it challenging for new users. Once you're familiar with the Clojure
ecosystem you'll know which libraries you prefer and create your own setup. And
that's exactly what I've done.

If you want complete control over your database stack, then this is probably not
for you. If you're a beginner and are overwhelmed with all the necessary
libraries and configuration, or if you're looking for a Clojure database library
that aims to provide a quality of life experience similar to Ruby's ActiveRecord
or Elixir's Ecto, then stick around.

### Data Driven

I cannot stress this enough, I really dislike macros. Clojure and a large part
of it's community have taught me the beauty of writing data driven code. With
great libraries such as HoneySQL, Hiccup, Integrant, Reitit, Malli, I think this
is the Golden age of Data Driven Clojure. I never want to see macros in my API
again.

## Features

### Plug & Play™

Include Gungnir in your project, and off you go! Gungnir includes everything for
your database needs.

* [next-jdbc](https://github.com/seancorfield/next-jdbc)
* [hikari-cp](https://github.com/brettwooldridge/HikariCP)
* [HoneySQL](https://github.com/seancorfield/honeysql)
* [Malli](https://github.com/metosin/malli)
* [Ragtime](https://github.com/weavejester/ragtime)

### Models

Gungnir uses models to provide data validation and seamless translation between
Clojure and SQL. [Read more](https://kwrooijen.github.io/gungnir/model.html)

### Changesets

Inspired by Elixir Ecto's Changesets. Validate your data before inserting or
updating it in your database. View the actual changes being made, and aggregate
any error messages. [Read
more](https://kwrooijen.github.io/gungnir/changeset.html)

### Querying & Extension to HoneySQL

Gungnir isn't here to reinvent the wheel. Even though we have an interface for
querying the database, we can still make use of HoneySQL syntax. This allows us
to expand our queries, or write more complex ones for the edge cases. [Read
more](https://kwrooijen.github.io/gungnir/query.html)

### Migrations

Define your migrations using Clojure data structures and extend them as
needed. You can also fallback to raw SQL if necessary. [Read
more](https://kwrooijen.github.io/gungnir/migrations.html)

### Relational mapping

Relations are easily accessed with Gungnir. Records with relations will have
access to `relational atoms` which can be dereffed to query any related
rows. [Read
more](https://kwrooijen.github.io/gungnir/model.html#model-relation-definitions),
[and more](https://kwrooijen.github.io/gungnir/query.html#querying-relations)

### Frontend validation

Gungnir also provides an extra package,
[gungnir.ui](https://github.com/kwrooijen/gungnir.ui). Which provides some
validation in the frontend. [Read
more](https://kwrooijen.github.io/gungnir/ui.html)

## Resources

### Guide

[Read the guide](https://kwrooijen.github.io/gungnir/guide.html) for a full
overview of all the features and how to use them.

### Code Playground

The [Gungnir code playground](https://github.com/kwrooijen/gungnir-playground)
is a repository with an "interactive tutorial". Clone the repository and execute
the code in the core namespace step by step.

## Developing

### Testing

In order to run the tests you'll need
[docker-compose](https://docs.docker.com/compose/compose-file/). Make sure this
is an up to date version. Inside of the root directory you can setup the testing
databases with the following command.

```sh
docker-compose up -d
```

Then run the tests with `lein`

```sh
lein test
```

## Author / License

Released under the [MIT License] by [Kevin William van Rooijen].

[Kevin William van Rooijen]: https://twitter.com/kwrooijen

[MIT License]: https://github.com/kwrooijen/gungnir/blob/master/LICENSE
