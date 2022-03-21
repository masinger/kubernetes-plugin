package org.csanchez.jenkins.plugins.kubernetes.pipeline.execution;

import hudson.Launcher;
import hudson.remoting.RemoteInputStream;
import hudson.remoting.RemoteOutputStream;
import org.apache.commons.io.output.TeeOutputStream;
import org.csanchez.jenkins.plugins.kubernetes.initagent.InterProcessExecutionCallable;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

public class InterContainerExecutionStrategy implements ContainerExecutionStrategy {

    public static final String NON_ALPHANUMERIC_CHARS = "[^A-z0-9]";
    private final String container;

    public InterContainerExecutionStrategy(String container) {
        this.container = container;
    }


    private static String getCanonicalContainerName(String container) {
        return container.toUpperCase()
                .replaceAll(NON_ALPHANUMERIC_CHARS, "_");
    }

    public static String getInitAgentPortEnvVariableName(String container) {
        return String.format("INIT_AGENT_%s", getCanonicalContainerName(container));
    }

    @Override
    public ContainerExecutionContext start(boolean quiet,
                                           Launcher launcher,
                                           OutputStream outputForCaller,
                                           String executable) throws IOException {
        OutputHandler outputHandler = OutputHandler.create(quiet, outputForCaller, launcher.getListener().getLogger());

        PipedInputStream stdinPipedInput = new PipedInputStream();
        PipedOutputStream stdinPipeOutput = new PipedOutputStream(stdinPipedInput);

        RemoteInputStream in = new RemoteInputStream(stdinPipedInput, RemoteInputStream.Flag.GREEDY);

        PipedOutputStream stdoutPipedOutput = new PipedOutputStream();
        PipedInputStream stdoutPipedInput = new PipedInputStream(stdoutPipedOutput);

        RemoteOutputStream out = new RemoteOutputStream(
                new TeeOutputStream(outputHandler.getStream(), stdoutPipedOutput)
        );


        InterProcessExecutionCallable callable = new InterProcessExecutionCallable(
                container,
                executable,
                out,
                out,
                in
        );

        RemoteInterContainerCall remoteInterContainerCall = new RemoteInterContainerCall(
                launcher.getChannel(),
                callable,
                stdinPipeOutput,
                stdoutPipedInput
        );

        remoteInterContainerCall.start();

        return new InterContainerExecutionContext(
                remoteInterContainerCall,
                outputHandler
        );
    }
}
