package com.treasuredata.underwrap.server;

import com.google.common.annotations.VisibleForTesting;
import io.undertow.Undertow;
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

import javax.servlet.ServletException;
import javax.ws.rs.core.Application;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static io.undertow.servlet.Servlets.servlet;

public class UnderwrapServer
{
    private static final Logger LOG = LoggerFactory.getLogger(UnderwrapServer.class);
    private final Class<? extends Application> applicationClass;
    private final Path serverRootPath;
    private Undertow undertow;
    private DeploymentManager deploymentManager;
    private GracefulShutdownHandler gracefulShutdownHandler;

    @FunctionalInterface
    public interface ServerBuild
    {
        void build(Undertow.Builder serverBuilder);
    }

    public UnderwrapServer(Class<? extends UnderwrapApplication> applicationClass, Path serverRootPath)
    {
        this.applicationClass = applicationClass;
        this.serverRootPath = serverRootPath;
    }

    private void deploy(Map<Class, Object> contextMap)
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
        di.setClassLoader(applicationClass.getClassLoader());
        di.setContextPath("").setDeploymentName("bigdam-dddb");

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

        // TODO: Make it enable to set custom format and access log path
        gracefulShutdownHandler = new GracefulShutdownHandler(new AccessLogHandlerFactory(applicationClass, serverRootPath, null).create(pathHandler));

        // Set instances we want to pass via @Context annotation
        for (Map.Entry<Class, Object> contextTuple : contextMap.entrySet()) {
            resteasyDeployment.getDispatcher().getDefaultContextObjects().put(contextTuple.getKey(), contextTuple.getValue());
        }
    }

    private void buildAndStartServer(ServerBuild builder)
    {
        // Configure Undertow server and start it with the root handler
        Undertow.Builder serverBuilder = Undertow.builder();

        builder.build(serverBuilder);

        undertow = serverBuilder.setHandler(gracefulShutdownHandler).build();
        undertow.start();
    }

    public void start(Map<Class, Object> contextMap, ServerBuild builder)
    {
        deploy(contextMap);
        buildAndStartServer(builder);
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

    @VisibleForTesting
    Undertow getUndertow()
    {
        return undertow;
    }

    public static class UnderwrapApplication
        extends Application
    {
        @Override
        public Set<Class<?>> getClasses()
        {
            HashSet<Class<?>> classes = new HashSet<>();

            registerResources(classes);
            registerMessageBodyProviders(classes);
            registerExceptionHandlers(classes);

            return classes;
        }

        protected void registerExceptionHandlers(HashSet<Class<?>> classes)
        {
        }

        protected void registerMessageBodyProviders(HashSet<Class<?>> classes)
        {
        }

        protected void registerResources(HashSet<Class<?>> classes)
        {
        }
    }
}
