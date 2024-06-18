# Förvariz Spring Boot Starter - 支持批量实例化 MinIO Client 的 Spring Boot Starter

MinIO 是一个高性能的 S3 兼容的对象存储系统实现，要在 Spring Boot 项目中使用 MinIO，你需要配置 `io.minio:minio` 依赖，然后实例化 `io.minio.MinioClient` Bean。

你可以使用 `@ConfigurationProperties` 注解配合 `application.properties` 配置 MinIO 客户端，这方面也有不少 `minio-spring-boot-starter` 可用，例如 [jlefebure/spring-boot-starter-minio - GitHub](https://github.com/jlefebure/spring-boot-starter-minio)。不过涉及到需要实例化多个 MinIO 客户端时（例如针对不同的场景需要操作特定 policy 的 bucket），事情就有些棘手了。

Förvariz Spring Boot Starter 项目提供这种批量实例化 MinIO 客户端的功能，项目代码基于 JDK 17 和 Spring Boot 3 编写，不过稍加改造应当也可以用于 Spring Boot 2。此外，就算你不需要使用多 Bucket 源这种特性，将 `cc.ddrpa.dorian.forvariz.BucketService` 用于自己的项目也是不错的。

Förvariz 是作者向 ChatGPT 询问后获得的项目名称，prompt 如下：

> 给我 10 个宜家风格的项目名称，名字要有 “存储” 的含义，可以使用瑞典语字符

## 怎样使用

作者对异常定义、API 设计等内容还在考虑中，后续可能还会有一些调整。你可以通过 [central.sonatype.com](https://central.sonatype.com/search?q=forvariz) 或 [mvnrepository.com](https://mvnrepository.com/artifact/cc.ddrpa.dorian) 查找最新版本。

在 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>cc.ddrpa.dorian</groupId>
    <artifactId>forvariz-spring-boot-starter</artifactId>
    <version>1.1.0</version>
</dependency>
```

你需要在 `application.properties` 中提供访问存储桶的配置，不过作者更喜欢 YAML。虽然作者提供了 `additional-spring-configuration-metadata.json`，不过行为好像不太符合预期：

```yaml
forvariz:
    # 配置一个主要的 MinIO 客户端
  - primary: true
    # 用于在依赖注入时指定使用的客户端
    qualifier: prog
    endpoint: https://oss.not-a.site/
    bucket: prog-bucket
    accessKey: au*********mq
    secretKey: mV**********************EOh
  - primary: false
    qualifier: playground
    endpoint: https://oss.not-a.site/
    bucket: playground
    # 也支持从文件读取凭证
    # credentials.json 可以从 Minio 控制台下载，程序会试图读取其中的 accessKey 和 secretKey 属性
    credentials: /Users/yufan/DevSpace/learn-java/omni-demo/credentials.json
```

在 `Controller`、`Service` 中注入 `cc.ddrpa.dorian.forvariz.BucketService` 实例，你可以使用 `@Qualifier` 特别指定要使用的实例：

```java
// 使用构造器注入
@RestController("/")
public class IndexController {
    private final BucketService primaryBucketService;
    private final BucketService bs2;
    private final BucketService bs3;

    public IndexController(BucketService bucketService,
                           @Qualifier("prog") BucketService bs2,
                           @Qualifier("playground") BucketService bs3) {
        this.primaryBucketService = bucketService;
        this.bs2 = bs2;
        this.bs3 = bs3;
        // ...

// 或使用 @Autowired 风格的注入方式
@SpringBootTest
public class BucketServiceTests {
    @Autowired
    private BucketService primaryBucketService;
    @Qualifier("prog")
    @Autowired
    private BucketService bs2;
    // ...
```

## Roadmap

- 支持 Actuator
- 看看 `listenBucketNotification` 方法怎么用