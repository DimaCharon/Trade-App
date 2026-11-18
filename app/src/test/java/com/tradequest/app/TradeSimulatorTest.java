package com.tradequest.app;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class TradeSimulatorTest {
    private List<MarketData.Candle> sample() {
        return Arrays.asList(
            new MarketData.Candle(60000,100,101,99,100,10),
            new MarketData.Candle(120000,100,103,99.5,102,12),
            new MarketData.Candle(180000,102,104,101,103,11),
            new MarketData.Candle(240000,103,104,102,103,9)
        );
    }

    @Test public void longEntryUsesNextCandleOpenAndTakeProfit() {
        List<MarketData.Candle> candles=sample();
        TradeSimulator.Result r=TradeSimulator.simulate(candles,0,true,100,1,2,1);
        assertEquals(100.0,r.entry,0.000001);
        assertTrue(r.hitTp);
        assertFalse(r.ambiguous);
        assertEquals(2.0,r.pnl,0.000001);
    }

    @Test public void simultaneousTpAndSlUsesConservativeStop() {
        List<MarketData.Candle> candles=Arrays.asList(
            new MarketData.Candle(60000,100,100,100,100,1),
            new MarketData.Candle(120000,100,103,98,101,1)
        );
        TradeSimulator.Result r=TradeSimulator.simulate(candles,0,true,100,1,2,1);
        assertTrue(r.ambiguous);
        assertFalse(r.hitTp);
        assertEquals(99.0,r.exit,0.000001);
    }

    @Test public void binaryUpWinsAtExpiryClose() {
        TradeSimulator.BinaryResult r=TradeSimulator.simulateBinary(sample(),0,true,100,1,0.8);
        assertTrue(r.won);
        assertEquals(80.0,r.pnl,0.000001);
    }

    @Test public void binaryDownLosesWhenMarketRises() {
        TradeSimulator.BinaryResult r=TradeSimulator.simulateBinary(sample(),0,false,100,1,0.8);
        assertFalse(r.won);
        assertEquals(-100.0,r.pnl,0.000001);
    }

    @Test(expected=IllegalArgumentException.class)
    public void binaryExpiryCannotExceedOneDay() {
        TradeSimulator.simulateBinary(sample(),0,true,100,1441,0.8);
    }

    @Test public void invalidCandleIsRejected() {
        MarketData.Candle bad=new MarketData.Candle(1,100,99,98,101,10);
        assertFalse(MarketData.isValidCandle(bad));
    }
}
