# streaming-proxy

A [few lines of code](src/streaming_proxy/core.clj) that show how you
to create a streaming proxy using [http-kit](http://www.http-kit.org/)
and [clj-http](https://github.com/dakrone/clj-http).

## Why?

If you want to create a proxy server that let's you use Clojure to
write custom rules for transforming requests or responses, then this
could be useful to you. Also check out
[aleph](https://github.com/ztellman/aleph).

http-kit can be used as a proxy out of the box, but it has to fully
receive responses from downstream servers before sending them to the
client. This can be a problem if you need large volumes of data to go
through the proxy. By using clj-http, you can send the downstream
server's response as it reaches the proxy server.

Using the two together proved a little tricky, however, with a bunch
of little tweaks to the requests and responses (and a fun excursion
into the land of byte buffers) so that http-kit and clj-http would
play nicely together. This code takes care of all that for you.

## Usage

`streaming-proxy.core/proxy-handler` is a ring-compatible request
handler that takes care of sending a request using clj-http and
handing the response back to http-kit. It expects a value for
`:streaming-proxy-url` in the ring request; therefore, you'll need to
create middleware that adds `:streaming-proxy-url` to the ring
request. Below is code that will create an example application. The
code was taken from
[the Midje tests](test/streaming_proxy/core_test.clj), and you might
find it useful to make your way through those.

```clojure
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

(def app (proxy-app 9001))

(org.httpkit.server/run-server app {:port 9000})
```

The proxy server is running on http://localhost:9000. Visit that, and
the proxy will forward requests to http://localhost:9001. The function
`proxy-response-handler` modifies downstream responses by prepending
`"Downstream error:"` to a response's body if the status is 400 or
greater. The proxy handler knows about `proxy-response-handler` thanks
to `proxy-wrapper`, which sets `:streaming-response-handler` and
`:streaming-proxy-url` in the Ring request map. `proxy-wrapper` also
assocs in the `:timeout` key, and this is used by `clj-http`. The ring
request map is passed to `clj-http` with only a few modifications; you
can control `clj-http` by modifying the request map.

## TODO

* Address the fact that `streaming-proxy.core/byte-array-size` is a
  magic number. Not sure how to handle it, though.

## License

Copyright © 2015 Daniel Higginbothama

Licensed under [the MIT license](http://opensource.org/licenses/MIT)
