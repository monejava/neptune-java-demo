package com.example.neptune;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.exceptions.Neo4jException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Demo application for connecting to Amazon Neptune using OpenCypher via Bolt protocol
 */
public class NeptuneBoltDemo {
    private static final Logger logger = LogManager.getLogger(NeptuneBoltDemo.class);

    private final Driver driver;

    public NeptuneBoltDemo(String uri) {
        // Create driver instance
        driver = GraphDatabase.driver(uri, AuthTokens.none(),
                Config.builder().withEncryption()
                        .withTrustStrategy(Config.TrustStrategy.trustSystemCertificates())
                        .build());

        logger.info("Successfully created Bolt driver for URI: {}", uri);
    }

    /**
     * Execute a simple query to test the connection
     */
    public void testConnection() {
        try (Session session = driver.session()) {
            // Simple query to test connectivity
            String query = "RETURN 'Hello from Neptune!' as message";
            
            logger.info("Executing test query: {}", query);
            Result result = session.run(query);
            
            if (result.hasNext()) {
                Record record = result.next();
                String message = record.get("message").asString();
                logger.info("Query result: {}", message);
                System.out.println("✓ Connection test successful: " + message);
            } else {
                logger.warn("Query returned no results");
                System.out.println("⚠ Query executed but returned no results");
            }
            
        } catch (Neo4jException e) {
            logger.error("Bolt driver error during test query: {}", e.getMessage(), e);
            System.err.println("✗ Connection test failed: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during test query: {}", e.getMessage(), e);
            System.err.println("✗ Unexpected error: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Execute sample OpenCypher queries to demonstrate Neptune functionality
     */
    public void runSampleQueries() {
        try (Session session = driver.session()) {
            logger.info("Starting sample OpenCypher queries...");
            System.out.println("\n=== Running Sample OpenCypher Queries ===");

            // Query 1: Create some sample nodes
            String createQuery = """
                CREATE (p1:Person {name: 'Alice', age: 30})
                CREATE (p2:Person {name: 'Bob', age: 25})
                CREATE (c:Company {name: 'TechCorp'})
                CREATE (p1)-[:WORKS_FOR]->(c)
                CREATE (p2)-[:WORKS_FOR]->(c)
                RETURN p1.name as person1, p2.name as person2, c.name as company
                """;

            logger.info("Executing create query...");
            System.out.println("\n1. Creating sample data...");
            Result createResult = session.run(createQuery);
            
            if (createResult.hasNext()) {
                Record record = createResult.next();
                System.out.println("   Created: " + record.get("person1").asString() + 
                                 " and " + record.get("person2").asString() + 
                                 " working for " + record.get("company").asString());
            }

            // Query 2: Find all persons
            String findQuery = "MATCH (p:Person) RETURN p.name as name, p.age as age ORDER BY p.name";
            
            logger.info("Executing find query: {}", findQuery);
            System.out.println("\n2. Finding all persons...");
            Result findResult = session.run(findQuery);
            
            while (findResult.hasNext()) {
                Record record = findResult.next();
                System.out.println("   Person: " + record.get("name").asString() + 
                                 " (age: " + record.get("age").asInt() + ")");
            }

            // Query 3: Find relationships
            String relationQuery = """
                MATCH (p:Person)-[r:WORKS_FOR]->(c:Company)
                RETURN p.name as person, type(r) as relationship, c.name as company
                """;

            logger.info("Executing relationship query...");
            System.out.println("\n3. Finding relationships...");
            Result relationResult = session.run(relationQuery);
            
            while (relationResult.hasNext()) {
                Record record = relationResult.next();
                System.out.println("   " + record.get("person").asString() + 
                                 " " + record.get("relationship").asString() + 
                                 " " + record.get("company").asString());
            }

            // Query 4: Cleanup - remove the test data
            String cleanupQuery = """
                MATCH (n)
                WHERE n:Person OR n:Company
                DETACH DELETE n
                """;

            logger.info("Executing cleanup query...");
            System.out.println("\n4. Cleaning up test data...");
            session.run(cleanupQuery);
            System.out.println("   Test data cleaned up successfully");

            logger.info("Sample queries completed successfully");
            System.out.println("\n✓ All sample queries executed successfully!");

        } catch (Neo4jException e) {
            logger.error("Bolt driver error during sample queries: {}", e.getMessage(), e);
            System.err.println("✗ Sample queries failed: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during sample queries: {}", e.getMessage(), e);
            System.err.println("✗ Unexpected error: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Close the driver connection
     */
    public void close() {
        if (driver != null) {
            driver.close();
            logger.info("Bolt driver closed");
        }
    }

    /**
     * Load properties from application.properties file
     */
    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream input = NeptuneBoltDemo.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            
            if (input == null) {
                logger.warn("application.properties file not found in classpath");
                return properties;
            }
            
            properties.load(input);
            logger.info("Loaded properties from application.properties");
            
        } catch (IOException e) {
            logger.error("Error loading application.properties: {}", e.getMessage(), e);
        }
        
        return properties;
    }

    /**
     * Get configuration value with fallback priority:
     * 1. Environment variable (highest priority)
     * 2. Properties file
     * 3. Default value (lowest priority)
     */
    private static String getConfigValue(Properties properties, String propertyKey, 
                                       String envKey, String defaultValue) {
        // Check environment variable first
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.trim().isEmpty()) {
            return envValue.trim();
        }
        
        // Check properties file
        String propValue = properties.getProperty(propertyKey);
        if (propValue != null && !propValue.trim().isEmpty()) {
            return propValue.trim();
        }
        
        // Return default value
        return defaultValue;
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

        // Validate required configuration
        if (neptuneEndpoint == null || neptuneEndpoint.trim().isEmpty() ||
                neptuneEndpoint.contains("your-neptune-cluster-endpoint")) {
            logger.error("Neptune endpoint not configured properly!");
            logger.error("Please set NEPTUNE_ENDPOINT environment variable or update application.properties");
            logger.error("Current value: {}", neptuneEndpoint);
            System.exit(1);
        }

        // Construct Bolt URI
        String boltUri = "bolt://" + neptuneEndpoint + ":" + neptunePort;

        logger.info("Connecting to Neptune at: {}", boltUri);
        logger.info("Configuration source: {}",
                System.getenv("NEPTUNE_ENDPOINT") != null ? "Environment variables" : "application.properties");

        NeptuneBoltDemo demo = null;
        try {
            // Create connection
            demo = new NeptuneBoltDemo(boltUri);

            // Test connection
            demo.testConnection();

            // Run sample queries
            demo.runSampleQueries();

            logger.info("Demo completed successfully");
            System.out.println("\n🎉 Neptune Bolt Demo completed successfully!");

        } catch (Exception e) {
            logger.error("Demo failed: {}", e.getMessage(), e);
            System.err.println("\n💥 Demo failed: " + e.getMessage());
            System.exit(1);
        } finally {
            if (demo != null) {
                demo.close();
            }
        }
    }
}
