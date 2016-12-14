package org.nd4j.parameterserver.distributed;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.util.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.aggregates.Aggregate;
import org.nd4j.linalg.api.ops.aggregates.Batch;
import org.nd4j.linalg.api.ops.aggregates.impl.AggregateSkipGram;
import org.nd4j.parameterserver.distributed.conf.Configuration;
import org.nd4j.parameterserver.distributed.enums.NodeRole;
import org.nd4j.parameterserver.distributed.logic.Connector;
import org.nd4j.parameterserver.distributed.logic.Shard;
import org.nd4j.parameterserver.distributed.transport.MulticastTransport;
import org.nd4j.parameterserver.distributed.transport.Transport;

import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This is "special case" distributed P2P parameter server implementation, suitable for Spark Word2Vec/ParagraphVectors/DeepWalk implementations.
 * Aeron is used as backbone for messaging system here.
 *
 * Highlights:
 * a) It does ONLY one-way messaging. Clients are NOT getting anything back from ParamServer.
 * b) It works sharded. Amount of shards is defined in runtime.
 * c) Data replication strategy is defined in runtime.
 * d) It's supposed to be used as singleton in Spark environment ONLY.
 *
 * @author raver119@gmail.com
 */
@Slf4j
public class VoidParameterServer {
    private static final VoidParameterServer INSTANCE = new VoidParameterServer();

    @Getter protected volatile NodeRole nodeRole;

    protected volatile Configuration configuration;


    protected AtomicBoolean initLocker = new AtomicBoolean(false);
    protected AtomicBoolean initFinished = new AtomicBoolean(false);
    protected AtomicBoolean shutdownLocker = new AtomicBoolean(false);
    protected AtomicBoolean shutdownFinished = new AtomicBoolean(false);

    protected transient Transport transport;


    protected Connector connector;

    protected VoidParameterServer() {
        nodeRole = NodeRole.NONE;
    }

    public static VoidParameterServer getInstance() {
        return INSTANCE;
    }


    protected Transport getTransport() {
        return transport;
    }

    public void init(@NonNull Configuration configuration) {
        init(configuration, new MulticastTransport());
    }

    /**
     * This method starts ParameterServer instance
     *
     * PLEASE NOTE: This method is blocking for first caller only
     */
    public void init(@NonNull Configuration configuration, Transport transport){
        /**
         * Basic plan here:
         *      start publishers/listeners/subscribers
         *      determine role of the current instance:
         *          Shard
         *          Backup
         *          Client
         *      shutdown unwanted aeron helpers (according to role)
         *      wait for incoming task queries (according to role
         *
         */
        if (initFinished.get())
            return;

        synchronized (this) {
            if (initLocker.compareAndSet(false, true)) {
                this.configuration = configuration;

                // first we need to check, if our current IP matches designated shards or backup
                Pair<NodeRole, String> pair = getRole(configuration, getLocalAddresses());
                nodeRole = pair.getFirst();


                // role-dependent additional initialization
                this.transport = transport;

                // TODO: we need real ip only if this is a shard *FOR NOW*, but later we'll need it for client as well
                transport.init(configuration, nodeRole, pair.getSecond());


                initFinished.set(true);
            }
        }
    }

    /**
     * This method checks for designated role, according to local IP addresses and configuration passed into method
     *
     * @param configuration
     * @param localIPs
     * @return
     */
    protected Pair<NodeRole, String> getRole(@NonNull Configuration configuration, @NonNull Collection<String> localIPs) {
        NodeRole result = NodeRole.CLIENT;

        for (String ip: localIPs) {
            if (configuration.getShardAddresses().contains(ip))
                return Pair.create(NodeRole.SHARD, ip);
        }

        if (configuration.getBackupAddresses() != null)
            for (String ip: localIPs)
                if (configuration.getBackupAddresses().contains(ip))
                    return Pair.create(NodeRole.BACKUP, ip);


        // local IP from pair is used for shard only, so we don't care
        return Pair.create(result, "127.0.0.1");
    }

    /**
     * This method initiates shutdown sequence for this instance.
     *
     * PLEASE NOTE: This method is blocking for first caller only
     */
    public void shutdown() {
        /**
         * Probably we don't need this method in practice
         */
        if (initLocker.get() && shutdownLocker.compareAndSet(false, true)) {
            // do shutdown
            log.info("Shutting down transport...");
            transport.shutdown();
        }
    }

    /**
     * This method returns set of local IP addresses available in system.
     *
     * PLEASE NOTE: loopback, disabled interfaces, IPv6 addresses are ignored here.
     *
     * @return
     */
    public static Set<String> getLocalAddresses() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());

            Set<String> result = new HashSet<>();

            for (NetworkInterface networkInterface : interfaces) {
                if (networkInterface.isLoopback() || !networkInterface.isUp())
                    continue;

                for(InterfaceAddress address: networkInterface.getInterfaceAddresses()) {
                    String addr = address.getAddress().getHostAddress();

                    if (addr == null || addr.isEmpty() || addr.contains(":"))
                        continue;

                    result.add(addr);
                }
            }

            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This method takes AggregateOp, and sends them for execution over network
     *
     * PLEASE NOTE: This method is NOT blocking
     *
     * @param aggregate
     */
    public <T extends Aggregate> void execDistributed(@NonNull T aggregate) {
        // we just form the message and send it


        transport.sendMessage(null);
    }

    /**
     * This method takes Batch of Aggregates, and executes them over network
     *
     * PLEASE NOTE: This method is NOT blocking
     *
     * @param batch
     * @param <T>
     */
    public <T extends Aggregate> void execDistributed(@NonNull Batch<T> batch) {
        // we form it and send it :)

        transport.sendMessage(null);
    }


    /**
     * This method returns INDArray matching requested storageId value
     *
     * PLEASE NOTE: This method IS blocking
     *
     * @param storageId
     * @return
     */
    public INDArray getVector(long storageId) {
        /**
         * we create VoidMessage, send it, and block until it gets responded
         */
        return null;
    }
}