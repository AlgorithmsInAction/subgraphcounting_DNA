#!/bin/bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_subgraph_counter_common.sh"

usage() {
  echo "Usage: $(basename "$0") [--timeout-seconds SECONDS] INPUT_GRAPH [OUTPUT_CSV]" >&2
  echo "Writes per-update timing data with the only separable pattern groups:" >&2
  echo "  triangles, all four-node patterns, and their sum." >&2
}

parse_common_runner_options "$@"
set -- "${subgraph_counter_positionals[@]}"

if [[ $# -lt 1 || $# -gt 2 ]]; then
  usage
  exit 1
fi

input_path="$(realpath "$1")"
require_file "$input_path"
output_path="${2:-$(default_output_path "$input_path" ".per_update_times.csv")}"
ensure_parent_dir "$output_path"

tmp_timing="$(mktemp)"
trap 'rm -f "$tmp_timing"' EXIT

ensure_built
run_signed_counter "$input_path" \
  --timing-mode per-update-no-memory \
  --timing-output "$tmp_timing" \
  "${subgraph_counter_runner_args[@]}" \
  > /dev/null

python3 "$python_helper" times --input "$tmp_timing" --output "$output_path"
echo "wrote $output_path"
