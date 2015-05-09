(defproject streaming-proxy "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.6.0"]]
  
  :profiles {:dev {:dependencies [[http-kit "2.1.18"]
                                  [clj-http "1.1.1"]
                                  [ring "1.3.1"]
                                  [ring/ring-mock "0.2.0"]
                                  [midje "1.6.3"]]}})
