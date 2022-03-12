package org.csanchez.jenkins.plugins.kubernetes.pipeline.execution;

import hudson.AbortException;
import hudson.Launcher;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.Execable;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.ContainerExecDecorator;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.KubernetesNodeContext;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class KubeApiExecStrategy implements ContainerExecutionStrategy {

    /**
     * time in milliseconds to wait for checking whether the process immediately returned
     */
    public static final int COMMAND_FINISHED_TIMEOUT_MS = 200;
    private static final Logger LOGGER = Logger.getLogger(KubeApiExecStrategy.class.getName());
    /**
     * stdin buffer size for commands sent to Kubernetes exec api. A low value will cause slowness in commands executed.
     * A higher value will consume more memory
     */
    private static final int STDIN_BUFFER_SIZE = Integer.getInteger(ContainerExecDecorator.class.getName() + ".stdinBufferSize", 16 * 1024);
    private static final String WEBSOCKET_CONNECTION_TIMEOUT_SYSTEM_PROPERTY = StrategicContainerExecutionDecorator.class.getName()
            + ".websocketConnectionTimeout";
    /**
     * time to wait in seconds for websocket to connect
     */
    private static final int WEBSOCKET_CONNECTION_TIMEOUT = Integer
            .getInteger(WEBSOCKET_CONNECTION_TIMEOUT_SYSTEM_PROPERTY, 30);
    private final KubernetesNodeContext nodeContext;
    private final String namespace;
    private final String podName;
    private final String containerName;

    public KubeApiExecStrategy(KubernetesNodeContext nodeContext,
                               String namespace,
                               String podName,
                               String containerName
    ) {
        this.nodeContext = nodeContext;
        this.namespace = namespace;
        this.podName = podName;
        this.containerName = containerName;
    }

    private static void closeWatch(ExecWatch watch) {
        try {
            watch.close();
        } catch (Exception e) {
            LOGGER.log(Level.INFO, "failed to close watch", e);
        }
    }

    private KubernetesClient getClient() {
        try {
            return nodeContext.connectToCloud();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ContainerExecutionContext start(boolean quiet, Launcher launcher, OutputStream outputForCaller, String executable) throws IOException {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(1);
        final AtomicBoolean alive = new AtomicBoolean(false);
        final AtomicLong startAlive = new AtomicLong();

        OutputHandler outputHandler = OutputHandler.create(
                quiet,
                outputForCaller,
                launcher.getListener().getLogger()
        );


        Execable<String, ExecWatch> execable = getClient().pods().inNamespace(namespace).withName(podName).inContainer(containerName) //
                .redirectingInput(STDIN_BUFFER_SIZE) // JENKINS-50429
                .writingOutput(outputHandler.getStream()).writingError(outputHandler.getStream()).writingErrorChannel(outputHandler.getError())
                .usingListener(new ExecListener() {
                    @Override
                    public void onOpen() {
                        alive.set(true);
                        started.countDown();
                        startAlive.set(System.nanoTime());
                        LOGGER.log(Level.FINEST, "onOpen : {0}", finished);
                    }

                    @Override
                    public void onFailure(Throwable t, Response response) {
                        alive.set(false);
                        t.printStackTrace(launcher.getListener().getLogger());
                        started.countDown();
                        LOGGER.log(Level.FINEST, "onFailure : {0}", finished);
                        if (finished.getCount() == 0) {
                            LOGGER.log(Level.WARNING,
                                    "onFailure called but latch already finished. This may be a bug in the kubernetes-plugin");
                        }
                        finished.countDown();
                    }

                    @Override
                    public void onClose(int i, String s) {
                        alive.set(false);
                        started.countDown();
                        LOGGER.log(Level.FINEST, "onClose : {0} [{1} ms]", new Object[]{finished, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startAlive.get())});
                        if (finished.getCount() == 0) {
                            LOGGER.log(Level.WARNING,
                                    "onClose called but latch already finished. This indicates a bug in the kubernetes-plugin");
                        }
                        finished.countDown();
                    }
                });

        ExecWatch watch;
        try {
            watch = execable.exec(executable);
        } catch (KubernetesClientException e) {
            if (e.getCause() instanceof InterruptedException) {
                throw new IOException(
                        "Interrupted while starting websocket connection, you should increase the Max connections to Kubernetes API",
                        e);
            } else {
                throw e;
            }
        } catch (RejectedExecutionException e) {
            throw new IOException(
                    "Connection was rejected, you should increase the Max connections to Kubernetes API", e);
        }

        boolean hasStarted = false;
        try {
            // prevent a wait forever if the connection is closed as the listener would never be called
            hasStarted = started.await(WEBSOCKET_CONNECTION_TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            closeWatch(watch);
            throw new IOException(
                    "Interrupted while waiting for websocket connection, you should increase the Max connections to Kubernetes API",
                    e);
        }

        if (!hasStarted) {
            closeWatch(watch);
            throw new IOException("Timed out waiting for websocket connection. "
                    + "You should increase the value of system property "
                    + WEBSOCKET_CONNECTION_TIMEOUT_SYSTEM_PROPERTY + " currently set at "
                    + WEBSOCKET_CONNECTION_TIMEOUT + " seconds");
        }

        try {
            // Depends on the ping time with the Kubernetes API server
            // Not fully satisfied with this solution because it can delay the execution
            if (finished.await(COMMAND_FINISHED_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                launcher.getListener().error("Process exited immediately after creation. See output below%n%s", outputHandler.getStdout().toString(StandardCharsets.UTF_8.name()));
                throw new AbortException("Process exited immediately after creation. Check logs above for more details.");
            }
        } catch (InterruptedException ie) {
            throw new InterruptedIOException(ie.getMessage());
        } catch (Exception e) {
            closeWatch(watch);
            throw e;
        }

        return new KubeApiExecutionContext(
                alive,
                finished,
                watch,
                outputHandler
        );
    }

}
