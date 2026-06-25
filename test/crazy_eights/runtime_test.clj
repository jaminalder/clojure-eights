(ns crazy_eights.runtime-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [crazy_eights.app.core :as app]
            [crazy_eights.runtime :as runtime]
            [crazy_eights.web.server :as server]))

(defn clean-runtime [test-fn]
  (runtime/stop!)
  (runtime/reset-store!)
  (test-fn)
  (runtime/stop!)
  (runtime/reset-store!))

(use-fixtures :each clean-runtime)

(deftest runtime-store-is-the-shared-app-store
  (let [{:keys [game-id]} (app/create-game! runtime/store)]
    (is (= game-id (:game-id (app/get-game runtime/store game-id))))
    (is (= [game-id] (keys (:games @runtime/store))))))

(deftest normal-web-start-does-not-start-nrepl
  (let [result (server/start! {:port 0})
        status (runtime/status)]
    (is (= :started (:status result)))
    (is (number? (:port result)))
    (is (:web status))
    (is (nil? (:nrepl status)))))

(deftest operator-repl-starts-nrepl-without-web
  (let [port-file (str (java.io.File/createTempFile "ce-nrepl" ".port"))
        result (runtime/start-operator-repl! {:nrepl {:port 0
                                                      :port-file port-file}})
        status (runtime/status)]
    (is (= :started (get-in result [:nrepl :status])))
    (is (number? (get-in result [:nrepl :port])))
    (is (= port-file (get-in result [:nrepl :port-file])))
    (is (nil? (:web status)))
    (is (:nrepl status))))
