## Code organization

- The core namespace `com.colinphill.extra-special` may depend on namespaces under `impl`.
- The core namespace `com.colinphill.extra-special` may not depend on internal namespaces other than those under `impl`.
- Namespaces under `impl` may depend on other `impl` namespaces.
- Namespaces under `impl` may not depend on internal namespaces other than those under `impl`.
- All other namespaces (i.e., `^com\.colinphill\.extra-special\.(?!impl(\.|$)).+`) may depend on anything.
- All namespaces may depend on external namespaces.

These rules support the following conventions:

- Core functionality and common utilities live in `com.colinphill.extra-special`.
- Implemenation details which take up a lot of lines of code, or which simply don't feel at home in the core namespace, live in `impl` namespaces.
- Extra features that most users won't need live in non-`impl` subordinate namespaces.
