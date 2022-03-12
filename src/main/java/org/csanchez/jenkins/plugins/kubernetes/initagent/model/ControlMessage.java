package org.csanchez.jenkins.plugins.kubernetes.initagent.model;

import java.io.Serializable;

public class ControlMessage implements Serializable {

    private final String kind;

    public ControlMessage(String kind) {
        this.kind = kind;
    }

    public String getKind() {
        return kind;
    }
}
