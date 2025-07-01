package com.example.neptune;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.neptunedata.NeptunedataClient;
import software.amazon.awssdk.services.neptunedata.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import java.util.Properties;

/**
 * Demo application for connecting to Amazon Neptune using the Neptune Data API (REST)
 * This class performs the same operations as NeptuneBoltDemo but uses AWS SDK instead of Bolt driver
 */
public class NeptuneDataApiDemo {
    private static final Logger logger = LogManager.getLogger(NeptuneDataApiDemo.class);

    private final NeptunedataClient neptuneClient;
    private final String neptuneEndpoint;
    private final String awsRegion;

    public NeptuneDataApiDemo(String neptuneUri, String region) {
        // Parse the URI to extract endpoint
        URI uri;
        try {
            // If the URI doesn't start with https://, add it
            if (!neptuneUri.startsWith("https://") && !neptuneUri.startsWith("http://")) {
                neptuneUri = "https://" + neptuneUri;
            }
            uri = URI.create(neptuneUri);
            this.neptuneEndpoint = uri.getHost() + (uri.getPort() != -1 ? ":" + uri.getPort() : "");
            this.awsRegion = region;
        } catch (Exception e) {
            throw new java.lang.IllegalArgumentException("Invalid Neptune URI: " + neptuneUri, e);
        }
        
        // Create Neptune Data API client
        this.neptuneClient = NeptunedataClient.builder()
                .region(Region.of(region))
                .endpointOverride(uri)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        logger.info("Successfully created Neptune Data API client for endpoint: {}", neptuneEndpoint);
    }

    /**
     * Execute a simple query to test the connection
     */
    public void testConnection() {
        try {
            ExecuteOpenCypherQueryRequest request = ExecuteOpenCypherQueryRequest.builder()
                    .openCypherQuery("RETURN 'Hello Neptune!' as message")
                    .build();

            ExecuteOpenCypherQueryResponse response = neptuneClient.executeOpenCypherQuery(request);
            
            // Parse the response
            Document results = response.results();
            logger.info("Connection test successful. Response: {}", results.toString());
            
        } catch (Exception e) {
            logger.error("Connection test failed", e);
            throw new RuntimeException("Connection test failed", e);
        }
    }

    /**
     * Create sample nodes and relationships using OpenCypher
     */
    public void createSampleData() {
        try {
            // Create Person nodes
            executeQuery("CREATE (p:Person {name: 'Alice', age: 30})");
            executeQuery("CREATE (p:Person {name: 'Bob', age: 25})");
            executeQuery("CREATE (c:Company {name: 'TechCorp'})");

            // Create relationships
            executeQuery("MATCH (a:Person {name: 'Alice'}), (c:Company {name: 'TechCorp'}) " +
                    "CREATE (a)-[:WORKS_FOR]->(c)");
            executeQuery("MATCH (a:Person {name: 'Alice'}), (b:Person {name: 'Bob'}) " +
                    "CREATE (a)-[:KNOWS]->(b)");

            logger.info("Sample data created successfully using Neptune Data API");
        } catch (Exception e) {
            logger.error("Failed to create sample data", e);
            throw e;
        }
    }

    /**
     * Query sample data using OpenCypher
     */
    public void querySampleData() {
        try {
            // Query all persons
            logger.info("Querying persons in the database:");
            ExecuteOpenCypherQueryResponse response = executeQuery("MATCH (p:Person) RETURN p.name as name, p.age as age");
            
            Document results = response.results();
            logger.info("Persons query results: {}", results.toString());
            
            // Parse results if they contain a results array
            if (results.isMap() && results.asMap().containsKey("results")) {
                Document resultsArray = results.asMap().get("results");
                if (resultsArray.isList()) {
                    for (Document row : resultsArray.asList()) {
                        if (row.isMap()) {
                            Map<String, Document> rowMap = row.asMap();
                            String name = rowMap.containsKey("name") ? rowMap.get("name").asString() : "Unknown";
                            Integer age = rowMap.containsKey("age") ? rowMap.get("age").asNumber().intValue() : 0;
                            logger.info("- Name: {}, Age: {}", name, age);
                        }
                    }
                }
            }

            // Query relationships
            logger.info("Querying relationships:");
            response = executeQuery(
                    "MATCH (p1:Person)-[r]->(p2) " +
                    "RETURN p1.name as person1, type(r) as relationship, p2.name as person2"
            );
            
            results = response.results();
            logger.info("Relationships query results: {}", results.toString());
            
            // Parse relationship results
            if (results.isMap() && results.asMap().containsKey("results")) {
                Document resultsArray = results.asMap().get("results");
                if (resultsArray.isList()) {
                    for (Document row : resultsArray.asList()) {
                        if (row.isMap()) {
                            Map<String, Document> rowMap = row.asMap();
                            String person1 = rowMap.containsKey("person1") ? rowMap.get("person1").asString() : "Unknown";
                            String relationship = rowMap.containsKey("relationship") ? rowMap.get("relationship").asString() : "Unknown";
                            String person2 = rowMap.containsKey("person2") ? rowMap.get("person2").asString() : "Unknown";
                            logger.info("- {} {} {}", person1, relationship, person2);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Query execution failed", e);
            throw e;
        }
    }

    /**
     * Clean up sample data
     */
    public void cleanupSampleData() {
        try {
            executeQuery("MATCH (n) DETACH DELETE n");
            logger.info("Sample data cleaned up using Neptune Data API");
        } catch (Exception e) {
            logger.error("Failed to cleanup sample data", e);
            throw e;
        }
    }

    /**
     * Execute an OpenCypher query using Neptune Data API
     */
    private ExecuteOpenCypherQueryResponse executeQuery(String query) {
        try {
            ExecuteOpenCypherQueryRequest request = ExecuteOpenCypherQueryRequest.builder()
                    .openCypherQuery(query)
                    .build();

            ExecuteOpenCypherQueryResponse response = neptuneClient.executeOpenCypherQuery(request);
            logger.debug("Executed query: {}", query);
            
            return response;
        } catch (Exception e) {
            logger.error("Failed to execute query: {}", query, e);
            throw e;
        }
    }

    /**
     * Get Neptune cluster status
     */
    public void getClusterStatus() {
        try {
            GetEngineStatusRequest request = GetEngineStatusRequest.builder().build();
            GetEngineStatusResponse response = neptuneClient.getEngineStatus(request);
            
            logger.info("Neptune cluster status: {}", response.status());
            logger.info("Database engine: {}", response.dbEngineVersion());
            
        } catch (Exception e) {
            logger.error("Failed to get cluster status", e);
        }
    }

    /**
     * Load properties from application.properties file
     */
    private static Properties loadProperties() {
        Properties properties = new Properties();

        try (InputStream input = NeptuneDataApiDemo.class.getClassLoader()
                .getResourceAsStream("application.properties")) {

            if (input == null) {
                logger.warn("application.properties file not found in classpath");
                return properties;
            }

            properties.load(input);
            logger.info("Loaded properties from application.properties");

        } catch (IOException e) {
            logger.error("Error loading application.properties", e);
        }

        return properties;
    }

    /**
     * Get property value with fallback to environment variable and default value
     */
    private static String getConfigValue(Properties props, String propKey, String envKey, String defaultValue) {
        // Priority: Environment Variable > Properties File > Default Value
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }

        String propValue = props.getProperty(propKey);
        if (propValue != null && !propValue.isEmpty()) {
            return propValue;
        }

        return defaultValue;
    }

    /**
     * Close the Neptune client
     */
    public void close() {
        if (neptuneClient != null) {
            neptuneClient.close();
            logger.info("Neptune Data API client closed");
        }
    }

    public static void main(String[] args) {
        // Load properties from application.properties
        Properties properties = loadProperties();

        // Get Neptune connection parameters with fallback priority:
        // 1. Environment variables (highest priority)
        // 2. application.properties file
        // 3. Default values (lowest priority)
        String neptuneEndpoint = getConfigValue(properties, "neptune.endpoint", "NEPTUNE_ENDPOINT", null);
        String neptunePort = getConfigValue(properties, "neptune.port", "NEPTUNE_PORT", "8182");
        String awsRegion = getConfigValue(properties, "aws.region", "AWS_REGION", "us-east-1");

        // Validate required configuration
        if (neptuneEndpoint == null || neptuneEndpoint.trim().isEmpty() ||
                neptuneEndpoint.contains("your-neptune-cluster-endpoint")) {
            logger.error("Neptune endpoint not configured properly!");
            logger.error("Please set NEPTUNE_ENDPOINT environment variable or update application.properties");
            logger.error("Current value: {}", neptuneEndpoint);
            System.exit(1);
        }

        // Construct the full Neptune URI
        String neptuneUri = "https://" + neptuneEndpoint + ":" + neptunePort;

        logger.info("Connecting to Neptune Data API at: {}", neptuneEndpoint);
        logger.info("Neptune Port: {}", neptunePort);
        logger.info("AWS Region: {}", awsRegion);
        logger.info("Configuration source: {}",
                System.getenv("NEPTUNE_ENDPOINT") != null ? "Environment variables" : "application.properties");

        NeptuneDataApiDemo demo = null;
        try {
            // Create connection with full URI
            demo = new NeptuneDataApiDemo(neptuneUri, awsRegion);

            // Get cluster status
            demo.getClusterStatus();

            // Test connection
            demo.testConnection();

            // Create and query sample data
            demo.createSampleData();
            demo.querySampleData();

            // Cleanup
            demo.cleanupSampleData();

        } catch (Exception e) {
            logger.error("Application failed", e);
            System.exit(1);
        } finally {
            if (demo != null) {
                demo.close();
            }
        }

        logger.info("Neptune Data API Demo completed successfully");
    }
}
