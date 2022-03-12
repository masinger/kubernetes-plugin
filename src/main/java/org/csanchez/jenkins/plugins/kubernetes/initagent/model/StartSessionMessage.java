package org.csanchez.jenkins.plugins.kubernetes.initagent.model;

import javax.sound.sampled.Control;

public class StartSessionMessage extends ControlMessage {

    public StartSessionMessage() {
        super("StartSession");
    }
}
