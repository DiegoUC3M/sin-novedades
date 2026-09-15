#!/usr/bin/env python3
"""Build and sign with the Android SDK directly; Python 3 + Java 17 + SDK 36.

Uses no third-party Python packages. --tools and --platform can override SDK paths.
Generates a private signing key in .signing/ on the first local build; never commit it.
"""
from __future__ import annotations
import argparse
import hashlib
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[1]

def run(args,**kwargs):
    subprocess.run([str(a) for a in args],check=True,**kwargs)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk',default=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT'))
    parser.add_argument('--tools',type=Path)
    parser.add_argument('--platform',type=Path)
    manifest_version=ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot().get('{http://schemas.android.com/apk/res/android}versionName')
    parser.add_argument('--output',type=Path,default=ROOT/'dist'/('sin-novedades-'+manifest_version+'.apk'))
    parser.add_argument('--test-only',action='store_true')
    args=parser.parse_args()
    java=shutil.which('java')
    if not java: parser.error('Java 17 no está disponible.')
    build=ROOT/'build'/'sdk'; build.mkdir(parents=True,exist_ok=True)
    tests=build/'tests'; tests.mkdir(exist_ok=True)
    detector=ROOT/'app/src/main/java/es/sinnovedades/app/TabDetector.java'
    policy=ROOT/'app/src/main/java/es/sinnovedades/app/TouchPolicy.java'
    run([java,'com.sun.tools.javac.Main','--release','8','-encoding','UTF-8','-d',tests,detector,policy,ROOT/'tests/TabDetectorTest.java',ROOT/'tests/TouchPolicyTest.java'])
    run([java,'-cp',tests,'es.sinnovedades.app.TabDetectorTest'])
    run([java,'-cp',tests,'es.sinnovedades.app.TouchPolicyTest'])
    if args.test_only: return
    sdk=Path(args.sdk) if args.sdk else None
    bt=args.tools or (sdk/'build-tools/36.0.0' if sdk else None)
    platform=args.platform or (sdk/'platforms/android-36/android.jar' if sdk else None)
    if not bt or not platform or not platform.is_file(): parser.error('Indica --sdk, o --tools y --platform.')
    exe='.exe' if os.name=='nt' else ''
    aapt=bt/('aapt2'+exe); align=bt/('zipalign'+exe)
    for f in (aapt,align,bt/'lib/d8.jar',bt/'lib/apksigner.jar'):
        if not f.is_file(): parser.error('Falta herramienta SDK: '+str(f))
    generated=build/'generated'; generated.mkdir(exist_ok=True)
    classes=build/'classes'
    if classes.exists(): shutil.rmtree(classes)
    classes.mkdir()
    res=build/'resources.zip'
    run([aapt,'compile','--dir',ROOT/'app/src/main/res','-o',res])
    manifest=ET.parse(ROOT/'app/src/main/AndroidManifest.xml')
    ET.register_namespace('android','http://schemas.android.com/apk/res/android')
    manifest.getroot().set('package','es.sinnovedades.app')
    manifest.write(build/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
    unsigned=build/'unsigned.apk'
    run([aapt,'link','-o',unsigned,'-I',platform,'--manifest',build/'AndroidManifest.xml','--java',generated,'--auto-add-overlay',res])
    sources=sorted((ROOT/'app/src/main/java').rglob('*.java'))+sorted(generated.rglob('*.java'))
    bootclasspath=os.pathsep.join((str(bt/'core-lambda-stubs.jar'),str(platform)))
    run([java,'com.sun.tools.javac.Main','-source','8','-target','8','-encoding','UTF-8','-Xlint:-options','-bootclasspath',bootclasspath,'-d',classes,*sources])
    classjar=build/'classes.jar'
    with zipfile.ZipFile(classjar,'w',zipfile.ZIP_DEFLATED) as z:
        for p in sorted(classes.rglob('*.class')): z.write(p,p.relative_to(classes))
    dex=build/'dex'; dex.mkdir(exist_ok=True)
    run([java,'-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--release','--min-api','26','--lib',platform,'--output',dex,classjar])
    with zipfile.ZipFile(unsigned,'a',zipfile.ZIP_DEFLATED) as z:
        for p in sorted(dex.glob('*.dex')): z.write(p,p.name)
    aligned=build/'aligned.apk'
    run([align,'-f','-p','4',unsigned,aligned])
    signing=ROOT/'.signing'; signing.mkdir(mode=0o700,exist_ok=True)
    key=signing/'sin-novedades.p12'; password=signing/'password.txt'
    if not key.exists():
        password.write_text(secrets.token_urlsafe(32),encoding='utf-8'); password.chmod(0o600)
        keytool=shutil.which('keytool') or str(Path(java).resolve().parent/('keytool'+exe))
        run([keytool,'-genkeypair','-keystore',key,'-storepass:file',password,'-alias','sin-novedades',
             '-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Sin Novedades, OU=Personal Android App','-storetype','PKCS12'])
        key.chmod(0o600)
    if not password.is_file(): parser.error('Falta la contraseña del almacén .signing/password.txt.')
    args.output.parent.mkdir(parents=True,exist_ok=True)
    run([java,'-jar',bt/'lib/apksigner.jar','sign','--ks',key,'--ks-key-alias','sin-novedades','--ks-pass','file:'+str(password),
         '--v1-signing-enabled','true','--v2-signing-enabled','true','--v3-signing-enabled','true','--v4-signing-enabled','false','--out',args.output,aligned])
    run([java,'-jar',bt/'lib/apksigner.jar','verify','--verbose','--print-certs',args.output])
    run([align,'-c','-v','4',args.output],stdout=subprocess.DEVNULL)
    digest=hashlib.sha256(args.output.read_bytes()).hexdigest()
    args.output.with_suffix('.apk.sha256').write_text(digest+'  '+args.output.name+'\n',encoding='utf-8')
    print('APK:',args.output)
    print('SHA-256:',digest)

if __name__=='__main__':
    main()
