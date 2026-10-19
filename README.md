# OpenClipFlow

Edytor wideo na Androida (10+) w Kotlinie i Jetpack Compose, z silnikiem **Media3 Transformer**. Interfejs w stylu [OpenPDF](https://github.com/BlackSingularities/OpenPDF): czarno-biały, z pigułkowymi przyciskami i panelami bocznymi.

## Możliwości

- Wielościeżkowa oś czasu: główna ścieżka obrazu, nakładki (obraz w obrazie), dowolna liczba ścieżek audio i ścieżka napisów.
- Przycinanie, rozcinanie, przesuwanie między ścieżkami, przyciąganie, usuwanie z przesunięciem, duplikowanie, znaczniki, cofnij/ponów.
- Prędkość, głośność, wyciszanie, filtry, korekcja kolorów, rozmycie, kadr i obrót, Ken Burns, przejścia.
- Przenikanie między ujęciami (montaż A/B z zapasem materiału) i cięcie pojedynczej ścieżki.
- Napisy z animacjami – rysowane tym samym kodem w podglądzie i w eksporcie.
- Podgląd z efektami GPU (`CompositionPlayer`) i eksport MP4 H.264/H.265 lub M4A do 4K / 60 kl/s.
- Autozapis projektów, plik projektu `.ocf.json`, „Udostępnij do OpenClipFlow” z galerii.
- Aktualizacje z GitHub Releases bezpośrednio w aplikacji.

## Budowanie

```bash
./gradlew testDebugUnitTest :app:assembleRelease
```

Wymagany JDK 17 i Android SDK 36. Wynik: `app/build/outputs/apk/release/app-release.apk`.

### Podpis wydań

APK release jest podpisywany kluczem PKCS12:

- **CI (GitHub Actions)** – z sekretów `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_PASSWORD`, `SIGNING_KEY_ALIAS`; workflow przerywa wydanie, jeśli APK zostałby podpisany kluczem debug.
- **Lokalnie** – z katalogu `~/.openclipflow-signing/` (`release.p12` + `password.txt`).

To ten sam klucz, więc APK z GitHuba i zbudowany lokalnie aktualizują się nawzajem. Bez klucza (np. fork) build używa klucza debug.

## Wydania

Każdy push na `main` buduje, testuje i publikuje podpisane wydanie na GitHubie. Numer wersji pochodzi z pliku `VERSION`; gdy wydanie o tym numerze już istnieje, dostaje przyrostek z numerem budowania (np. `1.0.0.57`). Opis wydania jest brany z `CHANGELOG.md`.

## Ograniczenia

- Przenikanie wymaga zapasu materiału przed wejściem następnego albo za wyjściem poprzedniego klipu; gdy go brak, jest skracane (zdjęcia mają zapas nieograniczony).
- Podgląd nakładek i przenikań wymaga kompozytora wielu wejść; na urządzeniach, które go nie obsługują, podgląd pokazuje samą główną ścieżkę, a eksport i tak zawiera nakładki.
- Projekt przechowuje odnośniki do plików (URI). Jeśli dostawca plików odbierze dostęp, trzeba je zaimportować ponownie.
