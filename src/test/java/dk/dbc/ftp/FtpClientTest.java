/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GPLv3
 * See license text in LICENSE.txt
 */

package dk.dbc.ftp;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.DirectoryEntry;
import org.mockftpserver.fake.filesystem.FileEntry;
import org.mockftpserver.fake.filesystem.FileSystem;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class FtpClientTest {
    private static final String USERNAME = "FtpClientTest";
    private static final String PASSWORD = "FtpClientTestPass";
    private static final String HOME_DIR = "/home/ftp";
    private static final String PUT_DIR = "put";

    private static FakeFtpServer fakeFtpServer;

    @BeforeAll
    static void startFakeFtpServer() {
        fakeFtpServer = new FakeFtpServer();
        fakeFtpServer.setServerControlPort(0);  // use any free port
        fakeFtpServer.addUserAccount(new UserAccount(USERNAME, PASSWORD, HOME_DIR));

        final FileSystem fileSystem = new UnixFakeFileSystem();
        fileSystem.add(new DirectoryEntry(HOME_DIR));
        fileSystem.add(new DirectoryEntry(pathJoin(HOME_DIR, PUT_DIR)));
        fakeFtpServer.setFileSystem(fileSystem);

        fakeFtpServer.start();
    }

    @BeforeEach
    void resetFileSystem() {
        final FileSystem fileSystem = new UnixFakeFileSystem();
        fileSystem.add(new DirectoryEntry(HOME_DIR));
        fileSystem.add(new DirectoryEntry(pathJoin(HOME_DIR, PUT_DIR)));
        fakeFtpServer.setFileSystem(fileSystem);
    }
    
    @AfterAll
    static void stopFakeFtpServer() {
        fakeFtpServer.stop();
    }

    @Test
    void putString() {
        final String filename = "put_string_with_remote.txt";
        final String fileContent = "testing put string";

        new FtpClient()
                .withHost("localhost")
                .withPort(fakeFtpServer.getServerControlPort())
                .withUsername(USERNAME)
                .withPassword(PASSWORD)
                .cd(PUT_DIR)
                .put(filename, fileContent)
                .close();

        assertThat(getRemoteFileContent(pathJoin(HOME_DIR, PUT_DIR, filename)),
                is(fileContent));
    }

    @Test
    void putFile() {
        final String filename = "put_file_with_remote.txt";
        final String fileContent = "testing put file";

        new FtpClient()
                .withHost("localhost")
                .withPort(fakeFtpServer.getServerControlPort())
                .withUsername(USERNAME)
                .withPassword(PASSWORD)
                .cd(PUT_DIR)
                .put(filename, Paths.get("src/test/resources/put_file.txt"))
                .close();

        assertThat(getRemoteFileContent(pathJoin(HOME_DIR, PUT_DIR, filename)),
                is(fileContent));
    }

    @Test
    void putFileWithoutRemote() {
        final String filename = "put_file.txt";
        final String fileContent = "testing put file";

        new FtpClient()
                .withHost("localhost")
                .withPort(fakeFtpServer.getServerControlPort())
                .withUsername(USERNAME)
                .withPassword(PASSWORD)
                .cd(PUT_DIR)
                .put(Paths.get("src/test/resources/put_file.txt"))
                .close();

        assertThat(getRemoteFileContent(pathJoin(HOME_DIR, PUT_DIR, filename)),
                is(fileContent));
    }

    @Test
    void putInputStream() {
        final String filename = "put_inputstream_with_remote.txt";
        final String fileContent = "testing put file";

        final InputStream is = getClass().getResourceAsStream("/put_file.txt");
        new FtpClient()
                .withHost("localhost")
                .withPort(fakeFtpServer.getServerControlPort())
                .withUsername(USERNAME)
                .withPassword(PASSWORD)
                .cd(PUT_DIR)
                .put(filename, is)
                .close();

        assertThat(getRemoteFileContent(pathJoin(HOME_DIR, PUT_DIR, filename)),
                is(fileContent));
    }

    @Test
    void putStringRemoteNull() {
        final String filename = null;
        final String fileContent = "testing put string";

        assertThrows(NullPointerException.class, () -> {
            new FtpClient()
                .withHost("localhost")
                .withPort(fakeFtpServer.getServerControlPort())
                .withUsername(USERNAME)
                .withPassword(PASSWORD)
                .cd(PUT_DIR)
                .put(filename, fileContent)
                .close();
        });
    }

    @Test
    void putStringRemoteEmpty() {
        final String filename = "";
        final String fileContent = "testing put string";

        assertThrows(IllegalArgumentException.class, () -> {
            new FtpClient()
                .withHost("localhost")
                .withPort(fakeFtpServer.getServerControlPort())
                .withUsername(USERNAME)
                .withPassword(PASSWORD)
                .cd(PUT_DIR)
                .put(filename, fileContent)
                .close();
        });
    }

    @Test
    void get() throws IOException {
        final String[] putFiles = new String[] {
            "src/test/resources/put_file.txt",
            "src/test/resources/put_another_file.txt"};
        final FtpClient ftpClient = new FtpClient()
            .withHost("localhost")
            .withPort(fakeFtpServer.getServerControlPort())
            .withUsername(USERNAME)
            .withPassword(PASSWORD)
            .cd(PUT_DIR);
        for(String path : putFiles) {
            ftpClient.put(Paths.get(path));
        }

        ArrayList<InputStream> inputStreamList = new ArrayList<>();
        inputStreamList.add(ftpClient.get("put_file.txt"));
        inputStreamList.add(ftpClient.get("put_another_file.txt"));

        assertThat("inputstream 1 isn't null", inputStreamList.get(0),
            is(notNullValue()));
        assertThat("inputstream 2 isn't null", inputStreamList.get(1),
            is(notNullValue()));
        assertThat("read inputstream 1", readInputString(
            inputStreamList.get(0)), is("testing put file"));
        assertThat("read inputstream 2", readInputString(
            inputStreamList.get(1)), is(
            "\"I wumbo, you wumbo, he-she-me wumbo.\n" +
            "Wumboing, wumbology, the study of wumbo!\n" +
            "It’s first grade Spongebob\""));

        ftpClient.close();
    }

    @Test
    void get_largeFile() throws IOException {
        /* this test ensures that the ftp client works with "large" files.
         * the retrieveFileStream method from the apache ftp client doesn't
         * seem to work with files larger than a few megabytes.
         */
        final int fileSize = 8388608; // eight megabytes
        final byte[] randomBytes = new byte[fileSize];
        new Random().nextBytes(randomBytes);
        final ByteArrayInputStream is = new ByteArrayInputStream(randomBytes);
        final FtpClient ftpClient = new FtpClient()
            .withHost("localhost")
            .withPort(fakeFtpServer.getServerControlPort())
            .withUsername(USERNAME)
            .withPassword(PASSWORD)
            .cd(PUT_DIR);
        ftpClient.put("musclebob_buffpants.gif", is, FtpClient.FileType.BINARY);

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final InputStream inputStream = ftpClient.get(
            "musclebob_buffpants.gif", FtpClient.FileType.BINARY);
        final byte[] buffer = new byte[1024];
        int read;
        while((read = inputStream.read(buffer, 0, buffer.length)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        outputStream.flush();
        byte[] output = outputStream.toByteArray();

        ftpClient.close();

        assertThat(output, is(randomBytes));
    }

    @Test
    void get_noFileFound() {
        final FtpClient ftpClient = new FtpClient()
            .withHost("localhost")
            .withPort(fakeFtpServer.getServerControlPort())
            .withUsername(USERNAME)
            .withPassword(PASSWORD)
            .cd(PUT_DIR);
        try {
            ftpClient.get("does_not_exist.txt");
            fail("expected ftpclient exception");
        } catch(FtpClientException e) {}
        finally {
            ftpClient.close();
        }
    }

    @Test
    void list_currentDirectory() {
        final String[] putFiles = new String[] {
            "src/test/resources/put_file.txt",
            "src/test/resources/put_another_file.txt"};
        final FtpClient ftpClient = new FtpClient()
            .withHost("localhost")
            .withPort(fakeFtpServer.getServerControlPort())
            .withUsername(USERNAME)
            .withPassword(PASSWORD)
            .cd(PUT_DIR);
        for(String path : putFiles) {
            ftpClient.put(Paths.get(path));
        }

        List<String> filenames = ftpClient.list();
        assertThat("list size", filenames.size(), is(2));
        assertThat("filename 1", filenames.get(0), is("put_another_file.txt"));
        assertThat("filename 2", filenames.get(1), is("put_file.txt"));
    }

    @Test
    void list_filteredCurrentDirectory() {
        final String[] putFiles = new String[] {
            "src/test/resources/put_file.txt",
            "src/test/resources/put_another_file.txt"};
        final FtpClient ftpClient = new FtpClient()
            .withHost("localhost")
            .withPort(fakeFtpServer.getServerControlPort())
            .withUsername(USERNAME)
            .withPassword(PASSWORD)
            .cd(PUT_DIR);
        for(String path : putFiles) {
            ftpClient.put(Paths.get(path));
        }

        List<String> filenames = ftpClient.list(
            file -> file != null && file.getName().contains("another"));
        assertThat("list size", filenames.size(), is(1));
        assertThat("filename", filenames.get(0), is("put_another_file.txt"));
    }

    @Test
    void list_filtered() {
        final String[] putFiles = new String[] {
            "src/test/resources/put_file.txt",
            "src/test/resources/put_another_file.txt"};
        final FtpClient ftpClient = new FtpClient()
            .withHost("localhost")
            .withPort(fakeFtpServer.getServerControlPort())
            .withUsername(USERNAME)
            .withPassword(PASSWORD)
            .cd(PUT_DIR);
        for(String path : putFiles) {
            ftpClient.put(Paths.get(path));
        }
        ftpClient.cd(HOME_DIR);

        List<String> filenames = ftpClient.list(PUT_DIR,
            file -> file != null && file.getName().contains("another"));
        assertThat("list size", filenames.size(), is(1));
        assertThat("filename", filenames.get(0), is("put_another_file.txt"));
    }

    @Test
    void list() {
        final String[] putFiles = new String[] {
            "src/test/resources/put_file.txt",
            "src/test/resources/put_another_file.txt"};
        final FtpClient ftpClient = new FtpClient()
            .withHost("localhost")
            .withPort(fakeFtpServer.getServerControlPort())
            .withUsername(USERNAME)
            .withPassword(PASSWORD)
            .cd(PUT_DIR);
        for(String path : putFiles) {
            ftpClient.put(Paths.get(path));
        }
        ftpClient.cd(HOME_DIR);

        List<String> filenames = ftpClient.list(PUT_DIR);
        assertThat("list size", filenames.size(), is(2));
        assertThat("filename 1", filenames.get(0), is("put_another_file.txt"));
        assertThat("filename 2", filenames.get(1), is("put_file.txt"));
    }

    private static String readInputString(InputStream is) throws IOException {
        try(final BufferedReader in = new BufferedReader(
                new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while((line = in.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString().trim();
        }
    }

    private static String getRemoteFileContent(String remoteFilePath) {
        final FileEntry fileEntry = ((FileEntry)  fakeFtpServer.getFileSystem()
                .getEntry(remoteFilePath));
        try {
            return IOUtils.toString(fileEntry.createInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String pathJoin(String... pathElements) {
        return String.join("/", pathElements);
    }
}
