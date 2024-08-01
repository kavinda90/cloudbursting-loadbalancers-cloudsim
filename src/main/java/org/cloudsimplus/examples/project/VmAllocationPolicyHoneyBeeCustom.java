package org.cloudsimplus.examples.project;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationFirstFitStaticThreshold;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.selectionpolicies.VmSelectionPolicy;
import org.cloudsimplus.vms.Vm;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.Random;

public class VmAllocationPolicyHoneyBeeCustom extends VmAllocationPolicyMigrationFirstFitStaticThreshold {

    private final Random random = new Random();

    public VmAllocationPolicyHoneyBeeCustom(VmSelectionPolicy vmSelectionPolicy, double overUtilizationThreshold) {
        this(vmSelectionPolicy, overUtilizationThreshold, (BiFunction)null);
    }

    public VmAllocationPolicyHoneyBeeCustom(VmSelectionPolicy vmSelectionPolicy, double overUtilizationThreshold, BiFunction<VmAllocationPolicy, Vm, Optional<Host>> findHostForVmFunction) {
        super(vmSelectionPolicy, overUtilizationThreshold, findHostForVmFunction);
    }

    public Optional<Host> findHostForVmInternal(Vm vm, Predicate<Host> predicate) {
        List<Host> hostList = getHostList();
        List<Host> potentialHosts = scoutBeesPhase(hostList);
        return workerBeesPhase(vm, potentialHosts, predicate);
    }

    private List<Host> scoutBeesPhase(List<Host> hostList) {
        List<Host> potentialHosts = new ArrayList<>();
        int scoutCount = (int) (hostList.size() * 0.5); // 50% hosts are scouts

        for (int i = 0; i < scoutCount; i++) {
            Host host = hostList.get(random.nextInt(hostList.size()));

            potentialHosts.add(host);
        }
        return potentialHosts;
    }

    private Optional<Host> workerBeesPhase(Vm vm, List<Host> potentialHosts, Predicate<Host> predicate) {
        Host bestHost = null;
        double bestFitness = Double.MAX_VALUE;

        for (Host host : potentialHosts) {
            if (predicate.test(host)) {
                double fitness = calculateFitness(host, vm);
                if (fitness < bestFitness) {
                    bestFitness = fitness;
                    bestHost = host;
                }
            }
        }
        return Optional.ofNullable(bestHost);
    }

    private double calculateFitness(Host host, Vm vm) {
        double availableMips = host.getTotalAvailableMips();
        double requiredMips = vm.getTotalMipsCapacity();
        return requiredMips / availableMips;
    }
}
