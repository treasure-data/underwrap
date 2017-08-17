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
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

public class UnderwrapServerTest
{
    private int serverPort;
    private UnderwrapServer server;

    @Path("/")
    @Consumes("application/json")
    public static class TestResource
    {
        @GET
        @Path("/fast")
        public void fastResponse()
                throws InterruptedException
        {
            TimeUnit.NANOSECONDS.sleep(1);
        }

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

    @Before
    public void setUp()
    {
        // Start UnderwrapServer
        server = new UnderwrapServer(TestApplication.class);
        server.start(Collections.emptyMap(), null, sb -> sb.addHttpListener(0, "0.0.0.0").setSocketOption(Options.REUSE_ADDRESSES, true));

        List<Undertow.ListenerInfo> listenerInfo = server.getListenerInfo();
        assertThat(listenerInfo.size(), is(1));

        InetSocketAddress address = (InetSocketAddress) listenerInfo.get(0).getAddress();
        serverPort = address.getPort();

        waitServer();
    }

    @After
    public void tearDown()
    {
        if (server != null) {
            server.stop();
        }
    }

    private void waitServer()
    {
        long timeout = System.nanoTime() + 3_000_000_000L;
        try {
            while (System.nanoTime() < timeout) {
                if (getSuccessfulResponse("/fast")) {
                    break;
                }
                TimeUnit.MILLISECONDS.sleep(100);
            }
        }
        catch (InterruptedException e) {
            e.printStackTrace();
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

    private boolean getSuccessfulResponse(String path)
    {
        Response res = getHttpResponse(path);
        return res.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL;
    }

    @Test
    public void buildHandler()
    {
        UnderwrapServer server2 = new UnderwrapServer(TestApplication.class);
        try {
            AtomicInteger counter = new AtomicInteger(0);
            server2.start(
                    Collections.emptyMap(),
                    null,
                    handler -> {
                        counter.incrementAndGet();
                        return handler;
                    },
                    sb -> sb
                        .addHttpListener(0, "0.0.0.0")
                        .setSocketOption(Options.REUSE_ADDRESSES, true)
            );
            assertThat(counter.get(), is(1));
        }
        finally {
            server2.stop();
        }
    }

    @Test
    public void gracefulShutdown()
            throws InterruptedException, ExecutionException, TimeoutException
    {
        // Send a HTTP request and wait for the response in another thread
        ExecutorService executorService = Executors.newCachedThreadPool();
        Future<Response> future = executorService.submit(() -> getHttpResponse("/slow"));

        // Wait 1 sec and stop the server
        executorService.execute(() -> {
            try {
                TimeUnit.SECONDS.sleep(1);
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
}