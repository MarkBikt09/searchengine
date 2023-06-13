package searchengine.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

public class ClearHtmlCode {

    private ClearHtmlCode() {}

    public static String clear(String content, String selector) {
        StringBuilder html = new StringBuilder();
        var doc = Jsoup.parse(content);
        var elements = doc.select(selector);
        for (Element el : elements) {
            html.append(el.html());
        }
        return Jsoup.parse(html.toString()).text();
    }
}
