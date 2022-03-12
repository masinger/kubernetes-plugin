package org.csanchez.jenkins.plugins.kubernetes.pipeline.execution;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Proc;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.ContainerExecProc;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class KubeApiExecutionContext implements ContainerExecutionContext {

    private final AtomicBoolean alive;
    private final CountDownLatch finished;
    private final ExecWatch watch;
    private final OutputHandler outputHandler;


    public KubeApiExecutionContext(
            AtomicBoolean alive,
            CountDownLatch finished,
            ExecWatch watch,
            OutputHandler outputHandler
    ) {
        this.alive = alive;
        this.finished = finished;
        this.watch = watch;
        this.outputHandler = outputHandler;
    }

    @Override
    public Proc scheduleExecution(
            EnvVars envVars,
            FilePath optionalWorkingDir,
            String[] commands,
            boolean[] masks,
            boolean isWindows
    ) throws IOException {
        OutputStream stdin = watch.getInput();
        PrintStream outputStream = outputHandler.getPrintStream();
        PrintStream in = new PrintStream(stdin, true, StandardCharsets.UTF_8.name());

        outputHandler.getToggleStdout().disable();
        ExecutionUtils.setWorkingDir(in, optionalWorkingDir, isWindows);
        ExecutionUtils.setupEnvironmentVariable(envVars, in, isWindows);
        ExecutionUtils.doExec(in, isWindows, outputStream, masks, commands);

        return new ContainerExecProc(watch, alive, finished, watch.getInput(), outputHandler.getError());
    }



}
