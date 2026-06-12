(ns crazy_eights.web.game_routes-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [crazy_eights.app.core :as app]
            [crazy_eights.domain.model :as model]
            [crazy_eights.web.routes :as routes]))

(defn- handler [store]
  (routes/app {:store store :simulation-service nil}))

(defn- request
  ([method uri] (request method uri nil nil))
  ([method uri params] (request method uri params nil))
  ([method uri params cookie]
   (cond-> {:request-method method :uri uri}
     params (assoc :params params)
     cookie (assoc :headers {"cookie" cookie}))))

(defn- set-cookie [response]
  (first (get-in response [:headers "Set-Cookie"])))

(def fixed-deck
  "Seat 0: QC 2C 6D KD AD / seat 1: 8D 2H AC KS JC / discard: QS."
  (vec (concat [(model/card :queen :clubs)
                (model/card :two :clubs)
                (model/card :six :diamonds)
                (model/card :king :diamonds)
                (model/card :ace :diamonds)
                (model/card :eight :diamonds)
                (model/card :two :hearts)
                (model/card :ace :clubs)
                (model/card :king :spades)
                (model/card :jack :clubs)
                (model/card :queen :spades)
                (model/card :four :clubs)]
               (drop 12 model/full-deck))))

(defn- started-game
  "Two joined players with the fixed deck already started. Returns [store game-id]."
  []
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        p1 (app/join-game! store game-id "anna")
        _p2 (app/join-game! store game-id "ben")]
    (app/start-game! store game-id (:player-id p1) {:deck fixed-deck})
    [store game-id]))

(deftest start-page-offers-game-creation
  (let [response ((handler (app/create-store)) (request :get "/"))]
    (is (= 200 (:status response)))
    (is (str/includes? (:body response) "action=\"/games\""))))

(deftest create-game-redirects-and-identifies-host
  (let [store (app/create-store)
        response ((handler store) (request :post "/games" {"name" "anna"}))]
    (is (= 303 (:status response)))
    (is (= "/games/game-0" (get-in response [:headers "Location"])))
    (is (str/starts-with? (set-cookie response) "ce-game-0=game-0-player-0"))
    (is (= "anna" (get-in (app/get-game store "game-0")
                          [:players "game-0-player-0" :name])))))

(deftest join-redirects-and-sets-cookie
  (let [store (app/create-store)
        h (handler store)
        _ (h (request :post "/games" {"name" "anna"}))
        response (h (request :post "/games/game-0/join" {"name" "ben"}))]
    (is (= 303 (:status response)))
    (is (= "/games/game-0" (get-in response [:headers "Location"])))
    (is (str/starts-with? (set-cookie response) "ce-game-0=game-0-player-1"))))

(deftest game-page-shows-waiting-room
  (let [store (app/create-store)
        h (handler store)
        _ (h (request :post "/games" {"name" "anna"}))
        page (h (request :get "/games/game-0" nil "ce-game-0=game-0-player-0"))]
    (is (= 200 (:status page)))
    (is (str/includes? (:body page) "anna"))
    (is (str/includes? (:body page) "sse-connect=\"/games/game-0/events\""))))

(deftest unknown-game-page-is-404
  (let [response ((handler (app/create-store)) (request :get "/games/missing"))]
    (is (= 404 (:status response)))))

(deftest only-host-can-start-via-route
  (let [store (app/create-store)
        h (handler store)
        _ (h (request :post "/games" {"name" "anna"}))
        _ (h (request :post "/games/game-0/join" {"name" "ben"}))
        guest (h (request :post "/games/game-0/start" nil "ce-game-0=game-0-player-1"))
        stranger (h (request :post "/games/game-0/start"))
        host (h (request :post "/games/game-0/start" nil "ce-game-0=game-0-player-0"))]
    (is (str/includes? (:body guest) "host"))
    (is (str/includes? (:body stranger) "not in this game"))
    (is (= 200 (:status host)))
    (is (= :in-progress (get-in (app/get-game store "game-0") [:state :status])))))

(deftest play-route-applies-the-move
  (let [[store game-id] (started-game)
        h (handler store)]
    (testing "out of turn is rejected with a status message"
      (let [response (h (request :post (str "/games/" game-id "/play")
                                 {"card" "KS"} "ce-game-0=game-0-player-1"))]
        (is (str/includes? (:body response) "not your turn"))
        (is (= :spades (get-in (app/get-game store game-id) [:state :active-suit])))))
    (testing "current player plays the queen of clubs"
      (let [response (h (request :post (str "/games/" game-id "/play")
                                 {"card" "QC"} "ce-game-0=game-0-player-0"))]
        (is (= 200 (:status response)))
        (is (= (model/card :queen :clubs)
               (model/top-discard (get-in (app/get-game store game-id)
                                          [:state :discard-pile]))))))
    (testing "the eight declares a new suit"
      (h (request :post (str "/games/" game-id "/play")
                  {"card" "8D" "declared-suit" "hearts"} "ce-game-0=game-0-player-1"))
      (is (= :hearts (get-in (app/get-game store game-id) [:state :active-suit]))))))

(deftest hand-fragment-serves-suit-picker
  (let [[store game-id] (started-game)
        h (handler store)]
    (h (request :post (str "/games/" game-id "/play") {"card" "QC"} "ce-game-0=game-0-player-0"))
    (let [picker (h (request :get (str "/games/" game-id "/hand")
                             {"declare" "8D"} "ce-game-0=game-0-player-1"))
          plain (h (request :get (str "/games/" game-id "/hand")
                            nil "ce-game-0=game-0-player-1"))]
      (is (str/includes? (:body picker) "suit-picker"))
      (is (not (str/includes? (:body plain) "suit-picker"))))))

(deftest draw-route-draws-into-hand
  (let [store (app/create-store)
        h (handler store)
        {:keys [game-id]} (app/create-game! store)
        _p1 (app/join-game! store game-id "anna")
        _p2 (app/join-game! store game-id "ben")
        state {:players [{:hand [(model/card :three :clubs)]}
                         {:hand [(model/card :five :spades)]}]
               :draw-pile [(model/card :king :hearts)]
               :discard-pile [(model/card :nine :diamonds)]
               :active-suit :diamonds
               :current-player 0
               :status :in-progress
               :winner nil
               :passes-in-row 0}]
    (swap! store assoc-in [:games game-id :state] state)
    (h (request :post (str "/games/" game-id "/draw") nil "ce-game-0=game-0-player-0"))
    (is (= 2 (count (get-in (app/get-game store game-id) [:state :players 0 :hand]))))))

(deftest pass-route-advances-the-turn
  (let [store (app/create-store)
        h (handler store)
        {:keys [game-id]} (app/create-game! store)
        _p1 (app/join-game! store game-id "anna")
        _p2 (app/join-game! store game-id "ben")
        state {:players [{:hand [(model/card :three :clubs)]}
                         {:hand [(model/card :five :spades)]}]
               :draw-pile []
               :discard-pile [(model/card :nine :diamonds)]
               :active-suit :diamonds
               :current-player 0
               :status :in-progress
               :winner nil
               :passes-in-row 0}]
    (swap! store assoc-in [:games game-id :state] state)
    (h (request :post (str "/games/" game-id "/pass") nil "ce-game-0=game-0-player-0"))
    (is (= 1 (get-in (app/get-game store game-id) [:state :current-player])))))

(deftest started-game-turns-visitors-into-spectators
  (let [[store game-id] (started-game)
        h (handler store)
        page (h (request :get (str "/games/" game-id)))
        join (h (request :post (str "/games/" game-id "/join") {"name" "late"}))]
    (is (str/includes? (:body page) "spectat"))
    (is (= 303 (:status join)))
    (is (= 2 (count (:players (app/get-game store game-id)))))))

(deftest winning-play-reports-the-winner
  (let [store (app/create-store)
        h (handler store)
        {:keys [game-id]} (app/create-game! store)
        _p1 (app/join-game! store game-id "anna")
        _p2 (app/join-game! store game-id "ben")
        state {:players [{:hand [(model/card :nine :hearts)]}
                         {:hand [(model/card :five :spades)]}]
               :draw-pile [(model/card :king :hearts)]
               :discard-pile [(model/card :nine :diamonds)]
               :active-suit :diamonds
               :current-player 0
               :status :in-progress
               :winner nil
               :passes-in-row 0}]
    (swap! store assoc-in [:games game-id :state] state)
    (let [response (h (request :post (str "/games/" game-id "/play")
                               {"card" "9H"} "ce-game-0=game-0-player-0"))]
      (is (str/includes? (:body response) "anna"))
      (is (= :finished (get-in (app/get-game store game-id) [:state :status]))))))

(deftest static-assets-are-served
  (let [h (handler (app/create-store))
        css (h (request :get "/assets/style.css"))
        card (h (request :get "/assets/cards/QS.svg"))]
    (is (= 200 (:status css)))
    (is (= 200 (:status card)))))
