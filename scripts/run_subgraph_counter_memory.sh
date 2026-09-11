#!/bin/bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_subgraph_counter_common.sh"

usage() {
  echo "Usage: $(basename "$0") [--timeout-seconds SECONDS] INPUT_GRAPH [MEMORY_CSV] [MEMORY_STATS_CSV]" >&2
  echo "Writes per-update memory usage and aggregate memory statistics." >&2
}

parse_common_runner_options "$@"
set -- "${subgraph_counter_positionals[@]}"

if [[ $# -lt 1 || $# -gt 3 ]]; then
  usage
  exit 1
fi

input_path="$(realpath "$1")"
require_file "$input_path"
memory_output="${2:-$(default_output_path "$input_path" ".memory.csv")}"
stats_output="${3:-$(default_output_path "$input_path" ".memory_stats.csv")}"
ensure_parent_dir "$memory_output"
ensure_parent_dir "$stats_output"

tmp_timing="$(mktemp)"
trap 'rm -f "$tmp_timing"' EXIT

ensure_built
run_signed_counter "$input_path" \
  --timing-mode per-update \
  --timing-output "$tmp_timing" \
  "${subgraph_counter_runner_args[@]}" \
  > /dev/null

python3 "$python_helper" memory \
  --input "$tmp_timing" \
  --memory-output "$memory_output" \
  --stats-output "$stats_output" \
  --graph-name "$(basename "$input_path")"

echo "wrote $memory_output"
echo "wrote $stats_output"
