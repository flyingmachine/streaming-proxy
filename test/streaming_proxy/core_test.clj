(ns streaming-proxy.core-test
  "Creates a test endpoint and a test proxy server, tests that
  requests to proxy get sent to endpoint and that proxy can handle
  responses"
  (:require [streaming-proxy.core-test-helpers :refer :all]
            [streaming-proxy.core :as sp]
            [org.httpkit.server :as hks]
            [org.httpkit.client :as hkc]
            [org.httpkit.timer  :as hkt]
            [clj-http.client :as client]
            [clojure.java.io :as io]
            [ring.middleware.multipart-params :as mp]
            [midje.sweet :refer :all]))

(def upload-dest "uploaded-test-file")

(defn endpoint-handler [req]
  (condp = (:uri req)
    "/upload" (do (.renameTo (get-in req [:params "file" :tempfile]) (io/file upload-dest))
                  {:status 200 :body ""})
    "/stream" (hks/with-channel req chan
                (sp/send-response chan {:status 200
                                        :body (io/input-stream (io/resource "walden"))}))
    
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    "default"}))

(def endpoint-app (mp/wrap-multipart-params endpoint-handler))


(defn proxy-error-handler
  [channel req source response & exception]
  (println "error: " req :source response)
  (sp/send-response channel response))

(defn proxy-wrapper
  [handler endpoint-port]
  #(handler (merge % {:streaming-proxy-url (str "http://localhost:" endpoint-port (:uri %))
                      :streaming-proxy-error-handler proxy-error-handler
                      :timeout 1000})))

(defn proxy-app
  [endpoint-port]
  (proxy-wrapper sp/proxy-handler endpoint-port))

(defn url [server path] (str "http://localhost:" (:port @server) path))
(defn delete-file
  [file]
  (when (.exists (io/file file))
    (io/delete-file file)))

(def repl-proxy (atom nil))
(def repl-endpoint (atom nil))
(defn start-repl-servers
  []
  (start-server repl-endpoint endpoint-app)
  (start-server repl-proxy (proxy-app (:port @repl-endpoint))))
(defn stop-repl-servers
  []
  (stop-server repl-endpoint)
  (stop-server repl-proxy))

(let [proxy    (atom nil)
      endpoint (atom nil)]
  (with-state-changes [(before :facts (do (start-server endpoint endpoint-app)
                                          (start-server proxy (proxy-app (:port @endpoint)))
                                          (delete-file upload-dest)))
                       (after :contents (do (stop-server endpoint)
                                            (stop-server proxy)))]
    
    ;; server puts file at location, test checks that file is there
    (fact "you can upload a file using POST"
      @(hkc/post (url proxy "/upload")
                  {:multipart [{:name "file"
                                :content (io/file (io/resource "test-upload"))
                                :filename "test-upload"}]})
      (slurp upload-dest) => "Kermit")

    ;; TODO why doesn't this work with the httpkit client?
    ;; TODO enhance to ensure that endpoint stream is passed along streaming
    (fact "handles streaming responses"
      (let [res (client/get (url proxy "/stream"))]
        (String. (:body res))
        => (slurp (io/resource "walden"))))))

(fact "GET")
(fact "PUT")
(fact "DELETE")
