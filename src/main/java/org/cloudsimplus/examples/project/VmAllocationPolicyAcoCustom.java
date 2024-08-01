package org.cloudsimplus.examples.project;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigration;
import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationAbstract;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.selectionpolicies.VmSelectionPolicy;
import org.cloudsimplus.vms.Vm;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public class VmAllocationPolicyAcoCustom extends VmAllocationPolicyMigrationAbstract implements VmAllocationPolicyMigration {
    private static final int NUM_ANTS = 10;
    private static final int MAX_ITERATIONS = 100;
    private static final double ALPHA = 1.0;
    private static final double BETA = 2.0;
    private static final double EVAPORATION_RATE = 0.5;
    private double[][] pheromones;
    private double[][] heuristic;
    private Random random;
    private double overUtilizationThreshold;

    public VmAllocationPolicyAcoCustom(VmSelectionPolicy vmSelectionPolicy, double overUtilizationThreshold) {
        this(vmSelectionPolicy, overUtilizationThreshold, (BiFunction)null);
    }

    public VmAllocationPolicyAcoCustom(VmSelectionPolicy vmSelectionPolicy, double overUtilizationThreshold, BiFunction<VmAllocationPolicy, Vm, Optional<Host>> findHostForVmFunction) {
        super(vmSelectionPolicy, findHostForVmFunction);
        this.setOverUtilizationThreshold(overUtilizationThreshold);
        this.random = new Random();
    }

    @Override
    public Optional<Host> findHostForVmInternal(Vm vm, Predicate<Host> predicate) {
        List<Host> hostList = getHostList();
        initializePheromones(hostList.size());
        initializeHeuristic(hostList, vm);

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            double[][] antSolutions = new double[NUM_ANTS][hostList.size()];

            for (int ant = 0; ant < NUM_ANTS; ant++) {
                constructSolution(antSolutions[ant], hostList, vm);
            }

            updatePheromones(antSolutions, hostList, vm);
        }

        return selectBestHost(hostList, vm, predicate);
    }

    private void initializePheromones(int size) {
        pheromones = new double[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                pheromones[i][j] = 1.0;
            }
        }
    }

    private void initializeHeuristic(List<Host> hostList, Vm vm) {
        heuristic = new double[hostList.size()][hostList.size()];
        for (int i = 0; i < hostList.size(); i++) {
            for (int j = 0; j < hostList.size(); j++) {
                heuristic[i][j] = 1.0 / (hostList.get(j).getTotalAvailableMips() - vm.getMips() + 1);
            }
        }
    }

    private void constructSolution(double[] solution, List<Host> hostList, Vm vm) {
        for (int i = 0; i < hostList.size(); i++) {
            solution[i] = chooseNextHost(i, hostList, vm);
        }
    }

    private int chooseNextHost(int currentIndex, List<Host> hostList, Vm vm) {
        double[] probabilities = new double[hostList.size()];
        double sum = 0.0;

        for (int j = 0; j < hostList.size(); j++) {
            probabilities[j] = Math.pow(pheromones[currentIndex][j], ALPHA) * Math.pow(heuristic[currentIndex][j], BETA);
            sum += probabilities[j];
        }

        for (int j = 0; j < hostList.size(); j++) {
            probabilities[j] /= sum;
        }

        double randomValue = random.nextDouble();
        double cumulativeProbability = 0.0;

        for (int j = 0; j < hostList.size(); j++) {
            cumulativeProbability += probabilities[j];
            if (randomValue <= cumulativeProbability) {
                return j;
            }
        }

        return hostList.size() - 1; // Should not reach here
    }

    private void updatePheromones(double[][] antSolutions, List<Host> hostList, Vm vm) {
        for (int i = 0; i < pheromones.length; i++) {
            for (int j = 0; j < pheromones[i].length; j++) {
                pheromones[i][j] *= (1.0 - EVAPORATION_RATE);
            }
        }

        for (int ant = 0; ant < NUM_ANTS; ant++) {
            double solutionQuality = evaluateSolution(antSolutions[ant], hostList, vm);
            for (int i = 0; i < antSolutions[ant].length; i++) {
                pheromones[i][(int) antSolutions[ant][i]] += solutionQuality;
            }
        }
    }

    private double evaluateSolution(double[] solution, List<Host> hostList, Vm vm) {
        double quality = 0.0;
        for (int i = 0; i < solution.length; i++) {
            quality += hostList.get((int) solution[i]).getTotalAvailableMips();
        }
        return quality;
    }

    private Optional<Host> selectBestHost(List<Host> hostList, Vm vm, Predicate<Host> predicate) {
        double bestQuality = Double.MIN_VALUE;
        Host bestHost = null;

        for (int i = 0; i < pheromones.length; i++) {

            double quality = 0.0;
            for (int j = 0; j < pheromones[i].length; j++) {
                quality += pheromones[i][j];
            }

            if (quality > bestQuality) {
                if(predicate.test(hostList.get(i))) {
                    bestQuality = quality;
                    bestHost = hostList.get(i);
                }
            }

        }

        return Optional.ofNullable(bestHost);
    }

    public final void setOverUtilizationThreshold(double overUtilizationThreshold) {
        if (!(overUtilizationThreshold <= 0.0) && !(overUtilizationThreshold >= 1.0)) {
            this.overUtilizationThreshold = overUtilizationThreshold;
        } else {
            throw new IllegalArgumentException("Over utilization threshold must be greater than 0 and lower than 1.");
        }
    }

    public double getOverUtilizationThreshold(Host host) {
        return this.overUtilizationThreshold;
    }

}
