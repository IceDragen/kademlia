package de.cgrotz.kademlia.protocol;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 *
 * Stores a (key, value) pair in one node.
 *
 * Created by Christoph on 22.09.2016.
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Store extends Message {
    private final String value;
    private final String key;

    public Store(long seqId, String key, String value) {
        super(MessageType.STORE, seqId);
        this.key = key;
        this.value = value;
    }
}
