(defproject kwrooijen/gungnir "0.0.1-SNAPSHOT"
  :description "A fully featured, data-driven database library for Clojure."
  :url "https://github.com/kwrooijen/gungnir"
  :license {:name "MIT"}
  :source-paths ["src/clj" "src/cljc"]
  :dependencies [[com.github.seancorfield/next.jdbc "1.2.780"]
                 [org.postgresql/postgresql "42.4.0"]
                 [com.github.seancorfield/honeysql "2.2.891"]
                 [metosin/malli "0.8.9"]
                 [differ "0.3.3"]
                 [hikari-cp "2.14.0"]
                 [kwrooijen/clj-database-url "0.0.1"]
                 [org.clojure/tools.logging "1.2.4"]
                 [dev.weavejester/ragtime "0.9.2"]]
  :plugins [[lein-cloverage "1.2.1"]
            [lein-codox "0.10.8"]
            [lein-ancient "0.6.15"]]

  :repositories [["public-github" {:url "git://github.com"}]
                 ["private-github" {:url "git://github.com" :protocol :ssh}]]

  :codox {:doc-files ["README.md"
                      "doc/guide.md"
                      "doc/modules/database.md"
                      "doc/modules/migrations.md"
                      "doc/modules/model.md"
                      "doc/modules/changeset.md"
                      "doc/modules/query.md"
                      "doc/modules/transactions.md"
                      "doc/ui.md"
                      "doc/ui/form.md"]
          :output-path "docs/"
          :html {:namespace-list :nested}
          :metadata {:doc/format :markdown}
          :themes [:rdash]}
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.10.1"]
                                  [orchestra "2021.01.01-1"]
                                  [codox-theme-rdash "0.1.2"]]}
             :test {:dependencies [[orchestra "2021.01.01-1"]]}}
  :deploy-repositories [["releases" :clojars]])
