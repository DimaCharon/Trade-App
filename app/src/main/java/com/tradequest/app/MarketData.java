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
            this.time=time; this.open=open; this.high=high; this.low=low; this.close=close; this.volume=volume;
        }
    }

    static List<Candle> load(String symbol,String interval) throws Exception {
        List<Candle> data=loadPage(symbol,interval,null,1000);
        removeUnclosed(data,intervalMillis(interval));
        if(data.size()<100)throw new Exception("Недостаточно закрытых исторических свечей: "+data.size());
        return data;
    }

    // Two Bybit pages provide enough one-minute history for a 24-hour expiry plus context.
    static List<Candle> loadBinaryHistory(String symbol) throws Exception {
        List<Candle> newest=loadPage(symbol,"1",null,1000);
        if(newest.isEmpty())throw new Exception("Bybit не вернул минутные свечи");
        long end=newest.get(0).time-1;
        List<Candle> older=loadPage(symbol,"1",end,1000);
        List<Candle> all=new ArrayList<>(older);
        all.addAll(newest);
        Collections.sort(all,(a,b)->Long.compare(a.time,b.time));
        removeUnclosed(all,60000L);
        if(all.size()<1550)throw new Exception("Не удалось загрузить достаточно минутной истории для экспирации до 24 часов");
        return all;
    }

    private static long intervalMillis(String interval) {
        if("D".equals(interval))return 86400000L;
        if("W".equals(interval))return 604800000L;
        if("M".equals(interval))return 2592000000L;
        return Long.parseLong(interval)*60000L;
    }

    private static void removeUnclosed(List<Candle> data,long duration) {
        long now=System.currentTimeMillis();
        while(!data.isEmpty() && data.get(data.size()-1).time+duration>now)data.remove(data.size()-1);
    }

    private static List<Candle> loadPage(String symbol,String interval,Long end,int limit) throws Exception {
        String query="https://api.bybit.com/v5/market/kline?category=linear&symbol="+symbol
                +"&interval="+interval+"&limit="+limit+(end==null?"":"&end="+end);
        HttpURLConnection connection=(HttpURLConnection)new URL(query).openConnection();
        connection.setRequestMethod("GET");connection.setConnectTimeout(10000);connection.setReadTimeout(15000);
        connection.setRequestProperty("Accept","application/json");connection.setRequestProperty("User-Agent","TradeQuest-Android/1.0");
        try {
            int code=connection.getResponseCode();
            InputStream stream=code>=200&&code<300?connection.getInputStream():connection.getErrorStream();
            StringBuilder body=new StringBuilder();
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){
                String line;while((line=reader.readLine())!=null)body.append(line);
            }
            if(code<200||code>=300)throw new Exception("Bybit HTTP "+code);
            JSONObject root=new JSONObject(body.toString());
            int retCode=root.optInt("retCode",-1);
            if(retCode!=0)throw new Exception(root.optString("retMsg","Ошибка ответа биржи"));
            JSONArray rows=root.getJSONObject("result").getJSONArray("list");
            List<Candle> candles=new ArrayList<>();
            for(int i=0;i<rows.length();i++){
                JSONArray row=rows.getJSONArray(i);
                candles.add(new Candle(Long.parseLong(row.getString(0)),Double.parseDouble(row.getString(1)),
                        Double.parseDouble(row.getString(2)),Double.parseDouble(row.getString(3)),
                        Double.parseDouble(row.getString(4)),Double.parseDouble(row.getString(5))));
            }
            Collections.sort(candles,(a,b)->Long.compare(a.time,b.time));
            return candles;
        } finally { connection.disconnect(); }
    }
}
