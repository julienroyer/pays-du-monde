package fr.eimonku.anki.paysmonde;

import java.util.List;

public class State {
	public final String name, wikipediaUrl, map, flag, gentile, internetDomain, enName, enWikipediaUrl;
	public final List<String> capitalNames;

	public State(String name, String wikipediaUrl, String map, String flag, String gentile, String internetDomain,
	    String enName, String enWikipediaUrl, List<String> capitalNames) {
		this.name = name;
		this.wikipediaUrl = wikipediaUrl;
		this.map = map;
		this.flag = flag;
		this.gentile = gentile;
		this.internetDomain = internetDomain;
		this.enName = enName;
		this.enWikipediaUrl = enWikipediaUrl;
		this.capitalNames = capitalNames;
	}
}
