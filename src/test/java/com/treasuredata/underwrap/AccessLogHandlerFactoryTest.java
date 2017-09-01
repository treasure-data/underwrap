package com.treasuredata.underwrap;

import org.junit.Test;

import javax.ws.rs.core.Application;

import java.io.File;
import java.nio.file.Paths;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

public class AccessLogHandlerFactoryTest
{
    static class DummyApplication
        extends UnderwrapServer.UnderwrapApplication
    {
    }

    @Test
    public void buildAccessLogPathWithAbsolutePath()
    {
        File file = new AccessLogHandlerFactory(DummyApplication.class, null, Paths.get("/var/log/bigdam_dddb")).accessLogPath();
        assertThat(file.getAbsolutePath(), is("/var/log/bigdam_dddb"));
    }

    @Test
    public void buildAccessLogPathWithRelativePath()
    {
        File file = new AccessLogHandlerFactory(DummyApplication.class, null, Paths.get("log/access")).accessLogPath();
        assertThat(file.getAbsolutePath(), is(Paths.get(System.getProperty("user.dir")).resolve("log/access").toString()));
    }

    @Test
    public void buildAccessLogPathWithAbsolutePathWithBIGDAM_ROOT_PATH()
    {
        File file = new AccessLogHandlerFactory(DummyApplication.class, Paths.get("/mnt/srv/bigdam_dddb/"), Paths.get("/var/log/bigdam_dddb")).accessLogPath();
        assertThat(file.getAbsolutePath(), is("/var/log/bigdam_dddb"));
    }

    @Test
    public void buildAccessLogPathWithRelativePathWithBIGDAM_ROOT_PATH()
    {
        File file = new AccessLogHandlerFactory(DummyApplication.class, Paths.get("/mnt/srv/bigdam_dddb/"), Paths.get("log/access")).accessLogPath();
        assertThat(file.getAbsolutePath(), is("/mnt/srv/bigdam_dddb/log/access"));
    }
}
