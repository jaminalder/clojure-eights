(ns crazy_eights.web.paths-test
  (:require [clojure.test :refer [deftest is]]
            [crazy_eights.web.paths :as paths]))

(deftest builds-game-paths
  (is (= "/games/game-0" (paths/game "game-0")))
  (is (= "/games/game-0/join" (paths/join "game-0")))
  (is (= "/games/game-0/start" (paths/start "game-0")))
  (is (= "/games/game-0/play" (paths/play "game-0")))
  (is (= "/games/game-0/draw" (paths/draw "game-0")))
  (is (= "/games/game-0/pass" (paths/pass "game-0")))
  (is (= "/games/game-0/leave" (paths/leave "game-0")))
  (is (= "/games/game-0/hand" (paths/hand "game-0")))
  (is (= "/games/game-0/events" (paths/events "game-0")))
  (is (= "/games/game-0/observer/token" (paths/observer "game-0" "token")))
  (is (= "/games/game-0/observer/token/events"
         (paths/observer-events "game-0" "token"))))
