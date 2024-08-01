package org.cloudsimplus.examples.project;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigration;
import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationAbstract;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.selectionpolicies.VmSelectionPolicy;
import org.cloudsimplus.vms.Vm;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public class VmAllocationPolicyGeneticAlgoCustom extends VmAllocationPolicyMigrationAbstract implements VmAllocationPolicyMigration {
    private static final int POPULATION_SIZE = 50;
    private static final int MAX_GENERATIONS = 100;
    private static final double MUTATION_RATE = 0.01;
    private static final double CROSSOVER_RATE = 0.9;
    private Random random;
    private double overUtilizationThreshold;

    public VmAllocationPolicyGeneticAlgoCustom(VmSelectionPolicy vmSelectionPolicy, double overUtilizationThreshold) {
        this(vmSelectionPolicy, overUtilizationThreshold, (BiFunction)null);
    }

    public VmAllocationPolicyGeneticAlgoCustom(VmSelectionPolicy vmSelectionPolicy, double overUtilizationThreshold, BiFunction<VmAllocationPolicy, Vm, Optional<Host>> findHostForVmFunction) {
        super(vmSelectionPolicy, findHostForVmFunction);
        this.setOverUtilizationThreshold(overUtilizationThreshold);
        this.random = new Random();
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

    @Override
    public Optional<Host> findHostForVmInternal(Vm vm, Predicate<Host> predicate) {
        List<Host> hostList = getHostList();
        List<int[]> population = initializePopulation(hostList.size(), POPULATION_SIZE);

        for (int generation = 0; generation < MAX_GENERATIONS; generation++) {
            List<Double> fitnessScores = evaluatePopulation(population, hostList, vm);
            List<int[]> newPopulation = new ArrayList<>();

            for (int i = 0; i < POPULATION_SIZE; i++) {
                int[] parent1 = selectParent(population, fitnessScores);
                int[] parent2 = selectParent(population, fitnessScores);
                int[] offspring = crossover(parent1, parent2);

                if (random.nextDouble() < MUTATION_RATE) {
                    mutate(offspring);
                }

                newPopulation.add(offspring);
            }

            population = newPopulation;
        }

        return selectBestHost(population, hostList, vm, predicate);
    }

    private List<int[]> initializePopulation(int hostCount, int populationSize) {
        List<int[]> population = new ArrayList<>();
        for (int i = 0; i < populationSize; i++) {
            int[] individual = new int[hostCount];
            for (int j = 0; j < hostCount; j++) {
                individual[j] = random.nextInt(hostCount);
            }
            population.add(individual);
        }
        return population;
    }

    private List<Double> evaluatePopulation(List<int[]> population, List<Host> hostList, Vm vm) {
        List<Double> fitnessScores = new ArrayList<>();
        for (int[] individual : population) {
            fitnessScores.add(evaluateFitness(individual, hostList, vm));
        }
        return fitnessScores;
    }

    private double evaluateFitness(int[] individual, List<Host> hostList, Vm vm) {
        double fitness = 0.0;
        for (int hostIndex : individual) {
            Host host = hostList.get(hostIndex);
            if (host.isSuitableForVm(vm)) {
                fitness += host.getTotalAvailableMips();
            }
        }
        return fitness;
    }

    private int[] selectParent(List<int[]> population, List<Double> fitnessScores) {
        double totalFitness = fitnessScores.stream().mapToDouble(Double::doubleValue).sum();
        double randomValue = random.nextDouble() * totalFitness;

        for (int i = 0; i < population.size(); i++) {
            randomValue -= fitnessScores.get(i);
            if (randomValue <= 0) {
                return population.get(i);
            }
        }

        return population.get(population.size() - 1);
    }

    private int[] crossover(int[] parent1, int[] parent2) {
        int[] offspring = new int[parent1.length];
        for (int i = 0; i < parent1.length; i++) {
            offspring[i] = (random.nextDouble() < CROSSOVER_RATE) ? parent1[i] : parent2[i];
        }
        return offspring;
    }

    private void mutate(int[] individual) {
        int index = random.nextInt(individual.length);
        individual[index] = random.nextInt(getHostList().size());
    }

    private Optional<Host> selectBestHost(List<int[]> population, List<Host> hostList, Vm vm, Predicate<Host> predicate) {
        int[] bestIndividual = null;
        double bestFitness = Double.MIN_VALUE;

        for (int[] individual : population) {
            double fitness = evaluateFitness(individual, hostList, vm);
            if (fitness > bestFitness) {
                bestFitness = fitness;
                bestIndividual = individual;
            }
        }

        if (bestIndividual != null) {
            for (int hostIndex : bestIndividual) {
                Host host = hostList.get(hostIndex);
                if (predicate.test(host)) {
                    return Optional.of(host);
                }
            }
        }

        return Optional.empty();
    }
}
