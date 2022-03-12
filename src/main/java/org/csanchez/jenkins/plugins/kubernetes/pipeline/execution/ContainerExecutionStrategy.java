package org.csanchez.jenkins.plugins.kubernetes.pipeline.execution;

import hudson.Launcher;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

public interface ContainerExecutionStrategy extends Serializable {


    ContainerExecutionContext start(boolean quiet,
                                    Launcher launcher,
                                    OutputStream outputForCaller,
                                    String executable) throws IOException;

}
