package fr.eimonku.wikimedia;

import static java.lang.String.format;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.sleep;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.isReadable;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

import org.jsoup.nodes.Document;

public class WikimediaResources {
	private final Path dir;

	public WikimediaResources(Path dir) {
		this.dir = requireNonNull(dir, "dir");
	}

	public void createSvgFile(Document wikimediaDocument, String fileName) {
		final String urlStr = wikimediaDocument.select("div.fullMedia a").first().absUrl("href");
		final URL url;
		try {
			url = new URL(urlStr);
		} catch (MalformedURLException e) {
			throw new RuntimeException(format("invalid URL '%s'", urlStr), e);
		}

		final Path mediaPath = dir.resolve(fileName);
		if (!isReadable(mediaPath)) {
			for (int i = 1; true; ++i) {
				try {
					final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
					connection.setRequestProperty("Accept-Encoding", "gzip");

					try (final InputStream in = "gzip".equals(connection.getContentEncoding())
					    ? new GZIPInputStream(connection.getInputStream()) : connection.getInputStream()) {
						copy(in, mediaPath);
						break;
					}
				} catch (IOException e) {
					if (i >= 3) {
						throw new RuntimeException(format("unable to copy '%s' to '%s'", url, mediaPath), e);
					} else {
						try {
							sleep(i * i * 1_000);
						} catch (InterruptedException e1) {
							currentThread().interrupt();
							throw new RuntimeException("interrupted", e1);
						}
					}
				}
			}
		}
	}
}
