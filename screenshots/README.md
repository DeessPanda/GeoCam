# Screenshots

Screenshots for the store listing. Reference them from the Screenshots table
in the top-level README.

## Before you capture anything: your location is in the picture

This app exists to show where a photo was taken, so a screenshot taken at
home reveals exactly that. The mini-map tile is the clearest giveaway, since
it draws the actual street you are standing on. The coordinates and place
name are also drawn as text.

These files end up in a public Git repository, and later in the F-Droid and
Play Store listings, permanently and publicly indexed. Treat a screenshot as
a public post.

**Mock your location before capturing.** Developer options has a "select mock
location app" setting; point it somewhere neutral and the screenshot will show
a believable map with no disclosure. Shooting somewhere public and generic
works too.

Never capture your home address, your exact coordinates, or anything you
would not want indexed.

## Also: do not upload real GeoCam photos

A screenshot does not carry EXIF from the photo being previewed, so those are
fine. An actual photo taken with GeoCam does contain GPS coordinates in its
EXIF, and that data survives cropping, resizing and re-saving.

If you want to share a real photo, use the app's own privacy option:
Settings -> Privacy -> **Save without location**, which writes a second copy
with no GPS written into it at all.

## Naming

- `main.png` — camera screen with the overlay
- `gallery.png` — gallery preview with info / share / delete
- `settings.png` — settings screen

Capture at 1080x2400 or larger.
