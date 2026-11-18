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
        validateRequest(symbol,interval);
        List<Candle> all=loadPages(symbol,interval,4);
        removeUnclosed(all,intervalMillis(interval));
        if(all.size()<100)throw new Exception("Недостаточно закрытых исторических свечей: "+all.size());
        return all;
    }

    // Wider minute history gives thousands of distinct historical decision points.
    static List<Candle> loadBinaryHistory(String symbol) throws Exception {
        validateRequest(symbol,"1");
        List<Candle> all=loadPages(symbol,"1",5);
        removeUnclosed(all,60000L);
        if(all.size()<3000)throw new Exception("Не удалось загрузить достаточно минутной истории для экспирации до 24 часов");
        return all;
    }

    private static void validateRequest(String symbol,String interval) {
        if(symbol==null||!symbol.matches("[A-Z0-9]{2,20}"))throw new IllegalArgumentException("Некорректный торговый инструмент");
        if(interval==null||!interval.matches("1|3|5|15|30|60|120|240|360|720|D|W|M"))throw new IllegalArgumentException("Некорректный таймфрейм");
    }

    private static List<Candle> loadPages(String symbol,String interval,int pages) throws Exception {
        List<Candle> all=new ArrayList<>();
        Long end=null;
        for(int page=0;page<pages;page++){
            List<Candle> batch=loadPage(symbol,interval,end,1000);
            if(batch.isEmpty())break;
            all.addAll(batch);
            long oldest=batch.get(0).time;
            if(end!=null&&oldest>=end)break;
            end=oldest-1;
            if(batch.size()<1000)break;
        }
        Collections.sort(all,(a,b)->Long.compare(a.time,b.time));
        List<Candle> unique=new ArrayList<>();
        long previous=Long.MIN_VALUE;
        for(Candle candle:all){if(candle.time!=previous){unique.add(candle);previous=candle.time;}}
        return unique;
    }

    static boolean isValidCandle(Candle c) {
        return c!=null&&c.time>0&&Double.isFinite(c.open)&&Double.isFinite(c.high)
                &&Double.isFinite(c.low)&&Double.isFinite(c.close)&&Double.isFinite(c.volume)
                &&c.open>0&&c.high>0&&c.low>0&&c.close>0&&c.volume>=0
                &&c.high>=Math.max(c.open,c.close)&&c.low<=Math.min(c.open,c.close)&&c.high>=c.low;
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
        String category="XAUTUSDT".equals(symbol)?"spot":"linear";
        String query="https://api.bybit.com/v5/market/kline?category="+category+"&symbol="+symbol
                +"&interval="+interval+"&limit="+limit+(end==null?"":"&end="+end);
        HttpURLConnection connection=(HttpURLConnection)new URL(query).openConnection();
        connection.setRequestMethod("GET");connection.setConnectTimeout(10000);connection.setReadTimeout(15000);
        connection.setUseCaches(false);connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept","application/json");connection.setRequestProperty("User-Agent","TradeQuest-Android/1.2");
        try {
            int code=connection.getResponseCode();
            InputStream stream=code>=200&&code<300?connection.getInputStream():connection.getErrorStream();
            if(stream==null)throw new Exception("Пустой ответ Bybit (HTTP "+code+")");
            StringBuilder body=new StringBuilder();
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){
                String line;while((line=reader.readLine())!=null){body.append(line);if(body.length()>2_000_000)throw new Exception("Ответ API превышает допустимый размер");}
            }
            if(code<200||code>=300)throw new Exception("Bybit HTTP "+code);
            JSONObject root=new JSONObject(body.toString());
            int retCode=root.optInt("retCode",-1);
            if(retCode!=0)throw new Exception(root.optString("retMsg","Ошибка ответа биржи"));
            JSONArray rows=root.getJSONObject("result").getJSONArray("list");
            List<Candle> candles=new ArrayList<>();
            for(int i=0;i<rows.length();i++){
                JSONArray row=rows.getJSONArray(i);
                try {
                    Candle candle=new Candle(Long.parseLong(row.getString(0)),Double.parseDouble(row.getString(1)),
                            Double.parseDouble(row.getString(2)),Double.parseDouble(row.getString(3)),
                            Double.parseDouble(row.getString(4)),Double.parseDouble(row.getString(5)));
                    if(isValidCandle(candle))candles.add(candle);
                } catch(RuntimeException ignored) { /* Skip malformed provider rows. */ }
            }
            Collections.sort(candles,(a,b)->Long.compare(a.time,b.time));
            return candles;
        } finally { connection.disconnect(); }
    }
}
