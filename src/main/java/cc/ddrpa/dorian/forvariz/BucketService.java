package cc.ddrpa.dorian.forvariz;

import cc.ddrpa.dorian.forvariz.configuration.S3BucketProperties;
import cc.ddrpa.dorian.forvariz.exception.GeneralBucketServiceException;
import cc.ddrpa.dorian.forvariz.exception.NoSuchKeyException;
import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import io.minio.messages.Item;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

public class BucketService {
    private final String bucket;
    private final MinioClient minioClient;

    private BucketService(String bucket, S3BucketProperties properties) {
        this.bucket = bucket;
        this.minioClient = MinioClient.builder()
                .endpoint(properties.endpoint())
                .region(properties.region())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    /**
     * 列举根路径下的所有对象，可以配合 {@link BucketService#toList(Iterable)} 使用获得 List
     *
     *
     * @return iterable item
     */
    public Iterable<Result<Item>> list() {
        ListObjectsArgs args = ListObjectsArgs.builder()
                .bucket(bucket)
                .prefix("")
                .delimiter("")
                .recursive(true)
                .build();
        return minioClient.listObjects(args);
    }

    /**
     * TODO see https://www.alibabacloud.com/help/zh/oss/developer-reference/listobjects
     * 列举指定路径下的所有对象，可以配合 {@link BucketService#toList(Iterable)} 使用获得 List
     *
     * @param prefix    指定前缀查询
     * @param delimiter 分隔符
     * @param recursive 是否递归查询
     * @return iterable item
     */
    public Iterable<Result<Item>> list(String prefix, String delimiter, boolean recursive) {
        ListObjectsArgs args = ListObjectsArgs.builder()
                .bucket(bucket)
                .prefix(prefix)
                .delimiter(delimiter)
                .recursive(recursive)
                .build();
        return minioClient.listObjects(args);
    }

    /**
     * 以 {@link InputStream} 读取对象，需要在结束后关闭流
     *
     * <pre>Example:{@code
     * try (InputStream stream = bucketOperator.get("dir1/my-pic.jpeg")) {
     *   // Read data from stream
     * }
     * }</pre>
     * @throws NoSuchKeyException 如果指定的对象不存在，抛出此异常
     * @throws GeneralBucketServiceException 其他异常
     */
    public InputStream get(@NotNull String objectName) throws GeneralBucketServiceException, NoSuchKeyException {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equalsIgnoreCase("NoSuchKey")) {
                throw new NoSuchKeyException(e);
            } else {
                throw new GeneralBucketServiceException(e);
            }
        } catch (InsufficientDataException | InternalException | InvalidKeyException | InvalidResponseException |
                 NoSuchAlgorithmException | ServerException | XmlParserException | IOException e) {
            throw new GeneralBucketServiceException(e);
        }
    }

    /**
     * 获取指定对象的元数据
     *
     * @param objectName
     * @return
     * @throws NoSuchKeyException 如果指定的对象不存在，抛出此异常
     * @throws GeneralBucketServiceException 其他异常
     */
    public StatObjectResponse stat(String objectName) throws GeneralBucketServiceException, NoSuchKeyException {
        try {
            StatObjectArgs args = StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build();
            return minioClient.statObject(args);
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equalsIgnoreCase("NoSuchKey")) {
                throw new NoSuchKeyException(e);
            } else {
                throw new GeneralBucketServiceException(e);
            }
        } catch (ServerException | InsufficientDataException | IOException | NoSuchAlgorithmException |
                 InvalidKeyException | InvalidResponseException | XmlParserException | InternalException e) {
            throw new GeneralBucketServiceException(e);
        }
    }

    /**
     * 通过 {@link InputStream} 上传对象
     *
     * <pre>Example: {@code
     * bucketOperator.put("dir/my-pic2.jpeg",
     *   file.getInputStream(),
     *   file.getContentType(),
     *   Map.of("X-Amz-Storage-Class", "REDUCED_REDUNDANCY"),
     *   Map.of("My-Project", "Project One"));
     * }</pre>
     *
     * @param objectName
     * @param stream
     * @param contentType
     * @param headers
     * @param userMetadata
     * @throws GeneralBucketServiceException
     */
    public void put(@NotNull String objectName,
                    @NotNull InputStream stream,
                    @NotNull String contentType,
                    @Nullable Map<String, String> headers,
                    @Nullable Map<String, String> userMetadata) throws GeneralBucketServiceException {
        try {
            var argsBuilder = PutObjectArgs.builder()
                    .bucket(bucket)
                    .contentType(contentType)
                    .object(objectName)
                    .stream(stream, stream.available(), -1);
            if (headers != null && !headers.isEmpty()) {
                argsBuilder.headers(headers);
            }
            if (userMetadata != null && !userMetadata.isEmpty()) {
                argsBuilder.userMetadata(userMetadata);
            }
            minioClient.putObject(argsBuilder.build());
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException |
                 InvalidResponseException | NoSuchAlgorithmException | ServerException | XmlParserException |
                 IOException e) {
            throw new GeneralBucketServiceException(e);
        }
    }

    /**
     * 删除指定对象
     *
     * <pre>Example:{@code
     * String url = bucketOperator.remove("my-pic.jpeg");
     * }</pre>
     */
    public void remove(@NotNull String objectName) throws GeneralBucketServiceException {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
        } catch (ErrorResponseException e) {
            if (!e.errorResponse().code().equalsIgnoreCase("NoSuchKey")) {
                throw new GeneralBucketServiceException(e);
            }
        } catch (InsufficientDataException | InternalException | InvalidKeyException | InvalidResponseException |
                 NoSuchAlgorithmException | ServerException | XmlParserException | IOException e) {
            throw new GeneralBucketServiceException(e);
        }
    }

    /**
     * 获取一个预签名的 URL，用于访问对象
     *
     * @param objectName
     * @param expireDuration 过期时间，受到 MinIO 等服务实现的限制，若要设置更长时间，请考虑使用 Policy
     * @return 用于访问文件的 URL
     * @throws ArithmeticException
     * @throws NoSuchKeyException 如果指定的对象不存在，抛出此异常
     * @throws GeneralBucketServiceException 其他异常
     */
    public String presignObjectUrlToGet(@NotNull String objectName, @NotNull Duration expireDuration) throws GeneralBucketServiceException, ArithmeticException, NoSuchKeyException {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry(Math.toIntExact(expireDuration.toSeconds()), TimeUnit.SECONDS)
                            .build());
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equalsIgnoreCase("NoSuchKey")) {
                throw new NoSuchKeyException(e);
            } else {
                throw new GeneralBucketServiceException(e);
            }
        } catch (InsufficientDataException | InternalException | InvalidKeyException | InvalidResponseException |
                 NoSuchAlgorithmException | ServerException | XmlParserException | IOException e) {
            throw new GeneralBucketServiceException(e);
        }
    }

    /**
     * 获取一个预签名的 URL，用于上传对象
     *
     * @param objectName
     * @param expireDuration 过期时间，受到 MinIO 等服务实现的限制，若要设置更长时间，请考虑使用 Policy
     * @param extraHeaders
     * @param extraQueryParams
     * @return 用于以 HTTP PUT 方式上传文件的 URL
     * @throws ArithmeticException
     * @throws GeneralBucketServiceException
     */
    public String presignObjectUrlToPut(@NotNull String objectName,
                                        @NotNull Duration expireDuration,
                                        @Nullable Map<String, String> extraHeaders,
                                        @Nullable Map<String, String> extraQueryParams) throws GeneralBucketServiceException, ArithmeticException {
        GetPresignedObjectUrlArgs.Builder argsBuilder = GetPresignedObjectUrlArgs.builder()
                .method(Method.PUT)
                .bucket(bucket)
                .object(objectName)
                .expiry(Math.toIntExact(expireDuration.toSeconds()), TimeUnit.SECONDS);
        if (extraHeaders != null && !extraHeaders.isEmpty()) {
            argsBuilder.extraHeaders(extraHeaders);
        }
        if (extraQueryParams != null && !extraQueryParams.isEmpty()) {
            argsBuilder.extraQueryParams(extraQueryParams);
        }
        try {
            return minioClient.getPresignedObjectUrl(argsBuilder.build());
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException |
                 InvalidResponseException | NoSuchAlgorithmException | ServerException | XmlParserException |
                 IOException e) {
            throw new GeneralBucketServiceException(e);
        }
    }

    /**
     * 获取这个 BucketService 的 MinioClient 对象
     *
     * @return
     */
    public MinioClient _getRawClient() {
        return minioClient;
    }

    /**
     * 获取这个 BucketService 的 bucket 名称
     *
     * @return bucket name
     */
    public String _getBucket() {
        return bucket;
    }

    /**
     * Utility method which map results to items and return a list
     *
     * @param items Iterable of results
     * @return List of items
     */
    public static List<Item> toList(Iterable<Result<Item>> items) {
        return StreamSupport
                .stream(items.spliterator(), true)
                .map(item -> {
                    try {
                        return item.get();
                    } catch (ServerException | InsufficientDataException | ErrorResponseException | IOException |
                             NoSuchAlgorithmException | InvalidKeyException | InvalidResponseException |
                             XmlParserException | InternalException ignored) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }
}