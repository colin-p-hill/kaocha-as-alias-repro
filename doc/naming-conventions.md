# Naming conventions

The specs in this library follow certain naming conventions, some of which have specific semantic meaning.

## Variance indicators

Oftentimes, we write a spec which does not perfectly describe the set of values we intend. We might do this to reduce complexity, to leave room for future extensions, or because we cannot find a way to write a totally precise spec.

These approximations work fine for most use cases, but they occasionally present surprising problems.

### Motivation

These specs seem reasonable, right?
```clojure
(s/def :example/predicate (s/fspec :args (s/cat :x any?)
                                   :ret boolean?))
(s/fdef my-every?
        :args (s/cat :pred :example/predicate :coll seqable?)
        :ret boolean?)
;; We copy the function to avoid colliding with spec's internal use of it later.
(def my-every clojure.core/every?)
```

They even pass generative testing.
```clojure
(st/summarize-results (st/check `my-every?))
;;=> {:total 1, :check-passed :1}
```

But we run into problems when we instrument the function and call it with realistic input.
```clojure
(st/instrument `my-every?)
(every? pos? [1 2 3 4])
;;=>! Execution error - invalid arguments to user/my-every? at (scratch.clj:2).
;;    (nil) - failed: (apply fn) at: [:pred] spec: :example/predicate
```

A narrower check locates the problem in the predicate, albeit with a slightly confusing error message:
```clojure
(s/explain :example/predicate pos?)
;;=>_ (\space) - failed: (apply fn) spec: :example/predicate

;; You may get a slightly different output, such as:
;;=>_ (nil) - failed: (apply fn) spec: :example/predicate
```

This error message means that validation failed for the `:example/predicate` spec when the validator applied the function `pos?` to the value `\space`. Naturally, since `\space` isn't a valid input for `pos?`, `pos?` threw an exception, causing validation to fail.

This happened because `fspec` validation involves running the function through a small generative test. We gave our `fspec` an `:args` parameter of `(s/cat :x any?)`, because we intend it to describe all possible predicates. However, this means that our spec generates input conforming to `any?`, which permits character values – it doesn't know that `pos?` can only accept numeric values.

The predicative design of spec gives it tremendous flexibility, but here we have one of its limitations. This pitfall can easily surprise developers, so we adopt naming conventions to make it clearer.
```clojure
;; Overspec: Valid predicates can accept only certain values.
(s/def :example/<predicate (s/fspec :args (s/cat :x any?)
                                   :ret boolean?))
```

This convention gives us an indicator to stop and think carefully about how a spec applies, which can save us from making these mistakes.
```clojure
(s/fdef my-every?
        :args (s/cat :pred
                     ;; We know from its name that :example/<predicate rejects
                     ;; some valid values, so using it in an args spec can
                     ;; cause failures on valid input.
                     #_:example/<predicate
                     ;; Instead, we use a broader spec.
                     ifn?
                     :coll seqable?)
        :ret boolean?)
```

In practice, we will rarely use overspecified specs, but we will often use underspecified specs.

```text
TODO: Instrument is unsafe on overspecified args, but check is unsafe on
      underspecified args. Naming conventions tell us which problem we have, but
      they only let us avoid the problem entirely if we can find a precise spec.
      Is this problem better solved by using liberal specs with conservative
      generators?
```

As an aside, we can sometimes avoid this problem in our own code by simply not writing [partial functions](https://mathworld.wolfram.com/PartialFunction.html). Rather than throwing an exception on invalid input, we can return an [anomaly](https://github.com/cognitect-labs/anomalies), which we can describe in the spec as a possible result. However, many situations constrain us in ways that make this pattern unworkable, in which case we adopt these naming conventions to at least make the problem more visible.

### `>` – <span id=underspecified>Underspecified</a>

A keyword beginning with `>` indicates an underspecified spec. These specs may accept values which they should reject according to their intended semantics.

You can read the `>` prefix as "superset of".

```clojure
;; This spec represents a complete sentence...
(s/def :example/>sentence string?)
;; ...but it accepts any string.
(s/valid? :example/>sentence "hunter2")
;; => true
```

### `<` – Overspecified

A keyword beginning with `<` indicates an overspecified spec. These specs may reject values which they should accept according to their intended semantics.

You can read the `<` prefix as "subset of".

TODO: Come up with a good example

### `<>` – Quasispecified

A keyword beginning with `<>` indicates what I term a "quasispecified" spec. These specs may _accept_ values which they should _reject_ according to their intended semantics, _and_ they may _reject_ values which they should _accept_ according to their intended semantics.

You can read the `<>` prefix as "approximately".

```clojure
;; The WHATWG HTML standard provides this regex for validating email addresses:
(def whatwg-email-re
  #"(?x)                                                  # regex flags
    ^
    [a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+                      # local-part
    @
    [a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?         # first label
    (?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*  # further labels
    $")
;; (https://html.spec.whatwg.org/multipage/input.html#valid-e-mail-address)

;; We can justify it as a pragmatic choice for general email validation.
(s/def :example/<>email (s/and string? #(re-find whatwg-email-re %)))

;; However, it rejects some technically valid email addresses...
(s/valid? :example/<>email "\"john..doe\"@example.org")
;; => false
;; (RFC 5322 permits quoted strings in the local-part)

;; ...and it accepts some technically invalid email addresses.
(s/valid? :example/<>email "john.doe@abc.123")
;; => true
;; (RFC 5321 requires real, resolvable domain names)
```
