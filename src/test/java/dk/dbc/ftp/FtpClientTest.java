/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GPLv3
 * See license text in LICENSE.txt
 */

package dk.dbc.ftp;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.DirectoryEntry;
import org.mockftpserver.fake.filesystem.FileEntry;
import org.mockftpserver.fake.filesystem.FileSystem;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
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
