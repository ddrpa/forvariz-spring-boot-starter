package cc.ddrpa.dorian.forvariz.configuration;

public class RawBucketProperties {
    /**
     * Whether this bucket is primary
     */
    private boolean primary = false;
    /**
     * Qualifier of bucket service，used for distinguishing while injecting
     */
    private String qualifier;
    /**
     * Endpoint of bucket
     */
    private String endpoint;
    /**
     * Region of bucket
     */
    private String region = "us-east-1";
    /**
     * Credentials JSON filepath of bucket
     */
    private String credentials;
    /**
     * Access key of bucket
     */
    private String accessKey;
    /**
     * Secret key of bucket
     */
    private String secretKey;
    /**
     * Bucket name of bucket
     */
    private String bucket;
    /**
     * Public URL of bucket, used by pre-signed URL generation
     */
    private String publicURL;

    public boolean isPrimary() {
        return primary;
    }

    public RawBucketProperties setPrimary(boolean primary) {
        this.primary = primary;
        return this;
    }

    public String getQualifier() {
        return qualifier;
    }

    public RawBucketProperties setQualifier(String qualifier) {
        this.qualifier = qualifier;
        return this;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public RawBucketProperties setEndpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    public String getRegion() {
        return region;
    }

    public RawBucketProperties setRegion(String region) {
        this.region = region;
        return this;
    }

    public String getCredentials() {
        return credentials;
    }

    public RawBucketProperties setCredentials(String credentials) {
        this.credentials = credentials;
        return this;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public RawBucketProperties setAccessKey(String accessKey) {
        this.accessKey = accessKey;
        return this;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public RawBucketProperties setSecretKey(String secretKey) {
        this.secretKey = secretKey;
        return this;
    }

    public String getBucket() {
        return bucket;
    }

    public RawBucketProperties setBucket(String bucket) {
        this.bucket = bucket;
        return this;
    }

    public String getPublicURL() {
        return publicURL;
    }

    public RawBucketProperties setPublicURL(String publicURL) {
        this.publicURL = publicURL;
        return this;
    }
}