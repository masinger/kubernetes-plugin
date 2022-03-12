package org.csanchez.jenkins.plugins.kubernetes.pipeline.execution;

import hudson.Proc;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class InterContainerProc extends Proc implements Closeable {
    private final RemoteInterContainerCall remoteInterContainerCall;

    public InterContainerProc(
            RemoteInterContainerCall remoteInterContainerCall
    ) {
        this.remoteInterContainerCall = remoteInterContainerCall;
    }

    @Override
    public boolean isAlive() throws IOException, InterruptedException {
        return remoteInterContainerCall.isAlive();
    }

    @Override
    public void kill() throws IOException, InterruptedException {
        remoteInterContainerCall.interrupt();
    }

    @Override
    public int join() throws IOException, InterruptedException {
        remoteInterContainerCall.join();
        return 0;
    }

    @Override
    public InputStream getStdout() {
        return remoteInterContainerCall.getRemoteStdout();
    }

    @Override
    public InputStream getStderr() {
        return remoteInterContainerCall.getRemoteStderr();
    }

    @Override
    public OutputStream getStdin() {
        return remoteInterContainerCall.getRemoteInput();
    }

    @Override
    public void close() throws IOException {
        if(remoteInterContainerCall.isAlive()) {
            remoteInterContainerCall.interrupt();
        }
    }
}
