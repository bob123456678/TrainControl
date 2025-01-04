import requests
import os
import shutil

"""
This script uses imagemagick to download and convert CS3 SVG icons to PNG
"""

cs3_ip = '192.168.50.25'
# color = b'#808080' # CS2 gray
# color = b'#010101' # CS3 black
color = b'#222222'  # TC gray

active = False

# There are currently 296 icons
for i in range(1, 297):

    icon_index = str(i).zfill(3)
    icon_index_out = str(i).zfill(2)

    letter = 'a' if active else 'i'

    dest = "FktIcon_" + letter + "_gr_" + icon_index_out + ".png"

    try:

        svg_url = 'http://' + cs3_ip + '/app/assets/fct/fkticon_' + letter + '_' + icon_index + '.svg'
        response = requests.get(svg_url)

        data = response.content.replace(b'#010101', color)

        open('input.svg', 'wb').write(data)

        os.system("convert -background none -resize 48x48 input.svg output.png")

        if os.path.exists(dest):
            os.remove(dest)

        os.rename("output.png", dest)

        print("Processed FktIcon_" + letter + "_gr_" + icon_index_out + ".png")


    except Exception as e:

        print(e)

        print("Icon " + icon_index + " failed ")

        # Copy blank icon if missing/failed
        if os.path.exists("FktIcon_i_gr_00.png"):
            shutil.copy2("FktIcon_i_gr_00.png", dest)

