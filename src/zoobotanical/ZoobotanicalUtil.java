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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import dsatool.util.ErrorLogger;
import javafx.scene.control.Labeled;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;

public class ZoobotanicalUtil {

	public static List<String> terrains = List.of("Beliebiges Gelände", "Eis", "Wüste und Wüstenrand", "Gebirge", "Hochland", "Steppe", "Grasland, Wiesen",
			"Fluss- und Seeufer, Teiche", "Küste, Strand", "Flussauen", "Sumpf und Moor", "Regenwald", "Wald", "Waldrand");

	public static int getDifficulty(final JSONObject plant, final String terrain) {
		final String prevalence = plant.getObj("Verbreitung").getString(terrain);
		if (prevalence == null) {
			if ("Sonstiges Gelände".equals(terrain))
				return 0;
			else
				throw new IllegalArgumentException("Unbekannte Verbreitung: " + prevalence);
		}
		return switch (prevalence) {
			case "sehr häufig" -> 1;
			case "häufig" -> 2;
			case "gelegentlich" -> 4;
			case "selten" -> 8;
			case "sehr selten" -> 16;
			default -> Integer.MAX_VALUE;
		};
	}

	public static boolean isAvailable(final JSONObject plant, final String harvestTime, final String terrain, final Map<String, Boolean> locationInRegion) {
		if (harvestTime != null) {
			final JSONArray harvest = plant.getArrOrDefault("Ernte", null);
			if (harvest != null && !harvest.contains(harvestTime)) return false;
		}

		if (!"Beliebiges Gelände".equals(terrain) && !"Sonstiges Gelände".equals(terrain)) {
			final JSONObject prevalence = plant.getObjOrDefault("Verbreitung", null);
			if (prevalence == null || !prevalence.containsKey(terrain)) return false;
		}

		final Collection<String> plantRegions = plant.getArr("Gebiet").getStrings();
		for (final String region : plantRegions) {
			if (!locationInRegion.containsKey(region)) {
				ErrorLogger.log("Unbekanntes Gebiet \"" + region + "\"");
			} else if (locationInRegion.get(region)) return true;
		}

		return false;
	}

	private static String plantTypeToCSSColor(final String plantType) {
		return switch (plantType) {
			case "Gefährliche Pflanze" -> "red";
			case "Giftpflanze" -> "darkviolet";
			case "Heilpflanze" -> "green";
			case "Nutzpflanze" -> "sienna";
			case "Übernatürliche Pflanze" -> "goldenrod";
			default -> "black";
		};
	}

	public static JSONArray setPlantNameType(final Labeled control, final JSONObject plants, final JSONObject plant) {
		control.setText(plants.keyOf(plant));

		final JSONArray plantType = plant.getArr("Typ");
		final String typeColor = plantType.size() == 1 ? plantTypeToCSSColor(plantType.getString(0))
				: "linear-gradient(to right," + plantType.getStrings().stream()
						.mapMulti((final String t, final Consumer<String> c) -> {
							c.accept(t);
							c.accept(t);
						})
						.map(t -> plantTypeToCSSColor(t)).collect(Collectors.joining(",")) + ")";
		control.setStyle("-fx-text-fill: " + typeColor);

		return plantType;
	}

	private ZoobotanicalUtil() {}

}
