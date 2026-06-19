(ns crazy_eights.web.routes-test
  (:require [clojure.test :refer [deftest is testing]]
            [crazy_eights.web.routes :as routes]))

(defn- response-for [method uri]
  ((routes/app {}) {:request-method method :uri uri}))

(deftest simulation-observer-routes-are-not-served
  (testing "observer page"
    (is (= 404 (:status (response-for :get "/observer")))))
  (testing "simulation start endpoint"
    (is (= 404 (:status (response-for :post "/simulations")))))
  (testing "simulation event stream endpoint"
    (is (= 404 (:status (response-for :get "/simulations/sim-1/events"))))))

(deftest unknown-route-returns-404
  (is (= 404 (:status (response-for :get "/favicon.ico")))))
