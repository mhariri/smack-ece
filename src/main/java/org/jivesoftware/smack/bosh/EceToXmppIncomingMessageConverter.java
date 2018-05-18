package org.jivesoftware.smack.bosh;

import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.FromTypeFilter;
import org.jivesoftware.smack.filter.MessageTypeFilter;
import org.jivesoftware.smack.filter.MessageWithBodiesFilter;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.jxmpp.jid.EntityFullJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

/**
 * Converts an ECE's style incoming chat message to a proper
 * XMPP incoming message.
 */
public class EceToXmppIncomingMessageConverter {


    /**
     * Qualifier for an ECE incoming chat message
     */
    private static final StanzaFilter ECE_INCOMING_MESSAGE_FILTER = new AndFilter(
            MessageTypeFilter.CHAT,
            MessageWithBodiesFilter.INSTANCE,
            FromTypeFilter.DOMAIN_BARE_JID
    );

    /**
     * Modifies ECE's incoming chat message to conform to XMPP standard incoming
     * chat message. Otherwise Smack will ignore the incoming message.
     */
    public static void modifyIncomingChatMessage(Stanza packet) {
        Message message = (Message) packet;
        final Jid from = message.getFrom();

        if (!from.isEntityFullJid()) {
            final EntityFullJid bareFrom;
            try {
                bareFrom = JidCreate.entityFullFrom(convertEceIdToFullJid(from));
            } catch (XmppStringprepException e) {
                throw new RuntimeException(e);
            }
            message.setFrom(bareFrom);
        }
    }

    private static String convertEceIdToFullJid(Jid from) {
        return from.toString().replace(" ", "_") + "@egain.com/xyz";
    }

    public static void process(Stanza packet) {
        if (ECE_INCOMING_MESSAGE_FILTER.accept(packet)) {
            EceToXmppIncomingMessageConverter.modifyIncomingChatMessage(packet);
        }
    }
}
