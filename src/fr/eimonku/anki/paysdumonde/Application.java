package fr.eimonku.anki.paysdumonde;

import static java.lang.String.format;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.newBufferedWriter;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import fr.eimonku.wikimedia.WikimediaCache;

// TODO use png files for maps (and flags?)
// TODO enCapitals, enGentile?
// TODO cloze map + region on globe
// TODO clean OrthographicProjectionsMaps
// TODO clean gentile and Internet domain
public class Application {
	public static void main(String... args) throws IOException {
		final Path resultFile = Paths.get(args[0]), mediaDir = Paths.get(args[1]), cacheDir = Paths.get(args[2]);
		Stream.of(resultFile.toAbsolutePath().getParent(), mediaDir, cacheDir).forEach(dir -> {
			try {
				createDirectories(dir);
			} catch (Exception e) {
				throw new RuntimeException(format("unable to create directory '%s'", dir), e);
			}
		});

		try (final Writer w = newBufferedWriter(resultFile)) {
			new ListeDesPaysDuMonde(mediaDir, new WikimediaCache(cacheDir)).forEach(state -> {
				try {
					w.append(state.name).append(';');
					w.append(state.capitals).append(';');
					w.append(state.map).append(';');
					w.append(state.flag).append(';');
					w.append(state.gentile).append(';');
					w.append(state.internetDomain).append(';');
					w.append(state.enName).append('\n');
				} catch (IOException e) {
					throw new RuntimeException(format("unable to write to '%s'", resultFile), e);
				}
			});
		}
	}
}
