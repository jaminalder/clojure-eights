(ns crazy_eights.domain.project-shell-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]))

;; Kept for later task coverage once classpath resources exist.
(defn resource-exists? [path]
  (boolean (or (io/resource path)
               (.exists (io/file path)))))

(deftest project-shell-files-exist
  (testing "documentation files exist"
    (is (resource-exists? "README.md"))
    (is (resource-exists? "deps.edn"))
    (is (resource-exists? "tests.edn"))
    (is (resource-exists? "docs/domain.md"))
    (is (resource-exists? "docs/adr/0001-domain-first-clojure.md"))
    (is (resource-exists? "docs/adr/0002-no-database-initially.md"))))
