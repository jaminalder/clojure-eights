# Crazy Eights Draw Card Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a strict `:draw-card` domain slice that lets the current player draw one card only when they have no playable card and advances the turn immediately.

**Architecture:** Reuse the existing command -> events -> state flow. `commands.clj` decides legality, `events.clj` applies a new `:card-drawn` event, and tests plus one executable scenario define the behavior. The slice intentionally excludes reshuffle, repeated draws, immediate play after draw, and pass commands.

**Tech Stack:** Clojure, clojure.test, test.check, Kaocha

---

## File Map

- Modify: `src/crazy_eights/domain/commands.clj`
- Modify: `src/crazy_eights/domain/events.clj`
- Modify: `test/crazy_eights/domain/commands_test.clj`
- Modify: `test/crazy_eights/domain/invariants_test.clj`
- Modify: `test/crazy_eights/domain/scenarios_test.clj`
- Create: `resources/domain/scenarios/draws_when_no_playable_card.edn`

### Task 1: Add The Failing Draw Command Tests

**Files:**
- Modify: `test/crazy_eights/domain/commands_test.clj`

- [ ] **Step 1: Write the failing draw command tests**

Append these tests to `test/crazy_eights/domain/commands_test.clj`:

```clojure
(deftest draw-card-emits-draw-and-turn-events
  (let [state {:players [(model/player [(model/card :ace :clubs)])
                        (model/player [(model/card :king :spades)])]
               :draw-pile [(model/card :two :diamonds)
                           (model/card :three :diamonds)]
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 0
               :status :in-progress
               :winner nil}
        events (commands/decide state {:type :draw-card
                                       :player 0})]
    (is (= [{:type :card-drawn
             :player 0
             :card (model/card :two :diamonds)}
            {:type :turn-advanced
             :player 1}]
           events))))

(deftest must-play-before-drawing
  (let [state {:players [(model/player [(model/card :queen :clubs)])
                        (model/player [(model/card :ace :spades)])]
               :draw-pile [(model/card :two :diamonds)]
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 0
               :status :in-progress
               :winner nil}]
    (is (= {:type :domain-error
            :reason :must-play-before-drawing}
           (commands/decide state {:type :draw-card
                                   :player 0})))))

(deftest draw-card-fails-when-pile-empty
  (let [state {:players [(model/player [(model/card :ace :clubs)])
                        (model/player [(model/card :king :spades)])]
               :draw-pile []
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 0
               :status :in-progress
               :winner nil}]
    (is (= {:type :domain-error
            :reason :draw-pile-empty}
           (commands/decide state {:type :draw-card
                                   :player 0})))))

(deftest draw-card-fails-for-non-current-player
  (let [state {:players [(model/player [(model/card :ace :clubs)])
                        (model/player [(model/card :king :spades)])]
               :draw-pile [(model/card :two :diamonds)]
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 0
               :status :in-progress
               :winner nil}]
    (is (= {:type :domain-error
            :reason :not-current-player}
           (commands/decide state {:type :draw-card
                                   :player 1})))))
```

- [ ] **Step 2: Run the focused commands tests to verify they fail**

Run: `clojure -M:test --focus crazy_eights.domain.commands-test`
Expected: FAIL because `:draw-card` is not implemented yet.

- [ ] **Step 3: Commit the red tests**

```bash
git add test/crazy_eights/domain/commands_test.clj
git commit -m "test: add draw-card command coverage"
```

### Task 2: Implement Draw Card Decision And Event Flow

**Files:**
- Modify: `src/crazy_eights/domain/commands.clj`
- Modify: `src/crazy_eights/domain/events.clj`

- [ ] **Step 1: Implement the new `:card-drawn` event**

Update `src/crazy_eights/domain/events.clj` by inserting this method before `:turn-advanced`:

```clojure
(defmethod apply-event :card-drawn [state {:keys [player card]}]
  (-> state
      (update :draw-pile #(vec (rest %)))
      (update-in [:players player :hand] conj card)))
```

The full file should still keep `:game-started`, `:card-played`, `:suit-declared`, `:turn-advanced`, `:game-won`, and `:default` methods.

- [ ] **Step 2: Implement the minimal draw decision logic**

Update `src/crazy_eights/domain/commands.clj` by adding these helpers after `next-player`:

```clojure
(defn- current-hand [state player]
  (get-in state [:players player :hand]))

(defn- any-playable-card? [state hand]
  (some #(model/playable-card? state %) hand))

(defn- draw-card-events [state {:keys [player]}]
  (let [hand (current-hand state player)
        drawn-card (first (:draw-pile state))]
    (cond
      (not= player (:current-player state))
      (domain-error :not-current-player)

      (any-playable-card? state hand)
      (domain-error :must-play-before-drawing)

      (nil? drawn-card)
      (domain-error :draw-pile-empty)

      :else
      [{:type :card-drawn
        :player player
        :card drawn-card}
       {:type :turn-advanced
        :player (next-player state)}])))
```

Then extend `decide` with:

```clojure
    :draw-card (draw-card-events state command)
```

So the end of `decide` becomes:

```clojure
(defn decide [state command]
  (case (:type command)
    :start-game ...
    :play-card (play-card-events state command)
    :draw-card (draw-card-events state command)
    (domain-error :unknown-command)))
```

- [ ] **Step 3: Run the focused commands tests to verify they pass**

Run: `clojure -M:test --focus crazy_eights.domain.commands-test`
Expected: PASS with the new draw behavior.

- [ ] **Step 4: Commit the draw implementation**

```bash
git add src/crazy_eights/domain/commands.clj src/crazy_eights/domain/events.clj
git commit -m "feat: add draw-card command flow"
```

### Task 3: Add Invariant And Scenario Coverage

**Files:**
- Modify: `test/crazy_eights/domain/invariants_test.clj`
- Modify: `test/crazy_eights/domain/scenarios_test.clj`
- Create: `resources/domain/scenarios/draws_when_no_playable_card.edn`

- [ ] **Step 1: Write the failing invariant-preservation and scenario updates**

Append this test to `test/crazy_eights/domain/invariants_test.clj`:

```clojure
(deftest drawn-card-state-preserves-invariants
  (let [state {:players [(model/player [(model/card :ace :clubs)])
                        (model/player [(model/card :king :spades)])]
               :draw-pile [(model/card :two :diamonds)
                           (model/card :three :diamonds)]
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 0
               :status :in-progress
               :winner nil}
        events (commands/decide state {:type :draw-card
                                       :player 0})
        next-state (events/apply-events state events)]
    (is (vector? events))
    (is (empty? (invariants/check next-state)))))
```

Update `test/crazy_eights/domain/scenarios_test.clj` to include the new path:

```clojure
(deftest bundled-scenarios-pass
  (doseq [path ["domain/scenarios/play_matching_rank.edn"
                "domain/scenarios/play_eight_declares_suit.edn"
                "domain/scenarios/cannot_play_invalid_card.edn"
                "domain/scenarios/draws_when_no_playable_card.edn"]]
    (testing path
      (is (true? (:pass? (scenarios/run-scenario-resource path)))))))
```

Create `resources/domain/scenarios/draws_when_no_playable_card.edn` with:

```edn
{:name "draws when no playable card"
 :given {:state {:players [{:hand [{:rank :ace :suit :clubs}]}
                           {:hand [{:rank :king :suit :spades}]}]
                 :draw-pile [{:rank :two :suit :diamonds}
                             {:rank :three :suit :diamonds}]
                 :discard-pile [{:rank :queen :suit :hearts}]
                 :active-suit :hearts
                 :current-player 0
                 :status :in-progress
                 :winner nil}}
 :when {:command {:type :draw-card
                  :player 0}}
 :then {:events [{:type :card-drawn
                  :player 0
                  :card {:rank :two :suit :diamonds}}
                 {:type :turn-advanced
                  :player 1}]
        :state-matches {:draw-pile [{:rank :three :suit :diamonds}]
                        :players [{:hand [{:rank :ace :suit :clubs}
                                          {:rank :two :suit :diamonds}]}
                                  {:hand [{:rank :king :suit :spades}]}]
                        :current-player 1}}}
```

- [ ] **Step 2: Run the focused invariant and scenario tests**

Run: `clojure -M:test --focus crazy_eights.domain.invariants-test --focus crazy_eights.domain.scenarios-test`
Expected: PASS if the command/event implementation already supports the draw slice fully.

- [ ] **Step 3: Run the full suite**

Run: `clojure -M:test`
Expected: PASS for all tests.

- [ ] **Step 4: Commit the coverage additions**

```bash
git add test/crazy_eights/domain/invariants_test.clj test/crazy_eights/domain/scenarios_test.clj resources/domain/scenarios/draws_when_no_playable_card.edn
git commit -m "test: cover draw-card scenarios"
```

## Plan Review Notes

- Spec coverage: covers strict draw legality, draw error cases, `:card-drawn` event application, invariant preservation, and one executable scenario.
- Placeholder scan: no placeholders remain; all files, commands, and code snippets are explicit.
- Type consistency: the plan uses `:draw-card`, `:card-drawn`, `:must-play-before-drawing`, and `:draw-pile-empty` consistently across tests, implementation, and scenario data.
