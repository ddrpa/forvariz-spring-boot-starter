package cc.ddrpa.dorian.forvariz;

import cc.ddrpa.dorian.forvariz.configuration.S3BucketProperties;
import cc.ddrpa.dorian.forvariz.exception.GeneralBucketServiceException;
import cc.ddrpa.dorian.forvariz.exception.NoSuchKeyException;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.GetPresignedObjectUrlArgs.Builder;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PostPolicy;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import io.minio.http.Method;
import io.minio.messages.Item;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BucketService {

    // 预签名 URL 的过期时间默认为 7 天，与 MinIO 保持一致
    private static final int DEFAULT_PRESIGNED_URL_EXPIRATION_IN_SECONDS = 7 * 24 * 60 * 60;

    private final String bucket;
    private final MinioClient minioClient;
    private final String endpoint;
    private final String defaultDelimiter;

    private BucketService(String bucket, String delimiter, S3BucketProperties properties) {
        this.bucket = bucket;
        this.endpoint = properties.endpoint();
        this.defaultDelimiter = delimiter;
        this.minioClient = MinioClient.builder()
            .endpoint(this.endpoint)
            .region(properties.region())
            .credentials(properties.accessKey(), properties.secretKey())
            .build();
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
                } catch (ServerException | InsufficientDataException | ErrorResponseException |
                         IOException | NoSuchAlgorithmException | InvalidKeyException |
                         InvalidResponseException | XmlParserException |
                         InternalException ignored) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * 列举所有对象，可以配合 {@link BucketService#toList(Iterable)} 使用获得 List
     * <p>
     * 从根目录开始递归获取所有对象，使用 "/" 作为分隔符
     *
     * @return iterable item
     */
    public Iterable<Result<Item>> list() {
        ListObjectsArgs args = ListObjectsArgs.builder()
            .bucket(bucket)
            .prefix("")
            .delimiter(defaultDelimiter)
            .recursive(true)
            .build();
        return minioClient.listObjects(args);
    }

    /**
     * 列出指定前缀的所有对象，可以配合 {@link BucketService#toList(Iterable)} 使用获得 List
     * <p>
     *
     * @param prefix    指定前缀查询
     * @param delimiter 分隔符
     * @param recursive 是否递归查询
     * @return iterable item
     * @see <a
     * href="https://docs.aws.amazon.com/zh_cn/AmazonS3/latest/userguide/using-prefixes.html">使用前缀组织对象</a>
     * 了解用法
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
     * <pre>Example:{@code
     * try (InputStream stream = bucketOperator.get("dir1/my-pic.jpeg")) {
     *   // Read data from stream
     * }
     * }</pre>
     *
     * @param objectName 对象名称
     * @throws NoSuchKeyException            如果指定的对象不存在，抛出此异常
     * @throws GeneralBucketServiceException 其他异常
     */
    public InputStream get(@NotNull String objectName)
        throws GeneralBucketServiceException, NoSuchKeyException {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .build());
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equalsIgnoreCase("NoSuchKey")) {
                throw new NoSuchKeyException(e.errorResponse().message(), e);
            } else {
                throw new GeneralBucketServiceException(e.errorResponse().message(), e);
            }
        } catch (InsufficientDataException | InternalException | InvalidKeyException |
                 InvalidResponseException | NoSuchAlgorithmException | ServerException |
                 XmlParserException | IOException e) {
            throw new GeneralBucketServiceException("Unclassified Error", e);
        }
    }

    /**
     * 获取指定对象的元数据，常见的用法是判断对象是否存在
     *
     * @param objectName
     * @return
     * @throws NoSuchKeyException            如果指定的对象不存在，抛出此异常
     * @throws GeneralBucketServiceException 其他异常
     */
    public StatObjectResponse stat(String objectName)
        throws GeneralBucketServiceException, NoSuchKeyException {
        try {
            StatObjectArgs args = StatObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .build();
            return minioClient.statObject(args);
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equalsIgnoreCase("NoSuchKey")) {
                throw new NoSuchKeyException(e.errorResponse().message(), e);
            } else {
                throw new GeneralBucketServiceException(e.errorResponse().message(), e);
            }
        } catch (ServerException | InsufficientDataException | IOException |
                 NoSuchAlgorithmException | InvalidKeyException | InvalidResponseException |
                 XmlParserException | InternalException e) {
            throw new GeneralBucketServiceException("Unclassified Error", e);
        }
    }

    /**
     * 通过 {@link InputStream} 上传对象
     *
     * <pre>Example: {@code
     * bucketService.put("dir/my-pic2.jpeg",
     *   file.getInputStream(),
     *   file.getContentType(),
     *   Map.of("X-Amz-Storage-Class", "REDUCED_REDUNDANCY"),
     *   Map.of("My-Project", "Project One"));
     * }</pre>
     *
     * @param objectName   对象名称
     * @param inputStream  输入流
     * @param contentType  文件的 MIME-Type
     * @param headers
     * @param userMetadata
     * @throws GeneralBucketServiceException
     */
    public void put(@NotNull String objectName,
        @NotNull InputStream inputStream,
        @NotNull String contentType,
        @Nullable Map<String, String> headers,
        @Nullable Map<String, String> userMetadata) throws GeneralBucketServiceException {
        try {
            var argsBuilder = PutObjectArgs.builder()
                .bucket(bucket)
                .contentType(contentType)
                .object(objectName)
                .stream(inputStream, inputStream.available(), -1);
            if (headers != null && !headers.isEmpty()) {
                argsBuilder.headers(headers);
            }
            if (userMetadata != null && !userMetadata.isEmpty()) {
                argsBuilder.userMetadata(userMetadata);
            }
            minioClient.putObject(argsBuilder.build());
        } catch (ErrorResponseException | InsufficientDataException | InternalException |
                 InvalidKeyException | InvalidResponseException | NoSuchAlgorithmException |
                 ServerException | XmlParserException | IOException e) {
            throw new GeneralBucketServiceException("Unclassified Error", e);
        }
    }

    /**
     * 删除指定对象
     * <pre>Example:{@code
     * String url = bucketOperator.remove("my-pic.jpeg");
     * }</pre>
     *
     * @param objectName 对象名称
     * @throws GeneralBucketServiceException
     */
    public void remove(@NotNull String objectName) throws GeneralBucketServiceException {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .build());
        } catch (ErrorResponseException e) {
            // 如果抛出的错误是「不存在该对象」，则忽略异常
            if (!e.errorResponse().code().equalsIgnoreCase("NoSuchKey")) {
                throw new GeneralBucketServiceException(e.errorResponse().message(), e);
            }
        } catch (InsufficientDataException | InternalException | InvalidKeyException |
                 InvalidResponseException | NoSuchAlgorithmException | ServerException |
                 XmlParserException | IOException e) {
            throw new GeneralBucketServiceException("Unclassified Error", e);
        }
    }

    /**
     * 获取一个预签名的 URL，用于访问对象
     *
     * @param objectName     对象名称
     * @param expireDuration 链接过期时间，默认为 7 天；受到 MinIO 等服务实现的限制，若要设置更长时间，请考虑通过 bucket 的 Policy 设置
     * @return 用于访问文件的 URL
     * @throws ArithmeticException
     * @throws NoSuchKeyException            如果指定的对象不存在，抛出此异常
     * @throws GeneralBucketServiceException 其他异常
     */
    public String presignObjectUrlToGet(
        @NotNull String objectName,
        @Nullable Duration expireDuration)
        throws GeneralBucketServiceException, ArithmeticException, NoSuchKeyException {
        return presignObjectUrlToGet(objectName, expireDuration, null, null);
    }

    /**
     * 获取一个预签名的 URL，用于访问对象
     *
     * @param objectName       对象名称
     * @param expireDuration   链接过期时间，默认为 7 天；受到 MinIO 等服务实现的限制，若要设置更长时间，请考虑通过 bucket 的 Policy 设置
     * @param extraHeaders     要求客户端发起的请求在请求头中包含指定键值对
     * @param extraQueryParams
     * @return 用于访问文件的 URL
     * @throws ArithmeticException
     * @throws NoSuchKeyException            如果指定的对象不存在，抛出此异常
     * @throws GeneralBucketServiceException 其他异常
     */
    public String presignObjectUrlToGet(
        @NotNull String objectName,
        @Nullable Duration expireDuration,
        @Nullable Map<String, String> extraHeaders,
        @Nullable Map<String, String> extraQueryParams)
        throws GeneralBucketServiceException, ArithmeticException, NoSuchKeyException {
        Builder builder = GetPresignedObjectUrlArgs.builder()
            .method(Method.GET)
            .bucket(bucket)
            .object(objectName);
        if (null == expireDuration) {
            builder.expiry(DEFAULT_PRESIGNED_URL_EXPIRATION_IN_SECONDS, TimeUnit.SECONDS);
        } else {
            builder.expiry(Math.toIntExact(expireDuration.toSeconds()), TimeUnit.SECONDS);
        }
        if (null != extraHeaders && !extraHeaders.isEmpty()) {
            builder.extraHeaders(extraHeaders);
        }
        if (null != extraQueryParams && !extraQueryParams.isEmpty()) {
            builder.extraQueryParams(extraQueryParams);
        }
        try {
            return minioClient.getPresignedObjectUrl(builder.build());
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equalsIgnoreCase("NoSuchKey")) {
                throw new NoSuchKeyException(e.errorResponse().message(), e);
            } else {
                throw new GeneralBucketServiceException(e.errorResponse().message(), e);
            }
        } catch (InsufficientDataException | InternalException | InvalidKeyException |
                 InvalidResponseException | NoSuchAlgorithmException | ServerException |
                 XmlParserException | IOException e) {
            throw new GeneralBucketServiceException("Unclassified Error", e);
        }
    }

    /**
     * 获取一个预签名的 URL，用于上传对象
     * <p>
     * 注意：用户可以使用预签名的 URL 上传任意文件到对象存储而不需要通过验证。如果此行为不符合预期，请通过 lambda 或其他方式验证文件内容。
     *
     * @param objectName     对象名称
     * @param expireDuration 链接过期时间，默认为 7 天；受到 MinIO 等服务实现的限制，若要设置更长时间，请考虑通过 bucket 的 Policy 设置
     * @return 用于以 HTTP PUT 方式上传文件的 URL
     * @throws ArithmeticException
     * @throws GeneralBucketServiceException
     * @see <a
     * href="https://www.reddit.com/r/aws/comments/zmbw4h/enforce_content_type_during_upload_with_s3_signed/">Enforce
     * content type during upload with S3 signed url</a>
     */
    public String presignObjectUrlToPut(
        @NotNull String objectName,
        @Nullable Duration expireDuration)
        throws GeneralBucketServiceException, ArithmeticException {
        return presignObjectUrlToPut(objectName, expireDuration, null, null);
    }

    /**
     * 获取一个预签名的 URL，用于上传对象
     * <p>
     * 注意：用户可以使用预签名的 URL 上传任意文件到对象存储而不需要通过验证。如果此行为不符合预期，请通过 lambda 或其他方式验证文件内容。
     * <p>
     * 注意：不要尝试通过 extraHeaders 参数设置 Content-Type 限制，恶意用户可以绕过这个机制
     *
     * @param objectName       对象名称
     * @param expireDuration   链接过期时间，默认为 7 天；受到 MinIO 等服务实现的限制，若要设置更长时间，请考虑通过 bucket 的 Policy 设置
     * @param extraHeaders     要求客户端发起的请求在请求头中包含指定键值对
     * @param extraQueryParams
     * @return 用于以 HTTP PUT 方式上传文件的 URL
     * @throws ArithmeticException
     * @throws GeneralBucketServiceException
     * @see <a
     * href="https://www.reddit.com/r/aws/comments/zmbw4h/enforce_content_type_during_upload_with_s3_signed/">Enforce
     * content type during upload with S3 signed url</a>
     */
    public String presignObjectUrlToPut(
        @NotNull String objectName,
        @Nullable Duration expireDuration,
        @Nullable Map<String, String> extraHeaders,
        @Nullable Map<String, String> extraQueryParams)
        throws GeneralBucketServiceException, ArithmeticException {
        GetPresignedObjectUrlArgs.Builder argsBuilder = GetPresignedObjectUrlArgs.builder()
            .method(Method.PUT)
            .bucket(bucket)
            .object(objectName);
        if (null == expireDuration) {
            argsBuilder.expiry(DEFAULT_PRESIGNED_URL_EXPIRATION_IN_SECONDS, TimeUnit.SECONDS);
        } else {
            argsBuilder.expiry(Math.toIntExact(expireDuration.toSeconds()), TimeUnit.SECONDS);
        }
        if (null != extraHeaders && !extraHeaders.isEmpty()) {
            argsBuilder.extraHeaders(extraHeaders);
        }
        if (null != extraQueryParams && !extraQueryParams.isEmpty()) {
            argsBuilder.extraQueryParams(extraQueryParams);
        }
        try {
            return minioClient.getPresignedObjectUrl(argsBuilder.build());
        } catch (ErrorResponseException | InsufficientDataException | InternalException |
                 InvalidKeyException | InvalidResponseException | NoSuchAlgorithmException |
                 ServerException | XmlParserException | IOException e) {
            throw new GeneralBucketServiceException("Unclassified Error", e);
        }
    }

    /**
     * 构造一个 Map，需要通过 POST 方式上传文件时，可以使用这个 Map 作为 form-data 的数据。
     * <p>
     * 注意：恶意用户可伪造 HTTP POST 的 FormData 从而绕过验证。如果此行为不符合预期，请通过 lambda 或其他方式验证文件内容。
     *
     * @param objectName              上传对象的名称
     * @param expireDuration          过期时间
     * @param contentTypePrefix       可指定请求的 Content-Type 为何种模式，例如 image/ 匹配所有图片类型
     * @param contentLengthLowerLimit 上传文件的最小长度
     * @param contentLengthUpperLimit 上传文件的最大长度
     * @return map of form data in HTTP POST
     * @throws GeneralBucketServiceException
     * @see <a
     * href="https://www.reddit.com/r/aws/comments/zmbw4h/enforce_content_type_during_upload_with_s3_signed/">Enforce
     * content type during upload with S3 signed url</a>
     * @see <a
     * href="https://min.io/docs/minio/linux/developers/java/API.html#getPresignedPostFormData">MinIO
     * API - getPresignedPostFormData</a>
     */
    public Map<String, String> getPresignedPostFormData(
        @NotNull String objectName,
        @Nullable Duration expireDuration,
        @Nullable String contentTypePrefix,
        @Nullable Long contentLengthLowerLimit, @Nullable Long contentLengthUpperLimit)
        throws GeneralBucketServiceException {
        ZonedDateTime policyExpiry;
        if (expireDuration == null) {
            policyExpiry = ZonedDateTime.now()
                .plusSeconds(DEFAULT_PRESIGNED_URL_EXPIRATION_IN_SECONDS);
        } else {
            policyExpiry = ZonedDateTime.now().plus(expireDuration);
        }
        PostPolicy policy = new PostPolicy(this.bucket, policyExpiry);
        policy.addEqualsCondition("key", objectName);
        if (StringUtils.isNotEmpty(contentTypePrefix)) {
            policy.addStartsWithCondition("Content-Type", contentTypePrefix);
        }
        if (contentLengthLowerLimit != null && contentLengthUpperLimit != null
            && contentLengthLowerLimit < contentLengthUpperLimit) {
            policy.addContentLengthRangeCondition(contentLengthLowerLimit, contentLengthUpperLimit);
        }
        try {
            Map<String, String> formData = minioClient.getPresignedPostFormData(policy);
            formData.put("key", objectName);
            return formData;
        } catch (ErrorResponseException | InsufficientDataException | InternalException |
                 InvalidKeyException | InvalidResponseException | IOException |
                 NoSuchAlgorithmException | ServerException | XmlParserException e) {
            throw new GeneralBucketServiceException("Unclassified Error", e);
        }
    }

    /**
     * 构造访问指定对象的公开 URL，当对象的 Prefix 或 Bucket 为 public-read 时可用
     * <pre>Example: {@code
     * {
     *   "Action": [
     *     "s3:GetObject"
     *   ],
     *   "Effect": "Allow",
     *   "Principal": {
     *     "AWS": [
     *       "*"
     *     ]
     *   },
     *   "Resource": [
     *     "arn:aws:s3:::general/avatar/*",
     *     "arn:aws:s3:::general/public-resources/*"
     *   ]
     * }}</pre>
     *
     * @param objectName 对象名称
     * @return
     */
    public String publicURLtoGet(@NotNull String objectName) {
        return String.join(defaultDelimiter, this.endpoint, bucket, objectName);
    }

    /**
     * 获取这个 BucketService 的 MinioClient 对象
     *
     * @return MinioClient 实例
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
}
