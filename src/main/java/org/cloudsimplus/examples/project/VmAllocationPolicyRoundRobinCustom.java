package org.cloudsimplus.examples.project;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyAbstract;
import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationAbstract;
import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationFirstFitStaticThreshold;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.selectionpolicies.VmSelectionPolicy;
import org.cloudsimplus.vms.Vm;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public class VmAllocationPolicyRoundRobinCustom extends VmAllocationPolicyMigrationFirstFitStaticThreshold {
    private int lastHostIndex;

    public VmAllocationPolicyRoundRobinCustom(VmSelectionPolicy vmSelectionPolicy) {
        this(vmSelectionPolicy, 0.9);
    }

    public VmAllocationPolicyRoundRobinCustom(VmSelectionPolicy vmSelectionPolicy, double overUtilizationThreshold) {
        this(vmSelectionPolicy, overUtilizationThreshold, (BiFunction)null);
    }

    public VmAllocationPolicyRoundRobinCustom(VmSelectionPolicy vmSelectionPolicy, double overUtilizationThreshold, BiFunction<VmAllocationPolicy, Vm, Optional<Host>> findHostForVmFunction) {
        super(vmSelectionPolicy, overUtilizationThreshold, findHostForVmFunction);
    }

    protected Optional<Host> findHostForVmInternal(Vm vm, Predicate<Host> predicate) {
        List<Host> hostList = this.getHostList();
        int maxTries = hostList.size();

        for(int i = 0; i < maxTries; ++i) {
            Host host = (Host)hostList.get(this.lastHostIndex);
            this.lastHostIndex = ++this.lastHostIndex % this.getHostList().size();
            if (predicate.test(host)) {
                return Optional.of(host);
            }

        }

        return Optional.empty();
    }

}
