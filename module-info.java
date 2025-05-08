module com.example.timetableapp {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.example.timetableapp to javafx.fxml;
    exports com.example.timetableapp;
}