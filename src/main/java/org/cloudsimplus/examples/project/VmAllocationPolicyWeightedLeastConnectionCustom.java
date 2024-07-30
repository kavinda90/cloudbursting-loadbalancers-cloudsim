package org.cloudsimplus.examples.project;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationWorstFitStaticThreshold;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.selectionpolicies.VmSelectionPolicy;
import org.cloudsimplus.vms.Vm;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public class VmAllocationPolicyWeightedLeastConnectionCustom extends VmAllocationPolicyMigrationWorstFitStaticThreshold {
    private int lastHostIndex;

    public VmAllocationPolicyWeightedLeastConnectionCustom(VmSelectionPolicy vmSelectionPolicy, double overUtilizationThreshold) {
        this(vmSelectionPolicy, overUtilizationThreshold, null);
    }

    public VmAllocationPolicyWeightedLeastConnectionCustom(VmSelectionPolicy vmSelectionPolicy, double overUtilizationThreshold, BiFunction<VmAllocationPolicy, Vm, Optional<Host>> findHostForVmFunction) {
        super(vmSelectionPolicy, overUtilizationThreshold, findHostForVmFunction);
    }

    @Override
    protected Optional<Host> findHostForVmInternal(Vm vm, Predicate<Host> predicate) {
        List<Host> hostList = this.getHostList();
        Host leastHost = null;
        double leastWeight = Double.MAX_VALUE;

        for (Host host : hostList) {
            if (predicate.test(host)) {
                double weight = calculateHostWeight(host, vm);
                if (leastHost == null || weight < leastWeight) {
                    leastHost = host;
                    leastWeight = weight;
                }
            }
        }

        return Optional.ofNullable(leastHost);
    }

    private double calculateHostWeight(Host host, Vm vm) {
        // Weight is calculated based on the number of VMs, the CPU utilization, and the host's capacity
        int vmCount = host.getVmList().size();
        double connections = host.getVmList().size();
        double hostCapacity = host.getTotalMipsCapacity();
        double hostUtilization = host.getCpuMipsUtilization();

        // Adjust the weight calculation to take host capacity and VM demand into account
        return (vmCount + 1) * (connections / (hostCapacity - hostUtilization));
    }
}
