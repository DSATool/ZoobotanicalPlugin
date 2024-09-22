/*
 * Copyright 2017 DSATool team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zoobotanical;

import dsatool.credits.Credits;
import dsatool.gui.Main;
import dsatool.plugins.Plugin;
import dsatool.util.Util;

/**
 * A plugin for animal and plant data
 *
 * @author Dominik Helm
 */
public class Zoobotanical extends Plugin {

	private ZoobotanicalController controller;

	/*
	 * (non-Javadoc)
	 *
	 * @see plugins.Plugin#getPluginName()
	 */
	@Override
	public String getPluginName() {
		return "Zoobotanical";
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see plugins.Plugin#initialize()
	 */
	@Override
	public void initialize() {
		Credits.credits.add(new Credits("Aventurienkarten Originale und Bearbeitungen des Ulisses-Spiele Kartenpakets", "Lizenzvereinbarung",
				Util.getAppDir() + "/licenses/Lizenzvereinbarung-Kartenpaket.txt", "https://de.wiki-aventurica.de/wiki/Kartenpaket/Lizenz",
				Util.getAppDir() + "/resources/logos/Fanprojekt.png"));
		Main.addDetachableToolComposite("DSA", "Pflanzen", 900, 800, () -> {
			controller = new ZoobotanicalController();
			return controller.getRoot();
		});
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see plugins.Plugin#load()
	 */
	@Override
	public void load() {}

}