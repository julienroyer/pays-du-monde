package fr.eimonku.anki.paysdumonde;

import static fr.eimonku.anki.AnkiApplication.ankiApplication;

import java.io.IOException;

// TODO use png files for maps (and flags?)
// TODO fix pb with large files
// TODO enCapitals, enGentile?
// TODO cloze map + region on globe
// TODO clean OrthographicProjectionsMaps
public class Application {
	public static void main(String... args) throws IOException {
		ankiApplication(args, (w, cache, mediaDir) -> {
			new ListeDesPaysDuMonde(mediaDir, cache).forEach(state -> {
			  try {
				  w.append(state.name).append(';');
				  w.append(state.capitals).append(';');
				  w.append(state.map).append(';');
				  w.append(state.flag).append(';');
				  w.append(state.gentile).append(';');
				  w.append(state.internetDomain).append(';');
				  w.append(state.enName).append('\n');
			  } catch (IOException e) {
				  throw new RuntimeException("unable to write result", e);
			  }
		  });
		});
	}
}
