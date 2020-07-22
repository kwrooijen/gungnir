(defproject kwrooijen/gungnir "0.0.1-SNAPSHOT"
  :description "High level Clojure database library"
  :url "https://github.com/kwrooijen/gungnir"
  :license {:name "MIT"}
  :source-paths ["src/clj" "src/cljs" "src/cljc"]
  :dependencies
  [[seancorfield/next.jdbc "1.1.569"]
   [org.postgresql/postgresql "42.2.14"]
   [honeysql "1.0.444"]
   [metosin/malli "0.0.1-SNAPSHOT"]
   [differ "0.3.3"]
   [hikari-cp "2.13.0"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.10.1"]
                                  [orchestra "2020.07.12-1"]]}
             :test {:dependencies [[orchestra "2020.07.12-1"]]}}
  :deploy-repositories [["releases" :clojars]])
