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

import java.io.UnsupportedEncodingException;

/**
 * Converts an ECE's style incoming chat message to a proper XMPP incoming message.
 */
public class EceToXmppIncomingMessageConverter {


    private MessageBodyDecoder decoder;

    public EceToXmppIncomingMessageConverter(MessageBodyDecoder decoder) {
        this.decoder = decoder;
    }

    /**
     * Qualifier for an ECE incoming chat message
     */
    private static final StanzaFilter ECE_INCOMING_MESSAGE_FILTER = new AndFilter(
            MessageTypeFilter.CHAT,
            MessageWithBodiesFilter.INSTANCE,
            FromTypeFilter.DOMAIN_BARE_JID
    );

    /**
     * Modifies the incoming chat message to fix ECE's non-standard behaviours
     *
     * @param packet to modify to make it compatible with XMPP
     */
    public void modifyIncomingChatMessage(Stanza packet) throws UnsupportedEncodingException {
        Message message = (Message) packet;
        fixFromField(message);
        convertCharactersInBody(message);
    }

    /**
     * Converts special characters in the message body to normal unicode ones
     *
     * @param message to modify
     */
    private void convertCharactersInBody(Message message) throws UnsupportedEncodingException {
        String text = message.getBody();
        message.getBodies().forEach(f -> message.removeBody(f));
        message.setBody(decoder.decode(text));
    }

    /**
     * Modifies ECE's incoming chat message's from header to conform to XMPP standard incoming chat message. Otherwise Smack will
     * ignore the incoming message.
     *
     * @param message to modify to make it compatible with XMPP
     */
    private static void fixFromField(Message message) {
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

    public void process(Stanza packet) {
        if (ECE_INCOMING_MESSAGE_FILTER.accept(packet)) {
            try {
                modifyIncomingChatMessage(packet);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
