package main;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;

import main.executor.Executor;
import main.executor.GroupingExecutor;
import main.generator.BreakType;
import main.generator.Direction;
import main.generator.Generator;
import main.generator.Part;
import main.productions.productionFactory.L2ProjectionFactory;
import main.scheduler.GraphScheduler;
import main.scheduler.Node;
import main.scheduler.SmartNode;
import main.scheduler.ProductionGraphBuilder;
import main.tree.DOF;
import main.tree.Element2D;
import main.tree.TreeInitializer;
import main.tree.Vertex;
import main.utils.MatrixUtil;

public class Application {

	private static List<List<Long>> timesMap = new ArrayList<>();

	// input: adaptation_type {1,2} level_count threads_count
	//output: adaptation_type;threads;levels;time
	public static void main(String[] args) {

		int adaptationType = new Integer(args[0]);
		int levels = new Integer(args[1]);
		int threads = new Integer(args[2]);
		Long executionTime = 0l;
		int iterations = 5;
		Vertex root = null;
		for (int i = 0; i < iterations; ++i) {
			root = generateMesh(adaptationType, levels);

			ProductionGraphBuilder graphBuilder = new ProductionGraphBuilder(
					new L2ProjectionFactory());

			GraphScheduler scheduler = new GraphScheduler();
			Set<? extends Node> graph = graphBuilder.makeGraph(root);

			List<List<Node>> scheduledNodes = scheduler.schedule(graph);

			Long st = System.currentTimeMillis();
			execute(scheduledNodes, root, threads);
			Long tmp = (System.currentTimeMillis() - st);
			executionTime += tmp;

		}

		System.out.println(adaptationType + ";" + threads + ";" + levels + ";" + executionTime / iterations);
		// for edge adaptation prints nanotime for all levels, for corner only
		// for first
		int size = adaptationType > 2 ? timesMap.get(0).size() : 1;
		for (int i = 0; i < size; ++i) {
			long time = Long.MAX_VALUE;
			for (int j = 0; j < timesMap.size(); ++j) {
				long tmp = timesMap.get(j).get(i);
				if (tmp < time) {
					time = tmp;
				}
			}
			System.out.println("\t " + threads + ";" + levels + ";" + time);
		}

		Set<Vertex> leaves = new HashSet<>();
		getLeaves(leaves, root);
		Map<DOF, Double> result = gatherResult(leaves);
		gatherElements(leaves);
		ResultPrinter.printResult(gatherElements(leaves), result);

	}

	private static void execute(List<List<Node>> scheduledNodes, Vertex root,
			int pool) {
		Executor executor = new GroupingExecutor(pool);
		List<Long> list = new ArrayList<>();
		for (List<Node> nodes : scheduledNodes) {
			Long st = System.nanoTime();
			executor.beginStage(nodes.size());
			for (Node n : nodes) {
				executor.submitProduction(((SmartNode) n).getProduction());
			}
			executor.waitForEnd();
			list.add(System.nanoTime() - st);
		}
		timesMap.add(list);

		executor.shutdown();
	}

	private static Map<DOF, Double> gatherResult(Set<Vertex> leaves) {
		Map<DOF, Double> result = new TreeMap<>();
		for (Vertex v : leaves) {
			for (int i = 0; i < v.rowDofs.size(); ++i) {
				result.put(v.rowDofs.get(i), v.x[i]);
			}
		}
		return result;
	}

	private static Vertex generateMesh(int meshType, int levelsCount) {
		Generator gen = new Generator(0, 1, 0, 1);
		switch (meshType) {
		case 1:
			generateEdgeMesh(levelsCount, new ArrayList<Integer>(), gen);
			break;
		case 2:
			generateCornerMesh(levelsCount, new ArrayList<Integer>(), gen);
			break;
		case 3:
			generateEdgeMesh(levelsCount, new ArrayList<Integer>(), gen);
			break;
		}

		Vertex root = gen.buildEliminationTree();

		TreeInitializer.visit(root);

		return root;

	}

	// generate edge mesh
	/*
	 * ----------------- |+|+|+|+| --------- | | | | | --------- | | | ---------
	 */
	private static void generateEdgeMesh(int level, List<Integer> path,
			Generator gen) {
		Queue<List<Integer>> q = new LinkedList<List<Integer>>();
		q.add(path);
		for (int i = 0; i < level; ++i) {
			List<List<Integer>> tmp = new ArrayList<>(q);
			q.clear();
			for (List<Integer> l : tmp) {
				gen.breakPart(l, BreakType.CROSS, gen.getRoot());
				l.add(0);
				q.add(new ArrayList<>(l));
				l.remove(l.size() - 1);
				l.add(1);
				q.add(new ArrayList<>(l));

			}
		}

	}

	// generate corner mesh
	/*
	 * ----------------- | | | |+| --------- | | | ---------
	 */
	private static void generateCornerMesh(int level, List<Integer> path,
			Generator gen) {
		Part root = gen.getRoot();
		for (int i = 0; i < level; ++i) {
			root = gen.breakPart(Collections.<Integer> emptyList(),
					BreakType.CROSS, root)[1];

		}

	}

	private static List<Element2D> gatherElements(Collection<Vertex> leaves) {
		List<Element2D> elements = new ArrayList<>();
		for (Vertex v : leaves) {
			elements.add((Element2D) v.element);
		}
		return elements;
	}

	public static void print(Part p) {
		System.out.println(p);
		for (Direction d : Direction.values()) {
			System.out.printf("%s -> %s\n", d, p.getEdge(d));
		}
	}

	private static void getLeaves(Set<Vertex> leaves, Vertex root) {
		Queue<Vertex> q = new ArrayDeque<Vertex>();
		q.add(root);

		while (!q.isEmpty()) {
			Vertex v = q.poll();
			if (v.children.isEmpty()) {
				leaves.add(v);
			} else {
				q.addAll(v.children);
			}
		}
	}

	private static Map<DOF, Double> gatherMatrix(Vertex root) {
		Map<DOF, Double> result = new TreeMap<>();
		Set<Vertex> leaves = new HashSet<>();
		getLeaves(leaves, root);
		Set<DOF> dofs = new HashSet<>();
		for (Vertex v : leaves) {
			dofs.addAll(v.rowDofs);
		}
		List<DOF> dofList = new ArrayList<>(dofs);
		double[][] matrixA = new double[dofList.size()][dofList.size()];
		double[] matrixB = new double[dofList.size()];
		combineMatrices(leaves, dofList, matrixA, matrixB);
		double[] solution = MatrixUtil.gaussianElimination(matrixA, matrixB);
		for (int i = 0; i < dofList.size(); ++i) {
			result.put(dofList.get(i), solution[i]);
		}
		return result;
	}

	private static void combineMatrices(Set<Vertex> leaves, List<DOF> dofList,
			double[][] matrixA, double[] matrixB) {
		for (Vertex v : leaves) {
			for (DOF d : v.rowDofs) {
				int parentI = dofList.indexOf(d);
				int childI = v.rowDofs.indexOf(d);
				for (DOF d2 : v.rowDofs) {
					int parentJ = dofList.indexOf(d2);

					int childJ = v.rowDofs.indexOf(d2);
					matrixA[parentI][parentJ] += v.A[childI][childJ];

				}
				matrixB[parentI] += v.b[childI];
			}
		}
	}

}
