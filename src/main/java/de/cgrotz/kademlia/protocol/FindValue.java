package de.cgrotz.kademlia.protocol;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 *
 * Same as FIND_NODE, but if the recipient of the request has the requested key in its store, it will return the corresponding value.
 *
 * Created by Christoph on 22.09.2016.
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class FindValue extends Message {

    private final String key;

    public FindValue(long seqId, String key) {
        super(MessageType.FIND_VALUE, seqId);
        this.key = key;
    }
}
