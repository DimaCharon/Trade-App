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

import java.text.DecimalFormat;
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
    private double balance = 10000.0;
    private int xp = 0, trades = 0, wins = 0;
    private double netPnl = 0;
    private boolean loading = false;
    private TextView practiceStatus;
    private CandlestickChartView practiceChart;
    private CandlestickChartView resultChart;
    private EditText marginInput, tpInput, slInput;
    private Spinner leverageSpinner;
    private TextView riskPreview;
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
        showHome();
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
        v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
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
        LinearLayout c = column(); c.setBackground(shape(PANEL, 22, BORDER, 1)); pad(c, 16, 16, 16, 16); return c;
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
        TextView b = button(label, primary); parent.addView(b, lp(-1, 52));
        b.setOnClickListener(v -> { v.animate().scaleX(.985f).scaleY(.985f).setDuration(70).withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()).start(); listener.onClick(v); });
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
        TextView status = text("●", 13, GREEN, true); status.setGravity(Gravity.CENTER); header.addView(status, lp(26, 38));
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
        String[] labels = {"Главная", "Практика", "Прогресс"};
        int[] pages = {0, 1, 4};
        for (int i=0; i<labels.length; i++) {
            final int target = pages[i];
            LinearLayout item = column(); item.setGravity(Gravity.CENTER);
            TextView icon = text(i==0?"⌂":(i==1?"⌁":"▥"), 20, selected==i?GREEN:MUTED, true); icon.setGravity(Gravity.CENTER);
            TextView label = text(labels[i], 10, selected==i?GREEN:MUTED, selected==i); label.setGravity(Gravity.CENTER);
            item.addView(icon); gap(item, 3); item.addView(label);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(46), 1); nav.addView(item, p);
            item.setOnClickListener(v -> { if(target==0) showHome(); else if(target==1) showPractice(); else showStats(); });
        }
        root.addView(nav, lp(-1, 62));
    }

    private void showHome() {
        page = 0; beginPage("TRADEQUEST", false);
        ScrollView s = scroll(root); LinearLayout c = content(s);
        TextView eyebrow = text("ТВОЯ ТОРГОВАЯ ТРЕНИРОВКА", 10, GREEN, true); c.addView(eyebrow);
        gap(c, 10);
        TextView heading = text("Учись на рынке.\nНе рискуя деньгами.", 29, TEXT, true); heading.setLineSpacing(dp(3), 1f); c.addView(heading);
        gap(c, 20);
        LinearLayout wallet = card(); wallet.setBackground(shape(Color.rgb(21, 27, 31), 23, Color.rgb(55, 72, 49), 1));
        LinearLayout statsRow = row();
        LinearLayout balCol = column();
        balCol.addView(text("ВИРТУАЛЬНЫЙ БАЛАНС", 10, MUTED, true)); gap(balCol, 7);
        balCol.addView(text(moneyFmt.format(balance) + " USDT", 25, TEXT, true));
        statsRow.addView(balCol, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout xpCol = column(); xpCol.setGravity(Gravity.RIGHT);
        xpCol.addView(text("ОПЫТ", 10, MUTED, true)); gap(xpCol, 7); xpCol.addView(text(xp + " XP", 18, GREEN, true));
        statsRow.addView(xpCol); wallet.addView(statsRow); gap(wallet, 18);
        View line = new View(this); line.setBackgroundColor(BORDER); wallet.addView(line, lp(-1, 1)); gap(wallet, 12);
        LinearLayout bottom = row(); bottom.addView(text("◷  Серия тренировок", 12, MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
        bottom.addView(text(trades + " сделок", 12, TEXT, true)); wallet.addView(bottom); c.addView(wallet);
        gap(c, 22);
        LinearLayout lessonCard = card();
        LinearLayout tag = row(); TextView badge = text("●  РЕАЛЬНАЯ ИСТОРИЯ", 10, GREEN, true); tag.addView(badge); lessonCard.addView(tag);
        gap(lessonCard, 11); lessonCard.addView(text("Сегодняшний челлендж", 19, TEXT, true));
        gap(lessonCard, 5); lessonCard.addView(text("Найди точку входа на настоящем графике BTC, выставь риск и проверь решение на следующих свечах.", 13, MUTED, false));
        gap(lessonCard, 16); addButton(lessonCard, "Начать тренировку  →", true, v -> showPractice()); c.addView(lessonCard);
        gap(c, 22);
        LinearLayout section = row(); section.addView(text("ТВОЙ ПУТЬ", 11, MUTED, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView all = text("Все уроки  ↗", 12, GREEN, true); section.addView(all); all.setOnClickListener(v -> showLessons()); c.addView(section);
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
        pairCard.addView(text("ВЫБЕРИ РЫНОК", 10, MUTED, true)); gap(pairCard, 8);
        LinearLayout selectors = row();
        Spinner pairSpinner = spinner(new String[]{"BTCUSDT", "ETHUSDT", "SOLUSDT", "XRPUSDT", "DOGEUSDT"});
        Spinner tfSpinner = spinner(new String[]{"1 мин", "5 мин", "15 мин", "1 час", "4 часа", "1 день"});
        String[] pairs = {"BTCUSDT", "ETHUSDT", "SOLUSDT", "XRPUSDT", "DOGEUSDT"};
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
        c.addView(pairCard); gap(c, 15);
        LinearLayout chartCard = card();
        LinearLayout chartTop = row(); chartTop.addView(text("ГРАФИК", 10, MUTED, true),new LinearLayout.LayoutParams(0,-2,1));
        chartTop.addView(text("BYBIT · LINEAR", 10, GREEN, true)); chartCard.addView(chartTop); gap(chartCard, 12);
        practiceChart = new CandlestickChartView(this); practiceChart.setEmptyLabel("Загружаем историю рынка"); chartCard.addView(practiceChart,lp(-1,230)); gap(chartCard, 8);
        chartCard.addView(text("Будущие свечи скрыты. Решение принимается только по данным, доступным на тот момент.", 11, MUTED, false));
        c.addView(chartCard); gap(c, 15);
        LinearLayout info = card(); info.addView(text("КАК ЭТО РАБОТАЕТ", 10, GREEN, true)); gap(info, 8);
        info.addView(text("Выбранная свеча уже закрыта. Вход в симуляции будет по открытию следующей свечи — так приложение не использует будущее для расчёта цены входа.", 12, MUTED, false)); c.addView(info);
        gap(c, 14);
        addButton(c, "Настроить сделку  →", true, v -> { if (pivot < 0 || candles.isEmpty()) toast("Сначала загрузи исторический момент"); else showSetup(); });
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
        final String requestedSymbol = symbol;
        final String requestedInterval = interval;
        if (practiceStatus != null) practiceStatus.setText("Подключаемся к Bybit и загружаем реальные свечи…");
        io.execute(() -> {
            try {
                List<MarketData.Candle> loaded = MarketData.load(requestedSymbol, requestedInterval);
                Random random = new Random();
                int min = Math.max(70, loaded.size()-250);
                int max = loaded.size()-45;
                int selected = min + random.nextInt(Math.max(1,max-min+1));
                main.post(() -> {
                    loading=false;
                    if (!requestedSymbol.equals(symbol) || !requestedInterval.equals(interval)) return;
                    candles=loaded; pivot=selected;
                    if (practiceStatus!=null) practiceStatus.setText("Загружено " + loaded.size() + " свечей · " + formatDate(loaded.get(pivot).time) + " · Bybit API");
                    updatePracticeChart();
                });
            } catch (Exception e) {
                main.post(() -> {
                    loading=false;
                    if (!requestedSymbol.equals(symbol) || !requestedInterval.equals(interval)) return;
                    if (practiceStatus!=null) practiceStatus.setText("Не удалось загрузить историю: " + safeMessage(e) + "\nПроверь интернет или попробуй позже.");
                    if (practiceChart!=null) practiceChart.setEmptyLabel("Нет данных. Нажми загрузить ещё раз.");
                });
            }
        });
    }

    private void updatePracticeChart() {
        if(practiceChart==null || pivot<0 || candles.isEmpty()) return;
        int start=Math.max(0,pivot-55);
        practiceChart.setCandles(candles.subList(start,pivot+1));
        practiceChart.setMarkers(Double.NaN,Double.NaN,Double.NaN);
        practiceChart.setEmptyLabel("");
        practiceChart.invalidate();
    }

    private void showSetup() {
        page=2; beginPage("Настройка сделки", true);
        ScrollView s=scroll(root); LinearLayout c=content(s);
        LinearLayout symbolCard=card();
        symbolCard.addView(text(symbol+"  ·  "+intervalLabel(interval)+"  ·  ИСТОРИЯ",13,TEXT,true)); gap(symbolCard,5);
        symbolCard.addView(text("Момент: "+formatDate(candles.get(pivot).time),11,MUTED,false)); gap(symbolCard,12);
        CandlestickChartView mini=new CandlestickChartView(this); mini.setCandles(candles.subList(Math.max(0,pivot-42),pivot+1));
        symbolCard.addView(mini,lp(-1,185)); c.addView(symbolCard); gap(c,14);
        LinearLayout direction=card(); direction.addView(text("НАПРАВЛЕНИЕ ПОЗИЦИИ",10,MUTED,true)); gap(direction,10);
        LinearLayout sides=row();
        TextView longBtn=button("↗  LONG",isLong), shortBtn=button("↘  SHORT",!isLong);
        sides.addView(longBtn,new LinearLayout.LayoutParams(0,dp(48),1)); View sep=new View(this); sides.addView(sep,lp(8,1)); sides.addView(shortBtn,new LinearLayout.LayoutParams(0,dp(48),1)); direction.addView(sides);
        longBtn.setOnClickListener(v->{isLong=true;longBtn.setBackground(ripple(GREEN,15));longBtn.setTextColor(Color.rgb(14,20,11));shortBtn.setBackground(ripple(PANEL2,15));shortBtn.setTextColor(TEXT);updateRiskPreview();});
        shortBtn.setOnClickListener(v->{isLong=false;shortBtn.setBackground(ripple(GREEN,15));shortBtn.setTextColor(Color.rgb(14,20,11));longBtn.setBackground(ripple(PANEL2,15));longBtn.setTextColor(TEXT);updateRiskPreview();});
        c.addView(direction); gap(c,14);
        LinearLayout params=card(); params.addView(text("ПАРАМЕТРЫ СДЕЛКИ",10,MUTED,true)); gap(params,12);
        params.addView(text("Сумма маржи · USDT",12,TEXT,true)); gap(params,6); marginInput=input("100"); params.addView(marginInput,lp(-1,48)); gap(params,12);
        params.addView(text("Плечо",12,TEXT,true)); gap(params,6);
        leverageSpinner=spinner(new String[]{"1×","2×","3×","5×","10×","20×","50×"}); leverageSpinner.setSelection(3); params.addView(leverageSpinner,lp(-1,48)); gap(params,12);
        LinearLayout two=row();
        LinearLayout tpCol=column(),slCol=column();
        tpCol.addView(text("Take Profit · %",12,TEXT,true)); gap(tpCol,6); tpInput=input("2.0"); tpCol.addView(tpInput,lp(-1,48));
        slCol.addView(text("Stop Loss · %",12,TEXT,true)); gap(slCol,6); slInput=input("1.0"); slCol.addView(slInput,lp(-1,48));
        two.addView(tpCol,new LinearLayout.LayoutParams(0,-2,1)); View sp=new View(this);two.addView(sp,lp(10,1));two.addView(slCol,new LinearLayout.LayoutParams(0,-2,1)); params.addView(two);
        c.addView(params); gap(c,14);
        LinearLayout preview=card(); preview.addView(text("ПРЕДВАРИТЕЛЬНЫЙ РАСЧЁТ",10,GREEN,true)); gap(preview,8);
        riskPreview=text("Загружаем расчёт…",13,TEXT,false); preview.addView(riskPreview); gap(preview,7);
        preview.addView(text("Результат считается по следующим реальным OHLC-свечам. Комиссии, funding и точная ликвидация пока не моделируются — это учебный P&L, не расчёт для реальной сделки.",10,MUTED,false)); c.addView(preview); gap(c,16);
        TextView confirm=button("Подтвердить и пройти историю  →",true); c.addView(confirm,lp(-1,54));
        confirm.setOnClickListener(v->{
            try {
                double margin=parse(marginInput.getText().toString());
                double tp=parse(tpInput.getText().toString()); double sl=parse(slInput.getText().toString());
                if(margin>balance) {toast("Маржа больше доступного баланса");return;}
                int lev=leverageValue();
                tradeResult=TradeSimulator.simulate(candles,pivot,isLong,margin,lev,tp,sl);
                balance+=tradeResult.pnl;
                trades++; if(tradeResult.pnl>0) wins++;
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
        result.addView(text(tradeResult.reason,11,win?GREEN:RED,true)); gap(result,7);
        result.addView(text((tradeResult.pnl>=0?"+":"")+moneyFmt.format(tradeResult.pnl)+" USDT",32,TEXT,true)); gap(result,5);
        result.addView(text(""+(tradeResult.roi>=0?"+":"")+priceFmt.format(tradeResult.roi)+"% к марже  ·  "+(isLong?"LONG":"SHORT"),12,MUTED,false)); c.addView(result); gap(c,14);
        LinearLayout chart=card(); LinearLayout top=row();top.addView(text(symbol+"  ·  "+intervalLabel(interval),13,TEXT,true),new LinearLayout.LayoutParams(0,-2,1));top.addView(text("ИСТОРИЯ РАСКРЫТА",9,GREEN,true));chart.addView(top);gap(chart,12);
        resultChart=new CandlestickChartView(this);
        resultChart.setCandles(candles.subList(Math.max(0,pivot-48),Math.min(candles.size(),tradeResult.exitIndex+1)));
        double entry=tradeResult.entry;double tpp=isLong?entry*(1+tp/100):entry*(1-tp/100);double slp=isLong?entry*(1-sl/100):entry*(1+sl/100);
        resultChart.setMarkers(entry,tpp,slp);chart.addView(resultChart,lp(-1,250)); c.addView(chart); gap(c,14);
        LinearLayout stats=card(); stats.addView(text("ДЕТАЛИ СДЕЛКИ",10,MUTED,true));gap(stats,12);
        addDetail(stats,"Цена входа",priceFmt.format(tradeResult.entry));
        addDetail(stats,"Цена выхода",priceFmt.format(tradeResult.exit));
        addDetail(stats,"Маржа / плечо",priceFmt.format(margin)+" USDT / "+leverage+"×");
        addDetail(stats,"Время в позиции",tradeResult.barsHeld+" свечей · "+intervalLabel(interval));
        addDetail(stats,"Реализованный P&L",moneyFmt.format(tradeResult.pnl)+" USDT");
        addDetail(stats,"Баланс после сделки",moneyFmt.format(balance)+" USDT");
        if(tradeResult.ambiguous){gap(stats,8);stats.addView(text("Обе границы попали внутрь одной OHLC-свечи. Для консервативного результата засчитан Stop Loss первым.",11,RED,false));}
        c.addView(stats);gap(c,14);
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
        LinearLayout hero=card();hero.addView(text("ТВОЯ СТАТИСТИКА",10,GREEN,true));gap(hero,8);hero.addView(text(xp+" XP",32,TEXT,true));gap(hero,4);hero.addView(text("Опыт за реальные исторические тренировки",12,MUTED,false));c.addView(hero);gap(c,14);
        LinearLayout grid=row();addMetric(grid,"СДЕЛОК",String.valueOf(trades));addMetric(grid,"WIN RATE",trades==0?"—":priceFmt.format((double)wins/trades*100)+"%");c.addView(grid);gap(c,10);
        LinearLayout grid2=row();addMetric(grid2,"ЧИСТЫЙ P&L",moneyFmt.format(netPnl)+" USDT");addMetric(grid2,"БАЛАНС",priceFmt.format(balance)+" USDT");c.addView(grid2);gap(c,16);
        LinearLayout note=card();note.addView(text("ЧЕСТНАЯ СТАТИСТИКА",10,GREEN,true));gap(note,7);note.addView(text("Все показатели собраны только из завершённых симуляций на исторических свечах. Положительный результат одной сделки не гарантирует будущую прибыль.",12,MUTED,false));c.addView(note);gap(c,18);
        addButton(c,"Вернуться к тренировке",true,v->showPractice());
        addBottomNav(2);
    }

    private void addMetric(LinearLayout parent,String title,String value) {
        LinearLayout m=card();m.addView(text(title,9,MUTED,true));gap(m,8);m.addView(text(value,20,TEXT,true));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(98),1);p.rightMargin=dp(7);parent.addView(m,p);
    }

    private void showLessons() {
        page=5;beginPage("Карта обучения",true);ScrollView s=scroll(root);LinearLayout c=content(s);
        c.addView(text("От первых свечей до уверенного риск-менеджмента — шаг за шагом.",14,MUTED,false));gap(c,18);
        String[][] lessons={{"01 · СВЕЧИ","Как устроена японская свеча","Открытие, закрытие, максимум и минимум."},{"02 · НАПРАВЛЕНИЕ","LONG против SHORT","Как прибыль и убыток зависят от направления."},{"03 · ПЛЕЧО","Маржа и размер позиции","Почему плечо увеличивает и прибыль, и риск."},{"04 · ЗАЩИТА","Stop Loss и Take Profit","План выхода до открытия позиции."},{"05 · ПРАКТИКА","Исторический рынок","Принимай решения по реальным свечам Bybit."}};
        for(int i=0;i<lessons.length;i++){
            LinearLayout l=card();l.addView(text(lessons[i][0],10,GREEN,true));gap(l,7);l.addView(text(lessons[i][1],17,TEXT,true));gap(l,5);l.addView(text(lessons[i][2],12,MUTED,false));gap(l,11);
            final int idx=i;addButton(l,i==4?"Перейти к практике →":"Изучить тему",i==4,v->{if(idx==4)showPractice();else toast("Короткий урок скоро появится в следующем обновлении.");});
            LinearLayout.LayoutParams p=lp(-1,-2);p.bottomMargin=dp(10);c.addView(l,p);
        }
        addBottomNav(0);
    }

    private void persist() {
        prefs.edit().putLong("balance",Double.doubleToLongBits(balance)).putInt("xp",xp).putInt("trades",trades).putInt("wins",wins).putLong("netPnl",Double.doubleToLongBits(netPnl)).apply();
    }

    private void toast(String value) { Toast.makeText(this,value,Toast.LENGTH_LONG).show(); }
    private String safeMessage(Exception e) { String m=e.getMessage(); return m==null?"ошибка сети":m; }
    private String intervalLabel(String i) { if("D".equals(i))return "1D"; if("240".equals(i))return "4H"; if("60".equals(i))return "1H"; return i+"M"; }
    private String formatDate(long time) { return new SimpleDateFormat("dd.MM.yyyy · HH:mm",Locale.getDefault()).format(new Date(time)); }

    @Override public void onBackPressed() {
        if(page==3||page==2) showPractice(); else if(page==1||page==4||page==5) showHome(); else super.onBackPressed();
    }

    @Override protected void onDestroy() { super.onDestroy(); io.shutdownNow(); }

    private class CandlestickChartView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private List<MarketData.Candle> values=new ArrayList<>();
        private double entry=Double.NaN,tp=Double.NaN,sl=Double.NaN;
        private String emptyLabel="";
        CandlestickChartView(Context context){super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void setCandles(List<MarketData.Candle> data){values=new ArrayList<>(data);setAlpha(.35f);animate().alpha(1f).setDuration(360).start();invalidate();}
        void setMarkers(double e,double t,double s){entry=e;tp=t;sl=s;invalidate();}
        void setEmptyLabel(String s){emptyLabel=s;invalidate();}
        private void line(Canvas c,float x1,float y1,float x2,float y2,int color,float width){p.setColor(color);p.setStrokeWidth(width);p.setStyle(Paint.Style.STROKE);c.drawLine(x1,y1,x2,y2,p);p.setStyle(Paint.Style.FILL);}
        private void label(Canvas c,String s,float x,float y,int color,float size){p.setColor(color);p.setTextSize(size);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));c.drawText(s,x,y,p);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float w=getWidth(),h=getHeight();canvas.drawColor(PANEL);
            if(values.isEmpty()){label(canvas,emptyLabel.length()==0?"Загрузка истории…":emptyLabel,dp(12),h/2,TEXT,dp(12));return;}
            float left=dp(8),right=w-dp(8),top=dp(12),bottom=h-dp(20);int n=values.size();
            double min=Double.MAX_VALUE,max=-Double.MAX_VALUE;
            for(MarketData.Candle c:values){min=Math.min(min,c.low);max=Math.max(max,c.high);}
            if(Double.isFinite(entry)){min=Math.min(min,Math.min(entry,Math.min(tp,sl)));max=Math.max(max,Math.max(entry,Math.max(tp,sl)));}
            double padRange=(max-min)*.10;if(padRange==0)padRange=max*.005;min-=padRange;max+=padRange;
            float plotH=bottom-top,plotW=right-left;
            for(int i=0;i<5;i++){float y=top+plotH*i/4f;line(canvas,left,y,right,y,Color.rgb(38,44,53),1);}
            for(int i=0;i<5;i++){float x=left+plotW*i/4f;line(canvas,x,top,x,bottom,Color.rgb(34,40,48),1);}
            float step=plotW/n,bodyW=Math.max(dp(2),Math.min(dp(8),step*.58f));
            for(int i=0;i<n;i++){
                MarketData.Candle c=values.get(i);float x=left+(i+.5f)*step;
                float yO=map(c.open,min,max,top,bottom),yC=map(c.close,min,max,top,bottom),yH=map(c.high,min,max,top,bottom),yL=map(c.low,min,max,top,bottom);
                int color=c.close>=c.open?Color.rgb(94,214,139):Color.rgb(246,99,107);
                p.setColor(color);p.setStrokeWidth(dp(1.2f));canvas.drawLine(x,yH,x,yL,p);
                float y=Math.min(yO,yC),bh=Math.max(dp(1.5f),Math.abs(yC-yO));canvas.drawRoundRect(new RectF(x-bodyW/2,y,x+bodyW/2,y+bh),dp(1),dp(1),p);
            }
            drawLevel(canvas,left,right,min,max,top,bottom,entry,Color.rgb(218,223,230),"ENTRY");
            drawLevel(canvas,left,right,min,max,top,bottom,tp,GREEN,"TP");
            drawLevel(canvas,left,right,min,max,top,bottom,sl,RED,"SL");
            label(canvas,priceFmt.format(max),left+dp(3),top+dp(10),MUTED,dp(8));
            label(canvas,priceFmt.format(min),left+dp(3),bottom-dp(3),MUTED,dp(8));
        }
        private float map(double v,double min,double max,float top,float bottom){return bottom-(float)((v-min)/(max-min))*(bottom-top);}
        private void drawLevel(Canvas canvas,float left,float right,double min,double max,float top,float bottom,double value,int color,String tag){
            if(!Double.isFinite(value)||value<min||value>max)return;float y=map(value,min,max,top,bottom);
            p.setColor(color);p.setStrokeWidth(dp(1));p.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(5),dp(4)},0));canvas.drawLine(left,y,right,y,p);p.setPathEffect(null);
            p.setStyle(Paint.Style.FILL);p.setColor(color);canvas.drawRoundRect(new RectF(right-dp(30),y-dp(9),right,y+dp(9)),dp(5),dp(5),p);
            p.setColor(Color.rgb(10,13,16));p.setTextSize(dp(8));p.setTypeface(Typeface.DEFAULT_BOLD);canvas.drawText(tag,right-dp(25),y+dp(3),p);
        }
    }
}
