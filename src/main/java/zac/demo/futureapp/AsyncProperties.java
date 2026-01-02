package zac.demo.futureapp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for async executor.
 * Reads from application.yml under 'async.executor' prefix.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "async.executor")
public class AsyncProperties {

    private int corePoolSize = 4;
    private int maxPoolSize = 8;
    private int queueCapacity = 100;
    private String threadNamePrefix = "Async-";
}
