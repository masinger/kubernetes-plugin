package org.csanchez.jenkins.plugins.kubernetes.initagent.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class ProcessTerminatedResponse extends ControlMessage implements Serializable {

    private final int exitCode;

    public ProcessTerminatedResponse(
            String kind,
            int exitCode
    ) {
        super(kind);
        this.exitCode = exitCode;
    }

    public ProcessTerminatedResponse(
            @JsonProperty("exitCode") int exitCode
    ) {
        super("ProcessTerminated");
        this.exitCode = exitCode;
    }

    public int getExitCode() {
        return exitCode;
    }
}
