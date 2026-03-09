# Using Lynxe (jmanus) as a Thin JAR in a New Project

This guide shows how to create a new Spring Boot project that depends on Lynxe as a **library** (thin JAR) so you get both Lynxe’s `/api/executor` endpoints and your own web (e.g. a console) in the **same process**.

---

## Prerequisites

- **Java 17**
- **Maven 3.6+**
- Lynxe built so the **main artifact** is the runnable fat JAR (`lynxe-<version>.jar`) and the library thin JAR is `lynxe-<version>-thin-jar.jar`

---

## Step 1: Install Lynxe into your local Maven repository

From the Lynxe (jmanus) project root:

```bash
cd /path/to/jmanus-new
mvn install -DskipTests
```

Optional: skip the Java format check if it fails:

```bash
mvn install -DskipTests -Dspring-javaformat.skip=true
```

This installs:

- **`lynxe-4.10.10.jar`** (fat, executable) → for running Lynxe standalone
- **`lynxe-4.10.10-thin-jar.jar`** (thin, library) → used as a dependency

Other projects depend on the **thin** JAR via `com.lynxe:lynxe:4.10.10` (Maven resolves the main artifact; to use the thin JAR as a dependency you may need to depend on the `thin-jar` classifier or your repo’s convention).

---

## Step 2: Create a new Maven project

Create a new directory for your app (e.g. next to `jmanus-new` or anywhere you like):

```bash
mkdir my-lynxe-app
cd my-lynxe-app
```

---

## Step 3: Add `pom.xml` with Lynxe as a dependency

Use the **same Spring Boot version** as Lynxe (e.g. 3.5.6) to avoid conflicts.

Create `pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>my-lynxe-app</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>
    <name>My Lynxe App</name>
    <description>Sample app that uses Lynxe as a thin JAR</description>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.6</version>
        <relativePath/>
    </parent>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Lynxe as a thin JAR (classifier required: main artifact is the fat JAR) -->
        <dependency>
            <groupId>com.lynxe</groupId>
            <artifactId>lynxe</artifactId>
            <version>4.10.10</version>
            <classifier>thin-jar</classifier>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

Important: **Spring Boot version** (3.5.6) and **Java** (17) should match Lynxe.

---

## Step 4: Create the main application class and scan Lynxe’s package

Lynxe’s controllers live under `com.alibaba.cloud.ai.lynxe`. Your main class must **component-scan** that package so those beans (including `LynxeController`) are registered in the same Spring context.

Create the main class, e.g. `src/main/java/com/example/app/MyLynxeApplication.java`:

```java
package com.example.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
    "com.example.app",              // your app
    "com.alibaba.cloud.ai.lynxe"     // Lynxe (LynxeController, services, etc.)
})
public class MyLynxeApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyLynxeApplication.class, args);
    }
}
```

If your code is in a different package, replace `com.example.app` with that package.

---

## Step 5: Add configuration (e.g. `application.yml`)

Lynxe brings its own `application.yml` and profile-specific config (e.g. `application-h2.yml`) from its JAR. Your app’s `application.yml` is loaded too; you only need to override what you care about (e.g. port, profile, datasource path).

**Minimal override** in your `src/main/resources/application.yml`:

```yaml
server:
  port: 8080

spring:
  profiles:
    active: h2

lynxe:
  cors:
    enabled: false
```

For full control or different environments, see the [Configuration guide](#configuration-guide) below.

---

## Configuration guide

Lynxe’s default config lives in the Lynxe repo:

- **Base:** `src/main/resources/application.yml`
- **H2 profile:** `src/main/resources/application-h2.yml`

When you use Lynxe as a thin JAR, these files are on the classpath. Your app’s `application.yml` (and `application-<profile>.yml`) are merged; your values override Lynxe’s. Below is what Lynxe defines and what you may want to set or override in your project.

### Server and application name

| Property | Lynxe default | In your app |
|----------|----------------|-------------|
| `server.port` | `18080` | Override if you want another port (e.g. `8080`). |
| `spring.application.name` | `spring-ai-alibaba-openlynxe` | Optional; set your own app name. |

### Profile and datasource (H2)

With `spring.profiles.active: h2`, Lynxe’s `application-h2.yml` applies. It sets:

| Property | Lynxe default | In your app |
|----------|----------------|-------------|
| `spring.datasource.url` | `jdbc:h2:file:./h2-data/openlynxe_db_new;MODE=MYSQL;DATABASE_TO_LOWER=TRUE` | Override to change DB path (e.g. a path under your app’s directory). |
| `spring.datasource.username` | `sa` | Override if you use another user. |
| `spring.datasource.password` | (set in Lynxe) | Override for your environment (do not commit secrets). |
| `spring.h2.console.enabled` | `true` | Set `false` if you do not want the H2 web console. |
| `spring.h2.console.path` | `/h2-console` | Change if you want a different console path. |

Example override in your app (e.g. different DB path and port):

```yaml
server:
  port: 8080

spring:
  profiles:
    active: h2
  datasource:
    url: jdbc:h2:file:./data/myapp_db;MODE=MYSQL;DATABASE_TO_LOWER=TRUE
    username: sa
    password: my-secure-password
  h2:
    console:
      enabled: true
      path: /h2-console
```

### Multipart (file upload)

Lynxe’s base config sets large limits for plan execution and chat uploads:

| Property | Lynxe default |
|----------|----------------|
| `spring.servlet.multipart.max-file-size` | `1073741824` (1GB) |
| `spring.servlet.multipart.max-request-size` | `6442450944` |
| `spring.servlet.multipart.enabled` | `true` |

Override in your app only if you need different limits.

### Root redirect and init path (when embedding Lynxe)

To avoid **ambiguous mapping** on `GET /`, either let your app own `/` or let Lynxe own it:

| Property | Lynxe default | In your app |
|----------|----------------|-------------|
| `lynxe.web.root-redirect-enabled` | `true` | Set to **`false`** when embedding Lynxe so your app can map `"/"` (e.g. your own home or console). When `true`, Lynxe maps `GET /` to the init path. |
| `lynxe.web.init-path` | `/lynxe` | Path Lynxe redirects `/` to (and uses in startup log). UI is served at `/lynxe`. Override if your UI is at a different path. |

**Example: use Lynxe as a library and own `/` in your app**

```yaml
lynxe:
  web:
    root-redirect-enabled: false
```

No need to set `init-path` unless you want a different redirect target when root redirect is enabled.

### Lynxe-specific (`lynxe.*`)

Lynxe uses these under `lynxe`:

| Property | Lynxe default | Notes |
|----------|----------------|-------|
| `lynxe.web.root-redirect-enabled` | `true` | See [Root redirect and init path](#root-redirect-and-init-path-when-embedding-lynxe) above. |
| `lynxe.web.init-path` | `/lynxe` | Redirect target for `/` and URL in startup log. UI is served at `/lynxe`. |
| `lynxe.file-upload.max-file-size` | `1073741824` | Max size per file (bytes). |
| `lynxe.file-upload.max-files-per-upload` | `10` | Max files per request. |
| `lynxe.file-upload.upload-directory` | `uploaded_files` | Directory name for uploads. |
| `lynxe.file-upload.validation-strategy` | `code` | `code` or `config`. |
| `lynxe.proxy.enabled` | `false` | Set `true` to use HTTP/HTTPS proxy. |
| `lynxe.proxy.httpProxyHost` / `httpProxyPort` | (e.g. 127.0.0.1, 6789) | Set when proxy is enabled. |
| `lynxe.proxy.httpsProxyHost` / `httpsProxyPort` | (optional) | Same for HTTPS. |

Override in your app when you need different upload limits, paths, or proxy settings.

### Logging

Lynxe does **not** ship `logback-spring.xml` or `logback.xml` in its JAR, so your application’s logging configuration is never overridden. When you run Lynxe as the main app (fat JAR), it explicitly loads `logback-lynxe-standalone.xml`; when Lynxe is a dependency, your app’s `logback-spring.xml` or `application.yml` controls logging.

Lynxe’s default config (used in standalone) sets:

- `logging.file.name`: `./logs/info.log`
- `logging.level.root`: `INFO`
- Several `com.alibaba.cloud.ai.*` loggers to `DEBUG` or `INFO`
- Optional appenders: `LLM_REQUEST_LOGGER`, `STREAMING_PROGRESS_LOGGER`

To reuse Lynxe’s logger names or file appenders (e.g. LLM request logs), copy the relevant parts from the `logback-lynxe-standalone.xml` file in the Lynxe JAR (or repo) into your own `logback-spring.xml`.

Override in your app to point logs elsewhere or change levels, e.g.:

```yaml
logging:
  file:
    name: ./logs/my-app.log
  level:
    root: INFO
    com.alibaba.cloud.ai.lynxe: INFO
```

### Other Spring / JPA settings

Lynxe’s base config also sets:

- `spring.main.allow-circular-references: true`
- `spring.main.lazy-initialization: false`
- `spring.jpa.open-in-view: false`
- Hikari pool and JPA/Hibernate options

Usually you do not need to override these unless you hit compatibility issues. If you use another database (e.g. MySQL), add a profile (e.g. `application-mysql.yml`) and set `spring.profiles.active` and the corresponding `spring.datasource.*` and `spring.jpa.database-platform` in your app.

---

## Step 6: (Optional) Add your own web – e.g. a simple console API

Example controller under your package, e.g. `src/main/java/com/example/app/ConsoleController.java`:

```java
package com.example.app;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/console")
public class ConsoleController {

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
            "app", "My Lynxe App",
            "lynxe", "embedded",
            "executorApi", "http://localhost:8080/api/executor"
        );
    }
}
```

This runs in the **same** process and port as Lynxe’s `/api/executor`.

---

## Step 7: Run the new project

From your new project root:

```bash
cd my-lynxe-app
mvn spring-boot:run
```

Or build and run the JAR:

```bash
mvn package -DskipTests
java -jar target/my-lynxe-app-1.0.0-SNAPSHOT.jar
```

Then:

- Your app: e.g. `http://localhost:8080/console/info`
- Lynxe executor API: `http://localhost:8080/api/executor/...` (e.g. `POST /api/executor/executeByToolNameSync`, `GET /api/executor/details/{planId}`, etc.)

---

## Summary checklist

| Step | Action                                                                    |
| ---- | ------------------------------------------------------------------------- |
| 1    | In jmanus: `mvn install -DskipTests` (installs thin JAR to `~/.m2`)       |
| 2    | Create new project directory and `pom.xml` with `com.lynxe:lynxe:4.10.10` |
| 3    | Use same Spring Boot (3.5.6) and Java 17                                  |
| 4    | Main class with `@ComponentScan` including `com.alibaba.cloud.ai.lynxe`   |
| 5    | Add `application.yml` (port, profile, datasource if needed); see [Configuration guide](#configuration-guide) |
| 6    | (Optional) Add your own controllers under your package                    |
| 7    | Run with `mvn spring-boot:run` or `java -jar target/<your-app>.jar`       |

---

## Using the sample project in this repo

This repository includes a minimal sample app that already does the above:

- **Location:** `lynxe-consumer/`
- **Run:**
  ```bash
  # From repo root: install Lynxe first, then run the consumer
  mvn install -DskipTests -Dspring-javaformat.skip=true
  cd lynxe-consumer
  mvn spring-boot:run
  ```
- **Endpoints:**
  - Sample console: `http://localhost:8080/console/info`
  - Lynxe API: `http://localhost:8080/api/executor/...`

See `lynxe-consumer/README.md` for short usage notes.
