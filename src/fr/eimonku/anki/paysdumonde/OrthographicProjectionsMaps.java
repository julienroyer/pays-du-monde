package fr.eimonku.anki.paysdumonde;

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
		{
			final String cleanName = NAME_PATTERN.matcher(enName).replaceAll("");
			final Elements els = this.document.select(format("div.gallerytext:has(p:contains(%s))", cleanName));
			if (!els.isEmpty() && !"Austro-Hungary".equals(els.first().text())) {
				return els.first().previousElementSibling().select("a").first().absUrl("href");
			}
		}

		for (final Document document : asList(frDocument, enDocument)) {
			final Elements els = document.select("div.images a[href*=orthographic]");
			if (!els.isEmpty()) {
				return els.first().absUrl("href");
			}
		}

		if ("Malta".equals(enName)) {
			return "https://commons.wikimedia.org/wiki/File:EU-Malta.svg";
		}

		final Elements els = frDocument.select("div.images a[href*=Fichier]");
		if (!els.isEmpty()) {
			return els.first().absUrl("href");
		}

		throw new RuntimeException(format("no map for '%s'", enName));
	}
}
