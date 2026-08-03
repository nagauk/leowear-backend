package com.clothstore.controller;

import com.clothstore.entity.Category;
import com.clothstore.entity.Product;
import com.clothstore.repository.CategoryRepository;
import com.clothstore.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Dynamic XML sitemap for crawlers (Google, Bing, etc.). Always reflects the
 * current set of active products and categories on every request — newly added
 * products are picked up on the next crawl without rebuilding the frontend.
 *
 * <p>Anonymous-crawlable: {@code SecurityConfig} permits GET on /api/sitemap.xml.
 */
@RestController
@RequiredArgsConstructor
public class SiteMapController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Value("${sitemap.public-base-url:https://leowear.example.com}")
    private String siteBase;

    @GetMapping(value = "/api/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        String base = siteBase.replaceAll("/+$", "");
        DateTimeFormatter isoDay = DateTimeFormatter.ISO_LOCAL_DATE;

        StringBuilder sb = new StringBuilder(8192);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // Static public pages
        appendUrl(sb, base + "/",          "daily",   "1.0", null);
        appendUrl(sb, base + "/products",   "daily",   "0.9", null);
        appendUrl(sb, base + "/register",   "monthly", "0.4", null);
        appendUrl(sb, base + "/login",      "monthly", "0.3", null);

        // Active products — every active product becomes its own /products/{id} entry
        List<Product> products = productRepository.findByActiveTrue(
                PageRequest.of(0, 5000)).getContent();
        for (Product p : products) {
            String lastMod = p.getUpdatedAt() != null ? p.getUpdatedAt().toLocalDate().format(isoDay) : null;
            appendUrl(sb, base + "/products/" + p.getId(), "weekly", "0.8", lastMod);
        }

        // Categories — one entry per category for deep linking
        for (Category c : categoryRepository.findAll()) {
            appendUrl(sb, base + "/products?category=" + c.getId(), "weekly", "0.5", null);
        }

        sb.append("</urlset>\n");
        return ResponseEntity.ok()
                .header("Content-Type", "application/xml; charset=UTF-8")
                .header("Cache-Control", "public, max-age=600")
                .body(sb.toString());
    }

    private void appendUrl(StringBuilder sb, String loc, String changefreq, String priority, String lastmod) {
        sb.append("  <url>\n");
        sb.append("    <loc>").append(xmlEscape(loc)).append("</loc>\n");
        if (lastmod != null) sb.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
        sb.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        sb.append("    <priority>").append(priority).append("</priority>\n");
        sb.append("  </url>\n");
    }

    private String xmlEscape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<':  out.append("&lt;");  break;
                case '>':  out.append("&gt;");  break;
                case '&':  out.append("&amp;"); break;
                case '"':  out.append("&quot;"); break;
                case '\'': out.append("&apos;"); break;
                default:   out.append(c);
            }
        }
        return out.toString();
    }
}
