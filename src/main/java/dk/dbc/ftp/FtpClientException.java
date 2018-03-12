/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GPLv3
 * See license text in LICENSE.txt
 */

package dk.dbc.ftp;

public class FtpClientException extends RuntimeException {
    public FtpClientException(Throwable cause) {
        super(cause);
    }

    public FtpClientException(String message) {
        super(message);
    }
}
