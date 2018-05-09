package org.igniterealtime.jbosh;

public class SmackUtils {

    public static BOSHClientConnEvent createConnectedEvent(BOSHClient client) {
        return BOSHClientConnEvent.createConnectionEstablishedEvent(client);
    }
}
