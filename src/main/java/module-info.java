module com.example.finmarkdataanalyzer {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.apache.commons.csv;
    requires weka.stable;
    requires java.net.http;
    requires com.google.gson;

    opens com.example.finmarkdataanalyzer to javafx.fxml;
    exports com.example.finmarkdataanalyzer;
}