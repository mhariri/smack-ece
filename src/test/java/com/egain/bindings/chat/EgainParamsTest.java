package com.egain.bindings.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import org.junit.Assert;
import org.junit.Test;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;

public class EgainParamsTest {

    XmlMapper mapper = new XmlMapper();

    @Test
    public void testSerialize() throws JsonProcessingException {
        EgainParams params = new EgainParams();

        Param param = new Param();
        param.setName("chat_queue_name");
        param.getMapping().setAttributeName("chat_queue");
        param.getMapping().setObjectName("casemgmt::activity_data");
        param.getMapping().setAttributeValue("XYZ");

        params.addParam(param);
        params.setMessagingData("cust_welcome_msg =An agent will be with you shortly.\n" +
                "asd asd");


        String xml = mapper.writeValueAsString(params);

        Diff myDiff = DiffBuilder
                .compare(getClass().getResource("egainParams.xml"))
                .withTest(xml)
                .ignoreWhitespace()
                .checkForSimilar()
                .build();

        Assert.assertFalse(myDiff.toString(), myDiff.hasDifferences());
    }
}