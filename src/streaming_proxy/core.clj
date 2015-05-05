(ns streaming-proxy.core
  "Performs request forwarding and response handling"
  (:require [org.httpkit.server :as hks]
            [org.httpkit.client :as hkc]
            [clj-http.client :as client]
            [ring.util.io]))

(defn dissoc-headers
  "Helper function to remove headers"
  [req & headers]
  (assoc req :headers (apply dissoc (:headers req) headers)))

(def byte-array-size 1000)

(defn ensure-body-input-stream
  "If the 'transfer-encoding' header is 'chunked' and the request body
  is nil, then clj-http (actually, Apache HTTP library) loses its mind
  and never completes a request. This ensures that this situation
  never happens by replacing a nil body with an empty string input
  stream and removing the transfer-encoding heading because Apache
  HTTP will complain if the header is present"
  [req]
  (if (:body req)
    req
    (-> req
        (assoc :body (ring.util.io/string-input-stream ""))
        (dissoc-headers "transfer-encoding"))))

(defn clean-req
  "Do some header jiggery to ensure that the incoming request can be
  passed to clj-http without errors or problematic behavior"
  [req]
  (-> req
      (assoc :length (when-let [cl (get-in req [:headers "content-length"])] (Integer. cl)))
      (dissoc-headers "content-length")
      ensure-body-input-stream))


(defn send-response
  "Send a response on an http-kit channel. Stream it if the response
  is streamable."
  [channel {:keys [body] :as res}]
  (if (string? body)
    (hks/send! channel res)
    (do (hks/send! channel (select-keys res [:status :headers]) false)
        (loop [bytes (byte-array byte-array-size)
               bytes-read (if body (.read body bytes) -1)]
          (hks/close channel)
          (if (= -1 bytes-read)
            (hks/close channel)
            (do
              (hks/send! channel (clojure.java.io/input-stream
                                  (if (< bytes-read byte-array-size)
                                    (byte-array (take bytes-read bytes))
                                    bytes))
                         false)
              (let [bytes (byte-array byte-array-size)]
                (recur bytes (.read body bytes)))))))))

;; TODO make pre checking that url exists
(defn proxy-handler
  [{err-handler :streaming-proxy-error-handler
    url         :streaming-proxy-url
    :or {err-handler (fn [& _])}
    :as req}]
  (hks/with-channel req channel
    (try (let [req (merge {:as :stream
                           :timeout 30000 ;ms
                           :decode-cookies false
                           :throw-exceptions false
                           :url url}
                          (clean-req req))
               {:keys [status headers body] :as res} (client/request req)]
           (println "PROXY RES" res)
           (if (>= status 400)
             (err-handler channel req :downstream res)
             (send-response channel res)))
         (catch java.net.ConnectException e
           (err-handler channel req :downstream {:status 502 :body "Not reachable"} e)))))
