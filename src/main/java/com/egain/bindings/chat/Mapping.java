package com.egain.bindings.chat;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public class Mapping {
    @JacksonXmlProperty(namespace = Constants.EGAIN_NS)
    private String attributeName;
    @JacksonXmlProperty(namespace = Constants.EGAIN_NS)
    private String objectName;
    @JacksonXmlProperty(namespace = Constants.EGAIN_NS)
    private String attributeValue;
    @JacksonXmlProperty(namespace = Constants.EGAIN_NS)
    private String validationString = "";
    @JacksonXmlProperty(namespace = Constants.EGAIN_NS)
    private int primaryKey = 0;
    @JacksonXmlProperty(namespace = Constants.EGAIN_NS)
    private int secureAttribute = 0;
    @JacksonXmlProperty(namespace = Constants.EGAIN_NS)
    private int minLength = 1;
    @JacksonXmlProperty(namespace = Constants.EGAIN_NS)
    private int maxLength = 120;
    @JacksonXmlProperty(namespace = Constants.EGAIN_NS)
    private int required = 0;
    @JacksonXmlProperty(namespace = Constants.EGAIN_NS)
    private int fieldType = 1;

    public String getAttributeName() {
        return attributeName;
    }

    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public String getAttributeValue() {
        return attributeValue;
    }

    public void setAttributeValue(String attributeValue) {
        this.attributeValue = attributeValue;
    }

    public String getValidationString() {
        return validationString;
    }

    public void setValidationString(String validationString) {
        this.validationString = validationString;
    }

    public int getPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(int primaryKey) {
        this.primaryKey = primaryKey;
    }

    public int getSecureAttribute() {
        return secureAttribute;
    }

    public void setSecureAttribute(int secureAttribute) {
        this.secureAttribute = secureAttribute;
    }

    public int getMinLength() {
        return minLength;
    }

    public void setMinLength(int minLength) {
        this.minLength = minLength;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    public int getRequired() {
        return required;
    }

    public void setRequired(int required) {
        this.required = required;
    }

    public int getFieldType() {
        return fieldType;
    }

    public void setFieldType(int fieldType) {
        this.fieldType = fieldType;
    }
}
