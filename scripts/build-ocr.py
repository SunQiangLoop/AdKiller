"""Standalone SDK build, including cached AAR resources, manifests and bundled model."""
import argparse,copy,json,os,secrets,shutil,subprocess,zipfile,xml.etree.ElementTree as ET
from pathlib import Path
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--variant',choices=['personal','release'],default='personal')
variant=parser.parse_args().variant
release=variant=='release'
sdk=Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME') or Path.home()/'Library/Android/sdk')
java=Path(os.environ.get('ADSKIP_JAVA_HOME','/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home'))/'bin'
bt=sdk/'build-tools/36.0.0';android=sdk/'platforms/android-36.1/android.jar'
base=Path('build')/f'ocr-{variant}';shutil.rmtree(base,ignore_errors=True);base.mkdir(parents=True)
for d in ['res','assets','java','classes','dex','lib/arm64-v8a']: (base/d).mkdir(parents=True)
A='{http://schemas.android.com/apk/res/android}';ET.register_namespace('android',A[1:-1])
manifest=ET.parse('app/src/main/AndroidManifest.xml').getroot();app=manifest.find('application')
app.set(A+'debuggable','false' if release else 'true')
values={};packages=[];jars=[];consumer_rules=[]
def resources(root):
 if not root.exists():return
 for f in root.rglob('*'):
  if not f.is_file():continue
  rel=f.relative_to(root)
  if rel.parts[0].startswith('values') and f.suffix=='.xml':
   entries=values.setdefault(rel.parts[0],{})
   for e in ET.parse(f).getroot():
    key=(e.tag,e.get('name'),e.get('type',''))
    if e.tag=='declare-styleable' and key in entries:
     old=entries[key];names={n.get('name') for n in old}
     for n in e:
      if n.get('name') not in names:old.append(copy.deepcopy(n))
    else:entries[key]=copy.deepcopy(e)
  else:
   dest=base/'res'/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(f,dest)
def merge_child(parent,e):
 name=e.get(A+'name')
 for old in parent:
  if old.tag==e.tag and old.get(A+'name')==name:
   for child in e:merge_child(old,copy.deepcopy(child))
   return
 parent.append(copy.deepcopy(e))
for item in json.loads(Path('build/ocr-deps/lock.json').read_text()):
 file=Path(item['file'])
 if item['type']=='jar':jars.append(file);continue
 folder=base/'aar'/f"{item['group']}-{item['name']}";folder.mkdir(parents=True)
 with zipfile.ZipFile(file) as z:z.extractall(folder)
 if (folder/'proguard.txt').exists():consumer_rules.append(folder/'proguard.txt')
 if (folder/'classes.jar').exists():jars.append(folder/'classes.jar')
 jars.extend((folder/'libs').glob('*.jar'))
 if (folder/'AndroidManifest.xml').exists():
  m=ET.parse(folder/'AndroidManifest.xml').getroot();pkg=m.get('package')
  if pkg and pkg not in packages:packages.append(pkg)
  for child in m:
   if child.tag=='application':
    for e in child:merge_child(app,e)
   elif child.tag=='permission':merge_child(manifest,child)
   elif child.tag=='uses-permission':
    if child.get(A+'name') not in ['android.permission.INTERNET','android.permission.ACCESS_NETWORK_STATE']:merge_child(manifest,child)
 resources(folder/'res')
 for dirname,target in [('assets','assets'),('jni/arm64-v8a','lib/arm64-v8a')]:
  if (folder/dirname).exists():shutil.copytree(folder/dirname,base/target,dirs_exist_ok=True)
resources(Path('app/src/main/res'));shutil.copytree('app/src/main/assets',base/'assets',dirs_exist_ok=True)
for qualifier,entries in values.items():
 r=ET.Element('resources');r.extend(entries.values());folder=base/'res'/qualifier;folder.mkdir(parents=True,exist_ok=True);ET.ElementTree(r).write(folder/'merged.xml',encoding='utf-8',xml_declaration=True)
device_test=Path('tests/OcrDeviceTest.java')
if not release and device_test.exists():
 ET.SubElement(manifest,'instrumentation',{A+'name':'cn.local.adskip.OcrDeviceTest',A+'targetPackage':'cn.local.adskip'})
xml=ET.tostring(manifest,encoding='unicode').replace('${applicationId}','cn.local.adskip')
(base/'AndroidManifest.xml').write_text(xml)
def run(args):subprocess.run([str(x) for x in args],check=True)
run([bt/'aapt2','compile','--dir',base/'res','-o',base/'resources.zip'])
run([bt/'aapt2','link','--auto-add-overlay','-o',base/'base.apk','-I',android,'--manifest',base/'AndroidManifest.xml','-A',base/'assets','--java',base/'java','--proguard',base/'aapt-rules.pro','--extra-packages',':'.join(packages),base/'resources.zip'])
cp=os.pathsep.join(str(p) for p in [android]+jars)
test_sources=[device_test] if not release and device_test.exists() else []
run([java/'javac','--release','8','-g:none' if release else '-g','-encoding','UTF-8','-cp',cp,'-d',base/'classes']+list(Path('app/src/main/java').rglob('*.java'))+test_sources+list((base/'java').rglob('*.java')))
run([java/'jar','cf',base/'classes.jar','-C',base/'classes','.'])
version=manifest.get(A+'versionName')
private=Path('artifacts')/f'adskip-{version}-release-private'
if release:
 private.mkdir(parents=True,exist_ok=True)
 # AAPT roots preserve manifest/XML entry points; consumer rules preserve ML Kit
 # registrars and native/reflective entry points. Do not keep the entire app.
 rules=[Path('app/proguard-rules.pro'),base/'aapt-rules.pro']+consumer_rules
 r8=[java/'java','-Xmx2g','-cp',bt/'lib/d8.jar','com.android.tools.r8.R8','--release','--min-api','26','--lib',android,'--output',base/'dex','--pg-map-output',private/'mapping.txt']
 for rule in rules:r8+=['--pg-conf',rule]
 run(r8+[base/'classes.jar']+jars)
else:
 run([java/'java','-Xmx2g','-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--debug','--min-api','26','--lib',android,'--output',base/'dex',base/'classes.jar']+jars)
shutil.copyfile(base/'base.apk',base/'unsigned.apk')
with zipfile.ZipFile(base/'unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as z:
 for folder in ['dex','lib']:
  for f in (base/folder).rglob('*'):
   if f.is_file():z.write(f,str(f.relative_to(base/'dex') if folder=='dex' else f.relative_to(base)))
run([bt/'zipalign','-f','-p','4',base/'unsigned.apk',base/'aligned.apk'])
output=Path('artifacts')/f'adskip-{version}-{variant}.apk'
if release:
 # Reuse this identity forever. Passwords are never put on the command line.
 config=Path('artifacts/release-signing.json');keystore=Path('artifacts/release.jks')
 new_identity=not config.exists() and not keystore.exists()
 if config.exists() and not keystore.exists():raise RuntimeError('Release signing key is missing; restore artifacts/release.jks from backup, do not generate a new identity')
 if not config.exists():
  if keystore.exists():raise RuntimeError('release.jks exists but release-signing.json is missing; restore the signing configuration instead of replacing the key')
  credentials={'alias':'qiang-release','password':secrets.token_urlsafe(32)}
  with open(config,'x',opener=lambda path,flags:os.open(path,flags,0o600)) as f:json.dump(credentials,f)
 credentials=json.loads(config.read_text());os.chmod(config,0o600)
 os.environ['ADSKIP_RELEASE_PASSWORD']=credentials['password']
 if new_identity:
  run([java/'keytool','-genkeypair','-keystore',keystore,'-storetype','PKCS12','-alias',credentials['alias'],'-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Qiang Release','-storepass:env','ADSKIP_RELEASE_PASSWORD','-keypass:env','ADSKIP_RELEASE_PASSWORD'])
 os.chmod(keystore,0o600)
 signing=['--ks',keystore,'--ks-key-alias',credentials['alias'],'--ks-pass','env:ADSKIP_RELEASE_PASSWORD','--key-pass','env:ADSKIP_RELEASE_PASSWORD']
else:
 debug_key=Path('artifacts/debug.jks')
 if not debug_key.exists():
  debug_key.parent.mkdir(parents=True,exist_ok=True)
  run([java/'keytool','-genkeypair','-keystore',debug_key,'-storetype','JKS','-alias','androiddebugkey','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Android Debug,O=Android,C=US','-storepass','android','-keypass','android','-noprompt'])
 signing=['--ks','artifacts/debug.jks','--ks-pass','pass:android','--key-pass','pass:android']
run([java/'java','-jar',bt/'lib/apksigner.jar','sign']+signing+['--out',output,base/'aligned.apk'])
run([java/'java','-jar',bt/'lib/apksigner.jar','verify',output])
print(f'APK: {output} ({output.stat().st_size/1024/1024:.1f} MB)')
