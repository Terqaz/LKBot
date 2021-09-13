package com.my.services.lstu;

import com.my.exceptions.AuthenticationException;
import com.my.exceptions.LkNotRespondingException;
import org.jsoup.Connection;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

public class LstuClient {

    private static LstuClient instance = null;

    public static LstuClient getInstance () {
        if (instance == null)
            instance = new LstuClient();
        return instance;
    }

    private LstuClient () {}

    public static final String LOGGED_IN_BEFORE = "You must be logged in before";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:89.0) Gecko/20100101 Firefox/89.0";
    private static String sessId = null;
    private static String phpSessId = null;

    public void updateSessionTokens (String sessId, String phpSessId) {
        this.sessId = sessId;
        this.phpSessId = phpSessId;
    }

    public boolean isNotLoggedIn() {
        return (sessId == null || phpSessId == null);
    }

    private Connection getOriginConnection () {
        return Jsoup.connect("http://lk.stu.lipetsk.ru/")
                .userAgent(USER_AGENT)
                .header("Accept", "text/html")
                .header("Connection", "Keep-alive");
    }

    private Connection getOriginSessionConnection(String url) {
        if (isNotLoggedIn()) {
            throw new AuthenticationException(LOGGED_IN_BEFORE);
        }
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

    public Document executeLoginRequest (String authUrl) {
        return post(authUrl);
    }

    public void executeLogoutRequest (String logoutUrl) {
        post(logoutUrl);
        sessId = null;
        phpSessId = null;
    }

    public Document get (String url) {
        return executeRequest(getOriginSessionConnection(url)
                .method(Connection.Method.GET));
    }

    public Document post (String url) {
        return executeRequest(getOriginSessionConnection(url)
                .method(Connection.Method.POST));
    }

    private Document executeRequest(Connection connection) {
        for (int triesCount = 3; triesCount > 0; triesCount--) {
            try {
                return connection.execute().parse();
            } catch (ConnectException | SocketTimeoutException ignored) {
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        throw new LkNotRespondingException();
    }
}
