#!/usr/bin/env python3

import argparse
import csv
import math
from pathlib import Path


def percentile_index(size: int, quantile: float) -> int:
    if size <= 1:
        return 0
    index = int(math.floor((size - 1) * quantile))
    if index < 0:
        return 0
    if index >= size:
        return size - 1
    return index


def distribution_stats(values):
    if not values:
        return {
            "total": 0.0,
            "mean": 0.0,
            "min": 0.0,
            "max": 0.0,
            "q1": 0.0,
            "q99": 0.0,
        }
    sorted_values = sorted(values)
    total = sum(values)
    return {
        "total": total,
        "mean": total / len(values),
        "min": min(values),
        "max": max(values),
        "q1": sorted_values[percentile_index(len(sorted_values), 0.01)],
        "q99": sorted_values[percentile_index(len(sorted_values), 0.99)],
    }


def read_timing_rows(path: Path):
    rows = []
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            triangle = float(row["triangle_metric_time_seconds"])
            four_node = float(row["motifs_metric_time_seconds"])
            rows.append(
                {
                    "step": int(row["step"]),
                    "timestamp": int(row["timestamp"]),
                    "update_time_seconds": float(row["update_time_seconds"]),
                    "cumulative_update_time_seconds": float(
                        row["cumulative_update_time_seconds"]
                    ),
                    "triangle_time_seconds": triangle,
                    "four_node_patterns_time_seconds": four_node,
                    "all_patterns_time_seconds": triangle + four_node,
                    "memory_used_bytes": int(row["memory_used_bytes"]),
                }
            )
    return rows


def write_csv(path: Path, header, rows):
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=header)
        writer.writeheader()
        writer.writerows(rows)


def command_times(args):
    rows = read_timing_rows(Path(args.input))
    header = [
        "step",
        "timestamp",
        "update_time_seconds",
        "cumulative_update_time_seconds",
        "triangle_time_seconds",
        "four_node_patterns_time_seconds",
        "all_patterns_time_seconds",
    ]
    output_rows = [{key: row[key] for key in header} for row in rows]
    write_csv(Path(args.output), header, output_rows)


def command_stats(args):
    rows = read_timing_rows(Path(args.input))
    overall = distribution_stats([row["update_time_seconds"] for row in rows])
    triangle = distribution_stats([row["triangle_time_seconds"] for row in rows])
    four_node = distribution_stats(
        [row["four_node_patterns_time_seconds"] for row in rows]
    )
    all_patterns = distribution_stats(
        [row["all_patterns_time_seconds"] for row in rows]
    )

    header = [
        "graph",
        "steps",
        "overall_total_update_seconds",
        "overall_mean_update_seconds",
        "overall_min_update_seconds",
        "overall_max_update_seconds",
        "overall_1q_update_seconds",
        "overall_99q_update_seconds",
        "triangle_total_seconds",
        "triangle_mean_seconds",
        "triangle_min_seconds",
        "triangle_max_seconds",
        "triangle_1q_seconds",
        "triangle_99q_seconds",
        "four_node_patterns_total_seconds",
        "four_node_patterns_mean_seconds",
        "four_node_patterns_min_seconds",
        "four_node_patterns_max_seconds",
        "four_node_patterns_1q_seconds",
        "four_node_patterns_99q_seconds",
        "all_patterns_total_seconds",
        "all_patterns_mean_seconds",
        "all_patterns_min_seconds",
        "all_patterns_max_seconds",
        "all_patterns_1q_seconds",
        "all_patterns_99q_seconds",
    ]
    output_row = {
        "graph": args.graph_name,
        "steps": len(rows),
        "overall_total_update_seconds": overall["total"],
        "overall_mean_update_seconds": overall["mean"],
        "overall_min_update_seconds": overall["min"],
        "overall_max_update_seconds": overall["max"],
        "overall_1q_update_seconds": overall["q1"],
        "overall_99q_update_seconds": overall["q99"],
        "triangle_total_seconds": triangle["total"],
        "triangle_mean_seconds": triangle["mean"],
        "triangle_min_seconds": triangle["min"],
        "triangle_max_seconds": triangle["max"],
        "triangle_1q_seconds": triangle["q1"],
        "triangle_99q_seconds": triangle["q99"],
        "four_node_patterns_total_seconds": four_node["total"],
        "four_node_patterns_mean_seconds": four_node["mean"],
        "four_node_patterns_min_seconds": four_node["min"],
        "four_node_patterns_max_seconds": four_node["max"],
        "four_node_patterns_1q_seconds": four_node["q1"],
        "four_node_patterns_99q_seconds": four_node["q99"],
        "all_patterns_total_seconds": all_patterns["total"],
        "all_patterns_mean_seconds": all_patterns["mean"],
        "all_patterns_min_seconds": all_patterns["min"],
        "all_patterns_max_seconds": all_patterns["max"],
        "all_patterns_1q_seconds": all_patterns["q1"],
        "all_patterns_99q_seconds": all_patterns["q99"],
    }
    write_csv(Path(args.output), header, [output_row])


def command_memory(args):
    rows = read_timing_rows(Path(args.input))
    memory_rows = [
        {
            "step": row["step"],
            "timestamp": row["timestamp"],
            "memory_used_bytes": row["memory_used_bytes"],
        }
        for row in rows
    ]
    write_csv(
        Path(args.memory_output),
        ["step", "timestamp", "memory_used_bytes"],
        memory_rows,
    )

    memory_values = [row["memory_used_bytes"] for row in rows]
    stats = distribution_stats(memory_values)
    last_value = memory_values[-1] if memory_values else 0
    write_csv(
        Path(args.stats_output),
        [
            "graph",
            "steps",
            "memory_last_bytes",
            "memory_mean_bytes",
            "memory_min_bytes",
            "memory_max_bytes",
            "memory_1q_bytes",
            "memory_99q_bytes",
        ],
        [
            {
                "graph": args.graph_name,
                "steps": len(rows),
                "memory_last_bytes": last_value,
                "memory_mean_bytes": stats["mean"],
                "memory_min_bytes": stats["min"],
                "memory_max_bytes": stats["max"],
                "memory_1q_bytes": stats["q1"],
                "memory_99q_bytes": stats["q99"],
            }
        ],
    )


def build_parser():
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    times_parser = subparsers.add_parser("times")
    times_parser.add_argument("--input", required=True)
    times_parser.add_argument("--output", required=True)
    times_parser.set_defaults(func=command_times)

    stats_parser = subparsers.add_parser("stats")
    stats_parser.add_argument("--input", required=True)
    stats_parser.add_argument("--output", required=True)
    stats_parser.add_argument("--graph-name", required=True)
    stats_parser.set_defaults(func=command_stats)

    memory_parser = subparsers.add_parser("memory")
    memory_parser.add_argument("--input", required=True)
    memory_parser.add_argument("--memory-output", required=True)
    memory_parser.add_argument("--stats-output", required=True)
    memory_parser.add_argument("--graph-name", required=True)
    memory_parser.set_defaults(func=command_memory)

    return parser


def main():
    parser = build_parser()
    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
