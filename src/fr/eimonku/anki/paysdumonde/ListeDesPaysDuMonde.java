package fr.eimonku.anki.paysdumonde;

import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.isReadable;
import static java.util.Locale.FRENCH;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
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

public class ListeDesPaysDuMonde {
	private final Path mediaDir;
	private final WikipediaCache cache;
	private final ListeDesCapitalesDuMonde listeDesCapitalesDuMonde;
	private final OrthographicProjectionsMaps orthographicProjectionsMaps;

	public ListeDesPaysDuMonde(Path mediaDir, WikipediaCache cache) {
		this.mediaDir = mediaDir;
		this.cache = cache;
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

	private static Pattern NAME_REPLACE_PATTERN = compile("[^a-zA-Z]+");

	private void processTable(Element table, Consumer<State> action) {
		table.children().select("tbody").forEach(tbody -> tbody.children().select("tr").forEach(tr -> {
			final Element td = tr.child(1);
			if ("td".equals(td.tagName())) {
				final Document document = cache.get(td.children().select("a").get(0).absUrl("href"));
				final String canonicalUrl = document.baseUri();
				final String name = name(document);
				final String fileName = NAME_REPLACE_PATTERN.matcher(name).replaceAll("-");

				final Document enDocument = cache.get("France".equals(name) ? "https://en.wikipedia.org/wiki/France"
		        : document.select("li.interwiki-en a").get(0).absUrl("href"));
				final String enName = name(enDocument);

				action.accept(new State(name,
		        String.join(", ",
		            listeDesCapitalesDuMonde.capitalNamesForWikipediaCanonicalUrl(canonicalUrl).collect(toList())),
		        map(enName, document, enDocument, fileName), flag(document, fileName), gentile(document),
		        internetDomain(document), enName));
			}
		}));
	}

	private String map(String enName, Document frDocument, Document enDocument, String fileName) {
		final URL url;
		try {
			url = new URL(cache.get(orthographicProjectionsMaps.mapForDocumentAndEnName(enName, frDocument, enDocument))
			    .select("div.fullMedia a").get(0).absUrl("href"));
		} catch (MalformedURLException e) {
			throw new RuntimeException("invalid map URL", e);
		}

		final String fullFileName = format("Carte-pays_%s.%s", fileName, url.getPath().endsWith(".png") ? "png" : "svg");

		final Path mapPath = mediaDir.resolve(fullFileName);
		if (!isReadable(mapPath)) {
			try (InputStream in = url.openStream()) {
				copy(in, mapPath);
			} catch (IOException e) {
				throw new RuntimeException(format("unable to write map '%s'", mapPath), e);
			}
		}

		return format("<img src=\"%s\" />", fullFileName);
	}

	private String flag(Document document, String fileName) {
		final URL url;
		try {
			url = new URL(cache.get(document.select("a[title=Drapeau]").get(0).absUrl("href")).select("div.fullMedia a")
			    .get(0).absUrl("href"));
		} catch (MalformedURLException e) {
			throw new RuntimeException("invalid flag URL", e);
		}

		final String fullFileName = format("Drapeau-pays_%s.svg", fileName);

		final Path flagPath = mediaDir.resolve(fullFileName);
		if (!isReadable(flagPath)) {
			try (InputStream in = url.openStream()) {
				copy(in, flagPath);
			} catch (IOException e) {
				throw new RuntimeException(format("unable to write flag '%s'", flagPath), e);
			}
		}

		return format("<img src=\"%s\" />", fullFileName);
	}

	private static String gentile(Document document) {
		final Elements els = document.select("th:has(a[href=/wiki/Gentil%C3%A9])+td");
		return !els.isEmpty() ? text(els.get(0)) : "";
	}

	private static String internetDomain(Document document) {
		return text(document.select("th:has(a[href=/wiki/Domaine_de_premier_niveau])+td").get(0));
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

	private static String text(Element el) {
		final StringBuilder sb = new StringBuilder();
		appendText(el, sb);
		return sb.toString();
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
}
