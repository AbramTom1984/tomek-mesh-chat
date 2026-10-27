# Aktualizacja testowa 0.3.0 — GPS telefonu i mapa offline

To dodatek do istniejącego projektu 0.2.0, NIE samodzielny projekt, APK ani gotowa mapa. Nie usuwaj istniejących plików. Kompilacja Androida, generowanie mapy i testy sprzętowe nie zostały wykonane tutaj. Zachowaj działający APK i lokalną historię przed zmianami.

## Wgranie
Rozpakuj ZIP i wgraj jego ZAWARTOŚĆ (app, tools, tests, .github i dokumenty) do głównego katalogu repozytorium, main. Nie wgrywaj całego folderu nadrzędnego jako dodatkowego poziomu. Zachowaj stare workflow.
Jeśli .github jest ukryty, dwa osobne załączniki YAML umieść w .github/workflows/.

## APK
Actions → Build Android APK - GPS Offline 0.3.0 → Run workflow → main.
Po sukcesie pobierz artefakt Tomek-Mesh-Chat-0.3.0-debug i rozpakuj APK.
Nowy workflow nakłada integrację w katalogu roboczym kompilacji. Pliki MainActivity, manifest i build.gradle w repozytorium nie są automatycznie przepisywane. Artefakt Tomek-Mesh-Chat-0.3.0-build-sources zawiera użyty kod. Stary workflow nie nakłada tej integracji.
Jeśli kod źródłowy nie odpowiada oczekiwanej wersji 0.2.0, skrypt zatrzyma kompilację zamiast dopasowywać zmiany w ciemno.
Klucz debug z nowego uruchomienia może się różnić. Odinstalowanie usuwa lokalne dane, mapę i historię.

## Rzeczywista mapa
Actions → Build Offline Map - Edinburgh Dunfermline → Run workflow → main.
Pobieranie/konwersja są wykonywane na serwerze GitHub, wymagają sieci i zasobów. Uruchom jeden raz, gdy potrzebujesz mapy. Po sukcesie pobierz Edinburgh-Dunfermline-offline-map, rozpakuj, przenieś edinburgh-dunfermline.map na OBA telefony i użyj Offline map → Import .map. Nie importuj ZIP ani PBF. Mapa nie jest przesyłana przez LoRa.
Zakres prostokąta: zachód -3.95, południe 55.60, wschód -2.55, północ 56.40. To szeroki obszar obu miast z okolicami, a NIE precyzyjnie wyliczony bufor 20 km od granic administracyjnych. Sprawdź pokrycie przed użyciem. Rozmiar wynikowy, data i SHA-256: MAP-INFO.json.

## GPS i prywatność
Location → Share my location once. Zgoda na dokładną lokalizację, włączony GPS, najlepiej otwarta przestrzeń. Satelitarny GPS_PROVIDER bez lokalizacji sieciowej i bez zależności od Google Play Services. Limit oczekiwania 90 sekund. Po pomiarze pojawia się podgląd i wybór Send to channel / Keep local only. Brak poprawnego pomiaru oznacza brak wysłania. Aktualizacje GPS są zatrzymywane po pomiarze, anulowaniu, limicie czasu i opuszczeniu ekranu czatu.
UWAGA: lokalizacja i prośby idą przez WSPÓLNY KANAŁ, NIE prywatne DM. Wszyscy z dostępem do kanału mogą odczytać współrzędne. Prośba nie uruchamia cudzego GPS bez zgody. Brak śledzenia, cyklicznego nadawania i automatycznych testów radiowych.
Własny krótki format tekstowy wymaga tej wersji na obu telefonach. To NIE natywne pakiety Meshtastic POSITION_APP. Nie zmienia pozycji zapisanej w radiu ani niezależnego nadawania GPS skonfigurowanego w firmware.

## Ograniczenia mapy
Lokalne pliki Mapsforge .map, import do 600 MB, bez kafelków internetowych. Niebieskie punkty: telefon lokalny, pomarańczowe: odebrane. Wiek i zgłoszona dokładność są wyświetlane. Ostatnie znane pozycje nie są bieżącym śledzeniem. Brak tras i nawigacji. Dane OSM mogą być niepełne.
Otwarcie oddzielnego ekranu mapy wstrzymuje dotychczasowe połączenie czatu działające na pierwszym planie. Wróć do czatu, aby połączyć się ponownie; nie zakładaj pełnego zachowania ruchu podczas rozłączenia.
Brak prywatnych DM, usługi w tle, selekcji GPS ATGM336H, traceroute, zdalnych testów sygnału i nawigacji ratunkowej.
Przetestuj oba telefony bez Wi-Fi i danych mobilnych, z mapą zainstalowaną wcześniej, zasilonymi radiami i aktywnym Bluetooth, jeśli używasz BLE. Zachowaj wersję 0.2.0 jako punkt powrotu.
