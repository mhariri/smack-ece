package org.jivesoftware.smack.bosh;

import org.apache.commons.text.StringEscapeUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/**
 * Decodes/unescapes special characters in ECE's message body
 */
public class MessageBodyDecoder {

    /**
     * Decodes the specified text replacing escaped characters with their unescaped representations
     *
     * @param text to decode
     * @return decoded results
     */
    public String decode(String text) throws UnsupportedEncodingException {
        text = URLDecoder.decode(text, "UTF-8");
        text = text.replaceAll("\\^([a-z0-9,2])","%$1");
        text = URLDecoder.decode(text, "UTF-8");
        text = StringEscapeUtils.unescapeXml(text);
        return text;
    }
}
