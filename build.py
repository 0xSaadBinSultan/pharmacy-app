#!/usr/bin/env python3
"""Build, test, run or package the Java demo on Windows, macOS and Linux."""
import argparse
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parent


def run(command):
    subprocess.run([str(part) for part in command], cwd=ROOT, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--test', action='store_true', help='Run headless model regression checks')
    parser.add_argument('--ui-test', action='store_true', help='Run desktop checks; requires a graphical session')
    parser.add_argument('--run', action='store_true', help='Open the desktop application')
    parser.add_argument('--package', action='store_true', help='Create a standalone app image using JDK 17+ jpackage')
    args = parser.parse_args()
    required = ['java', 'javac', 'jar'] + (['jpackage'] if args.package else [])
    missing = [name for name in required if not shutil.which(name)]
    if missing:
        parser.error('Install a JDK and put its bin directory on PATH. Missing: ' + ', '.join(missing))
    output = ROOT / 'build/classes'
    if output.exists():
        shutil.rmtree(output)
    output.mkdir(parents=True)
    sources = sorted(ROOT.glob('*.java'))
    run(['javac', '--release', '11', '-encoding', 'UTF-8', '-Xlint:all', '-d', output, *sources])
    if args.test:
        run(['java', '-Djava.awt.headless=true', '-cp', output, 'ModelTest'])
    if args.ui_test:
        run(['java', '-cp', output, 'UiSmokeTest'])
    app_classes = ROOT / 'build/app'
    if app_classes.exists():
        shutil.rmtree(app_classes)
    app_classes.mkdir(parents=True)
    for file in output.glob('*.class'):
        if not file.name.startswith(('ModelTest', 'UiSmokeTest')):
            shutil.copy2(file, app_classes / file.name)
    dist = ROOT / 'dist'
    dist.mkdir(exist_ok=True)
    jar = dist / 'PharmacyManagement.jar'
    run(['jar', '--create', '--file', jar, '--main-class', 'PharmacyApp', '-C', app_classes, '.'])
    if args.package:
        image_root = ROOT / 'build/native'
        if image_root.exists():
            shutil.rmtree(image_root)
        run(['jpackage', '--type', 'app-image', '--name', 'PharmacyDemo',
             '--input', dist, '--main-jar', jar.name, '--main-class', 'PharmacyApp',
             '--app-version', '1.1.0', '--vendor', '0xSaadBinSultan',
             '--dest', image_root, '--add-modules', 'java.desktop,java.logging,java.xml',
             '--java-options', '-Dfile.encoding=UTF-8'])
        print('Standalone app image: ' + str(image_root))
    if args.run:
        run(['java', '-jar', jar])
    print('Portable JAR: ' + str(jar))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
