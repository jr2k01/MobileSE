# Push-Benachrichtigungen einrichten

Die App kann benachrichtigen, wenn jemand aus der Crew ein Workout eintraegt und
wenn sich dadurch die Rangliste verschiebt. Verschickt werden die Nachrichten
nicht von der App, sondern von Supabase - ausgeloest von einem Webhook, sobald
in `activities` eine Zeile entsteht.

**Ohne die folgenden Schritte laeuft die App vollstaendig, nur eben ohne
Benachrichtigungen.** Das Projekt baut auch ohne Firebase-Datei: das
google-services-Plugin wird nur angewendet, wenn `app/google-services.json`
tatsaechlich daliegt (siehe `app/build.gradle.kts`).

Was wo hingehoert, in einem Satz: der **Firebase-Client** kommt in die App, der
**Firebase-Dienstkontoschluessel** ausschliesslich in die Edge Function.

---

## 1. Tabelle fuer die Geraetekennungen

Supabase → **SQL Editor**:

```sql
create table if not exists device_tokens (
    token      text primary key,
    user_id    uuid not null references profiles(id) on delete cascade,
    updated_at timestamptz not null default now()
);

alter table device_tokens enable row level security;

-- Jeder verwaltet nur seine eigenen Geraete.
--
-- Die Leseregel ist nicht optional, auch wenn die App die Zeilen nie anzeigt:
-- sie schreibt per Upsert, und ein Upsert ist ein INSERT ... ON CONFLICT DO
-- UPDATE. Dafuer muss Postgres die kollidierende Zeile lesen duerfen. Ohne die
-- Regel scheitert jedes Speichern mit "new row violates row-level security
-- policy" - einer Meldung, die auf die Schreibregel zeigt und nicht auf die
-- fehlende Leseregel.
drop policy if exists "device_tokens_read_own" on device_tokens;
create policy "device_tokens_read_own" on device_tokens
    for select to authenticated using (auth.uid() = user_id);

drop policy if exists "device_tokens_write_own" on device_tokens;
create policy "device_tokens_write_own" on device_tokens
    for insert to authenticated with check (auth.uid() = user_id);

drop policy if exists "device_tokens_update_own" on device_tokens;
create policy "device_tokens_update_own" on device_tokens
    for update to authenticated using (auth.uid() = user_id);

drop policy if exists "device_tokens_delete_own" on device_tokens;
create policy "device_tokens_delete_own" on device_tokens
    for delete to authenticated using (auth.uid() = user_id);
```

Der Schluessel ist die Kennung und nicht der Nutzer: wer auf Telefon und Tablet
angemeldet ist, soll auf beiden benachrichtigt werden. Meldet sich auf einem
Geraet jemand anderes an, wandert die Zeile per Upsert zum neuen Konto.

---

## 2. Firebase-Projekt anlegen

1. https://console.firebase.google.com → **Projekt hinzufuegen**, Name z. B.
   `CrewFit`. Google Analytics kann aus bleiben.
2. Im Projekt auf das **Android**-Symbol: Paketname genau
   `com.example.mobilese` eintragen, registrieren.
3. Die angebotene **`google-services.json`** herunterladen und in den Ordner
   **`app/`** legen - also `app/google-services.json`, neben `build.gradle.kts`.
4. Android Studio → Gradle-Sync. Ab jetzt wird das Plugin angewendet.

Die Datei enthaelt keinen geheimen Schluessel; sie gehoert in die App. Trotzdem
steht sie in `.gitignore`, weil sie zu deinem Firebase-Projekt gehoert und nicht
zum Quelltext.

---

## 3. Dienstkontoschluessel holen

Firebase-Konsole → Zahnrad → **Projekteinstellungen** → **Dienstkonten** →
**Neuen privaten Schluessel generieren**. Es faellt eine JSON-Datei an.

**Diese Datei ist geheim.** Mit ihr laesst sich an jedes Geraet des Projekts
senden. Sie darf nicht ins Repository und niemals in die App.

---

## 4. Edge Function ausrollen

Die Funktion liegt im Projekt unter `supabase/functions/notify/index.ts`.

### Zuerst der Schluessel als Secret

Supabase → **Project Settings** → **Edge Functions** → **Secrets** → **Add new
secret**:

| Feld | Wert |
| --- | --- |
| Name | `FIREBASE_SERVICE_ACCOUNT` |
| Value | der **gesamte Inhalt** der JSON-Datei aus Schritt 3 |

Die Datei mit einem Texteditor oeffnen, alles markieren, einfuegen. Die
Zeilenumbruche darin stoeren nicht.

`SUPABASE_URL` und `SUPABASE_SERVICE_ROLE_KEY` setzt Supabase in Edge Functions
von selbst; die muessen nicht hinterlegt werden.

### Dann die Funktion selbst - im Browser

Ohne Node.js auf dem Rechner ist das der einfachere Weg:

Supabase → **Edge Functions** → **Deploy a new function** → **Via Editor**.
Als Namen genau `notify` eintragen, den vorhandenen Beispielcode vollstaendig
loeschen und den Inhalt von `supabase/functions/notify/index.ts` hineinkopieren.
**Deploy**.

Danach in der Funktion unter **Details** pruefen, dass **Verify JWT** *aus* ist -
der Aufruf kommt vom Datenbank-Webhook und nicht von einem angemeldeten Nutzer.

### Oder mit der CLI

Wer Node.js hat, kann stattdessen aus dem Projektordner heraus ausrollen:

```bash
npx supabase login
```

```bash
npx supabase link --project-ref ghhtaaoedlvhipmnuziu
```

```bash
npx supabase functions deploy notify --no-verify-jwt
```

---

## 5. Ausloeser auf die Aktivitaeten

Damit die Funktion bei jedem neuen Workout anlaeuft, braucht es einen Trigger
auf `activities`. Zwei Wege, die dasselbe tun - ein "Database Webhook" ist bei
Supabase genau so ein Trigger, nur ueber die Oberflaeche angelegt.

### Per SQL - funktioniert immer

Supabase → **SQL Editor**:

```sql
-- Erlaubt der Datenbank, HTTP-Anfragen zu stellen. Ohne diese Erweiterung
-- kaeme der Trigger nicht aus der Datenbank heraus.
create extension if not exists pg_net;

create or replace function notify_on_activity()
returns trigger
language plpgsql
security definer
as $$
begin
    -- Das Feld heisst record, weil die Funktion genau das erwartet - dieselbe
    -- Form, die auch ein ueber die Oberflaeche angelegter Webhook schickt.
    perform net.http_post(
        url := 'https://ghhtaaoedlvhipmnuziu.supabase.co/functions/v1/notify',
        headers := '{"Content-Type": "application/json"}'::jsonb,
        body := jsonb_build_object('record', to_jsonb(new))
    );
    return new;
end;
$$;

drop trigger if exists notify_on_activity on activities;
create trigger notify_on_activity
    after insert on activities
    for each row execute function notify_on_activity();
```

`after insert` und nicht `before`: das Workout soll gespeichert sein, bevor
jemand davon erfaehrt. `net.http_post` wartet nicht auf die Antwort - das
Speichern eines Workouts haengt also nicht daran, ob die Benachrichtigung
klappt.

### Oder ueber die Oberflaeche

In aelteren Dashboards unter **Database** → **Webhooks**, in neueren unter
**Integrations** → **Database Webhooks** (dort erst einschalten). Dann **Create
a new hook** mit: Tabelle `activities`, Event nur **Insert**, Type **Supabase
Edge Functions**, Function `notify`, Method `POST`.

Nur einen der beiden Wege gehen - sonst laeuft die Funktion bei jedem Workout
zweimal und die Benachrichtigung kommt doppelt.

---

## 6. Ausprobieren

1. App auf zwei Geraeten mit zwei Konten derselben Crew anmelden. Beim Anmelden
   hinterlegt die App ihre Kennung - in `device_tokens` sollten danach zwei
   Zeilen stehen.
2. Auf dem einen Geraet in den Einstellungen unter **NOTIFICATIONS** die
   Erlaubnis erteilen (ab Android 13 fragt das System danach).
3. Auf dem anderen Geraet ein Workout eintragen.
4. Auf dem ersten Geraet sollte die Meldung erscheinen - auch bei geschlossener
   App.

Kommt nichts an, in dieser Reihenfolge nachsehen:

- Supabase → **Edge Functions** → `notify` → **Logs**. Dort steht, ob die
  Funktion ueberhaupt lief und was FCM geantwortet hat.
- Lief sie gar nicht, hat der Trigger nicht ausgeloest. Was er getan hat, steht
  in der Datenbank:

  ```sql
  select id, created, url, status_code, content
  from net._http_response
  order by created desc
  limit 5;
  ```

  Keine Zeile heisst: der Trigger existiert nicht oder feuert nicht. Dann
  pruefen, ob er da ist:

  ```sql
  select tgname from pg_trigger where tgrelid = 'activities'::regclass;
  ```

- Steht in `device_tokens` eine Zeile fuer den Empfaenger?
- Ist die Benachrichtigungserlaubnis erteilt? Einstellungen → NOTIFICATIONS.
- Ein Emulator **ohne Google Play** kann kein FCM. Es braucht ein Abbild mit
  Play Store oder ein echtes Geraet.

---

## Was die Nachrichten enthalten

Der Server schickt ausschliesslich Datennachrichten, keine
`notification`-Nutzlast. Sonst wuerde Android sie bei geschlossener App selbst
anzeigen - mit den Texten vom Server statt aus den Sprachdateien der App, im
falschen Kanal und ohne Ziel beim Antippen. So geht jede Nachricht durch
`CrewFitMessagingService` und damit durch `PushMessages`.

| Art | Wann | Feld |
| --- | --- | --- |
| `activity` | jemand traegt ein Workout ein | `name`, `sport`, `duration` |
| `overtake` | der Empfaenger wurde dadurch ueberholt | `name`, `rank` |
| `lead` | die Spitze der Crew hat gewechselt | `name` |

Zwei Kanaele, `crew_activities` und `crew_ranking`, damit sich beides getrennt
abschalten laesst: wen die Workouts der anderen nerven, soll trotzdem erfahren
koennen, dass er ueberholt wurde.

## Die Doppelung der Punkteregel

Die Edge Function rechnet die Punkte ein zweites Mal, in TypeScript. Sie muss
das, weil der Rang in der App aus Aktivitaeten, Belohnungen und Schritten
entsteht und die Datenbank ihn nicht kennt - dieselbe Lage wie beim Bild der
Nummer eins.

**Aendert sich die Punkteregel in `PointsCalculator`, `StepGoal` oder
`Scoreboard`, muss sie in `supabase/functions/notify/index.ts` nachgezogen
werden.** Sonst behauptet eine Benachrichtigung einen Rangwechsel, den die App
nicht zeigt. Sauberer waere eine Datenbankfunktion als einzige Quelle beider
Seiten; fuer ein Kursprojekt ist das mehr Aufwand als Nutzen, aber es ist eine
Entscheidung und kein Versehen.
