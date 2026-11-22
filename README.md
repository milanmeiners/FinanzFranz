# FinanzFranz

Eine schlichte Android-App, die zeigt, wie viel Geld auf dem Konto und im Portemonnaie ist – entwickelt mit KI als Coding-Partner.

**[⬇️ Neueste Version herunterladen (APK)](https://github.com/milanmeiners/FinanzFranz/releases/latest)**

## Funktionen

- **Kontostand & Bargeld** getrennt, dazu „noch verplant bis Monatsende“ und „frei verfügbar“
- **Buchungen** per Hand: Ausgaben (Konto oder bar), Einnahmen, Geld abheben (Konto → Bar)
- **Kassenbon scannen** – Summe, Datum, Laden und Bar/Karte werden offline auf dem Handy erkannt (ML Kit)
- **Feste Ausgaben & Einnahmen** (Miete, Abos, Versicherungen, Gehalt) – monatlich, vierteljährlich, halbjährlich oder jährlich; werden am Fälligkeitstag automatisch gebucht
- **Kategorien & Monatsbudgets** mit Hinweis bei 80 % und bei Überschreitung
- **Monatsbericht** mit Ausgaben je Kategorie
- **Monatsabgleich** mit dem echten Kontostand
- **Export** als CSV (Excel) und PDF, **Sicherung** als JSON – alle Daten bleiben lokal auf dem Gerät

## Installation

1. Lade die `.apk`-Datei unter [Releases](https://github.com/milanmeiners/FinanzFranz/releases/latest) auf dein Android-Handy herunter.
2. Öffne die Datei. Android fragt eventuell, ob du die Installation aus dieser Quelle erlauben möchtest – bestätige das.
3. Fertig – die App erscheint in deiner App-Übersicht.

> Die App ist nicht im Play Store, deshalb fragt Android einmalig nach der Erlaubnis.

## Technik

Kotlin · Jetpack Compose · Room · ML Kit Text Recognition (offline) · ViewModel + Repository

## Selbst bauen

```bash
./gradlew assembleDebug        # APK bauen
./gradlew testDebugUnitTest    # Tests (Logik, Bon-Erkennung, UI-Durchlauf mit Robolectric)
```
