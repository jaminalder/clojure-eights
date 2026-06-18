(ns crazy_eights.web.fragments-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [crazy_eights.app.core :as app]
            [crazy_eights.web.fragments :as fragments]))

(deftest game-fragments-renders-the-three-swap-targets
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        {:keys [player-id]} (app/join-game! store game-id "ben")
        frags (fragments/game-fragments store game-id player-id)]
    (is (= #{:status :game-board :player-hand} (set (keys frags))))
    (doseq [html (vals frags)]
      (is (string? html))
      (is (not (str/includes? html "\n")) "fragments are single-line for SSE"))))
