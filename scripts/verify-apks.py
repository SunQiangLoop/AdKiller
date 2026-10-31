"""Inspect actual APKs, not just source configuration. Run after building both variants."""
import argparse, hashlib, os, re, struct, subprocess, xml.etree.ElementTree as ET, zipfile
from pathlib import Path

sdk=Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME') or Path.home()/'Library/Android/sdk')
java=Path(os.environ.get('ADSKIP_JAVA_HOME','/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home'))/'bin/java'
bt=sdk/'build-tools/36.0.0'
A='{http://schemas.android.com/apk/res/android}'
version=ET.parse('app/src/main/AndroidManifest.xml').getroot().get(A+'versionName')
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--variant',choices=['all','personal','release'],default='all')
variant=parser.parse_args().variant

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

def inspect(variant):
    apk=Path('artifacts')/f'adskip-{version}-{variant}.apk'
    badging=output([bt/'aapt2','dump','badging',apk])
    tree=output([bt/'aapt2','dump','xmltree',apk,'--file','AndroidManifest.xml'])
    release=variant=='release'
    device_test=not release and Path('tests/OcrDeviceTest.java').exists()
    assert ('application-debuggable' in badging)==(not release), f'{variant}: wrong debug flag'
    assert ('E: instrumentation' in tree)==device_test, f'{variant}: wrong instrumentation entry'
    assert 'android.permission.INTERNET' not in badging, 'Unexpected network permission'
    with zipfile.ZipFile(apk) as z:
        classes=set()
        for name in z.namelist():
            if re.fullmatch(r'classes\d*\.dex',name):classes.update(dex_classes(z.read(name)))
        assert 'Lcn/local/adskip/MainActivity;' in classes
        assert 'Lcn/local/adskip/SkipService;' in classes
        assert ('Lcn/local/adskip/OcrDeviceTest;' in classes)==device_test
        assert ('Lcn/local/adskip/OcrEngine;' in classes)==(not release), 'OCR implementation not obfuscated as expected'
        # These classes are discovered through manifest strings/reflection at startup.
        merged=ET.parse(Path('build')/f'ocr-{variant}'/'AndroidManifest.xml').getroot()
        for meta in merged.findall('.//meta-data'):
            name,value=meta.get(A+'name',''),meta.get(A+'value','')
            reflected=None
            if name.startswith('com.google.firebase.components:'):reflected=name.split(':',1)[1]
            elif name.startswith('backend:'):reflected=name.split(':',1)[1]
            elif value=='androidx.startup':reflected=name
            if reflected:assert 'L'+reflected.replace('.','/')+';' in classes, f'Missing {reflected}'
        files={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.startswith(('assets/','lib/')) and not n.endswith('/')}
        assert any('tflite' in n for n in files), 'Bundled OCR model missing'
        assert any(n.endswith('.so') for n in files), 'OCR native libraries missing'
        assert not any(n.endswith(('.jks','mapping.txt','release-signing.json')) for n in z.namelist())
    signature=output([java,'-jar',bt/'lib/apksigner.jar','verify','--print-certs',apk])
    digest=re.search(r'Signer #1 certificate SHA-256 digest: (\S+)',signature).group(1)
    if release:
        mapping=(Path('artifacts')/f'adskip-{version}-release-private/mapping.txt').read_text()
        renamed=[(a,b) for a,b in re.findall(r'^(cn\.local\.adskip\.[^ ]+) -> ([^:]+):$',mapping,re.M) if a!=b]
        assert renamed, 'No app classes were renamed'
        print(f'Release: {len(renamed)} app classes renamed; debug/test entry disabled')
    print(f'{variant}: signature, entry points, model and native libraries checked ({apk.stat().st_size/1024/1024:.1f} MB)')
    return digest,files

if variant=='all':
    personal,personal_files=inspect('personal')
    release,release_files=inspect('release')
    assert personal!=release, 'Release must use an independent signing identity'
    assert personal_files==release_files, 'OCR assets/native libraries differ between variants'
    print('PASS: both variants verified; identical assets/native libraries, distinct signing identities')
else:
    inspect(variant)
    print(f'PASS: {variant} APK verified')
