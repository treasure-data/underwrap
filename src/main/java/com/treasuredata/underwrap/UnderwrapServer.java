package com.treasuredata.underwrap;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.GracefulShutdownHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.jboss.resteasy.plugins.server.servlet.HttpServlet30Dispatcher;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.XnioWorker;

import javax.servlet.ServletException;
import javax.ws.rs.core.Application;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static io.undertow.servlet.Servlets.servlet;

public class UnderwrapServer
{
    private static final Logger LOG = LoggerFactory.getLogger(UnderwrapServer.class);
    private final Class<? extends UnderwrapApplication> applicationClass;
    private final Path serverRootPath;

    private boolean accessLogEnabled;
    private String accessLogFormat;
    private Path accessLogPath;

    private Undertow undertow;
    private DeploymentManager deploymentManager;
    private GracefulShutdownHandler gracefulShutdownHandler;
    private HttpHandler httpHandler;

    @FunctionalInterface
    public interface DeploymentInfoBuildFunction
    {
        DeploymentInfo process(DeploymentInfo base);
    }

    @FunctionalInterface
    public interface HandlerBuildFunction
    {
        HttpHandler process(HttpHandler base);
    }

    @FunctionalInterface
    public interface ServerBuildFunction
    {
        Undertow.Builder build(Undertow.Builder base);
    }

    public UnderwrapServer(Class<? extends UnderwrapApplication> applicationClass)
    {
        this(applicationClass, null);
    }

    public UnderwrapServer(Class<? extends UnderwrapApplication> applicationClass, Path serverRootPath)
    {
        this.applicationClass = applicationClass;
        if (serverRootPath == null) {
            serverRootPath = Paths.get(System.getProperty("user.dir"));
        }
        this.serverRootPath = serverRootPath;
    }

    public void setAccessLogEnabled(boolean value)
    {
        this.accessLogEnabled = value;
    }

    public void setAccessLogFormat(String accessLogFormat)
    {
        this.accessLogFormat = accessLogFormat;
    }

    public void setAccessLogPath(Path accessLogPath)
    {
        this.accessLogPath = accessLogPath;
    }

    private void deploy(Map<Class<?>, Object> contextMap, DeploymentInfoBuildFunction deploymentInfoBuildFunction, HandlerBuildFunction handlerBuildFunction)
    {
        // Construct deployment information
        ResteasyDeployment resteasyDeployment = new ResteasyDeployment();
        resteasyDeployment.setApplicationClass(applicationClass.getName());
        DeploymentInfo di = new DeploymentInfo()
                .addServletContextAttribute(ResteasyDeployment.class.getName(), resteasyDeployment)
                .addServlet(
                        servlet("ResteasyServlet", HttpServlet30Dispatcher.class)
                                .setAsyncSupported(true)
                                .setLoadOnStartup(1)
                                .addMapping("/*")
            );

        // Delegate a build of DeployInfo to `deploymentInfoBuildFunction`
        if (deploymentInfoBuildFunction != null) {
            di = deploymentInfoBuildFunction.process(di);
        }

        // Take care of some mandatory attributes
        if (di.getDeploymentName() == null) {
            di.setDeploymentName("UnderWrap");
        }
        if (di.getClassLoader() == null) {
            di.setClassLoader(applicationClass.getClassLoader());
        }
        if (di.getContextPath() == null) {
            di.setContextPath("");
        }

        // Start a deployment manager. This manager is responsible for controlling the lifecycle of a servlet deployment.
        // Finally, a root handler is build.
        ServletContainer container = Servlets.defaultContainer();
        deploymentManager = container.addDeployment(di);

        deploymentManager.deploy();
        PathHandler pathHandler = new PathHandler();
        try {
            pathHandler.addPrefixPath(di.getContextPath(), deploymentManager.start());
        }
        catch (ServletException e) {
            throw new RuntimeException(e);
        }

        gracefulShutdownHandler = new GracefulShutdownHandler(handlerBuildFunction.process(pathHandler));

        if (accessLogEnabled) {
            // TODO: Make it enable to set custom format and access log path
            httpHandler = new AccessLogHandlerFactory(applicationClass, serverRootPath, accessLogPath, accessLogFormat).create(gracefulShutdownHandler);
        }
        else {
            httpHandler = gracefulShutdownHandler;
        }

        // Set instances we want to pass via @Context annotation
        if (contextMap != null) {
            for (Map.Entry<Class<?>, Object> contextTuple : contextMap.entrySet()) {
                resteasyDeployment.getDispatcher().getDefaultContextObjects().put(contextTuple.getKey(), contextTuple.getValue());
            }
        }
    }

    private HttpHandler defaultBuildHandler(final HttpHandler pathHandler)
    {
        return pathHandler;
    }

    private void buildAndStartServer(ServerBuildFunction serverBuildFunction)
    {
        // Configure Undertow server and start it with the root handler
        Undertow.Builder serverBuilder = Undertow.builder();

        if (serverBuildFunction != null) {
            serverBuilder = serverBuildFunction.build(serverBuilder);
        }

        undertow = serverBuilder.setHandler(httpHandler).build();
        undertow.start();
    }

    public synchronized void start(Map<Class<?>, Object> contextMap, DeploymentInfoBuildFunction deploymentInfoBuildFunction, ServerBuildFunction serverBuildFunction)
    {
        start(contextMap, deploymentInfoBuildFunction, this::defaultBuildHandler, serverBuildFunction);
    }

    public void start(Map<Class<?>, Object> contextMap, DeploymentInfoBuildFunction deploymentInfoBuildFunction, HandlerBuildFunction handlerBuildFunction, ServerBuildFunction serverBuildFunction)
    {
        deploy(contextMap, deploymentInfoBuildFunction, handlerBuildFunction);
        buildAndStartServer(serverBuildFunction);
    }

    private void shutdownGracefulShutdownHandler()
    {
        if (gracefulShutdownHandler != null) {
            gracefulShutdownHandler.shutdown();
            try {
                gracefulShutdownHandler.awaitShutdown(30 * 1000);
            }
            catch (InterruptedException e) {
                LOG.warn("Interrupted when waiting gracefulShutdownHandler shutdown", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    private void shutdownWorker()
    {
        if (undertow != null && undertow.getWorker() != null) {
            undertow.getWorker().shutdown();
            boolean workerFinished = false;
            try {
                if (undertow.getWorker().awaitTermination(30, TimeUnit.SECONDS)) {
                    workerFinished = true;
                }
            }
            catch (InterruptedException e) {
                LOG.warn("Interrupted when waiting worker termination", e);
                Thread.currentThread().interrupt();
            }
            if (!workerFinished) {
                undertow.getWorker().shutdownNow();
            }
        }
    }

    public synchronized void stop()
    {
        shutdownGracefulShutdownHandler();

        shutdownWorker();

        // Stop and undeploy DeploymentManager. And then stop Undertow
        // Avoiding NPE occurs when multiple threads call io.undertow.servlet.api.DeploymentManager.stop()...
        Failsafe.with(
                new RetryPolicy()
                        .retryOn(ServletException.class)
                        .withDelay(2, TimeUnit.SECONDS)
                        .withMaxRetries(15)
        ).run(() -> {
            LOG.info("deploymentManager: deployment={}, state={}", deploymentManager.getDeployment(), deploymentManager.getState());
            if (deploymentManager.getState() == DeploymentManager.State.STARTED) {
                deploymentManager.stop();
            }
        });

        if (deploymentManager.getState() != DeploymentManager.State.UNDEPLOYED) {
            deploymentManager.undeploy();
        }

        undertow.stop();
    }

    public UnderwrapMetrics getMetrics()
    {
        return new UnderwrapMetrics(undertow.getWorker());
    }

    public XnioWorker getXnioWorker()
    {
        return undertow.getWorker();
    }

    public List<Undertow.ListenerInfo> getListenerInfo()
    {
        return undertow.getListenerInfo();
    }

    public static class UnderwrapApplication
        extends Application
    {
        @Override
        public Set<Class<?>> getClasses()
        {
            Set<Class<?>> classes = new HashSet<>();

            registerResources(classes);
            registerMessageBodyProviders(classes);
            registerExceptionHandlers(classes);

            return classes;
        }

        protected void registerExceptionHandlers(Set<Class<?>> classes)
        {
        }

        protected void registerMessageBodyProviders(Set<Class<?>> classes)
        {
        }

        protected void registerResources(Set<Class<?>> classes)
        {
        }
    }
}
