(ns crazy_eights.app.simulation-test
  (:require [clojure.test :refer [deftest is]]
            [crazy_eights.app.core :as app]
            [crazy_eights.app.logging :as logging]
            [crazy_eights.app.simulation :as simulation]))

(def ^:dynamic *log-app-simulation* false)

(defn run-app-simulated-game [player-count]
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        players (vec (repeatedly player-count #(app/join-game! store game-id)))
        _start (app/submit-action! store game-id {:type :start-game
                                                  :player-count player-count
                                                  :deck (simulation/valid-start-deck player-count)})
        event-log (atom [])]
    (app/subscribe! store game-id :simulation #(swap! event-log conj %))
    (when *log-app-simulation*
      (app/subscribe! store game-id :stdout (logging/stdout-subscriber)))
    (loop [steps-left 500]
      (let [state (:state (app/get-game store game-id))]
        (if (or (= :finished (:status state)) (zero? steps-left))
          {:state state :events @event-log :steps-left steps-left :players players}
          (let [player-index (:current-player state)
                player-id (:player-id (nth players player-index))
                action (simulation/choose-player-action state)]
            (app/submit-player-action! store game-id player-id action)
            (recur (dec steps-left))))))))

(deftest app-layer-simulated-games-finish
  (doseq [player-count [2 3 4 5 6]]
    (let [{:keys [state events steps-left]} (run-app-simulated-game player-count)
          event-log (with-out-str (doseq [event events] (prn event)))]
      (is (= :finished (:status state))
          (str "app simulation did not finish for " player-count " players\n" event-log))
      (is (pos? steps-left)
          (str "app simulation exhausted step budget for " player-count " players\n" event-log)))))

(deftest valid-start-deck-rejects-invalid-player-count
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"invalid player-count"
                        (simulation/valid-start-deck 11))))

(deftest app-simulation-logger-prints-events
  (let [output (with-out-str
                 (let [store (app/create-store)
                       {:keys [game-id]} (app/create-game! store)]
                   (app/subscribe! store game-id :stdout (logging/stdout-subscriber))
                   (app/join-game! store game-id)
                   (app/join-game! store game-id)
                   (app/submit-action! store game-id {:type :start-game
                                                      :player-count 2
                                                      :deck (simulation/valid-start-deck 2)})))]
    (is (.contains output ":game-started"))
    (is (.contains output ":turn-changed"))))
