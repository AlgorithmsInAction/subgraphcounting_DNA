package dna.tools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dna.graph.Graph;
import dna.graph.datastructures.GDS;
import dna.graph.generators.reading.TimestampedGraph;
import dna.graph.generators.reading.TimestampedGraph.TimestampedGraphType;
import dna.graph.generators.reading.TimestampedReader;
import dna.io.BatchReader;
import dna.io.GraphReader;
import dna.metrics.IMetric;
import dna.metrics.MetricNotApplicableException;
import dna.metrics.algorithms.Algorithms;
import dna.metrics.algorithms.IAfterBatch;
import dna.metrics.algorithms.IAfterEA;
import dna.metrics.algorithms.IAfterER;
import dna.metrics.algorithms.IAfterEW;
import dna.metrics.algorithms.IAfterNA;
import dna.metrics.algorithms.IAfterNR;
import dna.metrics.algorithms.IAfterNW;
import dna.metrics.algorithms.IBeforeBatch;
import dna.metrics.algorithms.IBeforeEA;
import dna.metrics.algorithms.IBeforeER;
import dna.metrics.algorithms.IBeforeEW;
import dna.metrics.algorithms.IBeforeNA;
import dna.metrics.algorithms.IBeforeNR;
import dna.metrics.algorithms.IBeforeNW;
import dna.metrics.algorithms.IDynamicAlgorithm;
import dna.metrics.algorithms.IRecomputation;
import dna.metrics.clustering.UndirectedClusteringCoefficientU;
import dna.metrics.motifs.UndirectedMotifsU;
import dna.series.data.Value;
import dna.updates.batch.Batch;
import dna.updates.generators.reading.TimestampedBatch;
import dna.updates.generators.reading.TimestampedBatch.TimestampedBatchType;
import dna.updates.update.EdgeAddition;
import dna.updates.update.EdgeRemoval;
import dna.updates.update.EdgeWeight;
import dna.updates.update.NodeAddition;
import dna.updates.update.NodeRemoval;
import dna.updates.update.NodeWeight;
import dna.util.Log;
import dna.util.Log.LogLevel;

public class DynamicUndirectedSubgraphCounter {
	private static final String CSV_HEADER = "snapshot,timestamp,triangles,four_cycles,diamonds,paws,length_3_paths,claws,four_cliques";
	private static final String SIGNED_COUNTS_CSV_HEADER = "step,#triangles,#diamonds,#threePaths,#fourCycles,#claws,#fourCliques,#paw";
	private static final String TIMING_CSV_HEADER = "step,timestamp,update_time_seconds,cumulative_update_time_seconds,triangle_metric_time_seconds,motifs_metric_time_seconds,memory_used_bytes";
	private static final String STATS_CSV_HEADER = "graph,steps,overall_total_update_seconds,overall_mean_update_seconds,overall_min_update_seconds,overall_max_update_seconds,overall_1q_update_seconds,overall_99q_update_seconds,triangle_metric_total_seconds,triangle_metric_mean_seconds,triangle_metric_min_seconds,triangle_metric_max_seconds,triangle_metric_1q_seconds,triangle_metric_99q_seconds,motifs_metric_total_seconds,motifs_metric_mean_seconds,motifs_metric_min_seconds,motifs_metric_max_seconds,motifs_metric_1q_seconds,motifs_metric_99q_seconds,triangles_final,four_cycles_final,diamonds_final,paws_final,length_3_paths_final,claws_final,four_cliques_final,memory_last_bytes,memory_mean_bytes,memory_min_bytes,memory_max_bytes,memory_1q_bytes,memory_99q_bytes,pattern_specific_timing_supported,pattern_specific_timing_note";
	private static final String AGGREGATE_STATS_CSV_HEADER = "graph,metric_set,steps,overall_total_update_seconds,completed_all_updates,timed_out,timeout_seconds,last_processed_timestamp,triangles_final,four_cycles_final,diamonds_final,paws_final,length_3_paths_final,claws_final,four_cliques_final,pattern_specific_timing_supported,pattern_specific_timing_note";

	public static void main(String[] args) throws Exception {
		Log.setLogLevel(LogLevel.off);

		if (args.length == 0) {
			usage();
			System.exit(1);
		}

		String mode = args[0];
		Map<String, String> options = parseOptions(args, 1);

		if ("timestamped".equals(mode)) {
			runTimestamped(options);
		} else if ("signed".equals(mode)) {
			runSigned(options);
		} else if ("batches".equals(mode)) {
			runBatches(options);
		} else {
			System.err.println("Unknown mode: " + mode);
			usage();
			System.exit(1);
		}
	}

	private static void runTimestamped(Map<String, String> options)
			throws Exception {
		Path input = Paths.get(require(options, "--input")).toAbsolutePath();
		String name = options.containsKey("--name") ? options.get("--name")
				: input.getFileName().toString();
		String separator = unescape(options.getOrDefault("--separator", ","));
		String commentPrefix = unescape(options.getOrDefault(
				"--comment-prefix", "%"));
		boolean remap = Boolean.parseBoolean(options.getOrDefault("--remap",
				"true"));
		TimestampedGraphType initialType = TimestampedGraphType.valueOf(options
				.getOrDefault("--initial-type", "EDGE_COUNT"));
		long initialParam = Long.parseLong(options.getOrDefault(
				"--initial-param", "0"));
		TimestampedBatchType batchType = TimestampedBatchType.valueOf(require(
				options, "--batch-type"));
		long batchParam = Long.parseLong(require(options, "--batch-param"));
		Long maxTimestamp = options.containsKey("--max-timestamp") ? Long
				.valueOf(options.get("--max-timestamp")) : null;

		TimestampedReader reader = new TimestampedReader(parent(input), input
				.getFileName().toString(), name, remap, separator,
				commentPrefix);
		TimestampedGraph graphGenerator = new TimestampedGraph(reader,
				GDS.undirected(), initialType, initialParam);
		TimestampedBatch batchGenerator = maxTimestamp == null ? new TimestampedBatch(
				reader, batchType, batchParam) : new TimestampedBatch(reader,
				batchType, batchParam, maxTimestamp.longValue());

		Graph graph = graphGenerator.generate();
		runCounter(graph, buildMetrics(MetricSet.ALL), new BatchProvider() {
			@Override
			public boolean hasNext(Graph g) {
				return batchGenerator.isFurtherBatchPossible(g);
			}

			@Override
			public Batch next(Graph g) {
				return batchGenerator.generate(g);
			}
		});
	}

	private static void runSigned(Map<String, String> options) throws Exception {
		Path input = Paths.get(require(options, "--input")).toAbsolutePath();
		String name = options.containsKey("--name") ? options.get("--name")
				: input.getFileName().toString();
		String commentPrefix = unescape(options.getOrDefault(
				"--comment-prefix", "%"));
		String countsOutput = options.get("--counts-output");
		String timingOutput = options.get("--timing-output");
		String statsOutput = options.get("--stats-output");
		TimingMode timingMode = parseTimingMode(options.get("--timing-mode"),
				timingOutput != null || statsOutput != null);
		Integer debugRawLimit = options.containsKey("--debug-raw-limit") ? Integer
				.valueOf(options.get("--debug-raw-limit")) : null;
		Integer maxSteps = options.containsKey("--max-steps") ? Integer
				.valueOf(options.get("--max-steps")) : null;
		Long timeoutNs = options.containsKey("--timeout-seconds") ? secondsToNanos(
				Double.parseDouble(options.get("--timeout-seconds"))) : null;
		MetricSet metricSet = parseMetricSet(options.get("--metric-set"));
		validateTimingConfiguration(timingMode, timingOutput, statsOutput);
		ArrayList<SignedEdgeUpdate> updates = readSignedUpdates(input,
				commentPrefix);

		Graph graph = buildSignedInputGraph(updates, name);
		MetricBundle metricBundle = buildMetrics(metricSet);
		Algorithms algorithms = initializeMetrics(graph, metricBundle.metrics);
		UndirectedClusteringCoefficientU triangles = metricBundle.triangles;
		UndirectedMotifsU motifs = metricBundle.motifs;
		PrintWriter countsWriter = countsOutput == null ? new PrintWriter(
				System.out, true) : new PrintWriter(new BufferedWriter(
				new FileWriter(countsOutput)));
		PrintWriter timingWriter = timingOutput == null ? null
				: new PrintWriter(new BufferedWriter(new FileWriter(
						timingOutput)));
		PrintWriter statsWriter = statsOutput == null ? null
				: new PrintWriter(new BufferedWriter(new FileWriter(statsOutput)));

		try {
			LongStore overallTimes = statsWriter == null
					|| !timingMode.usesPerUpdateMeasurements() ? null
							: new LongStore();
			LongStore triangleTimes = statsWriter == null
					|| !timingMode.usesPerUpdateMeasurements() ? null
							: new LongStore();
			LongStore motifsTimes = statsWriter == null
					|| !timingMode.usesPerUpdateMeasurements() ? null
							: new LongStore();
			LongStore memoryValues = statsWriter == null
					|| !timingMode.collectsMemory() ? null : new LongStore();
			long cumulativeUpdateTimeNs = 0;
			long runStartNs = System.nanoTime();
			long aggregateStartNs = timingMode == TimingMode.AGGREGATE_ONLY ? System
					.nanoTime() : 0L;
			int step = 1;
			CountRow lastCount = null;
			long lastProcessedTimestamp = 0L;
			boolean completedAllUpdates = true;
			boolean timedOut = false;

			if (timingWriter != null && timingMode.emitsPerUpdateCsv()) {
				timingWriter.println(TIMING_CSV_HEADER);
			}
			countsWriter.println(SIGNED_COUNTS_CSV_HEADER);

			for (SignedEdgeUpdate update : updates) {
				if (maxSteps != null && step > maxSteps.intValue()) {
					completedAllUpdates = false;
					break;
				}
				if (timeoutReached(runStartNs, timeoutNs)) {
					completedAllUpdates = false;
					timedOut = true;
					break;
				}
				if (!isApplicableSignedUpdate(graph, update)) {
					continue;
				}

				Batch batch = new Batch(graph.getGraphDatastructures(), graph
						.getTimestamp(), update.timestamp);
				BatchMeasurement measurement = timingMode.usesPerUpdateMeasurements() ? new BatchMeasurement(
						step, update.timestamp) : null;
				if (measurement != null) {
					measurement.start();
				}
				applyBeforeBatch(algorithms, batch, measurement);
				applySignedUpdate(graph, algorithms, batch, update, measurement);
				finishSignedBatch(graph, algorithms, batch, measurement);
				if (measurement != null) {
					cumulativeUpdateTimeNs += measurement.updateTimeNs;
					measurement.cumulativeUpdateTimeNs = cumulativeUpdateTimeNs;
					if (timingMode.collectsMemory()) {
						measurement.memoryUsedBytes = currentMemoryUsedBytes();
					}
				}
				if (timingWriter != null && measurement != null
						&& timingMode.emitsPerUpdateCsv()) {
					timingWriter.println(measurement.toCsv());
				}
				if (debugRawLimit != null && step <= debugRawLimit.intValue()) {
					printRawDebugRow(step, graph.getTimestamp(), triangles, motifs);
				}
				lastCount = buildCountRow(step, graph.getTimestamp(), triangles,
						motifs);
				lastProcessedTimestamp = lastCount.timestamp;
				writeSignedCountRow(countsWriter, lastCount);
				if (statsWriter != null && measurement != null) {
					overallTimes.add(measurement.updateTimeNs);
					triangleTimes.add(measurement.triangleMetricTimeNs);
					motifsTimes.add(measurement.motifsMetricTimeNs);
					if (memoryValues != null) {
						memoryValues.add(measurement.memoryUsedBytes);
					}
				}
				step++;
			}

			if (statsWriter != null) {
				if (timingMode == TimingMode.AGGREGATE_ONLY) {
					statsWriter.println(AGGREGATE_STATS_CSV_HEADER);
					statsWriter.println(buildAggregateStatsRow(name, metricSet,
							step - 1,
							System.nanoTime() - aggregateStartNs,
							completedAllUpdates, timedOut, timeoutNs,
							lastProcessedTimestamp, lastCount));
				} else {
					statsWriter.println(STATS_CSV_HEADER);
					statsWriter.println(buildStatsRow(name, step - 1, overallTimes,
							triangleTimes, motifsTimes, memoryValues, lastCount,
							timingMode.collectsMemory()));
				}
			}
			if (timedOut) {
				System.err.println(buildTimeoutMessage(timeoutNs, step - 1,
						lastProcessedTimestamp));
			}
		} finally {
			if (countsOutput != null) {
				countsWriter.close();
			} else {
				countsWriter.flush();
			}
			if (timingWriter != null) {
				timingWriter.close();
			}
			if (statsWriter != null) {
				statsWriter.close();
			}
		}
	}

	private static void runBatches(Map<String, String> options) throws Exception {
		Path graphPath = Paths.get(require(options, "--graph")).toAbsolutePath();
		Path batchDir = Paths.get(require(options, "--batches")).toAbsolutePath();

		Graph graph = GraphReader.read(parent(graphPath), graphPath.getFileName()
				.toString());
		List<BatchFile> batchFiles = readBatchFiles(batchDir);

		runCounter(graph, buildMetrics(MetricSet.ALL), new BatchProvider() {
			private int index = 0;

			@Override
			public boolean hasNext(Graph g) {
				return this.index < batchFiles.size();
			}

			@Override
			public Batch next(Graph g) {
				BatchFile batchFile = batchFiles.get(this.index++);
				return BatchReader.read(parent(batchFile.path), batchFile.path
						.getFileName().toString(), g);
			}
		});
	}

	private static Algorithms initializeMetrics(Graph graph, IMetric[] metrics)
			throws MetricNotApplicableException {
		Algorithms algorithms = new Algorithms(metrics);
		for (IMetric metric : metrics) {
			metric.reset();
			metric.setGraph(graph);
			if (!metric.isApplicable(graph)) {
				throw new MetricNotApplicableException(metric, graph);
			}
			if (metric instanceof IDynamicAlgorithm) {
				if (!((IDynamicAlgorithm) metric).init()) {
					throw new IllegalStateException(
							"Failed to initialize metric " + metric.getName());
				}
			} else if (metric instanceof IRecomputation) {
				if (!((IRecomputation) metric).recompute()) {
					throw new IllegalStateException(
							"Failed to compute metric " + metric.getName());
				}
			} else {
				throw new IllegalStateException("Unsupported metric type: "
						+ metric.getClass().getName());
			}
		}
		return algorithms;
	}

	private static void runCounter(Graph graph, MetricBundle metricBundle,
			BatchProvider batches) throws MetricNotApplicableException {
		if (graph.isDirected()) {
			throw new IllegalArgumentException(
					"Only undirected graphs are supported by this runner.");
		}

		Algorithms algorithms = initializeMetrics(graph, metricBundle.metrics);

		UndirectedClusteringCoefficientU triangles = metricBundle.triangles;
		UndirectedMotifsU motifs = metricBundle.motifs;

		System.out.println(CSV_HEADER);
		writeCountRow(new PrintWriter(System.out, true),
				buildCountRow(0, graph.getTimestamp(), triangles, motifs));

		int snapshot = 1;
		while (batches.hasNext(graph)) {
			Batch batch = batches.next(graph);
			if (batch == null || batch.getSize() == 0) {
				continue;
			}
			applyBatch(graph, metricBundle.metrics, algorithms, batch);
			writeCountRow(new PrintWriter(System.out, true), buildCountRow(
					snapshot++, graph.getTimestamp(), triangles, motifs));
		}
	}

	private static void applyBatch(Graph graph, IMetric[] metrics,
			Algorithms algorithms, Batch batch)
			throws MetricNotApplicableException {
		for (IMetric metric : metrics) {
			if (!metric.isApplicable(batch)) {
				throw new MetricNotApplicableException(metric, batch);
			}
		}

		applyBeforeBatch(algorithms, batch, null);

		applyNodeRemovals(graph, algorithms.beforeUpdateNR,
				algorithms.afterUpdateNR, batch.getNodeRemovals(), null);
		applyEdgeRemovals(graph, algorithms.beforeUpdateER,
				algorithms.afterUpdateER, batch.getEdgeRemovals(), null);
		applyNodeAdditions(graph, algorithms.beforeUpdateNA,
				algorithms.afterUpdateNA, batch.getNodeAdditions(), null);
		applyEdgeAdditions(graph, algorithms.beforeUpdateEA,
				algorithms.afterUpdateEA, batch.getEdgeAdditions(), null);
		applyNodeWeights(graph, algorithms.beforeUpdateNW,
				algorithms.afterUpdateNW, batch.getNodeWeights(), null);
		applyEdgeWeights(graph, algorithms.beforeUpdateEW,
				algorithms.afterUpdateEW, batch.getEdgeWeights(), null);

		graph.setTimestamp(batch.getTo());

		applyAfterBatch(algorithms, batch, null);
	}

	private static void applyBeforeBatch(Algorithms algorithms, Batch batch,
			BatchMeasurement timing) {
		for (IBeforeBatch metric : algorithms.beforeBatch) {
			long start = System.nanoTime();
			boolean ok = metric.applyBeforeBatch(batch);
			addMetricTiming(timing, metric, System.nanoTime() - start);
			if (!ok) {
				throw new IllegalStateException(
						"Failed before-batch hook for " + metric.getName());
			}
		}
	}

	private static void applyAfterBatch(Algorithms algorithms, Batch batch,
			BatchMeasurement timing) {
		for (IAfterBatch metric : algorithms.afterBatch) {
			long start = System.nanoTime();
			boolean ok = metric.applyAfterBatch(batch);
			addMetricTiming(timing, metric, System.nanoTime() - start);
			if (!ok) {
				throw new IllegalStateException(
						"Failed after-batch hook for " + metric.getName());
			}
		}
		for (IRecomputation metric : algorithms.recomputation) {
			if (!metric.recompute()) {
				throw new IllegalStateException(
						"Failed recomputation for " + metric.getName());
			}
		}
	}

	private static void applyNodeAdditions(Graph graph, IBeforeNA[] before,
			IAfterNA[] after, Iterable<NodeAddition> updates,
			BatchMeasurement timing) {
		for (NodeAddition update : updates) {
			for (IBeforeNA metric : before) {
				long start = System.nanoTime();
				boolean ok = metric.applyBeforeUpdate(update);
				addMetricTiming(timing, metric, System.nanoTime() - start);
				if (!ok) {
					throw new IllegalStateException(
							"Failed before node-addition hook for "
									+ metric.getName());
				}
			}
			if (!update.apply(graph)) {
				throw new IllegalStateException("Failed to apply update "
						+ update.toString());
			}
			for (IAfterNA metric : after) {
				long start = System.nanoTime();
				boolean ok = metric.applyAfterUpdate(update);
				addMetricTiming(timing, metric, System.nanoTime() - start);
				if (!ok) {
					throw new IllegalStateException(
							"Failed after node-addition hook for "
									+ metric.getName());
				}
			}
		}
	}

	private static void applyNodeRemovals(Graph graph, IBeforeNR[] before,
			IAfterNR[] after, Iterable<NodeRemoval> updates,
			BatchMeasurement timing) {
		for (NodeRemoval update : updates) {
			for (IBeforeNR metric : before) {
				long start = System.nanoTime();
				boolean ok = metric.applyBeforeUpdate(update);
				addMetricTiming(timing, metric, System.nanoTime() - start);
				if (!ok) {
					throw new IllegalStateException(
							"Failed before node-removal hook for "
									+ metric.getName());
				}
			}
			if (!update.apply(graph)) {
				throw new IllegalStateException("Failed to apply update "
						+ update.toString());
			}
			for (IAfterNR metric : after) {
				long start = System.nanoTime();
				boolean ok = metric.applyAfterUpdate(update);
				addMetricTiming(timing, metric, System.nanoTime() - start);
				if (!ok) {
					throw new IllegalStateException(
							"Failed after node-removal hook for "
									+ metric.getName());
				}
			}
		}
	}

	private static void applyNodeWeights(Graph graph, IBeforeNW[] before,
			IAfterNW[] after, Iterable<NodeWeight> updates,
			BatchMeasurement timing) {
		for (NodeWeight update : updates) {
			for (IBeforeNW metric : before) {
				long start = System.nanoTime();
				boolean ok = metric.applyBeforeUpdate(update);
				addMetricTiming(timing, metric, System.nanoTime() - start);
				if (!ok) {
					throw new IllegalStateException(
							"Failed before node-weight hook for "
									+ metric.getName());
				}
			}
			if (!update.apply(graph)) {
				throw new IllegalStateException("Failed to apply update "
						+ update.toString());
			}
			for (IAfterNW metric : after) {
				long start = System.nanoTime();
				boolean ok = metric.applyAfterUpdate(update);
				addMetricTiming(timing, metric, System.nanoTime() - start);
				if (!ok) {
					throw new IllegalStateException(
							"Failed after node-weight hook for "
									+ metric.getName());
				}
			}
		}
	}

	private static void applyEdgeAdditions(Graph graph, IBeforeEA[] before,
			IAfterEA[] after, Iterable<EdgeAddition> updates,
			BatchMeasurement timing) {
		for (EdgeAddition update : updates) {
			for (IBeforeEA metric : before) {
				long start = System.nanoTime();
				boolean ok = metric.applyBeforeUpdate(update);
				addMetricTiming(timing, metric, System.nanoTime() - start);
				if (!ok) {
					throw new IllegalStateException(
							"Failed before edge-addition hook for "
									+ metric.getName());
				}
			}
			if (!update.apply(graph)) {
				throw new IllegalStateException("Failed to apply update "
						+ update.toString());
			}
			for (IAfterEA metric : after) {
				long start = System.nanoTime();
				boolean ok = metric.applyAfterUpdate(update);
				addMetricTiming(timing, metric, System.nanoTime() - start);
				if (!ok) {
					throw new IllegalStateException(
							"Failed after edge-addition hook for "
									+ metric.getName());
				}
			}
		}
	}

	private static void applyEdgeRemovals(Graph graph, IBeforeER[] before,
			IAfterER[] after, Iterable<EdgeRemoval> updates,
			BatchMeasurement timing) {
		for (EdgeRemoval update : updates) {
			for (IBeforeER metric : before) {
				long start = System.nanoTime();
				boolean ok = metric.applyBeforeUpdate(update);
				addMetricTiming(timing, metric, System.nanoTime() - start);
				if (!ok) {
					throw new IllegalStateException(
							"Failed before edge-removal hook for "
									+ metric.getName());
				}
			}
			if (!update.apply(graph)) {
				throw new IllegalStateException("Failed to apply update "
						+ update.toString());
			}
			for (IAfterER metric : after) {
				long start = System.nanoTime();
				boolean ok = metric.applyAfterUpdate(update);
				addMetricTiming(timing, metric, System.nanoTime() - start);
				if (!ok) {
					throw new IllegalStateException(
							"Failed after edge-removal hook for "
									+ metric.getName());
				}
			}
		}
	}

	private static void applyEdgeWeights(Graph graph, IBeforeEW[] before,
			IAfterEW[] after, Iterable<EdgeWeight> updates,
			BatchMeasurement timing) {
		for (EdgeWeight update : updates) {
			for (IBeforeEW metric : before) {
				long start = System.nanoTime();
				boolean ok = metric.applyBeforeUpdate(update);
				addMetricTiming(timing, metric, System.nanoTime() - start);
				if (!ok) {
					throw new IllegalStateException(
							"Failed before edge-weight hook for "
									+ metric.getName());
				}
			}
			if (!update.apply(graph)) {
				throw new IllegalStateException("Failed to apply update "
						+ update.toString());
			}
			for (IAfterEW metric : after) {
				long start = System.nanoTime();
				boolean ok = metric.applyAfterUpdate(update);
				addMetricTiming(timing, metric, System.nanoTime() - start);
				if (!ok) {
					throw new IllegalStateException(
							"Failed after edge-weight hook for "
									+ metric.getName());
				}
			}
		}
	}

	private static CountRow buildCountRow(int snapshot, long timestamp,
			UndirectedClusteringCoefficientU triangles, UndirectedMotifsU motifs) {
		long triangleCount = triangles == null ? 0L : Math.round(getValue(
				triangles, "triangleCount") / 3.0);
		long um1 = motifs == null ? 0L : Math.round(getValue(motifs, "UM1"));
		long um2 = motifs == null ? 0L : Math.round(getValue(motifs, "UM2"));
		long um3 = motifs == null ? 0L : Math.round(getValue(motifs, "UM3"));
		long um4 = motifs == null ? 0L : Math.round(getValue(motifs, "UM4"));
		long um5 = motifs == null ? 0L : Math.round(getValue(motifs, "UM5"));
		long um6 = motifs == null ? 0L : Math.round(getValue(motifs, "UM6"));
		long fourCycles = um3 + um5 + (3L * um6);
		long diamonds = um5 + (6L * um6);
		long paws = um4 + (4L * um5) + (12L * um6);
		long length3Paths = um1 + (4L * um3) + (2L * um4) + (6L * um5)
				+ (12L * um6);
		long claws = um2 + um4 + (2L * um5) + (4L * um6);
		long fourCliques = um6;
		return new CountRow(snapshot, timestamp, triangleCount, fourCycles,
				diamonds, paws, length3Paths, claws, fourCliques);
	}

	private static void writeCountRow(PrintWriter writer, CountRow row) {
		writer.println(row.toCsv());
	}

	private static void writeSignedCountRow(PrintWriter writer, CountRow row) {
		writer.println(row.toSignedCountsCsv());
	}

	private static void printRawDebugRow(int step, long timestamp,
			UndirectedClusteringCoefficientU triangles, UndirectedMotifsU motifs) {
		long triangleCount = triangles == null ? 0L : Math.round(getValue(
				triangles, "triangleCount") / 3.0);
		long um1 = motifs == null ? 0L : Math.round(getValue(motifs, "UM1"));
		long um2 = motifs == null ? 0L : Math.round(getValue(motifs, "UM2"));
		long um3 = motifs == null ? 0L : Math.round(getValue(motifs, "UM3"));
		long um4 = motifs == null ? 0L : Math.round(getValue(motifs, "UM4"));
		long um5 = motifs == null ? 0L : Math.round(getValue(motifs, "UM5"));
		long um6 = motifs == null ? 0L : Math.round(getValue(motifs, "UM6"));
		System.err.println("RAW step=" + step + " timestamp=" + timestamp
				+ " triangles=" + triangleCount + " UM1=" + um1 + " UM2="
				+ um2 + " UM3=" + um3 + " UM4=" + um4 + " UM5=" + um5
				+ " UM6=" + um6);
	}

	private static double getValue(IMetric metric, String name) {
		for (Value value : metric.getValues()) {
			if (name.equals(value.getName())) {
				return value.getValue();
			}
		}
		throw new IllegalArgumentException("Metric " + metric.getName()
				+ " does not provide value " + name);
	}

	private static MetricBundle buildMetrics(MetricSet metricSet) {
		ArrayList<IMetric> metrics = new ArrayList<IMetric>();
		UndirectedClusteringCoefficientU triangles = null;
		UndirectedMotifsU motifs = null;

		if (metricSet.includesTriangles()) {
			triangles = new UndirectedClusteringCoefficientU();
			metrics.add(triangles);
		}
		if (metricSet.includesMotifs()) {
			motifs = new UndirectedMotifsU();
			metrics.add(motifs);
		}

		return new MetricBundle(metrics.toArray(new IMetric[metrics.size()]),
				triangles, motifs);
	}

	private static ArrayList<SignedEdgeUpdate> readSignedUpdates(Path input,
			String commentPrefix) throws IOException {
		ArrayList<SignedEdgeUpdate> updates = new ArrayList<SignedEdgeUpdate>();
		try (BufferedReader reader = new BufferedReader(new FileReader(input
				.toFile()))) {
			String line;
			int order = 0;
			while ((line = reader.readLine()) != null) {
				SignedEdgeUpdate update = parseSignedLine(line, commentPrefix,
						order++);
				if (update != null) {
					updates.add(update);
				}
			}
		}
		updates.sort(Comparator.comparingLong((SignedEdgeUpdate update) -> update.timestamp)
				.thenComparingInt(update -> update.order));
		return updates;
	}

	private static Graph buildSignedInputGraph(
			List<SignedEdgeUpdate> updates, String name) throws IOException {
		HashMap<Integer, Boolean> nodeIds = new HashMap<Integer, Boolean>();
		for (SignedEdgeUpdate update : updates) {
			nodeIds.put(update.src, Boolean.TRUE);
			nodeIds.put(update.dst, Boolean.TRUE);
		}

		Graph graph = GDS.undirected().newGraphInstance(name, 0, nodeIds.size(),
				0);
		for (Integer nodeId : nodeIds.keySet()) {
			graph.addNode(graph.getGraphDatastructures().newNodeInstance(nodeId));
		}
		return graph;
	}

	private static SignedEdgeUpdate parseSignedLine(String line,
			String commentPrefix, int order) {
		String trimmed = line.trim();
		if (trimmed.length() == 0 || trimmed.startsWith(commentPrefix)) {
			return null;
		}

		String[] parts = trimmed.split("\\s+");
		if (parts.length != 4) {
			throw new IllegalArgumentException("Expected 4 columns, got: "
					+ line);
		}

		int src = Integer.parseInt(parts[0]);
		int dst = Integer.parseInt(parts[1]);
		String operation = parts[2];
		long timestamp = Long.parseLong(parts[3]);

		if (!"+1".equals(operation) && !"-1".equals(operation)) {
			throw new IllegalArgumentException("Expected +1 or -1, got: "
					+ operation);
		}

		if (src > dst) {
			int tmp = src;
			src = dst;
			dst = tmp;
		}

		if (src == dst) {
			return null;
		}

		return new SignedEdgeUpdate(src, dst, operation, timestamp, order);
	}

	private static void applySignedUpdate(Graph graph, Algorithms algorithms,
			Batch batch, SignedEdgeUpdate update, BatchMeasurement timing) {
		if ("+1".equals(update.operation)) {
			EdgeAddition addition = new EdgeAddition(graph
					.getGraphDatastructures().newEdgeInstance(graph.getNode(
							update.src), graph.getNode(update.dst)));
			batch.add(addition);
			applyEdgeAdditions(graph, algorithms.beforeUpdateEA,
					algorithms.afterUpdateEA, java.util.Collections
							.singletonList(addition), timing);
		} else {
			EdgeRemoval removal = new EdgeRemoval(update.src, update.dst, graph
					.getGraphDatastructures(), graph);
			batch.add(removal);
			applyEdgeRemovals(graph, algorithms.beforeUpdateER,
					algorithms.afterUpdateER, java.util.Collections
							.singletonList(removal), timing);
		}
	}

	private static void finishSignedBatch(Graph graph, Algorithms algorithms,
			Batch batch, BatchMeasurement timing) {
		graph.setTimestamp(batch.getTo());
		applyAfterBatch(algorithms, batch, timing);
		if (timing != null) {
			timing.finish();
		}
	}

	private static boolean isApplicableSignedUpdate(Graph graph,
			SignedEdgeUpdate update) {
		if ("+1".equals(update.operation)) {
			return !graph.containsEdge(update.src, update.dst);
		}
		return graph.containsEdge(update.src, update.dst);
	}

	private static long currentMemoryUsedBytes() {
		Runtime runtime = Runtime.getRuntime();
		return runtime.totalMemory() - runtime.freeMemory();
	}

	private static TimingMode parseTimingMode(String value,
			boolean timingRequested) {
		if (value == null) {
			return timingRequested ? TimingMode.PER_UPDATE : TimingMode.OFF;
		}
		String normalized = value.trim().replace('-', '_').toUpperCase();
		return TimingMode.valueOf(normalized);
	}

	private static void validateTimingConfiguration(TimingMode timingMode,
			String timingOutput, String statsOutput) {
		if (timingMode == TimingMode.OFF) {
			if (timingOutput != null || statsOutput != null) {
				throw new IllegalArgumentException(
						"--timing-mode off cannot be combined with --timing-output or --stats-output");
			}
			return;
		}
		if (timingMode == TimingMode.AGGREGATE_ONLY && timingOutput != null) {
			throw new IllegalArgumentException(
					"--timing-mode aggregate-only does not support --timing-output");
		}
	}

	private static void addMetricTiming(BatchMeasurement timing,
			IDynamicAlgorithm metric, long durationNs) {
		if (timing != null) {
			timing.addMetricTime(metric.getName(), durationNs);
		}
	}

	private static String buildStatsRow(String graphName, int steps,
			LongStore overallTimes, LongStore triangleTimes,
			LongStore motifsTimes, LongStore memoryValues, CountRow lastCount,
			boolean memoryEnabled) {
		DistributionStats overall = DistributionStats.fromLongStore(
				overallTimes);
		DistributionStats triangle = DistributionStats.fromLongStore(
				triangleTimes);
		DistributionStats motifs = DistributionStats.fromLongStore(motifsTimes);
		DistributionStats memory = DistributionStats.fromLongStore(memoryValues);
		CountRow finalCount = lastCount == null ? new CountRow(0, 0, 0, 0, 0, 0,
				0, 0, 0) : lastCount;

		StringBuilder row = new StringBuilder();
		appendCsv(row, graphName);
		appendCsv(row, steps);
		appendCsv(row, formatSeconds(overall.total));
		appendCsv(row, formatSeconds(overall.mean));
		appendCsv(row, formatSeconds(overall.min));
		appendCsv(row, formatSeconds(overall.max));
		appendCsv(row, formatSeconds(overall.p1));
		appendCsv(row, formatSeconds(overall.p99));
		appendCsv(row, formatSeconds(triangle.total));
		appendCsv(row, formatSeconds(triangle.mean));
		appendCsv(row, formatSeconds(triangle.min));
		appendCsv(row, formatSeconds(triangle.max));
		appendCsv(row, formatSeconds(triangle.p1));
		appendCsv(row, formatSeconds(triangle.p99));
		appendCsv(row, formatSeconds(motifs.total));
		appendCsv(row, formatSeconds(motifs.mean));
		appendCsv(row, formatSeconds(motifs.min));
		appendCsv(row, formatSeconds(motifs.max));
		appendCsv(row, formatSeconds(motifs.p1));
		appendCsv(row, formatSeconds(motifs.p99));
		appendCsv(row, finalCount.triangles);
		appendCsv(row, finalCount.fourCycles);
		appendCsv(row, finalCount.diamonds);
		appendCsv(row, finalCount.paws);
		appendCsv(row, finalCount.length3Paths);
		appendCsv(row, finalCount.claws);
		appendCsv(row, finalCount.fourCliques);
		appendCsv(row, memoryEnabled ? Long.valueOf(memoryValues.size() == 0 ? 0
				: memoryValues.get(memoryValues.size() - 1)) : null);
		appendCsv(row, memoryEnabled ? formatDouble(memory.mean) : null);
		appendCsv(row, memoryEnabled ? Long.valueOf(memory.min) : null);
		appendCsv(row, memoryEnabled ? Long.valueOf(memory.max) : null);
		appendCsv(row, memoryEnabled ? Long.valueOf(memory.p1) : null);
		appendCsv(row, memoryEnabled ? Long.valueOf(memory.p99) : null);
		appendCsv(row, false);
		appendCsv(row, buildTimingNote(memoryEnabled));
		return row.toString();
	}

	private static String buildAggregateStatsRow(String graphName,
			MetricSet metricSet, int steps,
			long totalUpdateTimeNs, boolean completedAllUpdates,
			boolean timedOut, Long timeoutNs, long lastProcessedTimestamp,
			CountRow lastCount) {
		CountRow finalCount = lastCount == null ? new CountRow(0, 0, 0, 0, 0, 0,
				0, 0, 0) : lastCount;

		StringBuilder row = new StringBuilder();
		appendCsv(row, graphName);
		appendCsv(row, metricSet.csvValue);
		appendCsv(row, steps);
		appendCsv(row, formatSeconds(totalUpdateTimeNs));
		appendCsv(row, completedAllUpdates);
		appendCsv(row, timedOut);
		appendCsv(row, timeoutNs == null ? null : formatSeconds(timeoutNs
				.longValue()));
		appendCsv(row, lastProcessedTimestamp);
		appendCsv(row, finalCount.triangles);
		appendCsv(row, finalCount.fourCycles);
		appendCsv(row, finalCount.diamonds);
		appendCsv(row, finalCount.paws);
		appendCsv(row, finalCount.length3Paths);
		appendCsv(row, finalCount.claws);
		appendCsv(row, finalCount.fourCliques);
		appendCsv(row, false);
		appendCsv(row, buildAggregateTimingNote(metricSet));
		return row.toString();
	}

	private static String buildAggregateTimingNote(MetricSet metricSet) {
		if (metricSet == MetricSet.TRIANGLES) {
			return "Only whole-run triangle update time is measured in aggregate-only mode. Four-node motif maintenance is not instantiated.";
		}
		if (metricSet == MetricSet.FOUR_NODE) {
			return "Only whole-run four-node motif update time is measured in aggregate-only mode. Triangle maintenance is not instantiated.";
		}
		return "Only whole-run update time is measured in aggregate-only mode to minimize timing overhead.";
	}

	private static String buildTimingNote(boolean memoryEnabled) {
		String note = "Only triangle timing is isolated exactly. Claws, four-cycles, diamonds, paws, length-3 paths, and four-cliques are maintained together inside UndirectedMotifsU.";
		if (!memoryEnabled) {
			note += " Memory sampling disabled by timing mode.";
		}
		return note;
	}

	private static void appendCsv(StringBuilder row, Object value) {
		if (row.length() > 0) {
			row.append(",");
		}
		if (value == null) {
			return;
		}
		String s = String.valueOf(value);
		if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0) {
			row.append('"').append(s.replace("\"", "\"\"")).append('"');
		} else {
			row.append(s);
		}
	}

	private static String formatDouble(double value) {
		return Double.toString(value);
	}

	private static String formatSeconds(long nanos) {
		return formatDouble(nanosToSeconds(nanos));
	}

	private static String formatSeconds(double nanos) {
		return formatDouble(nanosToSeconds(nanos));
	}

	private static long secondsToNanos(double seconds) {
		if (Double.isNaN(seconds) || Double.isInfinite(seconds)
				|| seconds <= 0.0) {
			throw new IllegalArgumentException(
					"--timeout-seconds must be greater than zero");
		}
		return Math.round(seconds * 1_000_000_000.0);
	}

	private static double nanosToSeconds(long nanos) {
		return ((double) nanos) / 1_000_000_000.0;
	}

	private static double nanosToSeconds(double nanos) {
		return nanos / 1_000_000_000.0;
	}

	private static List<BatchFile> readBatchFiles(Path batchDir)
			throws IOException {
		if (!Files.isDirectory(batchDir)) {
			throw new IllegalArgumentException("Not a directory: " + batchDir);
		}

		List<BatchFile> files = new ArrayList<BatchFile>();
		for (Path path : Files.newDirectoryStream(batchDir)) {
			if (!Files.isRegularFile(path)) {
				continue;
			}
			if (!path.getFileName().toString().startsWith("batch")) {
				continue;
			}
			long[] timestamps = BatchReader.readTimestamps(parent(path), path
					.getFileName().toString());
			files.add(new BatchFile(path, timestamps[0], timestamps[1]));
		}
		files.sort(Comparator.comparingLong((BatchFile file) -> file.from)
				.thenComparingLong(file -> file.to)
				.thenComparing(file -> file.path.getFileName().toString()));
		return files;
	}

	private static String require(Map<String, String> options, String key) {
		String value = options.get(key);
		if (value == null) {
			throw new IllegalArgumentException("Missing required option " + key);
		}
		return value;
	}

	private static Map<String, String> parseOptions(String[] args, int start) {
		Map<String, String> options = new HashMap<String, String>();
		for (int i = start; i < args.length; i++) {
			String key = args[i];
			if (!key.startsWith("--")) {
				throw new IllegalArgumentException("Expected option, got: "
						+ key);
			}
			if (i + 1 >= args.length) {
				throw new IllegalArgumentException("Missing value for " + key);
			}
			options.put(key, args[++i]);
		}
		return options;
	}

	private static String unescape(String value) {
		return value.replace("\\t", "\t").replace("\\n", "\n")
				.replace("\\r", "\r");
	}

	private static String parent(Path path) {
		Path parent = path.getParent();
		String dir = parent == null ? "." : parent.toString();
		if (dir.endsWith(File.separator)) {
			return dir;
		}
		return dir + File.separator;
	}

	private static void usage() {
		System.err.println("Usage:");
		System.err.println("  signed mode for '<src> <dst> +1/-1 timestamp' input:");
		System.err.println("    java -cp \"bin:lib/*\" dna.tools.DynamicUndirectedSubgraphCounter signed \\");
		System.err.println("      --input /path/to/link-dynamic-simplewiki [--name graph] [--comment-prefix %] \\");
		System.err.println("      [--counts-output counts.csv] [--timing-output timing.csv] [--stats-output stats.csv] \\");
		System.err.println("      [--timing-mode off|aggregate-only|per-update|per-update-no-memory] \\");
		System.err.println("      [--timeout-seconds 60.0] [--metric-set all|triangles|four-node]");
		System.err.println("  timestamped mode:");
		System.err.println("    java -cp \"bin:lib/*\" dna.tools.DynamicUndirectedSubgraphCounter timestamped \\");
		System.err.println("      --input /path/to/edges.csv --batch-type TIMESTAMP_INTERVAL --batch-param 1 \\");
		System.err.println("      [--name graph] [--separator ,] [--comment-prefix %] [--remap true] \\");
		System.err.println("      [--initial-type EDGE_COUNT|TIMESTAMP] [--initial-param 0] [--max-timestamp 100]");
		System.err.println("  native batch mode:");
		System.err.println("    java -cp \"bin:lib/*\" dna.tools.DynamicUndirectedSubgraphCounter batches \\");
		System.err.println("      --graph /path/to/graph.txt --batches /path/to/batch-files");
	}

	private enum TimingMode {
		OFF,
		AGGREGATE_ONLY,
		PER_UPDATE,
		PER_UPDATE_NO_MEMORY;

		private boolean isEnabled() {
			return this != OFF;
		}

		private boolean emitsPerUpdateCsv() {
			return this == PER_UPDATE || this == PER_UPDATE_NO_MEMORY;
		}

		private boolean usesPerUpdateMeasurements() {
			return this == PER_UPDATE || this == PER_UPDATE_NO_MEMORY;
		}

		private boolean collectsMemory() {
			return this == PER_UPDATE;
		}
	}

	private enum MetricSet {
		ALL("all"),
		TRIANGLES("triangles"),
		FOUR_NODE("four-node");

		private final String csvValue;

		private MetricSet(String csvValue) {
			this.csvValue = csvValue;
		}

		private boolean includesTriangles() {
			return this == ALL || this == TRIANGLES;
		}

		private boolean includesMotifs() {
			return this == ALL || this == FOUR_NODE;
		}
	}

	private static MetricSet parseMetricSet(String value) {
		if (value == null) {
			return MetricSet.ALL;
		}
		String normalized = value.trim().replace('_', '-').toLowerCase();
		if ("all".equals(normalized)) {
			return MetricSet.ALL;
		}
		if ("triangle".equals(normalized) || "triangles".equals(normalized)) {
			return MetricSet.TRIANGLES;
		}
		if ("four-node".equals(normalized) || "four-nodes".equals(normalized)
				|| "motifs".equals(normalized) || "four-node-patterns".equals(
						normalized)) {
			return MetricSet.FOUR_NODE;
		}
		throw new IllegalArgumentException("Unsupported --metric-set: " + value);
	}

	private static boolean timeoutReached(long startedAtNs, Long timeoutNs) {
		return timeoutNs != null && System.nanoTime() - startedAtNs >= timeoutNs
				.longValue();
	}

	private static String buildTimeoutMessage(Long timeoutNs, int processedSteps,
			long lastProcessedTimestamp) {
		String timeoutValue = timeoutNs == null ? "" : formatSeconds(timeoutNs
				.longValue());
		return "TIMEOUT processed_updates=" + processedSteps
				+ " last_processed_timestamp=" + lastProcessedTimestamp
				+ " timeout_seconds=" + timeoutValue;
	}

	private interface BatchProvider {
		boolean hasNext(Graph g);

		Batch next(Graph g);
	}

	private static class BatchFile {
		private final Path path;
		private final long from;
		private final long to;

		private BatchFile(Path path, long from, long to) {
			this.path = path;
			this.from = from;
			this.to = to;
		}
	}

	private static class SignedEdgeUpdate {
		private final int src;
		private final int dst;
		private final String operation;
		private final long timestamp;
		private final int order;

		private SignedEdgeUpdate(int src, int dst, String operation,
				long timestamp, int order) {
			this.src = src;
			this.dst = dst;
			this.operation = operation;
			this.timestamp = timestamp;
			this.order = order;
		}
	}

	private static class CountRow {
		private final int snapshot;
		private final long timestamp;
		private final long triangles;
		private final long fourCycles;
		private final long diamonds;
		private final long paws;
		private final long length3Paths;
		private final long claws;
		private final long fourCliques;

		private CountRow(int snapshot, long timestamp, long triangles,
				long fourCycles, long diamonds, long paws, long length3Paths,
				long claws, long fourCliques) {
			this.snapshot = snapshot;
			this.timestamp = timestamp;
			this.triangles = triangles;
			this.fourCycles = fourCycles;
			this.diamonds = diamonds;
			this.paws = paws;
			this.length3Paths = length3Paths;
			this.claws = claws;
			this.fourCliques = fourCliques;
		}

		private String toCsv() {
			return this.snapshot + "," + this.timestamp + ","
					+ this.triangles + "," + this.fourCycles + ","
					+ this.diamonds + "," + this.paws + ","
					+ this.length3Paths + "," + this.claws + ","
					+ this.fourCliques;
		}

		private String toSignedCountsCsv() {
			return this.snapshot + "," + this.triangles + ","
					+ this.diamonds + "," + this.length3Paths + ","
					+ this.fourCycles + "," + this.claws + ","
					+ this.fourCliques + "," + this.paws;
		}
	}

	private static class MetricBundle {
		private final IMetric[] metrics;
		private final UndirectedClusteringCoefficientU triangles;
		private final UndirectedMotifsU motifs;

		private MetricBundle(IMetric[] metrics,
				UndirectedClusteringCoefficientU triangles,
				UndirectedMotifsU motifs) {
			this.metrics = metrics;
			this.triangles = triangles;
			this.motifs = motifs;
		}
	}

	private static class BatchMeasurement {
		private final int snapshot;
		private final long timestamp;
		private long updateTimeNs;
		private long cumulativeUpdateTimeNs;
		private long triangleMetricTimeNs;
		private long motifsMetricTimeNs;
		private long memoryUsedBytes;
		private long startNs;

		private BatchMeasurement(int snapshot, long timestamp) {
			this.snapshot = snapshot;
			this.timestamp = timestamp;
		}

		private void start() {
			this.startNs = System.nanoTime();
		}

		private void finish() {
			this.updateTimeNs = System.nanoTime() - this.startNs;
		}

		private void addMetricTime(String metricName, long durationNs) {
			if ("UndirectedClusteringCoefficientU".equals(metricName)) {
				this.triangleMetricTimeNs += durationNs;
			} else if ("UndirectedMotifsU".equals(metricName)) {
				this.motifsMetricTimeNs += durationNs;
			}
		}

		private String toCsv() {
			return this.snapshot + "," + this.timestamp + ","
					+ formatSeconds(this.updateTimeNs) + ","
					+ formatSeconds(this.cumulativeUpdateTimeNs) + ","
					+ formatSeconds(this.triangleMetricTimeNs) + ","
					+ formatSeconds(this.motifsMetricTimeNs) + ","
					+ this.memoryUsedBytes;
		}
	}

	private static class LongStore {
		private long[] values;
		private int size;

		private LongStore() {
			this.values = new long[1024];
			this.size = 0;
		}

		private void add(long value) {
			if (this.size == this.values.length) {
				long[] resized = new long[this.values.length * 2];
				System.arraycopy(this.values, 0, resized, 0, this.size);
				this.values = resized;
			}
			this.values[this.size++] = value;
		}

		private long get(int index) {
			if (index < 0 || index >= this.size) {
				throw new IndexOutOfBoundsException(String.valueOf(index));
			}
			return this.values[index];
		}

		private int size() {
			return this.size;
		}

		private long[] sortedCopy() {
			long[] copy = new long[this.size];
			System.arraycopy(this.values, 0, copy, 0, this.size);
			java.util.Arrays.sort(copy);
			return copy;
		}
	}

	private static class DistributionStats {
		private final long total;
		private final long min;
		private final long max;
		private final long p99;
		private final long p1;
		private final double mean;

		private DistributionStats(long total, long min, long max, long p99,
				long p1, double mean) {
			this.total = total;
			this.min = min;
			this.max = max;
			this.p99 = p99;
			this.p1 = p1;
			this.mean = mean;
		}

		private static DistributionStats fromLongStore(LongStore values) {
			if (values == null || values.size() == 0) {
				return new DistributionStats(0, 0, 0, 0, 0, 0.0);
			}

			long total = 0;
			long min = Long.MAX_VALUE;
			long max = Long.MIN_VALUE;
			for (int i = 0; i < values.size(); i++) {
				long value = values.get(i);
				total += value;
				min = Math.min(min, value);
				max = Math.max(max, value);
			}
			long[] sorted = values.sortedCopy();
			long p1 = sorted[percentileIndex(sorted.length, 0.01)];
			long p99 = sorted[percentileIndex(sorted.length, 0.99)];
			double mean = ((double) total) / values.size();
			return new DistributionStats(total, min, max, p99, p1, mean);
		}

		private static int percentileIndex(int size, double quantile) {
			if (size <= 1) {
				return 0;
			}
			int index = (int) Math.floor((size - 1) * quantile);
			if (index < 0) {
				return 0;
			}
			if (index >= size) {
				return size - 1;
			}
			return index;
		}
	}
}
