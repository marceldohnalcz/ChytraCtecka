package com.example.smartreader

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Stáhne webovou stránku a pokusí se z ní vytáhnout hlavní čitelný text
 * (podobně jako "Reader mode"/"Simplified view" v prohlížeči). Funguje
 * spolehlivě u klasických webů/novinových článků, které posílají HTML
 * rovnou ze serveru.
 *
 * Nejde o stejný algoritmus jako Chrome/Readability.js (to by vyžadovalo
 * spouštět JavaScript a mnohem složitější skórování), ale o sadu praxí
 * ověřených heuristik: nejdřív zkusit sémantické/typické kontejnery
 * článku, odstranit zjevný "šum" (reklamy, komentáře, menu, newsletter...)
 * a teprve pak padat na obecnější fallbacky.
 *
 * POZOR: U webů, které obsah dotahují přes JavaScript (typicky Facebook
 * a Instagram příspěvky), toto nemusí fungovat - Jsoup JavaScript nespouští
 * a tyto platformy navíc obsah bez přihlášení silně omezují kvůli ochraně
 * proti scrapování. V takovém případě je spolehlivější v dané appce text
 * přímo označit a použít "Chytrá čtečka textu" z nabídky při výběru textu.
 */
object WebArticleExtractor {

    // Typické "šumové" prvky, které nechceme číst nahlas
    private const val CLUTTER_SELECTOR =
        "script, style, nav, footer, header, form, noscript, iframe, aside, " +
            "[class*=comment], [id*=comment], [class*=related], [class*=share], " +
            "[class*=social], [class*=newsletter], [class*=cookie], [class*=advert], " +
            "[class*=banner], [class*=sidebar], [class*=promo], [class*=subscribe], " +
            "[role=complementary], [role=navigation]"

    // Běžné třídy/atributy, kterými weby označují samotné tělo článku
    private const val ARTICLE_BODY_SELECTOR =
        "[itemprop=articleBody], [class*=article-body], [class*=articleBody], " +
            "[class*=post-content], [class*=entry-content], [class*=story-body], " +
            "[class*=content-body], #article-body"

    fun extractText(url: String): String? {
        return try {
            val doc: Document = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Android) SmartReader/1.0")
                .timeout(15000)
                .get()

            doc.select(CLUTTER_SELECTOR).remove()

            // 1) Typické kontejnery těla článku podle třídy/atributu
            val articleBody = doc.select(ARTICLE_BODY_SELECTOR).text()
            if (articleBody.length > 200) return articleBody

            // 2) Sémantický <article>
            val article = doc.select("article").text()
            if (article.length > 200) return article

            // 3) <main>
            val main = doc.select("main").text()
            if (main.length > 200) return main

            // 4) Nasbírat delší odstavce <p>
            val paragraphs = doc.select("p")
                .map { it.text() }
                .filter { it.length > 40 }
            if (paragraphs.isNotEmpty()) {
                return paragraphs.joinToString("\n\n")
            }

            // 5) Fallback na og:description / meta description
            //    (u příspěvků na Facebooku/Instagramu bývá jediné, co jde bez
            //    JavaScriptu vůbec získat - obvykle jen krátký úryvek/popisek)
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
