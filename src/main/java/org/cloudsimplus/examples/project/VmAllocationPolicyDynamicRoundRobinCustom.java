package org.cloudsimplus.examples.project;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationFirstFitStaticThreshold;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.selectionpolicies.VmSelectionPolicy;
import org.cloudsimplus.vms.Vm;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public class VmAllocationPolicyDynamicRoundRobinCustom extends VmAllocationPolicyMigrationFirstFitStaticThreshold {
    private int lastHostIndex;

    public VmAllocationPolicyDynamicRoundRobinCustom(VmSelectionPolicy vmSelectionPolicy) {
        this(vmSelectionPolicy, 0.9);
    }

    public VmAllocationPolicyDynamicRoundRobinCustom(VmSelectionPolicy vmSelectionPolicy, double overUtilizationThreshold) {
        this(vmSelectionPolicy, overUtilizationThreshold, (BiFunction)null);
    }

    public VmAllocationPolicyDynamicRoundRobinCustom(VmSelectionPolicy vmSelectionPolicy, double overUtilizationThreshold, BiFunction<VmAllocationPolicy, Vm, Optional<Host>> findHostForVmFunction) {
        super(vmSelectionPolicy, overUtilizationThreshold, findHostForVmFunction);
    }

    @Override
    protected Optional<Host> findHostForVmInternal(Vm vm, Predicate<Host> predicate) {
        List<Host> hostList = this.getHostList();
        int maxTries = hostList.size();
        Host bestHost = null;
        double bestWeight = Double.MAX_VALUE;

        for (int i = 0; i < maxTries; ++i) {
            Host host = hostList.get(this.lastHostIndex);
            this.lastHostIndex = (this.lastHostIndex + 1) % hostList.size();

            if (predicate.test(host)) {
                double weight = calculateHostWeight(host, vm);
                if (bestHost == null || weight > bestWeight) {
                    bestHost = host;
                    bestWeight = weight;
                }
            }
        }

        return Optional.ofNullable(bestHost);
    }

    private double calculateHostWeight(Host host, Vm vm) {
        // Example weight calculation based on CPU, memory, and network usage
        double cpuUtilization = host.getCpuPercentUtilization();
        double memoryUtilization = (double) host.getRam().getAllocatedResource() / host.getRam().getCapacity();
        double bandwidthUtilization = (double) host.getBw().getAllocatedResource() / host.getBw().getCapacity();
        double vmCpuDemand = vm.getMips();
        double vmMemoryDemand = vm.getRam().getCapacity();
        double vmBandwidthDemand = vm.getBw().getCapacity();

        // Weight formula considering host's current utilization and VM's demand
        return cpuUtilization + memoryUtilization + bandwidthUtilization;
    }
}
