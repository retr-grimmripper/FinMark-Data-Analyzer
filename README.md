📈 FinMark Terminal: AI-Powered Market Analyzer
FinMark Terminal is a high-performance, multithreaded desktop trading application built in Java. It aggregates live financial data across multiple asset classes (Cryptocurrency, Global Equities, Forex, and Precious Metals), processes the data through custom technical indicators, and utilizes Weka Machine Learning to generate autonomous trading signals.

The application also features a fully functional Paper Trading Simulator, allowing users to test AI-driven trading strategies against live market conditions in real-time.

✨ Core Features
🌍 Multi-Asset Market Feeds: Fetches live OHLC (Open, High, Low, Close) and volume data for Cryptocurrencies (via CoinGecko API) and Stocks, Forex, and Minerals (via Alpha Vantage API).

🧠 AI Trading Engine: Integrates Weka's LinearRegression model. The AI dynamically calculates predicted price trends based on user-defined Simple Moving Averages (SMAs) and outputs actionable BUY / HOLD / SELL momentum signals.

📊 Advanced Technical Indicators: Calculates and visualizes dynamic Short-Term/Long-Term SMAs and a 14-Day Relative Strength Index (RSI) to detect overbought and oversold market conditions.

🎮 Paper Trading Simulator: Users start with a $10,000 virtual portfolio. Execute precise Buy/Sell orders with custom quantities that dynamically update total net worth based on real-time API market prices.

📰 Live AI News Sentiment: Automatically fetches the top 15 most recent global news headlines related to the active asset and displays their AI-rated market sentiment (Bullish, Bearish, or Neutral).

⚡ Multithreaded Architecture: Uses CompletableFuture for asynchronous background network requests, ensuring the UI remains perfectly responsive while parsing heavy JSON payloads.

🎨 Custom JavaFX UI: Features a custom-built dual-ring loading overlay, toggleable Candlestick/Line charts, Light/Dark mode themes, and dynamic data tables.

🛠️ Tech Stack
Language: Java 17+

GUI Framework: JavaFX & FXML

Machine Learning: Weka API (Data Mining & Linear Regression)

Networking: java.net.http.HttpClient

JSON Parsing: Google Gson

Data Processing: Apache Commons CSV (For offline data import/export)

🚀 Installation & Setup
Clone the Repository:

Bash
git clone https://github.com/YourUsername/FinMark-Terminal.git
cd FinMark-Terminal
Obtain an API Key:

Go to Alpha Vantage and claim a free API key.

Configure the Application:

Launch the application.

Navigate to the ⚙️ Settings tab in the left sidebar.

Paste your Alpha Vantage API Key into the security field and click Save & Recalibrate AI.

Build and Run:

Run the project via your preferred IDE (IntelliJ IDEA / Eclipse) or compile using Maven/Gradle. Ensure VM options for JavaFX are configured properly (e.g., --module-path /path/to/javafx/lib --add-modules javafx.controls,javafx.fxml).

💻 Usage Guide
The Dashboard: Your main command center. View the Candlestick chart, the AI Trendline, current RSI, and the Weka Prediction Signal.

Executing Trades: Use the Trading Desk below the chart. Enter a quantity, check your available cash, and hit Execute Buy or Execute Sell.

Background Auto-Poller: When viewing Cryptocurrencies, the terminal automatically pings the server every 60 seconds in the background to update the chart and your portfolio value without interrupting your workflow.

Offline Analysis: Click "Load Data" to import custom .csv files from Google Sheets or Excel. The AI will instantly ingest the data and map the trendlines. Click "Export Results" to save the AI's math back to a .csv file.

🧠 Algorithmic Logic
The application relies on a combination of classical quantitative finance and machine learning:

RSI Calculation: Uses a 14-period smoothed moving average of gains and losses to generate a momentum oscillator from 0 to 100.

Weka Linear Regression: The model is dynamically trained on standard attributes (smaShort, smaLong, and close price). The AI evaluates the Mean Absolute Error (MAE) and Correlation Coefficient before plotting the projected trendline against the actual price history.

The Signal Engine: If the AI projects a momentum swing of > 1.5% and the RSI confirms the asset is not overbought (< 70), the UI fires a 🟢 STRONG BUY signal.

🔮 Future Roadmap
[ ] Implement local SQLite database caching to save API calls and store paper trading history persistently.

[ ] Add MACD (Moving Average Convergence Divergence) to the technical indicator suite.

[ ] Refactor the monolithic controller into a strict MVVM (Model-View-ViewModel) architecture.