package com.tradequest.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(9, 11, 15);
    private static final int PANEL = Color.rgb(19, 22, 28);
    private static final int PANEL2 = Color.rgb(25, 29, 36);
    private static final int TEXT = Color.rgb(241, 244, 247);
    private static final int MUTED = Color.rgb(137, 145, 155);
    private static final int GREEN = Color.rgb(183, 243, 107);
    private static final int RED = Color.rgb(255, 104, 112);
    private static final int BORDER = Color.rgb(43, 49, 58);

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private LinearLayout root;
    private int page = 0; // 0 home, 1 practice, 2 setup, 3 result, 4 stats, 5 lessons
    private List<MarketData.Candle> candles = new ArrayList<>();
    private String symbol = "BTCUSDT";
    private String interval = "15";
    private int pivot = -1;
    private TradeSimulator.Result tradeResult;
    private boolean isLong = true;
    private boolean binaryMode = false;
    private boolean loadedBinaryHistory = false;
    private TradeSimulator.BinaryResult binaryResult;
    private static final double BINARY_PAYOUT = 0.80;
    private double balance = 10000.0;
    private int xp = 0, trades = 0, wins = 0;
    private double netPnl = 0;
    private boolean loading = false;
    private TextView practiceStatus;
    private TextView practiceReadout;
    private CandlestickChartView practiceChart;
    private CandlestickChartView resultChart;
    private EditText marginInput, tpInput, slInput;
    private Spinner leverageSpinner;
    private TextView riskPreview;
    private CandlestickChartView setupChart;
    private final List<JSONObject> tradeHistory=new ArrayList<>();
    private int calendarMonthOffset=0;
    private static final String[] PAIRS={"BTCUSDT","ETHUSDT","SOLUSDT","XRPUSDT","DOGEUSDT","ADAUSDT","LINKUSDT","AVAXUSDT","LTCUSDT","BCHUSDT","SUIUSDT","TONUSDT","1000PEPEUSDT","XAUTUSDT"};
    private static final String[] PAIR_LABELS={"BTC · Bitcoin","ETH · Ethereum","SOL · Solana","XRP · Ripple","DOGE · Dogecoin","ADA · Cardano","LINK · Chainlink","AVAX · Avalanche","LTC · Litecoin","BCH · Bitcoin Cash","SUI · Sui","TON · Toncoin","PEPE · Pepe","XAUT · Gold"};
    private final DecimalFormat priceFmt = new DecimalFormat("#,##0.00");
    private final DecimalFormat moneyFmt = new DecimalFormat("+#,##0.00;-#,##0.00");

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        prefs = getSharedPreferences("tradequest_local", MODE_PRIVATE);
        balance = Double.longBitsToDouble(prefs.getLong("balance", Double.doubleToLongBits(10000.0)));
        xp = prefs.getInt("xp", 0);
        trades = prefs.getInt("trades", 0);
        wins = prefs.getInt("wins", 0);
        netPnl = Double.longBitsToDouble(prefs.getLong("netPnl", Double.doubleToLongBits(0.0)));
        try { JSONArray a=new JSONArray(prefs.getString("trade_history","[]"));for(int i=0;i<a.length();i++)tradeHistory.add(a.getJSONObject(i)); }catch(Exception ignored){}
        if(prefs.getString("profile_name","").trim().isEmpty())showWelcome();else showHome();
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private GradientDrawable shape(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor);
        return d;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        String shown=value;if(value!=null&&value.matches(".*[А-ЯЁ].*")&&value.equals(value.toUpperCase(new Locale("ru")))){String lower=value.toLowerCase(new Locale("ru"));shown=lower.substring(0,1).toUpperCase(new Locale("ru"))+lower.substring(1);}v.setText(shown); v.setTextSize(size); v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        v.setIncludeFontPadding(false);
        return v;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l;
    }

    private void pad(View v, int left, int top, int right, int bottom) {
        v.setPadding(dp(left), dp(top), dp(right), dp(bottom));
    }

    private LinearLayout.LayoutParams lp(int width, int height) { return new LinearLayout.LayoutParams(width < 0 ? width : dp(width), height < 0 ? height : dp(height)); }
    private void gap(LinearLayout parent, int height) { View v = new View(this); parent.addView(v, lp(1, height)); }

    private LinearLayout card() {
        LinearLayout c = column(); c.setBackground(shape(PANEL, 16, BORDER, 1)); pad(c, 16, 16, 16, 16); return c;
    }

    private TextView button(String label, boolean primary) {
        TextView b = text(label, 15, primary ? Color.rgb(14, 20, 11) : TEXT, true);
        b.setGravity(Gravity.CENTER); b.setMinHeight(dp(52));
        b.setBackground(ripple(primary ? GREEN : PANEL2, 17));
        b.setClickable(true); b.setFocusable(true);
        b.setElevation(dp(primary ? 3 : 0));
        return b;
    }

    private android.graphics.drawable.Drawable ripple(int color, int radius) {
        GradientDrawable bg = shape(color, radius, color == GREEN ? GREEN : BORDER, 1);
        return new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(Color.argb(45, 255, 255, 255)), bg, null);
    }

    private void addButton(LinearLayout parent, String label, boolean primary, View.OnClickListener listener) {
        TextView b=button(label,primary);LinearLayout.LayoutParams p=lp(-1,52);p.bottomMargin=dp(8);parent.addView(b,p);
        b.setOnClickListener(v->{v.animate().scaleX(.985f).scaleY(.985f).setDuration(70).withEndAction(()->v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()).start();listener.onClick(v);});
    }

    private void addProfileButton(LinearLayout parent,String label,View.OnClickListener listener){
        TextView b=button(label,false);b.setGravity(Gravity.CENTER);LinearLayout.LayoutParams p=lp(-1,58);p.bottomMargin=dp(12);parent.addView(b,p);b.setOnClickListener(listener);
    }

    private void beginPage(String title, boolean back) {
        root = column(); root.setBackgroundColor(BG); root.setAlpha(0f);
        setContentView(root);
        LinearLayout header = row(); pad(header, 20, 14, 20, 12);
        if (back) {
            TextView backView = text("‹", 32, TEXT, false); backView.setGravity(Gravity.CENTER);
            header.addView(backView, lp(38, 42)); backView.setOnClickListener(v -> onBackPressed());
        } else {
            TextView mark = text("TQ", 14, Color.rgb(14, 20, 11), true); mark.setGravity(Gravity.CENTER);
            mark.setBackground(shape(GREEN, 13, GREEN, 0)); header.addView(mark, lp(38, 38));
        }
        TextView titleView = text(title, 19, TEXT, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, dp(42), 1); titleLp.leftMargin = dp(10);
        header.addView(titleView, titleLp);
        root.addView(header);
        root.animate().alpha(1f).setDuration(220).start();
    }

    private ScrollView scroll(LinearLayout parent) {
        ScrollView s = new ScrollView(this); s.setFillViewport(false); s.setClipToPadding(false);
        parent.addView(s, new LinearLayout.LayoutParams(-1, 0, 1));
        return s;
    }

    private LinearLayout content(ScrollView scroll) {
        LinearLayout c = column(); pad(c, 20, 8, 20, 24); scroll.addView(c); return c;
    }

    private void addBottomNav(int selected) {
        LinearLayout nav = row(); nav.setBackgroundColor(Color.rgb(13, 15, 20));
        pad(nav, 14, 8, 14, 8); nav.setGravity(Gravity.CENTER);
        String[] labels = {"Главная", "Практика", "Календарь", "Профиль"};
        int[] pages = {0, 1, 8, 9};
        for (int i=0; i<labels.length; i++) {
            final int target = pages[i];
            LinearLayout item = column(); item.setGravity(Gravity.CENTER);
            TextView label = text(labels[i], 12, selected==i?TEXT:MUTED, selected==i); label.setGravity(Gravity.CENTER);
            item.setBackground(selected==i?shape(PANEL2,10,BORDER,1):shape(Color.TRANSPARENT,10,Color.TRANSPARENT,0));item.addView(label);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(40), 1); nav.addView(item, p);
            item.setOnClickListener(v -> { if(target==0)showHome();else if(target==1)showPractice();else if(target==8)showCalendar();else showProfile(); });
        }
        root.addView(nav, lp(-1, 56));
    }

    private void showHome() {
        page = 0; beginPage("TRADEQUEST", false);
        ScrollView s = scroll(root); LinearLayout c = content(s);
        TextView eyebrow = text("ТВОЯ ТОРГОВАЯ ТРЕНИРОВКА", 10, GREEN, true); c.addView(eyebrow);
        gap(c, 8);
        TextView heading = text("Учись на рынке.\nНе рискуя деньгами.", 29, TEXT, true); heading.setLineSpacing(dp(3), 1f); c.addView(heading);
        gap(c, 20);
        LinearLayout wallet = card(); wallet.setBackground(shape(Color.rgb(21, 27, 31), 16, BORDER, 1));
        LinearLayout statsRow = row();
        LinearLayout balCol = column();
        balCol.addView(text("ВИРТУАЛЬНЫЙ БАЛАНС", 10, MUTED, true)); gap(balCol, 8);
        balCol.addView(text(moneyFmt.format(balance) + " USDT", 25, TEXT, true));
        statsRow.addView(balCol, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout xpCol = column(); xpCol.setGravity(Gravity.RIGHT);
        xpCol.addView(text("ОПЫТ", 10, MUTED, true)); gap(xpCol, 8); xpCol.addView(text(xp + " XP", 18, GREEN, true));
        statsRow.addView(xpCol); wallet.addView(statsRow); gap(wallet, 16);
        View line = new View(this); line.setBackgroundColor(BORDER); wallet.addView(line, lp(-1, 1)); gap(wallet, 12);
        LinearLayout bottom = row(); bottom.addView(text("◷  Серия тренировок", 12, MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
        bottom.addView(text(trades + " сделок", 12, TEXT, true)); wallet.addView(bottom); c.addView(wallet);
        gap(c, 24);
        LinearLayout quick=row();
        LinearLayout practiceTile=card();practiceTile.addView(text("ПРАКТИКА",10,GREEN,true));gap(practiceTile,8);practiceTile.addView(text("Исторические сделки",15,TEXT,true));gap(practiceTile,4);practiceTile.addView(text("Работа с реальными свечами",11,MUTED,false));practiceTile.setOnClickListener(v->showPractice());quick.addView(practiceTile,new LinearLayout.LayoutParams(0,dp(154),1));
        LinearLayout calendarTile=card();calendarTile.addView(text("КАЛЕНДАРЬ",10,GREEN,true));gap(calendarTile,8);calendarTile.addView(text("Мои сделки",15,TEXT,true));gap(calendarTile,4);calendarTile.addView(text("Автоматический P&L",11,MUTED,false));calendarTile.setOnClickListener(v->showCalendar());LinearLayout.LayoutParams calp=new LinearLayout.LayoutParams(0,-2,1);calp.leftMargin=dp(8);calp.height=dp(154);quick.addView(calendarTile,calp);c.addView(quick);
        gap(c,24);
        LinearLayout section = row(); section.addView(text("ТВОЙ ПУТЬ", 11, MUTED, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView all = text("Все уроки", 12, GREEN, true); section.addView(all); all.setOnClickListener(v -> showLessons()); c.addView(section);
        gap(c, 12);
        addLessonRow(c, "01", "Как читать свечи", "Тело, тени и движение цены", true, 1);
        addLessonRow(c, "02", "LONG и SHORT", "Два направления сделки", true, 2);
        addLessonRow(c, "03", "Риск и плечо", "Размер позиции без эмоций", false, 3);
        addLessonRow(c, "04", "Историческая практика", "Только настоящие свечи биржи", false, 4);
        addBottomNav(0);
    }

    private void addLessonRow(LinearLayout parent, String num, String title, String sub, boolean done, int idx) {
        LinearLayout item = row(); item.setBackground(shape(PANEL, 17, BORDER, 1)); pad(item, 13, 13, 13, 13);
        TextView n = text(done ? "✓" : num, 14, done ? Color.rgb(12,20,10) : GREEN, true); n.setGravity(Gravity.CENTER);
        n.setBackground(shape(done?GREEN:PANEL2, 13, done?GREEN:BORDER, 1)); item.addView(n, lp(38,38));
        LinearLayout copy = column(); copy.setPadding(dp(11),0,0,0); copy.addView(text(title, 14, TEXT, true)); gap(copy,4); copy.addView(text(sub, 11, MUTED, false));
        item.addView(copy, new LinearLayout.LayoutParams(0,-2,1)); item.addView(text("›",26,MUTED,false));
        LinearLayout.LayoutParams p = lp(-1, 68); p.bottomMargin=dp(9); parent.addView(item,p); item.setOnClickListener(v -> showLessons());
    }

    private void showPractice() {
        page = 1; beginPage("Историческая практика", true);
        ScrollView s = scroll(root); LinearLayout c = content(s);
        LinearLayout pairCard = card();
        pairCard.addView(text("Выбери рынок", 10, MUTED, true)); gap(pairCard, 8);
        LinearLayout selectors = row();
        Spinner pairSpinner = spinner(PAIR_LABELS);
        Spinner tfSpinner = spinner(new String[]{"1 мин", "5 мин", "15 мин", "1 час", "4 часа", "1 день"});
        String[] pairs = PAIRS;
        String[] intervals = {"1", "5", "15", "60", "240", "D"};
        for(int i=0;i<pairs.length;i++) if(pairs[i].equals(symbol)) pairSpinner.setSelection(i);
        for(int i=0;i<intervals.length;i++) if(intervals[i].equals(interval)) tfSpinner.setSelection(i);
        selectors.addView(pairSpinner,new LinearLayout.LayoutParams(0,dp(48),1));
        View spacer = new View(this); selectors.addView(spacer,lp(8,1));
        selectors.addView(tfSpinner,new LinearLayout.LayoutParams(0,dp(48),1)); pairCard.addView(selectors);
        pairSpinner.setOnItemSelectedListener(simpleListener(pos -> { symbol=pairs[pos]; invalidateScenario(); }));
        tfSpinner.setOnItemSelectedListener(simpleListener(pos -> { interval=intervals[pos]; invalidateScenario(); }));
        gap(pairCard, 12);
        practiceStatus = text("Выбери пару и загрузи исторические свечи Bybit.", 12, MUTED, false); pairCard.addView(practiceStatus);
        gap(pairCard, 12); addButton(pairCard, loading?"Загрузка…":"Загрузить случайный момент", false, v -> loadScenario());
        c.addView(pairCard); gap(c, 16);
        LinearLayout modeCard=card();modeCard.addView(text("Тип практики",12,TEXT,true));gap(modeCard,8);
        LinearLayout modeRow=row();TextView classic=button("Обычная сделка",!binaryMode),binary=button("Бинарная",binaryMode);
        modeRow.addView(classic,new LinearLayout.LayoutParams(0,dp(44),1));View modeGap=new View(this);modeRow.addView(modeGap,lp(8,1));modeRow.addView(binary,new LinearLayout.LayoutParams(0,dp(44),1));modeCard.addView(modeRow);
        modeCard.addView(text(binaryMode?"Выбери направление и срок экспирации. Итог зависит от цены закрытия исторической свечи.":"LONG/SHORT с виртуальной маржой, плечом, TP и SL.",11,MUTED,false));
        classic.setOnClickListener(v->{if(binaryMode){binaryMode=false;invalidateScenario();showPractice();}});binary.setOnClickListener(v->{if(!binaryMode){binaryMode=true;invalidateScenario();showPractice();}});c.addView(modeCard);gap(c,16);
        LinearLayout chartCard = card();
        LinearLayout chartTop = row(); chartTop.addView(text("График", 10, MUTED, true),new LinearLayout.LayoutParams(0,-2,1));
        chartTop.addView(text(binaryMode?"Bybit · 1m execution":"Bybit · Linear",10,MUTED,false));chartCard.addView(chartTop);gap(chartCard,8);chartCard.addView(text("Свечи · EMA 20/50 · RSI 14 · объём · уровни",10,MUTED,false));gap(chartCard,8);
        practiceChart = new CandlestickChartView(this); practiceChart.setEmptyLabel("Загружаем историю рынка"); chartCard.addView(practiceChart,lp(-1,230)); gap(chartCard, 8);
        chartCard.addView(text("Будущие свечи скрыты. Решение принимается только по данным, доступным на тот момент.",11,MUTED,false));gap(chartCard,10);practiceReadout=text(buildMarketReadout(candles,pivot),11,TEXT,false);chartCard.addView(practiceReadout);
        c.addView(chartCard); gap(c, 16);
        LinearLayout info = card(); info.addView(text("КАК ЭТО РАБОТАЕТ", 10, GREEN, true)); gap(info, 8);
        info.addView(text("Выбранная свеча уже закрыта. Вход в симуляции будет по открытию следующей свечи — так приложение не использует будущее для расчёта цены входа.", 12, MUTED, false)); c.addView(info);
        gap(c, 16);
        addButton(c, binaryMode?"Выбрать направление и экспирацию":"Настроить сделку", true, v -> { if (pivot < 0 || candles.isEmpty()) toast("Сначала загрузи исторический момент"); else if(binaryMode)showBinarySetup();else showSetup(); });
        addBottomNav(1);
        if (candles.isEmpty() && !loading) loadScenario();
        else if (pivot >= 0) updatePracticeChart();
    }

    private interface PositionAction { void run(int position); }
    private android.widget.AdapterView.OnItemSelectedListener simpleListener(PositionAction action) {
        return new android.widget.AdapterView.OnItemSelectedListener() {
            boolean first=true;
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { if(first){first=false;return;} action.run(position); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        };
    }

    private Spinner spinner(String[] values) {
        Spinner sp = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, values) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView t = text(getItem(position), 13, TEXT, true); t.setGravity(Gravity.CENTER_VERTICAL); pad(t,12,0,8,0); return t;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView t = text(getItem(position), 14, TEXT, false); t.setBackgroundColor(PANEL2); pad(t,14,12,10,12); return t;
            }
        };
        sp.setAdapter(adapter); sp.setBackground(shape(PANEL2,14,BORDER,1)); sp.setPadding(dp(4),0,dp(4),0);
        return sp;
    }

    private void invalidateScenario() {
        candles = new ArrayList<>();
        pivot = -1;
        if (practiceStatus != null) practiceStatus.setText("Пара или таймфрейм изменены. Загрузи новую историческую ситуацию.");
        if (practiceChart != null) {
            practiceChart.setCandles(new ArrayList<>());
            practiceChart.setEmptyLabel("Загрузи новую историю для выбранного рынка");
        }
    }

    private void loadScenario() {
        if (loading) return;
        loading=true;
        final String requestedSymbol=symbol;
        final String requestedInterval=interval;
        final boolean requestedBinary=binaryMode;
        if (practiceStatus != null) practiceStatus.setText("Подключаемся к Bybit и загружаем реальные свечи…");
        io.execute(() -> {
            try {
                List<MarketData.Candle> loaded=requestedBinary?MarketData.loadBinaryHistory(requestedSymbol):MarketData.load(requestedSymbol,requestedInterval);
                Random random=new Random();
                int min=70;
                int max=requestedBinary?loaded.size()-1442:loaded.size()-45;
                if(max<min)throw new Exception("Недостаточно будущих минутных свечей для случайного момента");
                int selected = min + random.nextInt(Math.max(1,max-min+1));
                main.post(() -> {
                    loading=false;
                    if(!requestedSymbol.equals(symbol)||!requestedInterval.equals(interval)||requestedBinary!=binaryMode){if(page==1)loadScenario();return;}
                    candles=loaded;pivot=selected;loadedBinaryHistory=requestedBinary;
                    if(practiceStatus!=null)practiceStatus.setText("Загружено "+loaded.size()+" "+(requestedBinary?"минутных":"исторических")+" свечей · "+formatDate(loaded.get(pivot).time)+" · Bybit API");
                    updatePracticeChart();
                });
            } catch (Exception e) {
                main.post(() -> {
                    loading=false;
                    if(!requestedSymbol.equals(symbol)||!requestedInterval.equals(interval)||requestedBinary!=binaryMode)return;
                    if (practiceStatus!=null) practiceStatus.setText("Не удалось загрузить историю: " + safeMessage(e) + "\nПроверь интернет или попробуй позже.");
                    if (practiceChart!=null) practiceChart.setEmptyLabel("Нет данных. Нажми загрузить ещё раз.");
                });
            }
        });
    }

    private String buildMarketReadout(List<MarketData.Candle> data,int end){
        if(data==null||end<30||end>=data.size())return "Недостаточно данных.";
        int from=Math.max(0,end-59);double close=data.get(end).close;
        double e20=data.get(from).close,e50=e20;
        for(int i=from+1;i<=end;i++){double p=data.get(i).close;e20=p*(2.0/21.0)+e20*(1-2.0/21.0);e50=p*(2.0/51.0)+e50*(1-2.0/51.0);}
        double g=0,l=0;for(int i=end-13;i<=end;i++){double d=data.get(i).close-data.get(i-1).close;if(d>0)g+=d;else l-=d;}
        double rsi=l==0?100:100-100/(1+(g/14.0)/(l/14.0));
        double atr=0;for(int i=end-13;i<=end;i++){MarketData.Candle q=data.get(i),p=data.get(i-1);atr+=Math.max(q.high-q.low,Math.max(Math.abs(q.high-p.close),Math.abs(q.low-p.close)));}atr/=14.0;
        double vol=0;for(int i=Math.max(from,end-20);i<end;i++)vol+=data.get(i).volume;double avg=vol/Math.max(1,Math.min(20,end-from));double vr=avg<=0?0:data.get(end).volume/avg;
        double sup=Double.MAX_VALUE,res=-Double.MAX_VALUE;for(int i=Math.max(from,end-49);i<=end;i++){sup=Math.min(sup,data.get(i).low);res=Math.max(res,data.get(i).high);}
        double e12=data.get(from).close,e26=e12;double[] mline=new double[end-from+1];
        for(int i=from;i<=end;i++){if(i>from){double p=data.get(i).close;e12=p*(2.0/13.0)+e12*(1-2.0/13.0);e26=p*(2.0/27.0)+e26*(1-2.0/27.0);}mline[i-from]=e12-e26;}
        double signal=mline[0];for(int i=1;i<mline.length;i++)signal=mline[i]*(2.0/10.0)+signal*(1-2.0/10.0);double macdHist=mline[mline.length-1]-signal;
        String trend=e20>e50&&close>e20?"Восходящий":(e20<e50&&close<e20?"Нисходящий":"Боковой / смешанный");
        return "Тренд: "+trend+"\nEMA20 / EMA50: "+priceFmt.format(e20)+" / "+priceFmt.format(e50)+"\nRSI14: "+priceFmt.format(rsi)+"\nATR14: "+priceFmt.format(atr)+" ("+priceFmt.format(atr/close*100)+"%)\nMACD 12/26/9: "+(macdHist>=0?"+":"")+priceFmt.format(macdHist)+"\nОбъём: "+priceFmt.format(vr)+"× среднего за 20 свечей\nПоддержка: "+priceFmt.format(sup)+" · Сопротивление: "+priceFmt.format(res)+"\nЭто контекст рынка, а не гарантия направления.";
    }

    private void updatePracticeChart() {
        if(practiceChart==null || pivot<0 || candles.isEmpty()) return;
        int start=Math.max(0,pivot-55);
        practiceChart.setCandles(candles.subList(start,pivot+1));
        practiceChart.setMarkers(Double.NaN,Double.NaN,Double.NaN);
        practiceChart.setEmptyLabel("");
        practiceChart.invalidate();
        if(practiceReadout!=null)practiceReadout.setText(buildMarketReadout(candles,pivot));
    }


    private void showBinarySetup(){
        page=2;beginPage("Бинарная сделка",true);ScrollView sv=scroll(root);LinearLayout c=content(sv);
        LinearLayout chartCard=card();chartCard.addView(text(symbol+" · 1M · ИСТОРИЯ",13,TEXT,true));gap(chartCard,4);
        chartCard.addView(text("Историческая точка · "+formatDate(candles.get(pivot).time),11,MUTED,false));gap(chartCard,8);
        setupChart=new CandlestickChartView(this);setupChart.setCandles(candles.subList(Math.max(0,pivot-48),pivot+1));chartCard.addView(setupChart,lp(-1,220));c.addView(chartCard);gap(c,16);
        LinearLayout dirCard=card();dirCard.addView(text("НАПРАВЛЕНИЕ",12,TEXT,true));gap(dirCard,10);
        LinearLayout dirs=row();TextView up=button("Вверх",isLong),down=button("Вниз",!isLong);
        dirs.addView(up,new LinearLayout.LayoutParams(0,dp(52),1));View sp=new View(this);dirs.addView(sp,lp(10,1));dirs.addView(down,new LinearLayout.LayoutParams(0,dp(52),1));dirCard.addView(dirs);
        up.setOnClickListener(v->{isLong=true;up.setBackground(ripple(GREEN,14));up.setTextColor(BG);down.setBackground(ripple(PANEL2,14));down.setTextColor(TEXT);});
        down.setOnClickListener(v->{isLong=false;down.setBackground(ripple(GREEN,14));down.setTextColor(BG);up.setBackground(ripple(PANEL2,14));up.setTextColor(TEXT);});
        c.addView(dirCard);gap(c,16);
        LinearLayout params=card();params.addView(text("ВРЕМЯ ЭКСПИРАЦИИ И СТАВКА",12,TEXT,true));gap(params,10);
        params.addView(text("Длительность в минутах · от 1 до 1440",12,MUTED,false));gap(params,6);
        EditText expiryMinutes=input("15");expiryMinutes.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);expiryMinutes.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});
        params.addView(expiryMinutes,lp(-1,50));gap(params,6);
        params.addView(text("Примеры: 1 мин · 5 мин · 30 мин · 2 часа · 12 часов · 24 часа. Можно ввести любое целое число минут.",10,MUTED,false));gap(params,12);
        params.addView(text("Сумма сделки · USDT",12,MUTED,false));gap(params,6);EditText stake=input("100");params.addView(stake,lp(-1,50));c.addView(params);gap(c,16);
        LinearLayout preview=card();preview.addView(text("ПРЕДВАРИТЕЛЬНЫЕ УСЛОВИЯ",11,GREEN,true));gap(preview,8);
        TextView details=text("",12,TEXT,false);preview.addView(details);gap(preview,8);
        preview.addView(text("Расчёт по закрытым минутным свечам Bybit. Вход — открытие первой свечи после исторической точки; экспирация — закрытие первой свечи, достигшей выбранного времени. Выплата +80% — учебное условие, не котировка брокера.",10,MUTED,false));c.addView(preview);gap(c,16);
        TextWatcher expiryWatcher=new TextWatcher(){public void beforeTextChanged(CharSequence x,int st,int count,int after){}public void onTextChanged(CharSequence x,int st,int before,int count){updateBinaryPreview(details,expiryMinutes);}public void afterTextChanged(Editable x){}};
        expiryMinutes.addTextChangedListener(expiryWatcher);updateBinaryPreview(details,expiryMinutes);
        addButton(c,"Открыть историческую сделку",true,v->{
            try{
                int minutes=Integer.parseInt(expiryMinutes.getText().toString().trim());
                double amount=parse(stake.getText().toString());
                if(minutes<1||minutes>1440){toast("Укажи срок от 1 до 1440 минут");return;}
                if(amount<=0||amount>balance){toast("Ставка должна быть больше нуля и не превышать баланс");return;}
                binaryResult=TradeSimulator.simulateBinary(candles,pivot,isLong,amount,minutes,BINARY_PAYOUT);
                balance+=binaryResult.pnl;trades++;if(binaryResult.won)wins++;netPnl+=binaryResult.pnl;xp+=binaryResult.won?100:50;
                try{JSONObject t=new JSONObject();t.put("time",System.currentTimeMillis());t.put("symbol",symbol);t.put("interval","1");t.put("side",isLong?"UP":"DOWN");t.put("entry",binaryResult.entry);t.put("exit",binaryResult.exit);t.put("pnl",binaryResult.pnl);t.put("roi",binaryResult.stake==0?0:binaryResult.pnl/binaryResult.stake*100);t.put("reason",binaryResult.draw?"ВОЗВРАТ · РАВНАЯ ЦЕНА":(binaryResult.won?"ВЕРНОЕ НАПРАВЛЕНИЕ":"НЕВЕРНОЕ НАПРАВЛЕНИЕ"));t.put("expiryMinutes",binaryResult.expiryMinutes);t.put("bars",binaryResult.expiryMinutes);t.put("ambiguous",false);t.put("type","BINARY");tradeHistory.add(t);}catch(Exception ignored){}
                persist();showBinaryResult();
            }catch(NumberFormatException e){toast("Введи длительность в минутах");}
            catch(Exception e){toast("Не удалось рассчитать сделку: "+safeMessage(e));}
        });addBottomNav(1);
    }

    private int binaryExpiryIndex(int minutes){
        if(pivot<0||pivot+1>=candles.size())return -1;
        long target=candles.get(pivot+1).time+minutes*60000L;
        for(int i=pivot+1;i<candles.size();i++)if(candles.get(i).time+60000L>=target)return i;
        return -1;
    }

    private void updateBinaryPreview(TextView target,EditText expiryInput){
        try{
            int minutes=Integer.parseInt(expiryInput.getText().toString().trim());
            if(minutes<1||minutes>1440){target.setText("Допустимый срок: от 1 до 1440 минут.");return;}
            int exitIndex=binaryExpiryIndex(minutes);
            if(exitIndex<0){target.setText("Для выбранного времени не хватает будущих минутных свечей. Загрузи новый исторический момент.");return;}
            MarketData.Candle entry=candles.get(pivot+1),exit=candles.get(exitIndex);
            target.setText("Направление: "+(isLong?"ВВЕРХ":"ВНИЗ")+"\n"+
                    "Цена входа: "+priceFmt.format(entry.open)+"\n"+
                    "Время входа: "+formatDate(entry.time)+"\n"+
                    "Время экспирации: "+formatDate(exit.time+60000L)+"\n"+
                    "Ожидаемая расчётная свеча: "+priceFmt.format(exit.close)+"\n"+
                    "Ставка при выигрыше: +80% · при проигрыше: −100%");
        }catch(Exception e){target.setText("Введи целое число минут от 1 до 1440.");}
    }

    private void showBinaryResult(){
        page=3;beginPage("Результат бинарной сделки",true);ScrollView sv=scroll(root);LinearLayout c=content(sv);
        boolean positive=binaryResult.pnl>0;LinearLayout result=card();
        result.addView(text(binaryResult.draw?"ВОЗВРАТ СТАВКИ":(binaryResult.won?"ВЕРНО":"НЕВЕРНО"),11,binaryResult.draw?MUTED:(positive?GREEN:RED),true));gap(result,8);
        result.addView(text(moneyFmt.format(binaryResult.pnl)+" USDT",30,TEXT,true));gap(result,8);
        result.addView(text((isLong?"Вверх":"Вниз")+" · "+binaryDurationLabel(binaryResult.expiryMinutes)+" · Выплата "+(int)(binaryResult.payoutRate*100)+"%",12,MUTED,false));c.addView(result);gap(c,12);
        LinearLayout chart=card();chart.addView(text(symbol+" · минутная история до экспирации",12,TEXT,true));gap(chart,8);
        resultChart=new CandlestickChartView(this);resultChart.setCandles(candles.subList(Math.max(0,pivot-48),binaryResult.exitIndex+1));resultChart.setBinaryMarkers(binaryResult.entry,binaryResult.exit);chart.addView(resultChart,lp(-1,230));gap(chart,8);
        chart.addView(text("Вход "+priceFmt.format(binaryResult.entry)+" · Экспирация "+priceFmt.format(binaryResult.exit),11,MUTED,false));c.addView(chart);gap(c,12);
        LinearLayout explain=card();explain.addView(text("КАК ПОЛУЧЕН РЕЗУЛЬТАТ",11,GREEN,true));gap(explain,8);
        explain.addView(text("Сравнили открытие первой минутной свечи после выбранной точки с закрытием свечи, достигшей заданного времени экспирации. Длительность: "+binaryDurationLabel(binaryResult.expiryMinutes)+". Будущие свечи не использовались при выборе направления. Результат сохранён в календаре.",12,MUTED,false));c.addView(explain);gap(c,12);
        addButton(c,"Следующая историческая ситуация",true,v->{binaryMode=true;invalidateScenario();showPractice();});addButton(c,"Вернуться в практику",false,v->showPractice());addBottomNav(1);
    }

    private String binaryDurationLabel(int minutes){
        if(minutes<60)return minutes+" мин";
        if(minutes%60==0)return (minutes/60)+" ч";
        return (minutes/60)+" ч "+(minutes%60)+" мин";
    }

    private void showSetup() {
        if(binaryMode){showBinarySetup();return;}
        page=2; beginPage("Настройка сделки", true);
        ScrollView s=scroll(root); LinearLayout c=content(s);
        LinearLayout symbolCard=card();
        symbolCard.addView(text(symbol+"  ·  "+intervalLabel(interval)+"  ·  ИСТОРИЯ",13,TEXT,true)); gap(symbolCard,4);
        symbolCard.addView(text("Момент: "+formatDate(candles.get(pivot).time),11,MUTED,false)); gap(symbolCard,12);
        setupChart=new CandlestickChartView(this); setupChart.setCandles(candles.subList(Math.max(0,pivot-42),pivot+1));
        symbolCard.addView(setupChart,lp(-1,185)); c.addView(symbolCard); gap(c,12);
        LinearLayout direction=card(); direction.addView(text("НАПРАВЛЕНИЕ ПОЗИЦИИ",10,MUTED,true)); gap(direction,8);
        LinearLayout sides=row();
        TextView longBtn=button("LONG",isLong), shortBtn=button("SHORT",!isLong);
        sides.addView(longBtn,new LinearLayout.LayoutParams(0,dp(48),1)); View sep=new View(this); sides.addView(sep,lp(8,1)); sides.addView(shortBtn,new LinearLayout.LayoutParams(0,dp(48),1)); direction.addView(sides);
        longBtn.setOnClickListener(v->{isLong=true;longBtn.setBackground(ripple(GREEN,15));longBtn.setTextColor(Color.rgb(14,20,11));shortBtn.setBackground(ripple(PANEL2,15));shortBtn.setTextColor(TEXT);updateRiskPreview();});
        shortBtn.setOnClickListener(v->{isLong=false;shortBtn.setBackground(ripple(GREEN,15));shortBtn.setTextColor(Color.rgb(14,20,11));longBtn.setBackground(ripple(PANEL2,15));longBtn.setTextColor(TEXT);updateRiskPreview();});
        c.addView(direction); gap(c,12);
        LinearLayout params=card(); params.addView(text("ПАРАМЕТРЫ СДЕЛКИ",10,MUTED,true)); gap(params,12);
        params.addView(text("Сумма маржи · USDT",12,TEXT,true)); gap(params,8); marginInput=input("100"); params.addView(marginInput,lp(-1,48)); gap(params,12);
        params.addView(text("Плечо",12,TEXT,true)); gap(params,8);
        leverageSpinner=spinner(new String[]{"1×","2×","3×","5×","10×","20×","50×"}); leverageSpinner.setSelection(3); params.addView(leverageSpinner,lp(-1,48)); gap(params,12);
        LinearLayout two=row();
        LinearLayout tpCol=column(),slCol=column();
        tpCol.addView(text("Take Profit · %",12,TEXT,true)); gap(tpCol,8); tpInput=input("2.0"); tpCol.addView(tpInput,lp(-1,48));
        slCol.addView(text("Stop Loss · %",12,TEXT,true)); gap(slCol,8); slInput=input("1.0"); slCol.addView(slInput,lp(-1,48));
        two.addView(tpCol,new LinearLayout.LayoutParams(0,-2,1)); View sp=new View(this);two.addView(sp,lp(10,1));two.addView(slCol,new LinearLayout.LayoutParams(0,-2,1)); params.addView(two);
        c.addView(params); gap(c,12);
        LinearLayout preview=card(); preview.addView(text("ПРЕДВАРИТЕЛЬНЫЙ РАСЧЁТ",10,GREEN,true)); gap(preview,8);
        riskPreview=text("Загружаем расчёт…",13,TEXT,false); preview.addView(riskPreview); gap(preview,8);
        preview.addView(text("Результат считается по следующим реальным OHLC-свечам. Комиссии, funding и точная ликвидация пока не моделируются — это учебный P&L, не расчёт для реальной сделки.",10,MUTED,false)); c.addView(preview); gap(c,16);
        TextView confirm=button("Подтвердить и пройти историю",true); c.addView(confirm,lp(-1,54));
        confirm.setOnClickListener(v->{
            try {
                double margin=parse(marginInput.getText().toString());
                double tp=parse(tpInput.getText().toString()); double sl=parse(slInput.getText().toString());
                if(margin>balance) {toast("Маржа больше доступного баланса");return;}
                int lev=leverageValue();
                tradeResult=TradeSimulator.simulate(candles,pivot,isLong,margin,lev,tp,sl);
                balance+=tradeResult.pnl;
                trades++; if(tradeResult.pnl>0) wins++;
                try{JSONObject t=new JSONObject();t.put("time",System.currentTimeMillis());t.put("symbol",symbol);t.put("interval",interval);t.put("side",isLong?"LONG":"SHORT");t.put("entry",tradeResult.entry);t.put("exit",tradeResult.exit);t.put("pnl",tradeResult.pnl);t.put("roi",tradeResult.roi);t.put("reason",tradeResult.reason);t.put("bars",tradeResult.barsHeld);t.put("ambiguous",tradeResult.ambiguous);tradeHistory.add(t);}catch(Exception ignored){}
                netPnl+=tradeResult.pnl; xp+=tradeResult.hitTp?150:100;
                persist(); showResult(margin,lev,tp,sl);
            } catch(Exception e){toast("Проверь параметры: "+safeMessage(e));}
        });
        TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){updateRiskPreview();}public void afterTextChanged(Editable e){}};
        marginInput.addTextChangedListener(watcher);tpInput.addTextChangedListener(watcher);slInput.addTextChangedListener(watcher);
        leverageSpinner.setOnItemSelectedListener(simpleListener(pos -> updateRiskPreview()));
        updateRiskPreview();
    }

    private EditText input(String value) {
        EditText e=new EditText(this); e.setSingleLine(true); e.setText(value); e.setTextSize(15); e.setTextColor(TEXT); e.setHintTextColor(MUTED);
        e.setSelectAllOnFocus(true); e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setBackground(shape(PANEL2,13,BORDER,1)); pad(e,13,0,13,0); return e;
    }

    private EditText textInput(String value){EditText e=new EditText(this);e.setSingleLine(true);e.setText(value);e.setTextSize(15);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);e.setBackground(shape(PANEL2,10,BORDER,1));pad(e,12,0,12,0);return e;}

    private double parse(String value) { return Double.parseDouble(value.trim().replace(',','.')); }
    private int leverageValue() { if(leverageSpinner==null)return 5; String s=(String)leverageSpinner.getSelectedItem(); return Integer.parseInt(s.replace("×","")); }
    private void updateRiskPreview() {
        if(riskPreview==null||marginInput==null||tpInput==null||slInput==null||pivot<0||pivot+1>=candles.size())return;
        try {
            double margin=parse(marginInput.getText().toString()), tp=parse(tpInput.getText().toString()), sl=parse(slInput.getText().toString());
            int lev=leverageValue(); double entry=candles.get(pivot+1).open;
            double notional=margin*lev; double win=notional*tp/100.0; double loss=notional*sl/100.0;
            double tpPrice=isLong?entry*(1+tp/100):entry*(1-tp/100);
            double slPrice=isLong?entry*(1-sl/100):entry*(1+sl/100);
            if(setupChart!=null)setupChart.setMarkers(entry,tpPrice,slPrice);
            riskPreview.setText("Цена входа (следующая свеча):  "+priceFmt.format(entry)+"\n"+
                    "Размер позиции:  "+priceFmt.format(notional)+" USDT\n"+
                    "Take Profit:  "+priceFmt.format(tpPrice)+"  ·  +"+priceFmt.format(win)+" USDT\n"+
                    "Stop Loss:  "+priceFmt.format(slPrice)+"  ·  −"+priceFmt.format(loss)+" USDT\n"+
                    "Риск от баланса:  "+priceFmt.format(loss/balance*100)+"%");
        } catch(Exception e){riskPreview.setText("Введи корректные числовые параметры.");}
    }

    private void showResult(double margin,int leverage,double tp,double sl) {
        page=3; beginPage("Разбор сделки",true);
        ScrollView s=scroll(root); LinearLayout c=content(s);
        boolean win=tradeResult.pnl>0;
        LinearLayout result=card(); result.setBackground(shape(win?Color.rgb(20,31,24):Color.rgb(34,23,26),22,win?Color.rgb(62,100,49):Color.rgb(112,55,61),1));
        result.addView(text(tradeResult.reason,11,win?GREEN:RED,true)); gap(result,8);
        result.addView(text(moneyFmt.format(tradeResult.pnl)+" USDT",32,TEXT,true)); gap(result,4);
        result.addView(text(""+(tradeResult.roi>=0?"+":"")+priceFmt.format(tradeResult.roi)+"% к марже  ·  "+(isLong?"LONG":"SHORT"),12,MUTED,false)); c.addView(result); gap(c,12);
        LinearLayout chart=card(); LinearLayout top=row();top.addView(text(symbol+"  ·  "+intervalLabel(interval),13,TEXT,true),new LinearLayout.LayoutParams(0,-2,1));top.addView(text("ИСТОРИЯ РАСКРЫТА",9,GREEN,true));chart.addView(top);gap(chart,12);
        resultChart=new CandlestickChartView(this);
        resultChart.setCandles(candles.subList(Math.max(0,pivot-48),Math.min(candles.size(),tradeResult.exitIndex+1)));
        double entry=tradeResult.entry;double tpp=isLong?entry*(1+tp/100):entry*(1-tp/100);double slp=isLong?entry*(1-sl/100):entry*(1+sl/100);
        resultChart.setMarkers(entry,tpp,slp);chart.addView(resultChart,lp(-1,250)); c.addView(chart); gap(c,12);
        LinearLayout stats=card(); stats.addView(text("ДЕТАЛИ СДЕЛКИ",10,MUTED,true));gap(stats,12);
        addDetail(stats,"Цена входа",priceFmt.format(tradeResult.entry));
        addDetail(stats,"Цена выхода",priceFmt.format(tradeResult.exit));
        addDetail(stats,"Маржа / плечо",priceFmt.format(margin)+" USDT / "+leverage+"×");
        addDetail(stats,"Время в позиции",tradeResult.barsHeld+" свечей · "+intervalLabel(interval));
        addDetail(stats,"Реализованный P&L",moneyFmt.format(tradeResult.pnl)+" USDT");
        addDetail(stats,"Баланс после сделки",moneyFmt.format(balance)+" USDT");
        if(tradeResult.ambiguous){gap(stats,8);stats.addView(text("Обе границы попали внутрь одной OHLC-свечи. Для консервативного результата засчитан Stop Loss первым.",11,RED,false));}
        c.addView(stats);gap(c,12);
        LinearLayout learning=card();learning.addView(text("РАЗБОР СИТУАЦИИ",10,GREEN,true));gap(learning,8);
        String lessonText = tradeResult.hitTp
                ? "Цена достигла Take Profit раньше Stop Loss. Проверь, соответствовал ли размер позиции твоему плану риска."
                : ("STOP LOSS".equals(tradeResult.reason)
                    ? "Stop Loss был достигнут раньше Take Profit. Разбери, был ли риск на сделку заранее приемлемым. Не отодвигай стоп задним числом."
                    : "Горизонт симуляции закончился до TP или SL. Позиция закрыта по последней доступной цене истории. Такой результат не равен достижению цели.");
        learning.addView(text(lessonText,12,TEXT,false));gap(learning,8);
        learning.addView(text("Начислено: +"+(tradeResult.hitTp?150:100)+" XP за завершённую тренировку. Виртуальный баланс обновлён.",11,MUTED,false));c.addView(learning);gap(c,16);
        addButton(c,"Следующая историческая ситуация",true,v->{tradeResult=null;showPractice();loadScenario();});
        addButton(c,"Посмотреть прогресс",false,v->showStats());
        addBottomNav(1);
    }

    private void addDetail(LinearLayout parent,String label,String value) {
        LinearLayout r=row();r.addView(text(label,12,MUTED,false),new LinearLayout.LayoutParams(0,dp(30),1));r.addView(text(value,12,TEXT,true));parent.addView(r);
    }

    private void showStats() {
        page=4;beginPage("Твой прогресс",true);ScrollView s=scroll(root);LinearLayout c=content(s);
        LinearLayout hero=card();hero.addView(text("ТВОЯ СТАТИСТИКА",10,GREEN,true));gap(hero,8);hero.addView(text(xp+" XP",32,TEXT,true));gap(hero,4);hero.addView(text("Опыт за реальные исторические тренировки",12,MUTED,false));c.addView(hero);gap(c,12);
        LinearLayout grid=row();addMetric(grid,"СДЕЛОК",String.valueOf(trades));addMetric(grid,"WIN RATE",trades==0?"—":priceFmt.format((double)wins/trades*100)+"%");c.addView(grid);gap(c,8);
        LinearLayout grid2=row();addMetric(grid2,"ЧИСТЫЙ P&L",moneyFmt.format(netPnl)+" USDT");addMetric(grid2,"БАЛАНС",priceFmt.format(balance)+" USDT");c.addView(grid2);gap(c,16);
        LinearLayout note=card();note.addView(text("ЧЕСТНАЯ СТАТИСТИКА",10,GREEN,true));gap(note,8);note.addView(text("Все показатели собраны только из завершённых симуляций на исторических свечах. Положительный результат одной сделки не гарантирует будущую прибыль.",12,MUTED,false));c.addView(note);gap(c,16);
        addButton(c,"Вернуться к тренировке",true,v->showPractice());
        addBottomNav(2);
    }

    private void addMetric(LinearLayout parent,String title,String value) {
        LinearLayout m=card();m.addView(text(title,9,MUTED,true));gap(m,8);m.addView(text(value,20,TEXT,true));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(98),1);p.rightMargin=dp(7);parent.addView(m,p);
    }

    private void showLessons() {
        page=5;beginPage("Карта обучения",true);ScrollView s=scroll(root);LinearLayout c=content(s);
        c.addView(text("От первых свечей до уверенного риск-менеджмента — шаг за шагом.",14,MUTED,false));gap(c,16);
        String[][] lessons=lessonCatalog();
        for(int i=0;i<lessons.length;i++){
            LinearLayout l=card();l.addView(text(lessons[i][0],10,GREEN,true));gap(l,8);l.addView(text(lessons[i][1],17,TEXT,true));gap(l,4);l.addView(text(lessons[i][2],12,MUTED,false));gap(l,12);
            final int idx=i;addButton(l,"Открыть урок",true,v->showLessonDetail(idx));
            LinearLayout.LayoutParams p=lp(-1,-2);p.bottomMargin=dp(10);c.addView(l,p);
        }
        addBottomNav(0);
    }


    private void showWelcome(){
        page=6;beginPage("Добро пожаловать",false);ScrollView sv=scroll(root);LinearLayout c=content(sv);
        gap(c,24);TextView logo=text("TQ",32,BG,true);logo.setGravity(Gravity.CENTER);logo.setBackground(shape(GREEN,20,GREEN,0));c.addView(logo,lp(76,76));gap(c,20);
        c.addView(text("Начни свой путь в трейдинге.",27,TEXT,true));gap(c,8);
        c.addView(text("Создай профиль, чтобы сохранять прогресс и историю сделок на этом устройстве.",14,MUTED,false));gap(c,20);
        LinearLayout box=card();box.addView(text("ИМЯ ИЛИ НИКНЕЙМ",10,GREEN,true));gap(box,8);EditText name=textInput("");name.setHint("Как к тебе обращаться?");box.addView(name,lp(-1,50));gap(box,12);
        box.addView(text("ПОЧТА · НЕОБЯЗАТЕЛЬНО",10,MUTED,true));gap(box,8);EditText email=textInput("");email.setHint("name@example.com");email.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);box.addView(email,lp(-1,50));c.addView(box);gap(c,16);
        addButton(c,"Продолжить",true,v->{String n=name.getText().toString().trim();if(n.length()<2){toast("Введи имя или никнейм (минимум 2 символа)");return;}prefs.edit().putString("profile_name",n).putString("profile_email",email.getText().toString().trim()).apply();showHome();});
        c.addView(text("Это локальный профиль без облачной регистрации. Данные хранятся на устройстве.",11,MUTED,false));
    }

    private String[][] lessonCatalog(){ return LessonCatalog.all(); }

    private void showLessonDetail(int index){
        String[][] data=lessonCatalog();if(index<0||index>=data.length)return;page=7;beginPage("Урок "+(index+1)+" / "+data.length,true);
        ScrollView sv=scroll(root);LinearLayout c=content(sv);LinearLayout hero=card();hero.addView(text(data[index][0],10,GREEN,true));gap(hero,8);hero.addView(text(data[index][1],24,TEXT,true));gap(hero,12);hero.addView(text(data[index][2],15,MUTED,false));c.addView(hero);gap(c,12);
        LinearLayout note=card();note.addView(text("ПРАКТИЧЕСКИЙ ВЫВОД",10,GREEN,true));gap(note,8);note.addView(text(data[index].length>3?data[index][3]:"Перед каждой сделкой определи сценарий, точку отмены и максимальный допустимый убыток. Не принимай решение только по одному индикатору.",13,TEXT,false));c.addView(note);gap(c,12);
        boolean done=prefs.getBoolean("lesson_done_"+index,false);addButton(c,done?"Урок пройден ✓":"Отметить урок пройденным · +25 XP",true,v->{if(!prefs.getBoolean("lesson_done_"+index,false)){xp+=25;prefs.edit().putBoolean("lesson_done_"+index,true).apply();persist();}showLessonDetail(index);});
        if(index<data.length-1)addButton(c,"Следующий урок",false,v->showLessonDetail(index+1));else addButton(c,"Перейти к практике",false,v->showPractice());addBottomNav(0);
    }

    private void showCalendar(){
        page=8;beginPage("Календарь сделок",true);ScrollView sv=scroll(root);LinearLayout c=content(sv);
        Calendar cal=Calendar.getInstance();cal.add(Calendar.MONTH,calendarMonthOffset);int year=cal.get(Calendar.YEAR),month=cal.get(Calendar.MONTH);
        LinearLayout nav=row();TextView prev=text("‹",28,TEXT,true);prev.setGravity(Gravity.CENTER);nav.addView(prev,lp(40,42));TextView title=text(new SimpleDateFormat("LLLL yyyy",new Locale("ru")).format(cal.getTime()),18,TEXT,true);title.setGravity(Gravity.CENTER);nav.addView(title,new LinearLayout.LayoutParams(0,dp(42),1));TextView next=text("›",28,TEXT,true);next.setGravity(Gravity.CENTER);nav.addView(next,lp(40,42));c.addView(nav);prev.setOnClickListener(v->{calendarMonthOffset--;showCalendar();});next.setOnClickListener(v->{calendarMonthOffset++;showCalendar();});
        double total=0;int cnt=0,w=0,l=0;for(JSONObject t:tradeHistory){Calendar d=Calendar.getInstance();d.setTimeInMillis(t.optLong("time"));if(d.get(Calendar.YEAR)==year&&d.get(Calendar.MONTH)==month){double p=t.optDouble("pnl");total+=p;cnt++;if(p>0)w++;else if(p<0)l++;}}
        LinearLayout sum=card();sum.addView(text("МЕСЯЧНЫЙ P&L",10,MUTED,true));gap(sum,8);sum.addView(text(moneyFmt.format(total)+" USDT",26,total>=0?GREEN:RED,true));gap(sum,4);sum.addView(text(cnt+" сделок · "+w+" прибыльных · "+l+" убыточных",12,MUTED,false));c.addView(sum);gap(c,12);
        LinearLayout weekdays=row();for(String x:new String[]{"Пн","Вт","Ср","Чт","Пт","Сб","Вс"}){TextView v=text(x,10,MUTED,true);v.setGravity(Gravity.CENTER);weekdays.addView(v,new LinearLayout.LayoutParams(0,dp(28),1));}c.addView(weekdays);
        Calendar first=new GregorianCalendar(year,month,1);int days=first.getActualMaximum(Calendar.DAY_OF_MONTH),offset=(first.get(Calendar.DAY_OF_WEEK)+5)%7,rows=(offset+days+6)/7;
        for(int r=0;r<rows;r++){LinearLayout line=row();for(int col=0;col<7;col++){int day=r*7+col-offset;LinearLayout cell=column();cell.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);cell.setBackground(shape(PANEL,9,BORDER,1));pad(cell,2,5,2,3);if(day>=1&&day<=days){cell.addView(text(String.valueOf(day),11,TEXT,true));double pnl=0;int n=0;for(JSONObject t:tradeHistory){Calendar d=Calendar.getInstance();d.setTimeInMillis(t.optLong("time"));if(d.get(Calendar.YEAR)==year&&d.get(Calendar.MONTH)==month&&d.get(Calendar.DAY_OF_MONTH)==day){pnl+=t.optDouble("pnl");n++;}}if(n>0){gap(cell,4);TextView v=text((pnl>=0?"+":"")+new DecimalFormat("0.#").format(pnl),8,pnl>=0?GREEN:RED,true);v.setGravity(Gravity.CENTER);cell.addView(v);TextView q=text(n+" сд.",8,MUTED,false);q.setGravity(Gravity.CENTER);cell.addView(q);cell.setBackground(shape(pnl>=0?Color.rgb(20,43,30):Color.rgb(47,27,31),9,BORDER,1));}}line.addView(cell,new LinearLayout.LayoutParams(0,dp(64),1));}LinearLayout.LayoutParams rp=lp(-1,64);rp.bottomMargin=dp(4);c.addView(line,rp);}
        gap(c,12);c.addView(text("СДЕЛКИ ЗА МЕСЯЦ",10,MUTED,true));gap(c,8);boolean any=false;
        for(int i=tradeHistory.size()-1;i>=0;i--){JSONObject t=tradeHistory.get(i);Calendar d=Calendar.getInstance();d.setTimeInMillis(t.optLong("time"));if(d.get(Calendar.YEAR)==year&&d.get(Calendar.MONTH)==month){any=true;LinearLayout item=card();LinearLayout row=row();row.addView(text(t.optString("symbol")+" · "+t.optString("side"),13,TEXT,true),new LinearLayout.LayoutParams(0,-2,1));double p=t.optDouble("pnl");row.addView(text(moneyFmt.format(p)+" USDT",13,p>=0?GREEN:RED,true));item.addView(row);gap(item,4);item.addView(text(new SimpleDateFormat("dd MMM · HH:mm",new Locale("ru")).format(new Date(t.optLong("time")))+" · "+t.optString("reason"),10,MUTED,false));LinearLayout.LayoutParams ip=lp(-1,-2);ip.bottomMargin=dp(7);c.addView(item,ip);}}
        if(!any)c.addView(text("Сделок за этот месяц пока нет. После каждой завершённой тренировки запись появится здесь автоматически.",12,MUTED,false));addBottomNav(2);
    }

    private void showProfile(){
        page=9;beginPage("Личный кабинет",true);ScrollView sv=scroll(root);LinearLayout c=content(sv);String name=prefs.getString("profile_name","Трейдер");
        LinearLayout hero=card();TextView avatar=text(name.substring(0,1).toUpperCase(Locale.getDefault()),28,BG,true);avatar.setGravity(Gravity.CENTER);avatar.setBackground(shape(GREEN,28,GREEN,0));hero.addView(avatar,lp(58,58));gap(hero,8);hero.addView(text(name,23,TEXT,true));gap(hero,4);hero.addView(text(prefs.getString("profile_email","Локальный профиль"),12,MUTED,false));gap(hero,8);hero.addView(text("Уровень "+(xp/500+1)+" · "+xp+" XP",13,GREEN,true));c.addView(hero);gap(c,12);
        LinearLayout metrics=row();addMetric(metrics,"СДЕЛОК",String.valueOf(trades));addMetric(metrics,"WIN RATE",trades==0?"—":priceFmt.format((double)wins/trades*100)+"%");c.addView(metrics);gap(c,12);
        addProfileButton(c,"Календарь сделок",v->showCalendar());
        addProfileButton(c,"Мой прогресс",v->showStats());
        addProfileButton(c,"Карта обучения",v->showLessons());
        addProfileButton(c,"Изменить имя",v->{EditText e=textInput(name);new android.app.AlertDialog.Builder(this).setTitle("Имя профиля").setView(e).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(d,w)->{String n=e.getText().toString().trim();if(n.length()>1){prefs.edit().putString("profile_name",n).apply();showProfile();}}).show();});
        addProfileButton(c,"Выйти из профиля",v->new android.app.AlertDialog.Builder(this).setTitle("Выйти?").setMessage("Локальная история и прогресс сохранятся на устройстве.").setNegativeButton("Отмена",null).setPositiveButton("Выйти",(d,w)->{prefs.edit().remove("profile_name").remove("profile_email").apply();showWelcome();}).show());addBottomNav(3);
    }

    private void persist() {
        JSONArray a=new JSONArray();for(JSONObject t:tradeHistory)a.put(t);
        prefs.edit().putLong("balance",Double.doubleToLongBits(balance)).putInt("xp",xp).putInt("trades",trades).putInt("wins",wins).putLong("netPnl",Double.doubleToLongBits(netPnl)).putString("trade_history",a.toString()).apply();
    }

    private void toast(String value) { Toast.makeText(this,value,Toast.LENGTH_LONG).show(); }
    private String safeMessage(Exception e) { String m=e.getMessage(); return m==null?"ошибка сети":m; }
    private String intervalLabel(String i) { if("D".equals(i))return "1D"; if("240".equals(i))return "4H"; if("60".equals(i))return "1H"; return i+"M"; }
    private String formatDate(long time) { return new SimpleDateFormat("dd.MM.yyyy · HH:mm",Locale.getDefault()).format(new Date(time)); }

    @Override public void onBackPressed() {
        if(page==3||page==2)showPractice();else if(page==7)showLessons();else if(page==1||page==4||page==5||page==8||page==9)showHome();else super.onBackPressed();
    }

    @Override protected void onDestroy() { super.onDestroy(); io.shutdownNow(); }

    private class CandlestickChartView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private List<MarketData.Candle> values=new ArrayList<>();
        private double entry=Double.NaN,tp=Double.NaN,sl=Double.NaN;
        private String tpTag="TP",slTag="SL";
        private String emptyLabel="";
        CandlestickChartView(Context context){super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void setCandles(List<MarketData.Candle> data){values=new ArrayList<>(data);setAlpha(.35f);animate().alpha(1f).setDuration(180).start();invalidate();}
        void setMarkers(double e,double t,double s){entry=e;tp=t;sl=s;tpTag="TP";slTag="SL";invalidate();}
        void setBinaryMarkers(double e,double expiry){entry=e;tp=expiry;sl=Double.NaN;tpTag="EXP";invalidate();}
        void setEmptyLabel(String s){emptyLabel=s;invalidate();}
        private void line(Canvas c,float x1,float y1,float x2,float y2,int color,float width){p.setColor(color);p.setStrokeWidth(width);p.setStyle(Paint.Style.STROKE);p.setPathEffect(null);c.drawLine(x1,y1,x2,y2,p);p.setStyle(Paint.Style.FILL);}
        private void label(Canvas c,String s,float x,float y,int color,float size){p.setColor(color);p.setTextSize(size);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setStyle(Paint.Style.FILL);c.drawText(s,x,y,p);}
        private double ema(int index,int period){double alpha=2.0/(period+1.0),v=values.get(0).close;for(int i=1;i<=index;i++)v=values.get(i).close*alpha+v*(1-alpha);return v;}
        private double rsi(int period){if(values.size()<period+1)return Double.NaN;double gain=0,loss=0;for(int i=1;i<=period;i++){double d=values.get(i).close-values.get(i-1).close;if(d>0)gain+=d;else loss-=d;}double ag=gain/period,al=loss/period;for(int i=period+1;i<values.size();i++){double d=values.get(i).close-values.get(i-1).close;ag=(ag*(period-1)+Math.max(0,d))/period;al=(al*(period-1)+Math.max(0,-d))/period;}if(al==0)return 100;double rs=ag/al;return 100-(100/(1+rs));}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float w=getWidth(),h=getHeight();canvas.drawColor(PANEL);
            if(values.isEmpty()){label(canvas,emptyLabel.length()==0?"Нет исторических данных":emptyLabel,dp(12),h/2,TEXT,dp(12));return;}
            int n=values.size();float left=dp(8),right=w-dp(8),top=dp(34),priceBottom=h-dp(34),volumeTop=h-dp(29),bottom=h-dp(5);
            double min=Double.MAX_VALUE,max=-Double.MAX_VALUE,maxVol=0;
            for(MarketData.Candle c:values){min=Math.min(min,c.low);max=Math.max(max,c.high);maxVol=Math.max(maxVol,c.volume);}
            if(Double.isFinite(entry)){min=Math.min(min,entry);max=Math.max(max,entry);}
            if(Double.isFinite(tp)){min=Math.min(min,tp);max=Math.max(max,tp);}
            if(Double.isFinite(sl)){min=Math.min(min,sl);max=Math.max(max,sl);}
            double range=max-min;if(range<=0)range=Math.max(max*.002,0.000001);double extra=range*.08;min-=extra;max+=extra;
            float plotH=priceBottom-top,plotW=right-left;
            for(int i=0;i<4;i++){float y=top+plotH*i/3f;line(canvas,left,y,right,y,Color.rgb(43,48,56),dp(.6f));}
            float step=plotW/n,bodyW=Math.max(dp(1.5f),Math.min(dp(7),step*.58f));
            for(int i=0;i<n;i++){
                MarketData.Candle c=values.get(i);float x=left+(i+.5f)*step;
                float yO=map(c.open,min,max,top,priceBottom),yC=map(c.close,min,max,top,priceBottom),yH=map(c.high,min,max,top,priceBottom),yL=map(c.low,min,max,top,priceBottom);
                int color=c.close>=c.open?Color.rgb(78,174,119):Color.rgb(207,86,91);
                p.setColor(color);p.setStrokeWidth(dp(1));canvas.drawLine(x,yH,x,yL,p);
                float y=Math.min(yO,yC),bh=Math.max(dp(1.5f),Math.abs(yC-yO));canvas.drawRect(x-bodyW/2,y,x+bodyW/2,y+bh,p);
                if(maxVol>0){float vh=(float)(c.volume/maxVol)*dp(20);p.setColor(Color.argb(100,color==Color.rgb(78,174,119)?78:207, color==Color.rgb(78,174,119)?174:86, color==Color.rgb(78,174,119)?119:91));canvas.drawRect(x-bodyW/2, bottom-vh,x+bodyW/2,bottom,p);}
            }
            drawAverage(canvas,20,Color.rgb(115,181,139),left,step,min,max,top,priceBottom);
            drawAverage(canvas,50,Color.rgb(139,147,158),left,step,min,max,top,priceBottom);
            drawLevel(canvas,left,right,min,max,top,priceBottom,entry,Color.rgb(220,224,230),"IN");
            double support=Double.MAX_VALUE,resistance=-Double.MAX_VALUE;
            for(int i=Math.max(0,n-50);i<n;i++){support=Math.min(support,values.get(i).low);resistance=Math.max(resistance,values.get(i).high);}
            drawLevel(canvas,left,right,min,max,top,priceBottom,support,Color.rgb(110,145,124),"S");
            drawLevel(canvas,left,right,min,max,top,priceBottom,resistance,Color.rgb(151,135,112),"R");
            drawLevel(canvas,left,right,min,max,top,priceBottom,tp,GREEN,tpTag);
            drawLevel(canvas,left,right,min,max,top,priceBottom,sl,RED,slTag);
            MarketData.Candle last=values.get(n-1);double rv=rsi(14);
            String head="O "+priceFmt.format(last.open)+"   H "+priceFmt.format(last.high)+"   L "+priceFmt.format(last.low)+"   C "+priceFmt.format(last.close);
            label(canvas,head,left,dp(12),TEXT,dp(8));
            String stats="EMA20 "+priceFmt.format(ema(n-1,20))+"   EMA50 "+priceFmt.format(ema(n-1,50))+"   RSI14 "+(Double.isNaN(rv)?"—":priceFmt.format(rv));
            label(canvas,stats,left,dp(25),MUTED,dp(8));
            label(canvas,"VOL",right-dp(25),bottom-dp(1),MUTED,dp(7));
        }
        private void drawAverage(Canvas canvas,int period,int color,float left,float step,double min,double max,float top,float bottom){
            if(values.size()<2)return;Path path=new Path();for(int i=0;i<values.size();i++){float x=left+(i+.5f)*step;float y=map(ema(i,period),min,max,top,bottom);if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}
            p.setColor(color);p.setStrokeWidth(dp(1.2f));p.setStyle(Paint.Style.STROKE);p.setPathEffect(null);canvas.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        }
        private float map(double v,double min,double max,float top,float bottom){return bottom-(float)((v-min)/(max-min))*(bottom-top);}
        private void drawLevel(Canvas canvas,float left,float right,double min,double max,float top,float bottom,double value,int color,String tag){
            if(!Double.isFinite(value)||value<min||value>max)return;float y=map(value,min,max,top,bottom);
            p.setColor(color);p.setStrokeWidth(dp(1));p.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(4),dp(4)},0));canvas.drawLine(left,y,right,y,p);p.setPathEffect(null);
            p.setStyle(Paint.Style.FILL);p.setColor(color);canvas.drawRoundRect(new RectF(right-dp(26),y-dp(8),right,y+dp(8)),dp(4),dp(4),p);
            p.setColor(BG);p.setTextSize(dp(7));p.setTypeface(Typeface.DEFAULT_BOLD);canvas.drawText(tag,right-dp(22),y+dp(2.5f),p);
        }
    }}
