#!/bin/bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_subgraph_counter_common.sh"

usage() {
  echo "Usage: $(basename "$0") [--timeout-seconds SECONDS] INPUT_GRAPH [PER_UPDATE_TIMES_CSV] [TIMING_STATS_CSV]" >&2
  echo "Writes both per-update timing data and timing summary statistics." >&2
  echo "Pattern timing is separated only into triangles, all four-node patterns, and their sum." >&2
}

parse_common_runner_options "$@"
set -- "${subgraph_counter_positionals[@]}"

if [[ $# -lt 1 || $# -gt 3 ]]; then
  usage
  exit 1
fi

input_path="$(realpath "$1")"
require_file "$input_path"
times_output="${2:-$(default_output_path "$input_path" ".per_update_times.csv")}"
stats_output="${3:-$(default_output_path "$input_path" ".timing_stats.csv")}"
ensure_parent_dir "$times_output"
ensure_parent_dir "$stats_output"

tmp_timing="$(mktemp)"
trap 'rm -f "$tmp_timing"' EXIT

ensure_built
run_signed_counter "$input_path" \
  --timing-mode per-update-no-memory \
  --timing-output "$tmp_timing" \
  "${subgraph_counter_runner_args[@]}" \
  > /dev/null

python3 "$python_helper" times --input "$tmp_timing" --output "$times_output"
python3 "$python_helper" stats \
  --input "$tmp_timing" \
  --output "$stats_output" \
  --graph-name "$(basename "$input_path")"

echo "wrote $times_output"
echo "wrote $stats_output"
