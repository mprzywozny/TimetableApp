package com.example.timetableapp;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.paint.Color;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class TimetableClient extends Application {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12346;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    // Global communicationStatusArea used for connection-related messages.
    private TextArea communicationStatusArea;
    private List<Scene> scenes = new ArrayList<>();
    private Stage window;
    private Scene loginScene, homeScene, addLectureScene, removeLectureScene;

    // Persistent scenes for timetable, history, and statistics.
    private Scene viewTimetableScene, viewHistoryScene, statisticsScene;
    private GridPane timetableGrid;  // Fixed grid for the timetable.
    private Label[][] timetableCells; // Array to hold timetable cell labels.

    // Fixed arrays for days and time slots.
    private final String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};
    private final String[] times = {"9-10", "10-11", "11-12", "12-1", "1-2", "2-3", "3-4", "4-5", "5-6"};

    private boolean isDark = false; // Default to light mode.
    private boolean isFullScreen = false;

    // For undo functionality.
    // When adding a lecture, lastAddedLectureKey stores "day,time" of the added lecture.
    private String lastAddedLectureKey = null;
    // When removing a lecture, lastRemovedLectureKey stores "day,time" of the removed lecture.
    private String lastRemovedLectureKey = null;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        window = primaryStage;

        communicationStatusArea = new TextArea();
        communicationStatusArea.setEditable(false);
        communicationStatusArea.setMaxHeight(100);

        createLoginScene();
        window.setTitle("Lecture Scheduler");
        window.setScene(loginScene);
        window.show();
    }

    //---------------------- LOGIN SCENE ----------------------
    private void createLoginScene() {
        VBox layout = new VBox(20);
        layout.setAlignment(Pos.CENTER);

        Button loginButton = new Button("Login");
        loginButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 14px;");
        loginButton.setOnAction(e -> connectToServer());

        Button toggleFullScreenButton = new Button("Toggle Full-Screen");
        toggleFullScreenButton.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-size: 14px;");
        toggleFullScreenButton.setOnAction(e -> toggleFullScreen());

        communicationStatusArea.appendText("Server: Waiting for login.\n");
        layout.getChildren().addAll(loginButton, toggleFullScreenButton, communicationStatusArea);
        loginScene = new Scene(layout, 400, 300);
        scenes.add(loginScene);
        applyThemeToScene(loginScene);
    }

    //---------------------- CONNECT TO SERVER ----------------------
    private void connectToServer() {
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            communicationStatusArea.appendText("Server: Successfully Connected to Server!\n");

            createHomePageScene();
            window.setScene(homeScene);
            if (isFullScreen)
                window.setFullScreen(true);
        } catch (Exception e) {
            communicationStatusArea.appendText("Server: Failed to connect.\n");
        }
    }

    private void toggleFullScreen() {
        isFullScreen = !isFullScreen;
        window.setFullScreen(isFullScreen);
    }

    //---------------------- HOME PAGE ----------------------
    private void createHomePageScene() {
        VBox layout = new VBox(20);
        layout.setAlignment(Pos.CENTER);

        Button addLectureButton = new Button("Add Lecture");
        addLectureButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 14px;");
        addLectureButton.setOnAction(e -> openAddLectureWindow());

        Button removeLectureButton = new Button("Remove Lecture");
        removeLectureButton.setStyle("-fx-background-color: #F44336; -fx-text-fill: white; -fx-font-size: 14px;");
        removeLectureButton.setOnAction(e -> openRemoveLectureWindow());

        Button viewTimetableButton = new Button("View Timetable");
        viewTimetableButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 14px;");
        viewTimetableButton.setOnAction(e -> openViewTimetableWindow());

        Button viewHistoryButton = new Button("View History");
        viewHistoryButton.setStyle("-fx-background-color: #9C27B0; -fx-text-fill: white; -fx-font-size: 14px;");
        viewHistoryButton.setOnAction(e -> openViewHistoryWindow());

        Button earlyLecturesButton = new Button("Early Lectures");
        earlyLecturesButton.setStyle("-fx-background-color: #FFC107; -fx-text-fill: white; -fx-font-size: 14px;");
        earlyLecturesButton.setOnAction(e -> sendCommand("EARLY_LECTURES", response ->
                communicationStatusArea.appendText("Server: " + response + "\n")));

        Button otherButton = new Button("Other");
        otherButton.setStyle("-fx-background-color: #C8A2C8; -fx-text-fill: white; -fx-font-size: 14px;");
        otherButton.setOnAction(e -> other());

        Button logoutButton = new Button("Logout");
        logoutButton.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-size: 14px;");
        logoutButton.setOnAction(e -> logout());

        Button stopConnectionButton = new Button("Stop Connection");
        stopConnectionButton.setStyle("-fx-background-color: #9E9E9E; -fx-text-fill: white; -fx-font-size: 14px;");
        stopConnectionButton.setOnAction(e -> stopConnection());

        Button toggleThemeButton = new Button("Change Theme");
        toggleThemeButton.setStyle("-fx-background-color: #FFB6C1; -fx-text-fill: white; -fx-font-size: 14px;");
        toggleThemeButton.setOnAction(e -> toggleTheme());

        layout.getChildren().addAll(addLectureButton, removeLectureButton, viewTimetableButton,
                viewHistoryButton, earlyLecturesButton, otherButton, logoutButton, stopConnectionButton,
                communicationStatusArea, toggleThemeButton);
        homeScene = new Scene(layout, 400, 500);
        scenes.add(homeScene);

        // Initially apply the current theme.
        applyThemeToScene(homeScene);
    }

    //---------------------- sendCommand Helper ----------------------
    private void sendCommand(String command, java.util.function.Consumer<String> onResponse) {
        if (socket == null || socket.isClosed()) {
            communicationStatusArea.appendText("Server: Disconnected.\n");
            return;
        }
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                out.println(command);
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    sb.append(line).append("\n");
                    if (!in.ready()) break;
                }
                return sb.toString();
            }
        };
        task.setOnSucceeded(e -> onResponse.accept(task.getValue()));
        task.setOnFailed(e -> communicationStatusArea.appendText("Server: Error processing command.\n"));
        new Thread(task).start();
    }

    //---------------------- sendImportCSV Helper ----------------------
    private void sendImportCSV(String csvContent, java.util.function.Consumer<String> onResponse) {
        if (socket == null || socket.isClosed()) {
            communicationStatusArea.appendText("Server: Disconnected.\n");
            return;
        }
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                out.println("IMPORT_CSV");
                out.println("BEGIN_CSV");
                String[] lines = csvContent.split("\n");
                for (String line : lines) {
                    out.println(line);
                }
                out.println("END_CSV");
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    sb.append(line).append("\n");
                    if (!in.ready()) break;
                }
                return sb.toString();
            }
        };
        task.setOnSucceeded(e -> onResponse.accept(task.getValue()));
        task.setOnFailed(e -> communicationStatusArea.appendText("Server: Error importing CSV.\n"));
        new Thread(task).start();
    }

    //---------------------- ADD LECTURE WINDOW ----------------------
    private void openAddLectureWindow() {
        VBox layout = new VBox(20);
        layout.setAlignment(Pos.CENTER);

        ComboBox<String> timeComboBox = new ComboBox<>();
        timeComboBox.getItems().addAll("9-10", "10-11", "11-12", "12-1", "1-2", "2-3", "3-4", "4-5", "5-6");
        ComboBox<String> dayComboBox = new ComboBox<>();
        dayComboBox.getItems().addAll("Monday", "Tuesday", "Wednesday", "Thursday", "Friday");
        ComboBox<String> lectureTypeComboBox = new ComboBox<>();
        lectureTypeComboBox.getItems().addAll("Lecture", "Lab", "Tutorial");
        TextField classTextField = new TextField();
        classTextField.setPromptText("Enter Class Name");
        TextField moduleTextField = new TextField();
        moduleTextField.setPromptText("Enter Module Name");

        TextArea localMessageBox = new TextArea();
        localMessageBox.setEditable(false);
        localMessageBox.setMaxHeight(50);

        Button addButton = new Button("Add Lecture");
        addButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        addButton.setOnAction(e -> {
            String time = timeComboBox.getValue();
            String day = dayComboBox.getValue();
            String className = classTextField.getText();
            String module = moduleTextField.getText();
            String lectureType = lectureTypeComboBox.getValue();
            if (time != null && day != null && !className.isEmpty() && !module.isEmpty() && lectureType != null) {
                String command = "ADD_LECTURE," + time + "," + day + "," + className + "," + module + "," + lectureType;
                sendCommand(command, response -> {
                    localMessageBox.appendText(response + "\n");
                    // Store added lecture key for undo functionality.
                    lastAddedLectureKey = day + "," + time;
                });
            } else {
                localMessageBox.appendText("Server: Please fill in all fields!\n");
            }
        });

        Button undoButton = new Button("Undo");
        undoButton.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white;");
        undoButton.setOnAction(e -> {
            if (lastAddedLectureKey != null) {
                String command = "REMOVE_LECTURE," + lastAddedLectureKey;
                sendCommand(command, response -> {
                    localMessageBox.appendText("Undo: " + response + "\n");
                    lastAddedLectureKey = null;
                });
            } else {
                localMessageBox.appendText("Nothing to undo.\n");
            }
        });

        Button backButton = new Button("Back");
        backButton.setStyle("-fx-background-color: #9E9E9E; -fx-text-fill: white;");
        backButton.setOnAction(e -> window.setScene(homeScene));

        layout.getChildren().addAll(timeComboBox, dayComboBox, lectureTypeComboBox,
                classTextField, moduleTextField, addButton, undoButton, backButton, localMessageBox);
        addLectureScene = new Scene(layout, 400, 400);
        scenes.add(addLectureScene);
        applyThemeToScene(addLectureScene);
        window.setScene(addLectureScene);
        if (isFullScreen)
            window.setFullScreen(true);
    }

    //---------------------- REMOVE LECTURE WINDOW ----------------------
    private void openRemoveLectureWindow() {
        VBox layout = new VBox(20);
        layout.setAlignment(Pos.CENTER);
        Label infoLabel = new Label("Select a lecture to remove:");
        ComboBox<String> lectureComboBox = new ComboBox<>();

        Button refreshLecturesButton = new Button("Refresh List");
        refreshLecturesButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        refreshLecturesButton.setOnAction(e -> {
            sendCommand("GET_LECTURES", response -> {
                lectureComboBox.getItems().clear();
                String[] lines = response.split("\n");
                for (String line : lines) {
                    if (!line.trim().isEmpty() && !line.startsWith("No lectures"))
                        lectureComboBox.getItems().add(line.trim());
                }
            });
        });
        refreshLecturesButton.fire();

        TextArea localMessageBox = new TextArea();
        localMessageBox.setEditable(false);
        localMessageBox.setMaxHeight(50);

        Button removeButton = new Button("Remove Lecture");
        removeButton.setStyle("-fx-background-color: #F44336; -fx-text-fill: white;");
        removeButton.setOnAction(e -> {
            String selected = lectureComboBox.getValue();
            if (selected == null || selected.isEmpty()) {
                localMessageBox.appendText("Server: Please select a lecture to remove.\n");
                return;
            }
            // Expected format: "day,time: className (module, lectureType)"
            String[] parts = selected.split(":");
            if (parts.length < 1) {
                localMessageBox.appendText("Server: Invalid lecture format.\n");
                return;
            }
            String header = parts[0].trim();
            String[] headerParts = header.split(",");
            if (headerParts.length < 2) {
                localMessageBox.appendText("Server: Invalid lecture header format.\n");
                return;
            }
            String day = headerParts[0].trim();
            String time = headerParts[1].trim();
            String command = "REMOVE_LECTURE," + day + "," + time;
            sendCommand(command, response -> {
                localMessageBox.appendText(response + "\n");
                // Store removed lecture key for undo.
                lastRemovedLectureKey = day + "," + time;
                // Do not automatically switch scene.
            });
        });

        Button undoButton = new Button("Undo");
        undoButton.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white;");
        undoButton.setOnAction(e -> {
            if (lastRemovedLectureKey != null) {
                String command = "UNDO_REMOVE," + lastRemovedLectureKey;
                sendCommand(command, response -> {
                    localMessageBox.appendText(response + "\n");
                    lastRemovedLectureKey = null;
                });
            } else {
                localMessageBox.appendText("Nothing to undo.\n");
            }
        });

        Button backButton = new Button("Back");
        backButton.setStyle("-fx-background-color: #9E9E9E; -fx-text-fill: white;");
        backButton.setOnAction(e -> window.setScene(homeScene));

        layout.getChildren().addAll(infoLabel, lectureComboBox, refreshLecturesButton, removeButton, undoButton, backButton, localMessageBox);
        removeLectureScene = new Scene(layout, 400, 400);
        scenes.add(removeLectureScene);
        applyThemeToScene(removeLectureScene);
        window.setScene(removeLectureScene);
        if (isFullScreen)
            window.setFullScreen(true);
    }

    //---------------------- VIEW TIMETABLE WINDOW ----------------------
    // Creates a persistent grid with fixed header and time slots.
    private void openViewTimetableWindow() {
        if (viewTimetableScene == null) {
            timetableGrid = new GridPane();
            timetableGrid.setHgap(10);
            timetableGrid.setVgap(10);
            timetableGrid.setAlignment(Pos.CENTER);

            timetableCells = new Label[times.length][days.length];

            // Build header row.
            timetableGrid.add(new Label("Time"), 0, 0);
            for (int j = 0; j < days.length; j++) {
                Label dayLabel = new Label(days[j]);
                dayLabel.setStyle("-fx-font-weight: bold;");
                timetableGrid.add(dayLabel, j + 1, 0);
            }

            // Build rows: time labels and cells.
            for (int i = 0; i < times.length; i++) {
                Label timeLabel = new Label(times[i]);
                timeLabel.setStyle("-fx-font-weight: bold;");
                timetableGrid.add(timeLabel, 0, i + 1);
                for (int j = 0; j < days.length; j++) {
                    Label cell = new Label("---");
                    cell.setPrefWidth(150);
                    cell.setStyle("-fx-border-color: black; -fx-padding: 5;");
                    timetableGrid.add(cell, j + 1, i + 1);
                    timetableCells[i][j] = cell;
                }
            }

            Button refreshButton = new Button("Refresh");
            refreshButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
            refreshButton.setOnAction(e -> sendCommand("VIEW_TIMETABLE", response -> updateTimetableGrid(response)));

            Button exportCsvButton = new Button("Export to CSV");
            exportCsvButton.setStyle("-fx-background-color: #FFC107; -fx-text-fill: white;");
            exportCsvButton.setOnAction(e -> exportTimetableToCSV());

            Button importCsvButton = new Button("Import CSV");
            importCsvButton.setStyle("-fx-background-color: #ADFF2F; -fx-text-fill: white;");
            importCsvButton.setOnAction(e -> {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Import Timetable from CSV");
                fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
                File file = fileChooser.showOpenDialog(window);
                if (file != null) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                        StringBuilder csvContent = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            csvContent.append(line).append("\n");
                        }
                        sendImportCSV(csvContent.toString(), response -> {
                            Alert alert = new Alert(Alert.AlertType.INFORMATION);
                            alert.setTitle("Import Result");
                            alert.setHeaderText(null);
                            alert.setContentText(response);
                            alert.showAndWait();
                            sendCommand("VIEW_TIMETABLE", resp -> updateTimetableGrid(resp));
                        });
                    } catch (Exception ex) {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Import Failed");
                        alert.setHeaderText(null);
                        alert.setContentText("Error reading CSV file: " + ex.getMessage());
                        alert.showAndWait();
                    }
                }
            });

            Button statisticsButton = new Button("Statistics");
            statisticsButton.setStyle("-fx-background-color: #00BFFF; -fx-text-fill: white;");
            statisticsButton.setOnAction(e -> openStatisticsScene());

            Button backButton = new Button("Back");
            backButton.setStyle("-fx-background-color: #9E9E9E; -fx-text-fill: white;");
            backButton.setOnAction(e -> window.setScene(homeScene));

            HBox buttonBox = new HBox(10, refreshButton, exportCsvButton, importCsvButton, statisticsButton, backButton);
            buttonBox.setAlignment(Pos.CENTER);

            VBox layoutBox = new VBox(10, timetableGrid, buttonBox);
            layoutBox.setAlignment(Pos.CENTER);
            viewTimetableScene = new Scene(layoutBox, 800, 500);
        }
        scenes.add(viewTimetableScene);
        applyThemeToScene(viewTimetableScene);
        window.setScene(viewTimetableScene);
        if (isFullScreen)
            window.setFullScreen(true);
        sendCommand("VIEW_TIMETABLE", response -> updateTimetableGrid(response));
    }

    //---------------------- Statistics Scene ----------------------
    private void openStatisticsScene() {
        // Count scheduled lecture hours per day from the timetableCells array.
        int[] hoursPerDay = new int[days.length];
        for (int i = 0; i < timetableCells.length; i++) {
            for (int j = 0; j < timetableCells[i].length; j++) {
                String cellText = timetableCells[i][j].getText().trim();
                if (!cellText.equals("---") && !cellText.isEmpty()) {
                    hoursPerDay[j]++;
                }
            }
        }

        // Create the chart axes.
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Day");
        xAxis.setCategories(FXCollections.observableArrayList(days));
        NumberAxis yAxis = new NumberAxis(0, times.length, 1);
        yAxis.setLabel("Class Hours");

        // Create the BarChart.
        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        barChart.setTitle("Weekly Class Hours");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Scheduled Hours");
        for (int j = 0; j < days.length; j++) {
            series.getData().add(new XYChart.Data<>(days[j], hoursPerDay[j]));
        }
        barChart.getData().add(series);

        //Force bar fill color to orange for each data node.
        Platform.runLater(() -> {
            for (XYChart.Data<String, Number> data : series.getData()) {
                Node node = data.getNode();
                if (node != null) {
                    node.setStyle("-fx-bar-fill: orange;");
                }
            }
        });

        // Create a Back button to return to the timetable view.
        Button backButton = new Button("Back");
        backButton.setStyle("-fx-background-color: #9E9E9E; -fx-text-fill: white;");
        backButton.setOnAction(e -> window.setScene(viewTimetableScene));

        VBox statsLayout = new VBox(10, barChart, backButton);
        statsLayout.setAlignment(Pos.CENTER);
        statisticsScene = new Scene(statsLayout, 600, 400);
        scenes.add(statisticsScene);
        applyThemeToScene(statisticsScene);
        window.setScene(statisticsScene);
    }

    //---------------------- Update Timetable Grid ----------------------
    private void updateTimetableGrid(String response) {
        String[] lines = response.split("\n");
        if (lines.length < 2)
            return;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.length() < 10)
                continue;
            int row = i - 1;
            for (int j = 0; j < days.length; j++) {
                int start = 10 + j * 30;
                int end = start + 30;
                String cellData = line.length() >= end ? line.substring(start, end).trim() : "";
                if (cellData.isEmpty())
                    cellData = "---";
                timetableCells[row][j].setText(cellData);
            }
        }
    }

    //---------------------- Export Timetable to CSV ----------------------
    private void exportTimetableToCSV() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Timetable to CSV");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        fileChooser.setInitialFileName("timetable_export.csv");
        File file = fileChooser.showSaveDialog(window);
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                // Write header row.
                StringBuilder header = new StringBuilder("Time");
                for (String day : days) {
                    header.append(",").append(day);
                }
                writer.write(header.toString() + "\n");
                // Write each row.
                for (int i = 0; i < times.length; i++) {
                    StringBuilder row = new StringBuilder(times[i]);
                    for (int j = 0; j < days.length; j++) {
                        String cellText = timetableCells[i][j].getText();
                        if (cellText.contains(","))
                            cellText = "\"" + cellText + "\"";
                        row.append(",").append(cellText);
                    }
                    writer.write(row.toString() + "\n");
                }
                writer.flush();
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Export Successful");
                alert.setHeaderText(null);
                alert.setContentText("Timetable exported successfully!");
                alert.showAndWait();
            } catch (Exception ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Export Failed");
                alert.setHeaderText(null);
                alert.setContentText("Error exporting timetable: " + ex.getMessage());
                alert.showAndWait();
            }
        }
    }

    //---------------------- VIEW HISTORY WINDOW ----------------------
    private void openViewHistoryWindow() {
        if (viewHistoryScene == null) {
            TextArea historyArea = new TextArea();
            historyArea.setEditable(false);
            historyArea.setWrapText(true);
            historyArea.setPrefSize(800, 400);

            Button refreshButton = new Button("Refresh");
            refreshButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
            refreshButton.setOnAction(e -> sendCommand("VIEW_HISTORY", response -> historyArea.setText(response)));

            Button backButton = new Button("Back");
            backButton.setStyle("-fx-background-color: #9E9E9E; -fx-text-fill: white;");
            backButton.setOnAction(e -> window.setScene(homeScene));

            HBox buttonBox = new HBox(10, refreshButton, backButton);
            buttonBox.setAlignment(Pos.CENTER);

            VBox layout = new VBox(10, new Label("Timetable Change History:"), historyArea, buttonBox);
            layout.setAlignment(Pos.CENTER);
            viewHistoryScene = new Scene(layout, 800, 500);
        }
        scenes.add(viewHistoryScene);
        applyThemeToScene(viewHistoryScene);
        window.setScene(viewHistoryScene);
        if (isFullScreen)
            window.setFullScreen(true);
        sendCommand("VIEW_HISTORY", response -> {
            if (viewHistoryScene.getRoot() instanceof VBox) {
                VBox layout = (VBox) viewHistoryScene.getRoot();
                for (Node node : layout.getChildren()) {
                    if (node instanceof TextArea) {
                        ((TextArea) node).setText(response);
                        break;
                    }
                }
            }
        });
    }

    //---------------------- OTHER / LOGOUT / STOP CONNECTION ----------------------
    private void other() {
        try {
            throw new IncorrectActionException("Incorrect action. The system does not support this feature.");
        } catch (IncorrectActionException e) {
            communicationStatusArea.appendText("Error: " + e.getMessage() + "\n");
        }
    }

    private void logout() {
        window.close();
    }

    private void stopConnection() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                communicationStatusArea.appendText("Server: Disconnected.\n");
            }
        } catch (Exception e) {
            communicationStatusArea.appendText("Server: Failed to disconnect.\n");
        }
    }

    //---------------------- Theme Toggle ----------------------
    public void toggleTheme() {
        isDark = !isDark;
        applyThemeToScene(window.getScene());
    }

    public void applyThemeToScene(Scene scene) {
        if (isDark) {
            applyDarkMode(scene);
        } else {
            applyLightMode(scene);
        }
    }

    private void applyDarkMode(Scene scene) {
        scene.setFill(Color.BLACK);
        applyStyleToNodes(scene.getRoot(), "#333", Color.WHITE);
    }

    private void applyLightMode(Scene scene) {
        scene.setFill(Color.WHITE);
        applyStyleToNodes(scene.getRoot(), "#fff", Color.BLACK);
    }

    private void applyStyleToNodes(Node node, String backgroundColor, Color textColor) {
        if (node instanceof Label) {
            ((Label) node).setTextFill(textColor);
        } else if (node instanceof TextArea) {
            ((TextArea) node).setStyle("-fx-control-inner-background: " + (isDark ? "#555" : "#fff") +
                    "; -fx-text-fill: " + (isDark ? "white" : "black") + ";");
        } else if (node instanceof VBox || node instanceof HBox || node instanceof StackPane) {
            ((Region) node).setStyle("-fx-background-color: " + backgroundColor + ";");
        }
        if (node instanceof Parent) {
            for (Node child : ((Parent) node).getChildrenUnmodifiable()) {
                applyStyleToNodes(child, backgroundColor, textColor);
            }
        }
    }

    //---------------------- CUSTOM EXCEPTION ----------------------
    public static class IncorrectActionException extends Exception {
        public IncorrectActionException(String message) {
            super(message);
        }
    }
}