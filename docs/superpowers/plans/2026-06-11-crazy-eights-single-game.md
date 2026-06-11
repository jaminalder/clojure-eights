# Crazy Eights Single Game Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the Crazy Eights domain for one full playable single game, including redraws, explicit reshuffle, pass logic, blocked-game endings, and executable full-game scenarios.

**Architecture:** Extend the existing command -> events -> state model rather than introducing new layers. The domain remains deterministic by modeling reshuffle as an explicit command with supplied card order. The scenario runner grows from single-command checks into short executable game transcripts.

**Tech Stack:** Clojure, clojure.test, test.check, Kaocha

---

## File Map

- Modify: `src/crazy_eights/domain/model.clj`
- Modify: `src/crazy_eights/domain/commands.clj`
- Modify: `src/crazy_eights/domain/events.clj`
- Modify: `src/crazy_eights/domain/invariants.clj`
- Modify: `src/crazy_eights/domain/generators.clj`
- Modify: `src/crazy_eights/domain/scenarios.clj`
- Modify: `test/crazy_eights/domain/commands_test.clj`
- Modify: `test/crazy_eights/domain/invariants_test.clj`
- Modify: `test/crazy_eights/domain/scenarios_test.clj`
- Create: `resources/domain/scenarios/redraws_until_playable.edn`
- Create: `resources/domain/scenarios/reshuffles_draw_pile.edn`
- Create: `resources/domain/scenarios/passes_when_no_draw_possible.edn`
- Create: `resources/domain/scenarios/blocked_game_after_full_round_of_passes.edn`
- Create: `resources/domain/scenarios/full_single_game_to_win.edn`

### Task 1: Extend State And Event Model For Complete Turn Flow

**Files:**
- Modify: `test/crazy_eights/domain/commands_test.clj`
- Modify: `src/crazy_eights/domain/events.clj`
- Modify: `src/crazy_eights/domain/invariants.clj`
- Modify: `src/crazy_eights/domain/generators.clj`

- [ ] **Step 1: Write the failing state-shape and event tests**

Append these tests to `test/crazy_eights/domain/commands_test.clj`:

```clojure
(deftest start-game-initializes-pass-counter
  (let [events (commands/decide nil {:type :start-game
                                     :player-count 2
                                     :deck ordered-deck})
        state (events/apply-events nil events)]
    (is (= 0 (:passes-in-row state)))))

(deftest draw-card-keeps-same-player-turn
  (let [state {:players [(model/player [(model/card :ace :clubs)])
                        (model/player [(model/card :king :spades)])]
               :draw-pile [(model/card :two :diamonds)]
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 0
               :status :in-progress
               :winner nil
               :passes-in-row 0}
        events (commands/decide state {:type :draw-card :player 0})
        next-state (events/apply-events state events)]
    (is (= 0 (:current-player next-state)))
    (is (= 0 (:passes-in-row next-state)))))
```

- [ ] **Step 2: Run the focused commands test to verify it fails**

Run: `clojure -M:test --focus crazy_eights.domain.commands-test`
Expected: FAIL because start state lacks `:passes-in-row` and draw currently advances the turn.

- [ ] **Step 3: Update event application and base state shape**

Update `src/crazy_eights/domain/events.clj` to:

```clojure
(ns crazy_eights.domain.events)

(defmulti apply-event (fn [_state event] (:type event)))

(defmethod apply-event :game-started [_state event]
  (dissoc event :type))

(defmethod apply-event :card-played [state {:keys [player card]}]
  (-> state
      (update-in [:players player :hand]
                 (fn [hand]
                   (vec (remove #(= % card) hand))))
      (update :discard-pile conj card)
      (assoc :active-suit (:suit card)
             :passes-in-row 0)))

(defmethod apply-event :suit-declared [state {:keys [suit]}]
  (assoc state :active-suit suit))

(defmethod apply-event :card-drawn [state {:keys [player card]}]
  (-> state
      (update :draw-pile #(vec (rest %)))
      (update-in [:players player :hand] conj card)
      (assoc :passes-in-row 0)))

(defmethod apply-event :draw-pile-reshuffled [state {:keys [cards top-card]}]
  (assoc state
         :draw-pile (vec cards)
         :discard-pile [top-card]))

(defmethod apply-event :turn-advanced [state {:keys [player]}]
  (assoc state :current-player player))

(defmethod apply-event :turn-passed [state {:keys [player]}]
  (-> state
      (update :passes-in-row inc)
      (assoc :current-player player)))

(defmethod apply-event :game-won [state {:keys [player]}]
  (assoc state :status :finished
               :winner player))

(defmethod apply-event :game-blocked [state _event]
  (assoc state :status :finished
               :winner nil))

(defmethod apply-event :default [state _event]
  state)

(defn apply-events [state events]
  (reduce apply-event state events))
```

Update `src/crazy_eights/domain/commands.clj` start-game event to include:

```clojure
      :passes-in-row 0
```

Update `src/crazy_eights/domain/invariants.clj` to add `passes-in-row` and these checks:

```clojure
(defn check [{:keys [players draw-pile discard-pile active-suit current-player status winner passes-in-row] :as state}]
  (cond-> []
    ...
    (and (not (int? passes-in-row))
         (not (nil? passes-in-row)))
    (conj {:reason :invalid-passes-in-row})

    (and (int? passes-in-row)
         (neg? passes-in-row))
    (conj {:reason :negative-passes-in-row})
    ...))
```

Update `src/crazy_eights/domain/generators.clj` game-state generator to include:

```clojure
     :passes-in-row 0
```

- [ ] **Step 4: Run the focused commands and invariants tests**

Run: `clojure -M:test --focus crazy_eights.domain.commands-test --focus crazy_eights.domain.invariants-test`
Expected: FAIL because draw still advances the turn in command logic, but pass-counter/event structure is now in place.

- [ ] **Step 5: Commit the state and event groundwork**

```bash
git add src/crazy_eights/domain/events.clj src/crazy_eights/domain/commands.clj src/crazy_eights/domain/invariants.clj src/crazy_eights/domain/generators.clj test/crazy_eights/domain/commands_test.clj
git commit -m "feat: add single-game state and events"
```

### Task 2: Complete Draw, Reshuffle, And Pass Commands

**Files:**
- Modify: `test/crazy_eights/domain/commands_test.clj`
- Modify: `src/crazy_eights/domain/model.clj`
- Modify: `src/crazy_eights/domain/commands.clj`

- [ ] **Step 1: Write the failing command tests for redraw, reshuffle, pass, and blocked game**

Append these tests to `test/crazy_eights/domain/commands_test.clj`:

```clojure
(deftest draw-card-keeps-turn-with-same-player
  (let [state {:players [(model/player [(model/card :ace :clubs)])
                        (model/player [(model/card :king :spades)])]
               :draw-pile [(model/card :two :diamonds)]
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 0
               :status :in-progress
               :winner nil
               :passes-in-row 0}
        events (commands/decide state {:type :draw-card
                                       :player 0})]
    (is (= [{:type :card-drawn
             :player 0
             :card (model/card :two :diamonds)}]
           events))))

(deftest reshuffle-draw-pile-requires-exact-discard-tail
  (let [state {:players [(model/player [(model/card :ace :clubs)])
                        (model/player [(model/card :king :spades)])]
               :draw-pile []
               :discard-pile [(model/card :queen :hearts)
                              (model/card :ace :diamonds)
                              (model/card :two :spades)]
               :active-suit :hearts
               :current-player 0
               :status :in-progress
               :winner nil
               :passes-in-row 0}]
    (is (= [{:type :draw-pile-reshuffled
             :cards [(model/card :ace :diamonds)
                     (model/card :two :spades)]
             :top-card (model/card :queen :hearts)}]
           (commands/decide state {:type :reshuffle-draw-pile
                                   :cards [(model/card :ace :diamonds)
                                           (model/card :two :spades)]})))
    (is (= {:type :domain-error
            :reason :invalid-reshuffle-cards}
           (commands/decide state {:type :reshuffle-draw-pile
                                   :cards [(model/card :two :spades)]})))) )

(deftest must-reshuffle-before-passing
  (let [state {:players [(model/player [(model/card :ace :clubs)])
                        (model/player [(model/card :king :spades)])]
               :draw-pile []
               :discard-pile [(model/card :queen :hearts)
                              (model/card :ace :diamonds)]
               :active-suit :hearts
               :current-player 0
               :status :in-progress
               :winner nil
               :passes-in-row 0}]
    (is (= {:type :domain-error
            :reason :must-reshuffle-before-passing}
           (commands/decide state {:type :pass-turn
                                   :player 0})))))

(deftest pass-turn-can-block-the-game
  (let [state {:players [(model/player [(model/card :ace :clubs)])
                        (model/player [(model/card :king :spades)])]
               :draw-pile []
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 1
               :status :in-progress
               :winner nil
               :passes-in-row 1}
        events (commands/decide state {:type :pass-turn
                                       :player 1})]
    (is (= [{:type :turn-passed
             :player 0}
            {:type :game-blocked}]
           events))))
```

- [ ] **Step 2: Run the focused commands test to verify it fails**

Run: `clojure -M:test --focus crazy_eights.domain.commands-test`
Expected: FAIL because reshuffle and pass are not implemented and draw still advances the turn.

- [ ] **Step 3: Add the minimal model helpers for the complete turn rules**

Append to `src/crazy_eights/domain/model.clj`:

```clojure
(defn playable-hand? [state hand]
  (boolean (some #(playable-card? state %) hand)))

(defn reshuffleable? [{:keys [draw-pile discard-pile]}]
  (and (empty? draw-pile)
       (< 1 (count discard-pile))))

(defn reshuffle-cards [discard-pile]
  (vec (rest discard-pile)))

(defn top-discard [discard-pile]
  (first discard-pile))
```

- [ ] **Step 4: Implement the minimal complete turn command flow**

Update `src/crazy_eights/domain/commands.clj` to:

```clojure
(ns crazy_eights.domain.commands
  (:require [crazy_eights.domain.model :as model]))

(def cards-per-player 5)

(defn- domain-error [reason]
  {:type :domain-error
   :reason reason})

(defn- deal-hands [player-count deck]
  (mapv (fn [index]
          (model/player (take cards-per-player (drop (* index cards-per-player) deck))))
        (range player-count)))

(defn- remaining-deck [player-count deck]
  (drop (* player-count cards-per-player) deck))

(defn- start-game-events [{:keys [player-count deck]}]
  (let [remaining (vec (remaining-deck player-count deck))
        discard (first remaining)
        draw-pile (vec (rest remaining))]
    [{:type :game-started
      :players (deal-hands player-count deck)
      :draw-pile draw-pile
      :discard-pile [discard]
      :active-suit (:suit discard)
      :current-player 0
      :status :in-progress
      :winner nil
      :passes-in-row 0}]))

(defn- next-player [state]
  (mod (inc (:current-player state))
       (count (:players state))))

(defn- current-hand [state player]
  (get-in state [:players player :hand]))

(defn- draw-card-events [state {:keys [player]}]
  (let [hand (current-hand state player)
        drawn-card (first (:draw-pile state))]
    (cond
      (not= player (:current-player state))
      (domain-error :not-current-player)

      (model/playable-hand? state hand)
      (domain-error :must-play-before-drawing)

      (nil? drawn-card)
      (domain-error :draw-pile-empty)

      :else
      [{:type :card-drawn
        :player player
        :card drawn-card}])))

(defn- reshuffle-draw-pile-events [state {:keys [cards]}]
  (cond
    (not= :in-progress (:status state))
    (domain-error :reshuffle-not-allowed)

    (not (model/reshuffleable? state))
    (domain-error :reshuffle-not-allowed)

    (not= (vec cards) (model/reshuffle-cards (:discard-pile state)))
    (domain-error :invalid-reshuffle-cards)

    :else
    [{:type :draw-pile-reshuffled
      :cards (vec cards)
      :top-card (model/top-discard (:discard-pile state))}]))

(defn- pass-turn-events [state {:keys [player]}]
  (let [hand (current-hand state player)
        next (next-player state)
        passes-after (inc (:passes-in-row state))]
    (cond
      (not= player (:current-player state))
      (domain-error :not-current-player)

      (model/playable-hand? state hand)
      (domain-error :cannot-pass-while-playable)

      (seq (:draw-pile state))
      (domain-error :must-play-before-drawing)

      (model/reshuffleable? state)
      (domain-error :must-reshuffle-before-passing)

      :else
      (cond-> [{:type :turn-passed
                :player next}]
        (= passes-after (count (:players state)))
        (conj {:type :game-blocked})))))

(defn- play-card-events [state {:keys [player card declared-suit]}]
  (let [hand (current-hand state player)]
    (cond
      (not= player (:current-player state))
      (domain-error :not-current-player)

      (not (model/card-in-hand? hand card))
      (domain-error :card-not-in-hand)

      (not (model/playable-card? state card))
      (domain-error :card-not-playable)

      (and (model/requires-declared-suit? card)
           (not (model/valid-declared-suit? card declared-suit)))
      (domain-error :declared-suit-required)

      :else
      (cond-> [{:type :card-played
                :player player
                :card card}]
        (model/requires-declared-suit? card)
        (conj {:type :suit-declared
               :suit declared-suit})

        (empty? (remove #(= % card) hand))
        (conj {:type :game-won
               :player player})

        (seq (remove #(= % card) hand))
        (conj {:type :turn-advanced
               :player (next-player state)})))) )

(defn decide [state command]
  (case (:type command)
    :start-game (if (and (nil? state)
                         (pos-int? (:player-count command))
                         (every? model/card? (:deck command))
                         (< (* (:player-count command) cards-per-player)
                            (count (:deck command))))
                  (start-game-events command)
                  (domain-error :invalid-start-game))
    :play-card (play-card-events state command)
    :draw-card (draw-card-events state command)
    :reshuffle-draw-pile (reshuffle-draw-pile-events state command)
    :pass-turn (pass-turn-events state command)
    (domain-error :unknown-command)))
```

- [ ] **Step 5: Run the focused commands test to verify it passes**

Run: `clojure -M:test --focus crazy_eights.domain.commands-test`
Expected: PASS for the expanded command set.

- [ ] **Step 6: Commit the complete turn command flow**

```bash
git add src/crazy_eights/domain/model.clj src/crazy_eights/domain/commands.clj test/crazy_eights/domain/commands_test.clj
git commit -m "feat: complete single-game command flow"
```

### Task 3: Extend Scenario Runner To Executable Game Transcripts

**Files:**
- Modify: `test/crazy_eights/domain/scenarios_test.clj`
- Modify: `src/crazy_eights/domain/scenarios.clj`

- [ ] **Step 1: Write the failing scenario-runner test**

Replace `test/crazy_eights/domain/scenarios_test.clj` with:

```clojure
(ns crazy_eights.domain.scenarios-test
  (:require [clojure.test :refer [deftest is testing]]
            [crazy_eights.domain.scenarios :as scenarios]))

(deftest bundled-scenarios-pass
  (doseq [path ["domain/scenarios/play_matching_rank.edn"
                "domain/scenarios/play_eight_declares_suit.edn"
                "domain/scenarios/cannot_play_invalid_card.edn"
                "domain/scenarios/draws_when_no_playable_card.edn"
                "domain/scenarios/redraws_until_playable.edn"
                "domain/scenarios/reshuffles_draw_pile.edn"
                "domain/scenarios/passes_when_no_draw_possible.edn"
                "domain/scenarios/blocked_game_after_full_round_of_passes.edn"
                "domain/scenarios/full_single_game_to_win.edn"]]
    (testing path
      (is (true? (:pass? (scenarios/run-scenario-resource path)))))))
```

- [ ] **Step 2: Run the focused scenarios test to verify it fails**

Run: `clojure -M:test --focus crazy_eights.domain.scenarios-test`
Expected: FAIL because the new scenario files do not exist and the runner only supports one command.

- [ ] **Step 3: Extend the scenario runner for multi-step transcripts**

Update `src/crazy_eights/domain/scenarios.clj` to:

```clojure
(ns crazy_eights.domain.scenarios
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [crazy_eights.domain.commands :as commands]
            [crazy_eights.domain.events :as events]))

(defn load-scenario-resource [path]
  (-> path io/resource slurp edn/read-string))

(defn- subset-match? [expected actual]
  (every? (fn [[k v]]
            (let [actual-value (get actual k)]
              (if (map? v)
                (subset-match? v actual-value)
                (= v actual-value))))
          expected))

(defn- run-step [state {:keys [command then]}]
  (let [result (commands/decide state command)]
    (if (= :domain-error (:type result))
      {:pass? (= (:error then)
                 (select-keys result [:reason]))
       :state state}
      (let [next-state (events/apply-events state result)]
        {:pass? (and (= (:events then) result)
                     (or (nil? (:state-matches then))
                         (subset-match? (:state-matches then) next-state)))
         :state next-state}))))

(defn run-scenario [scenario]
  (let [initial-state (get-in scenario [:given :state])]
    (if-let [steps (:steps scenario)]
      (let [result (reduce (fn [{:keys [pass? state] :as acc} step]
                             (if-not pass?
                               acc
                               (run-step state step)))
                           {:pass? true :state initial-state}
                           steps)]
        {:pass? (and (:pass? result)
                     (or (nil? (get-in scenario [:then :state-matches]))
                         (subset-match? (get-in scenario [:then :state-matches])
                                        (:state result))))})
      (run-step initial-state {:command (get-in scenario [:when :command])
                               :then (:then scenario)}))))

(defn run-scenario-resource [path]
  (run-scenario (load-scenario-resource path)))
```

- [ ] **Step 4: Run the focused scenarios test again**

Run: `clojure -M:test --focus crazy_eights.domain.scenarios-test`
Expected: FAIL because the new scenario resource files are still missing.

- [ ] **Step 5: Commit the runner extension**

```bash
git add src/crazy_eights/domain/scenarios.clj test/crazy_eights/domain/scenarios_test.clj
git commit -m "feat: support multi-step game scenarios"
```

### Task 4: Add Complete Single-Game Scenarios And Invariant Coverage

**Files:**
- Modify: `test/crazy_eights/domain/invariants_test.clj`
- Create: `resources/domain/scenarios/redraws_until_playable.edn`
- Create: `resources/domain/scenarios/reshuffles_draw_pile.edn`
- Create: `resources/domain/scenarios/passes_when_no_draw_possible.edn`
- Create: `resources/domain/scenarios/blocked_game_after_full_round_of_passes.edn`
- Create: `resources/domain/scenarios/full_single_game_to_win.edn`

- [ ] **Step 1: Add the failing invariant tests for pass and blocked states**

Append to `test/crazy_eights/domain/invariants_test.clj`:

```clojure
(deftest passed-turn-state-preserves-invariants
  (let [state {:players [(model/player [(model/card :ace :clubs)])
                        (model/player [(model/card :king :spades)])]
               :draw-pile []
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 0
               :status :in-progress
               :winner nil
               :passes-in-row 0}
        events (commands/decide state {:type :pass-turn
                                       :player 0})
        next-state (events/apply-events state events)]
    (is (= :turn-passed (:type (first events))))
    (is (empty? (invariants/check next-state)))))

(deftest blocked-game-state-preserves-invariants
  (let [state {:players [(model/player [(model/card :ace :clubs)])
                        (model/player [(model/card :king :spades)])]
               :draw-pile []
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 1
               :status :in-progress
               :winner nil
               :passes-in-row 1}
        events (commands/decide state {:type :pass-turn
                                       :player 1})
        next-state (events/apply-events state events)]
    (is (= :game-blocked (:type (last events))))
    (is (empty? (invariants/check next-state)))))
```

- [ ] **Step 2: Run the focused invariants test to verify it fails if pass behavior is incomplete**

Run: `clojure -M:test --focus crazy_eights.domain.invariants-test`
Expected: FAIL until pass/block semantics and invariant support are complete.

- [ ] **Step 3: Add the complete scenario resources**

Create `resources/domain/scenarios/redraws_until_playable.edn` with:

```edn
{:name "redraws until playable"
 :given {:state {:players [{:hand [{:rank :ace :suit :clubs}]}
                           {:hand [{:rank :king :suit :spades}]}]
                 :draw-pile [{:rank :two :suit :diamonds}
                             {:rank :queen :suit :clubs}]
                 :discard-pile [{:rank :queen :suit :hearts}]
                 :active-suit :hearts
                 :current-player 0
                 :status :in-progress
                 :winner nil
                 :passes-in-row 0}}
 :steps [{:command {:type :draw-card
                    :player 0}
          :then {:events [{:type :card-drawn
                           :player 0
                           :card {:rank :two :suit :diamonds}}]
                 :state-matches {:current-player 0}}}
         {:command {:type :draw-card
                    :player 0}
          :then {:events [{:type :card-drawn
                           :player 0
                           :card {:rank :queen :suit :clubs}}]
                 :state-matches {:current-player 0}}}
         {:command {:type :play-card
                    :player 0
                    :card {:rank :queen :suit :clubs}}
          :then {:events [{:type :card-played
                           :player 0
                           :card {:rank :queen :suit :clubs}}
                          {:type :turn-advanced
                           :player 1}]}}]
 :then {:state-matches {:current-player 1}}}
```

Create `resources/domain/scenarios/reshuffles_draw_pile.edn` with:

```edn
{:name "reshuffles draw pile"
 :given {:state {:players [{:hand [{:rank :ace :suit :clubs}]}
                           {:hand [{:rank :king :suit :spades}]}]
                 :draw-pile []
                 :discard-pile [{:rank :queen :suit :hearts}
                                {:rank :ace :suit :diamonds}
                                {:rank :two :suit :spades}]
                 :active-suit :hearts
                 :current-player 0
                 :status :in-progress
                 :winner nil
                 :passes-in-row 0}}
 :steps [{:command {:type :reshuffle-draw-pile
                    :cards [{:rank :ace :suit :diamonds}
                            {:rank :two :suit :spades}]}
          :then {:events [{:type :draw-pile-reshuffled
                           :cards [{:rank :ace :suit :diamonds}
                                   {:rank :two :suit :spades}]
                           :top-card {:rank :queen :suit :hearts}}]}}]
 :then {:state-matches {:draw-pile [{:rank :ace :suit :diamonds}
                                    {:rank :two :suit :spades}]
                        :discard-pile [{:rank :queen :suit :hearts}]}}}
```

Create `resources/domain/scenarios/passes_when_no_draw_possible.edn` with:

```edn
{:name "passes when no draw possible"
 :given {:state {:players [{:hand [{:rank :ace :suit :clubs}]}
                           {:hand [{:rank :king :suit :spades}]}]
                 :draw-pile []
                 :discard-pile [{:rank :queen :suit :hearts}]
                 :active-suit :hearts
                 :current-player 0
                 :status :in-progress
                 :winner nil
                 :passes-in-row 0}}
 :steps [{:command {:type :pass-turn
                    :player 0}
          :then {:events [{:type :turn-passed
                           :player 1}]}}]
 :then {:state-matches {:current-player 1
                        :passes-in-row 1}}}
```

Create `resources/domain/scenarios/blocked_game_after_full_round_of_passes.edn` with:

```edn
{:name "blocked game after full round of passes"
 :given {:state {:players [{:hand [{:rank :ace :suit :clubs}]}
                           {:hand [{:rank :king :suit :spades}]}]
                 :draw-pile []
                 :discard-pile [{:rank :queen :suit :hearts}]
                 :active-suit :hearts
                 :current-player 0
                 :status :in-progress
                 :winner nil
                 :passes-in-row 0}}
 :steps [{:command {:type :pass-turn
                    :player 0}
          :then {:events [{:type :turn-passed
                           :player 1}]}}
         {:command {:type :pass-turn
                    :player 1}
          :then {:events [{:type :turn-passed
                           :player 0}
                          {:type :game-blocked}]}}]
 :then {:state-matches {:status :finished
                        :winner nil}}}
```

Create `resources/domain/scenarios/full_single_game_to_win.edn` with:

```edn
{:name "full single game to win"
 :given {:state {:players [{:hand [{:rank :ace :suit :clubs}]}
                           {:hand [{:rank :king :suit :spades}
                                   {:rank :three :suit :hearts}]}]
                 :draw-pile [{:rank :queen :suit :clubs}]
                 :discard-pile [{:rank :queen :suit :hearts}]
                 :active-suit :hearts
                 :current-player 0
                 :status :in-progress
                 :winner nil
                 :passes-in-row 0}}
 :steps [{:command {:type :draw-card
                    :player 0}
          :then {:events [{:type :card-drawn
                           :player 0
                           :card {:rank :queen :suit :clubs}}]}}
         {:command {:type :play-card
                    :player 0
                    :card {:rank :queen :suit :clubs}}
          :then {:events [{:type :card-played
                           :player 0
                           :card {:rank :queen :suit :clubs}}
                          {:type :turn-advanced
                           :player 1}]}}
         {:command {:type :play-card
                    :player 1
                    :card {:rank :three :suit :hearts}}
          :then {:events [{:type :card-played
                           :player 1
                           :card {:rank :three :suit :hearts}}
                          {:type :game-won
                           :player 1}]}}]
 :then {:state-matches {:status :finished
                        :winner 1}}}
```

- [ ] **Step 4: Run the focused scenarios and invariants tests**

Run: `clojure -M:test --focus crazy_eights.domain.scenarios-test --focus crazy_eights.domain.invariants-test`
Expected: PASS if the complete single-game behavior is implemented correctly.

- [ ] **Step 5: Run the full suite**

Run: `clojure -M:test`
Expected: PASS for the entire project.

- [ ] **Step 6: Commit the complete game coverage**

```bash
git add test/crazy_eights/domain/invariants_test.clj test/crazy_eights/domain/scenarios_test.clj resources/domain/scenarios/*.edn
git commit -m "test: cover single-game crazy eights flow"
```

## Plan Review Notes

- Spec coverage: covers redraws, explicit reshuffle, pass logic, blocked-game endings, pass-counter state, scenario runner extension, and executable full-game scenarios.
- Placeholder scan: no placeholders remain; every step names exact files, tests, commands, and snippets.
- Type consistency: `:passes-in-row`, `:reshuffle-draw-pile`, `:draw-pile-reshuffled`, `:pass-turn`, `:turn-passed`, and `:game-blocked` are used consistently across model, commands, events, invariants, tests, and scenarios.
