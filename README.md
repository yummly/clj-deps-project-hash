# clj-deps-project-hash

Compute hashes for a tree of Clojure tools.deps projects.

This tool can be given the root of a directory tree containing a number of Clojure projects built using `tools.deps`. The project files are expected to be named `deps.edn`.

It will return, for each of the projects, a hash. The hash is computed based on the `deps.edn` file, the files in the project's source directories used by the `:dev`, `:test`, and
`:check` aliases, and, recursively, the hashes of each of its `:local/repository` dependencies.  These hashes are meant to be used to determine if the projects needs to be rebuilt
(presumably, the output of the build process is cached somewhere).

## Usage

Run the project directly:

    $ clojure -m yummly.clj-deps-project-hash path/to/project/root

Or, include it in your project:

```clojure
{:aliases
 :hash {:extra-deps {yummly/clj-deps-project-hash {:git/url "https://github.com/yummly/clj-deps-project-hash.git
                                                   :sha "...}}
         :main-opts ["-m" "yummly.clj-deps-project-hash"]}}
```

    $ clj -A:hash

The output is a line per project, with the path to the project file, space, hash. The paths are absolute.

## License

Copyright © 2020 Yummly

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
