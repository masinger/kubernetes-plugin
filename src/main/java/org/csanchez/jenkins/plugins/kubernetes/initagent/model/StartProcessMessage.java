package org.csanchez.jenkins.plugins.kubernetes.initagent.model;

public class StartProcessMessage extends ControlMessage {

    private final String exec;

    public StartProcessMessage(String executable) {
        super("StartProcess");
        this.exec = executable;
    }

    public String getExec() {
        return exec;
    }
}
