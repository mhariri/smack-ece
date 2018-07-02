package org.jivesoftware.smack;

import org.jivesoftware.smack.bosh.MessageBodyDecoder;
import org.junit.Test;

import java.io.UnsupportedEncodingException;

import static org.junit.Assert.*;

public class MessageBodyDecoderTest {

    MessageBodyDecoder decoder = new MessageBodyDecoder();

    @Test
    public void testDecodeComma() throws UnsupportedEncodingException {
        assertEquals("Cats, dogs and mice", decoder.decode("Cats^2c dogs and mice"));
    }

    @Test
    public void testDecodeXml() throws UnsupportedEncodingException {
        assertEquals("Cats & dogs", decoder.decode("Cats &amp; dogs"));
    }

}