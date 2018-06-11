package com.egain.bindings.chat;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import static com.egain.bindings.chat.Constants.EGAIN_NS;

@JacksonXmlRootElement(localName = "param", namespace = EGAIN_NS)
public class Param {

    @JacksonXmlProperty(isAttribute = true)
    private String name;
    @JacksonXmlProperty(namespace = Constants.EGAIN_NS)
    private Mapping mapping = new Mapping();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Mapping getMapping() {
        return mapping;
    }

}
