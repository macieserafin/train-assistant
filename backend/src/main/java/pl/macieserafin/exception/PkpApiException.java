package pl.macieserafin.exception;

public class PkpApiException extends RuntimeException {
    private final Reason reason;
    private final Integer upstreamStatus;

    public PkpApiException(Reason reason, Integer upstreamStatus) {
        super(reason.name());
        this.reason = reason;
        this.upstreamStatus = upstreamStatus;
    }

    public Reason reason() { return reason; }
    public Integer upstreamStatus() { return upstreamStatus; }

    public enum Reason {
        MISSING_KEY,
        ACCESS_DENIED,
        RATE_LIMITED,
        UPSTREAM_ERROR,
        INVALID_RESPONSE,
        TIMEOUT,
        TLS_ERROR,
        CONNECTION_ERROR
    }
}
