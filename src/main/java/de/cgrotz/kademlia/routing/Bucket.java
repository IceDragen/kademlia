package de.cgrotz.kademlia.routing;

import de.cgrotz.kademlia.client.KademliaClient;
import de.cgrotz.kademlia.exception.TimeoutException;
import de.cgrotz.kademlia.node.Key;
import de.cgrotz.kademlia.node.Node;
import de.cgrotz.kademlia.protocol.Pong;
import lombok.Data;

import java.util.TreeSet;

/**
 * Created by Christoph on 22.09.2016.
 */
@Data
public class Bucket {
    private final int bucketId;

    private final TreeSet<Node> nodes = new TreeSet<>();
    private final TreeSet<Node> replacementNodes = new TreeSet<>();
    private final int k;
    private final KademliaClient client;

    public Bucket(KademliaClient client, int k, int bucketId) {
        this.k = k;
        this.bucketId = bucketId;
        this.client = client;
    }

    public void addNode(Key nodeId, String host, int port) {
        Node node = Node.builder().id(nodeId).address(host).port(port).build();
        if(nodes.size() < k) {
            nodes.add(node);
        }
        else {
            Node last = nodes.last();
            try {
                client.sendPing(last.getAddress(),last.getPort(), message -> {
                    Pong pong = (Pong)message;
                    nodes.remove(last);
                    last.setLastSeen(System.currentTimeMillis());
                    nodes.add(last);
                    replacementNodes.add(node);
                    if(replacementNodes.size() > k) {
                        replacementNodes.remove(replacementNodes.last());
                    }
                });
            } catch (TimeoutException e) {
                nodes.remove(last);
                nodes.add(node);
                return;
            }
        }
    }

    public TreeSet<Node> getNodes() {
        TreeSet<Node> set = new TreeSet<>();
        set.addAll(nodes);
        return set;
    }

    public void refreshBucket() {
        @SuppressWarnings("unchecked") TreeSet<Node> copySet = new TreeSet(nodes);
        // Check nodes on reachability and update
        copySet.stream().forEach(node -> {
            try {
                client.sendPing(node.getAddress(), node.getPort(), pong -> {
                    nodes.remove(node);
                    node.setLastSeen(System.currentTimeMillis());
                    nodes.add(node);
                });
            }
            catch(TimeoutException exp) {
                nodes.remove(node);
            }
        });

        // Fill up with reachable nodes from replacement set
        while(nodes.size() < k && !replacementNodes.isEmpty()) {
            Node node = replacementNodes.first();
            try {
                client.sendPing(node.getAddress(), node.getPort(), pong -> {
                    replacementNodes.remove(node);
                    node.setLastSeen(System.currentTimeMillis());
                    nodes.add(node);
                });
            }
            catch(TimeoutException exp) {
                replacementNodes.remove(node);
            }
        }
    }
}
