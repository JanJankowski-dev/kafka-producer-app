# Porównanie strategii i ETF-ów — stan lokalny

## Co działa

- Jeden obraz aplikacji zawiera backend Spring Boot, interfejs React/Vite i migracje bazy. Nie trzeba stawiać osobnego serwera frontendu.
- Dwa aktywne portfele paper (reguły i AI) są porównywane z dwoma pasywnymi symulacjami „kup i trzymaj”: CSSPX.SW (ta sama klasa co CSPX, S&P 500, ISIN IE00B5BMR087) i SWDA.SW (ta sama klasa co IWDA, MSCI World, ISIN IE00B4L5Y983). To giełdowe notowania USD na SIX z EODHD, nie NAV emitenta.
- Pasywne linie bazują na dziennych cenach zamknięcia z EODHD, zapisanych w `etf_benchmark_market_closes`. Dawne dane iShares NAV w `etf_benchmark_navs` pozostają w bazie, lecz nie są mieszane z nowymi cenami. Wykres pokazuje również tydzień kalendarzowy poprzedzający pierwszą sesję paper. Przerywana część to retrospekcja ETF-ów, nie decyzje podjęte wtedy przez aplikację. Każda pasywna symulacja zaczyna się od 10 000 USD w pierwszej wspólnej sesji; aktywne portfele są widoczne dopiero od pierwszej zapisanej wyceny.
- Brak wcześniejszej linii AI jest celowy: nie dopisujemy decyzji modelu z datą wsteczną. Domyślnie `APP_EXTERNAL_MARKET_DATA_SHARING_ALLOWED=false` blokuje wysyłkę danych do OpenAI, niezależnie od obecności klucza; AI wybiera HOLD. Nie włączaj flagi bez pisemnego potwierdzenia EODHD.
- Brak danych ETF oznacza brak linii; aplikacja nie podstawia zamiennika ani wymyślonej stopy zwrotu. Awaria importu ETF nie blokuje aktywnych decyzji paper.

## Uruchomienie lokalne

W katalogu `kafka-infra` użyj istniejącego prywatnego `.env` z `EODHD_API_KEY`, po czym uruchom:

```sh
docker compose --env-file .env -f infra/docker-compose.demo.yml up -d --build
```

Otwórz `http://localhost:8080/`. Gdy są już aktywne portfele i pierwsza wycena paper, przycisk „Odśwież ETF-y” pobierze giełdowe ceny zamknięcia. Następne codzienne cykle będą próbować aktualizować je automatycznie. Dzienne odświeżenie jest ograniczone do 10 zapytań o akcje i 2 o ETF-y; ponowne kliknięcie w tym samym dniu nie wykonuje ponownie całej paczki.

Do pracy nad samym UI: w `kafka-producer-app/frontend` uruchom `npm ci` i `npm run dev`; serwer deweloperski na `127.0.0.1:5173` przekazuje API do lokalnego backendu na porcie 8080. Wymagany jest Node 20.19+ lub 22.12+. Build produkcyjny Reacta wykonuje Dockerfile automatycznie.

## API i przepływ

`POST /daily-research/run` → import EODHD → sygnały i decyzje paper → wyceny → próba importu cen ETF z EODHD.

`GET /etf-benchmarks` zwraca datę początku retrospekcji, rzeczywisty start paper oraz wartości obu pasywnych symulacji. `POST /etf-benchmarks/refresh` odświeża ceny ETF maksymalnie raz na dzień. Oba endpointy działają wyłącznie przy `MARKET_DATA_LOCAL_MODE=true`; przy `APP_REQUIRE_AUTH=true` obejmuje je to samo Basic Auth co pozostałe API.

## Prywatny dostęp na Mikrusie — konfiguracja przygotowana, nie wdrożona

Przygotowany `kafka-infra/infra/docker-compose.mikrus.yml` wiąże port dashboardu wyłącznie do `127.0.0.1:40264` na serwerze, a Kafka UI do `127.0.0.1:14080`. Nginx nie przekazuje już `/kafka/` ani `/consumer/`; `/` kieruje do aplikacji chronionej przez `APP_REQUIRE_AUTH=true`. Kafka UI korzysta z aktywnie utrzymywanego obrazu Kafbat `v1.5.0` i wymaga osobnego loginu (`KAFKA_UI_USER`, `KAFKA_UI_PASSWORD`). Te pliki są **tylko lokalnie przygotowane** — serwera ani pipeline nie uruchamiano.

Po świadomym wdrożeniu właściciel konta SSH może otworzyć na swoim komputerze tunel, podstawiając własny host i użytkownika:

```sh
ssh -N -p SSH_PORT -L 127.0.0.1:18080:127.0.0.1:40264 -L 127.0.0.1:18081:127.0.0.1:14080 USER@MIKRUS_HOST
```

Dashboard będzie wtedy pod `http://127.0.0.1:18080/`, a Kafka UI pod `http://127.0.0.1:18081/`. Ruch między komputerem a serwerem jest szyfrowany przez SSH; HTTP nie jest wystawione do internetu. Przeglądarka poprosi dodatkowo o hasło aplikacji, a Kafka UI — o osobny login. Dla bezpośredniego publicznego adresu z logowaniem Basic potrzebne byłoby HTTPS, ponieważ Base64 nie szyfruje hasła.

Przed wdrożeniem trzeba uzupełnić dwa nowe sekrety Kafka UI w prywatnym `.env`, sprawdzić uprawnienia dostępu SSH i potwierdzić, że na Mikrusie nie ma innego reverse proxy udostępniającego starego portu. Zmiana obrazu Provectus → Kafbat nie została jeszcze uruchomiona na Mikrusie; należy ją sprawdzić lokalnie w całym zestawie Compose. Jeśli użytkownik będzie chciał stronę dostępną bez tunelu, potrzebna jest oddzielna decyzja o prywatnym VPN lub HTTPS z kontrolą dostępu.

EODHD dopuszcza analizę danych na prywatny, niekomercyjny użytek, ale jego warunki nie rozstrzygają wprost przekazania danych rynkowych do OpenAI. Trzeba uzyskać pisemną odpowiedź EODHD dla prywatnego dashboardu, zapisu dziennych cen w bazie i przesyłania do OpenAI symboli, cen oraz stóp zwrotu. Samo `store:false` po stronie OpenAI nie zastępuje zgody dostawcy danych. Domyślna flaga blokuje ten transfer. Dane EODHD EOD nie zawierają w tej integracji pełnych zdarzeń korporacyjnych (dywidendy/splity), więc wycena i wynik paper mogą być nieprecyzyjne; nie traktuj ich jako wiarygodnego wyniku inwestycyjnego.

## Źródła

- [CSPX — iShares](https://www.ishares.com/uk/individual/en/products/253743/ishares-core-sp-500-ucits-etf)
- [IWDA — iShares](https://www.ishares.com/uk/individual/en/products/251882/ishares-core-msci-world-ucits-etf)
- [Warunki EODHD](https://eodhd.com/financial-apis/terms-conditions)
- [Dokumentacja EODHD EOD](https://eodhd.com/financial-apis/api-for-historical-data-and-volumes)
- [Vite — dokumentacja](https://vite.dev/guide/)
- [Kafbat UI — uwierzytelnianie](https://ui.docs.kafbat.io/configuration/authentication/for-the-ui/basic-authentication)
- [Docker — wiązanie portu do localhost](https://docs.docker.com/get-started/docker-concepts/running-containers/publishing-ports/)
- [HTTP Basic i HTTPS — MDN](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Authentication)
