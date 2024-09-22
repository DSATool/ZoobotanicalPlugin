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

import java.io.File;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import dsa41basis.util.DSAUtil;
import dsatool.gui.GUIUtil;
import dsatool.resources.ResourceManager;
import dsatool.ui.ReactiveComboBox;
import dsatool.util.ErrorLogger;
import dsatool.util.Tuple;
import dsatool.util.Util;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.Glow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import jsonant.value.JSONArray;
import jsonant.value.JSONObject;
import zoobotanical.harvest.HarvestDialog;

public class ZoobotanicalController {
	@FXML
	private Node pane;
	@FXML
	private ScrollPane mapPane;
	@FXML
	private Group map;
	@FXML
	private Group mapRegions;
	@FXML
	private Label locationMarker;

	@FXML
	private ListView<JSONObject> plantList;
	@FXML
	private VBox plantBox;
	@FXML
	private Label identification;
	@FXML
	private Label harvestInfo;
	@FXML
	private Button harvestButton;
	@FXML
	private TableView<String> prevalenceTable;
	@FXML
	private TableColumn<String, String> prevalenceTerrainColumn;
	@FXML
	private TableColumn<String, Integer> prevalenceDifficultyColumn;
	@FXML
	private ReactiveComboBox<String> terrainList;
	@FXML
	private Button harvestAnyButton;

	@FXML
	private Label harvestLabel;
	@FXML
	private PieChart plantMonths;

	private final DoubleProperty scale = new SimpleDoubleProperty(1.0);
	private double scaleMinimum = 0;

	private Tuple<Double, Double> location = new Tuple<>(-1d, -1d);
	private PieChart.Data harvestTime = null;

	private final Map<String, Image> regions;
	private final Map<String, Boolean> locationInRegion;

	private final JSONObject plants = ResourceManager.getResource("data/Pflanzen");
	private final ObservableSet<JSONObject> availablePlants = FXCollections.observableSet();
	private final ObservableList<JSONObject> allPlants = FXCollections.observableArrayList(item -> new Observable[] { availablePlants });

	private final ReadOnlyObjectProperty<JSONObject> selectedPlant;

	private final ReadOnlyObjectProperty<String> selectedTerrain;

	public ZoobotanicalController() {
		final FXMLLoader fxmlLoader = new FXMLLoader();

		fxmlLoader.setController(this);

		try {
			fxmlLoader.load(getClass().getResource("Zoobotanical.fxml").openStream());
		} catch (final Exception e) {
			ErrorLogger.logError(e);
		}

		setupMap();

		final File[] regionFiles = new File(Util.getAppDir() + "/resources/images/maps/regions").listFiles();
		regions = new HashMap<>(regionFiles.length);
		locationInRegion = new HashMap<>(regionFiles.length);
		setupRegions(regionFiles);

		selectedPlant = plantList.getSelectionModel().selectedItemProperty();
		selectedPlant.addListener((o, oldV, newV) -> selectPlant(newV));
		setupPlants();

		terrainList.getItems().setAll(ZoobotanicalUtil.terrains);
		terrainList.getSelectionModel().select(0);
		selectedTerrain = terrainList.getSelectionModel().selectedItemProperty();
		selectedTerrain.addListener((o, oldV, newV) -> updateAvailablePlants());
		harvestAnyButton.disableProperty().bind(selectedPlant.isNotNull().or(selectedTerrain.isEqualTo("Beliebiges Gelände")));
	}

	public Node getRoot() {
		return pane;
	}

	@FXML
	private void harvestAnyPlant() {
		new HarvestDialog(pane.getScene().getWindow(), plants, null, selectedTerrain.get(), harvestTime == null ? null : harvestTime.getName(),
				locationInRegion);
	}

	@FXML
	private void harvestPlant() {
		final String terrain = selectedTerrain.get();
		new HarvestDialog(pane.getScene().getWindow(), plants, selectedPlant.get(), "Beliebiges Gelände".equals(terrain) ? null : terrain,
				harvestTime == null ? null : harvestTime.getName(), locationInRegion);
	}

	public void selectHarvestTime(final PieChart.Data selected) {
		harvestTime = selected;
		plantMonths.getData().forEach(month -> {
			if (month != selected) {
				month.getNode().setEffect(null);
			}
		});
		harvestLabel.setText("Ernte: " + (selected != null ? selected.getName() : ""));
		updateAvailablePlants();
	}

	private void selectLocation(final Tuple<Double, Double> newLocation) {
		final double x = newLocation._1;
		final double y = newLocation._2;

		location = newLocation;

		if (x >= 0) {
			for (final String region : regions.keySet()) {
				locationInRegion.put(region, regions.get(region).getPixelReader().getColor((int) x, (int) y).equals(Color.BLACK));
			}
		} else {
			for (final String region : regions.keySet()) {
				locationInRegion.put(region, true);
			}
		}

		updateAvailablePlants();

		final Bounds mapBounds = map.getBoundsInLocal();
		locationMarker.relocate((x - mapBounds.getWidth() / 2) * scale.get() + mapBounds.getWidth() / 2 - 15,
				(y - mapBounds.getHeight() / 2) * scale.get() + mapBounds.getHeight() / 2 - 15);

		locationMarker.setVisible(x >= 0);
	}

	private void selectPlant(final JSONObject plant) {
		plantBox.setVisible(plant != null);
		plantBox.setManaged(plant != null);
		mapRegions.getChildren().clear();
		if (plant == null) {
			plantMonths.getData().forEach(month -> month.getNode().setOpacity(1));
			harvestInfo.setVisible(false);
		} else {
			identification.setText(plant.getIntOrDefault("Bestimmung", Integer.MIN_VALUE).toString());

			final Set<String> prevalence = plant.getObj("Verbreitung").keySet();
			prevalenceTable.getItems().setAll(prevalence);
			prevalenceTable.setVisible(!prevalence.isEmpty());
			prevalenceTable.setManaged(!prevalence.isEmpty());

			mapRegions.getChildren().addAll(plant.getArr("Gebiet").getStrings().stream().map(region -> new ImageView(regions.get(region))).toList());

			final JSONArray harvest = plant.getArrOrDefault("Ernte", null);
			harvestButton.setVisible(harvest != null);
			harvestButton.setManaged(harvest != null);
			plantMonths.getData().forEach(month -> month.getNode().setOpacity(harvest == null || harvest.contains(month.getName()) ? 1 : 0.075));

			final String notes = plant.getString("Anmerkungen");
			if (notes != null) {
				harvestInfo.setTooltip(new Tooltip(notes));
				harvestInfo.setVisible(true);
			} else {
				harvestInfo.setVisible(false);
			}

			plantList.scrollTo(plant);
		}
	}

	private void setupMap() {
		map.scaleXProperty().bind(scale);
		map.scaleYProperty().bind(scale);

		final Image mapImage = ((ImageView) map.getChildren().get(0)).getImage();
		final double mapWidth = mapImage.getWidth();
		final double mapHeight = mapImage.getHeight();

		mapPane.addEventFilter(ScrollEvent.ANY, e -> {
			double scaleFactor = Math.exp(e.getDeltaY() * 0.005);
			final double newScale = Math.min(Math.max(scale.get() * scaleFactor, scaleMinimum), 10);
			scaleFactor = newScale / scale.get();

			final Bounds viewport = mapPane.getViewportBounds();
			final Bounds mapBounds = map.getBoundsInParent();

			final double relativeX = (mapBounds.getWidth() - viewport.getWidth()) * mapPane.getHvalue() + e.getX();
			final double relativeY = (mapBounds.getHeight() - viewport.getHeight()) * mapPane.getVvalue() + e.getY();

			scale.set(newScale);

			mapPane.setHvalue((relativeX * scaleFactor - e.getX()) / (mapBounds.getWidth() * scaleFactor - viewport.getWidth()));
			mapPane.setVvalue((relativeY * scaleFactor - e.getY()) / (mapBounds.getHeight() * scaleFactor - viewport.getHeight()));

			selectLocation(location);

			e.consume();
		});

		mapPane.maxWidthProperty().bind(mapImage.widthProperty().multiply(scale));

		@SuppressWarnings("unchecked")
		final ChangeListener<? super Number> setInitialScale[] = new ChangeListener[1];
		setInitialScale[0] = (o, oldV, newV) -> {
			final double initialScale = Math.max(mapPane.getWidth() / mapWidth, mapPane.getHeight() / mapHeight);
			scaleMinimum = initialScale;
			scale.set(initialScale);
			mapPane.heightProperty().removeListener(setInitialScale[0]);

			mapPane.setHvalue(0.5);
			mapPane.setVvalue(0.5);
		};
		mapPane.heightProperty().addListener(setInitialScale[0]);

		map.setOnMouseClicked(e -> {
			if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
				selectLocation(new Tuple<>(e.getX(), e.getY()));
			} else if (e.getButton() == MouseButton.SECONDARY && e.getClickCount() == 2) {
				selectLocation(new Tuple<>(-1d, -1d));
			}
		});

		final Polygon mask = new Polygon(500, 0, 500, 175, 0, 175, 0, mapHeight, mapWidth, mapHeight, mapWidth, 0);
		mask.setFill(Color.TURQUOISE);
		mask.setBlendMode(BlendMode.SRC_ATOP);
		((Group) mapRegions.getParent()).getChildren().add(mask);
	}

	void setupPlants() {
		for (final String name : plants.keySet()) {
			allPlants.add(plants.getObj(name));
			availablePlants.add(plants.getObj(name));
		}

		plantList.setCellFactory(list -> {
			final ListCell<JSONObject> cell = new ListCell<>() {
				@Override
				public void updateItem(final JSONObject item, final boolean empty) {
					super.updateItem(item, empty);
					if (empty) {
						setText(null);
						setStyle("");
						setTooltip(null);
						setGraphic(null);
					} else {
						final JSONArray plantType = ZoobotanicalUtil.setPlantNameType(this, plants, item);
						String typeString = plantType.getStrings().toString();
						typeString = typeString.substring(1, typeString.length() - 1);
						setTooltip(new Tooltip(typeString));

						Util.addReference(this, item, 35, plantList.widthProperty());
					}
				}
			};
			cell.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
				list.requestFocus();
				final MultipleSelectionModel<JSONObject> selectionModel = list.getSelectionModel();
				if (cell.isEmpty() || event.getButton() == MouseButton.SECONDARY) {
					selectionModel.clearSelection();
					event.consume();
				} else if (event.getButton() == MouseButton.PRIMARY) {
					final int index = cell.getIndex();
					if (selectionModel.getSelectedIndices().contains(index)) {
						selectionModel.clearSelection(index);
					} else {
						selectionModel.select(index);
					}
					event.consume();
				}
			});
			return cell;
		});
		plantList.setItems(new FilteredList<>(allPlants, availablePlants::contains));

		plantBox.setVisible(false);
		plantBox.setManaged(false);

		GUIUtil.autosizeTable(prevalenceTable);

		prevalenceTable.setRowFactory(table -> {
			final TableRow<String> row = new TableRow<>();
			final ContextMenu menu = new ContextMenu();

			final MenuItem harvestItem = new MenuItem("Kräuter Suchen");
			harvestItem.setOnAction(o -> {
				final String item = row.getItem();
				final JSONObject plant = selectedPlant.get();
				new HarvestDialog(pane.getScene().getWindow(), plants, plant, item, harvestTime == null ? null : harvestTime.getName(), locationInRegion);
			});
			menu.getItems().add(harvestItem);

			row.contextMenuProperty().bind(
					Bindings.createObjectBinding(() -> selectedPlant.get() != null && selectedPlant.get().containsKey("Ernte") ? menu : null, selectedPlant));
			return row;
		});

		prevalenceTerrainColumn.setCellFactory(list -> new TextFieldTableCell<String, String>() {
			@Override
			public void updateItem(final String item, final boolean empty) {
				super.updateItem(item, empty);
				if (empty) {
					setText(null);
					setStyle("");
					setTooltip(null);
				} else {
					setText(item);
					final String prevalence = selectedPlant.get().getObj("Verbreitung").getString(item);
					final String prevalenceColor = switch (prevalence) {
						case "sehr häufig" -> "green";
						case "häufig" -> "limegreen";
						case "gelegentlich" -> "orange";
						case "selten" -> "red";
						case "sehr selten" -> "darkred";
						default -> "black";
					};
					setStyle("-fx-text-fill: " + prevalenceColor);
					setTooltip(new Tooltip(prevalence));
				}
			}
		});
		prevalenceTerrainColumn.setCellValueFactory(e -> {
			return new SimpleStringProperty(e.getValue());
		});

		prevalenceDifficultyColumn.setCellValueFactory(e -> {
			final int difficulty = ZoobotanicalUtil.getDifficulty(selectedPlant.get(), e.getValue());
			return new SimpleIntegerProperty(selectedPlant.get().getIntOrDefault("Bestimmung", Integer.MAX_VALUE - difficulty) + difficulty).asObject();
		});

		harvestInfo.setVisible(false);

		plantMonths.getData().addAll(Stream.of(DSAUtil.months).map(month -> new PieChart.Data(month, "Namenloser".equals(month) ? 5 : 30)).toList());
		plantMonths.getStylesheets().add("""
				data:text/css,
				.chart-pie {
					-fx-background-insets: -0.1;
					-fx-border-width: 0;
				}
				""");
		plantMonths.getData().forEach(month -> {
			final Node node = month.getNode();
			node.setStyle("-fx-pie-color: " + DSAUtil.monthColors.get(month.getName()) + ";");

			Tooltip.install(node, new Tooltip(month.getName()));

			node.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
				if (node.getEffect() != null || e.getButton() == MouseButton.SECONDARY) {
					selectHarvestTime(null);
				} else if (node.getOpacity() == 1.0 && e.getButton() == MouseButton.PRIMARY) {
					selectHarvestTime(month);
					node.setEffect(new Glow(1.0));
				}
			});
		});
	}

	private void setupRegions(final File[] files) {
		if (files != null) {
			for (final File file : files) {
				if (file.isFile()) {
					final String fileName = file.getName();
					final String name = fileName.substring(0, fileName.indexOf('.'));
					try {
						regions.put(name, new Image(file.toURI().toURL().toString()));
						locationInRegion.put(name, true);
					} catch (final MalformedURLException e) {
						// Nothing to do here
					}
				}
			}
		}
	}

	private void updateAvailablePlants() {
		for (final JSONObject plant : allPlants) {
			if (ZoobotanicalUtil.isAvailable(plant, harvestTime == null ? null : harvestTime.getName(), selectedTerrain.get(), locationInRegion)) {
				availablePlants.add(plant);
			} else {
				if (plant == selectedPlant.get()) {
					plantList.getSelectionModel().clearSelection();
				}
				availablePlants.remove(plant);
			}
		}
	}
}
