# Underwrap

A very thin wrapper of Undertow and Resteasy

## Usage

### ~/.m2/settings.xml

```
<settings>
    <servers>
        <server>
            <id>treasuredata-releases</id>
            <username>${env.TD_ARTIFACTORY_USERNAME}</username>
            <password>${env.TD_ARTIFACTORY_PASSWORD}</password>
        </server>
        <server>
            <id>treasuredata-snapshots</id>
            <username>${env.TD_ARTIFACTORY_USERNAME}</username>
            <password>${env.TD_ARTIFACTORY_PASSWORD}</password>
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
        <version>0.1.0-local-SNAPSHOT</version>
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
    compile 'com.treasuredata:underwrap:0.1.0-local-SNAPSHOT'
}
```

## Example

