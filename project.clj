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
   [differ "0.3.3"]]
  :plugins [[lein-cloverage "1.0.13"]
            [lein-shell "0.5.0"]
            [lein-ancient "0.6.15"]
            [lein-changelog "0.3.2"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.10.1"]]}}
  :deploy-repositories [["releases" :clojars]])
