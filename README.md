# Dijkstra - Bellman-Ford - Radix Heap Analysis

##  Project Overview
This project implements and compares different single-source shortest path (SSSP) algorithms on large road network graphs. The goal is to analyze their performance in terms of runtime and memory usage under different graph sizes.

The implemented algorithms are:
- Dijkstra’s Algorithm (Priority Queue based)
- Bellman-Ford Algorithm
- Dijkstra’s Algorithm optimized with Radix Heap

---

##  Objectives
- Implement fundamental shortest path algorithms
- Optimize Dijkstra using Radix Heap
- Compare performance on large-scale graphs
- Evaluate runtime and memory consumption

---

## Algorithms

### 1. Dijkstra’s Algorithm
Uses a priority queue (min-heap) to always expand the closest unvisited node.  
Time complexity: **O(E log V)**

### 2. Bellman-Ford Algorithm
Handles negative weights and relaxes all edges repeatedly.  
Time complexity: **O(VE)**

### 3. Radix Heap Optimization
Improves Dijkstra by replacing the priority queue with a radix heap structure for faster integer-key operations.

---

## Experimental Setup
- Graph datasets: USA road networks (BAY, NE, NW, NY)
- Metrics measured:
  - Execution time
  - Memory usage
- Tools used:
  - Java (main implementation)
  
---

##  Results

The experiments show:

- Bellman-Ford is significantly slower due to repeated edge relaxation.
- Dijkstra performs much better using a priority queue.
- Radix Heap optimized Dijkstra achieves the best performance on large graphs.

### Example Observations:
- Runtime increases with number of nodes and edges.
- Radix Heap reduces priority queue overhead significantly.
- Memory usage remains relatively stable across implementations.
