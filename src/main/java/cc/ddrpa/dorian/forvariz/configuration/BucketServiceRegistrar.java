package cc.ddrpa.dorian.forvariz.configuration;

import cc.ddrpa.dorian.forvariz.BucketService;
import cc.ddrpa.dorian.forvariz.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class BucketServiceRegistrar implements BeanDefinitionRegistryPostProcessor {
    private static final Logger logger = LoggerFactory.getLogger(BucketServiceRegistrar.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private List<RawBucketProperties> rawBuckets;

    public BucketServiceRegistrar(Environment environment) {
        rawBuckets = Binder.get(environment)
                .bind("forvariz", Bindable.listOf(RawBucketProperties.class))
                .orElseThrow(() -> new ServiceException("Förvariz bucket properties not found."));
        rawBuckets.forEach(this::validate);
        if (rawBuckets.stream().map(RawBucketProperties::isPrimary).filter(i -> i).count() > 1) {
            logger.atError().log("Only one primary bucket operator is allowed.");
            throw new NoUniqueBeanDefinitionException(BucketService.class, "Only one primary bucket operator is allowed.");
        }
    }

    public void validate(RawBucketProperties properties) {
        if (!StringUtils.hasText(properties.getBucket())) {
            logger.atError().log("Bucket name is required.");
            throw new ServiceException("Bucket name is required.");
        }
        if (!StringUtils.hasText(properties.getEndpoint())) {
            logger.atError().log("Endpoint is required.");
            throw new ServiceException("Endpoint is required.");
        }
        if (!StringUtils.hasText(properties.getCredentials()) &&
                (!StringUtils.hasText(properties.getAccessKey()) || !StringUtils.hasText(properties.getSecretKey()))) {
            logger.atError().log("Credentials for bucket is required.");
            throw new ServiceException("Credentials for bucket is required.");
        }
        if (!StringUtils.hasText(properties.getQualifier())) {
            logger.atError().log("Qualifier of bucket operator is required");
            throw new ServiceException("Qualifier of bucket operator is required");
        }
    }

    @Override
    public void postProcessBeanDefinitionRegistry(@NotNull BeanDefinitionRegistry registry) throws BeansException {
        for (RawBucketProperties properties : rawBuckets) {
            var isPrimaryBean = properties.isPrimary();
            var qualifier = properties.getQualifier();
            var bucketName = properties.getBucket();
            // 读取 credentials 文件获得 accessKey 和 secretKey，或直接访问属性
            String accessKey = null;
            String secretKey = null;
            try {
                var credentialsContent = Files.readString(Path.of(properties.getCredentials()));
                var node = mapper.readTree(credentialsContent);
                accessKey = node.get("accessKey").asText();
                secretKey = node.get("secretKey").asText();
            } catch (Exception e) {
                if (!StringUtils.hasText(accessKey)) {
                    accessKey = properties.getAccessKey();
                }
                if (!StringUtils.hasText(secretKey)) {
                    secretKey = properties.getSecretKey();
                }
            }
            if (!StringUtils.hasText(accessKey) || !StringUtils.hasText(secretKey)) {
                logger.atError().log("Credentials for {} can not be found.", qualifier);
                throw new ServiceException(String.format("Credentials for %s can not be found.", qualifier));
            }
            BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(BucketService.class);
            beanDefinitionBuilder.addConstructorArgValue(bucketName);
            beanDefinitionBuilder.addConstructorArgValue(new S3BucketProperties(properties.getEndpoint(), properties.getRegion(), accessKey, secretKey));
            BeanDefinition beanDefinition = beanDefinitionBuilder.getRawBeanDefinition();
            beanDefinition.setPrimary(isPrimaryBean);
            registry.registerBeanDefinition(qualifier, beanDefinition);
            logger.atInfo().log("Register bean definition for {}.", qualifier);
        }
    }

    @Override
    public void postProcessBeanFactory(@NotNull ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }
}