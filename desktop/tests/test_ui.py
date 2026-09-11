from pathlib import Path
from streamlit.testing.v1 import AppTest
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import storage
ROOT=Path(__file__).resolve().parents[1]
def label(elements,name): return next(e for e in elements if e.label==name)
def test_mock_workflow(tmp_path,monkeypatch):
    monkeypatch.setenv('PHONE_MANAGER_DATA',str(tmp_path))
    at=AppTest.from_file(str(ROOT/'app.py'),default_timeout=20).run()
    assert not at.exception
    assert len(label(at.selectbox,'当前班级').options)==2
    status=next(e for e in at.selectbox if e.label.startswith('01 ') and e.label.endswith(' 状态'))
    status.select('免交').run()
    label(at.checkbox,'我已核对本次结果，未解决的待复核项暂不计入未交').check().run()
    label(at.button,'确认保存').click().run()
    label(at.button,'确认保存').click().run()
    assert not at.exception
    db=storage.connect(tmp_path/'demo/records.sqlite3')
    assert len(storage.details(db))==6
    db.close()
    for page in ['历史记录','统计导出','名单管理']:
        label(at.radio,'功能').set_value(page).run(); assert not at.exception
    label(at.selectbox,'数据空间').select('正式记录').run()
    db=storage.connect(tmp_path/'official/records.sqlite3'); assert not storage.details(db); db.close()
