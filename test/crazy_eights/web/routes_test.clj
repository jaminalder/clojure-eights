(ns crazy_eights.web.routes-test
  (:require [clojure.test :refer [deftest is]]
            [crazy_eights.web.routes :as routes]))

(deftest observer-page-renders-html
  (let [handler (routes/app {:simulation-service nil})
        response (handler {:request-method :get
                           :uri "/"})]
    (is (= 200 (:status response)))
    (is (= "text/html; charset=utf-8"
           (get-in response [:headers "Content-Type"])))
    (is (.contains (:body response) "start simulation"))))

(deftest start-simulation-validates-player-count
  (let [handler (routes/app {:simulation-service nil
                             :start! (fn [_] {:simulation-id "sim-1"})
                             :run-simulation! (fn [_] nil)
                             :sse-response (fn [_ _] {:status 200 :body "" :headers {}})})
        invalid (handler {:request-method :post
                          :uri "/simulations"
                          :params {"player-count" "1"}})
        valid (handler {:request-method :post
                        :uri "/simulations"
                        :params {"player-count" "4"}})]
    (is (= 400 (:status invalid)))
    (is (= 202 (:status valid)))))

(deftest sse-endpoint-delegates-to-service
  (let [called (atom nil)
        handler (routes/app {:simulation-service nil
                             :start! (fn [_] {:simulation-id "sim-1"})
                             :run-simulation! (fn [_] nil)
                             :sse-response (fn [simulation-id request]
                                             (reset! called [simulation-id request])
                                             {:status 200
                                              :headers {"Content-Type" "text/event-stream"}
                                              :body ""})})
        response (handler {:request-method :get
                           :uri "/simulations/sim-1/events"
                           :path-params {:id "sim-1"}})]
    (is (= "sim-1" (first @called)))
    (is (= 200 (:status response)))))
