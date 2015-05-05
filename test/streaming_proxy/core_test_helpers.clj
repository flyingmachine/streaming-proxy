(ns streaming-proxy.core-test-helpers
  (:require [org.httpkit.server :as hk]))

(defn start-hk
  [app]
  (some (fn [port] (try {:proc (hk/run-server app {:port port})
                        :port port}
                       (catch Exception e)))
        (range 49152 65535)))

(defn start-server
  [server app]
  (or @server
      (if-let [server-state (start-hk app)]
        (do (reset! server server-state)
            (println "Started test server at" (:port server-state))
            server)
        (throw (Exception. "Could not start test server")))))

(defn stop-server
  [server]
  (when-let [p (:proc @server)] (p))
  (reset! server nil))
