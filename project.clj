(defproject kwrooijen/gungnir "0.0.1-SNAPSHOT"
  :description "High level Clojure database library"
  :url "https://github.com/kwrooijen/gungnir"
  :license {:name "MIT"}
  :source-paths ["src/clj" "src/cljc"]
  :dependencies [[seancorfield/next.jdbc "1.1.613"]
                 [org.postgresql/postgresql "42.2.18"]
                 [honeysql "1.0.444"]
                 [metosin/malli "0.2.1"]
                 [differ "0.3.3"]
                 [hikari-cp "2.13.0"]
                 [kwrooijen/clj-database-url "0.0.1"]
                 [org.clojure/tools.logging "1.1.0"]]
  :plugins [[lein-cloverage "1.2.1"]
            [lein-codox "0.10.7"]
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
