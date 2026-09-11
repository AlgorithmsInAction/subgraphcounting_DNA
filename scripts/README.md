# Script Wrappers

These scripts wrap `dna.tools.DynamicUndirectedSubgraphCounter` for the signed
dynamic graph format:

```text
<src> <dst> +1/-1 timestamp
```

You can run them from any directory. Each script resolves the DNA repository
from its own location and builds the Java runner automatically before running.

## Available Scripts

- `run_subgraph_counter_per_update_times.sh`
- `run_subgraph_counter_stats.sh`
- `run_subgraph_counter_times_and_stats.sh`
- `run_subgraph_counter_memory.sh`
- `run_subgraph_counter_total_time.sh`
- `run_subgraph_counter_triangle_total_time.sh`
- `run_subgraph_counter_four_node_total_time.sh`

All wrappers also accept:

- `--timeout-seconds SECONDS`

When a timeout is reached, the Java runner prints a summary like
`TIMEOUT processed_updates=...` to stderr, and any generated CSVs reflect only
the updates that were actually completed before the stop.

Pattern timing is only separated where the underlying code can actually
separate it:

- `triangle`
- `four_node_patterns`
- `all_patterns = triangle + four_node_patterns`

It does not try to report separate timings for claws, diamonds, paws,
four-cycles, four-cliques, or length-3 paths individually, because those are
all maintained together inside the four-node motif metric.

## Usage Examples

Run from anywhere and write per-update timing data:

```bash
./run_subgraph_counter_per_update_times.sh \
  /abs/path/to/input.e \
  /abs/path/to/per_update_times.csv
```

Run from anywhere and write timing summary statistics only:

```bash
./run_subgraph_counter_stats.sh \
  /abs/path/to/input.e \
  /abs/path/to/timing_stats.csv
```

Run from anywhere and write only the total time for processing all applied updates:

```bash
./run_subgraph_counter_total_time.sh \
  /abs/path/to/input.e \
  /abs/path/to/total_time_stats.csv
```

Run from anywhere and write only the total time for triangle updates:

```bash
./run_subgraph_counter_triangle_total_time.sh \
  /abs/path/to/input.e \
  /abs/path/to/triangle_total_time_stats.csv
```

Run from anywhere and write only the total time for four-node motif updates:

```bash
./run_subgraph_counter_four_node_total_time.sh \
  /abs/path/to/input.e \
  /abs/path/to/four_node_total_time_stats.csv
```

Run from anywhere and write both per-update timing data and timing summary statistics:

```bash
./run_subgraph_counter_times_and_stats.sh \
  /abs/path/to/input.e \
  /abs/path/to/per_update_times.csv \
  /abs/path/to/timing_stats.csv
```

Run from anywhere and write per-update memory usage plus memory summary statistics:

```bash
./run_subgraph_counter_memory.sh \
  /abs/path/to/input.e \
  /abs/path/to/memory.csv \
  /abs/path/to/memory_stats.csv
```

Run any wrapper with a timeout:

```bash
./run_subgraph_counter_stats.sh \
  --timeout-seconds 60 \
  /abs/path/to/input.e \
  /abs/path/to/timing_stats.csv
```

## Default Output Names

If you omit output paths, the scripts write into the current working directory
using the input file name as a prefix:

- `run_subgraph_counter_per_update_times.sh`:
  `<input_basename>.per_update_times.csv`
- `run_subgraph_counter_stats.sh`:
  `<input_basename>.timing_stats.csv`
- `run_subgraph_counter_times_and_stats.sh`:
  `<input_basename>.per_update_times.csv` and
  `<input_basename>.timing_stats.csv`
- `run_subgraph_counter_memory.sh`:
  `<input_basename>.memory.csv` and `<input_basename>.memory_stats.csv`
- `run_subgraph_counter_total_time.sh`:
  `<input_basename>.total_time_stats.csv`
- `run_subgraph_counter_triangle_total_time.sh`:
  `<input_basename>.triangle_total_time_stats.csv`
- `run_subgraph_counter_four_node_total_time.sh`:
  `<input_basename>.four_node_total_time_stats.csv`

## Output Shapes

`run_subgraph_counter_per_update_times.sh` and
`run_subgraph_counter_times_and_stats.sh` write:

```text
step,timestamp,update_time_seconds,cumulative_update_time_seconds,triangle_time_seconds,four_node_patterns_time_seconds,all_patterns_time_seconds
```

`run_subgraph_counter_stats.sh` and
`run_subgraph_counter_times_and_stats.sh` write one-row CSVs with:

- overall update time stats
- triangle time stats
- four-node-pattern time stats
- combined all-pattern time stats

The stats reported are:

- `total`
- `mean`
- `min`
- `max`
- `1q`
- `99q`

`run_subgraph_counter_total_time.sh` writes a one-row CSV with:

```text
graph,metric_set,steps,overall_total_update_seconds,completed_all_updates,timed_out,timeout_seconds,last_processed_timestamp,triangles_final,four_cycles_final,diamonds_final,paws_final,length_3_paths_final,claws_final,four_cliques_final,pattern_specific_timing_supported,pattern_specific_timing_note
```

This mode measures only the whole signed-update replay time. It does not
collect per-update or per-metric timings, specifically to minimize timing
overhead.

`run_subgraph_counter_triangle_total_time.sh` writes the same header, but with
`metric_set=triangles`. In that mode the four-node motif metric is not
instantiated, so the measurement is for triangle maintenance only.

`run_subgraph_counter_four_node_total_time.sh` writes the same header, but with
`metric_set=four-node`. In that mode the triangle metric is not instantiated,
so the measurement is for four-node motif maintenance only.

`run_subgraph_counter_memory.sh` writes:

```text
step,timestamp,memory_used_bytes
```

and a one-row memory stats CSV with:

- `memory_last_bytes`
- `memory_mean_bytes`
- `memory_min_bytes`
- `memory_max_bytes`
- `memory_1q_bytes`
- `memory_99q_bytes`
