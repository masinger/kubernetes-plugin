package org.csanchez.jenkins.plugins.kubernetes.initagent;

import jenkins.security.MasterToSlaveCallable;
import org.apache.commons.compress.utils.IOUtils;
import org.csanchez.jenkins.plugins.kubernetes.initagent.model.ProcessTerminatedResponse;
import org.csanchez.jenkins.plugins.kubernetes.initagent.model.StartProcessMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class InterProcessExecutionCallable extends MasterToSlaveCallable<ProcessTerminatedResponse, IOException> {


    private final String container;
    private final String executable;
    private final OutputStream out;
    private final OutputStream err;
    private final InputStream in;

    public InterProcessExecutionCallable(
            String container,
            String executable,
            OutputStream out,
            OutputStream err,
            InputStream in
    ) {
        this.container = container;
        this.executable = executable;

        this.out = out;
        this.err = err;
        this.in = in;
    }

    @Override
    public ProcessTerminatedResponse call() throws IOException {
        Integer port = Integer.valueOf(System.getenv("INIT_AGENT_" + container));
        InterContainerClient interContainerClient = new InterContainerClient();
        InterContainerChannel containerChannel = interContainerClient.connect("localhost", port);

        InputStream remoteOutput = containerChannel.getRemoteOutput();
        InputStream remoteError = containerChannel.getRemoteError();
        OutputStream remoteInput = containerChannel.getRemoteInput();

        Thread copyOutput = new Thread(() -> {
            try {
                IOUtils.copy(remoteOutput, out);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        copyOutput.start();

        Thread copyError = new Thread(() -> {
            try {
                IOUtils.copy(remoteError, err);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        copyError.start();

        Thread copyStdin = new Thread(() -> {
            try {
                IOUtils.copy(in, remoteInput);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        copyStdin.start();

        containerChannel.sendMessage(new StartProcessMessage(executable));

        return containerChannel.readMessage(ProcessTerminatedResponse.class);
    }


    public InputStream getIn() {
        return in;
    }

    public OutputStream getErr() {
        return err;
    }

    public OutputStream getOut() {
        return out;
    }
}
