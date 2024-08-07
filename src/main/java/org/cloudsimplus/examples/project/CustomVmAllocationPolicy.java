package org.cloudsimplus.examples.project;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicySimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

public class CustomVmAllocationPolicy extends VmAllocationPolicySimple {

    public CustomVmAllocationPolicy() {
        super();
//        this.setFindHostForVmFunction(findHostForVmFunction());
    }

    public BiFunction<VmAllocationPolicy, Vm, Optional<Host>> findHostForVmFunction() {
        return (vmAllocationPolicy, vm) -> {
            for (Host host : vmAllocationPolicy.getHostList()) {
                if (host.isSuitableForVm(vm) && host.getTotalAvailableMips() >= vm.getMips()) {
                    return Optional.of(host);
                }
            }
            return Optional.empty();
        };
    }

    @Override
    protected Optional<Host> defaultFindHostForVm(Vm vm) {
        for (Host host : getHostList()) {
            if (host.isSuitableForVm(vm) && host.getTotalAvailableMips() >= vm.getMips()) {
                return Optional.of(host);
            }
        }
        return Optional.empty();
    }
}
