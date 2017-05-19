# Underwrap

A very thin wrapper of Undertow and Resteasy

## Usage

### ~/.m2/settings.xml

```
<settings>
    <servers>
        <server>
            <id>treasuredata-releases</id>
            <username>(Set your TD_ARTIFACTORY_USERNAME)</username>
            <password>(Set your TD_ARTIFACTORY_PASSWORD)</password>
        </server>
        <server>
            <id>treasuredata-snapshots</id>
            <username>(Set your TD_ARTIFACTORY_USERNAME)</username>
            <password>(Set your TD_ARTIFACTORY_PASSWORD)</password>
        </server>
    </servers>
</settings>
```

### Maven

```
<repositories>
        <id>treasuredata-releases</id>
        <name>treasuredata-releases</name>
        <url>https://treasuredata.artifactoryonline.com/treasuredata/libs-release</url>
    </repository>
    <repository>
        <id>treasuredata-snapshots</id>
        <name>treasuredata-snapshots</name>
        <url>https://treasuredata.artifactoryonline.com/treasuredata/libs-snapshot</url>
    </repository>
</repositories>
    
<dependencies>
    <dependency>
        <groupId>com.treasuredata</groupId>
        <artifactId>underwrap</artifactId>
        <version>0.1.1-SNAPSHOT</version>
    </dependency>
</dependencies>
```

### Gradle

```
repositories {
    maven {
        url "https://treasuredata.artifactoryonline.com/treasuredata/libs-release"
        credentials {
            username = "${System.env.TD_ARTIFACTORY_USERNAME}"
            password = "${System.env.TD_ARTIFACTORY_PASSWORD}"
        }
    }
    maven {
        url "https://treasuredata.artifactoryonline.com/treasuredata/libs-snapshot"
        credentials {
            username = "${System.env.TD_ARTIFACTORY_USERNAME}"
            password = "${System.env.TD_ARTIFACTORY_PASSWORD}"
        }
    }
}

dependencies {
    compile 'com.treasuredata:underwrap:0.1.1-SNAPSHOT'
}
```

## Example

```
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class UnderwrapExample
{
    public static void main(String[] args)
    {
        UnderwrapServer server = new UnderwrapServer(MyApplication.class);
        Map<Class, Object> contextMqp =  new HashMap<>();
        contextMqp.put(String.class, "This is UnderwrapExample");
        server.start(contextMqp, null,
                serverBuild -> {
                    serverBuild.addHttpListener(8080, "0.0.0.0");
                }
        );
    }

    public static class MyApplication
        extends UnderwrapServer.UnderwrapApplication
    {
        @Override
        protected void registerResources(Set<Class<?>> classes)
        {
            classes.add(Resources.class);
        }
    }

    @Path("/")
    public static class Resources
    {
        private final String comment;

        public Resources(@Context String comment)
        {
            this.comment = comment;
        }

        @GET
        @Path("/hello")
        public String hello()
        {
            return "Hello! " + comment;
        }
    }
}
```