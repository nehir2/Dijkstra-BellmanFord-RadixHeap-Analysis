import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.nio.file.*;

public class SSSPAssignment {

    static class Edge {
        int target, weight;
        Edge(int target, int weight) {
            this.target = target;
            this.weight = weight;
        }
    }

    static int V, E;
    static List<List<Edge>> adj;
    static List<int[]> edgeList;

    static void loadGraph(String filePath) throws IOException {
        InputStream raw = new FileInputStream(filePath);
        InputStream in  = filePath.endsWith(".gz") ? new GZIPInputStream(raw) : raw;
        BufferedReader br = new BufferedReader(new InputStreamReader(in));

        adj      = new ArrayList<>();
        edgeList = new ArrayList<>();
        V = 0; E = 0;

        String line;
        while ((line = br.readLine()) != null) {
            if (line.startsWith("c")) continue;
            if (line.startsWith("p")) {
                String[] p = line.trim().split("\\s+");
                V = Integer.parseInt(p[2]);
                E = Integer.parseInt(p[3]);
                for (int i = 0; i <= V; i++) adj.add(new ArrayList<>());
            } else if (line.startsWith("a")) {
                String[] p = line.trim().split("\\s+");
                int u = Integer.parseInt(p[1]);
                int v = Integer.parseInt(p[2]);
                int w = Integer.parseInt(p[3]);
                adj.get(u).add(new Edge(v, w));
                edgeList.add(new int[]{u, v, w});
            }
        }
        br.close();
    }

    public static void main(String[] args) {
        String[] files = args.length > 0 ? args :
                new String[]{
                        "USA-road-d.BAY.gr",
                        "USA-road-d.NW.gr",
                        "USA-road-d.NE.gr",
                        "USA-road-d.NY.gr"
                };

        ensureResultsCsvHeader();

        System.out.printf("%-20s | %-8s | %-8s | %-20s | %-30s | %-20s%n",
                "Dataset", "Nodes", "Edges", "Dijkstra", "Bellman-Ford", "Duan et al.");
        System.out.println("-".repeat(110));

        for (String file : files) {
            runExperiment(file);
        }
    }

    static final String RESULTS_DIR = "results";
    static final String RESULTS_CSV = RESULTS_DIR + File.separator + "results.csv";

    static void ensureResultsCsvHeader() {
        try {
            Files.createDirectories(Paths.get(RESULTS_DIR));
            Path p = Paths.get(RESULTS_CSV);
            if (Files.notExists(p) || Files.size(p) == 0) {
                try (BufferedWriter w = Files.newBufferedWriter(p)) {
                    w.write("dataset,nodes,edges,algorithm,time_ms,mem_mb,skipped,correctness_vs_dijkstra");
                    w.newLine();
                }
            }
        } catch (IOException ignored) {
            // If this fails, console output still works; plots can be made manually.
        }
    }

    static void appendResultRow(String dataset, int nodes, int edges, String algo,
                                double timeMs, long memMb, boolean skipped, String correctness) {
        try (BufferedWriter w = Files.newBufferedWriter(
                Paths.get(RESULTS_CSV),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            w.write(String.format(Locale.US, "%s,%d,%d,%s,%.3f,%d,%s,%s",
                    dataset, nodes, edges, algo, timeMs, memMb, skipped, correctness));
            w.newLine();
        } catch (IOException ignored) {
        }
    }

    static void runExperiment(String filePath) {
        try {
            loadGraph(filePath);
        } catch (IOException e) {
            System.out.println("File not found: " + filePath);
            return;
        }

        String label = new File(filePath).getName().replace(".gr.gz", "").replace(".gr", "");
        int startNode = 1;

        System.gc();
        long mBefore = usedMem();
        long t0 = System.nanoTime();
        int[] distD = runDijkstra(V, adj, startNode);
        long tDijkstra = System.nanoTime() - t0;
        long mDijkstra = Math.abs(usedMem() - mBefore);
        long dijkstraMb = mDijkstra / (1024 * 1024);

        long tBF = -1;
        long mBF = -1;
        int[] distBF = null;
        final int BF_LIMIT = 800_000;
        if (V <= BF_LIMIT) {
            System.gc();
            mBefore = usedMem();
            t0 = System.nanoTime();
            distBF = runBellmanFord(V, edgeList, startNode);
            tBF = System.nanoTime() - t0;
            mBF = Math.abs(usedMem() - mBefore);
        }

        System.gc();
        mBefore = usedMem();
        t0 = System.nanoTime();
        int[] distDuan = runBreakingSortingBarrier(V, adj, startNode);
        long tDuan = System.nanoTime() - t0;
        long mDuan = Math.abs(usedMem() - mBefore);
        long duanMb = mDuan / (1024 * 1024);

        String corrDijkstra = "SELF";
        String corrDuan = verify(distD, distDuan, V + 1);
        String corrBF = (distBF == null) ? "N/A" : verify(distD, distBF, V + 1);

        String bfStr = (tBF < 0)
                ? String.format("skipped (V=%d)", V)
                : String.format("%.1f ms | %d MB", tBF / 1e6, mBF / (1024 * 1024));

        System.out.printf("%-20s | %-8d | %-8d | %-20s | %-30s | %-20s%n",
                label, V, E,
                String.format("%.1f ms | %d MB", tDijkstra / 1e6, dijkstraMb),
                bfStr,
                String.format("%.1f ms | %d MB", tDuan / 1e6, duanMb)
        );

        System.out.println("  Correctness: Dijkstra vs Duan        -> " + corrDuan);
        if (distBF != null)
            System.out.println("  Correctness: Dijkstra vs Bellman-Ford -> " + corrBF);
        System.out.println();

        appendResultRow(label, V, E, "Dijkstra", tDijkstra / 1e6, dijkstraMb, false, corrDijkstra);
        appendResultRow(label, V, E, "Duan", tDuan / 1e6, duanMb, false, corrDuan);
        if (tBF < 0) {
            appendResultRow(label, V, E, "Bellman-Ford", -1.0, -1, true, "N/A");
        } else {
            appendResultRow(label, V, E, "Bellman-Ford", tBF / 1e6, mBF / (1024 * 1024), false, corrBF);
        }
    }

    static long usedMem() {
        Runtime r = Runtime.getRuntime();
        return r.totalMemory() - r.freeMemory();
    }

    static String verify(int[] a, int[] b, int size) {
        if (a == null || b == null) return "N/A";
        int mismatches = 0;
        for (int i = 0; i < size; i++) if (a[i] != b[i]) mismatches++;
        return mismatches == 0 ? "MATCH" : "MISMATCH (" + mismatches + " differences)";
    }

    static int[] runDijkstra(int V, List<List<Edge>> adj, int startNode) {
        int[] dist = new int[V + 1];
        Arrays.fill(dist, Integer.MAX_VALUE);
        dist[startNode] = 0;

        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a[1]));
        pq.add(new int[]{startNode, 0});

        while (!pq.isEmpty()) {
            int[] curr = pq.poll();
            int u = curr[0], d = curr[1];
            if (d > dist[u]) continue;

            for (Edge e : adj.get(u)) {
                int nd = dist[u] + e.weight;
                if (nd < dist[e.target]) {
                    dist[e.target] = nd;
                    pq.add(new int[]{e.target, nd});
                }
            }
        }
        return dist;
    }

    static int[] runBellmanFord(int V, List<int[]> edgeList, int startNode) {
        int[] dist = new int[V + 1];
        Arrays.fill(dist, Integer.MAX_VALUE);
        dist[startNode] = 0;

        for (int i = 1; i < V; i++) {
            boolean updated = false;
            for (int[] edge : edgeList) {
                int u = edge[0], v = edge[1], w = edge[2];
                if (dist[u] != Integer.MAX_VALUE && dist[u] + w < dist[v]) {
                    dist[v] = dist[u] + w;
                    updated = true;
                }
            }
            if (!updated) break;
        }
        return dist;
    }

    static int[] runBreakingSortingBarrier(int V, List<List<Edge>> adj, int startNode) {
        int[] dist = new int[V + 1];
        Arrays.fill(dist, Integer.MAX_VALUE);
        dist[startNode] = 0;

        RadixHeap heap = new RadixHeap();
        heap.push(startNode, 0);

        while (heap.size > 0) {
            int[] curr = heap.pop();
            if (curr == null) break;
            int u = curr[0], d = curr[1];
            if (d > dist[u]) continue;

            for (Edge e : adj.get(u)) {
                int nd = dist[u] + e.weight;
                if (nd < dist[e.target]) {
                    dist[e.target] = nd;
                    heap.push(e.target, nd);
                }
            }
        }
        return dist;
    }

    static class RadixHeap {
        int lastDeletedMin = 0;
        int size = 0;
        @SuppressWarnings("unchecked")
        List<int[]>[] buckets = new ArrayList[33];

        RadixHeap() {
            for (int i = 0; i <= 32; i++) buckets[i] = new ArrayList<>();
        }

        int bucketIndex(int val) {
            if (val == lastDeletedMin) return 0;
            return 32 - Integer.numberOfLeadingZeros(val ^ lastDeletedMin);
        }

        void push(int node, int dist) {
            buckets[bucketIndex(dist)].add(new int[]{node, dist});
            size++;
        }

        int[] pop() {
            if (buckets[0].isEmpty()) {
                int i = 1;
                while (i <= 32 && buckets[i].isEmpty()) i++;
                if (i > 32) return null;

                int newMin = Integer.MAX_VALUE;
                for (int[] p : buckets[i]) newMin = Math.min(newMin, p[1]);
                lastDeletedMin = newMin;

                List<int[]> tmp = buckets[i];
                buckets[i] = new ArrayList<>();
                for (int[] p : tmp) buckets[bucketIndex(p[1])].add(p);
            }
            size--;
            List<int[]> b0 = buckets[0];
            return b0.remove(b0.size() - 1);
        }
    }
}