package org.jivesoftware.smack.bosh;

import com.google.common.io.CharStreams;

import com.egain.bindings.chat.EgainParams;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import org.igniterealtime.jbosh.AbstractBody;
import org.igniterealtime.jbosh.AttrVersion;
import org.igniterealtime.jbosh.BOSHClient;
import org.igniterealtime.jbosh.BOSHClientConfig;
import org.igniterealtime.jbosh.BOSHClientConnEvent;
import org.igniterealtime.jbosh.BOSHClientConnListener;
import org.igniterealtime.jbosh.BOSHClientRequestListener;
import org.igniterealtime.jbosh.BOSHClientResponseListener;
import org.igniterealtime.jbosh.BOSHException;
import org.igniterealtime.jbosh.BOSHMessageEvent;
import org.igniterealtime.jbosh.BodyQName;
import org.igniterealtime.jbosh.ComposableBody;
import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.XMPPBOSHConnection;
import org.jivesoftware.smack.XMPPException.StreamErrorException;
import org.jivesoftware.smack.SmackException.ConnectionException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Element;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Nonza;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.StanzaError;
import org.jivesoftware.smack.sasl.packet.SaslStreamElements.SASLFailure;
import org.jivesoftware.smack.sasl.packet.SaslStreamElements.Success;
import org.jivesoftware.smack.util.PacketParserUtils;
import org.jxmpp.jid.parts.Resourcepart;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.StringReader;
import java.io.Writer;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.egain.bindings.chat.Constants.EGAIN_NS;
import static org.jivesoftware.smack.XMPPBOSHConnection.BOSH_URI;
import static org.jivesoftware.smack.XMPPBOSHConnection.XMPP_BOSH_NS;

public class EceBoshConnection extends AbstractXMPPConnection {
    public static final String EGAIN_FROM = "from";
    public static final String EGAIN_AUTHID = "authid";
    public static final String AGENT_JOIN_MSG = "agent_join_msg";
    public static final String AGENT_LEFT_SESSION = "agent_left_session";
    public static final String AGENT_CLOSE_SESSION = "agent_close_session";
    private static final Logger LOGGER = Logger.getLogger(EceBoshConnection.class.getName());
    /**
     * Holds the initial configuration used while creating the connection.
     */
    private final BOSHConfiguration config;
    /**
     * The session ID for the BOSH session with the connection manager.
     */
    protected String sessionID = null;
    private EgainParams params;
    /**
     * The used BOSH client from the jbosh library.
     */
    private BOSHClient client;
    // Some flags which provides some info about the current state.
    private boolean isFirstInitialization = true;
    private boolean done = false;
    // The readerPipe and consumer thread are used for the debugger.
    private PipedWriter readerPipe;
    private Thread readerConsumer;
    private boolean notified;
    private EceEventDispatcher eceEventDispatcher = new EceEventDispatcher(this);
    private XmlMapper mapper = new XmlMapper();
    private EceToXmppIncomingMessageConverter eceConverter = new EceToXmppIncomingMessageConverter(new MessageBodyDecoder());

    /**
     * Create a HTTP Binding connection to an XMPP server.
     *
     * @param config The configuration which is used for this connection.
     * @param params eGain parameters to be passed in the beginning of the connection
     */
    public EceBoshConnection(BOSHConfiguration config, EgainParams params) {
        super(config);
        this.config = config;
        this.params = params;

        if (null == params.getMessagingData()) {
            params.setMessagingData(getDefaultMessages());
        }
        applyEventTokensToMessages(params);
    }

    private void applyEventTokensToMessages(EgainParams params) {
        String msgs = params.getMessagingData();
        msgs = msgs.replaceFirst(AGENT_JOIN_MSG + "\\s*=",
                AGENT_JOIN_MSG + "=" + EceStatusEvent.CHAT_STARTED.getToken());
        msgs = msgs.replaceFirst(AGENT_LEFT_SESSION + "\\s*=",
                AGENT_LEFT_SESSION + "=" + EceStatusEvent.CHAT_CLOSED.getToken());
        msgs = msgs.replaceFirst(AGENT_CLOSE_SESSION + "\\s*=",
                AGENT_CLOSE_SESSION + "=" + EceStatusEvent.CHAT_CLOSED.getToken());
        params.setMessagingData(msgs);
    }

    private String getDefaultMessages() {
        try {
            return CharStreams.toString(
                    new InputStreamReader(
                            getClass().getResourceAsStream("/default_messages.txt")));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    protected void connectInternal() throws SmackException, InterruptedException {

        done = false;
        notified = false;
        try {
            // Ensure a clean starting state
            if (client != null) {
                client.close();
                client = null;
            }
            sessionID = null;

            // Initialize BOSH client
            BOSHClientConfig.Builder cfgBuilder = BOSHClientConfig.Builder
                    .create(config.getURI(), config.getXMPPServiceDomain().toString());
            if (config.isProxyEnabled()) {
                cfgBuilder.setProxy(config.getProxyAddress(), config.getProxyPort());
            }
            for(Map.Entry<String, String> h: config.getHttpHeaders().entrySet()) {
                cfgBuilder.addHttpHeader(h.getKey(), h.getValue());
            }
            if (null != config.getCustomSSLContext()) {
                cfgBuilder.setSSLContext(config.getCustomSSLContext());
            }
            try {
                cfgBuilder.setBoshVersion(AttrVersion.createFromString("1.6"));
            } catch (BOSHException e) {
                throw new RuntimeException(e);
            }

            cfgBuilder.setXMLLang("en-US");

            client = BOSHClient.create(cfgBuilder.build());

            client.addBOSHClientConnListener(new BOSHConnectionListener());
            client.addBOSHClientResponseListener(new BOSHPacketReader());

            // Initialize the debugger
            if (debugger != null) {
                initDebugger();
            }

            client.send(ComposableBody.builder()
                    .setNamespaceDefinition("xmpp", XMPP_BOSH_NS)
                    .setAttribute(BodyQName.createWithPrefix(XMPP_BOSH_NS, "version", "xmpp"), "1.0")
                    .setAttribute(BodyQName.create(EGAIN_NS, EGAIN_FROM), "anonymous@egain.com")
                    .setAttribute(BodyQName.create(EGAIN_NS, EGAIN_AUTHID), "0")
                    .setAttribute(BodyQName.create(EGAIN_NS, "content"), "text/xml; charset=utf-8")
                    .setPayloadXML(mapper.writeValueAsString(params))
                    .build());

        } catch (Exception e) {
            throw new ConnectionException(e);
        }

        authenticated = true;



        // Wait for the response from the server
        synchronized (this) {
            if (!connected) {
                final long deadline = System.currentTimeMillis() + getReplyTimeout();
                while (!notified) {
                    final long now = System.currentTimeMillis();
                    if (now >= deadline) {
                        break;
                    }
                    wait(deadline - now);
                }
            }
        }

        // If there is no feedback, throw an remote server timeout error
        if (!connected && !done) {
            done = true;
            String errorMessage = "Timeout reached for the connection to "
                    + getHost() + ":" + getPort() + ".";
            throw new SmackException(errorMessage);
        }
    }

    @Override
    public boolean isSecureConnection() {
        // TODO: Implement SSL usage
        return false;
    }

    @Override
    public boolean isUsingCompression() {
        // TODO: Implement compression
        return false;
    }

    @Override
    protected void loginInternal(String username, String password, Resourcepart resource) throws XMPPException,
            SmackException, IOException, InterruptedException {
        // Authenticate using SASL
        saslAuthentication.authenticate(username, password, config.getAuthzid(), null);

        bindResourceAndEstablishSession(resource);

        afterSuccessfulLogin(false);
    }

    @Override
    public void sendNonza(Nonza element) throws NotConnectedException {
        if (done) {
            throw new NotConnectedException();
        }
        sendElement(element);
    }

    @Override
    protected void sendStanzaInternal(Stanza packet) throws NotConnectedException {
        sendElement(packet);
    }

    private void sendElement(Element element) {
        try {
            send(ComposableBody.builder().setPayloadXML(element.toXML(BOSH_URI).toString()).build());
            if (element instanceof Stanza) {
                firePacketSendingListeners((Stanza) element);
            }
        } catch (BOSHException e) {
            LOGGER.log(Level.SEVERE, "BOSHException in sendStanzaInternal", e);
        }
    }

    /**
     * Closes the connection by setting presence to unavailable and closing the HTTP client. The shutdown logic will be used during
     * a planned disconnection or when dealing with an unexpected disconnection. Unlike {@link #disconnect()} the connection's BOSH
     * stanza(/packet) reader will not be removed; thus connection's state is kept.
     */
    @Override
    protected void shutdown() {
        setWasAuthenticated();
        sessionID = null;
        done = true;
        authenticated = false;
        connected = false;
        isFirstInitialization = false;

        // Close down the readers and writers.
        if (readerPipe != null) {
            try {
                readerPipe.close();
            } catch (Throwable ignore) { /* ignore */ }
            reader = null;
        }
        if (reader != null) {
            try {
                reader.close();
            } catch (Throwable ignore) { /* ignore */ }
            reader = null;
        }
        if (writer != null) {
            try {
                writer.close();
            } catch (Throwable ignore) { /* ignore */ }
            writer = null;
        }

        readerConsumer = null;
    }

    /**
     * Send a HTTP request to the connection manager with the provided body element.
     *
     * @param body the body which will be sent
     * @throws BOSHException on message transmission failure
     */
    protected void send(ComposableBody body) throws BOSHException {
        if (!connected) {
            throw new IllegalStateException("Not connected to a server!");
        }
        if (body == null) {
            throw new NullPointerException("Body mustn't be null!");
        }
        if (sessionID != null) {
            body = body.rebuild().setAttribute(
                    BodyQName.create(BOSH_URI, "sid"), sessionID).build();
        }
        LOGGER.log(Level.FINE, "Sending message:" + body.toXML());
        client.send(body);
    }

    /**
     * Initialize the SmackDebugger which allows to log and debug XML traffic.
     */
    @Override
    protected void initDebugger() {
        // TODO: Maybe we want to extend the SmackDebugger for simplification
        //       and a performance boost.

        // Initialize a empty writer which discards all data.
        writer = new Writer() {
            @Override
            public void write(char[] cbuf, int off, int len) {
                /* ignore */
            }

            @Override
            public void close() {
                /* ignore */
            }

            @Override
            public void flush() {
                /* ignore */
            }
        };

        // Initialize a pipe for received raw data.
        try {
            readerPipe = new PipedWriter();
            reader = new PipedReader(readerPipe);
        } catch (IOException e) {
            // Ignore
        }

        // Call the method from the parent class which initializes the debugger.
        super.initDebugger();

        // Add listeners for the received and sent raw data.
        client.addBOSHClientResponseListener(new BOSHClientResponseListener() {
            @Override
            public void responseReceived(BOSHMessageEvent event) {
                if (event.getBody() != null) {
                    try {
                        readerPipe.write(event.getBody().toXML());
                        readerPipe.flush();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        });
        client.addBOSHClientRequestListener(new BOSHClientRequestListener() {
            @Override
            public void requestSent(BOSHMessageEvent event) {
                if (event.getBody() != null) {
                    try {
                        writer.write(event.getBody().toXML());
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        });

        // Create and start a thread which discards all read data.
        readerConsumer = new Thread() {
            private Thread thread = this;
            private int bufferLength = 1024;

            @Override
            public void run() {
                try {
                    char[] cbuf = new char[bufferLength];
                    while (readerConsumer == thread && !done) {
                        reader.read(cbuf, 0, bufferLength);
                    }
                } catch (IOException e) {
                    // Ignore
                }
            }
        };
        readerConsumer.setDaemon(true);
        readerConsumer.start();
    }

    /**
     * Sends out a notification that there was an error with the connection and closes the connection.
     *
     * @param e the exception that causes the connection close event.
     */
    protected void notifyConnectionError(Exception e) {
        // Closes the connection temporary. A reconnection is possible
        shutdown();
        callConnectionClosedOnErrorListener(e);
    }

    @Override
    protected void invokeStanzaCollectorsAndNotifyRecvListeners(Stanza packet) {
        eceConverter.process(packet);
        super.invokeStanzaCollectorsAndNotifyRecvListeners(packet);
    }

    public void addEceEventListener(EceEventListener listener) {
        eceEventDispatcher.addListener(listener);
    }

    public String getSessionID() {
        return sessionID;
    }

    /**
     * A listener class which listen for a successfully established connection and connection errors and notifies the
     * BOSHConnection.
     *
     * @author Guenther Niess
     */
    public class BOSHConnectionListener implements BOSHClientConnListener {

        /**
         * Notify the BOSHConnection about connection state changes. Process the connection listeners and try to login if the
         * connection was formerly authenticated and is now reconnected.
         */
        @Override
        public void connectionEvent(BOSHClientConnEvent connEvent) {
            try {
                if (connEvent.isConnected()) {
                    connected = true;
                    if (isFirstInitialization) {
                        isFirstInitialization = false;
                    } else {
                        if (wasAuthenticated) {
                            try {
                                login();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                } else {
                    if (connEvent.isError()) {
                        // TODO Check why jbosh's getCause returns Throwable here. This is very
                        // unusual and should be avoided if possible
                        Throwable cause = connEvent.getCause();
                        Exception e;
                        if (cause instanceof Exception) {
                            e = (Exception) cause;
                        } else {
                            e = new Exception(cause);
                        }
                        notifyConnectionError(e);
                    }
                    connected = false;
                }
            } finally {
                notified = true;
                synchronized (EceBoshConnection.this) {
                    EceBoshConnection.this.notifyAll();
                }
            }
        }
    }

    /**
     * Listens for XML traffic from the BOSH connection manager and parses it into stanza(/packet) objects.
     *
     * @author Guenther Niess
     */
    private class BOSHPacketReader implements BOSHClientResponseListener {

        /**
         * Parse the received packets and notify the corresponding connection.
         *
         * @param event the BOSH client response which includes the received packet.
         */
        @Override
        public void responseReceived(BOSHMessageEvent event) {
            AbstractBody body = event.getBody();
            LOGGER.log(Level.FINE, "Received message:" + body.toXML());
            if (body != null) {
                try {
                    if (sessionID == null) {
                        sessionID = body.getAttribute(BodyQName.create(XMPPBOSHConnection.BOSH_URI, "sid"));
                    }
                    if (streamId == null) {
                        streamId = body.getAttribute(BodyQName.create(XMPPBOSHConnection.BOSH_URI, "authid"));
                    }
                    final XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
                    parser.setInput(new StringReader(body.toXML()));
                    int eventType = parser.getEventType();
                    do {
                        eventType = parser.next();
                        switch (eventType) {
                            case XmlPullParser.START_TAG:
                                String name = parser.getName();
                                switch (name) {
                                    case Message.ELEMENT:
                                    case IQ.IQ_ELEMENT:
                                    case Presence.ELEMENT:
                                        parseAndProcessStanza(parser);
                                        break;
                                    case "challenge":
                                        // The server is challenging the SASL authentication
                                        // made by the client
                                        final String challengeData = parser.nextText();
                                        getSASLAuthentication().challengeReceived(challengeData);
                                        break;
                                    case "success":
                                        send(ComposableBody.builder().setNamespaceDefinition("xmpp",
                                                XMPPBOSHConnection.XMPP_BOSH_NS).setAttribute(
                                                BodyQName.createWithPrefix(XMPPBOSHConnection.XMPP_BOSH_NS, "restart",
                                                        "xmpp"), "true").setAttribute(
                                                BodyQName.create(XMPPBOSHConnection.BOSH_URI, "to"), getXMPPServiceDomain().toString()).build());
                                        Success success = new Success(parser.nextText());
                                        getSASLAuthentication().authenticated(success);
                                        break;
                                    case "features":
                                        parseFeatures(parser);
                                        break;
                                    case "failure":
                                        if ("urn:ietf:params:xml:ns:xmpp-sasl".equals(parser.getNamespace(null))) {
                                            final SASLFailure failure = PacketParserUtils.parseSASLFailure(parser);
                                            getSASLAuthentication().authenticationFailed(failure);
                                        }
                                        break;
                                    case "error":
                                        // Some BOSH error isn't stream error.
                                        if ("urn:ietf:params:xml:ns:xmpp-streams".equals(parser.getNamespace(null))) {
                                        throw new StreamErrorException(PacketParserUtils.parseStreamError(parser));
                                        } else {
                                            StanzaError.Builder builder = PacketParserUtils.parseError(parser);
                                            throw new XMPPException.XMPPErrorException(null, builder.build());
                                        }
                                    case "body":
                                        String type = parser.getAttributeValue(null, "type");
                                        if ("terminate".equals(type)) {
                                            eceEventDispatcher.emitEvent(EceStatusEvent.CHAT_CLOSED);
                                        }
                                        break;
                                }
                                break;
                        }
                    }
                    while (eventType != XmlPullParser.END_DOCUMENT);
                }
                catch (Exception e) {
                    if (isConnected()) {
                        notifyConnectionError(e);
                    }
                }
            }
        }
    }
}
