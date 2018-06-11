package com.egain.bindings.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import org.junit.Assert;
import org.junit.Test;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;

public class ParamTest {

    XmlMapper mapper = new XmlMapper();

    @Test
    public void testSerialize() throws JsonProcessingException {

        Param param = new Param();
        param.setName("chat_queue_name");
        param.getMapping().setAttributeName("chat_queue");
        param.getMapping().setObjectName("casemgmt::activity_data");
        param.getMapping().setAttributeValue("XYZ");
        String xml = mapper.writeValueAsString(param);

        Diff myDiff = DiffBuilder
                .compare(getClass().getResource("param.xml"))
                .withTest(xml)
                .ignoreWhitespace()
                .ignoreComments()
                .checkForSimilar()
                .build();

        Assert.assertFalse(myDiff.toString(), myDiff.hasDifferences());
    }

}