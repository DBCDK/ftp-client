package dk.dbc.ftp;

public class FtpClientException extends RuntimeException {
    public FtpClientException(Throwable cause) {
        super(cause);
    }

    public FtpClientException(String message) {
        super(message);
    }
}
