package org.jivesoftware.smack.bosh;

import com.egain.bindings.chat.EgainParams;

import org.igniterealtime.jbosh.BOSHClient;
import org.igniterealtime.jbosh.BOSHException;
import org.igniterealtime.jbosh.ComposableBody;
import org.igniterealtime.jbosh.SmackUtils;
import org.jivesoftware.smack.SmackException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jxmpp.stringprep.XmppStringprepException;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.powermock.api.mockito.PowerMockito.doAnswer;
import static org.powermock.api.mockito.PowerMockito.mock;

@RunWith(PowerMockRunner.class)
@PrepareForTest(BOSHClient.class)
public class EceBoshConnectionTest {

    EceBoshConnection connection;
    BOSHConfiguration config;

    @Mock
    BOSHClient client;

    @Captor
    ArgumentCaptor<ComposableBody> bodyCaptor;


    @Before
    public void setup() throws XmppStringprepException {

        config = BOSHConfiguration.builder()
                .setXmppDomain("xyz")
                .setFile("/")
                .build();
        connection = new EceBoshConnection(config, new EgainParams());
        client = mock(BOSHClient.class);
    }

    @Test
    public void testConnectInternal() throws InterruptedException, SmackException, BOSHException {

        doAnswer((Answer<Void>) invocation ->
        {
            connection.new BOSHConnectionListener().connectionEvent(
                    SmackUtils.createConnectedEvent(client));
            return null;
        })
                .when(client)
                .send(bodyCaptor.capture());
        connection.connectInternal(client);

        assertThat(bodyCaptor.getValue().getPayloadXML(), containsString("<egainParams"));
    }

}