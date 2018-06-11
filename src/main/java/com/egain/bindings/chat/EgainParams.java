package com.egain.bindings.chat;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.ArrayList;
import java.util.List;

@JacksonXmlRootElement(localName = "egainParams", namespace = Constants.EGAIN_NS)
@JsonPropertyOrder({"param", "messagingData"})
public class EgainParams {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "param", namespace = Constants.EGAIN_NS)
    List<Param> params = new ArrayList<>();
    @JacksonXmlProperty(namespace = Constants.EGAIN_NS)
    String messagingData;

    public List<Param> getParams() {
        return params;
    }

    public void addParam(Param param) {
        this.params.add(param);
    }

    public String getMessagingData() {
        return messagingData;
    }

    public void setMessagingData(String messagingData) {
        this.messagingData = messagingData;
    }
}
