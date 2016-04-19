package fr.eimonku.anki.paysdumonde;

import static java.lang.System.out;

import java.nio.file.Paths;

public class Application {
	public static void main(String... args) {
		new ListeDesPaysDuMonde(Paths.get(args[0]), new WikipediaCache(Paths.get(args[1]))).forEach(state -> {
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
