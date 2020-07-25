(defproject kwrooijen/gungnir "0.0.1-SNAPSHOT"
  :description "High level Clojure database library"
  :url "https://github.com/kwrooijen/gungnir"
  :license {:name "MIT"}
  :source-paths ["src/clj" "src/cljs" "src/cljc"]
  :dependencies [[seancorfield/next.jdbc "1.1.569"]
                 [org.postgresql/postgresql "42.2.14"]
                 [honeysql "1.0.444"]
                 [metosin/malli "0.0.1-SNAPSHOT"]
                 [differ "0.3.3"]
                 [hikari-cp "2.13.0"]]
  :plugins [[lein-cloverage "1.1.2"]
            [lein-codox "0.10.7"]
            [lein-ancient "0.6.15"]]
  :codox {:doc-files ["README.md"
                      "doc/guide.md"
                      "doc/modules/database.md"
                      "doc/modules/migrations.md"
                      "doc/modules/model.md"
                      "doc/modules/changeset.md"
                      "doc/modules/query.md"
                      "doc/ui.md"
                      "doc/ui/form.md"]
          :output-path "docs/"
          :html {:namespace-list :nested}
          :metadata {:doc/format :markdown}
          :themes [:rdash]}
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.10.1"]
                                  [orchestra "2020.07.12-1"]
                                  [codox-theme-rdash "0.1.2"]]}
             :test {:dependencies [[orchestra "2020.07.12-1"]]}}
  :deploy-repositories [["releases" :clojars]])
