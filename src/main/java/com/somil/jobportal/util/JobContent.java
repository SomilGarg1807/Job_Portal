package com.somil.jobportal.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

public final class JobContent {
    private JobContent() { }
    public static String safeHtml(String value) {
        if (value == null) return "";
        return Jsoup.clean(value, "", Safelist.basic().addTags("h2", "h3", "h4"),
                new Document.OutputSettings().prettyPrint(false));
    }
    public static String plainText(String value) {
        return Jsoup.parse(value == null ? "" : value).text();
    }
}
