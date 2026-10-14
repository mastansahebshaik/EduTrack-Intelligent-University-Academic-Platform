package edutrack.modules;

/**
 * Blelloch Parallel Prefix Scan (Work-Efficient Parallel Prefix Sum).
 * Computes cumulative academic statistics (cumulative GPA, running credits, percentile ranks).
 *
 * Algorithm phases:
 * 1. Up-Sweep (Parallel Reduce Phase): Builds binary sum tree from leaves to root. Work = O(N), Span = O(log N).
 * 2. Down-Sweep (Distribution Phase): Sets root to zero and mirrors tree downwards. Work = O(N), Span = O(log N).
 * Total Work: O(N), Total Span: O(log N).
 */
public final class M6BlellochScan {

    private M6BlellochScan() {}

    /**
     * Executes work-efficient Blelloch scan returning inclusive prefix sums.
     */
    public static long[] inclusiveScan(long[] input) {
        if (input == null) throw new IllegalArgumentException("input array cannot be null");
        int originalLen = input.length;
        if (originalLen == 0) return new long[0];

        // Pad to next power of 2
        int n = 1;
        while (n < originalLen) n <<= 1;

        long[] a = new long[n];
        System.arraycopy(input, 0, a, 0, originalLen);

        // 1. Up-Sweep Phase (Reduce)
        for (int d = 0; (1 << d) < n; d++) {
            int step = 1 << (d + 1);
            int half = 1 << d;
            for (int i = 0; i < n; i += step) {
                a[i + step - 1] += a[i + half - 1];
            }
        }

        // Save total sum before setting root to 0
        long totalSum = a[n - 1];
        a[n - 1] = 0;

        // 2. Down-Sweep Phase
        for (int d = (int) (Math.log(n) / Math.log(2)) - 1; d >= 0; d--) {
            int step = 1 << (d + 1);
            int half = 1 << d;
            for (int i = 0; i < n; i += step) {
                long t = a[i + half - 1];
                a[i + half - 1] = a[i + step - 1];
                a[i + step - 1] += t;
            }
        }

        // a now contains the exclusive scan.
        // Convert to inclusive scan for the original length.
        long[] result = new long[originalLen];
        for (int i = 0; i < originalLen - 1; i++) {
            result[i] = a[i + 1];
        }
        result[originalLen - 1] = totalSum;

        return result;
    }
}
