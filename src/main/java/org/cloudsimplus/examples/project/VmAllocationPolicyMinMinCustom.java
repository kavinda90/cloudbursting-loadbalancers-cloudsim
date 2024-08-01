package org.cloudsimplus.examples.project;

import lombok.NonNull;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigration;
import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationAbstract;
import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationFirstFitStaticThreshold;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSuitability;
import org.cloudsimplus.selectionpolicies.VmSelectionPolicy;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmGroup;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class VmAllocationPolicyMinMinCustom extends VmAllocationPolicyMigrationFirstFitStaticThreshold {


    public VmAllocationPolicyMinMinCustom(VmSelectionPolicy vmSelectionPolicy, double overUtilizationThreshold) {
        this(vmSelectionPolicy, overUtilizationThreshold, (BiFunction)null);
    }

    public VmAllocationPolicyMinMinCustom(VmSelectionPolicy vmSelectionPolicy, double overUtilizationThreshold, BiFunction<VmAllocationPolicy, Vm, Optional<Host>> findHostForVmFunction) {
        super(vmSelectionPolicy, overUtilizationThreshold, findHostForVmFunction);
    }

    public Optional<Host> findHostForVmInternal(Vm vm, Predicate<Host> predicate) {
        List<Host> hostList = getHostList();
        double minCompletionTime = Double.MAX_VALUE;
        Host bestHost = null;

        for (Host host : hostList) {
            if (predicate.test(host)) {
                double completionTime = calculateCompletionTime(host, vm);
                if (completionTime < minCompletionTime) {
                    minCompletionTime = completionTime;
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

    @Override
    public Set<HostSuitability> allocateHostForVmInternal(@NonNull List<Vm> vmList) {
        System.out.printf("I'm here");
        if (vmList == null) {
            throw new NullPointerException("vmList is marked non-null but is null");
        } else {
            Set<HostSuitability> allocatedHosts = new HashSet<>();
            allocatedHosts = allocateVmsToHosts(vmList);
//            for (Vm vm : vmList) {
//                HostSuitability allocatedHost = allocateHostForVm(vm);
//                allocatedHosts.add(allocatedHost);

//            }
            return allocatedHosts;
        }
    }

    private boolean datacenterHasNoHosts(Vm vm) {
        Comparable<? extends Comparable<?>> vmStr = vm == null ? "Vm" : vm;
        if (this.getHostList().isEmpty()) {
            LOGGER.error("{}: {}: There is no Hosts in {} for requesting {} creation.", new Object[]{vm.getSimulation().clockStr(), this.getClass().getSimpleName(), vm.getSimulation(), vmStr});
            return true;
        } else {
            return false;
        }
    }


    private HostSuitability createVm(Vm vm, Host host) {
        HostSuitability suitability = host.createVm(vm);
        if (suitability.fully()) {
            LOGGER.info("{}: {}: {} has been allocated to {}", new Object[]{vm.getSimulation().clockStr(), this.getClass().getSimpleName(), vm, host});
        } else {
            LOGGER.error("{}: {} Creation of {} on {} failed due to {}.", new Object[]{vm.getSimulation().clockStr(), this.getClass().getSimpleName(), vm, host, suitability});
        }

        return suitability;
    }

    public Set<HostSuitability> allocateVmsToHosts(List<Vm> vmList) {
        Set<HostSuitability> allocatedHosts = new HashSet<>();
        while (!vmList.isEmpty()) {
            double minCompletionTime = Double.MAX_VALUE;
            Vm bestVm = null;
            Host bestHost = null;

            for (Vm vm : vmList) {
                Optional<Host> optionalHost = defaultFindHostForVm(vm);
                System.out.printf("host is " + optionalHost);
                if (optionalHost.isPresent()) {
                    Host host = optionalHost.get();
                    double completionTime = calculateCompletionTime(host, vm);
                    if (completionTime < minCompletionTime) {
                        minCompletionTime = completionTime;
                        bestVm = vm;
                        bestHost = host;
                    }
                }
            }

            if (bestVm != null && bestHost != null) {
                if (this.datacenterHasNoHosts(bestVm)) {
                    allocatedHosts.add(new HostSuitability(bestVm, "Datacenter has no host."));
                } else if (bestVm.isCreated()) {
                    allocatedHosts.add(new HostSuitability(bestVm, "VM is already created"));
                } else {
                    allocatedHosts.add(createVm(bestVm, bestHost));
                    vmList.remove(bestVm);
                }
            } else {
                break;
            }
        }

        return allocatedHosts;
    }
}
