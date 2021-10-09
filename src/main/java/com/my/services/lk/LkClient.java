package com.my.services.lk;

import com.my.exceptions.FileLoadingException;
import com.my.exceptions.LkNotRespondingException;
import com.my.exceptions.LoginNeedsException;
import com.my.models.AuthenticationData;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;
import org.jsoup.Connection;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;

public class LkClient {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:89.0) Gecko/20100101 Firefox/89.0";
    private static String phpSessId = null;

    public LkClient() {}

    public synchronized boolean login (AuthenticationData data) {
        if (isNotLoggedIn()) {
            try {
                return auth(data.getLogin(), data.getPassword());
            } catch (LkNotRespondingException e) {
                throw e;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        } else return true;
    }

    private boolean auth (String login, String password) throws IOException {
        final var firstResponse = openLoginPage();

        String phpSessId = firstResponse.header("Set-Cookie")
                .split(";")[0]
                .split("=")[1];

        final var document1 = firstResponse.parse();
        String sessId = document1.select("input[name=\"sessid\"]")
                .get(0)
                .attr("value");
        this.phpSessId = phpSessId;

        final Document document = post(
                LkUrlBuilder.buildAuthUrl(login, password, sessId));

        final String jsonResponse = document.body().text();
        if (!jsonResponse.startsWith("{\"SUCCESS\":\"1\"")) {
            discardSession();
            return false;
        } else return true;
    }

    public void keepAuth () {
        loggedPost(LkUrlBuilder.buildKeepAuthUrl());
    }

    public void logout () {
        try {
            post(LkUrlBuilder.buildLogoutUrl());
        } catch (Exception ignored) {}
        finally {
            discardSession();
        }
    }

    private Connection getOriginConnection () {
        return Jsoup.connect("http://lk.stu.lipetsk.ru/")
                .userAgent(USER_AGENT)
                .header("Accept", "text/html")
                .header("Connection", "Keep-alive");
    }

    private Connection getOriginSessionConnection(String url) {
        if (isNotLoggedIn())
            throw new LoginNeedsException("Relogin is needed");

        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Accept", "*/*")
                .header("Accept-Encoding", "gzip, deflate")
                .header("Connection", "Keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .cookie("PHPSESSID", phpSessId);
    }

    public Response openLoginPage () {
        try {
            return getOriginConnection().method(Connection.Method.GET).execute();
        } catch (ConnectException | SocketTimeoutException e) {
            throw new LkNotRespondingException();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public File loadFileTo(String fileDir, URL inputUrl) {
        try {
            final var httpTransportClient = new HttpTransportClient();
            HttpURLConnection connection = (HttpURLConnection) inputUrl.openConnection();
            connection.addRequestProperty("User-Agent", USER_AGENT);
            connection.addRequestProperty("Accept", "*/*");
            connection.addRequestProperty("Accept-Encoding", "gzip, deflate");
            connection.addRequestProperty("Connection", "Keep-alive");
            connection.addRequestProperty("Upgrade-Insecure-Requests", "1");
            connection.addRequestProperty("Cookie", "PHPSESSID=" + phpSessId);

            connection.setReadTimeout(40 * 1000); //40 секунд
            connection.connect();

            if (isNotAuthorized(connection.getResponseCode())) {
                discardSession();
                throw new LoginNeedsException("Relogin is needed");

            } else if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                throw new FileLoadingException("Server returned HTTP " + connection.getResponseCode()
                        + " " + connection.getResponseMessage());

            final var contentLength = connection.getContentLength();
//            final var dir = Paths.get(fileDir);
//            System.out.println(dir.toAbsolutePath());
//            Files.createDirectories(dir);
            File newFile = new File(fileDir + "\\" + getFileNameFromConnection(connection));
//            newFile.createNewFile();
            FileUtils.copyInputStreamToFile(connection.getInputStream(), newFile);
            System.out.println("content length:" + contentLength + ";file length: " + newFile.length());
            System.out.println(newFile.getAbsolutePath());
//            try (FileOutputStream fileOutputStream = new FileOutputStream(newFile)) {
//                final var readableByteChannel = Channels.newChannel(connection.getInputStream());
//                fileOutputStream.getChannel().transferFrom(
//                        readableByteChannel, 0, Long.MAX_VALUE);
//            }
            connection.disconnect();
            return newFile;

        } catch (ConnectException | SocketTimeoutException e) {
            throw new LkNotRespondingException();
        } catch (IOException e) {
            e.printStackTrace();
        }
        throw new FileLoadingException("Unknown exception while file loading");
    }

    private String getFileNameFromConnection(HttpURLConnection connection) {
        //Content-disposition: inline; filename="file.pdf"
        final var contentDisposition = connection.getHeaderField("Content-disposition");
        if (contentDisposition == null)
            throw new LoginNeedsException("Relogin is needed");

        final var value = contentDisposition.split("=")[1];
        return value.substring(1, value.length()-1); // Убрали кавычки
    }

    public Document loggedGet(String url) {
        try {
            return executeSessionRequest(getOriginSessionConnection(url)
                    .method(Connection.Method.GET)).parse();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Document loggedPost(String url) {
        try {
            return executeSessionRequest(getOriginSessionConnection(url)
                    .method(Connection.Method.POST)).parse();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Document get(String url) {
        try {
            return executeRequest(getOriginSessionConnection(url)
                    .method(Connection.Method.POST)).parse();
        } catch (LkNotRespondingException e) {
            throw e;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Document post(String url) {
        try {
            return executeRequest(getOriginSessionConnection(url)
                    .method(Connection.Method.POST)).parse();
        } catch (LkNotRespondingException e) {
            throw e;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Response executeSessionRequest(Connection connection) {
        Response response = executeRequest(connection);

        final int code = response.statusCode();
        if (isNotAuthorized(code)) {
            discardSession();
            throw new LoginNeedsException("Relogin is needed");
        }
        return response;
    }

    private boolean isNotAuthorized(int code) {
        return code == HttpStatus.SC_MOVED_TEMPORARILY || code == HttpStatus.SC_UNAUTHORIZED ||
                code == HttpStatus.SC_FORBIDDEN;
    }

    private Response executeRequest(Connection connection) {
        for (int triesCount = 5; triesCount > 0; triesCount--) {
            try {
                return connection.execute();
            } catch (ConnectException | SocketTimeoutException ignored) {
                continue;
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }
        }
        throw new LkNotRespondingException();
    }

    public boolean isNotLoggedIn() {
        return phpSessId == null;
    }

    private void discardSession() {
        phpSessId = null;
    }
}
