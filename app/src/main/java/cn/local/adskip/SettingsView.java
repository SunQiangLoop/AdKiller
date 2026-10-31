package cn.local.adskip;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Secondary settings surface; the daily-use controls stay on the home screen. */
final class SettingsView extends ScrollView {
    private static final int INK=Color.rgb(21,35,27),MUTED=Color.rgb(96,108,101),GREEN=Color.rgb(0,109,77);
    private final LinearLayout root;
    private LinearLayout group;
    private final TextView observations;
    final HomeView.Toggle collecting,diagnostic;

    SettingsView(Context context,String version,Runnable back,Runnable history,Runnable heatmap,Runnable export,Runnable background,Runnable info){
        super(context);setFillViewport(true);setBackgroundColor(HomeView.CANVAS);setVerticalScrollBarEnabled(false);
        root=column();root.setPadding(dp(18),dp(8),dp(18),dp(28));addView(root,new ScrollView.LayoutParams(-1,-2));
        TextView returnButton=text("‹  返回首页",15,GREEN);returnButton.setGravity(Gravity.CENTER_VERTICAL);
        returnButton.setMinHeight(dp(48));returnButton.setPadding(dp(4),0,dp(12),0);returnButton.setContentDescription("返回首页");
        returnButton.setOnClickListener(v->back.run());root.addView(returnButton);
        TextView title=text("设置",30,INK);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        title.setPadding(dp(4),dp(8),0,dp(6));root.addView(title);
        TextView intro=text("管理运行记录、诊断与本机信息",13,MUTED);intro.setPadding(dp(4),0,0,dp(8));root.addView(intro);
        section("运行与诊断");
        observations=action("运行记录","查看最近的识别和点击记录",history);
        action("AI 跳过热力图","查看端侧学习到的各应用跳过热区与坐标",heatmap);
        collecting=toggle("采集开屏画面","每次最多 5 分钟，每个开屏最多 4 张缩略图");
        diagnostic=toggle("详细诊断日志","排查识别问题时开启");
        action("导出诊断日志","包含版本、设备环境和识别记录",export);
        section("后台运行");
        action("后台运行设置","查看自启动与省电设置指引",background);
        section("版本与设备");
        LinearLayout versionRow=item();copy(versionRow,"软件版本",version);
        TextView badge=text("Qiang",12,GREEN);badge.setGravity(Gravity.CENTER_VERTICAL);versionRow.addView(badge);
        action("手机与系统信息",android.os.Build.MODEL+" · Android "+android.os.Build.VERSION.RELEASE,info);
        TextView note=text("诊断与设备信息仅在本机展示或导出，不上传云端。",11,MUTED);
        note.setPadding(dp(8),dp(18),dp(8),0);root.addView(note);
    }
    void updateObservations(int count){observations.setText("点击后按钮消失 "+count+" 次 · 未确认跳过");}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(getContext());v.setOrientation(LinearLayout.VERTICAL);return v;}
    private TextView text(String value,int size,int color){TextView v=new TextView(getContext());v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setIncludeFontPadding(false);v.setLineSpacing(dp(2),1);return v;}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void section(String name){
        TextView title=text(name,12,MUTED);title.setPadding(dp(14),dp(22),0,dp(8));root.addView(title);
        group=column();group.setBackground(shape(Color.WHITE,18));group.setClipToOutline(true);root.addView(group);
    }
    private LinearLayout item(){
        if(group.getChildCount()>0){View line=new View(getContext());line.setBackgroundColor(Color.rgb(222,226,224));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.leftMargin=dp(16);group.addView(line,lp);}
        LinearLayout row=new LinearLayout(getContext());row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(66));row.setPadding(dp(16),dp(12),dp(16),dp(12));group.addView(row,new LinearLayout.LayoutParams(-1,-2));return row;
    }
    private TextView copy(LinearLayout row,String title,String subtitle){
        LinearLayout words=column();TextView heading=text(title,15,INK);heading.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));words.addView(heading);
        TextView detail=text(subtitle,11,MUTED);detail.setPadding(0,dp(5),0,0);words.addView(detail);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.rightMargin=dp(12);row.addView(words,lp);return detail;
    }
    private TextView action(String title,String subtitle,Runnable action){
        LinearLayout row=item();TextView detail=copy(row,title,subtitle);
        android.widget.ImageView arrow=new android.widget.ImageView(getContext());arrow.setImageResource(R.drawable.home_chevron);arrow.setColorFilter(MUTED);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(arrow,new LinearLayout.LayoutParams(dp(16),dp(16)));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x16246B57),null,shape(Color.WHITE,0)));row.setFocusable(true);row.setOnClickListener(v->action.run());return detail;
    }
    private HomeView.Toggle toggle(String title,String subtitle){
        LinearLayout row=item();copy(row,title,subtitle);HomeView.Toggle control=new HomeView.Toggle(getContext());control.setContentDescription(title+"，"+subtitle);
        row.addView(control,new LinearLayout.LayoutParams(dp(51),dp(48)));row.setOnClickListener(v->control.toggle());
        row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.getChildAt(0).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);return control;
    }
}
