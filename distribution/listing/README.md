# Store listing copy

The text Google Play shows on the store page, kept here so it is reviewed in a pull request
rather than edited straight into the console and lost.

**These files are not uploaded by CI.** The release workflow only uploads the bundle and the
release notes in `distribution/whatsnew/`. Listing copy is pasted into Play Console by hand;
this directory is the source of truth for what was pasted.

| File | Play field | Limit |
| --- | --- | --- |
| `en-US/title.txt` | App name | 30 characters |
| `en-US/short-description.txt` | Short description | 80 characters |
| `en-US/full-description.txt` | Full description | 4000 characters |

Run `.github/scripts/check-listing.sh` to confirm each file is within its limit.

## Visual assets

In `en-US/graphics/`. The artwork masters live in `graphics/source/`; the files Play actually
wants are derived from them by `build-graphics.py`, so they are reproducible rather than
hand-cropped. Re-run it after changing the artwork or the wording on the feature graphic:

```sh
python3 distribution/listing/build-graphics.py
```

| Asset | File | Requirement |
| --- | --- | --- |
| App icon | `icon-512.png` | 512×512 PNG, under 1 MB, square — Play applies its own corner mask, so do not pre-round |
| Feature graphic | `feature-graphic-1024x500.png` | 1024×500, no transparency |
| Phone screenshots | not yet captured | 2–8, min 320px on the short edge, 16:9 or 9:16 |

Tablet, Chromebook and Android XR screenshots are optional.

### Screenshots

Not captured yet. When taking them, use a **throwaway key** — a fingerprint in a store
screenshot is public forever. Note also that privacy mode blocks screenshots on a real device,
so they have to come from an emulator or with privacy mode turned off.

### The launcher icon is not this icon

`en-US/graphics/icon-512.png` is the store listing icon. The icon inside the app is still
`app/src/main/res/mipmap-*/ic_launcher`, which is the unmodified Android Studio template — a
green robot. Replacing it is a separate change: adaptive icons need foreground and background
layers, not a single square image.
