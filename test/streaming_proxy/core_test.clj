(ns streaming-proxy.core-test
  "Creates a test endpoint and a test proxy server, tests that
  requests to proxy get sent to endpoint and that proxy can handle
  responses"
  (:require [streaming-proxy.core-test-helpers :refer :all]
            [streaming-proxy.core :as sp]
            [org.httpkit.server :as hks]
            [org.httpkit.client :as hkc]
            [clojure.java.io :as io]
            [ring.middleware.multipart-params :as mp]
            [midje.sweet :refer :all]))

(def upload-dest "uploaded-test-file")

(defn endpoint-handler [req]
  (condp = (:uri req)
    "/upload" (do (.renameTo (get-in req [:params "file" :tempfile]) (io/file upload-dest))
                  {:status 200 :body ""})
    "/stream" (hks/with-channel req chan
                (sp/send-response {:status 200
                                   :body (io/input-stream (io/resource "walden"))}
                                  chan))
    "/400"    {:status 400 :body "nope"}
    
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    "default"}))

(def endpoint-app (mp/wrap-multipart-params endpoint-handler))


(defn proxy-response-handler
  [res]
  (if (>= (:status res) 400)
    (merge res {:body (str "Downstream error: " (slurp (io/reader (:body res))))})
    res))

(defn proxy-wrapper
  [handler endpoint-port]
  #(handler (merge % {:streaming-proxy-url (str "http://localhost:" endpoint-port (:uri %))
                      :streaming-proxy-response-handler proxy-response-handler
                      :timeout 1000})))

(defn proxy-app
  [endpoint-port]
  (proxy-wrapper sp/proxy-handler endpoint-port))

(defn url [server path] (str "http://localhost:" (:port @server) path))
(defn delete-file
  [file]
  (when (.exists (io/file file))
    (io/delete-file file)))

(defonce repl-proxy (atom nil))
(defonce repl-endpoint (atom nil))
(defn start-repl-servers
  []
  (start-server repl-endpoint endpoint-app)
  (start-server repl-proxy (proxy-app (:port @repl-endpoint))))
(defn stop-repl-servers
  []
  (stop-server repl-endpoint)
  (stop-server repl-proxy))
(defn restart-repl-servers
  []
  (stop-repl-servers)
  (start-repl-servers))

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

    ;; TODO test that endpoint response is sent to client as it's received
    (fact "handles streaming responses"
      (let [res @(hkc/get (url proxy "/stream"))]
        (:body res)
        => (slurp (io/resource "walden"))))

    (fact "can transform response"
      (let [res @(hkc/get (url proxy "/400"))]
        (:status res)
        => 400

        (:body res)
        => "Downstream error: nope"))

    (fact "url has to exist"
      (sp/proxy-handler {})
      => (throws java.lang.AssertionError "Assert failed: url"))))
