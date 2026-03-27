package com.example.finmarkdataanalyzer;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
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
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;

public class HelloController {

    @FXML private Button loadDataBtn;
    @FXML private Button runModelBtn;
    @FXML private Button exportBtn;
    @FXML private Button resetBtn;
    @FXML private Button themeToggleBtn;
    @FXML private Button chartToggleBtn;
    @FXML private Button expandChartBtn; // Wires up the Full Screen Button
    @FXML private Label metricsLabel;
    @FXML private TableView<StockRecord> dataTable;
    @FXML private LineChart<String, Number> stockChart;

    private final ObservableList<StockRecord> stockDataList = FXCollections.observableArrayList();
    private boolean isDarkMode = true;
    private boolean isCandlestickMode = false;
    private boolean isChartExpanded = false; // Tracks if the chart is full screen

    @FXML
    public void initialize() {
        setupTableColumns();

        runModelBtn.setDisable(true);
        exportBtn.setDisable(true);
        resetBtn.setDisable(true);
        chartToggleBtn.setDisable(true);
        expandChartBtn.setDisable(true); // Keeps it disabled until data loads

        loadDataBtn.setOnAction(e -> openFilePicker());
        runModelBtn.setOnAction(e -> runMachineLearning());
        exportBtn.setOnAction(e -> exportData());
        resetBtn.setOnAction(e -> resetDashboard());
        themeToggleBtn.setOnAction(e -> toggleTheme());
        expandChartBtn.setOnAction(e -> toggleChartFullScreen()); // Listens for the click

        chartToggleBtn.setOnAction(e -> {
            isCandlestickMode = !isCandlestickMode;
            chartToggleBtn.setText(isCandlestickMode ? "📈 Line Chart View" : "📊 Candlestick View");
            if (!stockDataList.isEmpty() && stockChart.getData().size() > 0) {
                runMachineLearning();
            }
        });
    }

    @SuppressWarnings("unchecked")
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

    private void toggleTheme() {
        if (isDarkMode) {
            if (applyTheme("light-theme.css")) {
                themeToggleBtn.setText("🌙 Dark Mode");
                themeToggleBtn.setStyle("-fx-background-color: #E5E7EB; -fx-text-fill: #1F2937;");
                metricsLabel.setStyle("-fx-text-fill: #111827; -fx-font-weight: bold;");
                isDarkMode = false;
            }
        } else {
            if (applyTheme("dark-theme.css")) {
                themeToggleBtn.setText("☀️ Light Mode");
                themeToggleBtn.setStyle("-fx-background-color: #374151; -fx-text-fill: #FBBF24;");
                metricsLabel.setStyle("-fx-text-fill: #E0E0E0; -fx-font-weight: bold;");
                isDarkMode = true;
            }
        }
    }

    private boolean applyTheme(String cssFileName) {
        try {
            Scene scene = themeToggleBtn.getScene();
            java.net.URL cssUrl = getClass().getResource("/css/" + cssFileName);
            if (cssUrl != null) {
                scene.getStylesheets().clear();
                scene.getStylesheets().add(cssUrl.toExternalForm());
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // --- FULL SCREEN CHART LOGIC ---
    private void toggleChartFullScreen() {
        isChartExpanded = !isChartExpanded;

        if (isChartExpanded) {
            // Hide the table and metrics to give the chart 100% of the screen
            dataTable.setVisible(false);
            dataTable.setManaged(false);
            metricsLabel.setVisible(false);
            metricsLabel.setManaged(false);
            expandChartBtn.setText("🗗 Minimize Chart");
        } else {
            // Bring everything back
            dataTable.setVisible(true);
            dataTable.setManaged(true);
            metricsLabel.setVisible(true);
            metricsLabel.setManaged(true);
            expandChartBtn.setText("🗖 Full Screen Chart");
        }
    }

    private void exportData() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Analysis Results");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files (*.csv)", "*.csv"));
        File file = fileChooser.showSaveDialog(exportBtn.getScene().getWindow());

        if (file != null) {
            try (PrintWriter writer = new PrintWriter(file)) {
                writer.println("Date,Open,Close,Volume,10-Day SMA,50-Day SMA");
                for (StockRecord record : stockDataList) {
                    writer.printf("%s,%.2f,%.2f,%d,%.2f,%.2f\n",
                            record.getDate(), record.getOpen(), record.getClose(),
                            record.getVolume(), record.getSma10(), record.getSma50());
                }
                metricsLabel.setText("Success: Data exported to " + file.getName());
            } catch (Exception e) {
                metricsLabel.setText("Error saving file.");
            }
        }
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

    @SuppressWarnings("deprecation")
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

            runModelBtn.setDisable(false);
            exportBtn.setDisable(false);
            resetBtn.setDisable(false);
            chartToggleBtn.setDisable(false);
            expandChartBtn.setDisable(false); // WAKES UP THE BUTTON
            metricsLabel.setText("Data Loaded. Awaiting Analysis...");

        } catch (Exception e) {
            metricsLabel.setText("Error reading the CSV file.");
        }
    }

    private void calculateMovingAverages() {
        if (stockDataList.size() < 50) return;
        for (int i = 50; i < stockDataList.size(); i++) {
            double sum10 = 0;
            for (int j = i - 10; j < i; j++) { sum10 += stockDataList.get(j).getClose(); }
            stockDataList.get(i).setSma10(sum10 / 10.0);

            double sum50 = 0;
            for (int j = i - 50; j < i; j++) { sum50 += stockDataList.get(j).getClose(); }
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

            Evaluation eval = new Evaluation(dataset);
            eval.evaluateModel(model, dataset);
            double mae = eval.meanAbsoluteError();
            double correlation = eval.correlationCoefficient();

            drawChartWithPredictions(model, dataset, mae, correlation);

        } catch (Exception e) {
            metricsLabel.setText("Weka Error: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void drawChartWithPredictions(LinearRegression model, Instances dataset, double mae, double correlation) {
        Platform.runLater(() -> {
            stockChart.setAnimated(false);
            stockChart.setCreateSymbols(isCandlestickMode);
            stockChart.getData().clear();

            XYChart.Series<String, Number> actualPriceSeries = new XYChart.Series<>();
            actualPriceSeries.setName("Actual Price");

            XYChart.Series<String, Number> aiPredictionSeries = new XYChart.Series<>();
            aiPredictionSeries.setName("AI Predicted Trend");

            NumberAxis yAxis = (NumberAxis) stockChart.getYAxis();

            for (int i = 50; i < stockDataList.size(); i++) {
                StockRecord record = stockDataList.get(i);

                XYChart.Data<String, Number> dataPoint = new XYChart.Data<>(record.getDate(), record.getClose());

                if (isCandlestickMode) {
                    dataPoint.setNode(new CandleNode(record, yAxis));
                }
                actualPriceSeries.getData().add(dataPoint);

                try {
                    double predictedPrice = model.classifyInstance(dataset.get(i - 50));
                    aiPredictionSeries.getData().add(new XYChart.Data<>(record.getDate(), predictedPrice));
                } catch (Exception e) { }
            }

            stockChart.getData().addAll(actualPriceSeries, aiPredictionSeries);

            if (isCandlestickMode) {
                actualPriceSeries.getNode().setStyle("-fx-stroke: transparent;");
                for (XYChart.Data<String, Number> data : aiPredictionSeries.getData()) {
                    if (data.getNode() != null) {
                        data.getNode().setVisible(false);
                    }
                }
            }

            metricsLabel.setText(String.format("AI Model Performance | Mean Absolute Error: $%.2f | Correlation: %.2f", mae, correlation));
        });
    }

    private void resetDashboard() {
        stockDataList.clear();
        stockChart.getData().clear();
        metricsLabel.setText("AI Confidence Metrics will appear here...");

        // Disable buttons
        runModelBtn.setDisable(true);
        exportBtn.setDisable(true);
        resetBtn.setDisable(true);
        chartToggleBtn.setDisable(true);
        expandChartBtn.setDisable(true);

        // Ensure chart goes back to normal size on reset
        if (isChartExpanded) {
            toggleChartFullScreen();
        }
    }

    // =================================================================================
    // THE CANDLESTICK RENDERER
    // =================================================================================
    private static class CandleNode extends Group {
        private final Line wick = new Line();
        private final Region body = new Region();
        private final StockRecord record;
        private final NumberAxis yAxis;

        public CandleNode(StockRecord record, NumberAxis yAxis) {
            this.record = record;
            this.yAxis = yAxis;
            getChildren().addAll(wick, body);

            boolean isBullish = record.getClose() >= record.getOpen();
            String colorHex = isBullish ? "#10B981" : "#EF4444";

            wick.setStroke(Color.web(colorHex));
            wick.setStrokeWidth(1.5);

            body.setStyle("-fx-background-color: " + colorHex + ";");
            body.setPrefWidth(8);
            body.setLayoutX(-4);

            yAxis.lowerBoundProperty().addListener((obs, oldV, newV) -> Platform.runLater(this::updatePixels));
            yAxis.upperBoundProperty().addListener((obs, oldV, newV) -> Platform.runLater(this::updatePixels));
            yAxis.heightProperty().addListener((obs, oldV, newV) -> Platform.runLater(this::updatePixels));

            Platform.runLater(this::updatePixels);
        }

        private void updatePixels() {
            if (yAxis.getHeight() == 0) return;

            double closeY = yAxis.getDisplayPosition(record.getClose());
            double openY = yAxis.getDisplayPosition(record.getOpen());
            double highY = yAxis.getDisplayPosition(record.getHigh());
            double lowY = yAxis.getDisplayPosition(record.getLow());

            wick.setStartY(highY - closeY);
            wick.setEndY(lowY - closeY);

            double topY = Math.min(openY, closeY) - closeY;
            double bottomY = Math.max(openY, closeY) - closeY;

            body.setLayoutY(topY);
            body.setPrefHeight(Math.max(bottomY - topY, 1));
        }
    }
}