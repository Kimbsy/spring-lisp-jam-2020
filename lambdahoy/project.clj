(defproject lambdahoy "0.1.0-SNAPSHOT"
  :description "Lambdahoy: boat-shootingly good fun"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [com.googlecode.soundlibs/mp3spi "1.9.5-1"]
                 [quil "3.1.0"]]
  :aot [lambdahoy.core]
  :main lambdahoy.core)
