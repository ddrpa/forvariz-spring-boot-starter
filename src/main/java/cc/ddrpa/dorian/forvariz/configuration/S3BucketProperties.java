package cc.ddrpa.dorian.forvariz.configuration;

public record S3BucketProperties(String endpoint, String region, String accessKey,
                                 String secretKey) {

}