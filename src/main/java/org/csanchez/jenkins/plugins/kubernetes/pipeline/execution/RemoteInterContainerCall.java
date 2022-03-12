package org.csanchez.jenkins.plugins.kubernetes.pipeline.execution;

import hudson.remoting.VirtualChannel;
import org.csanchez.jenkins.plugins.kubernetes.initagent.InterProcessExecutionCallable;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.ContainerStepExecution;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RemoteInterContainerCall extends Thread {


    private static final transient Logger LOGGER = Logger.getLogger(RemoteInterContainerCall.class.getName());

    private final VirtualChannel channel;
    private final InterProcessExecutionCallable call;
    private final OutputStream remoteInput;
    private final InputStream remoteOutput;


    public RemoteInterContainerCall(
            VirtualChannel channel,
            InterProcessExecutionCallable call,
            OutputStream remoteInput,
            InputStream remoteOutput) {
        this.channel = channel;
        this.call = call;
        this.remoteInput = remoteInput;
        this.remoteOutput = remoteOutput;

        setDaemon(true);
    }

    public OutputStream getRemoteInput() {
        return remoteInput;
    }

    @Override
    public void run() {
        try {
            channel.call(call);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "An I/O error occurred during remote execution.", e);
        } catch (InterruptedException e) {
            LOGGER.log(Level.FINE, "Remote execution has been interrupted.", e);
        }
    }

    public InputStream getRemoteStdout() {
        return remoteOutput;
    }


    public InputStream getRemoteStderr() {
        return remoteOutput;
    }
}
