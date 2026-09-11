#!/bin/bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
python_helper="$script_dir/_subgraph_counter_csv_tools.py"
build_script="$repo_root/build/build-subgraph-counter.sh"
runner_class="dna.tools.DynamicUndirectedSubgraphCounter"
classpath="$repo_root/bin:$repo_root/lib/guava-16.0.1.jar"
subgraph_counter_runner_args=()
subgraph_counter_positionals=()

ensure_built() {
  "$build_script"
}

run_signed_counter() {
  local input_path="$1"
  shift
  java -cp "$classpath" "$runner_class" signed --input "$input_path" "$@"
}

parse_common_runner_options() {
  subgraph_counter_runner_args=()
  subgraph_counter_positionals=()

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --timeout-seconds)
        if [[ $# -lt 2 ]]; then
          echo "missing value for --timeout-seconds" >&2
          exit 1
        fi
        subgraph_counter_runner_args+=(--timeout-seconds "$2")
        shift 2
        ;;
      --)
        shift
        while [[ $# -gt 0 ]]; do
          subgraph_counter_positionals+=("$1")
          shift
        done
        ;;
      *)
        subgraph_counter_positionals+=("$1")
        shift
        ;;
    esac
  done
}

default_output_path() {
  local input_path="$1"
  local suffix="$2"
  local base_name
  base_name="$(basename "$input_path")"
  printf '%s/%s%s\n' "$(pwd)" "$base_name" "$suffix"
}

ensure_parent_dir() {
  local output_path="$1"
  mkdir -p "$(dirname "$output_path")"
}

require_file() {
  local input_path="$1"
  if [[ ! -f "$input_path" ]]; then
    echo "input file not found: $input_path" >&2
    exit 1
  fi
}
