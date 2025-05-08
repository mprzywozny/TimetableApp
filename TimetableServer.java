package com.example.timetableapp;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.scene.paint.Color;
import javafx.scene.Node;
import javafx.scene.Parent;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class TimetableServer extends Application {

    // Thread-safe collections for the timetable and history.
    // Outer map: Day -> (Time Slot -> Lecture)
    private static ConcurrentHashMap<String, ConcurrentHashMap<String, Lecture>> timetable = new ConcurrentHashMap<>();
    private static List<String> historyLog = Collections.synchronizedList(new ArrayList<>());
    // A map to store the removed lectures for undo functionality (key: "day,time", value: Lecture)
    private static Map<String, Lecture> removedLectures = new HashMap<>();

    private boolean isDark = false; // Light mode set by default
    private static ServerSocket serverSocket;
    private TextArea communicationStatusArea;
    private Button startServerButton;
    private Button stopServerButton;
    private Stage window;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        window = primaryStage;

        // Communication status area.
        communicationStatusArea = new TextArea();
        communicationStatusArea.setEditable(false);
        communicationStatusArea.setMaxHeight(150);

        startServerButton = new Button("Start Server");
        startServerButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 14px;");
        startServerButton.setOnAction(e -> startServer());

        stopServerButton = new Button("Stop Server");
        stopServerButton.setStyle("-fx-background-color: #F44336; -fx-text-fill: white; -fx-font-size: 14px;");
        stopServerButton.setOnAction(e -> stopServer());

        // Toggle theme button
        Button toggleThemeButton = new Button("Change Theme");
        toggleThemeButton.setStyle("-fx-background-color: #FFB6C1; -fx-text-fill: white; -fx-font-size: 14px;");
        toggleThemeButton.setOnAction(e -> toggleTheme());

        VBox layout = new VBox(20);
        layout.setAlignment(Pos.CENTER);
        layout.getChildren().addAll(startServerButton, stopServerButton, communicationStatusArea, toggleThemeButton);

        Scene scene = new Scene(layout, 400, 300);
        window.setTitle("Lecture Server");
        window.setScene(scene);
        window.show();
    }

    //-----------------SWITCH BETWEEN LIGHT/DARK MODE-------------------
    public void toggleTheme() {
        isDark = !isDark;
        applyThemeToScene(window.getScene());
    }

    private void applyThemeToScene(Scene scene) {
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
    //---------------------- SERVER CONTROL METHODS ----------------------
    private void startServer() {
        try {
            serverSocket = new ServerSocket(12346);
            communicationStatusArea.appendText("Waiting for connection...\n");
            new Thread(this::acceptClients).start();
        } catch (IOException e) {
            communicationStatusArea.appendText("Error: Unable to start server.\n");
        }
    }

    private void acceptClients() {
        try {
            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                communicationStatusArea.appendText("Client connected\n");
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            communicationStatusArea.appendText("Error: Unable to accept client connection.\n");
        }
    }

    private void stopServer() {
        try {
            if (serverSocket != null && !serverSocket.isClosed())
                serverSocket.close();
            communicationStatusArea.appendText("Stopped\n");
        } catch (IOException e) {
            communicationStatusArea.appendText("Error: Unable to stop server.\n");
        }
    }

    //---------------------- Client Handler ----------------------
    static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try {
                in  = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                out = new PrintWriter(clientSocket.getOutputStream(), true);

                String clientMessage;
                while ((clientMessage = in.readLine()) != null) {
                    System.out.println("Client: " + clientMessage);

                    if (clientMessage.startsWith("ADD_LECTURE,")) {
                        handleAddLecture(clientMessage);
                    } else if (clientMessage.startsWith("REMOVE_LECTURE,")) {
                        handleRemoveLecture(clientMessage);
                    } else if (clientMessage.startsWith("UNDO_REMOVE,")) {
                        // New branch for undo remove command.
                        handleUndoRemove(clientMessage);
                    } else if (clientMessage.equals("VIEW_TIMETABLE")) {
                        handleViewTimetable();
                    } else if (clientMessage.equals("VIEW_HISTORY")) {
                        handleViewHistory();
                    } else if (clientMessage.equals("EARLY_LECTURES")) {
                        handleEarlyLectures();
                    } else if (clientMessage.equals("GET_LECTURES")) {
                        handleGetLectures();
                    } else if (clientMessage.equals("IMPORT_CSV")) {
                        handleImportCSV();
                    } else if (clientMessage.equals("STOP_CONNECTION")) {
                        handleStopConnection();
                        break;
                    } else {
                        out.println("Unknown Command");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                closeClientConnection();
            }
        }

        // ---------- ADD_LECTURE ----------
        private synchronized void handleAddLecture(String command) {
            try {
                String[] parts = command.split(",");
                if (parts.length < 6) {
                    out.println("Invalid ADD_LECTURE command format.");
                    return;
                }
                String time = parts[1].trim();
                String day = parts[2].trim();
                String className = parts[3].trim();
                String moduleName = parts[4].trim();
                String lectureType = parts[5].trim();

                timetable.putIfAbsent(day, new ConcurrentHashMap<>());
                if (timetable.get(day).containsKey(time)) {
                    out.println("Timeslot already taken! Choose another time.");
                    return;
                }
                Lecture newLecture = new Lecture(time, day, className, moduleName, lectureType);
                timetable.get(day).put(time, newLecture);

                String logEntry = String.format("Lecture added: %s | %s | %s | %s | %s : %s",
                        day, time, className, moduleName, lectureType, new java.util.Date());
                historyLog.add(logEntry);

                out.println("Lecture added successfully.");
            } catch (Exception e) {
                out.println("Error while adding lecture.");
            }
        }

        // ---------- REMOVE_LECTURE ----------
        private synchronized void handleRemoveLecture(String command) {
            try {
                String[] parts = command.split(",");
                if (parts.length < 3) {
                    out.println("Invalid REMOVE_LECTURE command format.");
                    return;
                }
                String day = parts[1].trim();
                String time = parts[2].trim();
                if (timetable.containsKey(day) && timetable.get(day).containsKey(time)) {
                    Lecture removedLecture = timetable.get(day).get(time);
                    String logEntry = String.format("Lecture removed: %s | %s | %s | %s | %s : %s",
                            day, time, removedLecture.getClassName(), removedLecture.getModule(),
                            removedLecture.getLectureType(), new java.util.Date());
                    historyLog.add(logEntry);

                    // Store the removed lecture for undo functionality.
                    removedLectures.put(day + "," + time, removedLecture);

                    timetable.get(day).remove(time);
                    if (timetable.get(day).isEmpty())
                        timetable.remove(day);

                    out.println("Lecture removed successfully.");
                } else {
                    out.println("No lecture scheduled in this time slot.");
                }
            } catch (Exception e) {
                out.println("Error while removing lecture.");
            }
        }

        // ---------- UNDO_REMOVE ----------
        private synchronized void handleUndoRemove(String command) {
            try {
                // Expected command format: "UNDO_REMOVE,day,time"
                String[] parts = command.split(",");
                if (parts.length < 3) {
                    out.println("Invalid UNDO_REMOVE command format.");
                    return;
                }
                String day = parts[1].trim();
                String time = parts[2].trim();
                String key = day + "," + time;
                if (removedLectures.containsKey(key)) {
                    Lecture removedLecture = removedLectures.get(key);
                    // Check if slot is occupied (should not be)
                    if (timetable.containsKey(day) && timetable.get(day).containsKey(time)) {
                        out.println("Undo failed: Timeslot already occupied.");
                        return;
                    }
                    timetable.putIfAbsent(day, new ConcurrentHashMap<>());
                    timetable.get(day).put(time, removedLecture);

                    String logEntry = String.format("Lecture restored: %s | %s | %s | %s | %s : %s",
                            day, time, removedLecture.getClassName(), removedLecture.getModule(),
                            removedLecture.getLectureType(), new java.util.Date());
                    historyLog.add(logEntry);

                    // Remove from undo map since it's been restored.
                    removedLectures.remove(key);
                    out.println("Undo successful: Lecture restored.");
                } else {
                    out.println("No lecture to undo.");
                }
            } catch (Exception e) {
                out.println("Error: cannot undo.");
            }
        }

        // ---------- VIEW_TIMETABLE ----------
        private synchronized void handleViewTimetable() {
            StringBuilder sb = new StringBuilder();
            String[] headerDays = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};
            sb.append(String.format("%-10s", ""));
            for (String d : headerDays) {
                sb.append(String.format("%-30s", d));
            }
            sb.append("\n");
            String[] timeSlots = {"9-10", "10-11", "11-12", "12-1", "1-2", "2-3", "3-4", "4-5", "5-6"};
            for (String time : timeSlots) {
                sb.append(String.format("%-10s", time));
                for (String day : headerDays) {
                    String cell = "---";
                    if (timetable.containsKey(day) && timetable.get(day).containsKey(time)) {
                        Lecture lec = timetable.get(day).get(time);
                        cell = lec.getClassName() + "(" + lec.getModule() + "," + lec.getLectureType() + ")";
                    }
                    sb.append(String.format("%-30s", cell));
                }
                sb.append("\n");
            }
            out.println(sb.toString());
        }

        // ---------- VIEW_HISTORY ----------
        private synchronized void handleViewHistory() {
            StringBuilder sb = new StringBuilder();
            if (historyLog.isEmpty()) {
                sb.append("No history available.");
            } else {
                synchronized (historyLog) {
                    for (String entry : historyLog) {
                        sb.append(entry).append("\n");
                    }
                }
            }
            out.println(sb.toString());
        }

        // ---------- EARLY_LECTURES ----------
        private void handleEarlyLectures() {
            String result = shiftEarlyLectures();
            out.println("Early lectures processed:\n" + result);
        }

        // ---------- GET_LECTURES ----------
        private void handleGetLectures() {
            StringBuilder sb = new StringBuilder();
            if (timetable.isEmpty()) {
                sb.append("No lectures available.");
            } else {
                List<String> sortedDays = new ArrayList<>(timetable.keySet());
                Collections.sort(sortedDays);
                for (String day : sortedDays) {
                    ConcurrentHashMap<String, Lecture> daySchedule = timetable.get(day);
                    List<String> sortedTimes = new ArrayList<>(daySchedule.keySet());
                    sortedTimes.sort((t1, t2) -> {
                        try {
                            return Integer.compare(
                                    Integer.parseInt(t1.split("-")[0].trim()),
                                    Integer.parseInt(t2.split("-")[0].trim())
                            );
                        } catch (Exception e) {
                            return t1.compareTo(t2);
                        }
                    });
                    for (String time : sortedTimes) {
                        Lecture lec = daySchedule.get(time);
                        sb.append(day).append(",").append(time).append(": ")
                                .append(lec.getClassName()).append(" (")
                                .append(lec.getModule()).append(", ")
                                .append(lec.getLectureType()).append(")")
                                .append("\n");
                    }
                }
            }
            out.println(sb.toString());
        }

        // ---------- IMPORT_CSV ----------
        // Follows protocol: after "IMPORT_CSV", expect "BEGIN_CSV" then CSV lines then "END_CSV".
        private void handleImportCSV() {
            try {
                String marker = in.readLine();
                if (marker == null || !marker.equals("BEGIN_CSV")) {
                    out.println("IMPORT_CSV error: Expected BEGIN_CSV marker.");
                    return;
                }
                StringBuilder csvData = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    if(line.equals("END_CSV"))
                        break;
                    csvData.append(line).append("\n");
                }
                // Parse CSV and replace the timetable.
                parseCSVAndReplaceTimetable(csvData.toString());
                historyLog.add(String.format("Timetable imported via CSV (replaced current timetable): %s", new java.util.Date()));
                out.println("Timetable imported successfully.");
            } catch (Exception ex) {
                out.println("IMPORT_CSV failed: " + ex.getMessage());
            }
        }

        // ---------- STOP_CONNECTION ----------
        private void handleStopConnection() {
            out.println("Connection Stopped");
            System.out.println("Connection stopped.");
        }

        private void closeClientConnection() {
            try {
                if (clientSocket != null)
                    clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //---------------------- EARLY_LECTURES SHIFTING ----------------------
    private static synchronized String shiftEarlyLectures() {
        String[] earlySlots = {"9-10", "10-11", "11-12", "12-1"};
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<String>> futures = new ArrayList<>();

        for (String day : timetable.keySet()) {
            Callable<String> task = () -> {
                ConcurrentHashMap<String, Lecture> daySchedule = timetable.get(day);
                for (String earlySlot : earlySlots) {
                    if (!daySchedule.containsKey(earlySlot)) {
                        String candidate = null;
                        for (String timeKey : daySchedule.keySet()) {
                            boolean isEarly = false;
                            for (String early : earlySlots) {
                                if (early.equals(timeKey)) {
                                    isEarly = true;
                                    break;
                                }
                            }
                            if (!isEarly) {
                                if (candidate == null || compareTime(timeKey, candidate) < 0)
                                    candidate = timeKey;
                            }
                        }
                        if (candidate != null) {
                            Lecture lecture = daySchedule.remove(candidate);
                            Lecture shiftedLecture = new Lecture(earlySlot, lecture.getDay(),
                                    lecture.getClassName(), lecture.getModule(), lecture.getLectureType());
                            daySchedule.put(earlySlot, shiftedLecture);
                            synchronized (historyLog) {
                                historyLog.add(String.format("Lecture early shift: %s moved from %s to %s on %s : %s",
                                        lecture.getModule(), candidate, earlySlot, day, new java.util.Date()));
                            }
                        }
                    }
                }
                return "Day " + day + " processed.";
            };
            futures.add(executor.submit(task));
        }

        StringBuilder summary = new StringBuilder();
        for (Future<String> f : futures) {
            try {
                summary.append(f.get()).append("\n");
            } catch (Exception e) {
                summary.append("Error shifting lectures.\n");
            }
        }
        executor.shutdown();
        return summary.toString();
    }

    //---------------------- Helper to compare time strings ----------------------
    private static int compareTime(String t1, String t2) {
        try {
            int h1 = Integer.parseInt(t1.split("-")[0].trim());
            int h2 = Integer.parseInt(t2.split("-")[0].trim());
            return Integer.compare(h1, h2);
        } catch (Exception e) {
            return 0;
        }
    }

    //---------------------- CSV IMPORT METHOD ----------------------
    // Expects CSV text with header:
    // Time,Monday,Tuesday,Wednesday,Thursday,Friday
    // and each row contains exactly 6 fields. Uses a custom parser to handle quoted fields.
    private static synchronized void parseCSVAndReplaceTimetable(String csvText) throws Exception {
        String[] lines = csvText.split("\n");
        if (lines.length < 2)
            throw new Exception("CSV does not contain header and at least one data row.");

        // Validate header.
        String header = lines[0].trim();
        if (!header.equals("Time,Monday,Tuesday,Wednesday,Thursday,Friday"))
            throw new Exception("CSV header is incorrect. Expected: Time,Monday,Tuesday,Wednesday,Thursday,Friday");

        String[] allowedTimes = {"9-10", "10-11", "11-12", "12-1", "1-2", "2-3", "3-4", "4-5"};
        ConcurrentHashMap<String, ConcurrentHashMap<String, Lecture>> newTimetable = new ConcurrentHashMap<>();

        // Process each data row.
        for (int i = 1; i < lines.length; i++) {
            String row = lines[i];
            if (row.trim().isEmpty())
                continue;
            // Use custom CSV parser for exactly 6 columns.
            String[] cols = parseCSVLine(row, 6);

            String time = cols[0].trim();
            boolean validTime = false;
            for (String t : allowedTimes) {
                if (t.equals(time)) {
                    validTime = true;
                    break;
                }
            }
            if (!validTime)
                throw new Exception("Invalid time slot on row " + (i+1) + ": " + time);

            for (int d = 0; d < 5; d++) {
                String day = "";
                switch(d) {
                    case 0: day = "Monday"; break;
                    case 1: day = "Tuesday"; break;
                    case 2: day = "Wednesday"; break;
                    case 3: day = "Thursday"; break;
                    case 4: day = "Friday"; break;
                }
                String cell = cols[d+1].trim();
                // Remove surrounding quotes if any.
                if ((cell.startsWith("\"") && cell.endsWith("\"")) || (cell.startsWith("'") && cell.endsWith("'"))) {
                    cell = cell.substring(1, cell.length()-1).trim();
                }
                if (cell.equals("---") || cell.isEmpty())
                    continue;
                // Expected format: LectureName(Module,LectureType)
                int idxOpen = cell.indexOf("(");
                int idxClose = cell.lastIndexOf(")");
                if (idxOpen < 0 || idxClose < 0 || idxClose < idxOpen)
                    throw new Exception("Invalid lecture format in row " + (i+1) + " for " + day + ": " + cell);
                String lectureName = cell.substring(0, idxOpen).trim();
                String inside = cell.substring(idxOpen + 1, idxClose).trim();
                String[] parts = inside.split(",", 2);
                if (parts.length < 2)
                    throw new Exception("Invalid lecture details in row " + (i+1) + " for " + day + ": " + cell);
                String module = parts[0].trim();
                String lectureType = parts[1].trim();
                newTimetable.putIfAbsent(day, new ConcurrentHashMap<>());
                if (newTimetable.get(day).containsKey(time))
                    throw new Exception("Duplicate lecture for " + day + " at " + time);
                Lecture lec = new Lecture(time, day, lectureName, module, lectureType);
                newTimetable.get(day).put(time, lec);
            }
        }
        timetable = newTimetable;
    }

    //---------------------- Helper: Parse CSV Line ----------------------
    // Splits a CSV line into exactly expectedFields fields, respecting quotes.
    private static String[] parseCSVLine(String line, int expectedFields) throws Exception {
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString());
        if (fields.size() != expectedFields)
            throw new Exception("Expected " + expectedFields + " columns, but got " + fields.size() + ". Line: " + line);
        return fields.toArray(new String[0]);
    }

    //---------------------- Lecture Class ----------------------
    static class Lecture {
        private final String time;
        private final String day;
        private final String className;
        private final String module;
        private final String lectureType;

        public Lecture(String time, String day, String className, String module, String lectureType) {
            this.time = time;
            this.day = day;
            this.className = className;
            this.module = module;
            this.lectureType = lectureType;
        }

        public String getTime() { return time; }
        public String getDay() { return day; }
        public String getClassName() { return className; }
        public String getModule() { return module; }
        public String getLectureType() { return lectureType; }
    }
}