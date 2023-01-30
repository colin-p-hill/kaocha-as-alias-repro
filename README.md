Run with `clj -M:run/test` and observe that no tests run.

Run with `clj -M:run/test --watch` and observe that no tests run. Then make a whitespace change to either source file and observe that `repro.core/trivial-test` runs.
