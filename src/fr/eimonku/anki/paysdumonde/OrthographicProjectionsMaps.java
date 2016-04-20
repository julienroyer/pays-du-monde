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

		switch (enName) {
		case "Andorra":
			return "https://commons.wikimedia.org/wiki/File:Andorra_in_Europe_(zoomed).svg";
		case "Bahrain":
			return "https://en.wikipedia.org/wiki/File:Bahrain_on_the_globe_(Afro-Eurasia_centered).svg";
		case "Belize":
			return "https://commons.wikimedia.org/wiki/File:Belize_on_the_globe_(Americas_centered).svg";
		case "Liechtenstein":
			return "https://commons.wikimedia.org/wiki/File:Liechtenstein_in_Europe_(zoomed).svg";
		case "Malta":
			return "https://commons.wikimedia.org/wiki/File:EU-Malta.svg";
		case "Moldova":
			return "https://commons.wikimedia.org/wiki/File:Locator_map_of_Moldova.svg";
		case "Monaco":
			return "https://commons.wikimedia.org/wiki/File:Locator_map_of_Monaco.svg";
		case "Myanmar":
			return "https://commons.wikimedia.org/wiki/File:Location_Burma_(Myanmar)_ASEAN.svg";
		case "San Marino":
			return "https://commons.wikimedia.org/wiki/File:San_Marino_in_Europe_(zoomed).svg";
		case "Swaziland":
			return "https://commons.wikimedia.org/wiki/File:Swaziland_on_the_globe_(special_marker)_(Madagascar_centered).svg";
		case "Togo":
			return "https://commons.wikimedia.org/wiki/File:Togo_on_the_globe_(Africa_centered).svg";
		case "Turkmenistan":
			return "https://en.wikipedia.org/wiki/File:Turkmenistan_on_the_globe_(Eurasia_centered).svg";
		case "Vatican City":
			return "https://commons.wikimedia.org/wiki/File:Locator_map_of_Vatican_City.svg";
		}

		final Elements els = frDocument.select("div.images a[href*=Fichier]");
		if (!els.isEmpty()) {
			return els.first().absUrl("href");
		}

		throw new RuntimeException(format("no map for '%s'", enName));
	}
}
