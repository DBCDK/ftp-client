/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GPLv3
 * See license text in LICENSE.txt
 */

package dk.dbc.ftp;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This utility class provides convenience methods for executing FTP commands.
 * This class is NOT thread-safe.
 */
public class FtpClient {
    private String host;
    private Integer port;
    private String username;
    private String password;
    private FTPClient session;

    public FtpClient withHost(String host) {
        this.host = host;
        return this;
    }

    public FtpClient withPort(Integer port) {
        this.port = port;
        return this;
    }

    public FtpClient withUsername(String username) {
        close();
        this.username = username;
        return this;
    }

    public FtpClient withPassword(String password) {
        close();
        this.password = password;
        return this;
    }

    /**
     * Opens connection to ftp server specified through withHost method
     * @return this client
     */
    public FtpClient connect() {
        if (isConnected()) {
            close();
        }
        session = new FTPClient();
        try {
            if (port != null) {
                session.connect(host, port);
            } else {
                session.connect(host);
            }
            checkReplyCode();
            session.enterLocalPassiveMode();
            session.setFileType(FTP.BINARY_FILE_TYPE);
        } catch (IOException e) {
            throw new FtpClientException(e);
        }
        try {
            session.login(username, password);
            checkReplyCode();
        } catch (IOException e) {
            throw new FtpClientException(e);
        }
        return this;
    }

    /**
     * Closes the connection to the FTP server
     * @return this client
     */
    public FtpClient close() {
        if (isConnected()) {
            try {
                session.disconnect();
                session = null;
            } catch (IOException e) {
                throw new FtpClientException(e);
            }
        }
        return this;
    }

    /**
     * Changes the current working directory of the FTP session
     * @param remotePath the new current working directory
     * @return this client
     */
    public FtpClient cd(String remotePath) {
        if (!isConnected()) {
            connect();
        }
        try {
            session.changeWorkingDirectory(remotePath);
            checkReplyCode();
        } catch (IOException e) {
            throw new FtpClientException(e);
        }
        return this;
    }

    /**
     * Stores content of string {@code content} as file on the server
     * using name {@code remote}
     * @param remote name of remote file
     * @param content content of remote file
     * @return this client
     */
    public FtpClient put(String remote, String content) {
        put(remote, new ByteArrayInputStream(
                content.getBytes(StandardCharsets.UTF_8)));
        return this;
    }

    /**
     * Stores local file as pointed to by {@code localFile} as file
     * on the server using name {@code remote}
     * @param remote name of remote file
     * @param localFile path of local file
     * @return this client
     */
    public FtpClient put(String remote, Path localFile) {
        try {
            put(remote, Files.newInputStream(localFile));
        } catch (IOException e) {
            throw new FtpClientException(e);
        }
        return this;
    }

    /**
     * Stores local file as pointed to by {@code localFile} as file
     * on the server
     * @param localFile path of local file
     * @return this client
     */
    public FtpClient put(Path localFile) {
        return put(localFile.getFileName().toString(), localFile);
    }

    /**
     * Stores input from the given InputStream {@code inputStream}
     * as file on the server using name {@code remote}. This method
     * closes the given InputStream.
     * @param remote name of remote file
     * @param inputStream local InputStream from which to read content
     * @return this client
     */
    public FtpClient put(String remote, InputStream inputStream) {
        if (!isConnected()) {
            connect();
        }
        try {
            session.storeFile(remote, inputStream);
            checkReplyCode();
        } catch (IOException e) {
            throw new FtpClientException(e);
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                throw new FtpClientException(e);
            }
        }
        return this;
    }

    private boolean isConnected() {
        return session != null && session.isConnected();
    }

    private void checkReplyCode() {
        final int replyCode = session.getReplyCode();
        if (!FTPReply.isPositiveCompletion(replyCode)) {
            throw new FtpClientException(session.getReplyString());
        }
    }
}
