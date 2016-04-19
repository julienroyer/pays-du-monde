package fr.eimonku.anki.paysdumonde;

import static java.lang.System.out;
import static java.nio.file.Files.createDirectories;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

// TODO use png files for maps
public class Application {
	public static void main(String... args) {
		final Path mediaDir = Paths.get(args[0]), cacheDir = Paths.get(args[1]);
		Stream.of(mediaDir, cacheDir).forEach(dir -> {
			try {
				createDirectories(dir);
			} catch (Exception e) {
				throw new RuntimeException(String.format("unable to create directory '%s'", dir), e);
			}
		});

		new ListeDesPaysDuMonde(mediaDir, new WikipediaCache(cacheDir)).forEach(state -> {
			out.print(state.name);
			out.print(';');
			out.print(state.capitals);
			out.print(';');
			out.print(state.map);
			out.print(';');
			out.print(state.flag);
			out.print(';');
			out.print(state.gentile);
			out.print(';');
			out.print(state.internetDomain);
			out.print(';');
			out.print(state.enName);

			out.println();
		});
	}
}
