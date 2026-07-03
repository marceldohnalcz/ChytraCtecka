package com.example.smartreader

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Stáhne webovou stránku a pokusí se z ní vytáhnout hlavní čitelný text
 * (podobně jako "Reader mode" v prohlížeči). Funguje spolehlivě u
 * klasických webů/novinových článků, které posílají HTML rovnou ze serveru.
 *
 * POZOR: U webů, které obsah dotahují přes JavaScript (typicky Facebook
 * a Instagram příspěvky), toto nemusí fungovat - Jsoup JavaScript nespouští.
 * V takovém případě je lepší v dané appce text přímo označit a použít
 * funkci "Zpracovat text" (ACTION_PROCESS_TEXT).
 */
object WebArticleExtractor {

    fun extractText(url: String): String? {
        return try {
            val doc: Document = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Android) SmartReader/1.0")
                .timeout(15000)
                .get()

            doc.select("script, style, nav, footer, header, form, noscript, iframe").remove()

            // 1) Zkusit sémantický <article>
            val article = doc.select("article").text()
            if (article.length > 200) return article

            // 2) Zkusit <main>
            val main = doc.select("main").text()
            if (main.length > 200) return main

            // 3) Nasbírat delší odstavce <p>
            val paragraphs = doc.select("p")
                .map { it.text() }
                .filter { it.length > 40 }
            if (paragraphs.isNotEmpty()) {
                return paragraphs.joinToString("\n\n")
            }

            // 4) Fallback na og:description / meta description
            val ogDesc = doc.select("meta[property=og:description]").attr("content")
            if (ogDesc.length > 20) return ogDesc

            val metaDesc = doc.select("meta[name=description]").attr("content")
            if (metaDesc.length > 20) return metaDesc

            null
        } catch (e: Exception) {
            null
        }
    }
}
