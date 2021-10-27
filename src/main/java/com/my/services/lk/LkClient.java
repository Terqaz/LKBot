package com.my.services.lk;

import com.ibm.icu.text.Transliterator;
import com.my.TextUtils;
import com.my.exceptions.AuthenticationException;
import com.my.exceptions.FileLoadingException;
import com.my.exceptions.LkNotRespondingException;
import com.my.exceptions.LoginNeedsException;
import com.my.models.AuthenticationData;
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
import java.util.zip.GZIPInputStream;

public class LkClient {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:92.0) Gecko/20100101 Firefox/92.0";
    public static final String RELOGIN_IS_NEEDED = "Login or relogin needs";
    private String phpSessId = null;

    private static final Transliterator toLatinTransliterator = Transliterator.getInstance("Russian-Latin/BGN");

    public LkClient() {}

    // Гарантирует логин, даже если токен сессии прросрочен
    public synchronized void login (AuthenticationData data) {
        if (!keepAuth())
            discardSession();

        if (isSessionDiscarded()) {
            try {
                auth(data.getLogin(), data.getPassword());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void auth (String login, String password) throws IOException {
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
            throw new AuthenticationException("Wrong credentials");
        }
    }

    // Return that session not expired
    public boolean keepAuth () {
        final Response response;
        try {
            response = loggedPostResponse(LkUrlBuilder.buildKeepAuthUrl());
        } catch (LoginNeedsException e) {
            return false;
        }
        return !response.body().equals("/");
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
        if (isSessionDiscarded())
            throw new LoginNeedsException(RELOGIN_IS_NEEDED);

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
            HttpURLConnection connection = (HttpURLConnection) inputUrl.openConnection();
            connection.addRequestProperty("User-Agent", USER_AGENT);
            connection.addRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
            connection.addRequestProperty("Accept-Language", "ru-RU,ru;q=0.8,en-US;q=0.5,en;q=0.3");
            connection.addRequestProperty("Accept-Encoding", "gzip, deflate");
            connection.addRequestProperty("Connection", "Keep-alive");
            connection.addRequestProperty("DNT", "1");
            connection.addRequestProperty("Upgrade-Insecure-Requests", "1");
            connection.addRequestProperty("Cookie", "PHPSESSID=" + phpSessId);

            connection.connect();

            if (isNotAuthorized(connection.getResponseCode())) {
                discardSession();
                throw new LoginNeedsException(RELOGIN_IS_NEEDED);

            } else if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                throw new FileLoadingException("Server returned HTTP " + connection.getResponseCode()
                        + " " + connection.getResponseMessage());

            File newFile = new File(fileDir + "\\" + getFileNameFromConnection(connection));
            FileUtils.copyInputStreamToFile(new GZIPInputStream(connection.getInputStream()), newFile);
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
            throw new LoginNeedsException(RELOGIN_IS_NEEDED);

        var value = contentDisposition.split("=")[1];
        value = value.substring(1, value.length()-1);
        value = TextUtils.changeEncodingIso_8859_1_Windows_1251(value);
        return toLatinTransliterator.transliterate(value); // Убрали кавычки
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
            return loggedPostResponse(url).parse();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Response loggedPostResponse(String url) {
        return executeSessionRequest(getOriginSessionConnection(url)
                .method(Connection.Method.POST));
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
            throw new LoginNeedsException(RELOGIN_IS_NEEDED);
        }
        return response;
    }

    private boolean isNotAuthorized(int code) {
        return code == HttpStatus.SC_MOVED_TEMPORARILY || code == HttpStatus.SC_UNAUTHORIZED ||
                code == HttpStatus.SC_FORBIDDEN;
    }

    private Response executeRequest(Connection connection) {
        for (int triesCount = 10; triesCount > 0; triesCount--) {
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

    public boolean isSessionDiscarded() {
        return phpSessId == null;
    }

    private void discardSession() {
        phpSessId = null;
    }
}
