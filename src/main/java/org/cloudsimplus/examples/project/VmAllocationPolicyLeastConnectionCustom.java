package org.cloudsimplus.examples.project;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationWorstFitStaticThreshold;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.selectionpolicies.VmSelectionPolicy;
import org.cloudsimplus.vms.Vm;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public class VmAllocationPolicyLeastConnectionCustom extends VmAllocationPolicyMigrationWorstFitStaticThreshold {
    private int lastHostIndex;

    public VmAllocationPolicyLeastConnectionCustom(VmSelectionPolicy vmSelectionPolicy, double overUtilizationThreshold) {
        this(vmSelectionPolicy, overUtilizationThreshold, (BiFunction) null);
    }

    public VmAllocationPolicyLeastConnectionCustom(VmSelectionPolicy vmSelectionPolicy, double overUtilizationThreshold, BiFunction<VmAllocationPolicy, Vm, Optional<Host>> findHostForVmFunction) {
        super(vmSelectionPolicy, overUtilizationThreshold, findHostForVmFunction);
    }

//    protected Optional<Host> findHostForVmInternal(Vm vm, Predicate<Host> predicate) {
//        List<Host> hostList = this.getHostList();
//        int maxTries = hostList.size();
//        Host leastHost = null;
//
//        for (int i = 0; i < maxTries; ++i) {
//            Host host = (Host)hostList.get(this.lastHostIndex);
//            this.lastHostIndex = ++this.lastHostIndex % hostList.size();
//            if (predicate.test(host)) {
//                if (leastHost == null || host.getCpuMipsUtilization() < leastHost.getCpuMipsUtilization()) {
//                    leastHost = host;
//                }
//            }
//        }
//
//        return Optional.ofNullable(leastHost);
//    }

    protected Optional<Host> findHostForVmInternal(Vm vm, Predicate<Host> predicate) {
        List<Host> hostList = this.getHostList();
        Host leastHost = null;

        for (Host host : hostList) {
            if (predicate.test(host)) {
                if (leastHost == null || host.getVmList().size() < leastHost.getVmList().size()) {
                    leastHost = host;
                }
            }
        }

        return Optional.ofNullable(leastHost);
    }
}
