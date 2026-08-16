from PIL import Image, ImageDraw, ImageFont
import os

D = r'src/org/traincontrol/gui/resources/icons60'
OUT = r'C:/Users/adamo/AppData/Local/Temp/claude/C--Users-adamo-Downloads-ClaudeProjects/c317edda-1b03-44c7-aedc-07c86a7147df/scratchpad'

P = {'N': (30, 0), 'E': (60, 30), 'S': (30, 60), 'W': (0, 30)}

TILES = [
 ('straight',            'STRAIGHT',              [('E','W')], [], ''),
 ('curve',               'CURVE',                 [('E','S')], [], ''),
 ('curve_parallel',      'DOUBLE_CURVE',          [('N','W'),('E','S')], [], ''),
 ('s88',                 'FEEDBACK',              [('E','W')], [], 'always a Point'),
 ('s88_curve',           'FEEDBACK_CURVE',        [('E','S')], [], 'always a Point'),
 ('s88_double_curve',    'FEEDBACK_DOUBLE_CURVE', [('N','W'),('E','S')], [], 'always a Point'),
 ('signal',              'SIGNAL',                [('E','W')], [], 'straight-through'),
 ('decouple',            'UNCOUPLER',             [('E','W')], [], ''),
 ('end',                 'END',                   [('N',)], [], 'terminates'),
 ('tunnel',              'TUNNEL',                [('S',)], [], '+ paired portal'),
 ('link',                'LINK',                  [('W',)], [], 'side UNCONFIRMED'),
 ('cross',               'CROSSING',              [('N','S'),('E','W')], [], '1 orientation'),
 ('overpass',            'OVERPASS',              [('N','S'),('E','W')], [], 'grade separated'),
 ('switch_left',         'SWITCH_LEFT',           [('N','S')], [('S','W')], ''),
 ('switch_right',        'SWITCH_RIGHT',          [('N','S')], [('S','E')], ''),
 ('switch_y',            'SWITCH_Y',              [('S','W')], [('S','E')], 'no straight'),
 ('threeway',            'SWITCH_THREE',          [('N','S')], [('S','W'),('S','E')], '2 addresses'),
 ('crossswitch',         'SWITCH_CROSSING',       [('N','S'),('E','W')], [('N','W'),('S','E')], 'double slip'),
 ('custom_perm_left',    'CUSTOM_PERM_LEFT',      [('N','S'),('S','W')], [], 'INTO S ONLY'),
 ('custom_perm_right',   'CUSTOM_PERM_RIGHT',     [('N','S'),('S','E')], [], 'INTO S ONLY'),
 ('custom_perm_y',       'CUSTOM_PERM_Y',         [('S','W'),('S','E')], [], 'INTO S ONLY'),
 ('custom_perm_threeway','CUSTOM_PERM_THREEWAY',  [('N','S'),('S','W'),('S','E')], [], 'INTO S ONLY'),
 ('custom_scissors',     'CUSTOM_SCISSORS',       [], [], 'DISQUALIFIED'),
 ('custom_perm_scissors','CUSTOM_PERM_SCISSORS',  [], [], 'DISQUALIFIED'),
 ('turntable',           'TURNTABLE',             [], [], 'TERMINATOR'),
 ('lamp',                'LAMP',                  [], [], 'decorative'),
 ('route',               'ROUTE',                 [], [], 'transparent: neighbours decide'),
 ('text',                'TEXT',                  [], [], 'decorative'),
]

# distinct shades so overlapping paths stay separable
GREENS = [(0, 160, 0), (130, 210, 0), (0, 140, 110), (90, 240, 140)]
REDS   = [(215, 0, 0), (255, 120, 0), (170, 0, 90)]

S = 5
TILE = 60 * S
LBL = 52
CELL_W, CELL_H = TILE + 18, TILE + LBL + 14
COLS = 4
ROWS = (len(TILES) + COLS - 1) // COLS

def font(sz):
    for f in ('arial.ttf', 'segoeui.ttf'):
        try: return ImageFont.truetype(f, sz)
        except Exception: pass
    return ImageFont.load_default()

F_HEAD, F_NAME, F_NOTE = font(20), font(21), font(16)

def pt(side):
    x, y = P[side]
    return (x * S, y * S)

def draw_conn(d, pair, color):
    """Direct side-to-side line: a curve reads as a corner-cutting diagonal,
    so two independent curves in one tile never overlap."""
    if len(pair) == 1:
        a = pt(pair[0]); c = (30*S, 30*S)
        d.line([a, c], fill=(255,255,255), width=11)
        d.line([a, c], fill=color, width=6)
        x, y = a; r = 8
        d.ellipse([x-r, y-r, x+r, y+r], fill=color)
    else:
        a, b = pt(pair[0]), pt(pair[1])
        d.line([a, b], fill=(255, 255, 255), width=11)   # halo keeps crossings legible
        d.line([a, b], fill=color, width=6)

def arrow_into_S(d, color):
    x, y = pt('S'); k = 15
    d.polygon([(x, y-5), (x-k, y-5-k), (x+k, y-5-k)], fill=color)

sheet = Image.new('RGB', (COLS*CELL_W, ROWS*CELL_H + 70), (250, 250, 250))
sd = ImageDraw.Draw(sheet)
sd.text((14, 8), 'Port map at orientation 0  -  lines run DIRECTLY between the two sides, so a curve '
                 'shows as a corner-cutting diagonal', fill=(30,30,30), font=F_HEAD)
sd.text((14, 34), 'GREEN shades = unswitched (default), one shade per distinct path   |   '
                  'RED/ORANGE shades = switched   |   blue arrow = trailing-only   |   dot = single open port',
        fill=(30,30,30), font=F_HEAD)

for i, (icon, label, uns, sw, note) in enumerate(TILES):
    cell = Image.new('RGB', (CELL_W, CELL_H), (250, 250, 250))
    cd = ImageDraw.Draw(cell)
    art = Image.open(os.path.join(D, icon + '.gif')).convert('RGB').resize((TILE, TILE), Image.NEAREST)
    ov = Image.new('RGB', (TILE, TILE), (255, 255, 255)); ov.paste(art, (0, 0))
    od = ImageDraw.Draw(ov)
    for j, p in enumerate(uns): draw_conn(od, p, GREENS[j % len(GREENS)])
    for j, p in enumerate(sw):  draw_conn(od, p, REDS[j % len(REDS)])
    if note == 'INTO S ONLY': arrow_into_S(od, (0, 90, 220))
    cell.paste(ov, (9, 8))
    cd.rectangle([9, 8, 9+TILE, 8+TILE], outline=(170, 170, 170))
    cd.text((9, TILE + 14), label, fill=(20, 20, 20), font=F_NAME)
    legend = '  '.join(f"{'-'.join(p)}" for p in uns) + ('   ' if uns and sw else '') + \
             '  '.join(f"{'-'.join(p)}*" for p in sw)
    if legend.strip():
        cd.text((9, TILE + 36), legend.strip(), fill=(90, 90, 90), font=F_NOTE)
    if note:
        col = (215,0,0) if note in ('DISQUALIFIED','TERMINATOR','INTO S ONLY','side UNCONFIRMED') else (120,120,120)
        w = cd.textlength(note, font=F_NOTE)
        cd.text((CELL_W - w - 12, TILE + 36), note, fill=col, font=F_NOTE)
    sheet.paste(cell, ((i % COLS)*CELL_W, (i // COLS)*CELL_H + 70))

path = os.path.join(OUT, 'portmap_verification_v2.png')
sheet.save(path)
print(path, sheet.size)
