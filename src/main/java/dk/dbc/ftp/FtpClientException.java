package dk.dbc.ftp;

import org.apache.commons.net.io.CopyStreamException;

public class FtpClientException extends RuntimeException {
    long progress = 0;
    FtpClientException(CopyStreamException e) {
        super(e);
        progress = e.getTotalBytesTransferred();
    }
    public FtpClientException(Throwable cause) {
        super(cause);
    }

    public FtpClientException(String message) {
        super(message);
    }

    public long getProgress() {
        return progress;
    }
}
