package com.sia.gen;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Slf4j
@Testcontainers
public class LocalStackIT {

    private static Network network = Network.newNetwork();

    @Container
    public static LocalStackContainer localStackContainer = new LocalStackContainer(
            // DockerImageName.parse("localstack/localstack:3.5.0"))
            DockerImageName.parse("localstack/localstack:s3-latest"))
            // .withNetwork(network)
            // .withNetworkAliases("localstack")
            // .withExposedPorts(4566)
            .withEnv("DEBUG", "1")
            .withEnv("LS_LOG", "trace")
            // .withEnv("SKIP_SSL_CERT_DOWNLOAD", "1")
            .withEnv("SKIP_SSL_CERT_DOWNLOAD", "true")
            .withEnv("AWS_DEFAULT_REGION", Region.US_EAST_2.toString())
            .withServices(LocalStackContainer.Service.S3)
            // .withEnv("OUTBOUND_HTTP_PROXY", "http://{proxy}:{port}")
            // .withEnv("OUTBOUND_HTTPS_PROXY", "http://{proxy}:{port}")
            .waitingFor(Wait.forLogMessage(".*Ready.*", 1));

    private static S3Client s3Client;
    // private static S3AsyncClient s3Client;

    @BeforeAll
    public static void setUpLocalStack() throws URISyntaxException {

        log.info("Setting up LocalStack container!!!");
        s3Client = S3Client.builder()
                .endpointOverride(localStackContainer.getEndpoint())
                // .endpointOverride(new URI("http://localhost:4566"))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(localStackContainer.getAccessKey(),
                                        localStackContainer.getSecretKey())))
                .region(Region.of(localStackContainer.getRegion()))
                .forcePathStyle(true)
                .build();

        // s3Client = S3AsyncClient
        //         .crtBuilder()
        //         .endpointOverride(localStackContainer.getEndpoint())
        //         .credentialsProvider(
        //                 StaticCredentialsProvider.create(
        //                         AwsBasicCredentials.create(localStackContainer.getAccessKey(),
        //                                 localStackContainer.getSecretKey())))
        //         .region(Region.of(localStackContainer.getRegion()))
        //         .forcePathStyle(true)
        //         .build();

        log.info("S3 Client setup completed with endpoint: {} region {}",
                localStackContainer.getEndpoint(), localStackContainer.getRegion());
    }

    @BeforeEach
    public void setup() throws Exception {
        try {

            log.info("setup...");

            // Create bucket
            s3Client.createBucket(CreateBucketRequest.builder().bucket("sample-bucket").build());
            log.info("Bucket 'sample-bucket' created successfully.");

            // Verify bucket creation
            boolean bucketExists = s3Client.listBuckets().buckets().stream()
                    .anyMatch(bucket -> bucket.name().equals("sample-bucket"));
            assertTrue(bucketExists, "Bucket 'sample-bucket' should exist");

            // Upload a test CSV file to S3
            byte[] content = Files.readAllBytes(Paths.get("src/test/resources/sample.csv"));
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket("sample-bucket")
                    .key("sample.csv")
                    .build(), Paths.get("src/test/resources/sample.csv"));

            log.debug("Supposed to upload finish");

        } catch (S3Exception e) {
            log.error("ExceptionUtils.getMessage: {}", ExceptionUtils.getMessage(e));
            log.error("awsErrorDetails: {}", e.awsErrorDetails());
            log.error("S3Exception: {}", e.awsErrorDetails().errorMessage());
        } catch (Exception e) {
            log.error("Exception during setup: ", e);
        }
    }

    private void logFileContentFromS3(String bucketName, String key) throws Exception {

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        // Download the file from S3
        byte[] content = s3Client.getObject(getObjectRequest).readAllBytes();

        // Convert the byte array to a string and log it
        String fileContent = new String(content);
        log.info("Content of the file from S3 ({}):\n{}", key, fileContent);
    }

    @Test
    public void testLocalStackIntegration() throws Exception {
        // Log the content of the file from S3
        logFileContentFromS3("sample-bucket", "sample.csv");
    }

}