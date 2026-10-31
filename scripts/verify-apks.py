"""Inspect the signed release APK, not just source configuration."""
import os, re, struct, subprocess, xml.etree.ElementTree as ET, zipfile
from pathlib import Path

sdk=Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME') or Path.home()/'Library/Android/sdk')
java=Path(os.environ.get('ADSKIP_JAVA_HOME','/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home'))/'bin/java'
bt=sdk/'build-tools/36.0.0'
A='{http://schemas.android.com/apk/res/android}'
version=ET.parse('app/src/main/AndroidManifest.xml').getroot().get(A+'versionName')
def output(args):
    return subprocess.check_output([str(x) for x in args],text=True)

def dex_classes(data):
    assert data[:4]==b'dex\n', 'Expected standard DEX'
    def uint(offset):return struct.unpack_from('<I',data,offset)[0]
    string_count,string_offset=uint(56),uint(60)
    strings=[]
    for i in range(string_count):
        start=uint(string_offset+4*i)
        while data[start]&128:start+=1
        start+=1
        strings.append(data[start:data.index(b'\0',start)].decode('utf-8',errors='replace'))
    type_offset=uint(68)
    return {strings[uint(type_offset+4*uint(uint(100)+32*i))] for i in range(uint(96))}

def inspect():
    apk=Path('artifacts')/f'adskip-{version}-release.apk'
    badging=output([bt/'aapt2','dump','badging',apk])
    tree=output([bt/'aapt2','dump','xmltree',apk,'--file','AndroidManifest.xml'])
    assert 'application-debuggable' not in badging, 'Release APK is debuggable'
    assert 'E: instrumentation' not in tree, 'Release APK contains a test entry'
    assert 'android.permission.INTERNET' not in badging, 'Unexpected network permission'
    with zipfile.ZipFile(apk) as z:
        classes=set()
        for name in z.namelist():
            if re.fullmatch(r'classes\d*\.dex',name):classes.update(dex_classes(z.read(name)))
        assert 'Lcn/local/adskip/MainActivity;' in classes
        assert 'Lcn/local/adskip/SkipService;' in classes
        assert 'Lcn/local/adskip/OcrDeviceTest;' not in classes
        assert 'Lcn/local/adskip/OcrEngine;' not in classes, 'OCR implementation not obfuscated as expected'
        # These classes are discovered through manifest strings/reflection at startup.
        merged=ET.parse(Path('build/ocr-release/AndroidManifest.xml')).getroot()
        for meta in merged.findall('.//meta-data'):
            name,value=meta.get(A+'name',''),meta.get(A+'value','')
            reflected=None
            if name.startswith('com.google.firebase.components:'):reflected=name.split(':',1)[1]
            elif name.startswith('backend:'):reflected=name.split(':',1)[1]
            elif value=='androidx.startup':reflected=name
            if reflected:assert 'L'+reflected.replace('.','/')+';' in classes, f'Missing {reflected}'
        files={n for n in z.namelist() if n.startswith(('assets/','lib/')) and not n.endswith('/')}
        assert any('tflite' in n for n in files), 'Bundled OCR model missing'
        assert any(n.endswith('.so') for n in files), 'OCR native libraries missing'
        assert not any(n.endswith(('.jks','mapping.txt','release-signing.json')) for n in z.namelist())
    signature=output([java,'-jar',bt/'lib/apksigner.jar','verify','--print-certs',apk])
    assert re.search(r'Signer #1 certificate SHA-256 digest: \S+',signature), 'Release signature missing'
    mapping=(Path('artifacts')/f'adskip-{version}-release-private/mapping.txt').read_text()
    renamed=[(a,b) for a,b in re.findall(r'^(cn\.local\.adskip\.[^ ]+) -> ([^:]+):$',mapping,re.M) if a!=b]
    assert renamed, 'No app classes were renamed'
    print(f'Release: {len(renamed)} app classes renamed; debug/test entry disabled')
    print(f'Release APK: signature, entry points, model and native libraries checked ({apk.stat().st_size/1024/1024:.1f} MB)')

inspect()
print('PASS: release APK verified')
