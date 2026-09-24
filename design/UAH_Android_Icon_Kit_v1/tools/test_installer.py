#!/usr/bin/env python3
"""Temporary-project installer regression tests; never touches a real project."""
from __future__ import annotations
from contextlib import redirect_stdout
import importlib.util, io, json, tempfile, unittest, zipfile
from pathlib import Path
import xml.etree.ElementTree as ET

HERE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('installer',HERE/'install_icons.py')
installer=importlib.util.module_from_spec(spec);spec.loader.exec_module(installer)
MANIFEST='''<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:name=".AgentApp" android:label="@string/app_name" android:icon="@mipmap/ic_launcher" android:roundIcon="@mipmap/ic_launcher_round"><activity android:name=".MainActivity"/></application></manifest>'''

class Tests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.project=Path(self.tmp.name)/'工程 UAH'
        self.res=self.project/'app/src/main/res';(self.res/'values').mkdir(parents=True)
        (self.project/'app/src/main/AndroidManifest.xml').write_text(MANIFEST)
        self.strings=self.res/'values/strings.xml'
        self.strings.write_bytes('<resources>\r\n    <!-- keep me -->\r\n    <string name="app_name">构元 Actant</string>\r\n    <string name="other">保留内容</string>\r\n</resources>\r\n'.encode())
        for den in installer.LAUNCHER:
            p=self.res/f'mipmap-mdpi/{den}.webp';p.parent.mkdir(exist_ok=True);p.write_bytes(b'old webp fixture')
        (self.project/'app/build.gradle.kts').write_text('applicationId = "com.Ling.actant"\nnamespace = "com.example.myapplication"')
    def tearDown(self):self.tmp.cleanup()
    def files(self):return {p.relative_to(self.project).as_posix():p.read_bytes() for p in self.project.rglob('*') if p.is_file()}
    def run_it(self,apply=False,rename=False):
        with redirect_stdout(io.StringIO()):return installer.run(self.project,apply,rename)
    def test_dry_run_no_writes(self):
        before=self.files();self.run_it(False,True);self.assertEqual(before,self.files())
    def test_apply_icons_retains_identity_and_name(self):
        manifest=(self.project/'app/src/main/AndroidManifest.xml').read_bytes();strings=self.strings.read_bytes()
        self.run_it(True,False)
        self.assertEqual(manifest,(self.project/'app/src/main/AndroidManifest.xml').read_bytes());self.assertEqual(strings,self.strings.read_bytes())
        self.assertIn('com.Ling.actant',(self.project/'app/build.gradle.kts').read_text())
    def test_rename_preserves_other_text_and_crlf(self):
        self.run_it(True,True);s=self.strings.read_bytes()
        self.assertIn(b'Used AI Harness',s);self.assertIn(b'<!-- keep me -->',s);self.assertIn('保留内容'.encode(),s);self.assertIn(b'\r\n',s)
        root=ET.parse(self.res/'values/uah_brand_strings.xml').getroot()
        self.assertEqual({n.get('name'):n.text for n in root},{'app_name_full':'Used AI Harness','app_name_short':'UAH'})
    def test_removes_only_same_name_stale(self):
        p=self.res/'mipmap-mdpi/unrelated.webp';p.write_bytes(b'preserve')
        self.run_it(True,False);self.assertFalse((self.res/'mipmap-mdpi/ic_launcher.webp').exists());self.assertEqual(p.read_bytes(),b'preserve')
    def test_backup_contains_previous_bytes(self):
        original=self.strings.read_bytes();self.run_it(True,True)
        p=next((self.project/'.uah-icon-backups').glob('*.zip'))
        with zipfile.ZipFile(p) as z:self.assertEqual(z.read('before/app/src/main/res/values/strings.xml'),original);self.assertIn('restore.json',z.namelist())
    def test_idempotent_apply(self):
        self.run_it(True,True);before=self.files();self.run_it(True,True);self.assertEqual(before,self.files())
    def test_wrong_manifest_rejected(self):
        p=self.project/'app/src/main/AndroidManifest.xml';p.write_text(MANIFEST.replace('@mipmap/ic_launcher"','@drawable/custom"'));before=self.files()
        with self.assertRaises(ValueError):self.run_it(True,True)
        self.assertEqual(before,self.files())
    def test_invalid_existing_xml_rejected(self):
        self.strings.write_text('<resources>bad');before=self.files()
        with self.assertRaises(ET.ParseError):self.run_it(True,True)
        self.assertEqual(before,self.files())
    def test_duplicate_app_name_rejected(self):
        (self.res/'values/another.xml').write_text('<resources><string name="app_name">Other</string></resources>');before=self.files()
        with self.assertRaises(ValueError):self.run_it(True,True)
        self.assertEqual(before,self.files())
    def test_localized_name_updated(self):
        p=self.res/'values-ja/strings.xml';p.parent.mkdir();p.write_text('<resources><string name="app_name">旧名前</string><string name="other">はい</string></resources>')
        self.run_it(True,True);self.assertIn('Used AI Harness',p.read_text());self.assertIn('はい',p.read_text())
    def test_alias_collision_rejected(self):
        (self.res/'values/aliases.xml').write_text('<resources><item name="ic_launcher" type="mipmap">@mipmap/old</item></resources>')
        before=self.files()
        with self.assertRaises(ValueError):self.run_it(True,False)
        self.assertEqual(before,self.files())
    def test_symlink_target_rejected(self):
        out=Path(self.tmp.name)/'outside.png';out.write_bytes(b'dont overwrite')
        link=self.res/'mipmap-mdpi/ic_launcher.png'
        try:link.symlink_to(out)
        except OSError:self.skipTest('symlinks unavailable in test OS')
        with self.assertRaises(ValueError):self.run_it(True,False)
        self.assertEqual(out.read_bytes(),b'dont overwrite')

if __name__=='__main__':
    suite=unittest.defaultTestLoader.loadTestsFromTestCase(Tests)
    result=unittest.TextTestRunner(verbosity=2).run(suite)
    report={'kind':'temporary-project-tests','tests_run':result.testsRun,'failed':len(result.failures),'errors':len(result.errors),'skipped':len(result.skipped),'all_passed':result.wasSuccessful(),'real_project_modified':False}
    (HERE.parent/'INSTALLER_TESTS.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    raise SystemExit(0 if result.wasSuccessful() else 1)
