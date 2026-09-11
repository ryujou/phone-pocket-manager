"""Check tracked public files and ensure bundled rosters remain fictional."""
from pathlib import Path
import json
import re
import subprocess
from openpyxl import load_workbook

ROOT = Path(__file__).resolve().parents[1]
NAMES = {'张三', '李四', '王五', '赵六', '钱七', '孙八'}

def main():
    tracked = subprocess.check_output(['git', 'ls-files', '-z'], cwd=ROOT).decode().split('\0')
    errors = []
    for name in filter(None, tracked):
        p = ROOT / name
        if p.suffix.lower() in {'.db', '.sqlite', '.sqlite3', '.jks', '.keystore', '.apk', '.aab', '.p12', '.key'} or p.name == 'local.properties':
            errors.append(f'Forbidden public file: {name}')
        if p.suffix == '.xlsx':
            rows = list(load_workbook(p, read_only=True).active.values)
            if len(rows) != 7 or any(row[1] not in NAMES or not str(row[0]).startswith('9000') for row in rows[1:]):
                errors.append(f'Non-mock workbook: {name}')
        if p.name == 'rosters.json':
            text = p.read_text()
            data = json.loads(text)
            if len(data) != 12 or any(row['name'] not in NAMES or not row['sid'].startswith('9000') or row['class_name'] not in {'示例一班', '示例二班'} for row in data):
                errors.append(f'Non-mock roster: {name}')
            if '9000' not in text:
                errors.append(f'Missing mock IDs: {name}')
        if p.suffix in {'.py', '.kt', '.kts', '.swift', '.md', '.json', '.yml', '.sh', '.command', '.properties'} and p != Path(__file__).resolve():
            text = p.read_text()
            if re.search(r'/Users/|wxid_|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|gh[pousr]_[A-Za-z0-9]{25,}', text):
                errors.append(f'Personal path or secret marker: {name}')
    if errors:
        raise SystemExit('\n'.join(errors))
    print('Public-file audit passed (tracked files and mock rosters).')

if __name__ == '__main__':
    main()
