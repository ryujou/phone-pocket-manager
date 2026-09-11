from pathlib import Path
from datetime import date
import hashlib
import json
import html
import os
import io
import zipfile
import importlib
import cv2
import numpy as np
import pandas as pd
from PIL import Image
import streamlit as st
from streamlit_image_coordinates import streamlit_image_coordinates
import storage as store
import vision
# Streamlit's lightweight file watcher may retain imported modules during updates.
# These modules hold no live connections or mutable application state.
importlib.reload(store)
importlib.reload(vision)
from vision import read_image, locate, recognize, overlay, rectify, default_grid

ROOT=Path(__file__).resolve().parent
DATA=Path(os.environ.get('PHONE_MANAGER_DATA',ROOT/'data'))
st.set_page_config(page_title='手机上交管理',page_icon='📋',layout='wide')
st.markdown('''<style>
.stApp {background:#fff;color:#142437} .block-container{padding:2rem 1.5rem;max-width:1600px}
[data-testid="stSidebar"]{background:#f0f5f7;min-width:245px!important;max-width:245px!important} h1{font-size:1.9rem!important} h2{font-size:1.2rem!important}
[data-testid="stSidebar"] h1{font-size:1.5rem!important;color:#087f8c}
[data-testid="stToolbar"]{display:none}
[data-testid="stVerticalBlock"]{gap:.6rem} [data-testid="stSelectbox"] p{font-size:13px}
.pocket{padding:6px;border-radius:6px;font-size:13px;min-height:34px;white-space:normal}
.pocket b{font-size:16px;margin-right:5px}.green{background:#dcf3e6}.red{background:#ffe0df}.amber{background:#fff0c6}.gray{background:#edf0f3}
button[kind="primary"]{background:#087f8c;border-color:#087f8c} .hint{color:#687787;font-size:14px}
</style>''',unsafe_allow_html=True)
st.sidebar.title('手机上交管理')
page=st.sidebar.radio('功能',['本次识别','名单管理','历史记录','统计导出'],label_visibility='collapsed')
st.sidebar.divider()
space=st.sidebar.selectbox('数据空间',['样图测试','正式记录'])
space_key='demo' if space=='样图测试' else 'official'
folder=DATA/space_key
db=store.connect(folder/'records.sqlite3')
store.seed_rosters(db,sorted((ROOT/'samples').glob('roster*.xlsx')))
st.sidebar.caption('测试与正式数据库独立。样图测试不会增加正式未交次数。')
class_list=store.classes(db)
cls=st.sidebar.selectbox('当前班级',class_list)
st.sidebar.caption(f'{len(class_list)} 个班级 · 当前 {len(store.students(db,cls))} 名学生')
if st.session_state.pop('flash',None): st.success('保存成功。统计已更新；重复保存不会累计次数。')

def notify(message):
    st.session_state['flash']=message
    st.rerun()

def reset_prediction(result):
    st.session_state['analysis']=result
    st.session_state['revision']=st.session_state.get('revision',0)+1

def calibrate(im,source_key):
    with st.expander('校正袋位 · 自动定位不准时展开',expanded='analysis' not in st.session_state):
        manual=st.checkbox('手动点击四角',key='manual_'+source_key)
        points=st.session_state.get('corners')
        if manual:
            st.caption('包含顶部横条与最底部：依次点击左上、右上、右下、左下。选择完成后点“应用四角”。')
            if st.button('重新选择四角'):
                st.session_state['click_points']=[]
                st.session_state['click_nonce']=st.session_state.get('click_nonce',0)+1
            chosen=st.session_state.setdefault('click_points',[])
            scale=480/im.shape[1]
            display=Image.fromarray(im).resize((480,round(im.shape[0]*scale)))
            click=streamlit_image_coordinates(display,key=f'corners_{source_key}_{st.session_state.get("click_nonce",0)}',cursor='crosshair')
            event=json.dumps(click,sort_keys=True)
            if click and event!=st.session_state.get('last_click') and len(chosen)<4:
                st.session_state['last_click']=event
                chosen.append([round(click['x']/scale),round(click['y']/scale)])
            st.write(f'已选择 {len(chosen)}/4 个角点：{chosen}')
            if st.button('应用四角',disabled=len(chosen)!=4):
                try:
                    result,_=recognize(im,chosen)
                    st.session_state['corners']=chosen.copy(); reset_prediction(result); st.rerun()
                except ValueError as e: st.error(str(e))
        if points:
            try:
                result=st.session_state.get('analysis')
                warp=rectify(im,points)
                grid=result['grid'] if result else default_grid(warp)
                canvas=warp.copy()
                for y in grid['rows']: cv2.line(canvas,(0,y),(599,y),(240,60,60),2)
                for x in grid['cols']: cv2.line(canvas,(min(x,599),0),(min(x,599),1079),(20,180,150),2)
                left,right=st.columns([1,2])
                with left: st.image(canvas,caption='红线：黄色袋口背景中心；绿线：列边界',width=270)
                with right:
                    with st.form('grid_form_'+source_key):
                        row_text=st.text_input('9 条袋口线（像素，逗号分隔）',','.join(map(str,grid['rows'])))
                        col_text=st.text_input('7 条列边界（像素，逗号分隔）',','.join(map(str,grid['cols'])))
                        apply=st.form_submit_button('应用网格并重新识别')
                    if apply:
                        try:
                            new_grid={'rows':[int(v.strip()) for v in row_text.replace('，',',').split(',')],'cols':[int(v.strip()) for v in col_text.replace('，',',').split(',')]}
                            new_result,_=recognize(im,points,new_grid)
                            reset_prediction(new_result); st.rerun()
                        except ValueError as e: st.error(str(e))
                    st.caption('重新识别会重置尚未保存的状态修改。四角和网格随照片保存，不会影响其他班级。')
            except ValueError as e: st.error(str(e))

def recognition_page():
    st.title('本次识别')
    st.markdown('<p class="hint">上传照片，核对袋位，确认保存。</p>',unsafe_allow_html=True)
    c1,c2,c3=st.columns([2,1,1])
    term=c1.text_input('学期','2026—2027 学年上学期')
    day=c2.date_input('日期',date.today())
    lesson=c3.text_input('课次','第1节')
    photo_choice,photo_upload=st.columns([1,2])
    with photo_choice:
        if space_key=='demo':
            selected=st.selectbox('测试照片',['上传其他照片']+[f'样图 {i}' for i in range(1,8)]+['AI修改示例图'],index=1)
        else:
            selected='上传其他照片'
            st.caption('拍全手机袋，手机顶部露出袋口。')
    with photo_upload:
        upload=st.file_uploader('上传照片',type=['jpg','jpeg','png'],help='建议正面、清晰、完整拍摄，手机顶部露出袋口。')
    raw=upload.getvalue() if upload else ((ROOT/'samples'/('example_edited.jpg' if selected=='AI修改示例图' else f'sample_{selected.split()[-1]}.jpg')).read_bytes() if selected!='上传其他照片' else None)
    if not raw:
        st.info('上传手机袋照片后开始识别。'); return
    try: im=read_image(raw)
    except Exception:
        st.error('无法读取图片，请上传有效 JPG 或 PNG。'); return
    existing=db.execute('SELECT id FROM sessions WHERE class=? AND term=? AND day=? AND lesson=?',(cls,term.strip(),day.isoformat(),lesson.strip())).fetchone()
    roster=[{k:r[k] for k in ('sid','name','pocket')} for r in store.students(db,cls)]
    if existing:
        roster=[dict(r) for r in db.execute('SELECT sid,name,pocket FROM records WHERE session=? ORDER BY pocket IS NULL,pocket,sid',(existing['id'],))]
    signature=hashlib.sha256(raw+json.dumps(roster,sort_keys=True).encode()+space_key.encode()+str((cls,term,day,lesson)).encode()).hexdigest()[:16]
    if st.session_state.get('source_key')!=signature:
        for k in ['analysis','corners','click_points','last_click']: st.session_state.pop(k,None)
        st.session_state['source_key']=signature
        try:
            with st.spinner('正在定位手机袋并分析 54 个袋口…'):
                result,_=recognize(im)
            st.session_state['corners']=result['corners']; reset_prediction(result)
        except ValueError as e: st.warning(str(e))
    calibrate(im,signature)
    result=st.session_state.get('analysis')
    if not result: st.info('请展开“校正袋位”，手动选择四角。'); return
    if result.get('quality_warning'): st.warning(result['quality_warning'])
    warp=rectify(im,result['corners'])
    existing=db.execute('SELECT id FROM sessions WHERE class=? AND term=? AND day=? AND lesson=?',(cls,term.strip(),day.isoformat(),lesson.strip())).fetchone()
    if existing: st.warning('此课次已有记录；本次确认将更新对应学生状态。历史姓名和袋号快照保留。')
    by_pocket={r['pocket']:r for r in roster}
    saved_states={r['sid']:r['state'] for r in db.execute('SELECT sid,state FROM records WHERE session=?',(existing['id'],))} if existing else {}
    saved_notes={r['sid']:r['note'] for r in db.execute('SELECT sid,note FROM records WHERE session=?',(existing['id'],))} if existing else {}
    left,right=st.columns([.85,1.75],gap='medium')
    revision=st.session_state.get('revision',0)
    records=[]
    with left:
        view=st.radio('图片查看',['编号标注','原始照片'],horizontal=True)
        st.image(overlay(warp,result) if view=='编号标注' else im,width='stretch')
        zoom=st.selectbox('放大袋口',[p['pocket'] for p in result['pockets']])
        p=next(p for p in result['pockets'] if p['pocket']==zoom)
        x0,y0,x1,y1=p['bbox']
        st.image(warp[y0:y1,x0:x1],width=260,caption=f'{zoom} 号 · 黄色露出评分 {p["score"]:.2f}（不是置信概率）')
        note=st.text_input('本次备注（保存到本次每名学生记录）',key='note_'+signature)
    with right:
        st.subheader('袋位核对')
        st.caption('绿色 已交 · 红色 未交 · 黄色 待复核 · 灰色 未分配/例外。均为初筛，请核对后保存。')
        for row in range(9):
            cells=st.columns(6,gap='small')
            for col in range(6):
                pocket=result['pockets'][row*6+col]; number=pocket['pocket']; student=by_pocket.get(number)
                with cells[col]:
                    key=f'status_{signature}_{revision}_{number}'
                    initial=saved_states.get(student['sid']) if student else None
                    initial=initial or {'phone':'已交','empty':'未交','review':'待复核'}[pocket['prediction']]
                    current=st.session_state.get(key,initial) if student else '未分配'
                    color={'已交':'green','未交':'red','待复核':'amber'}.get(current,'gray')
                    name=html.escape(student['name']) if student else '未分配'
                    st.markdown(f'<div class="pocket {color}"><b>{number}</b>{name}</div>',unsafe_allow_html=True)
                    if student:
                        state=st.selectbox(f'{number} {student["name"]} 状态',store.STATES,index=store.STATES.index(initial),label_visibility='collapsed',key=key)
                        records.append(dict(sid=student['sid'],name=student['name'],pocket=number,prediction=pocket['prediction'],score=pocket['score'],state=state,note=note or saved_notes.get(student['sid'],'')))
                    else: st.caption('不参与统计')
        unassigned=[r for r in roster if r['pocket'] is None]
        if unassigned:
            st.warning(f'{len(unassigned)} 名学生尚未分配袋号，不自动判为未交。可在名单管理中指定袋号。')
            for student in unassigned:
                initial=saved_states.get(student['sid'],'待复核')
                state=st.selectbox(f'{student["name"]}（{student["sid"]}，待分配）',store.STATES,index=store.STATES.index(initial),key=f'unassigned_{signature}_{revision}_{student["sid"]}')
                records.append(dict(sid=student['sid'],name=student['name'],pocket=None,prediction='unassigned',score=0,state=state,note=note or saved_notes.get(student['sid'],'')))
        missing=[r['name'] for r in records if r['state']=='未交']
        st.write('待确认未交：'+('、'.join(missing) or '无'))
        st.caption(f'{len(records)} 名学生 · {len(missing)} 人未交 · {sum(r["state"]=="待复核" for r in records)} 人待复核')
        confirm=st.checkbox('我已核对本次结果，未解决的待复核项暂不计入未交',key='confirm_'+signature+'_'+str(revision))
        if st.button('确认保存',type='primary',disabled=not confirm,width='stretch'):
            photos=folder/'photos'; photos.mkdir(parents=True,exist_ok=True)
            photo=hashlib.sha256(raw).hexdigest()+'.jpg'
            Image.fromarray(im).save(photos/photo,quality=95)
            try:
                store.save_session(db,cls,term,day.isoformat(),lesson,photo,result,records)
                notify('saved')
            except ValueError as e: st.error(str(e))

def roster_page():
    st.title('名单管理')
    st.caption('可导入多个班级。同班按学号合并更新，文件未包含的学生保留；不同班级的袋号互不冲突。')
    upload=st.file_uploader('导入花名册 Excel',type=['xlsx'])
    if upload:
        try:
            incoming=store.read_roster(upload.getvalue())
            mode=st.radio('导入归班方式',['按表内班级拆分','统一归入当前教学班（含转专业学生）'])
            if mode.startswith('统一'):
                default=max({r['class_name'] for r in incoming},key=lambda c:sum(r['class_name']==c for r in incoming))
                target=st.text_input('当前教学班名称',default)
                incoming=store.assign_current_class(incoming,target)
                if any(r['pocket'] is None for r in incoming): st.warning('尾号冲突的转入学生已留空袋号，请导入后在下表分配。')
            st.dataframe(pd.DataFrame(incoming).rename(columns={'class_name':'当前班级','sid':'学号','name':'姓名','pocket':'袋号','source_class':'表内原班级'}),hide_index=True)
            st.write(f'{len(incoming)} 名学生，{len({r["class_name"] for r in incoming})} 个班级')
            if st.button('确认导入班级和学生',type='primary'):
                store.import_roster(db,incoming); notify('imported')
        except Exception as e: st.error(f'导入失败：{e}')
    st.subheader(cls)
    query=st.text_input('查找学生（姓名或学号）')
    rows=store.students(db,cls)
    if query: st.dataframe([r for r in rows if query in r['name'] or query in r['sid']],hide_index=True)
    st.caption('下表可修改姓名和袋号。袋号留空表示待分配，可交换但不能重复。转专业学生归入当前班级，原班级只作备注。')
    edited=st.data_editor(pd.DataFrame(rows)[['sid','name','pocket','source_class']],hide_index=True,disabled=['sid','source_class'],column_config={'sid':'学号','name':'姓名','pocket':st.column_config.TextColumn('袋号',help='01～54，两位数字，或留空待分配'),'source_class':'表内原班级'},key='roster_'+cls+space_key,width='stretch')
    if st.button('保存名单修改'):
        try:
            changed=[dict(class_name=cls,sid=str(r.sid),name=str(r.name).strip(),pocket=None if pd.isna(r.pocket) or not str(r.pocket).strip() else str(r.pocket).strip().zfill(2),source_class=r.source_class) for r in edited.itertuples()]
            store.import_roster(db,changed); notify('roster')
        except ValueError as e: st.error(str(e))

def history_page():
    st.title('历史记录')
    sessions=[dict(r) for r in db.execute('SELECT * FROM sessions WHERE class=? ORDER BY day DESC,id DESC',(cls,))]
    if not sessions: st.info('本班还没有保存的课次。'); return
    selected=st.selectbox('选择课次',[s['id'] for s in sessions],format_func=lambda v:next(f'{s["day"]} · {s["lesson"]} · {s["term"]}' for s in sessions if s['id']==v))
    session=next(s for s in sessions if s['id']==selected)
    rows=[dict(r) for r in db.execute('SELECT * FROM records WHERE session=? ORDER BY pocket',(selected,))]
    with st.expander('查看当次照片与识别标定'):
        path=folder/'photos'/session['photo']
        if path.exists(): st.image(str(path),width=420)
        st.json(json.loads(session['calibration']),expanded=False)
    display=pd.DataFrame(rows)[['sid','name','pocket','prediction','state','note']]
    edited=st.data_editor(display,disabled=['sid','name','pocket','prediction'],column_config={'sid':'学号','name':'姓名','pocket':'当次袋号','prediction':'原始预测','state':st.column_config.SelectboxColumn('最终状态',options=store.STATES,required=True),'note':'备注'},hide_index=True,key=f'history_{space_key}_{selected}',width='stretch')
    if st.button('保存历史修正',type='primary'):
        try:
            for old,new in zip(rows,edited.to_dict('records')):
                if old['state']!=new['state'] or old['note']!=new['note']:
                    store.correct_record(db,selected,new['sid'],new['state'],new['note'] or '')
            notify('history')
        except ValueError as e: st.error(str(e))
    with st.expander('修改日志'):
        st.dataframe([dict(r) for r in db.execute('SELECT time,sid,before,after FROM audit WHERE session=? ORDER BY id DESC',(selected,))],hide_index=True)

def export_page():
    st.title('统计导出')
    all_classes=st.checkbox('汇总所有班级')
    filter_cls=None if all_classes else cls
    terms=[r[0] for r in db.execute('SELECT DISTINCT term FROM sessions ORDER BY term DESC')]
    term=st.selectbox('学期',['全部学期']+terms)
    a,b=st.columns(2)
    start=a.date_input('开始日期',date(date.today().year,1,1))
    end=b.date_input('结束日期',date.today())
    if start>end: st.error('开始日期不能晚于结束日期。'); return
    rows=store.details(db,filter_cls,None if term=='全部学期' else term,start.isoformat(),end.isoformat())
    totals=store.summary(db,rows,filter_cls)
    st.caption(f'{space} · {"所有班级" if all_classes else cls} · {len(rows)} 条学生课次明细')
    st.dataframe(totals,hide_index=True,width='stretch')
    filters=f'{space}；{filter_cls or "所有班级"}；{term}；{start} 至 {end}；未交次数仅统计最终状态为未交的记录'
    st.download_button('导出 Excel 汇总与明细',store.export_excel(totals,rows,filters),file_name=f'手机上交统计_{space}_{start}_{end}.xlsx',mime='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',type='primary')
    with st.expander('备份当前数据空间'):
        st.caption('备份包括当前数据库与照片。恢复时先停止程序，再将压缩包解压到相同数据空间目录。')
        if st.button('生成备份'):
            backup=io.BytesIO()
            # Use SQLite backup API to obtain a consistent snapshot.
            import sqlite3,tempfile
            with tempfile.TemporaryDirectory() as tmp:
                target=sqlite3.connect(str(Path(tmp)/'records.sqlite3')); db.backup(target); target.close()
                with zipfile.ZipFile(backup,'w',zipfile.ZIP_DEFLATED) as archive:
                    archive.write(Path(tmp)/'records.sqlite3','records.sqlite3')
                    for p in (folder/'photos').glob('*'): archive.write(p,'photos/'+p.name)
            st.download_button('下载备份 ZIP',backup.getvalue(),file_name=f'{space_key}_backup.zip')

try:
    {'本次识别':recognition_page,'名单管理':roster_page,'历史记录':history_page,'统计导出':export_page}[page]()
finally:
    db.close()
