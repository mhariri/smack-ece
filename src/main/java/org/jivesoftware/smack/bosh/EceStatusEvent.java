package org.jivesoftware.smack.bosh;

public enum EceStatusEvent {
    CHAT_STARTED('-'),
    CHAT_CLOSED('_');

    private char token;

    EceStatusEvent(char token) {
        this.token = token;
    }

    public static EceStatusEvent extract(String msgBody) {
        for (EceStatusEvent v : values()) {
            if (v.getToken() == msgBody.charAt(0)) {
                return v;
            }
        }
        return null;
    }

    public char getToken() {
        return token;
    }
}
