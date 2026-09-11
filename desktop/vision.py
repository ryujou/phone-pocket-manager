"""Deterministic pocket recognizer. Scores are rule scores, not probabilities."""
import io
import cv2
import numpy as np
from PIL import Image, ImageOps

VERSION = 'yellow-band-v1'
W, H = 600, 1080

def read_image(source):
    if isinstance(source, bytes):
        source = io.BytesIO(source)
    im = ImageOps.exif_transpose(Image.open(source)).convert('RGB')
    im.thumbnail((1600, 2000))
    return np.asarray(im).copy()

def masks(im):
    hsv = cv2.cvtColor(im, cv2.COLOR_RGB2HSV)
    blue = cv2.inRange(hsv, (85, 75, 45), (125, 255, 255))
    yellow = cv2.inRange(hsv, (16, 90, 65), (40, 255, 255))
    return blue, yellow

def locate(im):
    blue, _ = masks(im)
    mask = cv2.morphologyEx(blue, cv2.MORPH_CLOSE, np.ones((9,9), np.uint8))
    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not contours:
        raise ValueError('没有找到蓝色袋体，请手动标定四角。')
    c = max(contours, key=cv2.contourArea)
    if cv2.contourArea(c) < im.shape[0]*im.shape[1]*.12:
        raise ValueError('袋体不完整或太小，请重新拍摄或手动标定。')
    hull = cv2.convexHull(c)
    p = cv2.approxPolyDP(hull, .025*cv2.arcLength(hull, True), True).reshape(-1,2)
    if len(p)!=4:
        p=hull.reshape(-1,2)
        p=np.array([p[np.argmin(p.sum(1))],p[np.argmin(np.diff(p,axis=1)[:,0])],p[np.argmax(p.sum(1))],p[np.argmax(np.diff(p,axis=1)[:,0])]])
    return order_corners(p).tolist()

def order_corners(points):
    p=np.asarray(points,dtype=np.float32)
    return np.array([p[np.argmin(p.sum(1))],p[np.argmin(p[:,1]-p[:,0])],p[np.argmax(p.sum(1))],p[np.argmax(p[:,1]-p[:,0])]],np.float32)

def rectify(im, points):
    p=np.array(points,dtype=np.float32)
    if p.shape!=(4,2) or len(np.unique(p,axis=0))!=4:
        raise ValueError('需要四个不同的角点。')
    if not cv2.isContourConvex(p.astype(np.int32)) or cv2.contourArea(p)<im.shape[0]*im.shape[1]*.05:
        raise ValueError('请按左上、右上、右下、左下顺序选择完整袋体。')
    if np.any(p<0) or np.any(p[:,0]>=im.shape[1]) or np.any(p[:,1]>=im.shape[0]):
        raise ValueError('角点超出图片。')
    matrix=cv2.getPerspectiveTransform(p,np.float32([[0,0],[W-1,0],[W-1,H-1],[0,H-1]]))
    return cv2.warpPerspective(im,matrix,(W,H))

def default_grid(warped):
    _, y=masks(warped)
    projection=(y>0).mean(1)
    smooth=np.convolve(projection,np.ones(9)/9,mode='same')
    rows=[]
    for i in range(9):
        expected=int(65+i*114)
        lo,hi=max(8,expected-30),min(H-20,expected+31)
        rows.append(int(lo+np.argmax(smooth[lo:hi])))
    return {'rows':rows,'cols':list(range(0,601,100))}

def recognize(im, corners=None, grid=None):
    corners=locate(im) if corners is None else corners
    warped=rectify(im,corners)
    grid=default_grid(warped) if grid is None else grid
    rows=list(map(int,grid['rows'])); cols=list(map(int,grid['cols']))
    if len(rows)!=9 or len(cols)!=7 or any(b-a<20 for a,b in zip(rows,rows[1:])) or any(b-a<20 for a,b in zip(cols,cols[1:])) or min(rows)<5 or max(rows)>H-10 or min(cols)<0 or max(cols)>W:
        raise ValueError('需要 9 条递增袋口线和 7 条递增列边界，且不能超出校正图。')
    blue,yellow=masks(warped)
    p=np.asarray(corners)
    clipped=bool(np.any(p[:,0]<=2) or np.any(p[:,1]<=2) or np.any(p[:,0]>=im.shape[1]-3) or np.any(p[:,1]>=im.shape[0]-3))
    output=[]
    for r, center in enumerate(rows):
        for col in range(6):
            left,right=cols[col:col+2]; pad=int((right-left)*.14)
            x0,x1=left+pad,right-pad
            # Find the local yellow backing strip in each pocket, allowing fabric sag.
            ya,yb=max(0,center-16),min(H,center+17)
            strip=(yellow[ya:yb,x0:x1]>0).astype(float)
            band=np.convolve(strip.mean(1),np.ones(5)/5,mode='same')
            peak=int(np.argmax(band))+ya
            # Locate the blue pocket lip and inspect the backing immediately above
            # it, so a short phone below the upper yellow band is not missed.
            ba,bb=max(0,center-5),min(H,center+43)
            bproj=(blue[ba:bb,x0:x1]>0).mean(1)
            bsmooth=np.convolve(bproj,np.ones(3)/3,mode='same')
            lip=ba+int(np.argmax(bsmooth))
            fraction=float((yellow[max(0,lip-13):max(1,lip-3),x0:x1]>0).mean())
            crop=[x0,max(0,center-65),x1,min(H,center+28)]
            local_blue=float((blue[max(0,center-8):center+30,left:right]>0).mean())
            edges=cv2.Canny(warped[crop[1]:crop[3],x0:x1],60,140)
            edge=float((edges>0).mean())
            state='empty' if fraction>=.65 else ('phone' if fraction<=.40 else 'review')
            if clipped or local_blue<.015 or np.std(warped[crop[1]:crop[3],x0:x1])<8:
                state='review'
            output.append(dict(pocket=f'{r*6+col+1:02}',prediction=state,score=round(fraction,4),bbox=crop,band_y=peak,edge=round(edge,4)))
    return dict(version=VERSION,corners=corners,quality_warning='袋体边界接近图片边缘，请检查是否拍全；本次全部待复核。' if clipped else '',grid={'rows':rows,'cols':cols},pockets=output),warped

def overlay(warped,result):
    canvas=warped.copy()
    colors={'phone':(20,155,105),'empty':(222,70,70),'review':(229,169,30)}
    for p in result['pockets']:
        x0,y0,x1,y1=p['bbox']; color=colors[p['prediction']]
        cv2.rectangle(canvas,(x0,y0),(x1,y1),color,2)
        cv2.rectangle(canvas,(x0,y1-19),(x0+27,y1),color,-1)
        cv2.putText(canvas,p['pocket'],(x0+2,y1-4),cv2.FONT_HERSHEY_SIMPLEX,.45,(255,255,255),1)
    return canvas
