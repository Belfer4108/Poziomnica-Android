# Plan testow modulow

Ten plik sluzy do sprawdzania aplikacji przed wypchnieciem projektu na GitHub i przed przygotowaniem release.

## 1. Poziomnica

- Telefon lezy na tylnej obudowie.
- Telefon stoi na lewej krawedzi.
- Telefon stoi na prawej krawedzi.
- Telefon stoi na gornej krawedzi.
- Telefon stoi na dolnej krawedzi.
- Po obrocie ekranu wynik, belka i przyciski sa widoczne bez nadmiernego przewijania.
- HOLD zatrzymuje wynik.
- Zapis pokazuje potwierdzenie.
- Cel mozna ustawic w aktualnej jednostce.
- Dzwiek i wibracja reaguja po osiagnieciu tolerancji.

## 2. Pion

- Pomiar dziala na bocznych krawedziach.
- Komunikat `Pion osiagniety` pojawia sie w tolerancji.
- Przyciski sa dostepne w pionie i poziomie.
- Zapis trafia do historii z polska nazwa typu pomiaru.

## 3. Poziomowanie powierzchni

- Babel porusza sie w osi X i Y.
- Centralny punkt jest widoczny przez babel.
- Babel zmienia kolor na zielony po wypoziomowaniu.
- Opis `Zero` i zerowanie sa zrozumiale po uzyciu.
- Zapis trafia do historii.

## 4. Spadek

- Wlasna wartosc celu dziala dla stopni, procentow, mm/m i cm/m.
- Przyciski kierunku nie lamia sie pionowo.
- Komunikaty `Za maly spadek`, `Za duzy spadek`, `Spadek prawidlowy` sa zgodne z pomiarem.
- Zapis trafia do historii.

## 5. Katomierz

- Przycisk bazy zapisuje aktualna pozycje jako zero odniesienia.
- Przycisk nachylenia zapisuje drugi pomiar.
- Wynik pokazuje kat miedzy baza i nachyleniem.
- Przycisk reset pozwala wykonac nowy pomiar.
- Babel porusza sie zgodnie z katem.
- Przyciski sa blisko wskaznika, a ustawianie celu nizej.

## 6. Aparat

- Aplikacja prosi o aparat dopiero po wejsciu do trybu aparatu.
- Bez uprawnienia do aparatu reszta aplikacji dziala.
- Zdjecie zapisuje sie w galerii w folderze `Poziomnica`.
- Zdjecie wykonane pionowo nie zapisuje sie bokiem.
- Nakladki/przyciski nie sa zapisywane na zdjeciu, jesli wybrano zapis bez nakladki.
- Linie pomiarowe i siatka mozna wlaczac i wylaczac.
- Reczna linia z obrazu pokazuje kat wzgledem poziomu i pionu.

## 7. Historia

- Historia po pierwszej instalacji jest pusta.
- Pomiar mozna podejrzec przed udostepnieniem.
- Mozna usunac pojedynczy pomiar.
- Mozna zaznaczyc wiele pomiarow i usunac je z potwierdzeniem.
- Eksport dziala dla zaznaczonych pomiarow.
- Udostepnianie dziala dla PDF, CSV, tekstu i obrazu, jezeli pomiar ma zdjecie.

## 8. Kalibracja

- Wejscie w kalibracje nie zamyka aplikacji.
- Kalibracja tylnej obudowy 4 x 90 stopni zapisuje korekte.
- Kalibracja krawedzi 2 x 180 stopni zapisuje korekte.
- Korekty kalibracji dobieraja sie automatycznie wedlug tylnej obudowy albo aktualnie wykrytej krawedzi.
- Przywrocenie ustawien wymaga potwierdzenia.

## 9. Ustawienia

- Motyw jasny i ciemny przelacza sie poprawnie.
- Wybrany poziom wygladzania jest widocznie zaznaczony.
- Tolerancja jest widocznie zaznaczona.
- Reset ustawien wymaga potwierdzenia.
- Test dzwieku i test wibracji dziala.

## 10. Release

- `assembleDebug` konczy sie sukcesem.
- `.\scripts\test-local.ps1` albo testy w GitHub Actions koncza sie sukcesem.
- APK instaluje sie na telefonie.
- Wszystkie krytyczne moduly przechodza testy z tej listy.
