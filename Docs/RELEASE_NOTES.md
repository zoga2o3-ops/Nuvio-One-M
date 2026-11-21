# Release notes

## 1.1.0-beta1.2

### Live TV recording

- Recordings on the SMB share now appear on your phone, alongside the recordings made on the TV and Windows.
- Both recording layouts are read, so existing TV and phone folders stay visible.
- **Combine** joins a finished recording's HLS pieces into one MPEG-TS file without re-encoding, with a progress card.
- The combined file goes to an `Exports` folder on the share by default. Recordings kept on the phone or a USB drive can also be saved next to the original.
- A recording that is still being made on another device is marked as such and cannot be stopped or deleted from the phone.
- Better compatibility with SMB servers that leave signing optional, and with info files saved with a byte-order mark.

### Diagnostics

- The **Send debug report** button in Settings → Advanced now includes a short account support code.
- Support can match the code to your Nuvio account without the account ID appearing in the report.

### Localization

- Live TV, recording and Combine are localized into German, Spanish, French, Italian, Dutch, Polish, Portuguese, Russian and Turkish.

## 0.6.6-beta3

- Stalker portal support for Live TV: add a portal address and MAC in Sources, and its channels, genres and guide load like any other source.
- Not covered yet: movies and series, catch-up, and portals that ask for a login. Recording, the pause buffer and casting are the least tested.

## 0.6.6-beta2

- Fixed YouTube sign-in and browsing failing in release builds with "Unable to create call adapter".

## 0.6.6-beta1

First release of NuvioM + IPTV + 1Tube: Nuvio Mobile with Live TV, On Demand, Sports, a Calendar, YouTube and casting.

**Live TV**
- M3U and Xtream playlists, XMLTV guides, groups of your own, channel editing, list and grid views, a guide grid, search by channel or programme.
- Recording (now, scheduled, series), reminders, sleep timer, pause and rewind buffer, channel surfing, an in-player channel list.
- A parental PIN for groups, with a setting for how long a group stays unlocked.
- Google Drive backup and restore of your setup.

**On Demand**
- Movies and series as rows, search, genres, sorting, versions, episode picker, continue watching, manage groups, Fix poster.
- "Watch On Demand" in Nuvio's stream list.

**Sports, Calendar, YouTube**
- Scores, My Teams, tables, a video window, reminders for followed teams.
- Upcoming, recent, today and following; movie release dates; New episodes on Home.
- A YouTube tab with sound tracks that start on the original language.

**Casting**
- Google Cast for movies, series, live channels and YouTube.

**Other**
- Simkl sign-in by code. Signing out of Nuvio clears Live TV and the Drive link.
