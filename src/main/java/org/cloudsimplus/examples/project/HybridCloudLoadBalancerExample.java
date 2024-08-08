/*
 * CloudSim Plus: A modern, highly-extensible and easier-to-use Framework for
 * Modeling and Simulation of Cloud Computing Infrastructures and Services.
 * http://cloudsimplus.org
 *
 *     Copyright (C) 2015-2021 Universidade da Beira Interior (UBI, Portugal) and
 *     the Instituto Federal de Educação Ciência e Tecnologia do Tocantins (IFTO, Brazil).
 *
 *     This file is part of CloudSim Plus.
 *
 *     CloudSim Plus is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     CloudSim Plus is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with CloudSim Plus. If not, see <http://www.gnu.org/licenses/>.
 */
package org.cloudsimplus.examples.project;

import ch.qos.logback.classic.Level;
import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigration;
import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationBestFitStaticThreshold;
import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationFirstFitStaticThreshold;
import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationWorstFitStaticThreshold;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.builders.tables.HostHistoryTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterCharacteristics;
import org.cloudsimplus.datacenters.DatacenterCharacteristics.Distribution;
import org.cloudsimplus.datacenters.DatacenterCharacteristicsSimple;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.listeners.DatacenterBrokerEventInfo;
import org.cloudsimplus.listeners.EventListener;
import org.cloudsimplus.listeners.VmHostEventInfo;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.selectionpolicies.VmSelectionPolicyMinimumUtilization;
import org.cloudsimplus.traces.SwfWorkloadFileReader;
import org.cloudsimplus.util.Log;
import org.cloudsimplus.utilizationmodels.UtilizationModel;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmCost;
import org.cloudsimplus.vms.VmSimple;

import java.util.*;
import java.util.stream.IntStream;

import static java.util.Comparator.comparingLong;
import static java.util.stream.Collectors.joining;

/**
 * An example showing how to create 2 Datacenters having different
 * number of Hosts and VMs with heterogeneous capacity/requirements.
 *
 * <p>
 * Despite there isn't the concept of multi-cloud environments on CloudSim Plus,
 * this example can be used to simulate that.
 * Lets say you have 2 or more datacenters where some of them belong to a cloud provider
 * and other ones belong to another provider.
 * Since the example is performing VM migration between those datacenters,
 * we can consider in that case we are performing VM migration between
 * cloud providers.
 * </p>
 *
 * <p>Realize the example is creating datacenters in private and public clouds.
 * This way, when migrating VMs from/to a public cloud, non-live migration will be performed,
 * where the VM is shutdown and restarted on the target datacenter.
 * Check {@link DatacenterCharacteristics#getDistribution()} for details.</p>
 *
 * <p>The example performs VM migration using
 * a {@link VmAllocationPolicyMigrationFirstFitStaticThreshold}.
 * Such a policy migrates VMs based on
 * a static host CPU utilization threshold.
 * The VmAllocationPolicy used in this example ignores power consumption of Hosts.
 * This way, it isn't required to set a PowerModel for Hosts.</p>
 *
 * <p>A Host from Datacenter 1 will become overloaded,
 * however, there will be no other suitable Host from the same datacenter
 * to migrate VMs to.
 * Therefore, intra-datacenter migration is not possible
 * and the next datacenter available will be tried next time.</p>
 *
 * <p>The {@link VmAllocationPolicyMigrationFirstFitStaticThreshold}
 * allows setting static under/over CPU utilization thresholds to
 * enable VM migration.
 * The example uses a {@link UtilizationModelDynamic} to define that CPU usage of cloudlets
 * increases along simulation time.
 * </p>
 *
 * <p>Some constants are used to create simulation objects such as
 * {@link  DatacenterSimple}, {@link  Host} and {@link  Vm}.
 * The values of these constants were careful and accordingly chosen to allow:
 * (i) migration of VMs due to either under and overloaded hosts; and
 * (ii) the researcher to know exactly how the simulation will run
 * and what will be the final results.
 * </p>
 *
 * <p>
 * Several values impact simulation results, such as
 * (i) hosts CPU capacity and number of PEs,
 * (ii) VMs and cloudlets requirements and
 * (iii) even VM bandwidth (which defines the VM migration time).
 *
 * This way, if you want to change these values, you must
 * define new appropriated ones to allow the simulation
 * to run correctly.</p>
 *
 * <p>Realize that Host State History is just collected
 * if you enable that by calling {@link Host#setStateHistoryEnabled(boolean)}.</p>
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 7.2.0
 */
public final class HybridCloudLoadBalancerExample {
    /** @see Datacenter#getSchedulingInterval() */
    private static final int  SCHEDULING_INTERVAL = 2;

    /**
     * A matrix where each row defines the PEs capacity for hosts in one Datacenter.
     * Each item is the number of PEs for a Host.
     * The number of rows (length of the matrix) define the number of Datacenters to create.
     * The number of cols (length of each row) define the number of Hosts for each Datacenter.
     *
     * <p>The total number of items define the number of Hosts to create.</p>
     */
//    private static final int[][] DC_HOST_PES = {{4, 5, 4, 5, 3}, {4, 4, 4, 8, 4, 4, 5, 8}};
    private static final int[][] DC_HOST_PES = {{4, 5}, {16, 16, 16, 32}};

    /**
     * The percentage of host CPU usage that trigger VM migration
     * due to under utilization (in scale from 0 to 1, where 1 is 100%).
     */
    private static final double HOST_UNDER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION = 0.1;

    /**
     * The percentage of host CPU usage that trigger VM migration
     * due to over utilization (in scale from 0 to 1, where 1 is 100%).
     */
    private static final double HOST_OVER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION = 0.78;

    /** @see Datacenter#setHostSearchRetryDelay(double) */
    private static final int HOST_SEARCH_RETRY_DELAY = 10;

    /**
     * The time spent during VM migration depend on the
     * bandwidth of the target Host.
     * By default, a {@link Datacenter}
     * uses only 50% of the BW to migrate VMs, while the
     * remaining capacity is used for VM communication.
     * This can be changed by calling
     * {@link DatacenterSimple#setBandwidthPercentForMigration(double)}.
     *
     * <p>The 16000 Mb/s is the same as 2000 MB/s. Since just half of this capacity
     * is used for VM migration, only 1000 MB/s will be available for this process.
     * The time that takes to migrate a Vm depend on the VM RAM capacity.
     * Since VMs in this example are created with 2000 MB of RAM, any migration
     * will take 2 seconds to finish, as can be seen in the logs.
     */
    private static final long   HOST_BW = 24_000L; //Mbps

    private static final int    HOST_MIPS = 1000; //for each PE

    /**
     * RAM capacity for created Hosts.
     * The length of this array must be the length of the largest row on {@link #DC_HOST_PES} matrix.
     */
//    private static final long[] HOST_RAM = {100_000, 100_000, 100_000, 100_000, 100_000, 100_000, 100_000, 100_000}; //host memory (MB)
    private static final long[] HOST_RAM = {500_000, 500_000, 500_000, 500_000}; //host memory (MB)

    private static final long   HOST_STORAGE = 1_000_000; //host storage (MB)

    /**
     * A matrix where each row defines the number of PEs required by each VM in one Datacenter.
     * The total number of items define the number of VMs to create.
     *
     * <p>The length of this matrix (number of rows) must be equal to the number of datacenters,
     * defined by the length of {@link #DC_HOST_PES}.</p>
     */
//    private static final int[][] VM_PES = {{2, 2, 2, 2, 2, 2, 2, 2, 2, 2}, {}};
    private static final int[][] VM_PES = {{2, 2, 2, 2, 2, 2, 2}, {}};

    private static final int    VM_MIPS = 1000; //for each PE
    private static final long   VM_SIZE = 100_000; //image size (MB)
    private static final int    VM_RAM  = 30_000; //VM memory (MB)
    private static final long   VM_BW   = 5_000; //Mbps

    private static final long   CLOUDLET_LENGTH = 50_000;
    private static final long   CLOUDLET_FILESIZE = 300;
    private static final long   CLOUDLET_OUTPUT_SIZE = 300;

    /**
     * The percentage of CPU that a cloudlet will use when
     * it starts executing (in scale from 0 to 1, where 1 is 100%).
     * For each cloudlet create, this value is used
     * as a base to define CPU usage.
     * @see #createAndSubmitCloudlets(DatacenterBroker)
     */
    private static final double CLOUDLET_INITIAL_CPU_PERCENTAGE = 0.5;

    /**
     * Defines the speed (in percentage) that CPU usage of a cloudlet
     * will increase during the simulation execution.
     * (in scale from 0 to 1, where 1 is 100%).
     * @see #createCpuUtilizationModel(double, double)
     */
    private static final double CLOUDLET_CPU_INCREMENT_PER_SECOND = 0.05;

    private final List<DatacenterBrokerSimple> brokerList;

    private final CloudSimPlus simulation;
    private final List<Datacenter> datacenterList;

    private int migrationsNumber;
    private int createdVms;
    private int createdCloudlets;
    private int createdHosts;
    private double totalCpuUsageMean;

    private static final String WORKLOAD_FILENAME = "workload/swf/NASA-iPSC-1993-3.1-cln.swf.gz";
    private int maxCloudletsToCreateFromWorkloadFile = Integer.MAX_VALUE;

    public static void main(final String[] args) {
        new HybridCloudLoadBalancerExample();
    }

    private HybridCloudLoadBalancerExample(){
        Log.setLevel(Level.INFO);
        validateConfiguration();

        System.out.println("Starting " + getClass().getSimpleName());
        simulation = new CloudSimPlus();

        this.datacenterList = createDatacenters();
        this.brokerList = createBrokers();
        for (final DatacenterBroker broker : brokerList) {
            broker.setVmDestructionDelayFunction(vm -> 5.0);
        }
        createVmsAndCloudlets();

        // Allocate VMs to hosts using Min-Min
//        allocateVmsToHosts();

        simulation.start();

        printResults();
        printPerformanceStatistics(this.brokerList.get(0).getCloudletCreatedList());
        printTotalVmsCost(this.brokerList);

        System.out.printf("%n%s finished!%n", getClass().getSimpleName());
    }

    private void validateConfiguration() {
        final int dcId = 1;
        for (final int[] hostPes : DC_HOST_PES) {
            final int hostsNumber = hostPes.length;
            if(HOST_RAM.length < hostsNumber){
                final var msg = String.format(
                        "The length of array HOST_RAM must be at least equal to the number of Hosts for Datacenter %d.", dcId);
                throw new IllegalStateException(msg);
            }
        }

        final int datacentersNumber = DC_HOST_PES.length;
        if(VM_PES.length < datacentersNumber) {
            throw new IllegalStateException("" +
                    "The number of rows on matrix VM_PES must be at least equal to the number of datacenters, indicating PEs for VMs in each datacenter..");
        }
    }

    /**
     * Creates an VmAllocationPolicy and sets an upper utilization threshold higher than the
     * {@link #HOST_OVER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION}
     * to enable placing VMs which will use more CPU than
     * defined by the value in the mentioned constant.
     * After VMs are all submitted to Hosts, the threshold is changed
     * to the value of the constant.
     * This is used to  place VMs into a Host which will
     * become overloaded in order to trigger the migration.
     */
    private VmAllocationPolicyMigration createVmAllocationPolicy() {
        final var vmSelection = new VmSelectionPolicyMinimumUtilization();
        final var vmAllocation = new VmAllocationPolicyWeightedLeastConnectionCustom(vmSelection, 0.9);

        vmAllocation.setUnderUtilizationThreshold(HOST_UNDER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION);
        return vmAllocation;
    }

    /**
     * Creates one {@link DatacenterBroker} for each Datacenter.
     * @return
     */
    private List<DatacenterBrokerSimple> createBrokers() {
        return datacenterList.stream().map(dc -> new DatacenterBrokerSimple(simulation)).toList();
    }

    private void printResults() {
        for (final DatacenterBroker broker : brokerList) {
            final var cloudletFinishedList = broker.getCloudletFinishedList();
            final Comparator<Cloudlet> cloudletComparator =
                    comparingLong((Cloudlet c) -> c.getVm().getHost().getId())
                            .thenComparingLong(c -> c.getVm().getId());
            cloudletFinishedList.sort(cloudletComparator);
            new CloudletsTableBuilder(cloudletFinishedList).setTitle(broker.toString()).build();
        }

        printHostStateHistory();
        System.out.printf("%nNumber of VM migrations: %d%n", migrationsNumber);
    }

    private void printHostStateHistory() {
        System.out.printf(
                "%nHosts CPU usage History (when allocated MIPS is lower than requested one, it is due to VM migration overhead)");
        datacenterList.stream()
                .map(Datacenter::getHostList).flatMap(List::stream)
                .filter(h -> !h.getStateHistory().isEmpty())
                .forEach(this::printHostStateHistory);
    }

    private void printHostStateHistory(final Host host) {
        new HostHistoryTableBuilder(host).setTitle(host.toString()).build();
    }

    private void printPerformanceStatistics(List<Cloudlet> finishedCloudlets) {
        double totalResponseTime = 0;

        for (Cloudlet cloudlet : finishedCloudlets) {
            totalResponseTime += cloudlet.getFinishTime() - cloudlet.getCreationTime();
        }

        double avgResponseTime = totalResponseTime / finishedCloudlets.size();
        double makespan = finishedCloudlets.stream().mapToDouble(Cloudlet::getFinishTime).max().orElse(0);
        double throughput = finishedCloudlets.size() / makespan;

        System.out.println("\nAverage Response Time: " + avgResponseTime + " seconds");
        System.out.println("Makespan: " + makespan + " seconds");
        System.out.println("Throughput: " + throughput + " cloudlets/second\n");

        datacenterList.stream()
                .map(Datacenter::getHostList).flatMap(List::stream)
                .filter(h -> !h.getStateHistory().isEmpty())
                .forEach(this::printCpuUtilizationForHost);
        var totalHostCount = datacenterList.stream()
                .map(Datacenter::getHostList).mapToInt(List::size).sum();
        System.out.printf("Average Host CPU usage: %6.2f%%%n", totalCpuUsageMean/totalHostCount);
    }

    /**
     * Shows CPU utilization mean of a host in a given Datacenter.
     */
    private void printCpuUtilizationForHost(Host host) {
        final double mipsByPe = host.getTotalMipsCapacity() / (double)host.getPesNumber();
        final double cpuUsageMean = host.getCpuUtilizationStats().getMean()*100;
        totalCpuUsageMean += cpuUsageMean;
        System.out.printf(
                "Host %d: PEs number: %2d MIPS by PE: %.0f CPU Utilization mean: %6.2f%%%n",
                host.getId(), host.getPesNumber(), mipsByPe, cpuUsageMean);
    }

    /**
     * Computes and print the cost ($) of resources (processing, bw, memory, storage)
     * for each VM inside the datacenter.
     */
    private void printTotalVmsCost(List<DatacenterBrokerSimple> brokerList) {
        System.out.println();
        double totalCost = 0.0;
        int totalNonIdleVms = 0;
        double processingTotalCost = 0, memoryTotaCost = 0, storageTotalCost = 0, bwTotalCost = 0;
        for (final Vm vm : brokerList.get(0).getVmCreatedList()) {
            final var cost = new VmCost(vm);
            processingTotalCost += cost.getProcessingCost();
            memoryTotaCost += cost.getMemoryCost();
            storageTotalCost += cost.getStorageCost();
            bwTotalCost += cost.getBwCost();

            totalCost += cost.getTotalCost();
            totalNonIdleVms += vm.getTotalExecutionTime() > 0 ? 1 : 0;
            System.out.println(cost);
        }

        System.out.printf(
                "Total cost ($) for %3d created VMs from %3d in DC %d: %8.2f$ %13.2f$ %17.2f$ %12.2f$ %15.2f$%n",
                totalNonIdleVms, brokerList.get(0).getVmsNumber(), brokerList.get(0).getId(),
                processingTotalCost, memoryTotaCost, storageTotalCost, bwTotalCost, totalCost);
    }

    /**
     * Creates VMs and Cloudlets for each broker.
     */
    private void createVmsAndCloudlets() {
        int i = 0;
        int brokerIndex = 0;
        for (final DatacenterBroker broker : brokerList) {
            createAndSubmitVms(broker, VM_PES[i++]);
            if (brokerIndex == 0) {
                createAndSubmitCloudlets(broker);
                brokerIndex++;
            }
            broker.addOnVmsCreatedListener(this::onVmsCreatedListener);
        }
    }

    /**
     * A listener that is called after all VMs from a broker are created,
     * setting the allocation policy to the default value
     * so that some Hosts will be overloaded with the placed VMs and migration will be fired.
     *
     * <p>The listener is removed after finishing, so that it's called just once,
     * even if new VMs are submitted and created later on.</p>
     */
    private void onVmsCreatedListener(final DatacenterBrokerEventInfo info) {
        final var broker = info.getDatacenterBroker();
        final String vmIds = broker.getVmCreatedList()
                .stream()
                .mapToLong(Vm::getId)
                .mapToObj(Long::toString)
                .collect(joining(", "));

        System.out.printf("# %d VMs submitted to %s have been created. VMs: %s.%n", broker.getVmCreatedList().size(), broker, vmIds);
        datacenterList.stream()
                .map(dc -> (VmAllocationPolicyWeightedLeastConnectionCustom)dc.getVmAllocationPolicy())
                .forEach(policy -> policy.setOverUtilizationThreshold(HOST_OVER_UTILIZATION_THRESHOLD_FOR_VM_MIGRATION));
        broker.removeOnVmsCreatedListener(info.getListener());
    }

    public void createAndSubmitCloudlets(final DatacenterBroker broker) {
        final var cloudletList = new ArrayList<Cloudlet>(VM_PES.length);
        final UtilizationModelDynamic um = createCpuUtilizationModel(CLOUDLET_INITIAL_CPU_PERCENTAGE, 1);
        for(final var vm: broker.getVmWaitingList()){
            createDataIntensiveCloudlets(vm, cloudletList, um);
//            for(int i=0; i<2; i++) {
//                final var cloudlet = createCloudlet(vm, um);
//                cloudletList.add(cloudlet);
//            }

        }
//        createCloudletsFromWorkloadFile(cloudletList, broker);


        broker.submitCloudletList(cloudletList);
    }

    public void createComputeIntensiveCloudlets(final Vm vm, List<Cloudlet> cloudletList, UtilizationModelDynamic um) {
        // Create compute-intensive cloudlets
        final var broker = vm.getBroker();
        for (int i = 0; i < 3; i++) {
            Cloudlet cloudlet = new CloudletSimple(createdCloudlets++, 100_000, vm.getPesNumber())
                    .setFileSize(300)
                    .setOutputSize(300)
                    .setUtilizationModelRam(new UtilizationModelDynamic(0.2))
                    .setUtilizationModelBw(new UtilizationModelDynamic(0.1))
                    .setUtilizationModelCpu(um);
            cloudletList.add(cloudlet);
            broker.bindCloudletToVm(cloudlet, vm);
        }
    }

    public void createDataIntensiveCloudlets(final Vm vm, List<Cloudlet> cloudletList, UtilizationModelDynamic um) {
        // Create data-intensive cloudlets
        final var broker = vm.getBroker();
        for (int i = 0; i < 3; i++) {
            Cloudlet cloudlet = new CloudletSimple(createdCloudlets++, 50_000, vm.getPesNumber())
                    .setFileSize(10_000)
                    .setOutputSize(10_000)
                    .setUtilizationModelRam(new UtilizationModelDynamic(0.4))
                    .setUtilizationModelBw(new UtilizationModelDynamic(0.4))
                    .setUtilizationModelCpu(um);
            cloudletList.add(cloudlet);
            broker.bindCloudletToVm(cloudlet, vm);
        }
    }

    private void createCloudletsFromWorkloadFile(List<Cloudlet> cloudletList, DatacenterBroker broker) {
        final var reader = SwfWorkloadFileReader.getInstance(WORKLOAD_FILENAME, VM_MIPS);
        reader.setMaxLinesToRead(maxCloudletsToCreateFromWorkloadFile);
        cloudletList = reader.generateWorkload();

        System.out.printf("# Created %12d Cloudlets for %s%n", cloudletList.size(), broker);
    }

    /**
     * Creates a Cloudlet.
     *
     * @param vm the VM that will run the Cloudlets
     * @param cpuUtilizationModel the CPU UtilizationModel for the Cloudlet
     * @return the created Cloudlets
     */
    public Cloudlet createCloudlet(final Vm vm, final UtilizationModel cpuUtilizationModel) {
        final var broker = vm.getBroker();
        final var cloudlet =
                new CloudletSimple(createdCloudlets++, CLOUDLET_LENGTH, vm.getPesNumber())
                        .setFileSize(CLOUDLET_FILESIZE)
                        .setOutputSize(CLOUDLET_OUTPUT_SIZE)
                        .setUtilizationModelRam(new UtilizationModelDynamic(0.4))
                        .setUtilizationModelBw(new UtilizationModelDynamic(0.2))
                        .setUtilizationModelCpu(cpuUtilizationModel);
        broker.bindCloudletToVm(cloudlet, vm);

        return cloudlet;
    }

    public void createAndSubmitVms(final DatacenterBroker broker, final int[] vmPesArray) {
        final var vmList = Arrays.stream(vmPesArray).mapToObj(this::createVm).toList();
        broker.submitVmList(vmList);
        vmList.forEach(vm -> vm.addOnMigrationStartListener(this::startMigration));
    }

    /**
     * A listener method that is called when a VM migration starts.
     * @param info information about the happened event
     *
     * @see Vm#addOnMigrationFinishListener(EventListener)
     */
    private void startMigration(final VmHostEventInfo info) {
        migrationsNumber++;
    }

    public Vm createVm(final int pes) {
        return new VmSimple(createdVms++, VM_MIPS, pes)
                .setRam(VM_RAM)
                .setBw(VM_BW)
                .setSize(VM_SIZE);
    }

    /**
     * Creates a CPU UtilizationModel for a Cloudlet.
     * If the initial usage is lower than the max usage, the usage will
     * be dynamically incremented along the time, according to the
     * {@link #getCpuUsageIncrement(UtilizationModelDynamic)}
     * function. Otherwise, the CPU usage will be static, according to the
     * defined initial usage.
     *
     * @param initialCpuUsagePercent the percentage of CPU utilization
     * that created Cloudlets will use when they start to execute.
     * If this value is greater than 1 (100%), it will be changed to 1.
     * @param maxCpuUsagePercentage the maximum percentage of
     * CPU utilization that created Cloudlets are allowed to use.
     * If this value is greater than 1 (100%), it will be changed to 1.
     * It must be equal or greater than the initial CPU usage.
     * @return
     */
    private UtilizationModelDynamic createCpuUtilizationModel(double initialCpuUsagePercent, double maxCpuUsagePercentage) {
        if(maxCpuUsagePercentage < initialCpuUsagePercent){
            throw new IllegalArgumentException("Max CPU usage must be equal or greater than the initial CPU usage.");
        }

        initialCpuUsagePercent = Math.min(initialCpuUsagePercent, 1);
        maxCpuUsagePercentage = Math.min(maxCpuUsagePercentage, 1);
        final UtilizationModelDynamic um;
        if (initialCpuUsagePercent < maxCpuUsagePercentage) {
            um = new UtilizationModelDynamic(initialCpuUsagePercent)
                    .setUtilizationUpdateFunction(this::getCpuUsageIncrement);
        }
        else um = new UtilizationModelDynamic(initialCpuUsagePercent);

        um.setMaxResourceUtilization(maxCpuUsagePercentage);
        return um;
    }

    /**
     * Increments the CPU resource utilization, that is defined in percentage values.
     * @return the new resource utilization after the increment
     */
    private double getCpuUsageIncrement(final UtilizationModelDynamic um){
        return um.getUtilization() + um.getTimeSpan()*CLOUDLET_CPU_INCREMENT_PER_SECOND;
    }

    private List<Datacenter> createDatacenters() {
        final int datacentersNumber = DC_HOST_PES.length;
        return IntStream.range(0, datacentersNumber).mapToObj(this::createDatacenter).toList();
    }

    /**
     * Create a private-cloud Datacenter for even indexes and public-cloud one for odd indexes.
     * @param index the Datacenter index
     * @return the created Datacenter
     * @see DatacenterCharacteristics.Distribution
     */
    private Datacenter createDatacenter(final int index) {
        final var distribution = index % 2 == 0 ? Distribution.PRIVATE : Distribution.PUBLIC;
        final var hostList = createHosts(DC_HOST_PES[index]);
        final var allocationPolicy = createVmAllocationPolicy();
        final var dc = new DatacenterSimple(simulation, hostList, allocationPolicy);
        dc.setSchedulingInterval(SCHEDULING_INTERVAL)
                .setHostSearchRetryDelay(HOST_SEARCH_RETRY_DELAY)
                // t2.micro
                .setCharacteristics(new DatacenterCharacteristicsSimple(0.01, 0.0001, 0.00001, 0.00001))
                // t2.large
                // .setCharacteristics(new DatacenterCharacteristicsSimple(0.02, 0.0002, 0.00002, 0.00002))
                .getCharacteristics()
                .setDistribution(distribution);

        final String hostsStr =
                hostList.stream()
                        .map(host -> String.format("Host %d w/ %d PEs", host.getId(), host.getPesNumber()))
                        .collect(joining(", "));
        System.out.printf("%7s-cloud %s: %s%n", distribution, dc, hostsStr);
        return dc;
    }

    private List<Host> createHosts(final int[] pesNumberArray) {
        final var hostList = new ArrayList<Host>(DC_HOST_PES.length);
        for (int i = 0; i < pesNumberArray.length; i++) {
            final long ram = HOST_RAM[i];
            hostList.add(createHost(pesNumberArray[i], ram));
        }

        return hostList;
    }

    public Host createHost(final int pesNumber, final long ram) {
        final var peList = createPeList(pesNumber);
        final var host = new HostSimple(ram, HOST_BW, HOST_STORAGE, peList);
        host.setId(createdHosts++)
                .setVmScheduler(new VmSchedulerTimeShared())
                .setStateHistoryEnabled(true);
        host.enableUtilizationStats();
        return host;
    }

    public List<Pe> createPeList(final int pesNumber) {
        final var peList = new ArrayList<Pe>(pesNumber);
        for(int i = 0; i < pesNumber; i++) {
            peList.add(new PeSimple(HOST_MIPS));
        }

        return peList;
    }

    public void allocateVmsToHosts() {
        for (Datacenter datacenter : datacenterList) {
            VmAllocationPolicyMinMinCustom allocationPolicy = (VmAllocationPolicyMinMinCustom) datacenter.getVmAllocationPolicy();
            for (DatacenterBroker broker : brokerList) {
                allocationPolicy.allocateHostForVmInternal(broker.getVmWaitingList());
            }
        }
    }
}
