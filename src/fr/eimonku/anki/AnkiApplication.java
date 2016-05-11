package fr.eimonku.anki;

import static java.lang.String.format;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.newBufferedWriter;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import fr.eimonku.wikimedia.WikimediaCache;
import fr.eimonku.wikimedia.WikimediaResources;

public class AnkiApplication {
	public static void ankiApplication(String[] args, ApplicationAction action) {
		final Path resultFile = Paths.get(args[0]), mediaDir = Paths.get(args[1]), cacheDir = Paths.get(args[2]);
		Stream.of(resultFile.toAbsolutePath().getParent(), mediaDir, cacheDir).forEach(dir -> {
			try {
				createDirectories(dir);
			} catch (IOException e) {
				throw new RuntimeException(format("unable to create directory '%s'", dir), e);
			}
		});

		try (final Writer w = newBufferedWriter(resultFile)) {
			action.accept(w, new WikimediaCache(cacheDir), new WikimediaResources(mediaDir));
		} catch (IOException e) {
			throw new RuntimeException(format("unable to write to '%s'", resultFile), e);
		}
	}

	public static interface ApplicationAction {
		void accept(Writer w, WikimediaCache cache, WikimediaResources resources);
	}
}
