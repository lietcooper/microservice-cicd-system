package cicd.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Uploads matched artifact files from the workspace to MinIO
 * (S3-compatible object storage) so they survive pod restarts.
 *
 * <p>Object key convention:
 * {@code {pipeline}/{run-no}/{stage}/{job}/{relative-path}}
 */
@Service
public class ArtifactStorageService {

  private static final Logger log =
      LoggerFactory.getLogger(ArtifactStorageService.class);

  private final MinioClient minioClient;
  private final String bucket;

  /** Creates the service with MinIO configuration. */
  @org.springframework.beans.factory.annotation.Autowired
  public ArtifactStorageService(
      @Value("${cicd.minio.endpoint:http://localhost:9000}")
      String endpoint,
      @Value("${cicd.minio.access-key:minioadmin}")
      String accessKey,
      @Value("${cicd.minio.secret-key:minioadmin}")
      String secretKey,
      @Value("${cicd.minio.bucket:cicd-artifacts}")
      String bucket) {
    this.minioClient = MinioClient.builder()
        .endpoint(endpoint)
        .credentials(accessKey, secretKey)
        .build();
    this.bucket = bucket;
  }

  /** Test-only constructor that accepts a pre-built client. */
  ArtifactStorageService(MinioClient minioClient, String bucket) {
    this.minioClient = minioClient;
    this.bucket = bucket;
  }

  /** Ensures the bucket exists on startup. */
  @PostConstruct
  public void ensureBucket() {
    try {
      boolean exists = minioClient.bucketExists(
          BucketExistsArgs.builder().bucket(bucket).build());
      if (!exists) {
        minioClient.makeBucket(
            MakeBucketArgs.builder().bucket(bucket).build());
        log.info("Created MinIO bucket '{}'", bucket);
      }
    } catch (Exception ex) {
      log.warn("Could not ensure bucket '{}': {}",
          bucket, ex.getMessage());
    }
  }

  /**
   * Uploads artifact files from workspace to MinIO.
   *
   * @param relativePaths artifact paths relative to workspace root
   * @param workspaceRoot the workspace directory
   * @param pipeline      pipeline name
   * @param runNo         run number
   * @param stage         stage name
   * @param job           job name
   * @return list of object keys where artifacts were stored
   */
  public List<String> store(List<String> relativePaths,
      Path workspaceRoot, String pipeline, int runNo,
      String stage, String job) {
    List<String> storedKeys = new ArrayList<>();

    if (relativePaths == null || relativePaths.isEmpty()) {
      return storedKeys;
    }

    String keyPrefix = pipeline + "/" + runNo + "/"
        + stage + "/" + job + "/";

    for (String relativePath : relativePaths) {
      Path source = workspaceRoot.resolve(relativePath);
      String objectKey = keyPrefix + relativePath;

      try (InputStream is = Files.newInputStream(source)) {
        long size = Files.size(source);
        minioClient.putObject(PutObjectArgs.builder()
            .bucket(bucket)
            .object(objectKey)
            .stream(is, size, -1)
            .build());
        storedKeys.add(objectKey);
        log.debug("Stored artifact: {} -> s3://{}/{}",
            relativePath, bucket, objectKey);
      } catch (Exception ex) {
        log.warn("Failed to store artifact '{}': {}",
            relativePath, ex.getMessage());
      }
    }

    log.info("Stored {} artifact(s) for job '{}'",
        storedKeys.size(), job);
    return storedKeys;
  }

  /** Returns the bucket name. */
  public String getBucket() {
    return bucket;
  }
}
