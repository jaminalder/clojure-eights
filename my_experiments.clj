(require '[crazy_eights.simulation.strategy :as strategy])
(require '[crazy_eights.simulation.experiment :as experiment])

(def my-strategy
  (strategy/from-rules
   :my-strategy
   [strategy/win-now
    strategy/avoid-eight
    strategy/declare-most-held-suit]))

(experiment/run
 {:games 1000
  :player-count 5
  :strategies [my-strategy
               strategy/careful]})

;; Rules
;; A rule is a pure scoring function:
;; (fn [observation candidate]
;;   score-number)
;;
;; Higher score wins.
;; observation is what the current player can know:
;;
;; {:player 0
;;  :hand [...]
;;  :top-card ...
;;  :active-suit :diamonds
;;  :draw-count 12
;;  :discard-count 4
;;  :other-card-counts [5 3]
;;  :passes-in-row 0
;;  :status :in-progress}
;;
;; candidate is a legal possible action with extra scoring data:
;;
;; {:type :play-card
;;  :card {:rank :nine :suit :clubs}
;;  :resulting-hand [...]
;;  :resulting-active-suit :clubs}
;;
;; Example custom rule:
;;
;; (defn prefer-hearts [_obs candidate]
;;   (if (= :hearts (:resulting-active-suit candidate))
;;     5
;;     0))
;;
;; Strategies
;; A strategy is:
;;
;; {:id :my-strategy
;;  :choose choose-fn}
;; Build one from rules:
;;
;; (def my-strategy
;;   (strategy/from-rules
;;    :my-strategy
;;    [strategy/win-now
;;     strategy/avoid-eight]))

;; Built-ins:

strategy/first-playable
strategy/careful

;; Run One Game

(require '[crazy_eights.app.core :as app])
(require '[crazy_eights.simulation.app :as sim])

(def store (app/create-store))

(def started
  (sim/start! store {:player-count 3
                     :simulation-name "test"}))

(sim/run-to-completion!
 store
 started
 {:delay-fn (fn [] nil)
  :strategies [strategy/careful
               strategy/first-playable
               my-strategy]})

;; Run Experiment

(experiment/run
 {:games 1000
  :player-count 5
  :strategies [strategy/careful
               strategy/first-playable
               my-strategy]})

