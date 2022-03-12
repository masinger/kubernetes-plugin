/*
 * Copyright (C) 2015 Original Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.csanchez.jenkins.plugins.kubernetes.pipeline.execution;


import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.*;
import hudson.model.Computer;
import hudson.model.Node;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.KubernetesNodeContext;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;

import java.io.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

/**
 * This decorator interacts directly with the Kubernetes exec API to run commands inside a container. It does not use
 * the Jenkins agent to execute commands.
 */
public class StrategicContainerExecutionDecorator extends LauncherDecorator implements Serializable, Closeable {

    /**
     * time in milliseconds to wait for checking whether the process immediately returned
     */
    public static final int COMMAND_FINISHED_TIMEOUT_MS = 200;
    private static final long serialVersionUID = 4419929753433397656L;
    private static final long DEFAULT_CONTAINER_READY_TIMEOUT = 5;
    private static final String CONTAINER_READY_TIMEOUT_SYSTEM_PROPERTY = StrategicContainerExecutionDecorator.class.getName() + ".containerReadyTimeout";
    private static final String WEBSOCKET_CONNECTION_TIMEOUT_SYSTEM_PROPERTY = StrategicContainerExecutionDecorator.class.getName()
            + ".websocketConnectionTimeout";
    /**
     * time to wait in seconds for websocket to connect
     */
    private static final int WEBSOCKET_CONNECTION_TIMEOUT = Integer
            .getInteger(WEBSOCKET_CONNECTION_TIMEOUT_SYSTEM_PROPERTY, 30);
    private static final long CONTAINER_READY_TIMEOUT = containerReadyTimeout();
    private static final String COOKIE_VAR = "JENKINS_SERVER_COOKIE";
    private static final Logger LOGGER = Logger.getLogger(StrategicContainerExecutionDecorator.class.getName());
    /**
     * stdin buffer size for commands sent to Kubernetes exec api. A low value will cause slowness in commands executed.
     * A higher value will consume more memory
     */
    private static final int STDIN_BUFFER_SIZE = Integer.getInteger(StrategicContainerExecutionDecorator.class.getName() + ".stdinBufferSize", 16 * 1024);
    @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "not needed on deserialization")
    private transient List<Closeable> closables;

    private String containerName;
    private EnvironmentExpander environmentExpander;
    private EnvVars globalVars;
    private EnvVars rcEnvVars;
    private String shell;
    private KubernetesNodeContext nodeContext;

    private ContainerExecutionStrategy containerExecutionStrategy;

    public StrategicContainerExecutionDecorator() {
    }

    @Deprecated
    public StrategicContainerExecutionDecorator(KubernetesClient client, String podName, String containerName, String namespace, EnvironmentExpander environmentExpander, FilePath ws) {
        this.containerName = containerName;
        this.environmentExpander = environmentExpander;
    }

    @Deprecated
    public StrategicContainerExecutionDecorator(KubernetesClient client, String podName, String containerName, String namespace, EnvironmentExpander environmentExpander) {
        this(client, podName, containerName, namespace, environmentExpander, null);
    }

    @Deprecated
    public StrategicContainerExecutionDecorator(KubernetesClient client, String podName, String containerName, String namespace) {
        this(client, podName, containerName, namespace, null, null);
    }

    @Deprecated
    public StrategicContainerExecutionDecorator(KubernetesClient client, String podName, String containerName, AtomicBoolean alive, CountDownLatch started, CountDownLatch finished, String namespace) {
        this(client, podName, containerName, namespace, null, null);
    }

    @Deprecated
    public StrategicContainerExecutionDecorator(KubernetesClient client, String podName, String containerName, AtomicBoolean alive, CountDownLatch started, CountDownLatch finished) {
        this(client, podName, containerName, (String) null, null, null);
    }

    @Deprecated
    public StrategicContainerExecutionDecorator(KubernetesClient client, String podName, String containerName, String path, AtomicBoolean alive, CountDownLatch started, CountDownLatch finished) {
        this(client, podName, containerName, (String) null, null, null);
    }

    static String[] getCommands(Launcher.ProcStarter starter, String containerWorkingDirStr, boolean unix) {
        List<String> allCommands = new ArrayList<String>();


        for (String cmd : starter.cmds()) {
            // BourneShellScript.launchWithCookie escapes $ as $$, we convert it to \$
            String fixedCommand = cmd.replaceAll("\\$\\$", Matcher.quoteReplacement("\\$"));
            if (unix) {
                fixedCommand = fixedCommand.replaceAll("\\\"", Matcher.quoteReplacement("\\\""));
            }

            String oldRemoteDir = null;
            FilePath oldRemoteDirFilepath = starter.pwd();
            if (oldRemoteDirFilepath != null) {
                oldRemoteDir = oldRemoteDirFilepath.getRemote();
            }
            if (oldRemoteDir != null && !oldRemoteDir.isEmpty() &&
                    !oldRemoteDir.equals(containerWorkingDirStr) && fixedCommand.contains(oldRemoteDir)) {
                // Container has a custom workingDir, update the dir in commands
                fixedCommand = fixedCommand.replaceAll(oldRemoteDir, containerWorkingDirStr);
            }
            allCommands.add(fixedCommand);
        }
        return allCommands.toArray(new String[allCommands.size()]);
    }

    private static Long containerReadyTimeout() {
        String timeout = System.getProperty(CONTAINER_READY_TIMEOUT_SYSTEM_PROPERTY, String.valueOf(DEFAULT_CONTAINER_READY_TIMEOUT));
        try {
            return Long.parseLong(timeout);
        } catch (NumberFormatException e) {
            return DEFAULT_CONTAINER_READY_TIMEOUT;
        }
    }

    private static String[] fixDoubleDollar(String[] envVars) {
        return Arrays.stream(envVars)
                .map(ev -> ev.replaceAll("\\$\\$", Matcher.quoteReplacement("$")))
                .toArray(String[]::new);
    }

    @Deprecated
    public KubernetesClient getClient() {
        try {
            return nodeContext.connectToCloud();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Deprecated
    public void setClient(KubernetesClient client) {
        // NOOP
    }

    @Deprecated
    // TODO make private
    public String getPodName() {
        try {
            return getNodeContext().getPodName();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Deprecated
    public void setPodName(String podName) {
        // NOOP
    }

    @Deprecated
    // TODO make private
    public String getNamespace() {
        try {
            return getNodeContext().getNamespace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Deprecated
    public void setNamespace(String namespace) {
        // NOOP
    }

    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    public EnvironmentExpander getEnvironmentExpander() {
        return environmentExpander;
    }

    public void setEnvironmentExpander(EnvironmentExpander environmentExpander) {
        this.environmentExpander = environmentExpander;
    }

    public EnvVars getGlobalVars() {
        return globalVars;
    }

    public void setGlobalVars(EnvVars globalVars) {
        this.globalVars = globalVars;
    }

    public EnvVars getRunContextEnvVars() {
        return this.rcEnvVars;
    }

    public void setRunContextEnvVars(EnvVars rcVars) {
        this.rcEnvVars = rcVars;
    }

    public void setShell(String shell) {
        this.shell = shell;
    }

    public KubernetesNodeContext getNodeContext() {
        return nodeContext;
    }

    public void setNodeContext(KubernetesNodeContext nodeContext) {
        this.nodeContext = nodeContext;
    }

    @Override
    public Launcher decorate(final Launcher launcher, final Node node) {

        //Allows other nodes to be provisioned inside the container clause
        //If the node is not a KubernetesSlave return the original launcher
        if (node != null && !(node instanceof KubernetesSlave)) {
            return launcher;
        }
        return new Launcher.DecoratedLauncher(launcher) {
            @Override
            public Proc launch(ProcStarter starter) throws IOException {
                LOGGER.log(Level.FINEST, "Launch proc with environment: {0}", Arrays.toString(starter.envs()));

                // find container working dir
                KubernetesSlave slave = (KubernetesSlave) node;
                FilePath containerWorkingDirFilePath = starter.pwd();
                String containerWorkingDirFilePathStr = containerWorkingDirFilePath != null
                        ? containerWorkingDirFilePath.getRemote() : ContainerTemplate.DEFAULT_WORKING_DIR;
                String containerWorkingDirStr = ContainerTemplate.DEFAULT_WORKING_DIR;
                if (slave != null && slave.getPod().isPresent() && containerName != null) {
                    Optional<Container> container = slave.getPod().get().getSpec().getContainers().stream()
                            .filter(container1 -> container1.getName().equals(containerName))
                            .findAny();
                    Optional<String> containerWorkingDir = Optional.empty();
                    if (container.isPresent() && container.get().getWorkingDir() != null) {
                        containerWorkingDir = Optional.of(container.get().getWorkingDir());
                    }
                    if (containerWorkingDir.isPresent()) {
                        containerWorkingDirStr = containerWorkingDir.get();
                    }

                    if (containerWorkingDir.isPresent() &&
                            containerWorkingDirFilePath != null &&
                            !containerWorkingDirFilePath.getRemote().startsWith(containerWorkingDirStr)) {
                        // Container has a custom workingDir, updated the pwd to match container working dir
                        containerWorkingDirFilePathStr = containerWorkingDirFilePath.getRemote().replaceFirst(
                                ContainerTemplate.DEFAULT_WORKING_DIR, containerWorkingDirStr);
                        containerWorkingDirFilePath = new FilePath(containerWorkingDirFilePath.getChannel(), containerWorkingDirFilePathStr);
                        LOGGER.log(Level.FINEST, "Modified the pwd to match {0} containers workspace directory : {1}",
                                new String[]{containerName, containerWorkingDirFilePathStr});
                    }
                }

                String[] envVars = starter.envs();
                if (node != null) { // It seems this is possible despite the method javadoc saying it is non-null
                    final Computer computer = node.toComputer();
                    if (computer != null) {
                        List<String> resultEnvVar = new ArrayList<>();
                        try {
                            EnvVars environment = computer.getEnvironment();
                            if (environment != null) {
                                Set<String> overriddenKeys = new HashSet<>();
                                for (String keyValue : envVars) {
                                    String[] split = keyValue.split("=", 2);
                                    if (!split[1].equals(environment.get(split[0]))) {
                                        // Only keep environment variables that differ from Computer's environment
                                        resultEnvVar.add(keyValue);
                                        overriddenKeys.add(split[0]);
                                    }
                                }

                                // modify the working dir on envvars part of Computer
                                if (!containerWorkingDirStr.equals(ContainerTemplate.DEFAULT_WORKING_DIR)) {
                                    for (Map.Entry<String, String> entry : environment.entrySet()) {
                                        if (entry.getValue().startsWith(ContainerTemplate.DEFAULT_WORKING_DIR)
                                                && !overriddenKeys.contains(entry.getKey())) {
                                            // Value should be overridden and is not overridden earlier
                                            String newValue = entry.getValue().replaceFirst(ContainerTemplate.DEFAULT_WORKING_DIR, containerWorkingDirStr);
                                            String keyValue = entry.getKey() + "=" + newValue;
                                            LOGGER.log(Level.FINEST, "Updated the value for envVar, key: {0}, Value: {1}",
                                                    new String[]{entry.getKey(), newValue});
                                            resultEnvVar.add(keyValue);
                                        }
                                    }
                                }
                                envVars = resultEnvVar.toArray(new String[resultEnvVar.size()]);
                            }
                        } catch (InterruptedException e) {
                            throw new IOException("Unable to retrieve environment variables", e);
                        }
                    }
                }
                return doLaunch(starter.quiet(), fixDoubleDollar(envVars), starter.stdout(), containerWorkingDirFilePath, starter.masks(),
                        getCommands(starter, containerWorkingDirFilePathStr, launcher.isUnix()));
            }

            private Proc doLaunch(boolean quiet, String[] cmdEnvs, OutputStream outputForCaller, FilePath pwd,
                                  boolean[] masks, String... commands) throws IOException {

                long startMethod = System.nanoTime();

                OutputHandler outputHandler = OutputHandler.create(quiet, outputForCaller, launcher.getListener().getLogger());

                String sh = shell != null ? shell : launcher.isUnix() ? "sh" : "cmd";
                String msg = "Executing " + sh + " script inside container " + containerName + " of pod " + getPodName();
                LOGGER.log(Level.FINEST, msg);
                outputHandler.getPrintStream().println(msg);

                if (closables == null) {
                    closables = new ArrayList<>();
                }


                containerExecutionStrategy = new InterContainerExecutionStrategy(
                        containerName
                );

                try {
                    ContainerExecutionContext executionContext = containerExecutionStrategy.start(
                            quiet,
                            launcher,
                            outputForCaller,
                            sh
                    );

                    EnvVars envVars = new EnvVars();

                    //get global vars here, run the export first as they'll get overwritten.
                    if (globalVars != null) {
                        envVars.overrideAll(globalVars);
                    }

                    if (rcEnvVars != null) {
                        envVars.overrideAll(rcEnvVars);
                    }

                    if (environmentExpander != null) {
                        environmentExpander.expand(envVars);
                    }

                    //setup specific command envs passed into cmd
                    if (cmdEnvs != null) {
                        for (String cmdEnv : cmdEnvs) {
                            envVars.addLine(cmdEnv);
                        }
                    }

                    // outputHandler.getToggleStdout().disable(); // TODO: Add to context

                    LOGGER.log(Level.FINEST, "Launching with env vars: {0}", envVars.toString());
                    Proc proc = executionContext.scheduleExecution(
                            envVars,
                            pwd,
                            commands,
                            masks,
                            !launcher.isUnix()
                    );

                    LOGGER.log(Level.INFO, "Created process inside pod: [" + getPodName() + "], container: ["
                            + containerName + "]" + "[" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startMethod) + " ms]");

                    //Proc proc = executionContext.createProc();
                    if (proc instanceof Closeable) {
                        closables.add((Closeable) proc);
                    }
                    return proc;
                } catch (InterruptedException ie) {
                    throw new InterruptedIOException(ie.getMessage());
                }
            }

            @Override
            public void kill(Map<String, String> modelEnvVars) throws IOException, InterruptedException {
                getListener().getLogger().println("Killing processes");

                String cookie = modelEnvVars.get(COOKIE_VAR);

                int exitCode = doLaunch(
                        true, null, null, null, null,
                        // TODO Windows
                        "sh", "-c", "kill \\`grep -l '" + COOKIE_VAR + "=" + cookie + "' /proc/*/environ | cut -d / -f 3 \\`"
                ).join();

                getListener().getLogger().println("kill finished with exit code " + exitCode);
            }

        };
    }

    @Override
    public void close() throws IOException {
        if (closables == null) return;

        for (Closeable closable : closables) {
            try {
                closable.close();
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "failed to close", e);
            }
        }
    }

    @Deprecated
    public void setKubernetesClient(KubernetesClient client) {
        // NOOP
    }

}
