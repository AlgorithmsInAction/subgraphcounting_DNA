#!/bin/bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_subgraph_counter_common.sh"

usage() {
  echo "Usage: $(basename "$0") [--timeout-seconds SECONDS] INPUT_GRAPH [OUTPUT_CSV]" >&2
  echo "Writes one summary row with only the total time for four-node motif updates." >&2
}

parse_common_runner_options "$@"
set -- "${subgraph_counter_positionals[@]}"

if [[ $# -lt 1 || $# -gt 2 ]]; then
  usage
  exit 1
fi

input_path="$(realpath "$1")"
require_file "$input_path"
output_path="${2:-$(default_output_path "$input_path" ".four_node_total_time_stats.csv")}"
ensure_parent_dir "$output_path"

ensure_built
run_signed_counter "$input_path" \
  --timing-mode aggregate-only \
  --metric-set four-node \
  --stats-output "$output_path" \
  "${subgraph_counter_runner_args[@]}" \
  > /dev/null

echo "wrote $output_path"
