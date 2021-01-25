package com.my;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jsoup.Connection;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class LstuParser {

    private static final CloseableHttpClient httpClient = HttpClients.createDefault();
    private final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:84.0) Gecko/20100101 Firefox/84.0";
    private String sessId;
    private String phpSessId;

    private void auth (String oldSessId, String oldPhpSessId) {

    }

    public void auth () throws IOException {
        final Response firstResponse = Jsoup.connect("http://lk.stu.lipetsk.ru/")
                .userAgent(USER_AGENT)
                .header("Accept", "text/html")
                .header("Connection", "keep-alive")
                .method(Connection.Method.POST)
                .execute();

        final String headerValue = firstResponse.header("Set-Cookie");
        int sessIdEnd = headerValue.indexOf(';');
        phpSessId = headerValue.substring(10, sessIdEnd);

        Document document1 = firstResponse.parse();
        sessId = document1.select("input[name=\"sessid\"]")
                .get(0)
                .attr("value");

        final Response response = Jsoup.connect("http://lk.stu.lipetsk.ru/index.php?AUTH_FORM=1&sessid=" + sessId + "&LOGIN=s11916327&PASSWORD=f7LLDSJibCw8QNGeR6")
                .userAgent(USER_AGENT)
                .header("Accept", "text/html")
                .header("Connection", "keep-alive")
                .cookie("PHPSESSID", phpSessId)
                .method(Connection.Method.POST)
                .execute();
        System.out.println(response.body());
    }

    public List<Semester> getSemestersData () throws IOException {

        Document document = Jsoup.connect("http://lk.stu.lipetsk.ru/education/0/")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:84.0) Gecko/20100101 Firefox/84.0")
                .header("Accept","text/html")
                .header("Connection", "keep-alive")
                .cookie("PHPSESSID", phpSessId)
                .get();

        final Elements elements = document.select("ul.ul-main > li");
        System.out.println(elements);
        return Arrays.asList();
    }
}
