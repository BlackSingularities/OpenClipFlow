## 1.2.2

- Nowa strona aplikacji z opisem funkcji.
- Jednolita informacja o autorze (Adam Rędzikowski · redzikowski.com) i przycisk „Postaw kawę”.
- Usunięte odnośniki do repozytorium z interfejsu.

## 1.2.1 — Transparent application icon

- Use the transparent application mark in launcher, splash and existing branding surfaces.
- Preserve alpha in legacy and adaptive Android icons; refresh themed icon resources.

# Historia zmian

## 1.2.0

- **Dwa ujęcia naraz – układy ekranu**: obok siebie, jedno nad drugim, okienko (obraz w obrazie) albo ćwiartki. Narzędzie „Układ” ustawia oba ujęcia spod głowicy jednym dotknięciem, a w zakładce Kadr każde ujęcie dostaje 13 gotowych położeń. Kadrowanie, zoom i obrót działają w obrębie swojej części ekranu.
- **Równorzędne ścieżki wideo**: „Wideo 1”, „Wideo 2”… (zamiast „nakładki”), oznaczenia V1/V2/A1 na osi czasu, pliki można dodać na dowolną ścieżkę z jej menu.
- **Ścieżki audio tylko dla dźwięku**: nie da się już upuścić na nie filmu ani zdjęcia. Dźwięk odłączony od wideo jest osobnym klipem dźwiękowym i można go przenosić tylko między ścieżkami audio.
- **Tło kadru**: czarne, w wybranym kolorze albo rozmyta kopia ujęcia (np. pionowe nagranie w filmie 16:9).
- **Lektor**: nagrywanie głosu z mikrofonu od miejsca głowicy, z odtwarzaniem filmu w tle; nagranie ląduje na wolnej ścieżce audio.
- **Stop-klatka**: zatrzymany kadr spod głowicy wstawiany jako 2-sekundowe ujęcie z tym samym filtrem i kadrem.

## 1.1.0

- **Przenikanie między ujęciami**: płynne przejście jednego klipu w drugi na głównej ścieżce (0,3–2 s). Punkty cięcia i długość filmu się nie zmieniają – przenikanie korzysta z zapasu materiału przed wejściem następnego i za wyjściem poprzedniego klipu (montaż A/B), dźwięk krzyżuje się razem z obrazem.
- Na osi czasu każde cięcie głównej ścieżki ma przycisk przejścia, a okno przenikania jest zaznaczone. Jednym dotknięciem można ustawić przenikanie na wszystkich cięciach.
- **Cięcie jednej ścieżki**: dotknięcie nagłówka ścieżki otwiera menu z „Podziel tę ścieżkę”; narzędzie „Podziel” pyta, czy ciąć wszystko, czy tylko wybraną ścieżkę, gdy głowica przecina kilka. Cięcie pojedynczej ścieżki także w panelu Ścieżki.
- Menu ścieżki: zaznacz wszystko na ścieżce, usuń luki, wycisz, ukryj, zablokuj, usuń.

## 1.0.0

Pierwsze pełne wydanie – OpenClipFlow staje się prawdziwym edytorem wideo.

- **Silnik Media3**: podgląd montażu z efektami GPU i eksport do MP4 (H.264 / H.265 + AAC) albo samego dźwięku (M4A); wynik trafia do galerii (Filmy/OpenClipFlow), można go udostępnić lub zapisać w wybranym miejscu.
- **Oś czasu**: stała głowica, przewijanie z rzutem, zoom szczypnięciem, pasek klatek na klipach, przebieg fali dźwięku, przycinanie uchwytami, przeciąganie między ścieżkami, przyciąganie do krawędzi, głowicy i znaczników.
- **Montaż**: rozcinanie, usuwanie z przesunięciem, duplikowanie, zamykanie luk, podmiana materiału, odłączanie dźwięku, znaczniki, cofnij/ponów, wielokrotne zaznaczanie.
- **Klipy**: prędkość 0,25–4× (z zachowaniem wysokości dźwięku), głośność do 200%, narastanie i wygaszanie dźwięku, 9 filtrów z intensywnością, korekcja (jasność, kontrast, nasycenie, temperatura, odcień, rozmycie), kadr (skala, obrót, przesunięcie, odbicia, wypełnienie), Ken Burns, przejścia (przez czerń/biel, zoom, wjazd).
- **Nakładki**: dodatkowe ścieżki obrazu – obraz w obrazie z kryciem, edycja kadru gestami w podglądzie.
- **Napisy**: style (cień, tło, kontur, belka, napisy), animacje (zanikanie, wjazd, wyskok, maszyna do pisania), kolory, wyrównanie, przeciąganie i skalowanie w podglądzie; w eksporcie wyglądają tak samo jak na ekranie.
- **Projekty**: autozapis, lista z miniaturami, zmiana nazwy, duplikowanie, plik projektu do kopii zapasowej, proporcje 16:9 / 9:16 / 1:1 / 4:5 / 4:3 / 21:9, rozdzielczość do 4K i 24–60 kl/s.
- **Interfejs w stylu OpenPDF**: czarno-biały, pigułkowe przyciski, panele boczne na tabletach i arkusze na telefonach, dymki po przytrzymaniu, haptyka.
- **Aktualizacje z aplikacji**: sprawdzanie GitHub Releases, pobieranie i instalacja nowej wersji jednym dotknięciem.
- **Podpis**: wydania są podpisane stałym kluczem, więc kolejne wersje instalują się na poprzednich (wcześniejsze APK były niepodpisane i nie dało się ich zainstalować).
