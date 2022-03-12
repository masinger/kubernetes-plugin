package org.csanchez.jenkins.plugins.kubernetes.initagent.model;

public class AttachStreamMessage extends ControlMessage {

    public static final String OUT_STREAM = "Out";
    public static final String ERR_STREAM = "Err";

    private final String session;
    private final String streamType;

    public AttachStreamMessage(String session, String streamType) {
        super("AttachStream");

        this.session = session;
        this.streamType = streamType;
    }

    public String getSession() {
        return session;
    }

    public String getStreamType() {
        return streamType;
    }
}
