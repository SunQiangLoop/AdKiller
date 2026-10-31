package cn.local.adskip;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Point;
import android.widget.*;
import java.util.*;

/** Native dialogs reuse the app's existing rule-management surface. */
final class RectangleRulesUi {
    static void manage(Activity activity,Store store){
        List<Store.AppRule> entries=new ArrayList<>();List<String> labels=new ArrayList<>();
        labels.add("＋ 新增矩形规则 · 选择应用");
        for(Store.AppRule app:store.rules())if(!app.rectangles.isEmpty()){entries.add(app);labels.add(app.name+" · "+app.rectangles.size()+" 条（启用 "+app.activeRectangles().size()+" 条）");}
        new AlertDialog.Builder(activity).setTitle("矩形坐标规则").setItems(labels.toArray(new String[0]),(d,i)->{
            if(i==0)chooseApp(activity,store);else{Store.AppRule app=entries.get(i-1);list(activity,store,app.pkg,app.name);}
        }).setNegativeButton("返回",null).show();
    }
    private static void chooseApp(Activity a,Store store){
        Toast.makeText(a,"正在读取应用列表",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            List<String[]> entries=new ArrayList<>();Set<String> seen=new HashSet<>();
            Intent launch=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            for(android.content.pm.ResolveInfo item:a.getPackageManager().queryIntentActivities(launch,0)){
                String pkg=item.activityInfo.packageName;
                SkipService current=SkipService.instance;
                if(!pkg.equals(a.getPackageName())&&(current==null||!current.isExcluded(pkg))&&seen.add(pkg)){
                    String label=item.loadLabel(a.getPackageManager()).toString();entries.add(new String[]{pkg,label.substring(0,Math.min(40,label.length()))});
                }
            }
            entries.sort((x,y)->java.text.Collator.getInstance(Locale.CHINA).compare(x[1],y[1]));
            a.runOnUiThread(()->{
                if(a.isFinishing()||a.isDestroyed())return;
                LinearLayout box=column(a);EditText search=new EditText(a);search.setHint("搜索应用名称或包名");box.addView(search);
                ListView list=new ListView(a);box.addView(list,new LinearLayout.LayoutParams(-1,Math.round(380*a.getResources().getDisplayMetrics().density)));
                List<String[]> visible=new ArrayList<>(entries);ArrayAdapter<String> adapter=new ArrayAdapter<>(a,android.R.layout.simple_list_item_1);
                for(String[] row:visible)adapter.add(row[1]+"\n"+row[0]);list.setAdapter(adapter);
                AlertDialog dialog=new AlertDialog.Builder(a).setTitle("选择目标应用").setView(box).setNegativeButton("取消",null).create();
                search.addTextChangedListener(new android.text.TextWatcher(){
                    public void beforeTextChanged(CharSequence s,int start,int count,int after){}
                    public void onTextChanged(CharSequence s,int start,int before,int count){
                        String query=s.toString().toLowerCase(Locale.ROOT);visible.clear();adapter.clear();
                        for(String[] row:entries)if((row[0]+row[1]).toLowerCase(Locale.ROOT).contains(query)){visible.add(row);adapter.add(row[1]+"\n"+row[0]);}
                    }
                    public void afterTextChanged(android.text.Editable s){}
                });
                list.setOnItemClickListener((parent,v,index,id)->{String[] row=visible.get(index);dialog.dismiss();
                    list(a,store,row[0],row[1]);
                });dialog.show();
            });
        },"rectangle-app-list").start();
    }
    private static LinearLayout column(Activity a){LinearLayout view=new LinearLayout(a);view.setOrientation(LinearLayout.VERTICAL);int pad=Math.round(20*a.getResources().getDisplayMetrics().density);view.setPadding(pad,0,pad,0);return view;}
    private static EditText field(Activity a,LinearLayout root,String title,int value){
        TextView label=new TextView(a);label.setText(title);root.addView(label);
        EditText input=new EditText(a);input.setSingleLine(true);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);input.setText(String.valueOf(value));root.addView(input);return input;
    }
    static void list(Activity a,Store store,String pkg,String name){
        List<RectRule> rules=new ArrayList<>();for(Store.AppRule app:store.rules())if(app.pkg.equals(pkg))rules.addAll(app.rectangles);
        List<String> labels=new ArrayList<>();labels.add("＋ 新增框选区域（最多 10 条）");
        for(int i=0;i<rules.size();i++){RectRule r=rules.get(i);labels.add("区域 "+(i+1)+(r.enabled?" · 已启用":" · 已暂停")+"\n("+r.left+", "+r.top+") — ("+r.right+", "+r.bottom+")");}
        new AlertDialog.Builder(a).setTitle(name+" · 框选区域").setItems(labels.toArray(new String[0]),(d,i)->{
            if(i==0){if(rules.size()>=10)Toast.makeText(a,"每个应用最多 10 条矩形规则",Toast.LENGTH_LONG).show();else pick(a,pkg,name,-1);}
            else edit(a,store,pkg,name,rules.get(i-1),i-1);
        }).setNegativeButton("返回",(d,i)->manage(a,store)).show();
    }
    static void pick(Activity a,String pkg,String name,int index){
        SkipService service=SkipService.instance;
        if(service==null||!SkipService.connected){new AlertDialog.Builder(a).setMessage("请先开启无障碍服务，再开始框选。").setPositiveButton("知道了",null).show();return;}
        try{service.beginRectanglePicker(pkg,name,index);}catch(Exception e){new AlertDialog.Builder(a).setMessage("无法打开框选："+e.getMessage()).setPositiveButton("关闭",null).show();}
    }
    static void edit(Activity a,Store store,String pkg,String name,RectRule rule,int index){
        LinearLayout box=column(a);TextView info=new TextView(a);
        info.setText(name+"\n"+pkg+"\n框选只用于 OCR 识别，不直接点击坐标。与普通 OCR、控件和模板同级并行，任一路识别到有效按钮就点击。\n");box.addView(info);
        EditText l=field(a,box,"左边界 X",rule.left),t=field(a,box,"上边界 Y",rule.top),r=field(a,box,"右边界 X",rule.right),b=field(a,box,"下边界 Y",rule.bottom);
        EditText w=field(a,box,"参考屏幕宽度（像素）",rule.width),h=field(a,box,"参考屏幕高度（像素）",rule.height);
        CheckBox relative=new CheckBox(a);relative.setText("按屏幕比例换算坐标");relative.setChecked(rule.relative);box.addView(relative);
        CheckBox enabled=new CheckBox(a);enabled.setText("启用此矩形规则");enabled.setChecked(rule.enabled);box.addView(enabled);
        TextView note=new TextView(a);note.setText("相同方向按比例换算；布局变化时可重新框选。未识别到跳过、关闭或叉号时不点击；其他识别方式始终并行运行。");box.addView(note);
        Button remove=new Button(a);remove.setText("删除当前区域");box.addView(remove);
        ScrollView scroll=new ScrollView(a);scroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("矩形规则 · "+name).setView(scroll).setPositiveButton("保存",null)
            .setNeutralButton("重新框选",(d,i)->pick(a,pkg,name,index)).setNegativeButton("取消",null).create();
        remove.setVisibility(index<0?android.view.View.GONE:android.view.View.VISIBLE);
        remove.setOnClickListener(v->new AlertDialog.Builder(a).setTitle("删除矩形规则？").setMessage("原有文字、热点和图像识别规则保留。")
            .setPositiveButton("删除",(d,i)->{try{store.saveRectangleAt(pkg,name,index,null);dialog.dismiss();list(a,store,pkg,name);}catch(Exception e){Toast.makeText(a,e.getMessage(),Toast.LENGTH_LONG).show();}}).setNegativeButton("取消",null).show());
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{RectRule saved=new RectRule(Integer.parseInt(l.getText().toString()),Integer.parseInt(t.getText().toString()),Integer.parseInt(r.getText().toString()),Integer.parseInt(b.getText().toString()),
                Integer.parseInt(w.getText().toString()),Integer.parseInt(h.getText().toString()),rule.delayMs,enabled.isChecked(),relative.isChecked());
                store.saveRectangleAt(pkg,name,index,saved);dialog.dismiss();Toast.makeText(a,"矩形规则已保存，下次进入应用生效",Toast.LENGTH_LONG).show();
            }catch(Exception error){Toast.makeText(a,error.getMessage(),Toast.LENGTH_LONG).show();}
        }));dialog.show();
    }
    static void picked(Activity a,Store store,Intent intent){
        if(!intent.hasExtra("rectanglePackage"))return;
        String pkg=intent.getStringExtra("rectanglePackage"),name=intent.getStringExtra("rectangleName");
        int index=intent.getIntExtra("rectangleIndex",-1);
        int[] b=intent.getIntArrayExtra("rectangleBounds");int w=intent.getIntExtra("rectangleWidth",0),h=intent.getIntExtra("rectangleHeight",0);
        intent.removeExtra("rectanglePackage");
        try{int delay=300;boolean enabled=true,relative=true;for(Store.AppRule app:store.rules())if(app.pkg.equals(pkg)&&index>=0&&index<app.rectangles.size()){RectRule r=app.rectangles.get(index);delay=r.delayMs;enabled=r.enabled;relative=r.relative;}
            edit(a,store,pkg,name,new RectRule(b[0],b[1],b[2],b[3],w,h,delay,enabled,relative),index);
        }catch(Exception e){Toast.makeText(a,"框选无效，请重试",Toast.LENGTH_LONG).show();}
    }
}
