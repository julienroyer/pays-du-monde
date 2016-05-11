package fr.eimonku.anki.paysdumonde;

import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.text.Normalizer.normalize;
import static java.text.Normalizer.Form.NFD;
import static java.util.Locale.FRENCH;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.joining;

import java.text.Collator;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import fr.eimonku.wikimedia.WikimediaCache;
import fr.eimonku.wikimedia.WikimediaResources;

public class ListeDesPaysDuMonde {
	private final WikimediaCache cache;
	private final WikimediaResources resources;
	private final ListeDesCapitalesDuMonde listeDesCapitalesDuMonde;
	private final OrthographicProjectionsMaps orthographicProjectionsMaps;

	public ListeDesPaysDuMonde(WikimediaCache cache, WikimediaResources resources) {
		this.cache = cache;
		this.resources = resources;
		listeDesCapitalesDuMonde = new ListeDesCapitalesDuMonde(cache);
		orthographicProjectionsMaps = new OrthographicProjectionsMaps(cache);
	}

	public void forEach(Consumer<State> action) {
		final Element firstSibling = cache.get("https://fr.wikipedia.org/wiki/Liste_des_pays_du_monde")
		    .select("h3:has(#Liste_principale)+*").first();
		if (firstSibling != null) {
			processFirstSibling(firstSibling, action);
		} else {
			throw new RuntimeException("unable to find #Liste_principale");
		}
	}

	private void processFirstSibling(Element firstSibling, Consumer<State> action) {
		final SortedMap<String, State> states = new TreeMap<>(Collator.getInstance(FRENCH));

		for (Element el = firstSibling; el != null
		    && titleLevel(el).map(level -> (level > 3)).orElse(true); el = el.nextElementSibling()) {
			if ("table".equals(el.tagName())) {
				processTable(el, state -> states.put(state.name, state));
			}
		}

		states.values().forEach(action);
	}

	private void processTable(Element table, Consumer<State> action) {
		table.children().select("tbody").forEach(tbody -> tbody.children().select("tr").forEach(tr -> {
			final Element td = tr.child(1);
			if ("td".equals(td.tagName())) {
				final Document document = cache.get(td.children().select("a").first().absUrl("href"));
				final String name = name(document);
				final String fileName = fileName(name);

				final Document enDocument = cache.get(document.select("li.interwiki-en a").first().absUrl("href"));
				final String enName = name(enDocument);

				action
		        .accept(new State(name,
		            listeDesCapitalesDuMonde.capitalNamesForWikipediaCanonicalUrl(document.baseUri())
		                .collect(joining(", ")),
		            map(enName, document, enDocument, fileName), flag(document, fileName), gentile(document),
		            internetDomain(document), enName));
			}
		}));
	}

	private String map(String enName, Document frDocument, Document enDocument, String fileName) {
		return svgFile(cache.get(orthographicProjectionsMaps.mapForDocumentAndEnName(enName, frDocument, enDocument)),
		    format("Carte-pays_%s.svg", fileName));
	}

	private String flag(Document document, String fileName) {
		return svgFile(cache.get(document.select("a[title=Drapeau]").first().absUrl("href")),
		    format("Drapeau-pays_%s.svg", fileName));
	}

	private String svgFile(Document wikimediaDocument, String fileName) {
		resources.createSvgFile(wikimediaDocument, fileName);
		return format("<img src=\"%s\" />", fileName);
	}

	private static String gentile(Document document) {
		final Elements els = document.select("th:has(a[href=/wiki/Gentil%C3%A9])+td");
		return !els.isEmpty() ? text(els.first()) : "";
	}

	private static String internetDomain(Document document) {
		return text(document.select("th:has(a[href=/wiki/Domaine_de_premier_niveau])+td").first());
	}

	private static final Pattern STATE_NAME_PATTERN = compile("([^(]+)(?: \\([^)]+\\))?");

	private static String name(Document document) {
		final String h1 = document.select("h1").text();
		final Matcher m = STATE_NAME_PATTERN.matcher(h1);
		if (m.matches()) {
			return m.group(1);
		} else {
			throw new RuntimeException(format("invalid state name '%s'", h1));
		}
	}

	private static Pattern TITLE_TAG_PATTERN = compile("h([1-6])");

	private static Optional<Integer> titleLevel(Element el) {
		final Matcher m = TITLE_TAG_PATTERN.matcher(el.tagName());
		return m.matches() ? Optional.of(parseInt(m.group(1))) : Optional.empty();
	}

	private static Pattern TEXT_REPLACE_PATTERN = compile("\\s+"), SLASH_P_PATTERN = compile("/ \\(");

	private static String text(Element el) {
		final StringBuilder sb = new StringBuilder();
		appendText(el, sb);
		return SLASH_P_PATTERN.matcher(TEXT_REPLACE_PATTERN.matcher(sb.toString()).replaceAll(" ").trim()).replaceAll("(");
	}

	private static void appendText(Node n, StringBuilder sb) {
		if (n instanceof TextNode) {
			sb.append(((TextNode) n).text());
		} else if (n instanceof Element) {
			final Element el = (Element) n;
			if (!el.hasClass("reference")) {
				if ("br".equals(el.tagName())) {
					sb.append(" / ");
				} else {
					el.childNodes().forEach(childNode -> appendText(childNode, sb));
				}
			}
		}
	}

	private static Pattern FILE_NAME_REPLACE_PATTERN = compile("[^a-zA-Z]");

	private static String fileName(String name) {
		return FILE_NAME_REPLACE_PATTERN.matcher(normalize(name, NFD)).replaceAll("");
	}
}
