import csv
import os
from collections import defaultdict


def read_results(path: str):
    rows = []
    with open(path, newline="", encoding="utf-8") as f:
        r = csv.DictReader(f)
        for row in r:
            row["nodes"] = int(row["nodes"])
            row["edges"] = int(row["edges"])
            row["time_ms"] = float(row["time_ms"])
            row["mem_mb"] = int(row["mem_mb"])
            row["skipped"] = row["skipped"].strip().lower() == "true"
            rows.append(row)
    return rows


def ensure_dir(p: str):
    os.makedirs(p, exist_ok=True)


def try_import_plotting():
    try:
        import matplotlib.pyplot as plt  # noqa: F401
        return True
    except Exception:
        return False


def main():
    results_csv = os.path.join("results", "results.csv")
    out_dir = os.path.join("results", "plots")
    ensure_dir(out_dir)

    if not os.path.exists(results_csv):
        raise SystemExit(f"Missing `{results_csv}`. Run the Java program first.")

    if not try_import_plotting():
        raise SystemExit(
            "matplotlib is not installed.\n"
            "Install with:\n"
            "  python3 -m pip install -r requirements.txt\n"
        )

    import matplotlib.pyplot as plt

    rows = read_results(results_csv)

    # group: algo -> list of (nodes, edges, time, mem, dataset)
    by_algo = defaultdict(list)
    datasets = {}
    for r in rows:
        datasets[r["dataset"]] = (r["nodes"], r["edges"])
        by_algo[r["algorithm"]].append(r)

    # sort datasets by nodes to get consistent X ordering
    ds_sorted = sorted(datasets.items(), key=lambda kv: kv[1][0])
    x_labels = [d for d, _ in ds_sorted]
    x_nodes = [datasets[d][0] for d in x_labels]
    x_edges = [datasets[d][1] for d in x_labels]

    algo_order = ["Dijkstra", "Duan", "Bellman-Ford"]
    colors = {"Dijkstra": "#1f77b4", "Duan": "#ff7f0e", "Bellman-Ford": "#2ca02c"}

    def series(algo: str, field: str):
        m = {r["dataset"]: r for r in by_algo.get(algo, [])}
        ys = []
        for d in x_labels:
            r = m.get(d)
            if not r or r["skipped"] or r[field] < 0:
                ys.append(None)
            else:
                ys.append(r[field])
        return ys

    def plot_metric(x, x_name, y_name, field, filename, logy=False):
        plt.figure(figsize=(10, 5))
        for algo in algo_order:
            ys = series(algo, field)
            # plot with gaps for None values
            xs_plot, ys_plot = [], []
            for xi, yi in zip(x, ys):
                if yi is None:
                    if xs_plot:
                        plt.plot(xs_plot, ys_plot, marker="o", label=algo, color=colors.get(algo))
                        xs_plot, ys_plot = [], []
                else:
                    xs_plot.append(xi)
                    ys_plot.append(yi)
            if xs_plot:
                plt.plot(xs_plot, ys_plot, marker="o", label=algo, color=colors.get(algo))

        plt.xlabel(x_name)
        plt.ylabel(y_name)
        plt.title(f"{y_name} vs {x_name}")
        if logy:
            plt.yscale("log")
        plt.grid(True, which="both", linestyle="--", alpha=0.4)
        plt.legend()
        plt.tight_layout()
        plt.savefig(os.path.join(out_dir, filename), dpi=200)
        plt.close()

    plot_metric(x_nodes, "Nodes (V)", "Runtime (ms)", "time_ms", "runtime_vs_nodes.png", logy=False)
    plot_metric(x_edges, "Edges (E)", "Runtime (ms)", "time_ms", "runtime_vs_edges.png", logy=False)
    plot_metric(x_nodes, "Nodes (V)", "Memory (MB)", "mem_mb", "memory_vs_nodes.png", logy=False)

    # Also produce log-scale runtime for scalability visibility
    plot_metric(x_nodes, "Nodes (V)", "Runtime (ms) [log scale]", "time_ms", "runtime_vs_nodes_log.png", logy=True)

    print("Saved plots to `results/plots/`.")


if __name__ == "__main__":
    main()

