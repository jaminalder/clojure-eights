(ns crazy_eights.web.views-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [crazy_eights.domain.model :as model]
            [crazy_eights.web.view_model :as vm]
            [crazy_eights.web.views :as views]))

(def waiting-game
  {:game-id "game-0"
   :state nil
   :players {"game-0-player-0" {:seat 0 :name "anna"}
             "game-0-player-1" {:seat 1 :name "ben"}}})

(def playing-state
  {:players [{:hand [(model/card :queen :clubs) (model/card :eight :diamonds)]}
             {:hand [(model/card :two :hearts)]}]
   :draw-pile [(model/card :four :clubs)]
   :discard-pile [(model/card :queen :spades)]
   :active-suit :spades
   :current-player 0
   :status :in-progress
   :winner nil
   :passes-in-row 0})

(def playing-game (assoc waiting-game :state playing-state))

(def between-games (assoc waiting-game :started-once? true))

(defn- host-view [] (vm/game-view playing-game "game-0-player-0"))

(deftest fragments-are-single-line
  (doseq [html [(views/status-html (host-view))
                (views/board-html (host-view))
                (views/hand-html (host-view))]]
    (is (not (str/includes? html "\n"))
        "SSE data frames must not contain newlines")))

(deftest start-page-has-create-form
  (let [html (views/start-page)]
    (is (str/includes? html "action=\"/games\""))
    (is (str/includes? html "method=\"post\""))
    (is (str/includes? html "name=\"name\""))))

(deftest game-page-wires-sse-and-fragments
  (let [html (views/game-page (host-view))]
    (is (str/includes? html "hx-ext=\"sse\""))
    (is (str/includes? html "sse-connect=\"/games/game-0/events\""))
    (is (str/includes? html "sse-close=\"table-ended\""))
    (doseq [target ["status" "game-board" "player-hand"]]
      (is (str/includes? html (str "sse-swap=\"" target "\"")))
      (is (str/includes? html (str "id=\"" target "\""))))
    (is (str/includes? html "sse-swap=\"table-ended\""))
    (is (str/includes? html "/assets/vendor/htmx.min.js"))))

(deftest observer-page-wires-sse-and-open-table-fragment
  (let [view (vm/observer-view playing-game)
        html (views/observer-page view "observer-token")]
    (is (str/includes? html "hx-ext=\"sse\""))
    (is (str/includes? html "sse-connect=\"/games/game-0/observer/observer-token/events\""))
    (is (str/includes? html "sse-swap=\"observer-table\""))
    (is (str/includes? html "id=\"observer-table\""))
    (is (str/includes? html "/assets/cards/QC.svg"))
    (is (str/includes? html "/assets/cards/8D.svg"))
    (is (str/includes? html "/assets/cards/2H.svg"))))

(deftest waiting-board-shows-players-and-host-start
  (let [host-html (views/board-html (vm/game-view waiting-game "game-0-player-0"))
        guest-html (views/board-html (vm/game-view waiting-game "game-0-player-1"))]
    (is (str/includes? host-html "anna"))
    (is (str/includes? host-html "ben"))
    (is (str/includes? host-html "hx-post=\"/games/game-0/start\""))
    (is (str/includes? host-html "action=\"/games/game-0/leave\""))
    (is (not (str/includes? guest-html "hx-post=\"/games/game-0/start\"")))))

(deftest between-games-board-shows-current-table-not-invite-flow
  (let [host-html (views/board-html (vm/game-view between-games "game-0-player-0"))
        guest-html (views/board-html (vm/game-view between-games "game-0-player-1"))
        stranger-hand (views/hand-html (vm/game-view between-games nil))]
    (is (str/includes? host-html "between games"))
    (is (str/includes? host-html "start new game"))
    (is (str/includes? host-html "action=\"/games/game-0/leave\""))
    (is (str/includes? guest-html "action=\"/games/game-0/leave\""))
    (is (not (str/includes? guest-html "hx-post=\"/games/game-0/start\"")))
    (is (not (str/includes? host-html "copy link")))
    (is (not (str/includes? stranger-hand "action=\"/games/game-0/join\"")))))

(deftest waiting-hand-shows-join-form-for-strangers
  (let [stranger (views/hand-html (vm/game-view waiting-game nil))
        joined (views/hand-html (vm/game-view waiting-game "game-0-player-1"))]
    (is (str/includes? stranger "action=\"/games/game-0/join\""))
    (is (not (str/includes? joined "join")))))

(deftest playing-board-shows-piles-and-opponents
  (let [html (views/board-html (host-view))]
    (is (str/includes? html "/assets/cards/QS.svg"))
    (is (str/includes? html "/assets/cards/BACK.svg"))
    (is (str/includes? html "♠"))
    (is (str/includes? html "ben"))
    (testing "opponent cards stay hidden"
      (is (not (str/includes? html "/assets/cards/2H.svg"))))))

(deftest observer-table-shows-piles-and-all-hands
  (let [html (views/observer-table-html (vm/observer-view playing-game))]
    (is (str/includes? html "/assets/cards/QS.svg"))
    (is (str/includes? html "/assets/cards/BACK.svg"))
    (is (str/includes? html "anna"))
    (is (str/includes? html "ben"))
    (is (str/includes? html "/assets/cards/QC.svg"))
    (is (str/includes? html "/assets/cards/8D.svg"))
    (is (str/includes? html "/assets/cards/2H.svg"))
    (is (not (str/includes? html "hx-post")))))

(deftest hand-renders-playable-cards-as-actions
  (let [html (views/hand-html (host-view))]
    (testing "plain playable card posts the play command"
      (is (str/includes? html "hx-post=\"/games/game-0/play?card=QC\"")))
    (testing "an eight opens the suit picker instead"
      (is (str/includes? html "hx-get=\"/games/game-0/hand?declare=8D\"")))))

(deftest suit-picker-for-an-eight
  (let [html (views/hand-html (host-view) {:declare-code "8D"})]
    (is (str/includes? html "suit-picker"))
    (doseq [suit ["clubs" "diamonds" "hearts" "spades"]]
      (is (str/includes? html (str "card=8D&amp;declared-suit=" suit))))
    (testing "cancel returns to the plain hand"
      (is (str/includes? html "hx-get=\"/games/game-0/hand\"")))))

(deftest opponent-turn-hand-has-no-actions
  (let [html (views/hand-html (vm/game-view playing-game "game-0-player-1"))]
    (is (str/includes? html "/assets/cards/2H.svg"))
    (is (not (str/includes? html "hx-post")))))

(deftest spectator-hand-is-a-note
  (let [html (views/hand-html (vm/game-view playing-game nil))]
    (is (str/includes? html "spectat"))
    (is (not (str/includes? html "hx-post")))))

(deftest status-texts
  (is (str/includes? (views/status-html (vm/game-view waiting-game nil)) "waiting"))
  (is (str/includes? (views/status-html (host-view)) "your turn"))
  (is (str/includes? (views/status-html (vm/game-view playing-game "game-0-player-1")) "anna"))
  (let [finished (assoc playing-game :state (assoc playing-state :status :finished :winner 1
                                                   :players [{:hand [(model/card :queen :clubs)]}
                                                             {:hand []}]))]
    (is (str/includes? (views/status-html (vm/game-view finished nil)) "ben"))
    (let [html (views/board-html (vm/game-view finished "game-0-player-0"))]
      (is (str/includes? html "start new game"))
      (is (str/includes? html "action=\"/games/game-0/leave\""))))
  (is (str/includes? (views/error-status-html :not-current-player) "not your turn")))
