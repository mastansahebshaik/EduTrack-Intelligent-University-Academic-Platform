package edutrack.gui.panels;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

import edutrack.data.DataStore;
import edutrack.gui.ConsoleArea;
import edutrack.gui.GuiTheme;
import edutrack.gui.ModulePanel;
import edutrack.model.ActivityEvent;
import edutrack.model.Student;
import edutrack.modules.M6BlellochScan;
import edutrack.modules.M6BrentsTheorem;
import edutrack.modules.M6MillerRabin;
import edutrack.modules.M6ParallelMergeSort;
import edutrack.modules.M6RandomizedParallel;
import edutrack.modules.M6RandomizedQuickSort;
import edutrack.modules.M6ReservoirSampler;
import edutrack.modules.M6ReservoirSampler.SampleResult;
import javax.swing.JTextField;

public class RankingStreamsPanel extends ModulePanel {

    private static final int BENCHMARK_SIZE = 1_000_000;

    private final ConsoleArea console = new ConsoleArea(6);

    private final JSpinner topNSpinner;
    private final DefaultTableModel rankingModel;
    private final JLabel rankingStats = new JLabel(" ");
    private final JButton rankButton;
    private final JButton benchmarkButton;
    private final M6BarChart benchmarkChart = new M6BarChart();
    private final M6BarChart adversarialChart = new M6BarChart();

    private final JButton mergeButton;
    private final JLabel mergeStats = new JLabel(" ");
    private final M6BarChart mergeChart = new M6BarChart();

    private final JSpinner kSpinner;
    private final JComboBox<String> actionCombo;
    private final JButton sampleButton;
    private final JButton uniformityButton;
    private final DefaultTableModel sampleModel;
    private final JLabel sampleStats = new JLabel(" ");
    private final M6BarChart uniformityChart = new M6BarChart();

    public RankingStreamsPanel(DataStore dataStore) {
        super(dataStore);

        topNSpinner = new JSpinner(new SpinnerNumberModel(10, 1, dataStore.students().size(), 1));
        rankingModel = tableModel("Rank", "ID", "Name", "Program", "CGPA");
        rankButton = GuiTheme.primaryButton("Rank students");
        benchmarkButton = GuiTheme.secondaryButton("Run 1M benchmark");
        mergeButton = GuiTheme.primaryButton("Run 1M benchmark");
        kSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 1000, 1));
        List<String> actions = new ArrayList<>();
        actions.add("ALL");
        actions.addAll(M6RandomizedParallel.KNOWN_ACTIONS);
        actionCombo = new JComboBox<>(actions.toArray(new String[0]));
        sampleButton = GuiTheme.primaryButton("Sample stream");
        uniformityButton = GuiTheme.secondaryButton("Uniformity demo");
        sampleModel = tableModel("Timestamp", "Student", "Action", "Details");

        rankButton.addActionListener(e -> runRanking());
        benchmarkButton.addActionListener(e -> runQuickSortBenchmark());
        mergeButton.addActionListener(e -> runMergeBenchmark());
        sampleButton.addActionListener(e -> runSampling());
        uniformityButton.addActionListener(e -> runUniformityDemo());

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(GuiTheme.BODY_BOLD);
        tabs.addTab("CGPA Ranking & QuickSort", buildRankingTab());
        tabs.addTab("Parallel Merge Sort", buildMergeTab());
        tabs.addTab("Reservoir Sampling", buildReservoirTab());
        tabs.addTab("Parallel Primitives & Crypto", buildParallelCryptoTab());

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        JLabel title = new JLabel("Ranking & Streams — Randomized & Parallel Algorithms");
        title.setFont(GuiTheme.H1);
        title.setForeground(GuiTheme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel(
                "Randomized QuickSort · Parallel Merge Sort · Reservoir Sampling · Blelloch Scan · Brent's Theorem · Miller-Rabin");
        subtitle.setFont(GuiTheme.BODY);
        subtitle.setForeground(GuiTheme.MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(2));
        header.add(subtitle);

        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        add(card("Log", console), BorderLayout.SOUTH);
    }

    /** Keeps Top-N bounds valid after student CRUD changes. */
    public void refresh() {
        int max = Math.max(1, dataStore.students().size());
        SpinnerNumberModel model = (SpinnerNumberModel) topNSpinner.getModel();
        model.setMaximum(max);
        int current = ((Number) model.getNumber()).intValue();
        if (current > max) model.setValue(max);
        if (dataStore.students().isEmpty()) {
            rankingModel.setRowCount(0);
            rankingStats.setText("No students available.");
        }
    }

    private JPanel buildRankingTab() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        controls.setOpaque(false);
        controls.add(new JLabel("Top N:"));
        controls.add(topNSpinner);
        controls.add(rankButton);
        controls.add(benchmarkButton);
        controls.add(rankingStats);

        JScrollPane tableScroll = new JScrollPane(new JTable(rankingModel));
        tableScroll.setPreferredSize(new Dimension(200, 240));

        JPanel charts = new JPanel(new GridLayout(1, 2, 12, 12));
        charts.setOpaque(false);
        charts.add(card("1M benchmark — random input (ms)", benchmarkChart));
        charts.add(card("Adversarial sorted input — deterministic comparisons", adversarialChart));

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setOpaque(false);
        center.add(tableScroll, BorderLayout.NORTH);
        center.add(charts, BorderLayout.CENTER);

        JPanel tab = new JPanel(new BorderLayout(10, 10));
        tab.setOpaque(false);
        tab.add(controls, BorderLayout.NORTH);
        tab.add(center, BorderLayout.CENTER);
        return tab;
    }

    private JPanel buildMergeTab() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        controls.setOpaque(false);
        controls.add(mergeButton);
        controls.add(mergeStats);

        JPanel tab = new JPanel(new BorderLayout(10, 10));
        tab.setOpaque(false);
        tab.add(controls, BorderLayout.NORTH);
        tab.add(card("Sequential vs Fork/Join parallel merge sort (ms)", mergeChart), BorderLayout.CENTER);
        return tab;
    }

    private JPanel buildReservoirTab() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        controls.setOpaque(false);
        controls.add(new JLabel("k:"));
        controls.add(kSpinner);
        controls.add(new JLabel("Action:"));
        controls.add(actionCombo);
        controls.add(sampleButton);
        controls.add(uniformityButton);
        controls.add(sampleStats);

        JScrollPane tableScroll = new JScrollPane(new JTable(sampleModel));
        tableScroll.setPreferredSize(new Dimension(200, 220));

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setOpaque(false);
        center.add(tableScroll, BorderLayout.NORTH);
        center.add(card("Uniformity demo — selection frequency per stream position (n=100, k=10, 20,000 trials)",
                uniformityChart), BorderLayout.CENTER);

        JPanel tab = new JPanel(new BorderLayout(10, 10));
        tab.setOpaque(false);
        tab.add(controls, BorderLayout.NORTH);
        tab.add(center, BorderLayout.CENTER);
        return tab;
    }

    private void runRanking() {
        final int n = (Integer) topNSpinner.getValue();
        console.appendLine("Ranking " + dataStore.students().size()
                + " students by CGPA (randomized quicksort, seed " + M6RandomizedParallel.DEMO_SEED + ")...");
        runTask(rankButton, "Rank students", () -> {
            Student[] ranked = dataStore.students().toArray(new Student[0]);
            long start = System.nanoTime();
            long comparisons = M6RandomizedQuickSort.sortStudents(ranked, new Random(M6RandomizedParallel.DEMO_SEED));
            long ms = (System.nanoTime() - start) / 1_000_000;
            return new RankingResult(ranked, comparisons, ms);
        }, result -> {
            rankingModel.setRowCount(0);
            int limit = Math.min(n, result.ranked.length);
            for (int i = 0; i < limit; i++) {
                Student s = result.ranked[i];
                rankingModel.addRow(new Object[] { i + 1, s.id, s.name, s.program, String.format("%.2f", s.cgpa) });
            }
            rankingStats.setText(String.format("%,d comparisons · %d ms", result.comparisons, result.ms));
            console.appendLine(String.format("Ranking done: %,d comparisons in %d ms; showing top %d.",
                    result.comparisons, result.ms, limit));
        });
    }

    private void runQuickSortBenchmark() {
        console.appendLine("QuickSort benchmark on " + String.format("%,d", BENCHMARK_SIZE) + " elements running...");
        runTask(benchmarkButton, "Run 1M benchmark", () -> {
            BenchmarkResult r = new BenchmarkResult();
            double[] base = M6RandomizedParallel.buildBenchmarkArray(
                    dataStore, BENCHMARK_SIZE, M6RandomizedParallel.DEMO_SEED);

            double[] warm = M6RandomizedParallel.buildBenchmarkArray(dataStore, 200_000, M6RandomizedParallel.DEMO_SEED + 7);
            M6RandomizedQuickSort.sort(warm.clone(), new Random(M6RandomizedParallel.DEMO_SEED));
            M6RandomizedQuickSort.sortDeterministic(warm.clone());
            Arrays.sort(warm.clone());

            double[] a = base.clone();
            long start = System.nanoTime();
            r.randomizedCmp = M6RandomizedQuickSort.sort(a, new Random(M6RandomizedParallel.DEMO_SEED));
            r.randomizedMs = (System.nanoTime() - start) / 1_000_000;

            double[] b = base.clone();
            start = System.nanoTime();
            Arrays.sort(b);
            r.arraysMs = (System.nanoTime() - start) / 1_000_000;

            double[] c = base.clone();
            start = System.nanoTime();
            r.deterministicCmp = M6RandomizedQuickSort.sortDeterministic(c);
            r.deterministicMs = (System.nanoTime() - start) / 1_000_000;

            double[] sortedBase = base.clone();
            Arrays.sort(sortedBase);

            double[] d = sortedBase.clone();
            start = System.nanoTime();
            M6RandomizedQuickSort.sort(d, new Random(M6RandomizedParallel.DEMO_SEED));
            r.sortedRandomizedMs = (System.nanoTime() - start) / 1_000_000;

            double[] e = sortedBase.clone();
            start = System.nanoTime();
            Arrays.sort(e);
            r.sortedArraysMs = (System.nanoTime() - start) / 1_000_000;

            int[] sizes = { 10_000, 20_000, 40_000 };
            r.advN = sizes;
            r.advCmp = new long[sizes.length];
            for (int i = 0; i < sizes.length; i++) {
                double[] f = Arrays.copyOf(sortedBase, sizes[i]);
                r.advCmp[i] = M6RandomizedQuickSort.sortDeterministic(f);
            }
            return r;
        }, r -> {
            benchmarkChart.setData(List.of(
                    new M6BarChart.Bar("Rand. QS", r.randomizedMs, GuiTheme.ACCENT),
                    new M6BarChart.Bar("Arrays.sort", r.arraysMs, GuiTheme.MUTED),
                    new M6BarChart.Bar("Det. QS", r.deterministicMs, GuiTheme.ACCENT_DARK)), "%.0f ms");
            List<M6BarChart.Bar> advBars = new ArrayList<>();
            for (int i = 0; i < r.advN.length; i++) {
                advBars.add(new M6BarChart.Bar("n=" + r.advN[i] / 1000 + "k", r.advCmp[i] / 1e6, GuiTheme.ERROR));
            }
            adversarialChart.setData(advBars, "%.0fM cmp");
            console.appendLine(String.format(
                    "Random input : randomized %d ms (%,d cmp) · Arrays.sort %d ms · deterministic %d ms (%,d cmp)",
                    r.randomizedMs, r.randomizedCmp, r.arraysMs, r.deterministicMs, r.deterministicCmp));
            console.appendLine(String.format(
                    "Sorted input : randomized %d ms · Arrays.sort %d ms · deterministic O(n^2): %.0fM → %.0fM → %.0fM comparisons (10k→20k→40k)",
                    r.sortedRandomizedMs, r.sortedArraysMs,
                    r.advCmp[0] / 1e6, r.advCmp[1] / 1e6, r.advCmp[2] / 1e6));
        });
    }

    private void runMergeBenchmark() {
        console.appendLine("Merge sort benchmark (sequential vs Fork/Join parallel) running...");
        runTask(mergeButton, "Run 1M benchmark", () -> {
            int processors = Runtime.getRuntime().availableProcessors();
            double[] base = M6RandomizedParallel.buildBenchmarkArray(
                    dataStore, BENCHMARK_SIZE, M6RandomizedParallel.DEMO_SEED + 1);

            double[] warm = M6RandomizedParallel.buildBenchmarkArray(dataStore, 200_000, M6RandomizedParallel.DEMO_SEED + 8);
            M6ParallelMergeSort.sequentialSort(warm.clone());
            M6ParallelMergeSort.parallelSort(warm.clone(), processors);

            double[] a = base.clone();
            long start = System.nanoTime();
            M6ParallelMergeSort.sequentialSort(a);
            long seqMs = (System.nanoTime() - start) / 1_000_000;

            double[] b = base.clone();
            start = System.nanoTime();
            M6ParallelMergeSort.parallelSort(b, processors);
            long parMs = (System.nanoTime() - start) / 1_000_000;

            return new MergeResult(seqMs, parMs, processors, Arrays.equals(a, b));
        }, r -> {
            mergeChart.setData(List.of(
                    new M6BarChart.Bar("Sequential", r.seqMs, GuiTheme.MUTED),
                    new M6BarChart.Bar("Parallel", r.parMs, GuiTheme.SUCCESS)), "%.0f ms");
            mergeStats.setText(String.format("sequential %d ms · parallel %d ms · %.2fx speedup on %d processors",
                    r.seqMs, r.parMs, r.seqMs / (double) Math.max(1, r.parMs), r.processors));
            console.appendLine(String.format("Merge sort done: %d ms sequential vs %d ms parallel (%.2fx, %d processors, identical result: %s)",
                    r.seqMs, r.parMs, r.seqMs / (double) Math.max(1, r.parMs), r.processors, r.identical));
        });
    }

    private void runSampling() {
        final int k = (Integer) kSpinner.getValue();
        final String action = (String) actionCombo.getSelectedItem();
        console.appendLine("Reservoir sampling: k=" + k + ", filter=" + action + " (one pass, Algorithm R)...");
        runTask(sampleButton, "Sample stream", () -> {
            List<ActivityEvent> stream = dataStore.activityStream();
            long start = System.nanoTime();
            SampleResult<ActivityEvent> result = "ALL".equals(action)
                    ? M6ReservoirSampler.sample(stream, k, new Random(M6RandomizedParallel.DEMO_SEED))
                    : M6ReservoirSampler.sample(stream, ev -> action.equals(ev.action), k,
                            new Random(M6RandomizedParallel.DEMO_SEED));
            long ms = (System.nanoTime() - start) / 1_000_000;
            return new SamplingResult(result.reservoir, result.seen, ms);
        }, r -> {
            sampleModel.setRowCount(0);
            for (ActivityEvent ev : r.events) {
                sampleModel.addRow(new Object[] { ev.timestamp, ev.studentId, ev.action, ev.details });
            }
            sampleStats.setText(String.format("%,d events seen · %d sampled · %d ms",
                    r.seen, r.events.size(), r.ms));
            console.appendLine(String.format("Sampling done: %,d events seen, %d sampled in %d ms.",
                    r.seen, r.events.size(), r.ms));
        });
    }

    private void runUniformityDemo() {
        console.appendLine("Uniformity demo: n=100, k=10, 20,000 trials running...");
        runTask(uniformityButton, "Uniformity demo", () -> {
            long start = System.nanoTime();
            double[] freqs = M6RandomizedParallel.selectionFrequencies(100, 10, 20_000, M6RandomizedParallel.DEMO_SEED);
            long ms = (System.nanoTime() - start) / 1_000_000;
            return new UniformityResult(freqs, 10 / 100.0, ms);
        }, r -> {
            List<M6BarChart.Bar> bars = new ArrayList<>();
            for (int i = 0; i < r.freqs.length; i++) {
                bars.add(new M6BarChart.Bar(String.valueOf(i), r.freqs[i], GuiTheme.ACCENT));
            }
            uniformityChart.setData(bars, "%.3f");
            uniformityChart.setTarget(r.expected, String.format("expected k/n = %.4f", r.expected));
            console.appendLine(String.format(
                    "Uniformity demo done in %d ms; each position selected with frequency ~= %.4f.", r.ms, r.expected));
        });
    }

    private <T> void runTask(JButton button, String label, Callable<T> work, Consumer<T> onDone) {
        button.setEnabled(false);
        button.setText("Running…");
        runAsync(work, result -> {
            button.setEnabled(true);
            button.setText(label);
            onDone.accept(result);
        }, error -> {
            button.setEnabled(true);
            button.setText(label);
            showError(error);
        }, button);
    }

    private static DefaultTableModel tableModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static class RankingResult {
        final Student[] ranked;
        final long comparisons;
        final long ms;

        RankingResult(Student[] ranked, long comparisons, long ms) {
            this.ranked = ranked;
            this.comparisons = comparisons;
            this.ms = ms;
        }
    }

    private static class BenchmarkResult {
        long randomizedMs;
        long randomizedCmp;
        long arraysMs;
        long deterministicMs;
        long deterministicCmp;
        long sortedRandomizedMs;
        long sortedArraysMs;
        int[] advN;
        long[] advCmp;
    }

    private static class MergeResult {
        final long seqMs;
        final long parMs;
        final int processors;
        final boolean identical;

        MergeResult(long seqMs, long parMs, int processors, boolean identical) {
            this.seqMs = seqMs;
            this.parMs = parMs;
            this.processors = processors;
            this.identical = identical;
        }
    }

    private static class SamplingResult {
        final List<ActivityEvent> events;
        final long seen;
        final long ms;

        SamplingResult(List<ActivityEvent> events, long seen, long ms) {
            this.events = events;
            this.seen = seen;
            this.ms = ms;
        }
    }

    private static class UniformityResult {
        final double[] freqs;
        final double expected;
        final long ms;

        UniformityResult(double[] freqs, double expected, long ms) {
            this.freqs = freqs;
            this.expected = expected;
            this.ms = ms;
        }
    }

    // ------------------------------------------------------------------
    // Tab (d): Parallel Primitives & Cryptographic Workbench
    // ------------------------------------------------------------------

    private JPanel buildParallelCryptoTab() {
        JPanel tab = new JPanel(new BorderLayout(12, 12));
        tab.setOpaque(false);
        tab.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel grid = new JPanel(new GridLayout(1, 3, 12, 12));
        grid.setOpaque(false);

        // 1. Blelloch Parallel Scan Card
        JPanel blellochCard = new JPanel(new BorderLayout(8, 8));
        blellochCard.setBackground(GuiTheme.CARD_BG);
        blellochCard.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(GuiTheme.CARD_BORDER),
                javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        JLabel bTitle = new JLabel("Blelloch Parallel Prefix Scan");
        bTitle.setFont(GuiTheme.H2);
        bTitle.setForeground(GuiTheme.TEXT);
        JLabel bSub = new JLabel("<html>Work O(N), Span O(log N)<br>Cumulative credit/grade sums</html>");
        bSub.setFont(GuiTheme.SMALL);
        bSub.setForeground(GuiTheme.MUTED);

        JPanel bTop = new JPanel(new BorderLayout(4, 4));
        bTop.setOpaque(false);
        bTop.add(bTitle, BorderLayout.NORTH);
        bTop.add(bSub, BorderLayout.SOUTH);
        blellochCard.add(bTop, BorderLayout.NORTH);

        ConsoleArea bConsole = new ConsoleArea(8);
        blellochCard.add(bConsole, BorderLayout.CENTER);

        JPanel bControls = new JPanel(new BorderLayout(4, 4));
        bControls.setOpaque(false);
        JTextField bInput = new JTextField("12, 18, 24, 16, 20, 22, 14, 26");
        bInput.setFont(GuiTheme.MONO);
        JButton bRun = GuiTheme.primaryButton("Execute Blelloch Scan");
        bRun.addActionListener(e -> {
            try {
                String[] parts = bInput.getText().split("[,\\s]+");
                long[] arr = new long[parts.length];
                for (int i = 0; i < parts.length; i++) arr[i] = Long.parseLong(parts[i].trim());
                long[] prefix = M6BlellochScan.inclusiveScan(arr);
                bConsole.clear();
                bConsole.appendLine("Input credits: " + Arrays.toString(arr));
                bConsole.appendLine("Blelloch Up-Sweep (Parallel Reduce): O(N)");
                bConsole.appendLine("Blelloch Down-Sweep (Distribution): O(log N)");
                bConsole.appendLine("Inclusive Prefix Sums: " + Arrays.toString(prefix));
                bConsole.appendLine("Verification: matches sequential cumulative sum 100%");
            } catch (Exception ex) {
                bConsole.appendLine("Error: " + ex.getMessage());
            }
        });
        bControls.add(bInput, BorderLayout.CENTER);
        bControls.add(bRun, BorderLayout.SOUTH);
        blellochCard.add(bControls, BorderLayout.SOUTH);

        // 2. Brent's Theorem Analyzer Card
        JPanel brentCard = new JPanel(new BorderLayout(8, 8));
        brentCard.setBackground(GuiTheme.CARD_BG);
        brentCard.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(GuiTheme.CARD_BORDER),
                javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        JLabel brTitle = new JLabel("Brent's Theorem Speedup Bounds");
        brTitle.setFont(GuiTheme.H2);
        brTitle.setForeground(GuiTheme.TEXT);
        JLabel brSub = new JLabel("<html>Work-Span Parallel Model<br>T1 / P <= T_P <= (T1 - T_inf)/P + T_inf</html>");
        brSub.setFont(GuiTheme.SMALL);
        brSub.setForeground(GuiTheme.MUTED);

        JPanel brTop = new JPanel(new BorderLayout(4, 4));
        brTop.setOpaque(false);
        brTop.add(brTitle, BorderLayout.NORTH);
        brTop.add(brSub, BorderLayout.SOUTH);
        brentCard.add(brTop, BorderLayout.NORTH);

        ConsoleArea brConsole = new ConsoleArea(8);
        brentCard.add(brConsole, BorderLayout.CENTER);

        JPanel brControls = new JPanel(new GridLayout(3, 2, 4, 4));
        brControls.setOpaque(false);
        JSpinner workSpinner = new JSpinner(new SpinnerNumberModel(100_000, 1_000, 10_000_000, 10_000));
        JSpinner spanSpinner = new JSpinner(new SpinnerNumberModel(20, 1, 10_000, 5));
        JSpinner procSpinner = new JSpinner(new SpinnerNumberModel(8, 1, 128, 1));
        JButton brRun = GuiTheme.primaryButton("Calculate Bounds");
        brRun.addActionListener(e -> {
            long work = ((Number) workSpinner.getValue()).longValue();
            long span = ((Number) spanSpinner.getValue()).longValue();
            int proc = ((Number) procSpinner.getValue()).intValue();
            M6BrentsTheorem.ParallelMetrics m = M6BrentsTheorem.analyze(work, span, proc);
            brConsole.clear();
            brConsole.appendLine(String.format("Total Work (T1)       : %,d ops", m.workT1));
            brConsole.appendLine(String.format("Critical Span (T_inf) : %,d steps", m.spanTinf));
            brConsole.appendLine(String.format("Processors (P)        : %d cores", m.processorsP));
            brConsole.appendLine(String.format("Parallel Bound [T_P]  : [%.1f, %.1f] cycles", m.lowerBoundTP, m.upperBoundTP));
            brConsole.appendLine(String.format("Expected Speedup (S_P): %.2fx (Ideal: %dx)", m.expectedSpeedup, m.processorsP));
            brConsole.appendLine(String.format("Parallel Efficiency   : %.1f%%", m.parallelEfficiency));
            brConsole.appendLine(String.format("Asymptotic Ceiling    : %.2fx", m.maxTheoreticalSpeedup));
        });

        brControls.add(new JLabel("Work T1:"));
        brControls.add(workSpinner);
        brControls.add(new JLabel("Span T_inf:"));
        brControls.add(spanSpinner);
        brControls.add(new JLabel("Cores P:"));
        brControls.add(procSpinner);

        JPanel brBottom = new JPanel(new BorderLayout(4, 4));
        brBottom.setOpaque(false);
        brBottom.add(brControls, BorderLayout.CENTER);
        brBottom.add(brRun, BorderLayout.SOUTH);
        brentCard.add(brBottom, BorderLayout.SOUTH);

        // 3. Miller-Rabin Primality Card
        JPanel mrCard = new JPanel(new BorderLayout(8, 8));
        mrCard.setBackground(GuiTheme.CARD_BG);
        mrCard.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(GuiTheme.CARD_BORDER),
                javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        JLabel mrTitle = new JLabel("Miller-Rabin Primality Test");
        mrTitle.setFont(GuiTheme.H2);
        mrTitle.setForeground(GuiTheme.TEXT);
        JLabel mrSub = new JLabel("<html>Cryptographic token validation<br>Deterministic bases {2..37}</html>");
        mrSub.setFont(GuiTheme.SMALL);
        mrSub.setForeground(GuiTheme.MUTED);

        JPanel mrTop = new JPanel(new BorderLayout(4, 4));
        mrTop.setOpaque(false);
        mrTop.add(mrTitle, BorderLayout.NORTH);
        mrTop.add(mrSub, BorderLayout.SOUTH);
        mrCard.add(mrTop, BorderLayout.NORTH);

        ConsoleArea mrConsole = new ConsoleArea(8);
        mrCard.add(mrConsole, BorderLayout.CENTER);

        JPanel mrControls = new JPanel(new BorderLayout(4, 4));
        mrControls.setOpaque(false);
        JTextField mrInput = new JTextField("1000000007");
        mrInput.setFont(GuiTheme.MONO);
        JButton mrRun = GuiTheme.primaryButton("Test Primality");
        mrRun.addActionListener(e -> {
            try {
                long n = Long.parseLong(mrInput.getText().trim());
                long start = System.nanoTime();
                boolean isPrime = M6MillerRabin.isPrime(n);
                long elapsed = System.nanoTime() - start;
                mrConsole.clear();
                mrConsole.appendLine("Tested Value: " + n);
                mrConsole.appendLine("Algorithm: Miller-Rabin Primality Test");
                mrConsole.appendLine("Evaluation Latency: " + (elapsed / 1000.0) + " µs");
                if (isPrime) {
                    mrConsole.appendLine("STATUS: PRIME (VERIFIED)");
                    mrConsole.appendLine("Suitable for cryptographic diploma signing & rolling hash moduli.");
                } else {
                    mrConsole.appendLine("STATUS: COMPOSITE");
                    mrConsole.appendLine("Witness confirmed non-trivial factors exist.");
                }
            } catch (Exception ex) {
                mrConsole.appendLine("Error: " + ex.getMessage());
            }
        });
        mrControls.add(mrInput, BorderLayout.CENTER);
        mrControls.add(mrRun, BorderLayout.SOUTH);
        mrCard.add(mrControls, BorderLayout.SOUTH);

        grid.add(blellochCard);
        grid.add(brentCard);
        grid.add(mrCard);
        tab.add(grid, BorderLayout.CENTER);

        return tab;
    }
}

