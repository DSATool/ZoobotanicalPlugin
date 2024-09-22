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
package zoobotanical.harvest;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import dsa41basis.util.DSAUtil;
import dsa41basis.util.DSAUtil.Units;
import dsatool.ui.ReactiveComboBox;
import dsatool.ui.ReactiveSpinner;
import dsatool.util.ErrorLogger;
import dsatool.util.Util;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import jsonant.value.JSONObject;
import zoobotanical.ZoobotanicalUtil;

public class HarvestDialog {
	@FXML
	private VBox root;
	@FXML
	private Button okButton;
	@FXML
	private Label difficultyLabel;
	@FXML
	private ReactiveSpinner<Integer> difficultyInput;
	@FXML
	private ReactiveComboBox<String> terrainList;
	@FXML
	private Label tapLabel;
	@FXML
	private ReactiveSpinner<Integer> tapInput;
	@FXML
	private CheckBox roll;
	@FXML
	private VBox harvestedPlantsBox;
	@FXML
	private VBox dangerousPlantsBox;

	private final JSONObject plants;

	private final String harvestTime;
	private final Map<String, Boolean> locationInRegion;

	private final Map<JSONObject, HBox> plantBoxes = new HashMap<>();

	public HarvestDialog(final Window window, final JSONObject plants, final JSONObject harvestedPlant, String selectedTerrain, final String harvestTime,
			final Map<String, Boolean> locationInRegion) {

		this.plants = plants;
		this.harvestTime = harvestTime;
		this.locationInRegion = locationInRegion;

		final FXMLLoader fxmlLoader = new FXMLLoader();

		fxmlLoader.setController(this);

		try {
			fxmlLoader.load(getClass().getResource("HarvestDialog.fxml").openStream());
		} catch (final Exception e) {
			ErrorLogger.logError(e);
		}

		final Stage stage = new Stage();
		stage.setTitle("Kräuter Suchen" + (harvestedPlant == null ? "" : ": " + plants.keyOf(harvestedPlant)));
		stage.setScene(new Scene(root, 660, 500)); // 85 + heroes.length * 27
		stage.initModality(Modality.WINDOW_MODAL);
		stage.setResizable(false);
		stage.initOwner(window);

		okButton.setOnAction(e -> stage.close());
		okButton.setDefaultButton(true);

		if (harvestedPlant != null) {
			final Set<String> prevalence = harvestedPlant.getObj("Verbreitung").keySet();
			if (prevalence.isEmpty()) {
				terrainList.getItems().setAll("Sonstiges Gelände");
			} else {
				terrainList.getItems().setAll(prevalence);
			}

			selectedTerrain = selectedTerrain == null ? terrainList.getItems().getFirst() : selectedTerrain;

			terrainList.getSelectionModel().selectedItemProperty().addListener(
					(o, oldV, newV) -> difficultyInput.getValueFactory()
							.setValue(harvestedPlant.getIntOrDefault("Bestimmung", null) + ZoobotanicalUtil.getDifficulty(harvestedPlant, newV)));
			terrainList.getSelectionModel().select(selectedTerrain);

			addPlant(harvestedPlant, true, false, selectedTerrain);
		} else {
			difficultyLabel.setVisible(false);
			difficultyLabel.setManaged(false);
			difficultyInput.setVisible(false);
			difficultyInput.setManaged(false);

			terrainList.getItems().setAll(ZoobotanicalUtil.terrains);
			terrainList.getSelectionModel().select(selectedTerrain);

			for (final String plantName : plants.keySet()) {
				final JSONObject plant = plants.getObj(plantName);
				if (plant.containsKey("Grundmenge")) {
					addPlant(plant, false, false, selectedTerrain);
				}
			}
		}

		for (final String plantName : plants.keySet()) {
			final JSONObject plant = plants.getObj(plantName);
			if (plant != harvestedPlant && plant.getArr("Typ").contains("Gefährliche Pflanze")) {
				addPlant(plant, false, true, selectedTerrain);
			}
		}

		final ChangeListener<? super Object> updateListener = (o, oldVal, newVal) -> {
			for (final JSONObject plant : plantBoxes.keySet()) {
				updateInterpretation(plant, plant == harvestedPlant, terrainList.getSelectionModel().getSelectedItem());
			}
		};

		difficultyInput.valueProperty().addListener(updateListener);
		terrainList.getSelectionModel().selectedItemProperty().addListener(updateListener);
		tapInput.valueProperty().addListener(updateListener);

		stage.show();
	}

	private void addPlant(final JSONObject plant, final boolean harvested, final boolean dangerous, final String selectedTerrain) {
		try {
			final FXMLLoader plantLoader = new FXMLLoader();
			final HBox plantBox = plantLoader.load(getClass().getResource("Plant.fxml").openStream());

			final ObservableList<Node> items = plantBox.getChildren();

			final Label nameLabel = (Label) items.get(0);
			ZoobotanicalUtil.setPlantNameType(nameLabel, plants, plant);
			Util.addReference(nameLabel, plant, 10, nameLabel.widthProperty());

			final Label infoLabel = (Label) items.get(1);
			final String info = plant.getString("Anmerkungen");
			infoLabel.setTooltip(new Tooltip(info));
			infoLabel.setVisible(info != null);

			if (dangerous) {
				dangerousPlantsBox.getChildren().add(plantBox);
			} else {
				harvestedPlantsBox.getChildren().add(plantBox);
			}
			plantBoxes.put(plant, plantBox);
			updateInterpretation(plant, harvested, selectedTerrain);
		} catch (final Exception e) {
			ErrorLogger.logError(e);
		}
	}

	private void updateInterpretation(final JSONObject plant, final boolean harvested, final String selectedTerrain) {
		final HBox plantBox = plantBoxes.get(plant);

		if (!harvested && !ZoobotanicalUtil.isAvailable(plant, harvestTime, selectedTerrain, locationInRegion)) {
			plantBox.setVisible(false);
			plantBox.setManaged(false);
			return;
		}

		final ObservableList<Node> items = plantBox.getChildren();

		final boolean isDangerous = dangerousPlantsBox.getChildren().contains(plantBox);

		final int tap = tapInput.getValue();

		final Label amountLabel = (Label) items.get(2);
		final Label durabilityLabel = (Label) items.get(3);

		if (isDangerous) {
			final int difficulty = plant.getIntOrDefault("Bestimmung", Integer.MAX_VALUE);

			amountLabel.setText(plant.getObj("Verbreitung").getString(selectedTerrain));
			durabilityLabel.setText(Integer.toString(difficulty));

			if (difficulty > tap) {
				plantBox.setVisible(true);
				plantBox.setManaged(true);
			} else {
				plantBox.setVisible(false);
				plantBox.setManaged(false);
			}
		} else {
			final int difficulty = harvested ? difficultyInput.getValue()
					: plant.getIntOrDefault("Bestimmung", Integer.MAX_VALUE) + ZoobotanicalUtil.getDifficulty(plant, selectedTerrain);

			final int count = tap < 0 ? 0 : harvested ? 1 + tap / ((difficulty + 1) / 2) : difficulty > (tap + 1) / 2 ? 0 : tap / difficulty;

			if (count < 1 && !harvested) {
				plantBox.setVisible(false);
				plantBox.setManaged(false);
				return;
			} else {
				plantBox.setVisible(true);
				plantBox.setManaged(true);
			}

			final JSONObject amount = plant.getObjOrDefault("Grundmenge", null);
			final int amountRoll = amount == null ? 0 : DSAUtil.randomRoll(amount, count);
			if (amount != null) {
				amountLabel.textProperty().bind(Bindings.createStringBinding(() -> {
					StringBuilder result;
					if (roll.isSelected()) {
						result = new StringBuilder();
						result.append(amountRoll);
					} else {
						result = DSAUtil.getRollString(amount, count, Units.NONE);
					}
					result.append(' ');
					result.append(amount.getString("Art"));
					return result.toString();
				}, roll.selectedProperty()));
			}

			final JSONObject durability = plant.getObjOrDefault("Haltbarkeit", null);
			if (durability != null) {
				final int durabilityRoll = DSAUtil.randomRoll(durability);
				durabilityLabel.textProperty().bind(Bindings.createStringBinding(() -> {
					if (roll.isSelected()) {
						final StringBuilder result = new StringBuilder();
						result.append(durabilityRoll);
						DSAUtil.appendUnit(result, durability, durabilityRoll == 1, Units.TIME);
						return result.toString();
					} else
						return DSAUtil.getRollString(durability, Units.TIME).toString();
				}, roll.selectedProperty()));
			}

			final Label valueLabel = (Label) items.get(4);
			final Double value = plant.getDouble("Preis");
			if (value != null) {
				valueLabel.textProperty().bind(Bindings.createStringBinding(() -> {
					return (roll.isSelected() ? "" : "je ") + DSAUtil.getMoneyString(roll.isSelected() ? value * amountRoll : value);
				}, roll.selectedProperty()));
			}
		}
	}
}
