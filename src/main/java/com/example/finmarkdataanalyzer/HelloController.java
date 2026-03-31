package com.example.finmarkdataanalyzer;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Line;
import javafx.stage.FileChooser;
import javafx.util.Duration;
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

    private String alphaVantageKey = "TOA5JW2J8EOS6JDD";
    private int shortSmaPeriod = 10;
    private int longSmaPeriod = 50;

    @FXML private StackPane contentArea;
    @FXML private VBox dashboardView;
    @FXML private Button navDashboardBtn, navCryptoBtn, navStocksBtn, navMineralsBtn, navNewsBtn, navOfflineBtn, navSettingsBtn;
    @FXML private Button loadDataBtn, fetchLiveBtn, runModelBtn, exportBtn, resetBtn, themeToggleBtn, chartToggleBtn, expandChartBtn;
    @FXML private Label metricsLabel, signalLabel, rsiLabel;
    @FXML private TableView<StockRecord> dataTable;
    @FXML private LineChart<String, Number> stockChart;

    @FXML private Label portfolioLabel;
    @FXML private TextField tradeQuantityField;
    @FXML private Button executeBuyBtn;
    @FXML private Button executeSellBtn;

    private double portfolioCash = 10000.00;
    private int ownedShares = 0;
    private double lastKnownPrice = 0.0;

    private final ObservableList<StockRecord> stockDataList = FXCollections.observableArrayList();
    private boolean isDarkMode = true;
    private boolean isCandlestickMode = false;
    private boolean isChartExpanded = false;

    private String currentLiveCoin = "bitcoin";
    private String currentStockSymbol = "AAPL";
    private String currentActiveAsset = "Asset";

    // Tracks the actual ticker symbol for the News API to use
    private String currentNewsTicker = "AAPL";

    private Timeline autoPoller;
    private final FinMarkLoader globalLoader = new FinMarkLoader();

    @FXML
    public void initialize() {
        setupTableColumns();

        runModelBtn.setDisable(true); exportBtn.setDisable(true); resetBtn.setDisable(true);
        chartToggleBtn.setDisable(true); expandChartBtn.setDisable(true);
        executeBuyBtn.setDisable(true); executeSellBtn.setDisable(true);

        loadDataBtn.setOnAction(e -> openFilePicker());

        fetchLiveBtn.setOnAction(e -> {
            fetchLiveCryptoData(currentLiveCoin);
            startAutoPoller();
        });

        runModelBtn.setOnAction(e -> runMachineLearning());
        exportBtn.setOnAction(e -> exportData());
        resetBtn.setOnAction(e -> resetDashboard());
        themeToggleBtn.setOnAction(e -> toggleTheme());
        expandChartBtn.setOnAction(e -> toggleChartFullScreen());

        executeBuyBtn.setOnAction(e -> executeTrade("BUY"));
        executeSellBtn.setOnAction(e -> executeTrade("SELL"));

        chartToggleBtn.setOnAction(e -> {
            isCandlestickMode = !isCandlestickMode;
            chartToggleBtn.setText(isCandlestickMode ? "📈 Line Chart View" : "📊 Candlestick View");
            if (!stockDataList.isEmpty() && !stockChart.getData().isEmpty()) runMachineLearning();
        });

        // ROUTER WIRING
        navDashboardBtn.setOnAction(e -> switchView(dashboardView, navDashboardBtn));
        navCryptoBtn.setOnAction(e -> switchView(buildCryptoMarketView(), navCryptoBtn));
        navStocksBtn.setOnAction(e -> switchView(buildStocksMarketView(), navStocksBtn));
        navMineralsBtn.setOnAction(e -> switchView(buildMineralsMarketView(), navMineralsBtn));
        navNewsBtn.setOnAction(e -> switchView(buildNewsView(), navNewsBtn)); // NEW
        navOfflineBtn.setOnAction(e -> switchView(dashboardView, navOfflineBtn));
        navSettingsBtn.setOnAction(e -> switchView(buildSettingsView(), navSettingsBtn));

        contentArea.getChildren().add(globalLoader);
    }

    private void startAutoPoller() {
        if (autoPoller != null) autoPoller.stop();
        autoPoller = new Timeline(new KeyFrame(Duration.seconds(60), e -> {
            metricsLabel.setText("Background Syncing... Fetching latest data.");
            fetchLiveCryptoDataBackground(currentLiveCoin);
        }));
        autoPoller.setCycleCount(Animation.INDEFINITE);
        autoPoller.play();
    }

    private void executeTrade(String action) {
        if (lastKnownPrice <= 0) return;
        int quantity = 0;
        try {
            quantity = Integer.parseInt(tradeQuantityField.getText());
            if (quantity <= 0) { metricsLabel.setText("❌ Trade Failed: Quantity must be 1 or greater."); return; }
        } catch (NumberFormatException e) { metricsLabel.setText("❌ Trade Failed: Please enter a valid number."); return; }

        double totalCost = quantity * lastKnownPrice;

        if (action.equals("BUY")) {
            if (portfolioCash >= totalCost) {
                portfolioCash -= totalCost; ownedShares += quantity;
                metricsLabel.setText("✅ Bought " + quantity + " " + currentActiveAsset + " for $" + String.format("%.2f", totalCost));
            } else { metricsLabel.setText("❌ Trade Failed: Insufficient funds."); }
        } else if (action.equals("SELL")) {
            if (ownedShares >= quantity) {
                portfolioCash += totalCost; ownedShares -= quantity;
                metricsLabel.setText("✅ Sold " + quantity + " " + currentActiveAsset + " for $" + String.format("%.2f", totalCost));
            } else { metricsLabel.setText("❌ Trade Failed: You only own " + ownedShares + " shares."); }
        }
        updatePortfolioUI();
    }

    private void updatePortfolioUI() {
        double totalValue = portfolioCash + (ownedShares * lastKnownPrice);
        String valueColor = totalValue >= 10000.00 ? "#10B981" : "#EF4444";
        Platform.runLater(() -> {
            portfolioLabel.setText(String.format("💰 Cash: $%,.2f  |  📦 Owned: %d  |  📈 Total Value: $%,.2f", portfolioCash, ownedShares, totalValue));
            portfolioLabel.setStyle("-fx-text-fill: " + valueColor + "; -fx-font-weight: bold; -fx-font-size: 16px;");
        });
    }

    // =================================================================================
    // DYNAMIC UI BUILDERS
    // =================================================================================

    private VBox buildCryptoMarketView() {
        VBox view = new VBox(20); view.setAlignment(Pos.TOP_CENTER); view.setStyle("-fx-padding: 40px; -fx-background-color: transparent;");
        Label title = new Label("Live Cryptocurrency Markets"); title.setStyle("-fx-text-fill: white; -fx-font-size: 28px; -fx-font-weight: bold;");
        String[][] coins = { {"Bitcoin", "bitcoin", "CRYPTO:BTC", "₿"}, {"Ethereum", "ethereum", "CRYPTO:ETH", "Ξ"}, {"Solana", "solana", "CRYPTO:SOL", "◎"}, {"Cardano", "cardano", "CRYPTO:ADA", "₳"} };
        VBox list = new VBox(15); list.setMaxWidth(600); list.setAlignment(Pos.CENTER);
        for(String[] coin : coins) {
            Button btn = createMenuButton(coin[3] + "  " + coin[0], "#10B981");
            btn.setOnAction(e -> {
                currentLiveCoin = coin[1]; currentActiveAsset = coin[0]; currentNewsTicker = coin[2]; // Save the crypto ticker for news
                switchView(dashboardView, navDashboardBtn); fetchLiveCryptoData(currentLiveCoin); startAutoPoller();
            });
            list.getChildren().add(btn);
        }
        view.getChildren().addAll(title, list); return view;
    }

    private VBox buildStocksMarketView() {
        VBox view = new VBox(20); view.setAlignment(Pos.TOP_CENTER); view.setStyle("-fx-padding: 40px; -fx-background-color: transparent;");
        Label title = new Label("Global Stock Markets"); title.setStyle("-fx-text-fill: white; -fx-font-size: 28px; -fx-font-weight: bold;");
        String[][] stocks = { {"Apple Inc.", "AAPL", "🍏"}, {"Tesla Motors", "TSLA", "⚡"}, {"Microsoft", "MSFT", "💻"}, {"NVIDIA", "NVDA", "🎮"} };
        VBox list = new VBox(15); list.setMaxWidth(600); list.setAlignment(Pos.CENTER);
        for(String[] stock : stocks) {
            Button btn = createMenuButton(stock[2] + "  " + stock[0] + " (" + stock[1] + ")", "#3B82F6");
            btn.setOnAction(e -> {
                currentStockSymbol = stock[1]; currentActiveAsset = stock[1]; currentNewsTicker = stock[1]; // Save stock ticker for news
                switchView(dashboardView, navDashboardBtn); fetchLiveStockData(currentStockSymbol);
            });
            list.getChildren().add(btn);
        }
        view.getChildren().addAll(title, list); return view;
    }

    private VBox buildMineralsMarketView() {
        VBox view = new VBox(20); view.setAlignment(Pos.TOP_CENTER); view.setStyle("-fx-padding: 40px; -fx-background-color: transparent;");
        Label title = new Label("Minerals & Foreign Exchange"); title.setStyle("-fx-text-fill: white; -fx-font-size: 28px; -fx-font-weight: bold;");
        String[][] assets = { {"Euro / US Dollar", "EUR", "USD", "EURUSD", "💶"}, {"British Pound / USD", "GBP", "USD", "GBPUSD", "💷"} };
        VBox list = new VBox(15); list.setMaxWidth(600); list.setAlignment(Pos.CENTER);
        for(String[] asset : assets) {
            Button btn = createMenuButton(asset[4] + "  " + asset[0] + " (" + asset[1] + "/" + asset[2] + ")", "#F59E0B");
            btn.setOnAction(e -> {
                currentActiveAsset = asset[1]; currentNewsTicker = "FOREX:" + asset[3];
                switchView(dashboardView, navDashboardBtn); fetchLiveForexData(asset[1], asset[2], asset[0]);
            });
            list.getChildren().add(btn);
        }
        view.getChildren().addAll(title, list); return view;
    }

    // --- NEW: THE NEWS & SENTIMENT UI BUILDER ---
    private VBox buildNewsView() {
        VBox view = new VBox(20); view.setAlignment(Pos.TOP_CENTER); view.setStyle("-fx-padding: 30px; -fx-background-color: transparent;");

        Label title = new Label("📰 Live News & Sentiment");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 28px; -fx-font-weight: bold;");
        Label subTitle = new Label("Fetching latest global headlines and AI Sentiment analysis for: " + currentActiveAsset);
        subTitle.setStyle("-fx-text-fill: #9CA3AF; -fx-font-size: 16px;");

        // We use a VBox inside a ScrollPane so the user can scroll through the articles
        VBox articleListContainer = new VBox(15);
        articleListContainer.setAlignment(Pos.TOP_CENTER);
        articleListContainer.setStyle("-fx-background-color: transparent;");

        ScrollPane scrollPane = new ScrollPane(articleListContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #111827; -fx-border-color: transparent; -fx-padding: 10px;");
        VBox.setVgrow(scrollPane, javafx.scene.layout.Priority.ALWAYS);

        // Fetch the news dynamically
        fetchNewsSentimentData(currentNewsTicker, articleListContainer);

        view.getChildren().addAll(title, subTitle, scrollPane);
        return view;
    }

    private VBox buildSettingsView() {
        VBox view = new VBox(25); view.setAlignment(Pos.TOP_CENTER); view.setStyle("-fx-padding: 40px; -fx-background-color: transparent;");
        Label title = new Label("Terminal Settings"); title.setStyle("-fx-text-fill: white; -fx-font-size: 28px; -fx-font-weight: bold;");
        Button manualThemeToggle = new Button("Toggle Global Theme (Dark/Light)"); manualThemeToggle.setStyle("-fx-background-color: #374151; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 10px 20px; -fx-cursor: hand; -fx-background-radius: 8px;"); manualThemeToggle.setOnAction(e -> toggleTheme());
        VBox apiBox = new VBox(10); apiBox.setAlignment(Pos.CENTER);
        Label apiLabel = new Label("Alpha Vantage API Key (Stocks & Minerals):"); apiLabel.setStyle("-fx-text-fill: #9CA3AF; -fx-font-size: 14px;");
        TextField apiKeyField = new TextField(alphaVantageKey); apiKeyField.setMaxWidth(300); apiKeyField.setStyle("-fx-background-color: #1F2937; -fx-text-fill: white; -fx-border-color: #374151; -fx-border-radius: 5px; -fx-padding: 8px;");
        apiBox.getChildren().addAll(apiLabel, apiKeyField);
        HBox aiBox = new HBox(20); aiBox.setAlignment(Pos.CENTER);
        VBox shortSmaBox = new VBox(10); shortSmaBox.setAlignment(Pos.CENTER);
        Label shortLabel = new Label("Short-Term SMA:"); shortLabel.setStyle("-fx-text-fill: #9CA3AF; -fx-font-size: 14px;");
        ComboBox<Integer> shortCombo = new ComboBox<>(); shortCombo.getItems().addAll(5, 10, 20); shortCombo.setValue(shortSmaPeriod); shortCombo.setStyle("-fx-background-color: #1F2937; -fx-border-color: #374151;"); shortSmaBox.getChildren().addAll(shortLabel, shortCombo);
        VBox longSmaBox = new VBox(10); longSmaBox.setAlignment(Pos.CENTER);
        Label longLabel = new Label("Long-Term SMA:"); longLabel.setStyle("-fx-text-fill: #9CA3AF; -fx-font-size: 14px;");
        ComboBox<Integer> longCombo = new ComboBox<>(); longCombo.getItems().addAll(50, 100, 200); longCombo.setValue(longSmaPeriod); longCombo.setStyle("-fx-background-color: #1F2937; -fx-border-color: #374151;"); longSmaBox.getChildren().addAll(longLabel, longCombo);
        aiBox.getChildren().addAll(shortSmaBox, longSmaBox);
        Label statusLabel = new Label(); statusLabel.setStyle("-fx-text-fill: #10B981; -fx-font-size: 14px; -fx-font-weight: bold;");
        Button saveBtn = new Button("💾 Save & Recalibrate AI"); saveBtn.setStyle("-fx-background-color: #2563EB; -fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 10px 30px; -fx-cursor: hand; -fx-background-radius: 8px;");
        saveBtn.setOnAction(e -> { alphaVantageKey = apiKeyField.getText(); shortSmaPeriod = shortCombo.getValue(); longSmaPeriod = longCombo.getValue(); if (!stockDataList.isEmpty()) { calculateTechnicalIndicators(); runMachineLearning(); } statusLabel.setText("Settings Saved & AI Recalibrated!"); Timeline fade = new Timeline(new KeyFrame(Duration.seconds(3), ev -> statusLabel.setText(""))); fade.play(); });
        view.getChildren().addAll(title, manualThemeToggle, apiBox, aiBox, saveBtn, statusLabel); return view;
    }

    private Button createMenuButton(String text, String hoverColor) {
        Button btn = new Button(text + "  ➔  Launch Terminal"); btn.setMaxWidth(Double.MAX_VALUE);
        String defaultStyle = "-fx-background-color: #1F2937; -fx-text-fill: " + hoverColor + "; -fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 20px; -fx-cursor: hand; -fx-border-color: #374151; -fx-border-radius: 8px; -fx-background-radius: 8px;";
        String hoverStyle = "-fx-background-color: #374151; -fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 20px; -fx-cursor: hand; -fx-border-color: " + hoverColor + "; -fx-border-radius: 8px; -fx-background-radius: 8px;";
        btn.setStyle(defaultStyle); btn.setOnMouseEntered(e -> btn.setStyle(hoverStyle)); btn.setOnMouseExited(e -> btn.setStyle(defaultStyle)); return btn;
    }

    private void switchView(javafx.scene.Node newView, Button activeButton) {
        Button[] allNavButtons = {navDashboardBtn, navCryptoBtn, navStocksBtn, navMineralsBtn, navNewsBtn, navOfflineBtn, navSettingsBtn};
        for (Button btn : allNavButtons) btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #9CA3AF; -fx-alignment: BASELINE_LEFT; -fx-padding: 12px; -fx-font-size: 14px; -fx-cursor: hand;");
        activeButton.setStyle("-fx-background-color: #374151; -fx-text-fill: white; -fx-alignment: BASELINE_LEFT; -fx-padding: 12px; -fx-font-size: 14px; -fx-cursor: hand;");
        contentArea.getChildren().setAll(newView, globalLoader);
    }

    // =================================================================================
    // API PIPELINES
    // =================================================================================

    // --- NEW: THE NEWS SENTIMENT PARSER ---
    private void fetchNewsSentimentData(String ticker, VBox container) {
        globalLoader.show("SCANNING HEADLINES...");

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                // We ask Alpha Vantage for the top 15 news articles related to the active ticker
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("https://www.alphavantage.co/query?function=NEWS_SENTIMENT&tickers=" + ticker + "&limit=15&apikey=" + alphaVantageKey))
                        .GET().build();
                java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

                com.google.gson.JsonObject jsonObject = new com.google.gson.Gson().fromJson(response.body(), com.google.gson.JsonObject.class);
                com.google.gson.JsonArray feed = jsonObject.getAsJsonArray("feed");

                if (feed == null) {
                    Platform.runLater(() -> {
                        Label error = new Label("No recent news found, or API limit reached (25/day).");
                        error.setStyle("-fx-text-fill: #EF4444; -fx-font-size: 16px;");
                        container.getChildren().add(error);
                        globalLoader.hide();
                    });
                    return;
                }

                Platform.runLater(() -> {
                    for (com.google.gson.JsonElement element : feed) {
                        com.google.gson.JsonObject article = element.getAsJsonObject();

                        String headlineText = article.get("title").getAsString();
                        String sourceText = article.get("source").getAsString();
                        String sentimentText = article.get("overall_sentiment_label").getAsString();

                        // Create a beautiful UI card for each article
                        VBox articleCard = new VBox(8);
                        articleCard.setStyle("-fx-background-color: #1F2937; -fx-padding: 15px; -fx-background-radius: 8px; -fx-border-color: #374151; -fx-border-radius: 8px;");
                        articleCard.setMaxWidth(800);

                        Label headline = new Label(headlineText);
                        headline.setWrapText(true);
                        headline.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");

                        String colorHex = sentimentText.contains("Bullish") ? "#10B981" : (sentimentText.contains("Bearish") ? "#EF4444" : "#9CA3AF");
                        Label metaData = new Label("Source: " + sourceText + "  |  AI Sentiment: " + sentimentText);
                        metaData.setStyle("-fx-text-fill: " + colorHex + "; -fx-font-size: 13px; -fx-font-weight: bold;");

                        articleCard.getChildren().addAll(headline, metaData);
                        container.getChildren().add(articleCard);
                    }
                    globalLoader.hide();
                });
            } catch (Exception e) {
                Platform.runLater(() -> globalLoader.hide());
            }
        });
    }

    private void fetchLiveCryptoData(String coinId) {
        globalLoader.show("SYNCING " + coinId.toUpperCase() + "..."); signalLabel.setText("TRADING SIGNAL: ANALYZING...");
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder().uri(java.net.URI.create("https://api.coingecko.com/api/v3/coins/" + coinId + "/ohlc?vs_currency=usd&days=365")).GET().build();
                java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                com.google.gson.JsonArray jsonArray = new com.google.gson.Gson().fromJson(response.body(), com.google.gson.JsonArray.class);
                Platform.runLater(() -> {
                    stockDataList.clear(); java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                    for (com.google.gson.JsonElement element : jsonArray) {
                        com.google.gson.JsonArray dayData = element.getAsJsonArray();
                        stockDataList.add(new StockRecord(sdf.format(new java.util.Date(dayData.get(0).getAsLong())), dayData.get(1).getAsDouble(), dayData.get(2).getAsDouble(), dayData.get(3).getAsDouble(), dayData.get(4).getAsDouble(), 0L));
                    }
                    finalizeDataLoad(coinId.toUpperCase()); globalLoader.hide();
                });
            } catch (Exception e) { Platform.runLater(() -> { metricsLabel.setText("Network Error: Could not fetch live data."); globalLoader.hide(); }); }
        });
    }

    private void fetchLiveCryptoDataBackground(String coinId) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder().uri(java.net.URI.create("https://api.coingecko.com/api/v3/coins/" + coinId + "/ohlc?vs_currency=usd&days=365")).GET().build();
                java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                com.google.gson.JsonArray jsonArray = new com.google.gson.Gson().fromJson(response.body(), com.google.gson.JsonArray.class);
                Platform.runLater(() -> {
                    stockDataList.clear(); java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                    for (com.google.gson.JsonElement element : jsonArray) {
                        com.google.gson.JsonArray dayData = element.getAsJsonArray();
                        stockDataList.add(new StockRecord(sdf.format(new java.util.Date(dayData.get(0).getAsLong())), dayData.get(1).getAsDouble(), dayData.get(2).getAsDouble(), dayData.get(3).getAsDouble(), dayData.get(4).getAsDouble(), 0L));
                    }
                    finalizeDataLoad(coinId.toUpperCase());
                });
            } catch (Exception e) { }
        });
    }

    private void fetchLiveStockData(String symbol) {
        if (autoPoller != null) autoPoller.stop();
        globalLoader.show("FETCHING WALL STREET DATA..."); signalLabel.setText("TRADING SIGNAL: ANALYZING...");
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder().uri(java.net.URI.create("https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=" + symbol + "&outputsize=compact&apikey=" + alphaVantageKey)).GET().build();
                java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                com.google.gson.JsonObject jsonObject = new com.google.gson.Gson().fromJson(response.body(), com.google.gson.JsonObject.class);
                com.google.gson.JsonObject timeSeries = jsonObject.getAsJsonObject("Time Series (Daily)");
                if (timeSeries == null) { Platform.runLater(() -> { metricsLabel.setText("API Limit Reached! Alpha Vantage limits free keys."); globalLoader.hide(); }); return; }
                Platform.runLater(() -> {
                    stockDataList.clear(); ArrayList<StockRecord> tempRecords = new ArrayList<>();
                    for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : timeSeries.entrySet()) {
                        com.google.gson.JsonObject dailyData = entry.getValue().getAsJsonObject();
                        tempRecords.add(new StockRecord(entry.getKey(), dailyData.get("1. open").getAsDouble(), dailyData.get("2. high").getAsDouble(), dailyData.get("3. low").getAsDouble(), dailyData.get("4. close").getAsDouble(), dailyData.get("5. volume").getAsLong()));
                    }
                    java.util.Collections.reverse(tempRecords); stockDataList.addAll(tempRecords);
                    finalizeDataLoad(symbol); globalLoader.hide();
                });
            } catch (Exception e) { Platform.runLater(() -> globalLoader.hide()); }
        });
    }

    private void fetchLiveForexData(String fromSymbol, String toSymbol, String displayName) {
        if (autoPoller != null) autoPoller.stop();
        globalLoader.show("FETCHING " + displayName.toUpperCase() + "..."); signalLabel.setText("TRADING SIGNAL: ANALYZING...");
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder().uri(java.net.URI.create("https://www.alphavantage.co/query?function=FX_DAILY&from_symbol=" + fromSymbol + "&to_symbol=" + toSymbol + "&apikey=" + alphaVantageKey)).GET().build();
                java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                com.google.gson.JsonObject jsonObject = new com.google.gson.Gson().fromJson(response.body(), com.google.gson.JsonObject.class);
                com.google.gson.JsonObject timeSeries = jsonObject.getAsJsonObject("Time Series FX (Daily)");
                if (timeSeries == null) { Platform.runLater(() -> { metricsLabel.setText("API Limit Reached! Alpha Vantage limits free keys."); globalLoader.hide(); }); return; }
                Platform.runLater(() -> {
                    stockDataList.clear(); ArrayList<StockRecord> tempRecords = new ArrayList<>();
                    for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : timeSeries.entrySet()) {
                        com.google.gson.JsonObject dailyData = entry.getValue().getAsJsonObject();
                        tempRecords.add(new StockRecord(entry.getKey(), dailyData.get("1. open").getAsDouble(), dailyData.get("2. high").getAsDouble(), dailyData.get("3. low").getAsDouble(), dailyData.get("4. close").getAsDouble(), 0L));
                    }
                    java.util.Collections.reverse(tempRecords); stockDataList.addAll(tempRecords);
                    finalizeDataLoad(displayName); globalLoader.hide();
                });
            } catch (Exception e) { Platform.runLater(() -> globalLoader.hide()); }
        });
    }

    private void finalizeDataLoad(String title) {
        calculateTechnicalIndicators();
        runModelBtn.setDisable(false); exportBtn.setDisable(false); resetBtn.setDisable(false); chartToggleBtn.setDisable(false); expandChartBtn.setDisable(false);
        executeBuyBtn.setDisable(false); executeSellBtn.setDisable(false);
        fetchLiveBtn.setText("🔄 Manual Sync " + title); stockChart.setTitle(title + " AI Trend Analysis");
        metricsLabel.setText(title + " Data Loaded Successfully.");
        runMachineLearning();
    }

    // =================================================================================
    // MATH, AI, AND UI RENDERERS
    // =================================================================================

    @SuppressWarnings("unchecked")
    private void setupTableColumns() {
        TableColumn<StockRecord, String> dateCol = new TableColumn<>("Date"); dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));
        TableColumn<StockRecord, Double> openCol = new TableColumn<>("Open"); openCol.setCellValueFactory(new PropertyValueFactory<>("open"));
        TableColumn<StockRecord, Double> closeCol = new TableColumn<>("Close"); closeCol.setCellValueFactory(new PropertyValueFactory<>("close"));
        TableColumn<StockRecord, Long> volCol = new TableColumn<>("Volume"); volCol.setCellValueFactory(new PropertyValueFactory<>("volume"));
        TableColumn<StockRecord, Double> smaShortCol = new TableColumn<>("Short SMA"); smaShortCol.setCellValueFactory(new PropertyValueFactory<>("sma10"));
        TableColumn<StockRecord, Double> smaLongCol = new TableColumn<>("Long SMA"); smaLongCol.setCellValueFactory(new PropertyValueFactory<>("sma50"));
        TableColumn<StockRecord, Double> rsiCol = new TableColumn<>("14-Day RSI"); rsiCol.setCellValueFactory(new PropertyValueFactory<>("rsi"));
        dataTable.getColumns().addAll(dateCol, openCol, closeCol, volCol, smaShortCol, smaLongCol, rsiCol); dataTable.setItems(stockDataList);
    }

    private void toggleTheme() {
        if (isDarkMode) { if (applyTheme("light-theme.css")) { themeToggleBtn.setText("🌙 Dark Mode"); themeToggleBtn.setStyle("-fx-background-color: #E5E7EB; -fx-text-fill: #1F2937;"); isDarkMode = false; }
        } else { if (applyTheme("dark-theme.css")) { themeToggleBtn.setText("☀️ Light Mode"); themeToggleBtn.setStyle("-fx-background-color: #374151; -fx-text-fill: #FBBF24;"); isDarkMode = true; } }
    }

    private boolean applyTheme(String cssFileName) {
        try { Scene scene = themeToggleBtn.getScene(); java.net.URL cssUrl = getClass().getResource("/css/" + cssFileName);
            if (cssUrl != null) { scene.getStylesheets().clear(); scene.getStylesheets().add(cssUrl.toExternalForm()); return true; }
            return false;
        } catch (Exception e) { return false; }
    }

    private void toggleChartFullScreen() {
        isChartExpanded = !isChartExpanded;
        if (isChartExpanded) { dataTable.setVisible(false); dataTable.setManaged(false); expandChartBtn.setText("🗗 Minimize Chart");
        } else { dataTable.setVisible(true); dataTable.setManaged(true); expandChartBtn.setText("🗖 Full Screen Chart"); }
    }

    private void exportData() {
        FileChooser fileChooser = new FileChooser(); fileChooser.setTitle("Save Analysis Results");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files (*.csv)", "*.csv"));
        File file = fileChooser.showSaveDialog(exportBtn.getScene().getWindow());
        if (file != null) {
            try (PrintWriter writer = new PrintWriter(file)) {
                writer.println("Date,Open,Close,Volume,Short SMA,Long SMA,RSI");
                for (StockRecord r : stockDataList) writer.printf("%s,%.2f,%.2f,%d,%.2f,%.2f,%.2f\n", r.getDate(), r.getOpen(), r.getClose(), r.getVolume(), r.getSma10(), r.getSma50(), r.getRsi());
                metricsLabel.setText("Success: Data exported.");
            } catch (Exception e) {}
        }
    }

    private void openFilePicker() {
        if (autoPoller != null) autoPoller.stop();
        FileChooser fileChooser = new FileChooser(); fileChooser.setTitle("Select CSV Data");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File file = fileChooser.showOpenDialog(loadDataBtn.getScene().getWindow());
        if (file != null) loadCsvData(file);
    }

    @SuppressWarnings("deprecation")
    private void loadCsvData(File file) {
        globalLoader.show("LOADING OFFLINE DATA...");
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                stockDataList.clear();
                try (Reader in = new FileReader(file)) {
                    Iterable<CSVRecord> records = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(in);
                    for (CSVRecord record : records) stockDataList.add(new StockRecord(record.get("Date"), Double.parseDouble(record.get("Open")), Double.parseDouble(record.get("High")), Double.parseDouble(record.get("Low")), Double.parseDouble(record.get("Close")), (long) Double.parseDouble(record.get("Volume"))));
                    Platform.runLater(() -> { finalizeDataLoad(file.getName()); fetchLiveBtn.setText("🌐 Enter Live Mode"); globalLoader.hide(); });
                }
            } catch (Exception e) { Platform.runLater(() -> globalLoader.hide()); }
        });
    }

    private void calculateTechnicalIndicators() {
        if (stockDataList.size() < longSmaPeriod) {
            Platform.runLater(() -> metricsLabel.setText("Need " + longSmaPeriod + "+ points for AI Math. Got " + stockDataList.size()));
            return;
        }
        for (int i = longSmaPeriod; i < stockDataList.size(); i++) {
            double sumShort = 0; for (int j = i - shortSmaPeriod; j < i; j++) sumShort += stockDataList.get(j).getClose();
            stockDataList.get(i).setSma10(sumShort / (double)shortSmaPeriod);
            double sumLong = 0; for (int j = i - longSmaPeriod; j < i; j++) sumLong += stockDataList.get(j).getClose();
            stockDataList.get(i).setSma50(sumLong / (double)longSmaPeriod);
        }
        int rsiPeriod = 14;
        if (stockDataList.size() < rsiPeriod + 1) return;
        double avgGain = 0, avgLoss = 0;
        for (int i = 1; i <= rsiPeriod; i++) {
            double change = stockDataList.get(i).getClose() - stockDataList.get(i - 1).getClose();
            if (change > 0) avgGain += change; else avgLoss += Math.abs(change);
        }
        avgGain /= rsiPeriod; avgLoss /= rsiPeriod;
        stockDataList.get(rsiPeriod).setRsi(avgLoss == 0 ? 100 : 100 - (100 / (1 + (avgGain / avgLoss))));
        for (int i = rsiPeriod + 1; i < stockDataList.size(); i++) {
            double change = stockDataList.get(i).getClose() - stockDataList.get(i - 1).getClose();
            double gain = change > 0 ? change : 0; double loss = change < 0 ? Math.abs(change) : 0;
            avgGain = ((avgGain * (rsiPeriod - 1)) + gain) / rsiPeriod; avgLoss = ((avgLoss * (rsiPeriod - 1)) + loss) / rsiPeriod;
            double rs = avgGain / avgLoss; double rsi = avgLoss == 0 ? 100 : 100 - (100 / (1 + rs));
            stockDataList.get(i).setRsi(rsi);
        }
    }

    private void runMachineLearning() {
        if (stockDataList.size() < longSmaPeriod) { signalLabel.setText("SIGNAL ERROR: NEED " + longSmaPeriod + "+ DAYS OF DATA"); return; }
        try {
            ArrayList<Attribute> attributes = new ArrayList<>();
            attributes.add(new Attribute("smaShort")); attributes.add(new Attribute("smaLong")); attributes.add(new Attribute("close"));
            Instances dataset = new Instances("StockPredictions", attributes, stockDataList.size()); dataset.setClassIndex(2);
            for (int i = longSmaPeriod; i < stockDataList.size(); i++) {
                StockRecord record = stockDataList.get(i); DenseInstance instance = new DenseInstance(3);
                instance.setValue(attributes.get(0), record.getSma10()); instance.setValue(attributes.get(1), record.getSma50()); instance.setValue(attributes.get(2), record.getClose()); dataset.add(instance);
            }
            LinearRegression model = new LinearRegression(); model.buildClassifier(dataset);
            Evaluation eval = new Evaluation(dataset); eval.evaluateModel(model, dataset);
            drawChartWithPredictions(model, dataset, eval.meanAbsoluteError(), eval.correlationCoefficient());
        } catch (Exception e) {}
    }

    @SuppressWarnings("unchecked")
    private void drawChartWithPredictions(LinearRegression model, Instances dataset, double mae, double correlation) {
        Platform.runLater(() -> {
            stockChart.setAnimated(false); stockChart.setCreateSymbols(isCandlestickMode); stockChart.getData().clear();
            XYChart.Series<String, Number> actualSeries = new XYChart.Series<>(); actualSeries.setName("Actual Price");
            XYChart.Series<String, Number> aiSeries = new XYChart.Series<>(); aiSeries.setName("AI Trend");
            NumberAxis yAxis = (NumberAxis) stockChart.getYAxis();
            double lastActualPrice = 0; double lastPredictedPrice = 0; double lastRsi = 0;

            for (int i = longSmaPeriod; i < stockDataList.size(); i++) {
                StockRecord record = stockDataList.get(i);
                XYChart.Data<String, Number> dataPoint = new XYChart.Data<>(record.getDate(), record.getClose());
                if (isCandlestickMode) dataPoint.setNode(new CandleNode(record, yAxis));
                actualSeries.getData().add(dataPoint);
                try {
                    double predictedPrice = model.classifyInstance(dataset.get(i - longSmaPeriod));
                    aiSeries.getData().add(new XYChart.Data<>(record.getDate(), predictedPrice));
                    if (i == stockDataList.size() - 1) {
                        lastActualPrice = record.getClose(); lastPredictedPrice = predictedPrice; lastRsi = record.getRsi();
                        lastKnownPrice = lastActualPrice; updatePortfolioUI();
                    }
                } catch (Exception ignored) { }
            }

            stockChart.getData().addAll(actualSeries, aiSeries);
            if (isCandlestickMode) {
                actualSeries.getNode().setStyle("-fx-stroke: transparent;");
                for (XYChart.Data<String, Number> data : aiSeries.getData()) if (data.getNode() != null) data.getNode().setVisible(false);
            }
            metricsLabel.setText(String.format("AI Precision | MAE: $%.4f | Corr: %.2f", mae, correlation));
            String rsiColor = lastRsi > 70 ? "#EF4444" : (lastRsi < 30 ? "#10B981" : "#3B82F6");
            rsiLabel.setText(String.format("RSI: %.2f", lastRsi)); rsiLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 18px; -fx-text-fill: " + rsiColor + ";");

            double percentDiff = ((lastPredictedPrice - lastActualPrice) / lastActualPrice) * 100;
            if (percentDiff > 1.5 && lastRsi < 70) {
                signalLabel.setText("🟢 SIGNAL: STRONG BUY (+ " + String.format("%.2f", percentDiff) + "% momentum)"); signalLabel.setStyle("-fx-text-fill: #10B981; -fx-font-weight: bold; -fx-font-size: 20px;");
            } else if (percentDiff < -1.5 && lastRsi > 30) {
                signalLabel.setText("🔴 SIGNAL: STRONG SELL (" + String.format("%.2f", percentDiff) + "% correction)"); signalLabel.setStyle("-fx-text-fill: #EF4444; -fx-font-weight: bold; -fx-font-size: 20px;");
            } else {
                signalLabel.setText("⚪ SIGNAL: HOLD (Market Noise)"); signalLabel.setStyle("-fx-text-fill: #9CA3AF; -fx-font-weight: bold; -fx-font-size: 20px;");
            }
        });
    }

    private void resetDashboard() {
        if (autoPoller != null) autoPoller.stop();
        stockDataList.clear(); stockChart.getData().clear();
        metricsLabel.setText("Metrics will appear here...");
        rsiLabel.setText("RSI: --"); rsiLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 18px; -fx-text-fill: #3B82F6;");
        signalLabel.setText("TRADING SIGNAL: AWAITING DATA"); signalLabel.setStyle("-fx-text-fill: #6B7280; -fx-font-weight: bold; -fx-font-size: 20px;");
        stockChart.setTitle("Market Trend & AI Predictions");
        runModelBtn.setDisable(true); exportBtn.setDisable(true); resetBtn.setDisable(true); chartToggleBtn.setDisable(true); expandChartBtn.setDisable(true);
        executeBuyBtn.setDisable(true); executeSellBtn.setDisable(true);
        if (isChartExpanded) toggleChartFullScreen();
    }

    private static class CandleNode extends Group {
        private final Line wick = new Line(); private final Region body = new Region(); private final StockRecord record; private final NumberAxis yAxis;
        public CandleNode(StockRecord record, NumberAxis yAxis) {
            this.record = record; this.yAxis = yAxis; getChildren().addAll(wick, body);
            boolean isBullish = record.getClose() >= record.getOpen(); String colorHex = isBullish ? "#10B981" : "#EF4444";
            wick.setStroke(Color.web(colorHex)); wick.setStrokeWidth(1.5);
            body.setStyle("-fx-background-color: " + colorHex + ";"); body.setPrefWidth(8); body.setLayoutX(-4);
            yAxis.lowerBoundProperty().addListener((o, old, nw) -> Platform.runLater(this::updatePixels));
            yAxis.upperBoundProperty().addListener((o, old, nw) -> Platform.runLater(this::updatePixels));
            yAxis.heightProperty().addListener((o, old, nw) -> Platform.runLater(this::updatePixels));
            Platform.runLater(this::updatePixels);
        }
        private void updatePixels() {
            if (yAxis.getHeight() == 0) return;
            double closeY = yAxis.getDisplayPosition(record.getClose()); double openY = yAxis.getDisplayPosition(record.getOpen());
            double highY = yAxis.getDisplayPosition(record.getHigh()); double lowY = yAxis.getDisplayPosition(record.getLow());
            wick.setStartY(highY - closeY); wick.setEndY(lowY - closeY);
            double topY = Math.min(openY, closeY) - closeY; double bottomY = Math.max(openY, closeY) - closeY;
            body.setLayoutY(topY); body.setPrefHeight(Math.max(bottomY - topY, 1));
        }
    }

    private static class FinMarkLoader extends StackPane {
        private final Label statusText;
        public FinMarkLoader() {
            setStyle("-fx-background-color: rgba(17, 24, 39, 0.85);");
            Arc arc1 = new Arc(0, 0, 35, 35, 0, 270); arc1.setFill(Color.TRANSPARENT); arc1.setStroke(Color.web("#10B981")); arc1.setStrokeWidth(5); arc1.setType(ArcType.OPEN);
            Arc arc2 = new Arc(0, 0, 55, 55, 90, 270); arc2.setFill(Color.TRANSPARENT); arc2.setStroke(Color.web("#3B82F6")); arc2.setStrokeWidth(4); arc2.setType(ArcType.OPEN);
            RotateTransition rt1 = new RotateTransition(Duration.seconds(1), arc1); rt1.setByAngle(360); rt1.setCycleCount(Animation.INDEFINITE); rt1.setInterpolator(Interpolator.LINEAR); rt1.play();
            RotateTransition rt2 = new RotateTransition(Duration.seconds(1.5), arc2); rt2.setByAngle(-360); rt2.setCycleCount(Animation.INDEFINITE); rt2.setInterpolator(Interpolator.LINEAR); rt2.play();
            statusText = new Label("SYNCING DATA..."); statusText.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 16px; -fx-padding: 160 0 0 0;");
            getChildren().addAll(arc1, arc2, statusText); setVisible(false);
        }
        public void show(String text) { statusText.setText(text); setVisible(true); }
        public void hide() { setVisible(false); }
    }
}