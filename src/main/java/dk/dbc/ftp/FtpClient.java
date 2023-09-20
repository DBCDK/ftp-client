package dk.dbc.ftp;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPFileFilter;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.io.CopyStreamException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
    private Proxy proxy = Proxy.NO_PROXY;

    public enum FileType {
        ASCII(FTP.ASCII_FILE_TYPE),
        BINARY(FTP.BINARY_FILE_TYPE);

        private final int value;
        FileType(int value) {
            this.value = value;
        }
    }

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

    public FtpClient withProxy(Proxy proxy) {
        close();
        this.proxy = proxy;
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
        session.setProxy(proxy);
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
        return put(remote, inputStream, FileType.ASCII);
    }

    /**
     * Stores input from the given InputStream {@code inputStream}
     * as file on the server using name {@code remote}. This method
     * closes the given InputStream.
     * @param remote name of remote file
     * @param inputStream local InputStream from which to read content
     * @param fileType type of file to be sent
     * @return this client
     */
    public FtpClient put(String remote, InputStream inputStream, FileType fileType) {
        if (remote == null) {
            throw new NullPointerException("Parameter 'remote' in FtpClient(...) must not be null or empty");
        }
        if (remote.isEmpty()) {
            throw new IllegalArgumentException("Parameter 'remote' in FtpClient(...) must not be null or empty");
        }
        if (!isConnected()) {
            connect();
        }
        try {
            if(!session.setFileType(fileType.value)) {
                throw new FtpClientException(String.format(
                    "error setting file type to %s", fileType));
            }
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

    /**
     * Append input from the given InputStream {@code inputStream}
     * as file on the server using name {@code remote}. This method
     * closes the given InputStream.
     * @param remote name of remote file
     * @param inputStream local InputStream from which to read content
     * @param fileType type of file to be sent
     * @return this client
     */
    public FtpClient append(String remote, InputStream inputStream, FileType fileType) {
        if (remote == null) {
            throw new NullPointerException("Parameter 'remote' in FtpClient(...) must not be null or empty");
        }
        if (remote.isEmpty()) {
            throw new IllegalArgumentException("Parameter 'remote' in FtpClient(...) must not be null or empty");
        }
        if (!isConnected()) {
            connect();
        }
        try {
            if (!session.setFileType(fileType.value)) {
                throw new FtpClientException(String.format(
                        "error setting file type to %s", fileType));
            }
            session.appendFile(remote, inputStream);
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

    /**
     * Retrieves an inputstream from which the given file can be read
     * @param remote file to retrieve
     * @return inputstream
     */
    public InputStream get(String remote) {
        return get(remote, FileType.ASCII);
    }

    /**
     * Retrieves an inputstream from which the given file can be read
     * @param remote file to retrieve
     * @param fileType type of file to retrieve
     * @return inputstream
     */
    public InputStream get(String remote, FileType fileType) {
        if(!isConnected()) {
            connect();
        }
        try {
            if(!session.setFileType(fileType.value)) {
                throw new FtpClientException(String.format(
                    "error setting file type to %s", fileType));
            }
            final ByteArrayOutputStream outputStream =
                new ByteArrayOutputStream();
            session.retrieveFile(remote, outputStream);
            if(!FTPReply.isPositiveCompletion(session.getReplyCode())) {
                throw new FtpClientException(session.getReplyString());
            }
            return new ByteArrayInputStream(outputStream.toByteArray());
        } catch(IOException e) {
            throw new FtpClientException(e);
        }
    }

    /**
     * list files in a directory
     * @param directory directory to list files in
     * @param fileFilter filter on filenames
     * @return list of filenames
     */
    public List<String> list(String directory, FTPFileFilter fileFilter) {
        if(!isConnected()) {
            connect();
        }
        try {
            List<String> filenames = new ArrayList<>();
            // use listFiles instead of listNames to get filtering in the client
            for (FTPFile file : session.listFiles(directory, fileFilter)) {
                if(file != null) {
                    filenames.add(file.getName());
                }
            }
            return filenames;
        } catch(IOException e) {
            throw new FtpClientException(e);
        }
    }

    /**
     * list files in a directory
     * @param directory directory to list files in
     * @return list of filenames
     */
    public List<String> list(String directory) {
        return list(directory, file -> true);
    }

    /**
     * list files in the current directory
     * @param fileFilter filter on filenames
     * @return list of filenames
     */
    public List<String> list(FTPFileFilter fileFilter) {
        return list(null, fileFilter);
    }

    /**
     * list files in the current directory
     * @return list of filenames
     */
    public List<String> list() {
        return list(null, file -> true);
    }

    public List<FTPFile> ls() {
        if (!isConnected()) {
            connect();
        }
        try {
            List<FTPFile> files = new ArrayList<>();
            // use listFiles instead of listNames to get filtering in the client
            for (FTPFile file : session.listFiles(null, file -> true)) {
                if (file != null) {
                    files.add(file);
                }
            }
            return files;
        } catch (IOException e) {
            throw new FtpClientException(e);
        }
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
