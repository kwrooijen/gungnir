# Gungnir CHANGELOG

## Breaking changes in pre-alpha:

* 2021.01.13
RENAME `gungnir.database/*database*` -> `gungnir.database/*datasource*`
RENAME `:through` -> `:foreign-key`

* 2022.07.22
Use Honey SQL version 2.0
You can no longer name your table `user` as it's used by Postgres internally.
