package fr.eimonku.anki.paysdumonde;

import static java.lang.String.format;
import static java.util.Locale.FRENCH;
import static java.util.regex.Pattern.compile;

import java.text.Collator;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import fr.eimonku.wikimedia.WikimediaCache;

public class ListeDesCapitalesDuMonde {
	private final WikimediaCache cache;
	private final Map<String, SortedSet<String>> capitalNamesByWikipediaCanonicalUrls = new HashMap<>();

	public ListeDesCapitalesDuMonde(WikimediaCache cache) {
		this.cache = cache;

		final Element table = cache.get("https://fr.wikipedia.org/wiki/Liste_des_capitales_du_monde")
		    .select("h2:has(#Liste_principale)~table").first();

		if (table != null) {
			processTable(table);
		} else {
			throw new RuntimeException("unable to find #Liste_principale");
		}
	}

	public Stream<String> capitalNamesForWikipediaCanonicalUrl(String wikipediaCanonicalUrl) {
		final SortedSet<String> capitalNames = capitalNamesByWikipediaCanonicalUrls.get(wikipediaCanonicalUrl);
		if (capitalNames != null) {
			return capitalNames.stream();
		}

		throw new RuntimeException(format("no capital name for '%s'", wikipediaCanonicalUrl));
	}

	private void processTable(Element table) {
		table.children().select("tbody").forEach(tbody -> tbody.children().select("tr").forEach(tr -> {
			final Element td0 = tr.child(0), td1 = tr.child(1);
			if ("td".equals(td0.tagName()) && "td".equals(td1.tagName())) {
				final String url = cache.get(td1.children().select("a").get(1).absUrl("href")).baseUri();
				capitalNamesByWikipediaCanonicalUrls.computeIfAbsent(url, k -> new TreeSet<>(Collator.getInstance(FRENCH)))
		        .add(fullName(td0));
			}
		}));
	}

	private static final Pattern NAME_PATTERN = compile("\\(capitale ([^)]+)\\)");

	private static String fullName(Element td) {
		final StringBuilder sb = new StringBuilder();
		appendText(td, sb);

		return NAME_PATTERN.matcher(sb).replaceFirst("($1)");
	}

	private static void appendText(Node n, StringBuilder sb) {
		if (n instanceof TextNode) {
			sb.append(((TextNode) n).text());
		} else if (n instanceof Element) {
			final Element el = (Element) n;
			if (!el.hasClass("reference")) {
				el.childNodes().forEach(childNode -> appendText(childNode, sb));
			}
		}
	}
}
