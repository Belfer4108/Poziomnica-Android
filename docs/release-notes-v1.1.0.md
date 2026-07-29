# Poziomnica 1.1.0

## Najwazniejsze zmiany

- Poprawione uklady ekranow pomiarowych w pionie i poziomie.
- Dodana Miarka AR z tasma pomiarowa oparta o ARCore.
- Uporzadkowane menu, FAQ, ustawienia i opisy modulow.
- Dodane przeliczniki spadku, dlugosci i jednostek.
- Poprawione zapisywanie zdjec do Galerii w folderze `Poziomnica`.
- Wylaczony backup danych aplikacji, aby lokalne pomiary i kalibracje nie byly automatycznie przenoszone przez backup systemowy.

## Znane ograniczenia

- Miarka AR wymaga telefonu zgodnego z ARCore i dobrego oswietlenia.
- Pomiar AR jest orientacyjny i nie zastepuje dalmierza laserowego.
- Testy jednostkowe na tej lokalnej maszynie nie uruchamiaja workera Gradle (`GradleWorkerMain`), mimo ze build APK przechodzi. Testy nalezy potwierdzic w Android Studio albo w GitHub Actions.

## Plik APK

Asset release powinien miec nazwe:

`poziomnica.apk`

SHA-256:

`0591BAD9DB7D87D1C43F647FD66DE9AE3CCB43FCF6047219F3F52B1357D25CA8`
