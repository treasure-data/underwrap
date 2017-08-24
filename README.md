# Underwrap

A very thin wrapper of Undertow and Resteasy

## Usage

### Maven

#### pom.xml

```xml
<dependencies>
    <dependency>
        <groupId>com.treasuredata</groupId>
        <artifactId>underwrap</artifactId>
        <version>0.1.4</version>
    </dependency>
</dependencies>
```

### Gradle

#### build.gradle

```groovy
dependencies {
    compile 'com.treasuredata:underwrap:0.1.4'
}
```

## Example

```java
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
