# DNA - Dynamic Network Analyzer

![DNA](logo/dna-logo-all.pdf)

old webpage: https://www.p2p.tu-darmstadt.de/research/dna/

## Documentation

- https://github.com/BenjaminSchiller/DNA.doc
- https://github.com/BenjaminSchiller/DNA/tree/master/doc


## Analysis of Konect datasets

- https://github.com/BenjaminSchiller/DNA.Konect


## Examples

- https://github.com/BenjaminSchiller/DNA.demo
- https://github.com/BenjaminSchiller/DNA.examples

## Modifications in this fork

This fork adds `dna.tools.DynamicUndirectedSubgraphCounter`, a command-line
runner used as a baseline for the experiments in the following paper:

> Kathrin Hanauer, Sophia Heck, Monika Henzinger, and Leonhard Paul Sidl.
> Fully dynamic triangle and 4-vertex subgraph counting: From theory to
> practice and back again. In Philip Bille and Quanquan C. Liu, editors,
> Proceedings of the 29th Symposium on Algorithm Engineering and Experiments,
> ALENEX 2027, Philadelphia, Pennsylvania, U.S., January 24-25, 2027. SIAM,
> 2027. Accepted, to appear.

The runner reads signed, timestamped updates of the form
`<source> <target> +1|-1 <timestamp>` and maintains triangle and four-node
subgraph counts. Wrapper scripts under `scripts/` provide per-update and total
timings, memory measurements, summary statistics, metric-specific runs, and an
optional timeout. The fork also includes the compatibility changes needed to
compile this runner with a current JDK while producing Java 8 bytecode.

Building requires JDK 9 or newer, Bash, and Python 3. Run
`./build/build-subgraph-counter.sh` or use one of the wrappers directly; see
[`scripts/README.md`](scripts/README.md) for commands, input semantics, and
output formats.

The modified code remains under GPLv3. Bundled dependencies and incorporated
third-party sources retain their respective licenses; see
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
