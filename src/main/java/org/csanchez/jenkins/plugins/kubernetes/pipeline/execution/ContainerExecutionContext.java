package org.csanchez.jenkins.plugins.kubernetes.pipeline.execution;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Proc;

import java.io.IOException;
import java.io.Serializable;

public interface ContainerExecutionContext extends Serializable {

    Proc scheduleExecution(
            EnvVars envVars,
            FilePath optionalWorkingDir,
            String[] commands,
            boolean[] masks,
            boolean isWindows
    ) throws IOException;
}
