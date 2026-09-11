# Third-party notices

The `lib/` directory contains the following third-party components. They are
unmodified from their respective upstream releases unless stated otherwise.
Their copyrights remain with their respective authors.

| Component | Version/file | License used for redistribution | Source |
| --- | --- | --- | --- |
| GraphStream Core and UI | 1.3 | LGPL-3.0 | <https://github.com/graphstream/gs-core/tree/1.3>, <https://github.com/graphstream/gs-ui/tree/1.3> |
| Guava | 16.0.1 | Apache-2.0 | <https://github.com/google/guava/tree/v16.0.1> |
| JGraphT Core | 0.9.1 | LGPL-2.1 | <https://github.com/jgrapht/jgrapht/tree/jgrapht-0.9.1> |
| Joda-Time | 2.9.2 | Apache-2.0 | <https://github.com/JodaOrg/joda-time/tree/v2.9.2> |
| jpathwatch | 0.95 | GPL-2.0 with Classpath Exception | <https://sourceforge.net/projects/jpathwatch/files/jpathwatch/0.95/> |
| Monte Media Library | 0.7.7 | LGPL-3.0 | <https://www.randelshofer.ch/monte/> |
| JSON.org Java sources | files under `src/dna/visualization/config/JSON/` | License reproduced in the source headers | <https://www.json.org/java/> |
| Darius Bacon's expression parser | files under `src/dna/util/expr/` | Original permissive license | <https://github.com/darius/expr> |

The Apache-2.0, GPL-2.0, GPL Classpath Exception, LGPL-2.1, and LGPL-3.0 texts
are included under `licenses/`. Joda-Time additionally embeds its license and
notice in its JAR. The expression parser's license is reproduced in
`src/dna/util/expr/COPYING`.

Three binaries inherited from the upstream DNA repository are not redistributed
because none is required by the supported subgraph-counting runner:

- `jchart2d-3.2.2_modified.jar`: corresponding source for the locally modified
  binary was unavailable. Unmodified JChart2D 3.2.2 source and binaries are
  available from <https://sourceforge.net/projects/jchart2d/files/jchart2d/>
  under LGPL-2.1.
- `ArgList.jar`: the archive does not identify a license or upstream source.
- `aspectjrt-1.7.4.jar`: this legacy component is not needed by the runner;
  source remains available from
  <https://github.com/eclipse-aspectj/aspectj/tree/V1_7_4> under EPL-1.0.
