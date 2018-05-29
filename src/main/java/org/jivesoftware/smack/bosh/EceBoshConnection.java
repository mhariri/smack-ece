package org.jivesoftware.smack.bosh;

import com.google.common.io.CharStreams;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.Header;
import org.igniterealtime.jbosh.AbstractBody;
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
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Element;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Nonza;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smack.sasl.packet.SaslStreamElements;
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
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EceBoshConnection extends AbstractXMPPConnection {
    /**
     * The XMPP Over Bosh namespace.
     */
    public static final String XMPP_BOSH_NS = "urn:xmpp:xbosh";
    /**
     * The BOSH namespace from XEP-0124.
     */
    public static final String BOSH_URI = "http://jabber.org/protocol/httpbind";
    public static final String EGAIN_NS = "http://bindings.egain.com/chat";
    public static final String EGAIN_TO = "to";
    public static final String EGAIN_FROM = "from";
    public static final String EGAIN_AUTHID = "authid";
    private static final Logger LOGGER = Logger.getLogger(EceBoshConnection.class.getName());
    private static final String CUSTOMER_NAME_TOKEN = "$$CUSTOMER_NAME$$";
    private static final String SUBJECT_TOKEN = "$$SUBJECT$$";

    /**
     * Holds the initial configuration used while creating the connection.
     */
    private final BOSHConfiguration config;
    /**
     * The session ID for the BOSH session with the connection manager.
     */
    protected String sessionID = null;
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
    private String egainParams;
    private EceEventDispatcher eceEventDispatcher = new EceEventDispatcher(this);

    /**
     * Create a HTTP Binding connection to an XMPP server.
     *
     * @param config       The configuration which is used for this connection.
     * @param customerName that appears in agent's view
     * @param subject      of the chat, appears as the first chat message for the agent
     */
    public EceBoshConnection(BOSHConfiguration config, String customerName, String subject) {
        super(config);
        this.config = config;

        try {
            egainParams = CharStreams.toString(
                    new InputStreamReader(
                            getClass().getResourceAsStream("/egain_params.xml")));
            egainParams = egainParams.replace(CUSTOMER_NAME_TOKEN, StringEscapeUtils.escapeXml11(customerName));
            egainParams = egainParams.replace(SUBJECT_TOKEN, StringEscapeUtils.escapeXml11(subject));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    protected void connectInternal(BOSHClient client) throws SmackException, InterruptedException {

        done = false;
        notified = false;

        client.addBOSHClientConnListener(new EceBoshConnection.BOSHConnectionListener());
        client.addBOSHClientResponseListener(new EceBoshConnection.BOSHPacketReader());

        // Initialize the debugger
        if (config.isDebuggerEnabled()) {
            initDebugger();
            if (isFirstInitialization) {
                if (debugger.getReaderListener() != null) {
                    addAsyncStanzaListener(debugger.getReaderListener(), null);
                }
                if (debugger.getWriterListener() != null) {
                    addStanzaSendingListener(debugger.getWriterListener(), null);
                }
            }
        }

        // Send the session creation request
        try {
            client.send(ComposableBody.builder()
                    .setNamespaceDefinition("xmpp", XMPP_BOSH_NS)
                    .setAttribute(BodyQName.createWithPrefix(XMPP_BOSH_NS, "version", "xmpp"), "1.0")
                    .setAttribute(BodyQName.create(EGAIN_NS, EGAIN_FROM), "anonymous@egain.com")
                    .setAttribute(BodyQName.create(EGAIN_NS, EGAIN_AUTHID), "0")
                    .setAttribute(BodyQName.create(EGAIN_NS, "content"), "text/xml; charset=utf-8")
                    .setPayloadXML(egainParams)
                    .build());
            authenticated = true;
        } catch (BOSHException e) {
            throw new SmackException.ConnectionException(e);
        }


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
    protected void connectInternal() throws SmackException, InterruptedException {
        // Ensure a clean starting state
        if (client != null) {
            client.close();
            client = null;
        }
        sessionID = null;

        // Initialize BOSH client
        BOSHClientConfig.Builder cfgBuilder = null;
        try {
            cfgBuilder = BOSHClientConfig.Builder
                    .create(config.getURI(), config.getXMPPServiceDomain().toString());
            if (config.isProxyEnabled()) {
                cfgBuilder.setProxy(config.getProxyAddress(), config.getProxyPort());
            }
            for (Header h : config.getHttpHeaders()) {
                cfgBuilder.addHttpHeader(h);
            }

            cfgBuilder.setXMLLang("en-US");

            client = BOSHClient.create(cfgBuilder.build());
            connectInternal(client);
        } catch (URISyntaxException e) {
            throw new SmackException.ConnectionException(e);
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
    public void sendNonza(Nonza element) throws SmackException.NotConnectedException {
        if (done) {
            throw new SmackException.NotConnectedException();
        }
        sendElement(element);
    }

    @Override
    protected void sendStanzaInternal(Stanza packet) throws SmackException.NotConnectedException {
        sendElement(packet);
    }

    private void sendElement(Element element) {
        try {
            String x = element.toXML().toString();
            // TODO: seems to be fixed in 4.4.0 version of Smack, so this goes away after upgrading
            x = x.replaceFirst("<message ", "<message xmlns='jabber:client' ");
            x = x.replaceFirst("<presence id='[^']+' ", "<presence xmlns='jabber:client' ");
            send(ComposableBody.builder().setPayloadXML(x).build());
            if (element instanceof Stanza) {
                firePacketSendingListeners((Stanza) element);
            }
        } catch (BOSHException e) {
            LOGGER.log(Level.SEVERE, "BOSHException in sendStanzaInternal", e);
        }
    }

    /**
     * Closes the connection by setting presence to unavailable and closing the
     * HTTP client. The shutdown logic will be used during a planned disconnection or when
     * dealing with an unexpected disconnection. Unlike {@link #disconnect()} the connection's
     * BOSH stanza(/packet) reader will not be removed; thus connection's state is kept.
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
     * @param body the body which will be sent.
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
     * Sends out a notification that there was an error with the connection
     * and closes the connection.
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
        EceToXmppIncomingMessageConverter.process(packet);
        super.invokeStanzaCollectorsAndNotifyRecvListeners(packet);
    }

    public void addEceEventListener(EceEventListener listener) {
        eceEventDispatcher.addListener(listener);
    }

    public String getSessionID() {
        return sessionID;
    }

    /**
     * A listener class which listen for a successfully established connection
     * and connection errors and notifies the BOSHConnection.
     *
     * @author Guenther Niess
     */
    public class BOSHConnectionListener implements BOSHClientConnListener {

        /**
         * Notify the BOSHConnection about connection state changes.
         * Process the connection listeners and try to login if the
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
     * Listens for XML traffic from the BOSH connection manager and parses it into
     * stanza(/packet) objects.
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
            if (body != null) {
                try {
                    if (sessionID == null) {
                        sessionID = body.getAttribute(BodyQName.create(EceBoshConnection.BOSH_URI, "sid"));
                    }
                    if (streamId == null) {
                        streamId = body.getAttribute(BodyQName.create(EceBoshConnection.BOSH_URI, "authid"));
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
                                                EceBoshConnection.XMPP_BOSH_NS).setAttribute(
                                                BodyQName.createWithPrefix(EceBoshConnection.XMPP_BOSH_NS, "restart",
                                                        "xmpp"), "true").setAttribute(
                                                BodyQName.create(EceBoshConnection.BOSH_URI, "to"), getXMPPServiceDomain().toString()).build());
                                        SaslStreamElements.Success success = new SaslStreamElements.Success(parser.nextText());
                                        getSASLAuthentication().authenticated(success);
                                        break;
                                    case "features":
                                        parseFeatures(parser);
                                        break;
                                    case "failure":
                                        if ("urn:ietf:params:xml:ns:xmpp-sasl".equals(parser.getNamespace(null))) {
                                            final SaslStreamElements.SASLFailure failure = PacketParserUtils.parseSASLFailure(parser);
                                            getSASLAuthentication().authenticationFailed(failure);
                                        }
                                        break;
                                    case "error":
                                        // Some BOSH error isn't stream error.
                                        if ("urn:ietf:params:xml:ns:xmpp-streams".equals(parser.getNamespace(null))) {
                                            throw new XMPPException.StreamErrorException(PacketParserUtils.parseStreamError(parser));
                                        } else {
                                            XMPPError.Builder builder = PacketParserUtils.parseError(parser);
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
                } catch (Exception e) {
                    if (isConnected()) {
                        notifyConnectionError(e);
                    }
                }
            }
        }
    }
}
