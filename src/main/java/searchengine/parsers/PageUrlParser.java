package searchengine.parsers;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.dto.statistics.PageDto;
import searchengine.utils.RandomUserAgent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RecursiveTask;

public class PageUrlParser extends RecursiveTask<List<PageDto>> {
    private final String url;
    private static final CopyOnWriteArrayList<String> WRITE_ARRAY_LIST = new CopyOnWriteArrayList<>();
    private final List<String> urlList;
    private final List<PageDto> pageDtoList;
    private static final String CSS_QUERY = "a[href]";
    private static final String ATTRIBUTE_KEY = "href";

    public PageUrlParser(String url, List<PageDto> pageDtoList, List<String> urlList) {
        this.url = url.trim();
        this.pageDtoList = pageDtoList;
        this.urlList = urlList;
    }

    @Override
    protected List<PageDto> compute() {
        try {
            Connection.Response response = Jsoup.connect(url)
                    .ignoreContentType(true)
                    .userAgent(RandomUserAgent.getRandomUserAgent())
                    .execute();
            Document document = response.parse();
            String html = document.outerHtml();
            int status = response.statusCode();
            PageDto pageDto = new PageDto(url, html, status);
            pageDtoList.add(pageDto);
            Elements elements = document.select(CSS_QUERY);
            List<PageUrlParser> taskList = new ArrayList<>();
            for (Element element : elements) {
                String attributeUrl = element.absUrl(ATTRIBUTE_KEY);
                if (!attributeUrl.isEmpty() && attributeUrl.startsWith(url) && !attributeUrl.contains("#") &&
                        !attributeUrl.contains(".pdf") && !attributeUrl.contains(".jpg") && !attributeUrl.contains(".JPG")
                        && !attributeUrl.contains(".png") && !WRITE_ARRAY_LIST.contains(attributeUrl) && !urlList.contains(attributeUrl)) {

                    WRITE_ARRAY_LIST.add(attributeUrl);
                    urlList.add(attributeUrl);
                    PageUrlParser task = new PageUrlParser(attributeUrl, pageDtoList, urlList);
                    task.fork();
                    taskList.add(task);
                }
            }
            taskList.sort(Comparator.comparing(PageUrlParser::getUrl));
            int i = 0;
            int allTasksSize = taskList.size();
            while (i < allTasksSize) {
                PageUrlParser task = taskList.get(i);
                task.join();
                i++;
            }
        } catch (Exception e) {
            PageDto pageDto = new PageDto(url, "", 500);
            pageDtoList.add(pageDto);
        }

        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return pageDtoList;
    }

    public String getUrl() {
        return url;
    }
}