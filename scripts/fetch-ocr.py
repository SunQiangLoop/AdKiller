"""Resolve fixed Maven coordinates using curl; cache all inputs for offline SDK builds."""
import concurrent.futures,json,subprocess,sys,xml.etree.ElementTree as ET
from pathlib import Path
cache=Path('build/ocr-deps');cache.mkdir(parents=True,exist_ok=True)
ns={'m':'http://maven.apache.org/POM/4.0.0'}
repos=['https://dl.google.com/dl/android/maven2/','https://repo.maven.apache.org/maven2/']
def fetch(g,a,v,extension):
 path=f'{g.replace(".","/")}/{a}/{v}/{a}-{v}.{extension}'
 target=cache/f'{g}-{a}-{v}.{extension}'
 if target.exists() and target.stat().st_size:return target
 url=(repos[0] if g.startswith(('com.google.android','com.google.mlkit','androidx.','com.google.firebase','com.google.android.datatransport')) else repos[1])+path
 if '--download' in sys.argv:
  temp=target.with_suffix(target.suffix+'.partial')
  subprocess.run(['curl','-fsSL','--retry','2','--max-time','90',url,'-o',str(temp)],check=True)
  temp.replace(target);return target
 raise FileNotFoundError(json.dumps({'url':url,'file':str(target)}))

def inspect(coord):
 g,a,v=coord;root=ET.parse(fetch(g,a,v,'pom')).getroot()
 def val(el,key,default=''):return el.findtext('m:'+key,default,namespaces=ns)
 props={'project.version':v,'project.groupId':g}
 p=root.find('m:properties',ns)
 if p is not None:props.update({e.tag.split('}')[-1]:e.text for e in p})
 def expand(v):
  for k,x in props.items():v=v.replace('${'+k+'}',x)
  return v.strip('[]')
 deps=[]
 for d in root.findall('m:dependencies/m:dependency',ns):
  if val(d,'scope') in ['test','provided'] or val(d,'optional')=='true':continue
  dv=expand(val(d,'version'))
  if not dv or '${' in dv:raise RuntimeError(f'Unresolved dependency in {coord}: {ET.tostring(d)}')
  deps.append((val(d,'groupId'),val(d,'artifactId'),dv))
 return coord,val(root,'packaging','jar'),deps
selected={};todo=[('com.google.mlkit','text-recognition-chinese','16.0.1')]
def version(v):return tuple(int(x) if x.isdigit() else x for x in v.replace('-','.').split('.'))
needed=[]
while todo:
 batch=[]
 for c in todo:
  key=c[:2]
  if key in selected and version(selected[key][0])>=version(c[2]):continue
  selected[key]=(c[2],None);batch.append(c)
 todo=[]
 for c in batch:
  try:
   c,typ,deps=inspect(c)
   if selected[c[:2]][0]==c[2]:selected[c[:2]]=(c[2],typ)
   todo.extend(deps)
  except FileNotFoundError as e:needed.append(json.loads(str(e)))
artifacts=[]
for (g,a),(v,t) in selected.items():
 if t in [None,'pom']:continue
 try:artifacts.append({'group':g,'name':a,'version':v,'type':t,'file':str(fetch(g,a,v,t))})
 except FileNotFoundError as e:needed.append(json.loads(str(e)))
if needed:print(json.dumps({'needed':needed}))
else:
 (cache/'lock.json').write_text(json.dumps(artifacts,indent=2))
 print(json.dumps({'done':len(artifacts)}))
