"""Generate fictitious rosters and geometric image fixtures; no real student data."""
from pathlib import Path
import json
from PIL import Image, ImageDraw
from openpyxl import Workbook

ROOT=Path(__file__).resolve().parents[1]
names=['张三','李四','王五','赵六','钱七','孙八']
rows=[]
for class_num,cls in enumerate(['示例一班','示例二班'],1):
    book=Workbook(); sheet=book.active; sheet.title='虚拟名单'
    sheet.append(['学号/工号','姓名','班级','袋号'])
    for index,name in enumerate(names,1):
        sid=f'9000{class_num:02}{index:02}'
        row=dict(class_name=cls,sid=sid,name=name,pocket=f'{index:02}',source_class=cls)
        rows.append(row); sheet.append([sid,name,cls,row['pocket']])
    path=ROOT/'desktop/samples'/('roster.xlsx' if class_num==1 else 'roster_2.xlsx')
    path.parent.mkdir(parents=True,exist_ok=True); book.save(path)
for path in [ROOT/'android/app/src/main/assets/rosters.json',ROOT/'android/app/src/test/resources/rosters.json']:
    path.parent.mkdir(parents=True,exist_ok=True); path.write_text(json.dumps(rows,ensure_ascii=False,indent=2)+'\n')
for variant in range(1,8):
    im=Image.new('RGB',(800,1280),'#f6f5f0'); d=ImageDraw.Draw(im)
    d.text((100,40),'SYNTHETIC FIXTURE - NOT A REAL PHOTO',fill='#142437')
    d.rectangle((100,100,700,1180),fill='#143044',outline='#00bde0',width=8)
    for r in range(9):
        center=165+r*114
        for c in range(6):
            x=100+c*100; num=r*6+c+1
            d.rectangle((x+5,center-10,x+95,center+20),fill='#ffe000')
            empty=num in {8,13,17,19,20,22,33,34,36,40,44,48,51,53,54} or (variant%2==0 and num==32)
            if not empty: d.rounded_rectangle((x+20,center-42,x+80,center+18),radius=6,fill='#101117',outline='#44464e',width=2)
            d.rectangle((x+3,center+17,x+97,center+23),fill='#00bde0')
            d.text((x+43,center+48),f'{num:02}',fill='white')
    for c in range(7): d.line((100+c*100,100,100+c*100,1180),fill='#00bde0',width=6)
    im.save(ROOT/f'desktop/samples/sample_{variant}.jpg',quality=95)
    if variant==1: im.save(ROOT/'android/app/src/main/assets/示例照片.jpg',quality=95)
print('Generated 12 fictional students and synthetic image fixtures.')
