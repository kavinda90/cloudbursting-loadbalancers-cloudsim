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

public class VmAllocationPolicyMaxMinCustom extends VmAllocationPolicyMigrationFirstFitStaticThreshold {

    public VmAllocationPolicyMaxMinCustom(VmSelectionPolicy vmSelectionPolicy, double overUtilizationThreshold) {
        this(vmSelectionPolicy, overUtilizationThreshold, (BiFunction)null);
    }

    public VmAllocationPolicyMaxMinCustom(VmSelectionPolicy vmSelectionPolicy, double overUtilizationThreshold, BiFunction<VmAllocationPolicy, Vm, Optional<Host>> findHostForVmFunction) {
        super(vmSelectionPolicy, overUtilizationThreshold, findHostForVmFunction);
    }

    public Optional<Host> findHostForVmInternal(Vm vm, Predicate<Host> predicate) {
        List<Host> hostList = getHostList();
        double maxCompletionTime = Double.MIN_VALUE;
        Host bestHost = null;

        for (Host host : hostList) {
            if (predicate.test(host)) {
                double completionTime = calculateCompletionTime(host, vm);
                if (completionTime > maxCompletionTime) {
                    maxCompletionTime = completionTime;
                    bestHost = host;
                }
            }
        }

        return Optional.ofNullable(bestHost);
    }

    private double calculateCompletionTime(Host host, Vm vm) {
        double availableMips = host.getTotalAvailableMips();
        double requiredMips = vm.getTotalMipsCapacity();
        return requiredMips / availableMips;
    }
}
