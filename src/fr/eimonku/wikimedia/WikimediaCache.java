package fr.eimonku.wikimedia;

import static java.lang.Long.max;
import static java.lang.Long.parseLong;
import static java.lang.String.format;
import static java.lang.Thread.sleep;
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
import java.net.SocketTimeoutException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.AbstractMap.SimpleEntry;
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

public class WikimediaCache {
	private static final Logger logger = getLogger();
	private static final String IDS_FILE_NAME = "ids.json", PROPERTIES_FILE_NAME = "properties.json";

	private final Path dir;
	private final Map<Long, CachedDocument> documentsByIds = new LinkedHashMap<>();
	private final Map<String, Long> idsByUrls = new LinkedHashMap<>();
	private long nextId = 0;

	public WikimediaCache(Path dir) {
		this.dir = dir;
		readPropertiesFile();
		readIdsFile();
	}

	public Document get(String url) {
		{
			final Long id = idsByUrls.get(url);
			if (id != null) {
				return documentsByIds.get(id).document;
			}
		}

		Response response;
		Document document;
		for (int i = 1; true; ++i) {
			try {
				response = Jsoup.connect(url).maxBodySize(0).execute();
				document = response.parse();
				break;
			} catch (SocketTimeoutException e) {
				if (i >= 3) {
					throw new RuntimeException(format("unable to access url '%s'", url), e);
				} else {
					try {
						sleep(i * i * 1_000);
					} catch (InterruptedException e1) {
						throw new RuntimeException("interrupted", e1);
					}
				}
			} catch (IOException e) {
				throw new RuntimeException(format("unable to access url '%s'", url), e);
			}
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

	private void readPropertiesFile() {
		try (final Reader r = newBufferedReader(propertiesPath())) {
			new JsonReader(r).readMap((idStr, properties) -> {
				final long id = parseLong(idStr);
				if (id < 0) {
					throw new RuntimeException(format("properties file: invalid id %s", id));
				}

				final CachedDocument document = cachedDocument(id, (Map<?, ?>) properties);
				if (documentsByIds.putIfAbsent(id, document) != null) {
					throw new RuntimeException(format("properties file: duplicate id %s", id));
				}

				nextId = max(nextId, id + 1);

				if (idsByUrls.putIfAbsent(document.baseUri, id) != null) {
					throw new RuntimeException(format("properties file: duplicate baseUri '%s'", document.baseUri));
				}
			});
		} catch (NoSuchFileException e) {
			logger.warn("no properties file");
		} catch (IOException | InvalidJsonException e) {
			throw new RuntimeException("unable to read properties file", e);
		}
	}

	private void readIdsFile() {
		try (final Reader r = newBufferedReader(idsPath())) {
			new JsonReader(r).readMap((url, idStr) -> {
				final long id = (Long) idStr;
				if (!documentsByIds.containsKey(id)) {
					throw new RuntimeException(format("ids file: invalid id %s", id));
				}

				if (idsByUrls.putIfAbsent(url, id) != null) {
					throw new RuntimeException(format("ids file: duplicate url '%s'", url));
				}
			});
		} catch (NoSuchFileException e) {
			logger.warn("no ids file");
		} catch (IOException | InvalidJsonException e) {
			throw new RuntimeException("unable to read ids file", e);
		}
	}

	private void addId(String url, long id) {
		idsByUrls.put(url, id);

		try (Writer w = newBufferedWriter(idsPath())) {
			new JsonWriter(w, 0).appendMap(new Iterator<Entry<String, Long>>() {
				final Iterator<Entry<String, Long>> it = idsByUrls.entrySet().iterator();
				Entry<String, Long> next;

				@Override
				public boolean hasNext() {
					while (next == null && it.hasNext()) {
						final Entry<String, Long> next = it.next();
						if (!documentsByIds.get(next.getValue()).baseUri.equals(next.getKey())) {
							this.next = next;
						}
					}

					return next != null;
				}

				@Override
				public Entry<String, Long> next() {
					hasNext();
					final Entry<String, Long> next = this.next;
					this.next = null;
					return next;
				}
			});
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
					return new SimpleEntry<>(entry.getKey(), properties);
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
			throw new RuntimeException(format("unable to read cached document %s", id), e);
		}
	}

	private Path propertiesPath() {
		return dir.resolve(PROPERTIES_FILE_NAME);
	}

	private Path idsPath() {
		return dir.resolve(IDS_FILE_NAME);
	}

	private Path cachedDocumentPath(long id) {
		return dir.resolve(format("cache_%07d.html", id));
	}

	private static class CachedDocument {
		final Document document;
		final String charsetName, baseUri;

		CachedDocument(Document document, String charsetName, String baseUri) {
			this.document = document;
			this.charsetName = charsetName;
			this.baseUri = baseUri;
		}
	}
}
