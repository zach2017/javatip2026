# Feature Flags in Spring Boot
## A Complete Step-by-Step Tutorial
### Using Java 21 & Spring Boot 3.4 with Actuator

---

## Table of Contents
1. [Understanding Feature Flags](#chapter-1-understanding-feature-flags)
2. [Java 21 Features Explained](#chapter-2-java-21-features-explained)
3. [Project Structure Overview](#chapter-3-project-structure-overview)
4. [Step-by-Step Setup Guide](#chapter-4-step-by-step-setup-guide)
5. [Running and Testing](#chapter-5-running-and-testing)
6. [Using Feature Flags in Your Code](#chapter-6-using-feature-flags-in-your-code)
7. [Conclusion & Next Steps](#conclusion--next-steps)

---

# Chapter 1: Understanding Feature Flags

## What is a Feature Flag?

A **feature flag** (also called a feature toggle or feature switch) is a simple on/off switch in your code that controls whether a feature is visible or active for users. Think of it like a light switch in your house - you can turn features on or off without changing the actual wiring (code).

### A Simple Analogy

Imagine you own a restaurant. You have a new dessert menu, but you're not sure if customers will like it. Instead of permanently adding it to your menu, you could:

1. Print special menus with the new desserts
2. Only give these menus to certain tables
3. If customers love it, give the menu to everyone
4. If they don't, simply stop giving out those menus

Feature flags work the same way in software. You deploy code with new features, but those features are "hidden" behind a flag. You can then turn them on for specific users, test them, and roll them out gradually.

> 💡 **Key Insight**: Feature flags separate the deployment of code from the release of features. You can deploy anytime, but release when ready!

## Why Use Feature Flags?

| Use Case | Description |
|----------|-------------|
| **Gradual Rollout** | Release to 10% of users first, then gradually increase to 100% |
| **A/B Testing** | Show different versions to different users and compare results |
| **Kill Switch** | Instantly disable a problematic feature without redeploying |
| **Beta Testing** | Enable experimental features only for beta testers |
| **Trunk-Based Dev** | Merge incomplete features to main branch safely (hidden by flag) |

---

# Chapter 2: Java 21 Features Explained

Our feature flag application uses several modern Java 21 features. Let's understand each one.

## 1. Records

**What is it?** A record is a special type of class designed to hold data. Java automatically creates the constructor, getters, equals(), hashCode(), and toString() methods for you.

**Why use it?** Less boilerplate code! What used to take 50+ lines now takes just 1 line.

### Traditional Java Class (Old Way):
```java
public class FeatureFlag {
    private final String name;
    private final boolean enabled;
    
    public FeatureFlag(String name, boolean enabled) {
        this.name = name;
        this.enabled = enabled;
    }
    
    public String getName() { return name; }
    public boolean isEnabled() { return enabled; }
    
    // Plus equals(), hashCode(), toString()...
    // Total: 30-50 lines!
}
```

### Java 21 Record (New Way):
```java
public record FeatureFlag(String name, boolean enabled) { }
// That's it! Just 1 line!
```

## 2. Pattern Matching in Switch

**What is it?** Pattern matching lets you check the type and extract data from objects in one step.

### Old Way:
```java
String result;
if (selector.equals("enabled")) {
    result = getEnabledFlags();
} else if (selector.equals("disabled")) {
    result = getDisabledFlags();
} else {
    result = findFlag(selector);
}
```

### Java 21 Way:
```java
return switch (selector.toLowerCase()) {
    case "enabled" -> getEnabledFlags();
    case "disabled" -> getDisabledFlags();
    case "summary" -> getSummary();
    default -> findFlag(selector);
};
```

## 3. Enhanced Switch Expressions

Switch expressions can return values directly using the arrow syntax (->). No more break statements!

```java
// Pattern matching with guards (when clause)
public String getStatusEmoji() {
    return switch (this) {
        case FeatureFlag f when f.enabled() -> "✅";
        case FeatureFlag f when !f.enabled() -> "❌";
        default -> "❓";
    };
}
```

## 4. Text Blocks and String Templates

```java
// Clean string formatting
"Flag '%s' not found".formatted(flagName)

// Instead of:
String.format("Flag '%s' not found", flagName)
```

---

# Chapter 3: Project Structure Overview

```
feature-flag-demo/
├── pom.xml                          # Maven dependencies
├── src/main/
│   ├── java/com/example/featureflag/
│   │   ├── FeatureFlagDemoApplication.java   # Main entry point
│   │   ├── config/
│   │   │   ├── FeatureFlagConfig.java        # Spring configuration
│   │   │   └── FeatureFlagProperties.java    # Type-safe properties
│   │   ├── model/
│   │   │   └── FeatureFlag.java              # Data record
│   │   ├── service/
│   │   │   └── FeatureFlagService.java       # Business logic
│   │   ├── actuator/
│   │   │   └── FeatureFlagEndpoint.java      # Custom actuator
│   │   └── controller/
│   │       ├── FeatureFlagController.java    # REST API
│   │       └── DemoController.java           # Demo endpoints
│   └── resources/
│       └── application.yml                    # Configuration
```

## How Components Work Together

| Component | Purpose |
|-----------|---------|
| **application.yml** | Defines all feature flags with name, description, enabled status, and group |
| **FeatureFlagProperties** | Binds YAML config to Java objects (type-safe configuration) |
| **FeatureFlagService** | Business logic - check flags, filter, group, and summarize |
| **FeatureFlagEndpoint** | Custom Actuator endpoint at /actuator/featureflags |
| **Controllers** | REST API endpoints and demo showing real-world usage |

## Data Flow

1. **Request arrives** at /actuator/featureflags
2. **FeatureFlagEndpoint** receives the request
3. **FeatureFlagService** is called to get flag data
4. **Service reads from FeatureFlagProperties** (loaded from YAML)
5. **Data is returned** as JSON to the client

---

# Chapter 4: Step-by-Step Setup Guide

## Prerequisites

- **Java 21** - Download from adoptium.net or use SDKMAN
- **Maven 3.9+** - For building the project
- **IDE** - IntelliJ IDEA, VS Code, or Eclipse

## Step 1: Create Project Structure

```bash
mkdir -p feature-flag-demo/src/main/java/com/example/featureflag/{config,controller,service,actuator,model}
mkdir -p feature-flag-demo/src/main/resources
cd feature-flag-demo
```

## Step 2: Create pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.1</version>
    </parent>
    
    <groupId>com.example</groupId>
    <artifactId>feature-flag-demo</artifactId>
    <version>1.0.0</version>
    
    <properties>
        <java.version>21</java.version>
    </properties>
    
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
    </dependencies>
</project>
```

## Step 3: Create the FeatureFlag Record

**File:** `src/main/java/com/example/featureflag/model/FeatureFlag.java`

```java
package com.example.featureflag.model;

public record FeatureFlag(
        String name,
        String description,
        boolean enabled,
        String group
) {
    public FeatureFlag {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                "Feature flag name cannot be null or blank"
            );
        }
    }

    public String getStatusEmoji() {
        return switch (this) {
            case FeatureFlag f when f.enabled() -> "✅";
            case FeatureFlag f when !f.enabled() -> "❌";
            default -> "❓";
        };
    }
}
```

## Step 4: Create Configuration Properties

**File:** `src/main/java/com/example/featureflag/config/FeatureFlagProperties.java`

```java
package com.example.featureflag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "feature")
public class FeatureFlagProperties {

    private Map<String, FlagConfig> flags = new HashMap<>();

    public Map<String, FlagConfig> getFlags() { return flags; }
    public void setFlags(Map<String, FlagConfig> flags) { this.flags = flags; }

    public static class FlagConfig {
        private boolean enabled = false;
        private String description = "";
        private String group = "default";
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getDescription() { return description; }
        public void setDescription(String d) { this.description = d; }
        public String getGroup() { return group; }
        public void setGroup(String group) { this.group = group; }
    }
}
```

## Step 5: Create the Service Layer

**File:** `src/main/java/com/example/featureflag/service/FeatureFlagService.java`

```java
package com.example.featureflag.service;

import com.example.featureflag.config.FeatureFlagProperties;
import com.example.featureflag.model.FeatureFlag;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class FeatureFlagService {

    private final FeatureFlagProperties properties;

    public FeatureFlagService(FeatureFlagProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled(String featureName) {
        return Optional.ofNullable(properties.getFlags().get(featureName))
                .map(FeatureFlagProperties.FlagConfig::isEnabled)
                .orElse(false);
    }

    public List<FeatureFlag> getAllFlags() {
        return properties.getFlags().entrySet().stream()
                .map(e -> new FeatureFlag(
                    e.getKey(),
                    e.getValue().getDescription(),
                    e.getValue().isEnabled(),
                    e.getValue().getGroup()
                ))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();
    }

    public List<FeatureFlag> getEnabledFlags() {
        return getAllFlags().stream()
                .filter(FeatureFlag::enabled)
                .toList();
    }

    public FlagSummary getSummary() {
        var all = getAllFlags();
        long enabled = all.stream().filter(FeatureFlag::enabled).count();
        return new FlagSummary(all.size(), enabled, all.size() - enabled);
    }

    public record FlagSummary(long total, long enabled, long disabled) {}
}
```

## Step 6: Create the Actuator Endpoint

**File:** `src/main/java/com/example/featureflag/actuator/FeatureFlagEndpoint.java`

```java
package com.example.featureflag.actuator;

import com.example.featureflag.service.FeatureFlagService;
import org.springframework.boot.actuate.endpoint.annotation.*;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

@Component
@Endpoint(id = "featureflags")
public class FeatureFlagEndpoint {

    private final FeatureFlagService featureFlagService;

    public FeatureFlagEndpoint(FeatureFlagService service) {
        this.featureFlagService = service;
    }

    @ReadOperation
    public Map<String, Object> featureFlags() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", Instant.now().toString());
        result.put("summary", featureFlagService.getSummary());
        result.put("flags", featureFlagService.getAllFlags());
        result.put("enabled", featureFlagService.getEnabledFlags()
            .stream().map(f -> f.name()).toList());
        return result;
    }

    @ReadOperation
    public Object featureFlag(@Selector String selector) {
        return switch (selector.toLowerCase()) {
            case "enabled" -> featureFlagService.getEnabledFlags();
            case "summary" -> featureFlagService.getSummary();
            default -> findFlag(selector);
        };
    }
    
    private Object findFlag(String name) {
        return featureFlagService.getAllFlags().stream()
            .filter(f -> f.name().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }
}
```

## Step 7: Configure application.yml

**File:** `src/main/resources/application.yml`

```yaml
server:
  port: 8080

feature:
  flags:
    dark-mode:
      enabled: true
      description: "Enable dark mode theme"
      group: "ui"
    
    new-greeting-message:
      enabled: true
      description: "Show new welcome message"
      group: "ui"
    
    analytics-v2:
      enabled: true
      description: "Enable Analytics V2"
      group: "analytics"
    
    beta-features:
      enabled: false
      description: "Enable beta features"
      group: "beta"
    
    email-notifications:
      enabled: true
      description: "Enable email notifications"
      group: "notifications"

management:
  endpoints:
    web:
      exposure:
        include: health,info,featureflags
  endpoint:
    featureflags:
      enabled: true
```

## Step 8: Create Main Application Class

**File:** `src/main/java/com/example/featureflag/FeatureFlagDemoApplication.java`

```java
package com.example.featureflag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.example.featureflag.config.FeatureFlagProperties;

@SpringBootApplication
@EnableConfigurationProperties(FeatureFlagProperties.class)
public class FeatureFlagDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(FeatureFlagDemoApplication.class, args);
    }
}
```

---

# Chapter 5: Running and Testing

## Building and Running

```bash
# Build the project
mvn clean package

# Run the application
mvn spring-boot:run

# Or run the JAR directly
java -jar target/feature-flag-demo-1.0.0.jar
```

## Testing the Actuator Endpoint

### 1. Get all feature flags:
```bash
curl http://localhost:8080/actuator/featureflags | jq
```

### Expected Response:
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "summary": {
    "total": 5,
    "enabled": 4,
    "disabled": 1
  },
  "flags": [
    {
      "name": "analytics-v2",
      "description": "Enable Analytics V2",
      "enabled": true,
      "group": "analytics",
      "status": "✅"
    }
  ],
  "enabled": ["analytics-v2", "dark-mode", "email-notifications"]
}
```

### 2. Get only enabled flags:
```bash
curl http://localhost:8080/actuator/featureflags/enabled | jq
```

### 3. Get summary statistics:
```bash
curl http://localhost:8080/actuator/featureflags/summary | jq
```

### 4. Check a specific flag:
```bash
curl http://localhost:8080/actuator/featureflags/dark-mode | jq
```

---

# Chapter 6: Using Feature Flags in Your Code

## Basic Usage Pattern

```java
@Service
public class GreetingService {

    private final FeatureFlagService featureFlagService;

    public GreetingService(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    public String getGreeting() {
        if (featureFlagService.isEnabled("new-greeting-message")) {
            return "🎉 Welcome to our awesome new experience!";
        } else {
            return "Hello, welcome to our application.";
        }
    }
}
```

## Real-World Example: Dashboard Controller

```java
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final FeatureFlagService flags;

    public DashboardController(FeatureFlagService flags) {
        this.flags = flags;
    }

    @GetMapping
    public Map<String, Object> getDashboard() {
        Map<String, Object> dashboard = new HashMap<>();
        
        dashboard.put("basicStats", getBasicStats());
        
        if (flags.isEnabled("dark-mode")) {
            dashboard.put("theme", "dark");
        } else {
            dashboard.put("theme", "light");
        }
        
        if (flags.isEnabled("analytics-v2")) {
            dashboard.put("analytics", Map.of(
                "version", "v2",
                "aiInsights", true
            ));
        }
        
        if (flags.isEnabled("beta-features")) {
            dashboard.put("betaFeatures", List.of("AI Assistant"));
        }
        
        return dashboard;
    }
}
```

## Best Practices

| Practice | Why It Matters |
|----------|----------------|
| Use descriptive names | "analytics-v2" is better than "flag1" |
| Group related flags | Organize by feature area (ui, analytics, beta) |
| Add descriptions | Document what each flag does |
| Default to disabled | New features should be off until explicitly enabled |
| Clean up old flags | Remove flags after feature is fully released |

---

# Conclusion & Next Steps

## What You've Learned

- ✅ What feature flags are and why they're valuable
- ✅ Modern Java 21 features: records, pattern matching, switch expressions
- ✅ How to create custom Spring Boot Actuator endpoints
- ✅ Type-safe configuration with @ConfigurationProperties
- ✅ Real-world patterns for using feature flags in code

## Possible Enhancements

1. **Dynamic updates** - Change flags without restarting (using @RefreshScope)
2. **Database storage** - Store flags in a database for persistence
3. **User targeting** - Enable flags for specific users or groups
4. **Percentage rollouts** - Enable for X% of users
5. **Admin UI** - Build a dashboard to toggle flags
6. **Audit logging** - Track who changed what and when

## Quick Reference - Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /actuator/featureflags` | All flags with summary |
| `GET /actuator/featureflags/enabled` | Only enabled flags |
| `GET /actuator/featureflags/summary` | Statistics summary |
| `GET /actuator/featureflags/{name}` | Specific flag details |

---

> 🚀 **Happy Coding!** You now have a solid foundation for implementing feature flags. Start small, experiment, and gradually adopt more advanced patterns as your needs grow.
