package com.tradequest.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class MarketData {
    private MarketData() {}

    static final class Candle {
        final long time;
        final double open, high, low, close, volume;
        Candle(long time, double open, double high, double low, double close, double volume) {
            this.time = time; this.open = open; this.high = high;
            this.low = low; this.close = close; this.volume = volume;
        }
    }

    static List<Candle> load(String symbol, String interval) throws Exception {
        String query = "https://api.bybit.com/v5/market/kline?category=linear&symbol=" + symbol
                + "&interval=" + interval + "&limit=1000";
        HttpURLConnection connection = (HttpURLConnection) new URL(query).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "TradeQuest-Android/1.0");
        try {
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
            }
            if (code < 200 || code >= 300) throw new Exception("Bybit HTTP " + code);
            JSONObject root = new JSONObject(body.toString());
            int retCode = root.optInt("retCode", -1);
            if (retCode != 0) throw new Exception(root.optString("retMsg", "Ошибка ответа биржи"));
            JSONArray rows = root.getJSONObject("result").getJSONArray("list");
            List<Candle> candles = new ArrayList<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONArray row = rows.getJSONArray(i);
                candles.add(new Candle(
                        Long.parseLong(row.getString(0)),
                        Double.parseDouble(row.getString(1)),
                        Double.parseDouble(row.getString(2)),
                        Double.parseDouble(row.getString(3)),
                        Double.parseDouble(row.getString(4)),
                        Double.parseDouble(row.getString(5))));
            }
            Collections.reverse(candles); // Bybit returns newest first.
            if (candles.size() < 100) throw new Exception("Недостаточно исторических свечей: " + candles.size());
            return candles;
        } finally {
            connection.disconnect();
        }
    }
}
