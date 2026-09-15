#!/usr/bin/env python3
"""Build a synthetic com.whatsapp navigation APK for an EMPTY EMULATOR only.

Never install this fixture on a device with WhatsApp. It deliberately uses that
package solely to exercise the exact release APK without adding test bypasses.
It has no network permission and implements no WhatsApp functionality.
"""
import argparse,os,subprocess,zipfile
from pathlib import Path

root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--tools',type=Path,required=True)
parser.add_argument('--platform',type=Path,required=True)
args=parser.parse_args(); bt=args.tools
out=root/'build/fixture'; out.mkdir(parents=True,exist_ok=True)
def run(*argv): subprocess.run([str(x) for x in argv],check=True)
(out/'AndroidManifest.xml').write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.whatsapp" android:versionCode="1" android:versionName="fixture">
<uses-sdk android:minSdkVersion="30" android:targetSdkVersion="36" />
<application android:label="Barra sintética de prueba" android:debuggable="true" android:theme="@android:style/Theme.Material.Light.NoActionBar">
<activity android:name="fixture.FixtureActivity" android:exported="true"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity>
</application></manifest>''',encoding='utf-8')
classes=out/'classes'; classes.mkdir(exist_ok=True)
run('java','com.sun.tools.javac.Main','-source','8','-target','8','-encoding','UTF-8','-Xlint:-options',
    '-bootclasspath',os.pathsep.join([str(bt/'core-lambda-stubs.jar'),str(args.platform)]),
    '-d',classes,root/'tests/android-fixture/FixtureActivity.java')
jar=out/'classes.jar'
with zipfile.ZipFile(jar,'w') as z:
    for p in classes.rglob('*.class'): z.write(p,p.relative_to(classes))
run('java','-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--min-api','30','--lib',args.platform,'--output',out,jar)
unsigned=out/'unsigned.apk'
run(bt/'aapt2','link','-I',args.platform,'--manifest',out/'AndroidManifest.xml','-o',unsigned)
with zipfile.ZipFile(unsigned,'a') as z: z.write(out/'classes.dex','classes.dex')
run(bt/'zipalign','-f','4',unsigned,out/'aligned.apk')
run('java','-jar',bt/'lib/apksigner.jar','sign','--ks',root/'.signing/sin-novedades.p12','--ks-key-alias','sin-novedades',
    '--ks-pass','file:'+str(root/'.signing/password.txt'),'--out',out/'fixture.apk',out/'aligned.apk')
print(out/'fixture.apk')
