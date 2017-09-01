package com.treasuredata.underwrap;

import io.undertow.Undertow;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.xnio.Options;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.*;

public class UnderwrapServerTest
{
    private static final int WORKER_THREADS_IN_TEST = 4;

    private int serverPort;
    private UnderwrapServer server;

    @Path("/")
    @Consumes("application/json")
    public static class TestResource
    {
        @GET
        @Path("/slow")
        public void slowResponse()
                throws InterruptedException
        {
            TimeUnit.SECONDS.sleep(4);
        }
    }

    public static class TestApplication
        extends UnderwrapServer.UnderwrapApplication
    {
        @Override
        protected void registerResources(Set<Class<?>> classes)
        {
            classes.add(TestResource.class);
        }
    }

    private void startServer(Optional<UnderwrapServer.HandlerBuildFunction> handlerBuildFunction)
    {
        UnderwrapServer.ServerBuildFunction serverBuildFunction = (sb) -> {
            return sb
            .addHttpListener(0, "0.0.0.0")
            .setWorkerThreads(WORKER_THREADS_IN_TEST)
            .setSocketOption(Options.REUSE_ADDRESSES, true);
        };

        if (handlerBuildFunction.isPresent()) {
            server.start(Collections.emptyMap(), null, handlerBuildFunction.get(), serverBuildFunction);
        }
        else {
            server.start(Collections.emptyMap(), null, serverBuildFunction);
        }

        List<Undertow.ListenerInfo> listenerInfo = server.getListenerInfo();
        assertThat(listenerInfo.size(), is(1));

        InetSocketAddress address = (InetSocketAddress) listenerInfo.get(0).getAddress();
        serverPort = address.getPort();
    }

    @Before
    public void setUp()
    {
        server = new UnderwrapServer(TestApplication.class);
    }

    @After
    public void tearDown()
    {
        if (server != null) {
            server.stop();
        }
    }

    private WebTarget createTarget(String path)
    {
        Client client = ClientBuilder.newClient();
        return client.target(String.format("http://localhost:%d%s", serverPort, path));
    }

    private Response getHttpResponse(String path)
    {
        WebTarget target = createTarget(path);
        return target.request().get();
    }

    @Test
    public void buildHandler()
    {
        final AtomicInteger counter = new AtomicInteger(0);

        startServer(
                Optional.of(
                        handler -> {
                            counter.incrementAndGet();
                            return handler;
                        }));

        assertThat(counter.get(), is(1));
    }

    @Test
    public void gracefulShutdown()
            throws InterruptedException, ExecutionException, TimeoutException
    {
        startServer(Optional.empty());

        // Send a HTTP request and wait for the response in another thread
        ExecutorService executorService = Executors.newCachedThreadPool();
        Future<Response> future = executorService.submit(() -> getHttpResponse("/slow"));

        // Wait 1 sec and stop the server
        executorService.execute(() -> {
            try {
                TimeUnit.SECONDS.sleep(2);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
            server.stop();
        });

        try {
            Response response = future.get(6, TimeUnit.SECONDS);
            assertThat(response.getStatus(), is(204));
        }
        finally {
            executorService.shutdownNow();
        }
    }

    @Test
    public void getMetrics()
    {
        startServer(Optional.empty());

        UnderwrapMetrics m = server.getMetrics();
        assertThat(m, is(instanceOf(UnderwrapMetrics.class)));
        assertThat(m.getCoreWorkerPoolSize(), is(WORKER_THREADS_IN_TEST));
        assertThat(m.getMaxWorkerPoolSize(), is(WORKER_THREADS_IN_TEST));
        assertThat(m.getBusyWorkerThreadCount(), is(lessThan(WORKER_THREADS_IN_TEST)));
        assertThat(m.getWorkerQueueSize(), is(lessThan(WORKER_THREADS_IN_TEST)));
    }
}
