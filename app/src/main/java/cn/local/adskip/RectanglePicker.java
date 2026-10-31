package cn.local.adskip;

import android.content.Intent;
import android.graphics.*;
import android.view.*;
import android.widget.*;

/** Accessibility overlay: navigation passes through until the user arms selection. */
final class RectanglePicker {
    private final SkipService service;
    private final WindowManager windows;
    private final String pkg,name;
    private final int index;
    private View panel,selection;
    private final Runnable onClose;
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable expire=this::close;
    RectanglePicker(SkipService service,String pkg,String name,int index,Runnable onClose){
        this.service=service;this.index=index;this.pkg=pkg;this.name=name;this.onClose=onClose;windows=service.getSystemService(WindowManager.class);
    }
    private int dp(int n){return Math.round(n*service.getResources().getDisplayMetrics().density);}
    private WindowManager.LayoutParams params(int width,int height){
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(width,height,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.LEFT;
        if(android.os.Build.VERSION.SDK_INT>=30)p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        else if(android.os.Build.VERSION.SDK_INT>=28)p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        if(android.os.Build.VERSION.SDK_INT>=30)p.setFitInsetsTypes(0);
        return p;
    }
    void show(){
        LinearLayout bar=new LinearLayout(service);bar.setOrientation(LinearLayout.VERTICAL);bar.setPadding(dp(12),dp(8),dp(12),dp(8));bar.setElevation(dp(12));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(0xF21B4537);bg.setCornerRadius(dp(14));bar.setBackground(bg);
        TextView title=new TextView(service);title.setText(name+" · 拖动此处移动");title.setTextColor(Color.WHITE);title.setTextSize(14);bar.addView(title);
        TextView hint=new TextView(service);hint.setText("打开广告后点「框选」；自动跳过已暂时暂停");hint.setTextColor(Color.WHITE);hint.setTextSize(12);bar.addView(hint);
        LinearLayout buttons=new LinearLayout(service);bar.addView(buttons);
        Button select=new Button(service);select.setText("框选矩形");buttons.addView(select);select.setOnClickListener(v->arm());
        Button cancel=new Button(service);cancel.setText("退出");buttons.addView(cancel);cancel.setOnClickListener(v->close());
        WindowManager.LayoutParams p=params(Math.min(dp(330),service.rectangleScreen().width()-dp(24)),-2);p.x=dp(12);p.y=dp(280);
        title.setOnTouchListener(new View.OnTouchListener(){float x,y;int px,py;
            public boolean onTouch(View v,MotionEvent e){
                if(e.getActionMasked()==MotionEvent.ACTION_DOWN){x=e.getRawX();y=e.getRawY();px=p.x;py=p.y;return true;}
                if(e.getActionMasked()==MotionEvent.ACTION_MOVE){Rect screen=service.rectangleScreen();p.x=Math.max(0,Math.min(screen.width()-bar.getWidth(),px+(int)(e.getRawX()-x)));p.y=Math.max(0,Math.min(screen.height()-bar.getHeight(),py+(int)(e.getRawY()-y)));windows.updateViewLayout(bar,p);return true;}
                return true;
            }
        });panel=bar;windows.addView(panel,p);handler.postDelayed(expire,120000);
    }
    private void arm(){
        Rect screen=service.rectangleScreen();int width=screen.width(),height=screen.height();
        selection=new View(service){
            float x1,y1,x2,y2;boolean dragging;
            final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas){
                int[] origin=new int[2];getLocationOnScreen(origin);
                canvas.drawColor(0x18000000);paint.setTextSize(dp(16));paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.FILL);
                canvas.drawText("拖动框住关闭按钮，松手记录矩形",dp(16),dp(180),paint);
                if(dragging){int[] b=RectRule.box((int)x1,(int)y1,(int)x2,(int)y2,width,height);
                    paint.setColor(0x3300BD7E);canvas.drawRect(b[0]-origin[0],b[1]-origin[1],b[2]-origin[0],b[3]-origin[1],paint);
                    paint.setColor(0xFF00EF9B);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));canvas.drawRect(b[0]-origin[0],b[1]-origin[1],b[2]-origin[0],b[3]-origin[1],paint);
                    paint.setStyle(Paint.Style.FILL);canvas.drawText("("+b[0]+", "+b[1]+") — ("+b[2]+", "+b[3]+")",dp(16),dp(215),paint);
                }
            }
            @Override public boolean onTouchEvent(MotionEvent e){
                if(e.getActionMasked()==MotionEvent.ACTION_DOWN){x1=x2=e.getRawX();y1=y2=e.getRawY();dragging=true;invalidate();return true;}
                if(e.getActionMasked()==MotionEvent.ACTION_MOVE){x2=e.getRawX();y2=e.getRawY();invalidate();return true;}
                if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){dragging=false;invalidate();return true;}
                if(e.getActionMasked()==MotionEvent.ACTION_UP){
                    x2=e.getRawX();y2=e.getRawY();int[] b=RectRule.box((int)x1,(int)y1,(int)x2,(int)y2,width,height);
                    if(b[2]-b[0]<12||b[3]-b[1]<12){Toast.makeText(service,"请拖出矩形范围，不是点一下",Toast.LENGTH_SHORT).show();return true;}
                    Rect now=service.rectangleScreen();if(now.width()!=width||now.height()!=height){close();Toast.makeText(service,"屏幕方向已变化，请重新框选",Toast.LENGTH_LONG).show();return true;}
                    Intent edit=new Intent(service,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra("rectangleIndex",index).putExtra("rectanglePackage",pkg).putExtra("rectangleName",name).putExtra("rectangleBounds",b).putExtra("rectangleWidth",width).putExtra("rectangleHeight",height);
                    close();service.startActivity(edit);return true;
                }return true;
            }
        };
        android.widget.FrameLayout full=new android.widget.FrameLayout(service);full.addView(selection,new android.widget.FrameLayout.LayoutParams(-1,-1));
        Button cancel=new Button(service);cancel.setText("取消框选");android.widget.FrameLayout.LayoutParams cp=new android.widget.FrameLayout.LayoutParams(dp(150),dp(52),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);cp.bottomMargin=dp(48);full.addView(cancel,cp);cancel.setOnClickListener(v->close());
        selection=full;windows.addView(selection,params(-1,-1));
        if(panel!=null){windows.removeView(panel);panel=null;}
    }
    void close(){
        handler.removeCallbacks(expire);
        if(selection!=null){try{windows.removeView(selection);}catch(Exception ignored){}selection=null;}
        if(panel!=null){try{windows.removeView(panel);}catch(Exception ignored){}panel=null;}
        onClose.run();
    }
}
