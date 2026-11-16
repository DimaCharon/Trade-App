package com.tradequest.app;

import java.util.List;

final class TradeSimulator {
    static final class Result {
        final boolean hitTp;
        final boolean ambiguous;
        final double entry, exit, pnl, roi;
        final int barsHeld;
        final String reason;
        final int exitIndex;
        Result(boolean hitTp, boolean ambiguous, double entry, double exit, double pnl,
               double roi, int barsHeld, String reason, int exitIndex) {
            this.hitTp=hitTp; this.ambiguous=ambiguous; this.entry=entry; this.exit=exit;
            this.pnl=pnl; this.roi=roi; this.barsHeld=barsHeld; this.reason=reason; this.exitIndex=exitIndex;
        }
    }

    static final class BinaryResult {
        final boolean won,draw;
        final double entry,exit,stake,pnl,payoutRate;
        final int expiryMinutes,exitIndex;
        final long entryTime,expiryTime;
        BinaryResult(boolean won,boolean draw,double entry,double exit,double stake,double pnl,double payoutRate,
                     int expiryMinutes,int exitIndex,long entryTime,long expiryTime){
            this.won=won;this.draw=draw;this.entry=entry;this.exit=exit;this.stake=stake;this.pnl=pnl;
            this.payoutRate=payoutRate;this.expiryMinutes=expiryMinutes;this.exitIndex=exitIndex;
            this.entryTime=entryTime;this.expiryTime=expiryTime;
        }
    }

    static BinaryResult simulateBinary(List<MarketData.Candle> candles,int pivot,boolean up,
                                       double stake,int expiryMinutes,double payoutRate) {
        if(pivot<0||pivot+1>=candles.size())throw new IllegalArgumentException("Нет свечи для открытия сделки");
        if(stake<=0||expiryMinutes<1||expiryMinutes>1440||payoutRate<0||payoutRate>1)
            throw new IllegalArgumentException("Срок должен быть от 1 минуты до 24 часов");
        int entryIndex=pivot+1;
        MarketData.Candle entryCandle=candles.get(entryIndex);
        double entry=entryCandle.open;
        long expiryTime=entryCandle.time+expiryMinutes*60000L;
        int exitIndex=-1;
        for(int i=entryIndex;i<candles.size();i++){
            if(candles.get(i).time+60000L>=expiryTime){exitIndex=i;break;}
        }
        if(exitIndex<0)throw new IllegalArgumentException("Для этой экспирации не хватает будущей минутной истории. Загрузи другой исторический момент.");
        double exit=candles.get(exitIndex).close;
        boolean draw=Math.abs(exit-entry)<=Math.max(Math.abs(entry)*1e-8,1e-10);
        boolean won=!draw&&(up?exit>entry:exit<entry);
        double pnl=draw?0:(won?stake*payoutRate:-stake);
        return new BinaryResult(won,draw,entry,exit,stake,pnl,payoutRate,expiryMinutes,exitIndex,entryCandle.time,expiryTime);
    }

    static Result simulate(List<MarketData.Candle> candles, int pivot, boolean isLong,
                           double margin, int leverage, double tpPct, double slPct) {
        if (pivot + 1 >= candles.size()) throw new IllegalArgumentException("Нет свечей после выбранного момента");
        if (margin <= 0 || leverage < 1 || tpPct <= 0 || slPct <= 0) throw new IllegalArgumentException("Проверь параметры сделки");
        double entry = candles.get(pivot + 1).open; // Entry is next candle open to avoid look-ahead.
        double tp = isLong ? entry * (1 + tpPct / 100.0) : entry * (1 - tpPct / 100.0);
        double sl = isLong ? entry * (1 - slPct / 100.0) : entry * (1 + slPct / 100.0);
        double notional = margin * leverage;
        for (int i = pivot + 1; i < candles.size() && i <= pivot + 300; i++) {
            MarketData.Candle c = candles.get(i);
            boolean slTouched = isLong ? c.low <= sl : c.high >= sl;
            boolean tpTouched = isLong ? c.high >= tp : c.low <= tp;
            if (slTouched || tpTouched) {
                boolean ambiguous = slTouched && tpTouched;
                boolean win = tpTouched && !slTouched;
                double exit;
                if (ambiguous) {
                    // Conservative: SL first if both levels occur in one candle; respect stop gaps.
                    exit = isLong ? Math.min(sl, c.open) : Math.max(sl, c.open);
                } else if (win) {
                    // A favorable gap through TP receives the better opening price; otherwise TP limit.
                    exit = isLong ? Math.max(tp, c.open) : Math.min(tp, c.open);
                } else {
                    // A gap through SL is filled at the worse opening price, not the stop level.
                    exit = isLong ? Math.min(sl, c.open) : Math.max(sl, c.open);
                }
                double move = isLong ? (exit - entry) / entry : (entry - exit) / entry;
                double pnl = notional * move;
                return new Result(win, ambiguous, entry, exit, pnl, pnl / margin * 100.0,
                        i - pivot, win ? "TAKE PROFIT" : "STOP LOSS", i);
            }
        }
        int last = Math.min(candles.size() - 1, pivot + 300);
        double exit = candles.get(last).close;
        double move = isLong ? (exit - entry) / entry : (entry - exit) / entry;
        double pnl = notional * move;
        return new Result(false, false, entry, exit, pnl, pnl / margin * 100.0,
                last - pivot, "ВРЕМЯ ВЫШЛО", last);
    }
}
