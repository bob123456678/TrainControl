# cs3-main-layout

**A snapshot of Adam's CS3 (Central Station 3 plus, firmware 2.6.1), taken 2026-11-14.** It is the test case for
OB-465: a downloaded CS3 layout must keep each square's protocol where one address has an MM2 and a DCC accessory.

Adam: *"for our validation, use the current Main Layout on the CS3 as your test case.  snapshot its config files"*

## Where it came from

Read once over HTTP from the station, read-only, exactly as served:

- `config/`: `magnetartikel.cs2`, `gleisbild.cs2`, `fahrstrassen.cs2` and the four pages in `config/gleisbilder/`.
- `app/api/`: `gbs` (the CS3's own plans), `mags`, `mags/state` and `info`, saved with a `.json` suffix.

`app/api/info.json` is the one file changed: its serial number, host name and two device UIDs are replaced with
placeholders. Firmware, hardware and model are as served.

## Invariants: a test may rely on these

- **The four pages**, in order: `Startpackung`, `Main`, `Something`, `Main Layout`. `Main Layout` is a grid page
  (`stellpult` in `gbs`), and its plan sits four columns off the page file's squares.
- **Two addresses with two protocols**, which the page files can't tell apart (each square carries only `.artikel`):
  - **25:** `W 14` (DCC) at row 7, column 4 of `Main Layout`, and `W 15` (MM2) at row 9, column 5 of the same page;
    both are left turnouts stored as `artikel=50`.
  - **1:** `W 11` (DCC) on `Main Layout`, and `W 1R` (MM2) on `Startpackung`.
- **Both turnouts at 25 were straight** when the snapshot was taken. `magnetartikel.cs2` records `W 14` with
  `stellung=129`.
- **Routes:** none. `fahrstrassen.cs2` is empty.

Read from the station, TrainControl tells each pair apart through `gbs` (OB-402). A copy made with Download Central
Station Layout Files must keep the same protocol on every square (OB-465).

## Not invariants: set these in code

- **Switch states other than the two at 25.** They are whatever the station showed that day; a test that needs a state sets it.
- **Locomotives, lengths and the autonomy setup.** The snapshot has none of these; a test creates its own.
- **The station's serial number, host name and IDs** in `app/api/info.json` are redacted placeholders, not real values.

## How tests use it

Serve it from a stand-in CS3, or copy it to a sandbox first. Never write to this folder.

## Used by

- `regression.testADownloadedCS3LayoutKeepsEachSquaresProtocol`
