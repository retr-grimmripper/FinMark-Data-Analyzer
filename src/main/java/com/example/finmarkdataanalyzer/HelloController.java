package com.example.finmarkdataanalyzer;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import weka.classifiers.Evaluation;
import weka.classifiers.functions.LinearRegression;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;

public class HelloController {

    @FXML private Button loadDataBtn;
    @FXML private Button runModelBtn;
    @FXML private Button resetBtn;
    @FXML private Label metricsLabel;
    @FXML private TableView<StockRecord> dataTable;

    // Notice the chart now expects <String, Number> to handle our text dates
    @FXML private LineChart<String, Number> stockChart;

    private ObservableList<StockRecord> stockDataList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTableColumns();

        runModelBtn.setDisable(true);
        resetBtn.setDisable(true);

        loadDataBtn.setOnAction(event -> openFilePicker());
        runModelBtn.setOnAction(event -> runMachineLearning());
        resetBtn.setOnAction(event -> resetDashboard());
    }

    private void setupTableColumns() {
        TableColumn<StockRecord, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));

        TableColumn<StockRecord, Double> openCol = new TableColumn<>("Open");
        openCol.setCellValueFactory(new PropertyValueFactory<>("open"));

        TableColumn<StockRecord, Double> closeCol = new TableColumn<>("Close");
        closeCol.setCellValueFactory(new PropertyValueFactory<>("close"));

        TableColumn<StockRecord, Long> volCol = new TableColumn<>("Volume");
        volCol.setCellValueFactory(new PropertyValueFactory<>("volume"));

        TableColumn<StockRecord, Double> sma10Col = new TableColumn<>("10-Day SMA");
        sma10Col.setCellValueFactory(new PropertyValueFactory<>("sma10"));

        TableColumn<StockRecord, Double> sma50Col = new TableColumn<>("50-Day SMA");
        sma50Col.setCellValueFactory(new PropertyValueFactory<>("sma50"));

        dataTable.getColumns().addAll(dateCol, openCol, closeCol, volCol, sma10Col, sma50Col);
        dataTable.setItems(stockDataList);
    }

    private void openFilePicker() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Google Sheets CSV Data");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files (*.csv)", "*.csv"));

        File file = fileChooser.showOpenDialog(loadDataBtn.getScene().getWindow());

        if (file != null) {
            loadCsvData(file);
        }
    }

    private void loadCsvData(File file) {
        stockDataList.clear();

        try (Reader in = new FileReader(file)) {
            Iterable<CSVRecord> records = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(in);

            for (CSVRecord record : records) {
                String date = record.get("Date");
                double open = Double.parseDouble(record.get("Open"));
                double high = Double.parseDouble(record.get("High"));
                double low = Double.parseDouble(record.get("Low"));
                double close = Double.parseDouble(record.get("Close"));
                long volume = (long) Double.parseDouble(record.get("Volume"));

                stockDataList.add(new StockRecord(date, open, high, low, close, volume));
            }

            calculateMovingAverages();

            // Unlock UI
            runModelBtn.setDisable(false);
            resetBtn.setDisable(false);
            metricsLabel.setText("Data Loaded. Awaiting Analysis...");

        } catch (Exception e) {
            System.out.println("Error reading the CSV file: " + e.getMessage());
        }
    }

    private void calculateMovingAverages() {
        if (stockDataList.size() < 50) return;

        for (int i = 50; i < stockDataList.size(); i++) {
            double sum10 = 0;
            for (int j = i - 10; j < i; j++) {
                sum10 += stockDataList.get(j).getClose();
            }
            stockDataList.get(i).setSma10(sum10 / 10.0);

            double sum50 = 0;
            for (int j = i - 50; j < i; j++) {
                sum50 += stockDataList.get(j).getClose();
            }
            stockDataList.get(i).setSma50(sum50 / 50.0);
        }
    }

    private void runMachineLearning() {
        if (stockDataList.size() < 50) return;

        try {
            ArrayList<Attribute> attributes = new ArrayList<>();
            attributes.add(new Attribute("sma10"));
            attributes.add(new Attribute("sma50"));
            attributes.add(new Attribute("close"));

            Instances dataset = new Instances("StockPredictions", attributes, stockDataList.size());
            dataset.setClassIndex(2);

            for (int i = 50; i < stockDataList.size(); i++) {
                StockRecord record = stockDataList.get(i);
                DenseInstance instance = new DenseInstance(3);
                instance.setValue(attributes.get(0), record.getSma10());
                instance.setValue(attributes.get(1), record.getSma50());
                instance.setValue(attributes.get(2), record.getClose());
                dataset.add(instance);
            }

            LinearRegression model = new LinearRegression();
            model.buildClassifier(dataset);

            // Calculate Model Accuracy Metrics
            Evaluation eval = new Evaluation(dataset);
            eval.evaluateModel(model, dataset);
            double mae = eval.meanAbsoluteError();
            double correlation = eval.correlationCoefficient();

            drawChartWithPredictions(model, dataset, mae, correlation);

        } catch (Exception e) {
            System.out.println("Weka Error: " + e.getMessage());
        }
    }

    private void drawChartWithPredictions(LinearRegression model, Instances dataset, double mae, double correlation) {
        Platform.runLater(() -> {
            stockChart.setAnimated(false);
            stockChart.setCreateSymbols(false);
            stockChart.getData().clear();

            XYChart.Series<String, Number> actualPriceSeries = new XYChart.Series<>();
            actualPriceSeries.setName("Actual Price");

            XYChart.Series<String, Number> aiPredictionSeries = new XYChart.Series<>();
            aiPredictionSeries.setName("AI Predicted Trend");

            for (int i = 50; i < stockDataList.size(); i++) {
                StockRecord record = stockDataList.get(i);

                actualPriceSeries.getData().add(new XYChart.Data<>(record.getDate(), record.getClose()));

                try {
                    double predictedPrice = model.classifyInstance(dataset.get(i - 50));
                    aiPredictionSeries.getData().add(new XYChart.Data<>(record.getDate(), predictedPrice));
                } catch (Exception e) { }
            }

            stockChart.getData().addAll(actualPriceSeries, aiPredictionSeries);

            // Update the UI Label with the math
            metricsLabel.setText(String.format("AI Model Performance | Mean Absolute Error: $%.2f | Correlation: %.2f", mae, correlation));
        });
    }

    // Safely wipe the dashboard clean for the next dataset
    private void resetDashboard() {
        stockDataList.clear();
        stockChart.getData().clear();
        metricsLabel.setText("AI Confidence Metrics will appear here...");
        runModelBtn.setDisable(true);
        resetBtn.setDisable(true);
    }
}