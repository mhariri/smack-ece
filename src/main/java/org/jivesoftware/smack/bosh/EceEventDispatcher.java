package org.jivesoftware.smack.bosh;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.FromTypeFilter;
import org.jivesoftware.smack.filter.MessageTypeFilter;
import org.jivesoftware.smack.filter.MessageWithBodiesFilter;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.packet.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects ECE specific events and dispatches them to
 * registered listeners.
 */
public class EceEventDispatcher {

    private static final StanzaFilter ECE_EVENT_FILTER = new AndFilter(
            MessageTypeFilter.HEADLINE,
            MessageWithBodiesFilter.INSTANCE,
            FromTypeFilter.DOMAIN_BARE_JID
    );

    private List<EceEventListener> listeners = new ArrayList<>();

    public EceEventDispatcher(AbstractXMPPConnection connection) {
        connection.addAsyncStanzaListener(packet -> {
                    Message msg = (Message) packet;
                    EceStatusEvent status = EceStatusEvent.valueOf(msg.getBody());
                    if (status != null) {
                        emitEvent(status);
                    }
                },
                ECE_EVENT_FILTER);
    }


    public void addListener(EceEventListener listener) {
        listeners.add(listener);
    }

    public void emitEvent(EceStatusEvent event) {
        listeners.stream()
                .forEach(l -> l.newEvent(event));
    }
}
