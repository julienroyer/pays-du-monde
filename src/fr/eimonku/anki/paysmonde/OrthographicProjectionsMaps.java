package fr.eimonku.anki.paysmonde;

import static java.lang.String.format;
import static java.util.Arrays.asList;

import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

public class OrthographicProjectionsMaps {
	final Document document;

	public OrthographicProjectionsMaps(WikipediaCache cache) {
		document = cache.get("https://commons.wikimedia.org/wiki/Grey-green_orthographic_projections_maps");
	}

	private static final Pattern NAME_PATTERN = Pattern.compile("The ");

	public String mapForDocumentAndEnName(String enName, Document frDocument, Document enDocument) {
		if ("Malta".equals(enName)) {
			return "https://en.wikipedia.org/wiki/File:EU-Malta.svg";
		}

		if (!"Hungary".equals(enName)) {
			final String cleanName = NAME_PATTERN.matcher(enName).replaceAll("");
			final Elements els = this.document
			    .select(format("div.gallerytext:has(p:contains(%s))", "Morocco".equals(cleanName) ? "Morocco " : cleanName));
			if (!els.isEmpty()) {
				return els.get(0).previousElementSibling().select("a").get(0).absUrl("href");
			}
		}

		for (final String selector : asList("[href*=orthographic]", "[href^=/wiki/Fichier:Location]",
		    "[href^=/w/index.php?title=Fichier:]")) {
			for (final Document document : asList(frDocument, enDocument)) {
				final Elements els = document.select("div.images a" + selector);
				if (!els.isEmpty()) {
					return els.get(0).absUrl("href");
				}
			}
		}

		throw new RuntimeException(format("no map for '%s'", enName));
	}
}
