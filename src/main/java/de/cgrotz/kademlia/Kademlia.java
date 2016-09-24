package de.cgrotz.kademlia;

import de.cgrotz.kademlia.client.KademliaClient;
import de.cgrotz.kademlia.node.Node;
import de.cgrotz.kademlia.node.NodeId;
import de.cgrotz.kademlia.protocol.Codec;
import de.cgrotz.kademlia.protocol.ValueReply;
import de.cgrotz.kademlia.routing.RoutingTable;
import de.cgrotz.kademlia.server.KademliaServer;
import de.cgrotz.kademlia.storage.InMemoryStorage;
import de.cgrotz.kademlia.storage.LocalStorage;
import io.netty.util.internal.ConcurrentSet;
import lombok.Data;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Created by Christoph on 21.09.2016.
 */
@Data
public class Kademlia {
    private final RoutingTable routingTable;
    private AtomicLong seqId = new AtomicLong();
    private final NodeId nodeId;
    private final String hostname;
    private final int port;
    private final KademliaClient client;
    private final KademliaServer server;

    private final Codec codec = new Codec();

    private final int kValue;
    private final LocalStorage localStorage;
    private final Node localNode;

    public Kademlia(NodeId nodeId, String hostname, int port) throws InterruptedException {
        this.nodeId = nodeId;
        this.hostname = hostname;
        this.port = port;
        this.kValue = 20;
        this.localNode = Node.builder().id(nodeId).address(hostname).port(port).build();

        this.routingTable = new RoutingTable(kValue, nodeId);
        this.localStorage =  new InMemoryStorage();

        this.client = new KademliaClient(nodeId, routingTable, hostname, port);
        this.server = new KademliaServer(port, kValue, routingTable, localStorage,
                Node.builder().id(nodeId).address(hostname).port(port).build());
    }

    public void bootstrap(String hostname, int port) throws InterruptedException {
        client.sendPing(hostname, port, seqId.incrementAndGet());

        // FIND_NODE with own IDs to find nearby nodes
        client.sendFindNode(hostname, port,seqId.incrementAndGet(), nodeId, nodes -> {
        });

        // Refresh buckets
        for (int i = 1; i < NodeId.ID_LENGTH; i++) {
            // Construct a NodeId that is i bits away from the current node Id
            final NodeId current = this.nodeId.generateNodeIdByDistance(i);

            routingTable.getBucketStream()
                    .flatMap(bucket -> bucket.getNodes().stream())
                    .forEach(node -> {
                        client.sendFindNode(node.getAddress(), node.getPort(),seqId.incrementAndGet(), current, nodes -> {

                        });
                    });

        }
    }

    /**
     * Put or Update the value in the DHT
     *
     * @param key
     * @param value
     */
    public void put(String key, String value) throws InterruptedException {
        int id = key.hashCode();
        client.sendFindNode(hostname, port, seqId.incrementAndGet(), new NodeId(id), nodes -> {
                    nodes.stream().forEach(node -> {
                        client.sendContentToNode(seqId.incrementAndGet(), node, key,value);
                    });
                });
    }

    public String get(String key) throws ExecutionException, InterruptedException {
        CompletableFuture<String> future = new CompletableFuture<>();
        new Thread(() -> {
            get(key, valueReply -> {
                future.complete(valueReply.getValue());
            });
        }).start();
        return future.get();
    }


    public void get(String key, Consumer<ValueReply> valueReplyConsumer) {
        // using Java's hashCode Algorithm could be a consistency problem
        BigInteger id = BigInteger.valueOf(key.hashCode());

        if(localStorage.contains(key)) {
            valueReplyConsumer.accept(new ValueReply(-1,key, localStorage.get(key)));
        }
        else {
            ConcurrentSet<Node> alreadyCheckedNodes = new ConcurrentSet<>();
            AtomicBoolean found = new AtomicBoolean(false);
            get(found, key, routingTable.getBucketStream()
                    .flatMap(bucket -> bucket.getNodes().stream())
                    .sorted((node1, node2) -> node1.getId().getKey().xor(id).abs()
                            .compareTo(node2.getId().getKey().xor(id).abs()))
                    .collect(Collectors.toList()), alreadyCheckedNodes, valueReply -> {
                        if(!found.getAndSet(true)) {
                            valueReplyConsumer.accept(valueReply);
                        }
                    });
        }
    }

    private void get(AtomicBoolean found, String key, List<Node> nodes, ConcurrentSet<Node> alreadyCheckedNodes, Consumer<ValueReply> valueReplyConsumer) {
        for( Node node : nodes) {
            if(!alreadyCheckedNodes.contains(node) && !found.get()) {
                client.sendFindValue(node.getAddress(), node.getPort(), seqId.incrementAndGet(),
                        key, nodeReply -> get(found, key, nodeReply.getNodes(), alreadyCheckedNodes, valueReplyConsumer), valueReplyConsumer);

                alreadyCheckedNodes.add(node);
            }
        }
    }

    public Node getLocalNode() {
        return localNode;
    }
}
