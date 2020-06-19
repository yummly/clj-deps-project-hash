(ns yummly.clj-deps-project-hash
  (:require [clojure.java.io :as io]
            [digest :as digest]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [valuehash.api :as v]
            [clojure.set :as set])
  (:import java.io.File)
  (:gen-class))

(defn project-file? [^File f]
  (str/ends-with? (str f) "/deps.edn"))

(defn code-file?
  "All files under source directories are considered, without filtering by extension"
  [^File f]
  (.isFile f))

(defn project-files-in-tree [root]
  (let [root (io/file root)
        files (file-seq root)]
    (->> files
         (filter project-file?)
         sort)))

(defn normalize-deps
  "Make local/root paths canonical"
  [project-file deps]
  (let [project-dir (-> (io/file project-file) .getAbsoluteFile .getParent io/file)]
    (into (sorted-map)
          (for [[k v] deps]
            [k (update v :local/root #(when %
                                        (-> (io/file project-dir % "deps.edn")
                                            (.getCanonicalPath))))]))))

(defn file->project-hash
  "Compute the hash for a project based on its source code and deps file but NOT its dependencies"
  [file aliases]
  (let [project (-> file io/file slurp edn/read-string)
        deps    (->> (reduce merge
                             (:deps project)
                             (for [alias aliases]
                               (get-in project [:aliases alias :extra-deps])))
                     (normalize-deps file)
                     vec)
        ;; canonical paths
        paths   (->> (reduce concat
                             (concat (:paths project) (:extra-paths project))
                             (for [alias aliases]
                               (get-in project [:aliases alias :extra-paths])))
                     (mapv #(-> (io/file file ".." %)
                                (.getCanonicalPath)))
                     sort
                     (map io/file))

        files        (->> (mapcat file-seq paths)
                          (filter code-file?)
                          sort)
        project-hash (v/sha-1-str project)
        file-hashes  (mapv digest/sha1 files)
        hashes       (->> (cons project-hash file-hashes)
                          (str/join "\n"))
        hash         (digest/sha1 hashes)]
    {:project-file (-> file io/file (.getCanonicalPath))
     :deps         deps
     :local-deps   (->> deps (map second) (keep :local/root))
     :paths        paths
     :files        files
     :project-hash project-hash
     :file-hashes  file-hashes
     :hash         hash}))

(defn unroll [from->set]
  (let [next (reduce-kv
              (fn [from->set from to-set]
                (update from->set from set/union
                        (->> (map from->set to-set)
                             (reduce set/union))))
              from->set
              from->set)]
    (if (= next from->set)
      next
      (recur next))))

(defn tree->hashes [root]
  (let [root (-> root io/file (.getCanonicalPath))
        projects (project-files-in-tree root)
        project-file-hashes (mapv #(file->project-hash % [:dev :test :check]) projects)
        project->hash (into (sorted-map)
                            (zipmap (map :project-file project-file-hashes)
                                    (map :hash project-file-hashes)))
        inverted-local-deps (reduce
                             (fn [from->to {:keys [project-file local-deps]}]
                               (merge-with set/union
                                           from->to
                                           (zipmap local-deps (repeat #{project-file}))))
                             (zipmap (keys project->hash) (repeat #{}))
                             project-file-hashes)
        local-deps (into (sorted-map)
                         (zipmap (map :project-file project-file-hashes)
                                 (map (comp set :local-deps) project-file-hashes)))
        local-deps (unroll local-deps)]
    (zipmap
     (keys project->hash)
     (for [project (keys project->hash)]
       (let [deps (->> (local-deps project) vec sort)
             hashes (map project->hash (cons project deps))]
         (digest/sha-1 (str/join "\n" hashes)))))))


(defn -main
  [& args]
  (let [root (or (first args) ".")
        p->h (tree->hashes root)]
    (doseq [[p h] (sort-by first p->h)]
      (printf "%s %s\n" p h))))
