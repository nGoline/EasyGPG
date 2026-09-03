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

Play also requires, and these are **not** in the repository because they are binaries:

| Asset | Size | Notes |
| --- | --- | --- |
| App icon | 512×512 PNG, 32-bit | No transparency, no rounded corners — Play applies the mask |
| Feature graphic | 1024×500 PNG or JPEG | Shown at the top of the listing; no transparency |
| Phone screenshots | 2–8, min 320px on the short edge | 16:9 or 9:16, PNG or JPEG |

Tablet, Chromebook and Android XR screenshots are optional.
