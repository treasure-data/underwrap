package com.treasuredata.underwrap;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.accesslog.AccessLogHandler;
import io.undertow.server.handlers.accesslog.AccessLogReceiver;
import io.undertow.server.handlers.accesslog.DefaultAccessLogReceiver;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AccessLogHandlerFactory
{
    private static final String DEFAULT_LOG_FORMAT = "time:%t\thost:%a\tforwardedfor:%{i,X-Forwarded-For}\treq:%r\tstatus:%s\tsize:%b\tduration:%D.%T";

    private final Class applicationClass;
    private final String logFormat;
    private final Path accessLogPath;

    public AccessLogHandlerFactory(Class applicationClass, Path serverRootPath, Path accessLogPath, String logFormat)
    {
        this.applicationClass = applicationClass;

        if (logFormat == null) {
            logFormat = DEFAULT_LOG_FORMAT;
        }
        this.logFormat = logFormat;

        if (accessLogPath == null) {
            accessLogPath = Paths.get("log");
        }

        if (serverRootPath != null) {
            accessLogPath = serverRootPath.resolve(accessLogPath).toAbsolutePath();
        }
        this.accessLogPath = accessLogPath;
    }

    public AccessLogHandlerFactory(Class applicationClass, String logFormat)
    {
        this(applicationClass, null, null, logFormat);
    }

    public AccessLogHandlerFactory(Class applicationClass)
    {
        this(applicationClass, null, null, null);
    }

    public AccessLogHandlerFactory(Class applicationClass, Path serverRootPath, Path accessLogPath)
    {
        this(applicationClass, serverRootPath, accessLogPath, null);
    }

    public AccessLogHandler create(HttpHandler next)
    {
        Executor logWriterExecutor = Executors.newCachedThreadPool(
                new ThreadFactoryBuilder()
                        .setDaemon(false)  // non-daemon
                        .setNameFormat("access-log-%d")
                        .build()
        );

        File accessLogPath = accessLogPath();
        if (!accessLogPath.isDirectory()) {
            if (!accessLogPath.mkdir()) {
                throw new RuntimeException("Failed to create a directory for access log files");
            }
        }
        AccessLogReceiver logReceiver = new DefaultAccessLogReceiver(logWriterExecutor, accessLogPath, "access.", "log");

        return new AccessLogHandler(next, logReceiver, logFormat, applicationClass.getClassLoader());
    }

    @VisibleForTesting File accessLogPath()
    {
        return accessLogPath.toFile();
    }
}
