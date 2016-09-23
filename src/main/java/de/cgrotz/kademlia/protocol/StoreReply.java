package de.cgrotz.kademlia.protocol;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 *
 * Response to Ping
 *
 * Created by Christoph on 22.09.2016.
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class StoreReply extends Message {
    public StoreReply(long seqId) {
        super(MessageType.STORE_REPLY, seqId);
    }
}
