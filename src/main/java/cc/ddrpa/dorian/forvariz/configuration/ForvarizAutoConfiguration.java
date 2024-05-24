package cc.ddrpa.dorian.forvariz.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class ForvarizAutoConfiguration {

    @Bean
    public static BucketServiceRegistrar beanDefinitionRegistrar(Environment environment) {
        return new BucketServiceRegistrar(environment);
    }
}