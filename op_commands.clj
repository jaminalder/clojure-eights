(require '[crazy_eights.operator :as op])

(op/start-sim 3 0)

(count (:in-progress (op/games)))
(count (:finished (op/games)))
(first (:finished (op/games)))

(dotimes [_ 1000]
  (op/start-sim 3 2))

;; VM data

(import '[java.lang.management ManagementFactory])

(def runtime (Runtime/getRuntime))

{:free-memory (.freeMemory runtime)
 :total-memory (.totalMemory runtime)
 :max-memory (.maxMemory runtime)
 :available-processors (.availableProcessors runtime)}

;; Thread count:
(let [mx (ManagementFactory/getThreadMXBean)]
  {:thread-count (.getThreadCount mx)
   :daemon-thread-count (.getDaemonThreadCount mx)
   :peak-thread-count (.getPeakThreadCount mx)})

;; List threads:
(->> (Thread/getAllStackTraces)
     keys
     (map #(hash-map :name (.getName %) :state (.getState %) :daemon? (.isDaemon %)))
     (sort-by :name))

;; GC stats:
(for [gc (ManagementFactory/getGarbageCollectorMXBeans)]
  {:name (.getName gc)
   :count (.getCollectionCount gc)
   :time-ms (.getCollectionTime gc)})

;; System load:
(let [os (ManagementFactory/getOperatingSystemMXBean)]
  {:name (.getName os)
   :arch (.getArch os)
   :processors (.getAvailableProcessors os)
   :system-load-average (.getSystemLoadAverage os)})
