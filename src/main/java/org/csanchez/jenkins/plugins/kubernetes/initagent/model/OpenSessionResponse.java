package org.csanchez.jenkins.plugins.kubernetes.initagent.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class OpenSessionResponse extends ControlMessage {

    private final String session;

    @JsonCreator
    public OpenSessionResponse(@JsonProperty("session") String session) {
        super("SessionStarted");
        this.session = session;
    }

    public String getSession() {
        return session;
    }
}
