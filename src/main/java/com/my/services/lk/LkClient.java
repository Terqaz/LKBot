package com.my.services.lk;

import com.my.exceptions.LkNotRespondingException;
import com.my.exceptions.LoginNeedsException;
import com.my.models.AuthenticationData;
import org.apache.http.HttpStatus;
import org.jsoup.Connection;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

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
        if (code == HttpStatus.SC_MOVED_TEMPORARILY || code == HttpStatus.SC_UNAUTHORIZED ||
                code == HttpStatus.SC_FORBIDDEN) {
            discardSession();
            throw new LoginNeedsException("Relogin is needed");
        }
        return response;
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
