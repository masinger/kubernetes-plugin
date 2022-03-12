package org.csanchez.jenkins.plugins.kubernetes.pipeline.execution;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Proc;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public class InterContainerExecutionContext implements ContainerExecutionContext {

    private final RemoteInterContainerCall containerCall;
    private final OutputHandler outputHandler;

    public InterContainerExecutionContext(
            RemoteInterContainerCall containerCall,
            OutputHandler outputHandler
    ) {
        this.containerCall = containerCall;
        this.outputHandler = outputHandler;
    }

    @Override
    public Proc scheduleExecution(EnvVars envVars,
                                  FilePath optionalWorkingDir,
                                  String[] commands,
                                  boolean[] masks,
                                  boolean isWindows) throws IOException {


        OutputStream stdin = containerCall.getRemoteInput();
        PrintStream outputStream = outputHandler.getPrintStream();
        PrintStream in = new PrintStream(stdin, true, StandardCharsets.UTF_8.name());

        outputHandler.getToggleStdout().disable();
        ExecutionUtils.setWorkingDir(in, optionalWorkingDir, isWindows);
        ExecutionUtils.setupEnvironmentVariable(envVars, in, isWindows);
        ExecutionUtils.doExec(in, isWindows, outputStream, masks, commands);

        return new InterContainerProc(containerCall);


    }
}
