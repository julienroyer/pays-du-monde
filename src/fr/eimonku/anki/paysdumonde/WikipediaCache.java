package fr.eimonku.anki.paysdumonde;

import static java.lang.Long.max;
import static java.lang.Long.parseLong;
import static java.lang.String.format;
import static java.nio.file.Files.newBufferedReader;
import static java.nio.file.Files.newBufferedWriter;
import static java.nio.file.Files.newInputStream;
import static java.nio.file.Files.newOutputStream;
import static org.apache.logging.log4j.LogManager.getLogger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.Logger;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import fr.eimonku.json.JsonReader;
import fr.eimonku.json.JsonReader.InvalidJsonException;
import fr.eimonku.json.JsonWriter;

public class WikipediaCache {
	private static final Logger logger = getLogger();
	private static final String IDS_FILE_NAME = "ids.json", PROPERTIES_FILE_NAME = "properties.json";

	private final Path cacheDir;
	private final Map<String, Long> idsByUrls = new LinkedHashMap<>();
	private final Map<Long, CachedDocument> documentsByIds = new LinkedHashMap<>();
	private long nextId = 0;

	public WikipediaCache(Path cacheDir) {
		this.cacheDir = cacheDir;

		try (final Reader r = newBufferedReader(propertiesPath())) {
			new JsonReader(r).readMap((k, properties) -> {
				final long id = parseLong(k);
				final CachedDocument document = cachedDocument(id, (Map<?, ?>) properties);
				documentsByIds.put(id, document);
				nextId = max(nextId, id + 1);

				if (idsByUrls.putIfAbsent(document.baseUri, id) != null) {
					throw new RuntimeException(format("duplicate URI '%s'", document.baseUri));
				}
			});
		} catch (NoSuchFileException e) {
			logger.warn("no properties file");
		} catch (IOException | InvalidJsonException e) {
			throw new RuntimeException("unable to read properties file", e);
		}

		try (final Reader r = newBufferedReader(idsPath())) {
			new JsonReader(r).readMap((url, v) -> {
				final long id = (Long) v;
				if (!documentsByIds.containsKey(id)) {
					throw new RuntimeException(format("invalid id %s", id));
				}

				idsByUrls.put(url, id);
			});
		} catch (NoSuchFileException e) {
			logger.warn("no ids file");
		} catch (IOException | InvalidJsonException e) {
			throw new RuntimeException("unable to read ids file", e);
		}
	}

	public Document get(String url) {
		{
			final Long id = idsByUrls.get(url);
			if (id != null) {
				return documentsByIds.get(id).document;
			}
		}

		final Response response;
		final Document document;
		try {
			response = Jsoup.connect(url).execute();
			document = response.parse();
		} catch (IOException e) {
			throw new RuntimeException(format("unable to access url '%s'", url), e);
		}

		final String baseUri = document.select("link[rel=canonical]").attr("href"), charsetName = document.charset().name();

		if (!url.equals(baseUri)) {
			final Document result = get(baseUri);
			addId(url, idsByUrls.get(baseUri));
			return result;
		}

		final long id = nextId++;
		try (final OutputStream os = new BufferedOutputStream(newOutputStream(cachedDocumentPath(id)))) {
			os.write(response.bodyAsBytes());
		} catch (IOException e) {
			throw new RuntimeException(format("unable to write cached document %s", id), e);
		}

		addDocument(id, new CachedDocument(document, charsetName, baseUri));
		addId(url, id);

		return document;
	}

	private void addId(String url, long id) {
		idsByUrls.put(url, id);

		try (Writer w = newBufferedWriter(idsPath())) {
			new JsonWriter(w, 0).appendMap(idsByUrls);
		} catch (IOException e) {
			throw new RuntimeException("unable to write ids file", e);
		}
	}

	private void addDocument(long id, CachedDocument document) {
		documentsByIds.put(id, document);

		try (Writer w = newBufferedWriter(propertiesPath())) {
			final Iterator<Entry<Long, CachedDocument>> it = documentsByIds.entrySet().iterator();

			new JsonWriter(w, 0).appendMap(new Iterator<Entry<Long, Map<String, String>>>() {
				@Override
				public boolean hasNext() {
					return it.hasNext();
				}

				@Override
				public Entry<Long, Map<String, String>> next() {
					final Entry<Long, CachedDocument> entry = it.next();
					final Map<String, String> properties = new LinkedHashMap<>();
					properties.put("charsetName", entry.getValue().charsetName);
					properties.put("baseUri", entry.getValue().baseUri);
					return new AbstractMap.SimpleEntry<>(entry.getKey(), properties);
				}
			});
		} catch (IOException e) {
			throw new RuntimeException("unable to write properties file", e);
		}
	}

	private CachedDocument cachedDocument(long id, Map<?, ?> properties) {
		final String charsetName = (String) properties.get("charsetName"), baseUri = (String) properties.get("baseUri");
		try (final InputStream is = new BufferedInputStream(newInputStream(cachedDocumentPath(id)))) {
			return new CachedDocument(Jsoup.parse(is, charsetName, baseUri), charsetName, baseUri);
		} catch (IOException e) {
			throw new RuntimeException(format("unable to read cached document %s", properties), e);
		}
	}

	private Path idsPath() {
		return cacheDir.resolve(IDS_FILE_NAME);
	}

	private Path propertiesPath() {
		return cacheDir.resolve(PROPERTIES_FILE_NAME);
	}

	private Path cachedDocumentPath(long id) {
		return cacheDir.resolve(format("cache_%06d.html", id));
	}

	private static class CachedDocument {
		final Document document;
		final String charsetName, baseUri;

		public CachedDocument(Document document, String charsetName, String baseUri) {
			this.document = document;
			this.charsetName = charsetName;
			this.baseUri = baseUri;
		}
	}
}
