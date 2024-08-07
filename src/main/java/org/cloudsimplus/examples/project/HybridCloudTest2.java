package org.cloudsimplus.examples.project;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyRoundRobin;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicySimple;
import org.cloudsimplus.autoscaling.HorizontalVmScaling;
import org.cloudsimplus.autoscaling.HorizontalVmScalingSimple;
import org.cloudsimplus.autoscaling.VerticalVmScaling;
import org.cloudsimplus.autoscaling.VerticalVmScalingSimple;
import org.cloudsimplus.autoscaling.resources.ResourceScaling;
import org.cloudsimplus.autoscaling.resources.ResourceScalingGradual;
import org.cloudsimplus.autoscaling.resources.ResourceScalingInstantaneous;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.builders.tables.HostHistoryTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.core.Identifiable;
import org.cloudsimplus.core.Simulation;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterCharacteristics;
import org.cloudsimplus.datacenters.DatacenterCharacteristicsSimple;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.distributions.ContinuousDistribution;
import org.cloudsimplus.distributions.UniformDistr;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.listeners.EventListener;
import org.cloudsimplus.provisioners.ResourceProvisionerSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.resources.Processor;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerSpaceShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModel;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.utilizationmodels.UtilizationModelStochastic;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmCost;
import org.cloudsimplus.vms.VmSimple;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Comparator.comparingDouble;

/**
 * An example that balances load by dynamically creating VMs,
 * according to the arrival of Cloudlets.
 * Cloudlets are {@link #createNewComputeCloudlets(EventInfo) dynamically created and submitted to the broker
 * at specific time intervals}.
 *
 * <p>A {@link HorizontalVmScalingSimple}
 * is set to each {@link #createListOfScalableVms(int) initially created VM},
 * that will check at {@link #SCHEDULING_INTERVAL specific time intervals}
 * if the VM {@link #isVmOverloaded(Vm) is overloaded or not} to then
 * request the creation of a new VM to attend arriving Cloudlets.</p>
 *
 * <p>The example uses CloudSim Plus {@link EventListener} feature
 * to enable monitoring the simulation and dynamically creating objects such as Cloudlets and VMs.
 * It relies on
 * <a href="http://www.oracle.com/webfolder/technetwork/tutorials/obe/java/Lambda-QuickStart/index.html">Java 8 Lambda Expressions</a>
 * to set a Listener for the {@link Simulation#addOnClockTickListener(EventListener) onClockTick event}.
 * That Listener gets notified every time the simulation clock advances and then creates and submits new Cloudlets.
 * </p>
 *
 * <p>The {@link DatacenterBroker} is accountable to perform horizontal down scaling.
 * The down scaling is enabled by setting a {@link Function} using the {@link DatacenterBroker#setVmDestructionDelayFunction(Function)}.
 * This Function defines the time the broker has to wait to destroy a VM after it becomes idle.
 * If no Function is set, the broker just destroys VMs after all running Cloudlets are finished
 * and there is no Cloudlet waiting to be created.
 * </p>
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 1.0
 */
public class HybridCloudTest2 {
    /**
     * The interval in which the Datacenter will schedule events.
     * As lower is this interval, sooner the processing of VMs and Cloudlets
     * is updated and you will get more notifications about the simulation execution.
     * However, that also affect the simulation performance.
     *
     * <p>A large schedule interval, such as 15, will make that just
     * at every 15 seconds the processing of VMs is updated. If a VM is overloaded, just
     * after this time the creation of a new one will be requested
     * by the VM's {@link HorizontalVmScaling Horizontal Scaling} mechanism.</p>
     *
     * <p>If this interval is defined using a small value, you may get
     * more dynamically created VMs than expected.
     * Accordingly, this value has to be trade-off.
     * For more details, see {@link Datacenter#getSchedulingInterval()}.</p>
     */
    private static final int SCHEDULING_INTERVAL = 2;


    private static final double VM_OVERLOAD_THRESHOLD = 0.7;
    private static final double DC_OVERLOAD_THRESHOLD = 0.7;

    /**
     * The interval to request the creation of new Cloudlets.
     */
    private static final int CLOUDLETS_CREATION_INTERVAL = SCHEDULING_INTERVAL * 2;

//    private static final int HOSTS = 2;
//    private static final int HOST_PES = 8;

    private static final int VMS = 4;
    private static final int CLOUDLETS = 4;
    private final CloudSimPlus simulation;
    private final DatacenterBroker broker0;
    private final List<Vm> vmList;
    private final List<Cloudlet> cloudletList;

    /**
     * Different lengths that will be randomly assigned to created Cloudlets.
     */
    private static final long[] CLOUDLET_COMPUTE_LENGTHS = {1000000, 1200000, 2000000, 2500000, 1700000, 900000, 1500000};
    private static final long[] CLOUDLET_DATA_LENGTHS = {10000, 15000, 20000, 27000, 8000, 6000, 30000};
    private static final long[] CLOUDLET_MIX_LENGTHS = {50000, 60000, 70000, 45000, 67000, 55000, 37000};

    private final ContinuousDistribution rand;

    private int createdCloudlets;
    private int createsVms;
    private List<Datacenter> datacenterList;
    private static final int CLOUDLETS_INITIAL_LENGTH = 20_000;

    private int lastHostIndex;


    private double totalCpuUsageMean;

    //ACO variables

    private static final int NUM_ANTS = 10;
    private static final int MAX_ITERATIONS = 100;
    private static final double ALPHA = 1.0;
    private static final double BETA = 2.0;
    private static final double EVAPORATION_RATE = 0.5;
    private double[][] pheromones;
    private double[][] heuristic;
    private Random random;

    //Genetic variables

    private static final int POPULATION_SIZE = 50;
    private static final int MAX_GENERATIONS = 100;
    private static final double MUTATION_RATE = 0.01;
    private static final double CROSSOVER_RATE = 0.9;


    private int[][] DC_HOST_PES = {{16, 32, 16, 32}, {16, 32, 16, 64, 32}};
    private static final String TYPE = "Compute";
    private static final int DC_PES_TYPE = 4;
    private static final int MAX_CLOUDLETS = 180;
    private static final int MAX_TIME = 500;

    public static void main(String[] args) {
        new org.cloudsimplus.examples.project.HybridCloudTest2();
    }

    /**
     * Default constructor that builds the simulation scenario and starts the simulation.
     */
    private HybridCloudTest2() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        /*You can remove the seed parameter to get a dynamic one, based on current computer time.
         * With a dynamic seed you will get different results at each simulation run.*/
        final long seed = 1;

        if (DC_PES_TYPE == 0) {
            DC_HOST_PES = new int[][]{{16, 32, 16, 32}, {16, 32, 16, 32, 64}};
        } else if (DC_PES_TYPE == 1) {
            DC_HOST_PES = new int[][]{{16, 32, 16, 32}, {8, 32, 16, 16, 48}};
        } else if (DC_PES_TYPE == 2) {
            DC_HOST_PES = new int[][]{{16, 32, 16, 32}, {32, 16, 8, 32, 32}};
        } else if (DC_PES_TYPE == 3){
            DC_HOST_PES = new int[][]{{16, 32, 16, 32}, {16, 16, 16, 32, 64}};
        } else {
            DC_HOST_PES = new int[][]{{16, 32, 16, 32}, {32, 32, 16, 16, 8}};
        }

//        rand = new UniformDistr(0, CLOUDLET_LENGTHS.length);
        vmList = new ArrayList<>(VMS);
        cloudletList = new ArrayList<>(CLOUDLETS);

        //random for Aco allocation
        this.random = new Random();

        simulation = new CloudSimPlus();
        if(TYPE.equals("Compute")) {
            rand = new UniformDistr(0, CLOUDLET_COMPUTE_LENGTHS.length, seed);
            simulation.addOnClockTickListener(this::createNewComputeCloudlets);

        } else if (TYPE.equals("Data")) {
            rand = new UniformDistr(0, CLOUDLET_DATA_LENGTHS.length, seed);
            simulation.addOnClockTickListener(this::createNewDataCloudlets);

        } else {
            rand = new UniformDistr(0, CLOUDLET_MIX_LENGTHS.length, seed);
            simulation.addOnClockTickListener(this::createNewMixCloudlets);

        }

//        simulation.addOnClockTickListener(this::onClockTickListener);

        datacenterList = createDatacenters();
        broker0 = createBroker();

        /**
         * Defines the Vm Destruction Delay Function as a lambda expression
         * so that the broker will wait 10 seconds before destroying any idle VM.
         * By commenting this line, no down scaling will be performed
         * and idle VMs will be destroyed just after all running Cloudlets
         * are finished and there is no waiting Cloudlet.
         * @see DatacenterBroker#setVmDestructionDelayFunction(Function)
         * */
        broker0.setVmDestructionDelay(30.0);

        vmList.addAll(createListOfScalableVms(VMS));

        createCloudletList(TYPE);
//        createCloudletListsWithDifferentDelays();
        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        simulation.start();

        printSimulationResults();

        printPerformanceStatistics(broker0.getCloudletFinishedList());
        printTotalVmsCost(broker0);
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
    private void printTotalVmsCost(DatacenterBroker broker) {
        System.out.println();
        double totalCost = 0.0;
        int totalNonIdleVms = 0;
        double processingTotalCost = 0, memoryTotaCost = 0, storageTotalCost = 0, bwTotalCost = 0, privateDcCost = 0;
        for (final Vm vm : broker.getVmCreatedList()) {
            if(vm.getHost().getDatacenter().getCharacteristics().getDistribution() == DatacenterCharacteristics.Distribution.PUBLIC) {
                final var cost = new VmCost(vm);
                processingTotalCost += cost.getProcessingCost();
                memoryTotaCost += cost.getMemoryCost();
                storageTotalCost += cost.getStorageCost();
                bwTotalCost += cost.getBwCost();

                totalCost += cost.getTotalCost();
                totalNonIdleVms += vm.getTotalExecutionTime() > 0 ? 1 : 0;
            } else {
                privateDcCost += vm.getTotalExecutionTime() * 0.01;
                totalCost += vm.getTotalExecutionTime() * 0.01;
            }

        }

        System.out.printf(
                "Total cost ($) for %3d created VMs from %3d: %8.2f$ %13.2f$ %17.2f$ %12.2f$ %12.2f$ %15.2f$%n",
                totalNonIdleVms, broker.getVmsNumber(),
                processingTotalCost, memoryTotaCost, storageTotalCost, bwTotalCost, privateDcCost, totalCost);
    }

    private DatacenterBrokerSimple createBroker() {
        final var broker = new DatacenterBrokerSimple(simulation);
        broker.setName("Broker %d".formatted(broker.getId()));

        // Sets the initial target Datacenter
//        broker.setLastSelectedDc(datacenterList.get(0));
        broker.setDatacenterMapper(customDatacenterMapper());
        return broker;
    }

    private BiFunction<Datacenter, Vm, Datacenter> customDatacenterMapper() {
        return (lastSelectedDc, vm) -> {
            if (isDatacenterUnderUtilized(datacenterList.get(0)) && hasSuitableHost(datacenterList.get(0), vm)) {
                return datacenterList.get(0);
            } else if (isDatacenterUnderUtilized(datacenterList.get(1)) && hasSuitableHost(datacenterList.get(1), vm)) {
                return datacenterList.get(1);
            }
            return Datacenter.NULL;
        };
    }

    private boolean isDatacenterUnderUtilized(Datacenter datacenter) {
        return datacenter.getHostList().stream()
                .mapToDouble(Host::getCpuPercentUtilization)
                .average()
                .orElse(0) < DC_OVERLOAD_THRESHOLD;
    }

    private boolean hasSuitableHost(Datacenter datacenter, Vm vm) {
        return datacenter.getHostList().stream().anyMatch(host -> host.isSuitableForVm(vm));
    }

    private List<Datacenter> createDatacenters(){
        return IntStream.range(0, 2)
                .mapToObj(this::createDatacenter)
                .toList();
    }

    private void printSimulationResults() {
        final var cloudletFinishedList = broker0.getCloudletFinishedList();
        final Comparator<Cloudlet> sortByCloudletId = comparingDouble(Identifiable::getId);
        final Comparator<Cloudlet> sortByVmId = comparingDouble(c -> c.getVm().getId());
        final Comparator<Cloudlet> sortByStartTime = comparingDouble(Cloudlet::getStartTime);
        cloudletFinishedList.sort(sortByCloudletId.thenComparing(sortByStartTime));

        new CloudletsTableBuilder(cloudletFinishedList).build();

        System.out.println("Total finished cloudlet count is " + cloudletFinishedList.size());

//        printHostStateHistory();
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


    private void createCloudletList(String type) {
        for (int i = 0; i < CLOUDLETS; i++) {
            if(type.equals("compute")) {
                cloudletList.add(createComputeCloudlet(i*2));
            } else if (type.equals("data")) {
                cloudletList.add(createDataCloudlet(i*2));
            } else {
                cloudletList.add(createMixCloudlet(i*2));
            }
        }
    }

    /**
     * Creates new Cloudlets at every {@link #CLOUDLETS_CREATION_INTERVAL} seconds, up to the 50th simulation second.
     * A reference to this method is set as the {@link EventListener}
     * to the {@link Simulation#addOnClockTickListener(EventListener)}.
     * The method is called every time the simulation clock advances.
     *
     * @param info the information about the OnClockTick event that has happened
     */
    private void createNewComputeCloudlets(final EventInfo info) {
        final long time = (long) info.getTime();
        if (time % 10 == 0 && time <= MAX_TIME) {
            final int cloudletsNumber = 4;
            System.out.printf("\t#Creating %d Cloudlets at time %d.%n", cloudletsNumber, time);
            final List<Cloudlet> newCloudlets = new ArrayList<>(cloudletsNumber);
            for (int i = 0; i < cloudletsNumber; i++) {
                if (cloudletList.size() < MAX_CLOUDLETS) {
                    final var cloudlet = createComputeCloudlet(i*2);
                    cloudletList.add(cloudlet);
                    newCloudlets.add(cloudlet);
                }
            }

            broker0.submitCloudletList(newCloudlets);
        }
    }

    private void createNewDataCloudlets(final EventInfo info) {
        final long time = (long) info.getTime();
        if (time % 10 == 0 && time <= MAX_TIME) {
            final int cloudletsNumber = 4;
            System.out.printf("\t#Creating %d Cloudlets at time %d.%n", cloudletsNumber, time);
            final List<Cloudlet> newCloudlets = new ArrayList<>(cloudletsNumber);
            for (int i = 0; i < cloudletsNumber; i++) {
                if (cloudletList.size() < MAX_CLOUDLETS) {
                    final var cloudlet = createDataCloudlet(i*2);
                    cloudletList.add(cloudlet);
                    newCloudlets.add(cloudlet);
                }
            }

            broker0.submitCloudletList(newCloudlets);
        }
    }

    private void createNewMixCloudlets(final EventInfo info) {
        final long time = (long) info.getTime();
        if (time % 10 == 0 && time <= MAX_TIME) {
            final int cloudletsNumber = 4;
            System.out.printf("\t#Creating %d Cloudlets at time %d.%n", cloudletsNumber, time);
            final List<Cloudlet> newCloudlets = new ArrayList<>(cloudletsNumber);
            for (int i = 0; i < cloudletsNumber; i++) {
                if (cloudletList.size() < MAX_CLOUDLETS) {
                    final var cloudlet = createMixCloudlet(i*2);
                    cloudletList.add(cloudlet);
                    newCloudlets.add(cloudlet);
                }
            }

            broker0.submitCloudletList(newCloudlets);
        }
    }

    private void createCloudletListsWithDifferentDelays() {
        final int initialCloudletsNumber = (int)(CLOUDLETS/2.5);
        final int remainingCloudletsNumber = CLOUDLETS-initialCloudletsNumber;
        //Creates a List of Cloudlets that will start running immediately when the simulation starts
        for (int i = 0; i < initialCloudletsNumber; i++) {
            cloudletList.add(createCloudletDelay(CLOUDLETS_INITIAL_LENGTH+(i*1000), 2));
        }

        /*
         * Creates several Cloudlets, increasing the arrival delay and decreasing
         * the length of each one.
         * The progressing delay enables CPU usage to increase gradually along the arrival of
         * new Cloudlets (triggering CPU up scaling at some point in time).
         *
         * The decreasing length enables Cloudlets to finish in different times,
         * to gradually reduce CPU usage (triggering CPU down scaling at some point in time).
         *
         * Check the logs to understand how the scaling is working.
         */
        for (int i = 1; i <= remainingCloudletsNumber; i++) {
            cloudletList.add(createCloudletDelay(CLOUDLETS_INITIAL_LENGTH*2/i, 1,i*2));
        }
    }

    private Cloudlet createCloudletDelay(final long length, final int pesNumber) {
        return createCloudletDelay(length, pesNumber, 0);
    }

    private Cloudlet createCloudletDelay(final long length, final int pesNumber, final double delay) {
        /*
        Since a VM PE isn't used by two Cloudlets at the same time,
        the Cloudlet can use 100% of that CPU capacity at the time
        it is running. Even if a CloudletSchedulerTimeShared is used
        to share the same VM PE among multiple Cloudlets,
        just one Cloudlet uses the PE at a time.
        Then it is preempted to enable other Cloudlets to use such a VM PE.
         */
        final var utilizationCpu = new UtilizationModelFull();

        /**
         * Since BW e RAM are shared resources that don't enable preemption,
         * two Cloudlets can't use the same portion of such resources at the same time
         * (unless virtual memory is enabled, but such a feature is not available in simulation).
         * This way, the total capacity of such resources is being evenly split among created Cloudlets.
         * If there are 10 Cloudlets, each one will use just 10% of such resources.
         * This value can be defined in different ways, as you want. For instance, some Cloudlets
         * can require more resources than other ones.
         * To enable that, you would need to instantiate specific {@link UtilizationModelDynamic} for each Cloudlet,
         * use a {@link UtilizationModelStochastic} to define resource usage randomly,
         * or use any other {@link UtilizationModel} implementation.
         */
        final var utilizationModelDynamic = new UtilizationModelDynamic(1.0/CLOUDLETS);
        final var cl = new CloudletSimple(length, pesNumber);
        cl.setFileSize(1024)
                .setOutputSize(1024)
                .setUtilizationModelBw(utilizationModelDynamic)
                .setUtilizationModelRam(utilizationModelDynamic)
                .setUtilizationModelCpu(utilizationCpu)
                .setSubmissionDelay(delay);
        return cl;
    }

    /**
     * Creates a Datacenter and its Hosts.
     */
    private Datacenter createDatacenter(int index) {
        final var distribution = index % 2 == 0 ? DatacenterCharacteristics.Distribution.PRIVATE : DatacenterCharacteristics.Distribution.PUBLIC;
        final var newHostList = new ArrayList<Host>(DC_HOST_PES[index].length);
        final var allocationPolicy = new VmAllocationPolicyRoundRobin();
//        allocationPolicy.setFindHostForVmFunction(this::findGeneticHostForVm);
        for (int i = 0; i < DC_HOST_PES[index].length; i++) {
            newHostList.add(createHost(DC_HOST_PES[index][i]));
        }
        final var dc = new DatacenterSimple(simulation, newHostList, allocationPolicy);
        dc.setSchedulingInterval(SCHEDULING_INTERVAL)
            .setHostSearchRetryDelay(10)
            .setCharacteristics(new DatacenterCharacteristicsSimple(0.01, 0.0001, 0.00001, 0.00001))
            .getCharacteristics()
            .setDistribution(distribution);


        return dc;
    }

    private Host createHost(int pesNumber) {
        final var peList = new ArrayList<Pe>(pesNumber);
        for (int i = 0; i < pesNumber; i++) {
            peList.add(new PeSimple(1000));
        }

        final long ram = 2048; // in Megabytes
        final long storage = 1000000; // in Megabytes
        final long bw = 10000; //in Megabits/s
        var host =  new HostSimple(ram, bw, storage, peList).setStateHistoryEnabled(true);
        host.enableUtilizationStats();
        return host;
    }

    /**
     * Creates a list of initial VMs in which each VM is able to scale horizontally
     * when it is overloaded.
     *
     * @param vmsNumber number of VMs to create
     * @return the list of scalable VMs
     * @see #createHorizontalVmScaling(Vm)
     */
    private List<Vm> createListOfScalableVms(final int vmsNumber) {
        final var newVmList = new ArrayList<Vm>(vmsNumber);
        for (int i = 0; i < vmsNumber; i++) {
            final Vm vm = createVm();
            createHorizontalVmScaling(vm);
            newVmList.add(vm);
        }

        return newVmList;
    }

    /**
     * Creates a {@link HorizontalVmScaling} object for a given VM.
     *
     * @param vm the VM for which the Horizontal Scaling will be created
     * @see #createListOfScalableVms(int)
     */
    private void createHorizontalVmScaling(final Vm vm) {
        final var horizontalScaling = new HorizontalVmScalingSimple();
        horizontalScaling
                .setVmSupplier(this::createVm)
                .setOverloadPredicate(this::isVmOverloaded);
        vm.setHorizontalScaling(horizontalScaling);
        vm.setPeVerticalScaling(createVerticalPeScaling());
    }

    private VerticalVmScaling createVerticalPeScaling() {
        //The percentage in which the number of PEs has to be scaled
        final double scalingFactor = 0.1;
        VerticalVmScalingSimple verticalCpuScaling = new VerticalVmScalingSimple(Processor.class, scalingFactor);

        /* By uncommenting the line below, you will see that, instead of gradually
         * increasing or decreasing the number of PEs, when the scaling object detects
         * the CPU usage is above or below the defined thresholds,
         * it will automatically calculate the number of PEs to add/remove to
         * move the VM from the over or underload condition.
         */
        verticalCpuScaling.setResourceScaling(new ResourceScalingInstantaneous());

        /** Different from the commented line above, the line below implements a ResourceScaling using a Lambda Expression.
         * It is just an example which scales the resource twice the amount defined by the scaling factor
         * defined in the constructor.
         *
         * Realize that if the setResourceScaling method is not called, a ResourceScalingGradual will be used,
         * which scales the resource according to the scaling factor.
         * The lower and upper thresholds after this line can also be defined using a Lambda Expression.
         *
         * So, here we are defining our own {@link ResourceScaling} instead of
         * using the available ones such as the {@link ResourceScalingGradual}
         * or {@link ResourceScalingInstantaneous}.
         */
        verticalCpuScaling.setResourceScaling(vs -> 2*vs.getScalingFactor()*vs.getAllocatedResource());

        verticalCpuScaling.setLowerThresholdFunction(this::lowerCpuUtilizationThreshold);
        verticalCpuScaling.setUpperThresholdFunction(this::upperCpuUtilizationThreshold);

        return verticalCpuScaling;
    }

    /**
     * Defines the minimum CPU utilization percentage that indicates a Vm is underloaded.
     * This function is using a statically defined threshold, but it would be defined
     * a dynamic threshold based on any condition you want.
     * A reference to this method is assigned to each Vertical VM Scaling created.
     *
     * @param vm the VM to check if its CPU is underloaded.
     *        <b>The parameter is not being used internally, which means the same
     *        threshold is used for any Vm.</b>
     * @return the lower CPU utilization threshold
     * @see #createVerticalPeScaling()
     */
    private double lowerCpuUtilizationThreshold(final Vm vm) {
        return 0.4;
    }

    /**
     * Defines a dynamic CPU utilization threshold that indicates a Vm is overloaded.
     * Such a threshold is the maximum CPU a VM can use before requesting vertical CPU scaling.
     * A reference to this method is assigned to each Vertical VM Scaling created.
     *
     * <p>The dynamic upper threshold is defined as 20% above the mean (mean * 1.2),
     * if there are at least 10 CPU utilization history entries.
     * That means if the CPU utilization of a VM is 20% above its mean
     * CPU utilization, it indicates the VM is overloaded.
     * If there aren't enough history entries,
     * it defines a static threshold as 70% of CPU utilization.</p>
     *
     * @param vm the VM to check if its CPU is overloaded.
     *        The parameter is not being used internally, that means the same
     *        threshold is used for any Vm.
     * @return the upper dynamic CPU utilization threshold
     * @see #createVerticalPeScaling()
     */
    private double upperCpuUtilizationThreshold(final Vm vm) {
        return vm.getCpuUtilizationStats().count() > 10 ? vm.getCpuUtilizationStats().getMean() * 1.2 : 0.7;
    }

    private void onClockTickListener(EventInfo evt) {
        vmList.forEach(vm -> {
            System.out.printf(
                    "\t\tTime %6.1f: Vm %d CPU Usage: %6.2f%% (%2d vCPUs. Running Cloudlets: #%02d) Upper Threshold: %.2f%n",
                    evt.getTime(), vm.getId(), vm.getCpuPercentUtilization()*100.0,
                    vm.getPesNumber(),
                    vm.getCloudletScheduler().getCloudletExecList().size(),
                    vm.getPeVerticalScaling().getUpperThresholdFunction().apply(vm));
        });
    }

    /**
     * Creates lists of Cloudlets to be submitted to the broker with different delays,
     * simulating their arrivals at different times.
     * Adds all created Cloudlets to the {@link #cloudletList}.
     */

    /**
     * A {@link Predicate} that checks if a given VM is overloaded or not,
     * based on upper CPU utilization threshold.
     * A reference to this method is assigned to each {@link HorizontalVmScaling} created.
     *
     * @param vm the VM to check if it is overloaded
     * @return true if the VM is overloaded, false otherwise
     * @see #createHorizontalVmScaling(Vm)
     */
    private boolean isVmOverloaded(final Vm vm) {
        return vm.getCpuPercentUtilization() > VM_OVERLOAD_THRESHOLD;
    }

    /**
     * Creates a Vm object.
     *
     * @return the created Vm
     */
    private Vm createVm() {
        final int id = createsVms++;
        return new VmSimple(id, 1000, 4)
                .setRam(512).setBw(1000).setSize(10000)
                .setCloudletScheduler(new CloudletSchedulerTimeShared());
    }

    private Cloudlet createComputeCloudlet(int delay) {
        final int id = createdCloudlets++;
        final var utilizadionModelDynamic = new UtilizationModelDynamic(0.1);

        //randomly selects a length for the cloudlet
        final long length = CLOUDLET_COMPUTE_LENGTHS[(int) rand.sample()];
        final var cl =  new CloudletSimple(id, length, 2)
                .setFileSize(300)
                .setOutputSize(300)
                .setUtilizationModelBw(utilizadionModelDynamic)
                .setUtilizationModelRam(utilizadionModelDynamic)
                .setUtilizationModelCpu(new UtilizationModelFull());
        cl.setSubmissionDelay(delay);
        return cl;
    }

    private Cloudlet createDataCloudlet(int delay) {
        final int id = createdCloudlets++;
        final var utilizadionModelDynamic = new UtilizationModelDynamic(0.1);

        //randomly selects a length for the cloudlet
        final long length = CLOUDLET_DATA_LENGTHS[(int) rand.sample()];
        final var cl =  new CloudletSimple(id, length, 2)
                .setFileSize(1000000)
                .setOutputSize(1000000)
                .setUtilizationModelBw(utilizadionModelDynamic)
                .setUtilizationModelRam(utilizadionModelDynamic)
                .setUtilizationModelCpu(new UtilizationModelFull());
        cl.setSubmissionDelay(delay);
        return cl;
    }

    private Cloudlet createMixCloudlet(int delay) {
        final int id = createdCloudlets++;
        final var utilizadionModelDynamic = new UtilizationModelDynamic(0.1);

        //randomly selects a length for the cloudlet
        final long length = CLOUDLET_MIX_LENGTHS[(int) rand.sample()];
        final var cl =  new CloudletSimple(id, length, 2)
                .setFileSize(500000)
                .setOutputSize(500000)
                .setUtilizationModelBw(utilizadionModelDynamic)
                .setUtilizationModelRam(utilizadionModelDynamic)
                .setUtilizationModelCpu(new UtilizationModelFull());
        cl.setSubmissionDelay(delay);
        return cl;
    }

    private Optional<Host> findLeastConnectionHostForVm(final VmAllocationPolicy vmAllocationPolicy, final Vm vm) {

        List<Host> hostList = vmAllocationPolicy.getHostList();
        Host leastHost = null;

        for (Host host : hostList) {
            List<Vm> activeVms = getActiveVms(host.getVmList());
            System.out.println("Checking host: " + host + " utlization " + host.getCpuPercentUtilization());
            if (host.isSuitableForVm(vm) && host.getCpuPercentUtilization() < DC_OVERLOAD_THRESHOLD) {
                System.out.println("Host " + host + " is suitable for VM " + vm.getId());
                if (leastHost == null || host.getCpuMipsUtilization() < leastHost.getCpuMipsUtilization()) {
                    leastHost = host;
                }
            }
        }

        if (leastHost != null) {
            System.out.println("Selected host for VM " + vm.getId() + ": " + leastHost);
        } else {
            System.out.println("No suitable host found for VM " + vm.getId());
        }

        return Optional.ofNullable(leastHost);
    }

    private List<Vm> getActiveVms(List<Vm> vmList) {
        return vmList.stream()
                .filter(Vm::isIdle)
                .collect(Collectors.toList());
    }

    private Optional<Host> findWeightedLeastConnectionHostForVm(final VmAllocationPolicy vmAllocationPolicy, final Vm vm) {
        List<Host> hostList = vmAllocationPolicy.getHostList();
        Host leastHost = null;
        double leastWeight = Double.MAX_VALUE;

        for (Host host : hostList) {
            System.out.println("utlization of " + host + " - " + host.getCpuPercentUtilization());
            if (host.isSuitableForVm(vm) && host.getCpuPercentUtilization() < DC_OVERLOAD_THRESHOLD) {
                double weight = calculateHostConnectionWeight(host);
                if (leastHost == null || weight < leastWeight) {
                    leastHost = host;
                    leastWeight = weight;
                }
            }
        }

        return Optional.ofNullable(leastHost);
    }

    private double calculateHostConnectionWeight(Host host) {
        List<Vm> activeVms = getActiveVms(host.getVmList());
        double totalHostCapacity = host.getTotalMipsCapacity();
        double totalActiveVmCpuUsage = activeVms.stream().mapToDouble(Vm::getTotalMipsCapacity).sum();

        return (1 / (totalHostCapacity - totalActiveVmCpuUsage));
    }

    private Optional<Host> findDynamicRoundRobinHostForVm(final VmAllocationPolicy vmAllocationPolicy, final Vm vm) {
        List<Host> hostList = vmAllocationPolicy.getHostList();
        int maxTries = hostList.size();
        Host bestHost = null;
        double bestWeight = Double.MAX_VALUE;

        for (int i = 0; i < maxTries; ++i) {
            Host host = hostList.get(this.lastHostIndex);
            this.lastHostIndex = (this.lastHostIndex + 1) % hostList.size();

            if (host.isSuitableForVm(vm) && host.getCpuPercentUtilization() < DC_OVERLOAD_THRESHOLD) {
                double weight = calculateHostWeight(host);
                System.out.println("Weight of host " + host + " is " + weight);
                if (bestHost == null || weight > bestWeight) {
                    bestHost = host;
                    bestWeight = weight;
                }
            }
        }

        return Optional.ofNullable(bestHost);
    }

    private double calculateHostWeight(Host host) {
        // Example weight calculation based on CPU, memory, and network usage
         return host.getTotalMipsCapacity();
    }

    private Optional<Host> findAcoHostForVm(final VmAllocationPolicy vmAllocationPolicy, final Vm vm) {
        List<Host> hostList = vmAllocationPolicy.getHostList();;
        initializePheromones(hostList.size());
        initializeHeuristic(hostList, vm);

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            double[][] antSolutions = new double[NUM_ANTS][hostList.size()];

            for (int ant = 0; ant < NUM_ANTS; ant++) {
                constructSolution(antSolutions[ant], hostList, vm);
            }

            updatePheromones(antSolutions, hostList, vm);
        }

        return selectBestHost(hostList, vm);

    }

    private void initializePheromones(int size) {
        pheromones = new double[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                pheromones[i][j] = 1.0;
            }
        }
    }

    private void initializeHeuristic(List<Host> hostList, Vm vm) {
        heuristic = new double[hostList.size()][hostList.size()];
        for (int i = 0; i < hostList.size(); i++) {
            for (int j = 0; j < hostList.size(); j++) {
                heuristic[i][j] = 1.0 / (hostList.get(j).getTotalAvailableMips() - vm.getMips() + 1);
            }
        }
    }

    private void constructSolution(double[] solution, List<Host> hostList, Vm vm) {
        for (int i = 0; i < hostList.size(); i++) {
            solution[i] = chooseNextHost(i, hostList, vm);
        }
    }

    private int chooseNextHost(int currentIndex, List<Host> hostList, Vm vm) {
        double[] probabilities = new double[hostList.size()];
        double sum = 0.0;

        for (int j = 0; j < hostList.size(); j++) {
            probabilities[j] = Math.pow(pheromones[currentIndex][j], ALPHA) * Math.pow(heuristic[currentIndex][j], BETA);
            sum += probabilities[j];
        }

        for (int j = 0; j < hostList.size(); j++) {
            probabilities[j] /= sum;
        }

        double randomValue = random.nextDouble();
        double cumulativeProbability = 0.0;

        for (int j = 0; j < hostList.size(); j++) {
            cumulativeProbability += probabilities[j];
            if (randomValue <= cumulativeProbability) {
                return j;
            }
        }

        return hostList.size() - 1; // Should not reach here
    }

    private void updatePheromones(double[][] antSolutions, List<Host> hostList, Vm vm) {
        for (int i = 0; i < pheromones.length; i++) {
            for (int j = 0; j < pheromones[i].length; j++) {
                pheromones[i][j] *= (1.0 - EVAPORATION_RATE);
            }
        }

        for (int ant = 0; ant < NUM_ANTS; ant++) {
            double solutionQuality = evaluateSolution(antSolutions[ant], hostList, vm);
            for (int i = 0; i < antSolutions[ant].length; i++) {
                pheromones[i][(int) antSolutions[ant][i]] += solutionQuality;
            }
        }
    }

    private double evaluateSolution(double[] solution, List<Host> hostList, Vm vm) {
        double quality = 0.0;
        for (int i = 0; i < solution.length; i++) {
            quality += hostList.get((int) solution[i]).getTotalAvailableMips();
        }
        return quality;
    }

    private Optional<Host> selectBestHost(List<Host> hostList, Vm vm) {
        double bestQuality = Double.MIN_VALUE;
        Host bestHost = null;

        for (int i = 0; i < pheromones.length; i++) {

            double quality = 0.0;
            for (int j = 0; j < pheromones[i].length; j++) {
                quality += pheromones[i][j];
            }

            if (quality > bestQuality) {
                System.out.println("utlization of " + hostList.get(i) + " - " + hostList.get(i).getCpuPercentUtilization());
                if(hostList.get(i).isSuitableForVm(vm) && hostList.get(i).getCpuPercentUtilization() < DC_OVERLOAD_THRESHOLD) {
                    bestQuality = quality;
                    bestHost = hostList.get(i);
                }
            }

        }

        return Optional.ofNullable(bestHost);
    }

    private Optional<Host> findGeneticHostForVm(final VmAllocationPolicy vmAllocationPolicy, final Vm vm) {
        List<Host> hostList = vmAllocationPolicy.getHostList();
        List<int[]> population = initializePopulation(hostList.size(), POPULATION_SIZE);

        for (int generation = 0; generation < MAX_GENERATIONS; generation++) {
            List<Double> fitnessScores = evaluatePopulation(population, hostList, vm);
            List<int[]> newPopulation = new ArrayList<>();

            for (int i = 0; i < POPULATION_SIZE; i++) {
                int[] parent1 = selectParent(population, fitnessScores);
                int[] parent2 = selectParent(population, fitnessScores);
                int[] offspring = crossover(parent1, parent2);

                if (random.nextDouble() < MUTATION_RATE) {
                    mutate(offspring, hostList);
                }

                newPopulation.add(offspring);
            }

            population = newPopulation;
        }

        return selectBestHostGenetic(population, hostList, vm);
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

    private void mutate(int[] individual, List<Host> hostList) {
        int index = random.nextInt(individual.length);
        individual[index] = random.nextInt(hostList.size());
    }

    private Optional<Host> selectBestHostGenetic(List<int[]> population, List<Host> hostList, Vm vm) {
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
                if (host.isSuitableForVm(vm) && host.getCpuPercentUtilization() < DC_OVERLOAD_THRESHOLD) {
                    return Optional.of(host);
                }
            }
        }

        return Optional.empty();
    }

}


