from PIL import Image

src = Image.open(
    r"C:\Users\13423\.dsh\attachments\v1\objects\17\178147e5e5f5482a84b32b3ba4ae738bd3146d2f751156c862484240acdaf705"
).convert("RGBA")
bbox = src.split()[-1].getbbox()
pad = 12
box = (
    max(0, bbox[0] - pad),
    max(0, bbox[1] - pad),
    min(src.width, bbox[2] + pad),
    min(src.height, bbox[3] + pad),
)
im = src.crop(box)
im.thumbnail((300, 300), Image.LANCZOS)
canvas = Image.new("RGBA", (320, 320), (0, 0, 0, 0))
canvas.paste(im, ((320 - im.width) // 2, (320 - im.height) // 2), im)
canvas.save(r"E:\DataAgent\dataagent\dsh-plugin\dragon-pet.png", optimize=True)
print(canvas.size)
