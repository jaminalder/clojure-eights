(ns crazy_eights.runtime
  (:require [clojure.java.io :as io]
            [crazy_eights.app.core :as app]
            [crazy_eights.web.routes :as routes]
            [org.httpkit.server :as http]))

(defonce store (app/create-store))

(defonce services
  (atom {:web nil
         :nrepl nil}))

(defn reset-store! []
  (reset! store @(app/create-store))
  store)

(defn status []
  @services)

(defn- web-port [server]
  (some-> server meta :local-address .getPort))

(defn start-web!
  ([] (start-web! {}))
  ([{:keys [port] :or {port 8080}}]
   (if-let [running (:web @services)]
     {:status :already-running
      :port (:port running)}
     (let [handler (routes/app {:store store})
           stop-server (http/run-server handler {:port port})
           actual-port (or (web-port stop-server) port)]
       (swap! services assoc :web {:stop stop-server
                                   :port actual-port})
       {:status :started
        :port actual-port}))))

(defn stop-web! []
  (when-let [{:keys [stop]} (:web @services)]
    (stop)
    (swap! services assoc :web nil)
    {:status :stopped}))

(defn- resolve-required [sym]
  (or (requiring-resolve sym)
      (throw (ex-info "required namespace is not on the classpath"
                      {:symbol sym}))))

(defn- resolve-optional [sym]
  (try
    (requiring-resolve sym)
    (catch java.io.FileNotFoundException _
      nil)))

(defn- nrepl-options [bind port]
  (cond-> [:bind bind :port port]
    (resolve-optional 'cider.nrepl/cider-nrepl-handler)
    (conj :handler @(resolve-optional 'cider.nrepl/cider-nrepl-handler))))

(defn start-nrepl!
  ([] (start-nrepl! {}))
  ([{:keys [bind port port-file]
     :or {bind "127.0.0.1"
          port 0
          port-file ".nrepl-port"}}]
   (if-let [running (:nrepl @services)]
     {:status :already-running
      :bind (:bind running)
      :port (:port running)
      :port-file (:port-file running)}
     (let [start-server (resolve-required 'nrepl.server/start-server)
           server (apply start-server (nrepl-options bind port))
           actual-port (:port server)]
       (spit port-file (str actual-port "\n"))
       (swap! services assoc :nrepl {:server server
                                     :bind bind
                                     :port actual-port
                                     :port-file port-file})
       {:status :started
        :bind bind
        :port actual-port
        :port-file port-file}))))

(defn stop-nrepl! []
  (when-let [{:keys [server port-file]} (:nrepl @services)]
    ((resolve-required 'nrepl.server/stop-server) server)
    (when port-file
      (io/delete-file port-file true))
    (swap! services assoc :nrepl nil)
    {:status :stopped}))

(defn start-operator!
  ([] (start-operator! {}))
  ([{:keys [web nrepl]}]
   {:web (start-web! web)
    :nrepl (start-nrepl! nrepl)}))

(defn start-operator-repl!
  ([] (start-operator-repl! {}))
  ([{:keys [nrepl]}]
   {:nrepl (start-nrepl! nrepl)}))

(defn stop! []
  {:nrepl (stop-nrepl!)
   :web (stop-web!)})

(defn repl-main []
  (let [{:keys [nrepl]} (start-operator-repl!)]
    (println "nREPL started on" (:bind nrepl) (:port nrepl))
    (println "nREPL port file:" (:port-file nrepl)))
  @(promise))

(defn operator-web-main []
  (let [{:keys [web nrepl]} (start-operator!)]
    (println "web server started on" (str "http://localhost:" (:port web)))
    (println "nREPL started on" (:bind nrepl) (:port nrepl))
    (println "nREPL port file:" (:port-file nrepl)))
  @(promise))

(defn -main [& args]
  (case (first args)
    "repl" (repl-main)
    (operator-web-main)))
