from pathlib import Path
import re
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
p=root/'app/src/main/java/com/tomaszabramowicz/meshchat/MainActivity.java'
s=p.read_text(encoding='utf-8')
if '// TMC_OFFLINE_LOCATION_03' not in s:
 def edit(old,new):
  global s
  if s.count(old)!=1:raise SystemExit('Source does not match expected 0.2.0; no changes saved. Missing/ambiguous anchor: '+old)
  s=s.replace(old,new,1)
 edit('    private TextView status,counter;', '    private TextView status,counter;\n    // TMC_OFFLINE_LOCATION_03\n    private LocationFeature locationFeature;\n    String sendLocationPayload(String value){return session==null?"Connect to the radio first":session.send(value);}\n')
 edit('buildUi(); loadHistory();','buildUi(); locationFeature=new LocationFeature(this); loadHistory();')
 edit('        addOptions(page);','        LinearLayout locbar=new LinearLayout(this);\n        Button loc=button("Location"),mapButton=button("Offline map");\n        locbar.addView(loc,new LinearLayout.LayoutParams(0,dp(44),1));\n        locbar.addView(mapButton,new LinearLayout.LayoutParams(0,dp(44),1));\n        loc.setOnClickListener(v->locationFeature.menu());\n        mapButton.setOnClickListener(v->startActivity(new Intent(this,OfflineMapActivity.class)));\n        page.addView(locbar);\n        addOptions(page);')
 edit('        if(code==100) {','        if(code==LocationFeature.PERMISSION){locationFeature.permissionResult();return;}\n        if(code==100) {')
 edit('seen.add(key); addMessage(key,sender,value,false,0,"Received");','seen.add(key); String locText=locationFeature.receive(key,sender,value); addMessage(key,sender,locText==null?value:locText,false,0,"Received");')
 edit('if(token==generation) addMessage("out:"+System.currentTimeMillis()+":"+id,"You",value,true,id,state);','if(token==generation){LocationCodec.Record locRecord=LocationCodec.parse(value);addMessage("out:"+System.currentTimeMillis()+":"+id,"You",locRecord==null?value:LocationCodec.describe(locRecord),true,id,state);}')
 edit('        visible=false; h.removeCallbacks(retry); stopScan();','        if(locationFeature!=null)locationFeature.cancel();\n        visible=false; h.removeCallbacks(retry); stopScan();')
 s=s.replace('Simple LoRa messaging · 0.2.0','Simple LoRa messaging · 0.3.0').replace('Prototype 0.2.0','Prototype 0.3.0')
 s=s.replace('The app does not read GPS coordinates.','GPS is read only after you request a location fix.')
 s=s.replace('GPS sharing and traceroute are not included in this build.','One-shot phone GPS and offline map import included. Shared-channel locations, not private DMs. No traceroute.')
 p.write_text(s,encoding='utf-8')
ns='{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android',ns[1:-1])
p=root/'app/src/main/AndroidManifest.xml';tree=ET.parse(p);m=tree.getroot()
for name in ['android.permission.ACCESS_FINE_LOCATION','android.permission.ACCESS_COARSE_LOCATION']:
 for e in list(m.findall('uses-permission')):
  if e.get(ns+'name')==name:m.remove(e)
 m.insert(0,ET.Element('uses-permission',{ns+'name':name}))
a=m.find('application')
if a is None:raise SystemExit('Manifest application missing')
if not any(e.get(ns+'name')=='.OfflineMapActivity' for e in a.findall('activity')):a.append(ET.Element('activity',{ns+'name':'.OfflineMapActivity',ns+'exported':'false',ns+'configChanges':'orientation|screenSize'}))
tree.write(p,encoding='utf-8',xml_declaration=True)
p=root/'app/build.gradle';s=p.read_text();s=re.sub(r'\bversionCode\s+\d+','versionCode 3',s);s=re.sub(r"\bversionName\s+['\"][^'\"]+['\"]","versionName '0.3.0-offline-test'",s)
if 'org.mapsforge:mapsforge-map-android:0.21.0' not in s:s+="\ndependencies {\n implementation 'org.mapsforge:mapsforge-map-android:0.21.0'\n implementation 'org.mapsforge:mapsforge-themes:0.21.0'\n}\n"
p.write_text(s)
print('GPS/offline source integration applied. Radio configuration unchanged.')
