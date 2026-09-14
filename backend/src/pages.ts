/**
 * GENERÁLT FÁJL — ne szerkeszd kézzel.
 *
 * Forrás: a repó `mealpilot/` könyvtára. Újragenerálás:
 *
 *     cd backend && npm run pages
 *
 * A `pages.test.ts` elbukik, ha a kettő szétcsúszik, és a deploy is
 * újragenerálja, mielőtt kiküldi.
 */

export interface StaticPage {
  readonly contentType: string
  readonly body: string
}

export const PAGES: Record<string, StaticPage> = {
  'assets/site.css': {
    contentType: 'text/css; charset=utf-8',
    body: `/* MealPilot — közös stílus a jogi és tájékoztató oldalakhoz.
   Szándékosan egyetlen fájl, külső betűtípus és szkript nélkül: ezek az oldalak
   akkor is be kell töltsenek, ha a hálózat lassú vagy egy CDN éppen nem elérhető. */

:root {
  --bg: #fdfdfb;
  --fg: #1a1d17;
  --muted: #6b7263;
  --accent: #2f6b3e;
  --line: #e3e6dd;
  --card: #f5f7f1;
  --warn-bg: #fbeeec;
  --warn-line: #a32e22;
  --draft-bg: #fdf8e8;
  --draft-line: #c9a227;
}

@media (prefers-color-scheme: dark) {
  :root {
    --bg: #101310;
    --fg: #e7ebe3;
    --muted: #929a8b;
    --accent: #96d6a2;
    --line: #262b24;
    --card: #171b16;
    --warn-bg: #2c1713;
    --warn-line: #ff9a88;
    --draft-bg: #2a2412;
    --draft-line: #c9a227;
  }
}

* { box-sizing: border-box; }

body {
  max-width: 46rem;
  margin: 0 auto;
  padding: 2rem 1.25rem 5rem;
  font: 16px/1.65 Georgia, "Times New Roman", serif;
  color: var(--fg);
  background: var(--bg);
}

h1 {
  font-size: 1.9rem;
  line-height: 1.15;
  margin: 0 0 .4rem;
  font-family: system-ui, -apple-system, sans-serif;
}

h2 {
  font-size: 1.05rem;
  margin: 2.2rem 0 .6rem;
  font-family: system-ui, -apple-system, sans-serif;
  text-transform: uppercase;
  letter-spacing: .08em;
  color: var(--accent);
}

h3 {
  font-size: 1rem;
  font-family: system-ui, -apple-system, sans-serif;
  margin: 1.4rem 0 .4rem;
}

a { color: var(--accent); }

ul { padding-left: 1.3rem; }
li { margin-bottom: .45rem; }

.meta {
  color: var(--muted);
  font-size: .9rem;
  margin-bottom: 2rem;
}

.nav {
  display: flex;
  flex-wrap: wrap;
  gap: .4rem 1.1rem;
  padding-bottom: 1.4rem;
  margin-bottom: 1.6rem;
  border-bottom: 1px solid var(--line);
  font-family: system-ui, -apple-system, sans-serif;
  font-size: .88rem;
}

.nav strong { margin-right: auto; }
.nav a { text-decoration: none; }
.nav a:hover { text-decoration: underline; }

.draft {
  border-left: 3px solid var(--draft-line);
  background: var(--draft-bg);
  padding: .8rem 1rem;
  margin: 1.5rem 0;
  font-size: .92rem;
}

.warn {
  border-left: 3px solid var(--warn-line);
  background: var(--warn-bg);
  padding: .9rem 1rem;
  margin: 1.5rem 0;
}

.warn h2 { margin-top: 0; }

.card {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: .6rem;
  padding: 1.1rem 1.2rem;
  margin: 1.2rem 0;
}

.card h3 { margin-top: 0; }

.lead {
  font-size: 1.1rem;
  color: var(--fg);
}

footer {
  margin-top: 4rem;
  padding-top: 1.2rem;
  border-top: 1px solid var(--line);
  color: var(--muted);
  font-size: .85rem;
  font-family: system-ui, -apple-system, sans-serif;
}

footer a { color: var(--muted); }

code {
  font-family: ui-monospace, "SFMono-Regular", Menlo, monospace;
  font-size: .9em;
  background: var(--card);
  padding: .1em .35em;
  border-radius: .25em;
}
`,
  },
  'delete-data.html': {
    contentType: 'text/html; charset=utf-8',
    body: `<!DOCTYPE html>
<html lang="hu">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>MealPilot — Adatok törlése</title>
<link rel="stylesheet" href="assets/site.css">
</head>
<body>

<nav class="nav">
  <strong>MealPilot</strong>
  <a href="index.html">Főoldal</a>
  <a href="support.html">Támogatás</a>
  <a href="privacy.html">Adatkezelés</a>
  <a href="terms.html">Feltételek</a>
  <a href="en/delete-data.html">English</a>
</nav>

<h1>Adatok törlése</h1>
<p class="meta">MealPilot mobilalkalmazás</p>

<div class="card">
  <h3>A rövid válasz</h3>
  <p>Az alkalmazásban: <strong>Beállítások → Jogi tudnivalók és adatok → Minden adat
  törlése</strong>. Ez egy lépésben törli az étrendedet, az étkezési és testsúlynaplódat,
  a profilodat, a beállításaidat és a beszélgetést. Nincs visszavonás.</p>
  <p>Ugyanezt éri el az alkalmazás eltávolítása is, ha a készülék biztonsági mentése nincs
  bekapcsolva.</p>
</div>

<h2>Mi hol van, és mi törlődik</h2>
<ul>
  <li><strong>A készülékeden:</strong> az étrend, a naplók, a testadatok, a beállítások és
    a beszélgetés. Ezeket a fenti gomb azonnal és véglegesen törli.</li>
  <li><strong>A kiszolgálónkon:</strong> nem tárolunk étrendet, naplót vagy testadatot.
    Csak a telepítés véletlen azonosítójának lenyomata, az előfizetés állapota, a
    felhasznált keret és a hibajelentések vannak ott — ezek nem alkalmasak arra, hogy
    téged mint személyt azonosítsanak. A „Minden adat törlése" a telepítési azonosítót is
    eldobja, tehát az új azonosító már nem köthető a korábbihoz.</li>
  <li><strong>Amit te küldtél be:</strong> ha használtad a jelentés gombot, a bejelentés
    szövege nálunk marad — ez a lényege. Ennek törlését e-mailben kérheted.</li>
</ul>

<h2>Megőrzési idő</h2>
<ul>
  <li>Hibajelentések és használati számlálók: legfeljebb 12 hónap.</li>
  <li>Számlázáshoz és visszaélés-megelőzéshez tartozó kerettel kapcsolatos adatok:
    legfeljebb 24 hónap.</li>
  <li>Bejelentések: amíg a hiba kivizsgálása tart, legfeljebb 24 hónap.</li>
</ul>

<h2>Ha e-mailben kéred</h2>
<p>Írj a <a href="mailto:mate.teke@gmail.com">mate.teke@gmail.com</a> címre. Mivel nem
vezetünk fiókot, a kéréshez meg kell adnod, mihez tartozik: a jelentés elküldése után
kapott visszaigazolást, vagy az alkalmazásbeli bejelentés hozzávetőleges időpontját.
Legkésőbb 30 napon belül válaszolunk.</p>

<h2>Az előfizetésed</h2>
<p>Az adatok törlése <strong>nem</strong> mondja le az előfizetést — az a Google-fiókodhoz
tartozik. Lemondás: Google Play → Fizetések és előfizetések → Előfizetések.</p>

<footer>
  MealPilot ·
  <a href="privacy.html">Adatkezelési tájékoztató</a> ·
  <a href="terms.html">Felhasználási feltételek</a> ·
  <a href="support.html">Támogatás</a> ·
  <a href="index.html">Főoldal</a> ·
  <a href="en/delete-data.html">English</a>
</footer>

</body>
</html>
`,
  },
  'en/delete-data.html': {
    contentType: 'text/html; charset=utf-8',
    body: `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>MealPilot — Deleting your data</title>
<link rel="stylesheet" href="../assets/site.css">
</head>
<body>

<nav class="nav">
  <strong>MealPilot</strong>
  <a href="index.html">Home</a>
  <a href="support.html">Support</a>
  <a href="privacy.html">Privacy</a>
  <a href="terms.html">Terms</a>
  <a href="../delete-data.html">Magyar</a>
</nav>

<h1>Deleting your data</h1>
<p class="meta">MealPilot mobile app</p>

<div class="card">
  <h3>The short answer</h3>
  <p>In the app: <strong>Settings → Legal and your data → Delete all data</strong>. That
  deletes your meal plan, your food and weight logs, your profile, your settings and your
  chat in one step. There is no undo.</p>
  <p>Uninstalling the app does the same, as long as your device backup is switched
  off.</p>
</div>

<h2>What is where, and what gets deleted</h2>
<ul>
  <li><strong>On your device:</strong> the meal plan, the logs, your body data, your
    settings and the chat. The button above deletes these immediately and
    permanently.</li>
  <li><strong>On our server:</strong> we store no meal plan, no log and no body data. Only
    a fingerprint of the installation's random identifier, the subscription state, the
    allowance used and crash reports are there — none of which can identify you as a
    person. "Delete all data" also throws away the installation identifier, so the new one
    cannot be linked to the old.</li>
  <li><strong>What you sent us:</strong> if you used the report button, the text of that
    report stays with us — that is the point of it. You can ask for it to be deleted by
    email.</li>
</ul>

<h2>Retention</h2>
<ul>
  <li>Crash reports and usage counters: at most 12 months.</li>
  <li>Allowance data used for billing and abuse prevention: at most 24 months.</li>
  <li>Reports: for as long as the investigation takes, at most 24 months.</li>
</ul>

<h2>If you ask by email</h2>
<p>Write to <a href="mailto:mate.teke@gmail.com">mate.teke@gmail.com</a>. Since we keep no
accounts, you need to tell us what the request relates to: the confirmation you got after
sending a report, or roughly when you sent it from the app. We answer within 30 days at the
latest.</p>

<h2>Your subscription</h2>
<p>Deleting your data does <strong>not</strong> cancel your subscription — that belongs to
your Google account. To cancel: Google Play → Payments and subscriptions →
Subscriptions.</p>

<footer>
  MealPilot ·
  <a href="privacy.html">Privacy notice</a> ·
  <a href="terms.html">Terms of use</a> ·
  <a href="support.html">Support</a> ·
  <a href="index.html">Home</a> ·
  <a href="../delete-data.html">Magyar</a>
</footer>

</body>
</html>
`,
  },
  'en/index.html': {
    contentType: 'text/html; charset=utf-8',
    body: `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>MealPilot — calorie-deficit meal planner</title>
<meta name="description" content="MealPilot builds a calorie-deficit meal plan from your own body data, writes the shopping list to go with it, and reminds you when to eat what.">
<link rel="stylesheet" href="../assets/site.css">
</head>
<body>

<nav class="nav">
  <strong>MealPilot</strong>
  <a href="index.html">Home</a>
  <a href="support.html">Support</a>
  <a href="privacy.html">Privacy</a>
  <a href="terms.html">Terms</a>
  <a href="../index.html">Magyar</a>
</nav>

<h1>MealPilot</h1>
<p class="lead">A calorie-deficit meal plan built from your own body data — with a shopping
list, reminders and a food log.</p>

<h2>What it does</h2>
<ul>
  <li>Works out your daily calorie and macro targets from your weight, height, age and
    activity level, and tells you how fast you'll lose weight at that rate.</li>
  <li>Puts together a 3, 7, 14 or 30-day meal plan from real, shop-bought ingredients,
    with full nutrition figures.</li>
  <li>Turns the plan into a shopping list, grouped by aisle, with quantities added up.</li>
  <li>Reminds you when to eat what, and logs what you actually ate.</li>
  <li>Lets you change it by talking to it: move meal times, swap days, add an allergy,
    have a single day rewritten.</li>
</ul>

<div class="card">
  <h3>Allergies and exclusions</h3>
  <p>On first launch you pick what you can't eat — the 14 EU allergens, gluten-, lactose-
  and casein-free, vegetarian and vegan diets, and fructose, histamine and FODMAP
  sensitivity are all on the list. The ingredients you rule out can't appear in your plan,
  and the app machine-checks the finished plan for them as well.</p>
  <p><strong>This does not replace reading the label.</strong> With a serious allergy,
  always check what is actually in the product.</p>
</div>

<div class="warn">
  <h2>Not medical advice</h2>
  <p>Meal plans are put together by an automated planner from the data you give it, and
  they can contain mistakes. The app is for information only; it is not suitable for
  preventing, diagnosing or treating any illness. With an illness, in pregnancy, while
  breastfeeding, with an eating disorder or on regular medication, talk your diet through
  with a doctor. The app is made for people over 18.</p>
</div>

<h2>What's free and what isn't</h2>
<p>What runs on your phone stays free: the food log, the shopping list, the reminders and
the plan built from the app's own recipes. One plan and ten messages a month are included
too.</p>
<p>The full plan comes with a subscription: unlimited planning and chat, 30-day plans, and
rewriting individual days. The subscription is handled by Google Play, renews
automatically, and can be cancelled any time under Play → Subscriptions.</p>

<h2>Machine planning</h2>
<p>Meal plans and chat answers are produced by a language model. That is fast and
flexible, but it can be wrong — which is why there's a <strong>report</strong> button on
every plan and every dish in the app. If something went wrong, that's how it reaches us.</p>

<h2>Contact</h2>
<p>Questions, bug reports, data requests:
<a href="mailto:mate.teke@gmail.com">mate.teke@gmail.com</a>.
More on the <a href="support.html">support page</a>.</p>

<footer>
  MealPilot ·
  <a href="privacy.html">Privacy notice</a> ·
  <a href="terms.html">Terms of use</a> ·
  <a href="delete-data.html">Deleting your data</a> ·
  <a href="support.html">Support</a> ·
  <a href="../index.html">Magyar</a>
</footer>

</body>
</html>
`,
  },
  'en/privacy.html': {
    contentType: 'text/html; charset=utf-8',
    body: `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>MealPilot — Privacy notice</title>
<link rel="stylesheet" href="../assets/site.css">
</head>
<body>

<nav class="nav">
  <strong>MealPilot</strong>
  <a href="index.html">Home</a>
  <a href="support.html">Support</a>
  <a href="privacy.html">Privacy</a>
  <a href="terms.html">Terms</a>
  <a href="../privacy.html">Magyar</a>
</nav>

<h1>Privacy notice</h1>
<p class="meta">MealPilot mobile app · In force: 11 September 2026</p>

<div class="draft">
  <strong>Draft.</strong> This text was written from how the app actually works, but it
  needs a legal review before publication.
</div>

<h2>1. The controller</h2>
<p>
  <strong>Máté Teke</strong>, a private individual acting as data controller<br>
  Neptun utca 88., 4th floor, door 18, 1158 Budapest, Hungary<br>
  The controller is a private individual and has no company registration number.<br>
  Contact: <a href="mailto:mate.teke@gmail.com">mate.teke@gmail.com</a>
</p>

<h2>2. What data we handle</h2>
<p>The app asks for no registration and creates no user account. You enter the following
data yourself, and by default it stays on your own device:</p>
<ul>
  <li><strong>Health and body data:</strong> biological sex, age, height, weight, body fat
    percentage, target weight, activity level, diet style, allergies and
    intolerances.</li>
  <li><strong>Log data:</strong> the meals you ate and their nutrition, weight
    readings.</li>
  <li><strong>Free text you write:</strong> preferences and chat messages.</li>
  <li><strong>App settings:</strong> reminders, display settings.</li>
</ul>
<p>Data about allergies and health counts as a special category of personal data under
Article 9 GDPR. We handle it solely on the basis of your explicit consent, for the purpose
of building your meal plan.</p>

<h2>3. Where we store it</h2>
<p>All the data listed above is stored in a local database on your device. We keep
<strong>no server-side copy</strong> of your meal plan, your logs or your body data, and we
run no user accounts: no registration, no password, no email address. If your device backup
is switched on, the data may end up in your own Google account's backup — that is your
setting, not ours.</p>
<p>Planning is relayed through a server of ours (see section 4). That server stores the
following, solely to prevent abuse and to verify billing:</p>
<ul>
  <li>a one-way fingerprint of the installation's random identifier (it does not identify
    you, and it is replaced when you delete your data or uninstall the app);</li>
  <li>a one-way fingerprint of the Google Play purchase token and the subscription
    state;</li>
  <li>per call: the time, the task type and the number of tokens used;</li>
  <li>a monthly total of the allowance used;</li>
  <li>crash reports and daily aggregated usage counters (see section 3a).</li>
</ul>
<p><strong>The server does not write down the text of your request — that is, your meal
plan, your data and your messages.</strong> The one exception is when you send a report
yourself with the "report" button: then the text of the plan or dish you objected to is
saved as well, so that we can see what went wrong.</p>

<h2>3a. Crash reports and anonymous statistics</h2>
<p>If the app crashes, a description of the error (the exception type, the call stack, the
app version, the Android version and the device model) is sent to our server on the next
launch. We also count, per day, how many plans, log entries, messages and reports were
made.</p>
<p>These are <strong>aggregated counts</strong>, not an event log: we do not store when
anything happened, only how many times it happened that day. Your meal plan, food log,
weight, allergies and the contents of your chat are <strong>not</strong> included. We do
not share this data with advertisers or analytics providers; we run no third-party
trackers.</p>
<p>You can <strong>switch this off any time in Settings</strong> ("Crash reports and
anonymous statistics"). Switched off, the app does not even collect it — it doesn't merely
stop sending. The legal basis is legitimate interest (running the service free of faults),
which you can object to with that switch.</p>

<h2>4. What leaves your device</h2>
<p>When a meal plan is built and when you use the chat, the following data goes out through
our own server to the provider that does the planning (<strong>Anthropic PBC</strong>,
United States), so that the plan can be made:</p>
<ul>
  <li>biological sex, age, height, weight, body fat percentage, target weight, activity
    level;</li>
  <li>the calculated calorie and macro targets;</li>
  <li>your dietary exclusions and your free-text requests;</li>
  <li>chat messages and a short summary of your plan and today's log that goes with
    them.</li>
</ul>
<p><strong>Your name, email address and detailed log history are not sent.</strong>
The legal basis for the transfer is performance of the contract (providing the service),
and for special category data your explicit consent.</p>
<p>On Anthropic's data handling:
  <a href="https://www.anthropic.com/legal/privacy">anthropic.com/legal/privacy</a>.
  The transfer to the United States takes place under the EU–US Data Privacy Framework or
  standard contractual clauses.</p>

<h2>5. Payment</h2>
<p>Subscriptions are handled by Google Play. We have no access to card or payment details
and do not store them. Our server queries the Google Play developer interface for whether a
given purchase is valid, and stores that state (see section 3). On Google's data handling:
  <a href="https://policies.google.com/privacy">policies.google.com/privacy</a>.</p>

<h2>6. Notifications</h2>
<p>Meal reminders are produced on the device; they do not come from a server. Notification
permission can be withdrawn at any time in your system settings.</p>

<h2>7. How long we keep it</h2>
<p>Data on your device stays until you delete it. Uninstalling the app deletes all local
data. Inside the app you can delete everything in one step at any time under
<em>Settings → Legal and your data → Delete all data</em>.</p>
<p>Retention on the server:</p>
<ul>
  <li>crash reports and usage counters: at most 12 months;</li>
  <li>data on the monthly allowance and subscription state: at most 24 months;</li>
  <li>reports you send us: for as long as the investigation takes, at most 24 months.</li>
</ul>
<p>Step-by-step guide: <a href="delete-data.html">Deleting your data</a>.</p>

<h2>8. Your rights</h2>
<p>Under the GDPR you have the right of access, rectification, erasure, restriction of
processing, data portability and withdrawal of consent. Because your data lives on your own
device, you exercise most of these directly in the app. Get in touch with any question at
the email address above. You may lodge a complaint with the Hungarian National Authority
for Data Protection and Freedom of Information (<a href="https://naih.hu">naih.hu</a>), or
with the supervisory authority of your own country of residence.</p>

<h2>9. Children</h2>
<p>The app is not made for people under 18, and we do not knowingly collect data from
them.</p>

<h2>10. Changes</h2>
<p>If this notice is amended we update the date it comes into force, and we tell you about
material changes in the app as well.</p>

<footer>
  MealPilot ·
  <a href="privacy.html">Privacy notice</a> ·
  <a href="terms.html">Terms of use</a> ·
  <a href="delete-data.html">Deleting your data</a> ·
  <a href="support.html">Support</a> ·
  <a href="../privacy.html">Magyar</a>
</footer>

</body>
</html>
`,
  },
  'en/support.html': {
    contentType: 'text/html; charset=utf-8',
    body: `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>MealPilot — Support</title>
<link rel="stylesheet" href="../assets/site.css">
</head>
<body>

<nav class="nav">
  <strong>MealPilot</strong>
  <a href="index.html">Home</a>
  <a href="support.html">Support</a>
  <a href="privacy.html">Privacy</a>
  <a href="terms.html">Terms</a>
  <a href="../support.html">Magyar</a>
</nav>

<h1>Support</h1>
<p class="meta">Write in — a person reads this, not a ticketing system.</p>

<div class="card">
  <h3>Contact</h3>
  <p><a href="mailto:mate.teke@gmail.com">mate.teke@gmail.com</a></p>
  <p>If you're reporting a bug, it helps to say: what phone you have, which app version
  you're on (Settings → About), and what you were doing when it happened.</p>
</div>

<h2>Frequently asked questions</h2>

<h3>A dish or a nutrition figure is wrong. What do I do?</h3>
<p>Use the report button in the app: on the plan page under the plan, at the bottom of a
dish's detail view, and in the chat by pressing and holding a message. That way the text
you objected to reaches us too, and we can see what went wrong. It's the fastest route.</p>

<h3>How do I cancel my subscription?</h3>
<p>Google Play app → your profile picture → Payments and subscriptions → Subscriptions →
MealPilot → Cancel subscription. You keep the full plan until the end of the period you
have already paid for. There's a button on the app's Settings page that takes you
there.</p>

<h3>I'd like a refund.</h3>
<p>The purchase is handled by Google Play, so refunds are too: Google Play → Order history
→ the item → Report a problem. If that doesn't work out, write to us and we'll see what we
can do.</p>

<h3>I got a new phone. Does my data come with me?</h3>
<p>Your meal plan, logs and body data are on the device, and can come across with Google's
device backup. Your subscription belongs to your Google account, so it works on the new
phone too: open Settings and press "Restore a previous purchase".</p>

<h3>The plan ignores my allergy.</h3>
<p>That's a serious bug — please report it from the app, with the reason "It suggested
something I ruled out". In the meantime: you can check your exclusions under Settings →
Allergies and intolerances, and the next plan will be built with them.
<strong>Always check what is actually in an ingredient on its packaging</strong> — the app
cannot do that for you.</p>

<h3>Planning is slow.</h3>
<p>A week takes a few tens of seconds; a month can take several minutes, because it is
built in stages. The first days are usable straight away and the rest loads in the
background — feel free to leave the app in the meantime, the work carries on.</p>

<h3>I don't want it to send crash reports.</h3>
<p>Settings → Legal and your data → the "Crash reports and anonymous statistics" switch.
Switched off, the app does not even collect them.</p>

<h3>I want to delete all my data.</h3>
<p>See the <a href="delete-data.html">data deletion page</a>.</p>

<footer>
  MealPilot ·
  <a href="privacy.html">Privacy notice</a> ·
  <a href="terms.html">Terms of use</a> ·
  <a href="delete-data.html">Deleting your data</a> ·
  <a href="index.html">Home</a> ·
  <a href="../support.html">Magyar</a>
</footer>

</body>
</html>
`,
  },
  'en/terms.html': {
    contentType: 'text/html; charset=utf-8',
    body: `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>MealPilot — Terms of use</title>
<link rel="stylesheet" href="../assets/site.css">
</head>
<body>

<nav class="nav">
  <strong>MealPilot</strong>
  <a href="index.html">Home</a>
  <a href="support.html">Support</a>
  <a href="privacy.html">Privacy</a>
  <a href="terms.html">Terms</a>
  <a href="../terms.html">Magyar</a>
</nav>

<h1>Terms of use</h1>
<p class="meta">MealPilot mobile app · In force: 11 September 2026</p>

<div class="draft">
  <strong>Draft.</strong> Needs a legal review before publication.
</div>

<h2>1. The service</h2>
<p>MealPilot is a mobile app that works out a calorie target from the body data you give
it, builds meal plans, makes a shopping list, and helps you track your meals and your
weight. Provider: <strong>Máté Teke</strong>, a private individual,
Neptun utca 88., 4th floor, door 18, 1158 Budapest, Hungary.
Contact: <a href="mailto:mate.teke@gmail.com">mate.teke@gmail.com</a>.</p>

<div class="warn">
  <h2 style="margin-top:0">2. Not medical advice</h2>
  <p>The app is for information only and <strong>does not constitute medical, dietetic or
  any other health advice</strong>. Meal plans are put together by an automated planner
  from the data you give it; they can contain errors or inaccuracies. The app is not
  suitable for preventing, diagnosing or treating any illness.</p>
  <p>With an illness, in pregnancy, while breastfeeding, with an eating disorder, on
  regular medication, or under the age of 18, you <strong>must consult a doctor or a
  dietitian</strong> before starting the diet. Entering your allergy data is your own
  responsibility, and you must check what is actually in each ingredient on its packaging,
  every time.</p>
</div>

<h2>3. Who may use it</h2>
<p>The app is made for people aged 18 and over. You are responsible for the accuracy of the
data you enter — wrong data gives a wrong calorie target.</p>

<h2>4. Free and paid plans</h2>
<p>The app's core features are free to use: logging, the shopping list, reminders, meal
plans built from the app's own recipes, and a set number of plans and messages a month.</p>
<p>The full plan comes with a subscription and allows unlimited planning, unlimited chat,
longer plans and rewriting individual days.</p>

<h2>5. Subscription, renewal, cancellation</h2>
<ul>
  <li>The subscription is handled by Google Play and charged to your Google Play
    account.</li>
  <li>It <strong>renews automatically</strong> at the end of each term until you cancel
    it.</li>
  <li>You can cancel any time under Google Play → Subscriptions, at the latest 24 hours
    before it renews.</li>
  <li>A period you have already paid for remains usable after cancellation.</li>
  <li>Refunds are governed by Google Play's policy as in force from time to time. As a
    consumer you may exercise your right of withdrawal under the applicable law.</li>
  <li>We may change prices; we will tell you in advance under Google Play's rules, and a
    change only takes effect from the next renewal.</li>
</ul>

<h2>6. Proper use</h2>
<p>You may not use the app for unlawful purposes, attempt to reverse-engineer it, work
around its limits, or load it with automated tools. The chat feature may not be used to
produce unlawful, hateful or self-harm content.</p>

<h2>7. Liability</h2>
<p>The app is provided "as is". We do not warrant that a meal plan will always be
error-free, complete, or appropriate for your individual state of health. To the extent
permitted by law we exclude liability for indirect damage arising from use of the app. This
limitation does not affect consumers' statutory rights, and does not exclude liability for
breaches caused intentionally or by gross negligence, or for breaches causing harm to life,
bodily integrity or health.</p>

<h2>8. Termination</h2>
<p>You may stop using the app and uninstall it at any time. In case of a serious breach of
these terms we may restrict access to the service.</p>

<h2>9. Amendments</h2>
<p>We may amend these terms; we will tell you about material changes in the app. Continued
use after an amendment means you accept the terms.</p>

<h2>10. Governing law</h2>
<p>These terms are governed by Hungarian law. In a consumer dispute you may turn to the
conciliation body for your place of residence, or to the EU Online Dispute Resolution
platform.</p>

<h2>11. Contact</h2>
<p><a href="mailto:mate.teke@gmail.com">mate.teke@gmail.com</a></p>

<footer>
  MealPilot ·
  <a href="privacy.html">Privacy notice</a> ·
  <a href="terms.html">Terms of use</a> ·
  <a href="delete-data.html">Deleting your data</a> ·
  <a href="support.html">Support</a> ·
  <a href="../terms.html">Magyar</a>
</footer>

</body>
</html>
`,
  },
  'index.html': {
    contentType: 'text/html; charset=utf-8',
    body: `<!DOCTYPE html>
<html lang="hu">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>MealPilot — kalóriadeficites étrendtervező</title>
<meta name="description" content="A MealPilot a testadataid alapján kalóriadeficites étrendet állít össze, bevásárlólistát ír hozzá, és emlékeztet, mikor mit egyél.">
<link rel="stylesheet" href="assets/site.css">
</head>
<body>

<nav class="nav">
  <strong>MealPilot</strong>
  <a href="index.html">Főoldal</a>
  <a href="support.html">Támogatás</a>
  <a href="privacy.html">Adatkezelés</a>
  <a href="terms.html">Feltételek</a>
  <a href="en/index.html">English</a>
</nav>

<h1>MealPilot</h1>
<p class="lead">Kalóriadeficites étrend a saját testadataid alapján — bevásárlólistával,
emlékeztetőkkel és naplózással.</p>

<h2>Mit csinál</h2>
<ul>
  <li>A testsúlyodból, magasságodból, korodból és mozgásszintedből kiszámolja a napi
    kalória- és makrócélodat, és megmondja, milyen ütemben fogysz vele.</li>
  <li>Összeállít egy 3, 7, 14 vagy 30 napos étrendet, valódi, itthon beszerezhető
    alapanyagokból, részletes tápértékkel.</li>
  <li>Az étrendből bevásárlólistát ír, polcok szerint csoportosítva, a mennyiségeket
    összevonva.</li>
  <li>Emlékeztet, mikor mit egyél, és naplózza, hogy mit ettél valójában.</li>
  <li>Beszélgetve lehet módosítani rajta: étkezési időpontokat átállítani, napokat
    cserélni, allergiát hozzáadni, egy napot átíratni.</li>
</ul>

<div class="card">
  <h3>Allergiák és kizárások</h3>
  <p>Az első indításkor kiválaszthatod, mit nem ehetsz — a 14 uniós allergén, a glutén-,
  laktóz- és kazeinmentesség, a vegetáriánus és vegán étrend, valamint a fruktóz-,
  hisztamin- és FODMAP-érzékenység is szerepel közte. A kiválasztott alapanyagok nem
  kerülhetnek az étrendbe, és az app gépi ellenőrzéssel is átnézi a kész tervet.</p>
  <p><strong>Ez nem helyettesíti a csomagolás elolvasását.</strong> Súlyos allergia esetén
  a tényleges összetételt minden esetben ellenőrizd.</p>
</div>

<div class="warn">
  <h2>Nem orvosi tanács</h2>
  <p>Az étrendeket gépi tervező állítja össze a megadott adataid alapján, és ezek
  tartalmazhatnak hibát. Az alkalmazás tájékoztató jellegű, nem alkalmas betegség
  megelőzésére, diagnosztizálására vagy kezelésére. Betegség, terhesség, szoptatás,
  evészavar vagy rendszeres gyógyszerszedés esetén a diétát orvossal kell egyeztetni.
  Az alkalmazás 18 éven felülieknek készült.</p>
</div>

<h2>Mi ingyenes, mi fizetős</h2>
<p>Ami a telefonodon fut, az ingyenes marad: a naplózás, a bevásárlólista, az
emlékeztetők és a beépített receptekből készülő étrend. Havonta egy
tervezés és tíz üzenet is belefér.</p>
<p>A teljes csomag előfizetéssel jár: korlátlan tervezés és beszélgetés, 30 napos tervek,
és az egyes napok átíratása. Az előfizetést a Google Play kezeli, automatikusan megújul,
és bármikor lemondható a Play → Előfizetések menüpontban.</p>

<h2>Gépi tervezés</h2>
<p>Az étrendeket és a beszélgetés válaszait nyelvi modell állítja elő. Ez gyors és
rugalmas, de tévedhet — ezért minden tervnél és fogásnál van <strong>jelentés</strong>
gomb az alkalmazásban. Ha valami félrement, azon az úton jut el hozzánk.</p>

<h2>Kapcsolat</h2>
<p>Kérdés, hibajelentés, adatkezelési kérés:
<a href="mailto:mate.teke@gmail.com">mate.teke@gmail.com</a>.
Részletek a <a href="support.html">támogatási oldalon</a>.</p>

<footer>
  MealPilot ·
  <a href="privacy.html">Adatkezelési tájékoztató</a> ·
  <a href="terms.html">Felhasználási feltételek</a> ·
  <a href="delete-data.html">Adatok törlése</a> ·
  <a href="support.html">Támogatás</a> ·
  <a href="en/index.html">English</a>
</footer>

</body>
</html>
`,
  },
  'privacy.html': {
    contentType: 'text/html; charset=utf-8',
    body: `<!DOCTYPE html>
<html lang="hu">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>MealPilot — Adatkezelési tájékoztató</title>
<link rel="stylesheet" href="assets/site.css">
</head>
<body>

<nav class="nav">
  <strong>MealPilot</strong>
  <a href="index.html">Főoldal</a>
  <a href="support.html">Támogatás</a>
  <a href="privacy.html">Adatkezelés</a>
  <a href="terms.html">Feltételek</a>
  <a href="en/privacy.html">English</a>
</nav>

<h1>Adatkezelési tájékoztató</h1>
<p class="meta">MealPilot mobilalkalmazás · Hatályos: 2026. szeptember 11.</p>

<div class="draft">
  <strong>Tervezet.</strong> Ez a szöveg a valós működés alapján készült, de közzététel előtt
  jogi felülvizsgálatot igényel.
</div>

<h2>1. Az adatkezelő</h2>
<p>
  <strong>Teke Máté</strong> természetes személy adatkezelő<br>
  1158 Budapest, Neptun utca 88., 4. emelet 18.<br>
  Az adatkezelő magánszemélyként jár el, cégjegyzék- vagy nyilvántartási számmal nem rendelkezik.<br>
  Kapcsolat: <a href="mailto:mate.teke@gmail.com">mate.teke@gmail.com</a>
</p>

<h2>2. Milyen adatokat kezelünk</h2>
<p>Az alkalmazás nem kér regisztrációt, és nem hoz létre felhasználói fiókot. A következő
adatokat te magad adod meg, és alapértelmezetten a saját készülékeden maradnak:</p>
<ul>
  <li><strong>Egészségügyi és testadatok:</strong> biológiai nem, életkor, testmagasság,
    testsúly, testzsírszázalék, célsúly, mozgásszint, étrendi stílus, allergiák és
    intoleranciák.</li>
  <li><strong>Naplóadatok:</strong> elfogyasztott étkezések és azok tápértéke,
    testsúlymérések.</li>
  <li><strong>Az általad írt szabad szöveg:</strong> preferenciák és a beszélgetés üzenetei.</li>
  <li><strong>Alkalmazásbeállítások:</strong> emlékeztetők, megjelenítési beállítások.</li>
</ul>
<p>Az allergiákra és az egészségi állapotra vonatkozó adatok a GDPR 9. cikke szerinti
különleges kategóriájú adatnak minősülnek. Ezeket kizárólag a te kifejezett hozzájárulásod
alapján kezeljük, az étrend összeállítása céljából.</p>

<h2>3. Hol tároljuk</h2>
<p>Minden fent felsorolt adat a készüléked helyi adatbázisában tárolódik. Az étrendedről,
a naplóidról és a testadataidról <strong>nem készítünk szerveroldali másolatot</strong>, és
nem vezetünk felhasználói fiókot: nincs regisztráció, nincs jelszó, nincs e-mail-cím. Ha a
készülék biztonsági mentése be van kapcsolva, az adatok a saját Google-fiókod mentésébe
kerülhetnek — ez a te beállításod, nem a miénk.</p>
<p>A tervezést egy saját kiszolgálónk közvetíti (lásd 4. pont). Ez a kiszolgáló a következőket
tárolja, kizárólag a visszaélés megelőzése és a számlázás ellenőrzése céljából:</p>
<ul>
  <li>a telepítés véletlen azonosítójának egyirányú lenyomata (nem azonosít téged, és az
    adatok törlésekor vagy az app eltávolításakor újat kap);</li>
  <li>a Google Play vásárlási tokenjének egyirányú lenyomata és az előfizetés állapota;</li>
  <li>hívásonként az időpont, a feladat típusa és a felhasznált tokenek száma;</li>
  <li>havi összesítés a felhasznált keretről;</li>
  <li>hibajelentések és napi összesített használati számlálók (lásd 3/a. pont).</li>
</ul>
<p><strong>A kérés szövegét — tehát az étrendedet, az adataidat és az üzeneteidet — a
kiszolgáló nem írja le.</strong> Kivétel, ha te magad küldesz be bejelentést a „jelentés"
gombbal: ilyenkor a kifogásolt terv vagy fogás szövege is elmentésre kerül, hogy meg tudjuk
nézni, mi ment félre.</p>

<h2>3/a. Hibajelentés és névtelen statisztika</h2>
<p>Ha az alkalmazás összeomlik, a hiba leírása (a kivétel típusa, a hívási lánc, az
alkalmazás verziója, az Android verziója és a készülék típusa) a következő indításkor
elküldésre kerül a kiszolgálónkra. Emellett napi bontásban megszámoljuk, hogy hány terv,
naplóbejegyzés, üzenet és bejelentés készült.</p>
<p>Ez <strong>összesített darabszám</strong>, nem eseménynapló: nem tároljuk, mikor mi
történt, csak azt, hogy aznap hányszor. Étrend, étkezési napló, testsúly, allergia és a
beszélgetés tartalma <strong>nem</strong> kerül bele. Az adatokat nem osztjuk meg
hirdetőkkel vagy analitikai szolgáltatókkal; nem üzemeltetünk harmadik féltől származó
nyomkövetőt.</p>
<p>Ez a <strong>Beállításokban bármikor kikapcsolható</strong> („Hibajelentés és névtelen
statisztika"). Kikapcsolva az alkalmazás nem is gyűjti ezeket, nem csak a küldést hagyja
abba. A kezelés jogalapja a jogos érdek (a szolgáltatás hibamentes működtetése), amely
ellen ezzel a kapcsolóval tiltakozhatsz.</p>

<h2>4. Mi hagyja el a készüléket</h2>
<p>Étrend készítésekor és a beszélgetés használatakor a következő adatok kimennek a saját
kiszolgálónkon keresztül a tervezést végző szolgáltatóhoz (<strong>Anthropic PBC</strong>,
Egyesült Államok), hogy a terv elkészülhessen:</p>
<ul>
  <li>biológiai nem, életkor, testmagasság, testsúly, testzsírszázalék, célsúly, mozgásszint;</li>
  <li>a kiszámított kalória- és makrócélok;</li>
  <li>az étrendi kizárásaid és a szabad szöveges kéréseid;</li>
  <li>a beszélgetés üzenetei és a hozzájuk tartozó rövid összefoglaló a tervedről és a mai
    naplódról.</li>
</ul>
<p>A <strong>neved, e-mail-címed és pontos naplótörténeted nem kerül elküldésre.</strong>
Az adattovábbítás jogalapja a szerződés teljesítése (a szolgáltatás nyújtása), a különleges
kategóriájú adatok esetén a kifejezett hozzájárulásod.</p>
<p>Az Anthropic adatkezeléséről:
  <a href="https://www.anthropic.com/legal/privacy">anthropic.com/legal/privacy</a>.
  Az adattovábbítás az Egyesült Államokba az EU-USA adatvédelmi keret, illetve általános
  szerződési feltételek alapján történik.</p>

<h2>5. Fizetés</h2>
<p>Az előfizetést a Google Play kezeli. Bankkártya- és fizetési adatokhoz nem férünk hozzá,
azokat nem tároljuk. A kiszolgálónk a Google Play fejlesztői felületén keresztül azt az
információt kérdezi le, hogy az adott vásárlás érvényes-e, és ezt az állapotot tárolja
(lásd 3. pont). A Google adatkezeléséről:
  <a href="https://policies.google.com/privacy">policies.google.com/privacy</a>.</p>

<h2>6. Értesítések</h2>
<p>Az étkezési emlékeztetők a készüléken készülnek, nem szerverről érkeznek. Az értesítési
engedély bármikor visszavonható a rendszerbeállításokban.</p>

<h2>7. Meddig őrizzük meg</h2>
<p>A készüléken lévő adatok addig maradnak meg, amíg te nem törlöd őket. Az alkalmazás
eltávolítása minden helyi adatot töröl. Az alkalmazáson belül a <em>Beállítások → Jogi
tudnivalók és adatok → Minden adat törlése</em> ponttal bármikor egy lépésben törölhetsz
mindent.</p>
<p>A kiszolgálón tárolt adatok megőrzési ideje:</p>
<ul>
  <li>hibajelentések és használati számlálók: legfeljebb 12 hónap;</li>
  <li>a havi kerettel és az előfizetés állapotával kapcsolatos adatok: legfeljebb 24 hónap;</li>
  <li>az általad beküldött bejelentések: a kivizsgálás idejéig, legfeljebb 24 hónap.</li>
</ul>
<p>Részletes útmutató: <a href="delete-data.html">Adatok törlése</a>.</p>

<h2>8. Jogaid</h2>
<p>A GDPR alapján jogod van a hozzáféréshez, helyesbítéshez, törléshez, az adatkezelés
korlátozásához, az adathordozhatósághoz és a hozzájárulás visszavonásához. Mivel az adataid
a saját készülékeden vannak, ezek nagy részét közvetlenül te gyakorolod az alkalmazásban.
Bármilyen kérdéssel fordulj hozzánk a fenti e-mail-címen. Panasszal a Nemzeti Adatvédelmi és
Információszabadság Hatósághoz fordulhatsz (<a href="https://naih.hu">naih.hu</a>).</p>

<h2>9. Gyermekek</h2>
<p>Az alkalmazás 18 éven aluliak számára nem készült, és nem is gyűjtünk tudatosan adatot
tőlük.</p>

<h2>10. Változások</h2>
<p>A tájékoztató módosítása esetén a hatálybalépés dátumát frissítjük, és lényeges változásról
az alkalmazásban is tájékoztatunk.</p>

<footer>
  MealPilot ·
  <a href="privacy.html">Adatkezelési tájékoztató</a> ·
  <a href="terms.html">Felhasználási feltételek</a> ·
  <a href="delete-data.html">Adatok törlése</a> ·
  <a href="support.html">Támogatás</a> ·
  <a href="en/privacy.html">English</a>
</footer>

</body>
</html>
`,
  },
  'support.html': {
    contentType: 'text/html; charset=utf-8',
    body: `<!DOCTYPE html>
<html lang="hu">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>MealPilot — Támogatás</title>
<link rel="stylesheet" href="assets/site.css">
</head>
<body>

<nav class="nav">
  <strong>MealPilot</strong>
  <a href="index.html">Főoldal</a>
  <a href="support.html">Támogatás</a>
  <a href="privacy.html">Adatkezelés</a>
  <a href="terms.html">Feltételek</a>
  <a href="en/support.html">English</a>
</nav>

<h1>Támogatás</h1>
<p class="meta">Írj bátran — egy ember olvassa, nem egy ügyfélszolgálati rendszer.</p>

<div class="card">
  <h3>Kapcsolat</h3>
  <p><a href="mailto:mate.teke@gmail.com">mate.teke@gmail.com</a></p>
  <p>Ha hibát jelentesz, segít, ha leírod: milyen telefonod van, melyik alkalmazásverziót
  használod (Beállítások → Névjegy), és mit csináltál, amikor a hiba történt.</p>
</div>

<h2>Gyakori kérdések</h2>

<h3>Hibás egy fogás vagy egy tápérték. Mit tegyek?</h3>
<p>Használd az alkalmazásban a jelentés gombot: az étrend lapján a terv alatt, a fogás
részletes nézetében alul, a beszélgetésben pedig hosszan nyomva az üzenetre. Így a
kifogásolt szöveg is elérhetővé válik, és látjuk, mi ment félre. Ez a leggyorsabb út.</p>

<h3>Hogyan mondom le az előfizetést?</h3>
<p>Google Play alkalmazás → profilkép → Fizetések és előfizetések → Előfizetések →
MealPilot → Előfizetés lemondása. A már kifizetett időszak végéig a teljes csomag
megmarad. Az alkalmazás Beállítások oldalán is van egy gomb, ami ide vezet.</p>

<h3>Visszatérítést szeretnék.</h3>
<p>A vásárlást a Google Play kezeli, így a visszatérítést is: Google Play →
Rendelési előzmények → az adott tétel → Probléma bejelentése. Ha ez nem vezet
eredményre, írj nekünk, és megnézzük, mit tehetünk.</p>

<h3>Új telefonom lett. Átjönnek az adataim?</h3>
<p>Az étrend, a napló és a testadatok a készüléken vannak, és a Google biztonsági
mentésével átkerülhetnek. Az előfizetésed a Google-fiókodhoz tartozik, tehát az új
telefonon is él: nyisd meg a Beállításokat, és nyomd meg a „Vásárlás visszaállítása"
gombot.</p>

<h3>Az étrend nem veszi figyelembe az allergiámat.</h3>
<p>Ez súlyos hiba — kérjük, jelentsd az alkalmazásból, az „Olyat ajánlott, amit kizártam"
okkal. Addig is: a kizárásokat a Beállítások → Allergiák és kizárások alatt tudod
ellenőrizni, és a következő terv már azokkal készül. <strong>Az alapanyagok tényleges
összetételét a csomagoláson mindig ellenőrizd</strong> — az alkalmazás ezt nem tudja
helyetted megtenni.</p>

<h3>Lassú a tervezés.</h3>
<p>Egy hetes terv néhány tíz másodperc, egy hónapos több perc is lehet, mert szakaszokban
készül. Az első napok azonnal használhatók, a többi a háttérben töltődik — nyugodtan
kiléphetsz az alkalmazásból közben, a munka nem szakad meg.</p>

<h3>Nem akarom, hogy hibajelentést küldjön.</h3>
<p>Beállítások → Jogi tudnivalók és adatok → „Hibajelentés és névtelen statisztika"
kapcsoló. Kikapcsolva az alkalmazás nem is gyűjti ezeket.</p>

<h3>Törölni szeretném minden adatomat.</h3>
<p>Lásd az <a href="delete-data.html">adattörlési oldalt</a>.</p>

<footer>
  MealPilot ·
  <a href="privacy.html">Adatkezelési tájékoztató</a> ·
  <a href="terms.html">Felhasználási feltételek</a> ·
  <a href="delete-data.html">Adatok törlése</a> ·
  <a href="index.html">Főoldal</a> ·
  <a href="en/support.html">English</a>
</footer>

</body>
</html>
`,
  },
  'terms.html': {
    contentType: 'text/html; charset=utf-8',
    body: `<!DOCTYPE html>
<html lang="hu">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>MealPilot — Felhasználási feltételek</title>
<link rel="stylesheet" href="assets/site.css">
</head>
<body>

<nav class="nav">
  <strong>MealPilot</strong>
  <a href="index.html">Főoldal</a>
  <a href="support.html">Támogatás</a>
  <a href="privacy.html">Adatkezelés</a>
  <a href="terms.html">Feltételek</a>
  <a href="en/terms.html">English</a>
</nav>

<h1>Felhasználási feltételek</h1>
<p class="meta">MealPilot mobilalkalmazás · Hatályos: 2026. szeptember 11.</p>

<div class="draft">
  <strong>Tervezet.</strong> Közzététel előtt jogi felülvizsgálatot igényel.
</div>

<h2>1. A szolgáltatás</h2>
<p>A MealPilot egy mobilalkalmazás, amely a megadott testadataid alapján kalóriacélt számol,
étrendet állít össze, bevásárlólistát készít, és segít nyomon követni az étkezéseidet és
a testsúlyodat. Szolgáltató: <strong>Teke Máté</strong> természetes személy,
1158 Budapest, Neptun utca 88., 4. emelet 18.
Kapcsolat: <a href="mailto:mate.teke@gmail.com">mate.teke@gmail.com</a>.</p>

<div class="warn">
  <h2 style="margin-top:0">2. Nem orvosi tanács</h2>
  <p>Az alkalmazás tájékoztató jellegű, és <strong>nem minősül orvosi, dietetikai vagy
  egyéb egészségügyi tanácsadásnak</strong>. Az étrendeket gépi tervező állítja össze a
  megadott adataid alapján; ezek tartalmazhatnak hibát vagy pontatlanságot. Az alkalmazás
  nem alkalmas betegség megelőzésére, diagnosztizálására vagy kezelésére.</p>
  <p>Betegség, terhesség, szoptatás, evészavar, rendszeres gyógyszerszedés vagy 18 év alatti
  életkor esetén az étrend megkezdése előtt <strong>orvossal vagy dietetikussal kell
  egyeztetni</strong>. Az allergiákra vonatkozó adatok megadása a te felelősséged, és az
  alapanyagok tényleges összetételét minden esetben ellenőrizned kell a csomagoláson.</p>
</div>

<h2>3. Ki használhatja</h2>
<p>Az alkalmazás 18. életévüket betöltött személyek számára készült. A megadott adatok
valóságtartalmáért te felelsz — hibás adatokból hibás kalóriacél következik.</p>

<h2>4. Ingyenes és fizetős csomag</h2>
<p>Az alkalmazás alapfunkciói ingyenesen használhatók: naplózás, bevásárlólista,
emlékeztetők, a beépített receptekből készülő étrend, valamint havonta
meghatározott számú tervezés és üzenet.</p>
<p>A teljes csomag előfizetéssel érhető el, és korlátlan tervezést, korlátlan beszélgetést,
hosszabb terveket és az egyes napok átíratását teszi lehetővé.</p>

<h2>5. Előfizetés, megújulás, lemondás</h2>
<ul>
  <li>Az előfizetést a Google Play kezeli, és a Google Play fiókodat terheli.</li>
  <li>Az előfizetés a futamidő végén <strong>automatikusan megújul</strong>, amíg le nem mondod.</li>
  <li>A lemondás a Google Play → Előfizetések menüpontban bármikor elvégezhető, legkésőbb
    a megújulás előtt 24 órával.</li>
  <li>A már kifizetett, folyamatban lévő időszak a lemondás után is kihasználható.</li>
  <li>A visszatérítésekre a Google Play mindenkori szabályzata irányadó. Fogyasztóként az
    elállási jogodat a vonatkozó jogszabályok szerint gyakorolhatod.</li>
  <li>Az árakat megváltoztathatjuk; a változásról a Google Play szabályai szerint előre
    értesítünk, és a változás csak a következő megújulástól lép hatályba.</li>
</ul>

<h2>6. Helyes használat</h2>
<p>Nem használhatod az alkalmazást jogellenes célra, nem próbálhatod visszafejteni,
megkerülni a korlátozásait, vagy automatizált eszközökkel terhelni. A beszélgetés funkció
nem használható jogsértő, gyűlöletkeltő vagy önkárosító tartalom előállítására.</p>

<h2>7. Felelősség</h2>
<p>Az alkalmazást „adott állapotban” biztosítjuk. Nem vállalunk felelősséget azért, hogy az
étrend minden esetben hibátlan, teljes vagy az egyéni egészségi állapotodnak megfelelő. A
jogszabály által megengedett mértékig kizárjuk a felelősségünket az alkalmazás használatából
eredő közvetett károkért. Ez a korlátozás nem érinti a fogyasztókat megillető, jogszabályon
alapuló jogokat, és nem zárja ki a szándékosan vagy súlyos gondatlanságból okozott, illetve
az életet, testi épséget vagy egészséget károsító szerződésszegésért való felelősséget.</p>

<h2>8. Megszüntetés</h2>
<p>Bármikor abbahagyhatod a használatot és eltávolíthatod az alkalmazást. A feltételek súlyos
megsértése esetén a szolgáltatáshoz való hozzáférést korlátozhatjuk.</p>

<h2>9. Módosítás</h2>
<p>A feltételeket módosíthatjuk; a lényeges változásokról az alkalmazásban tájékoztatunk. A
módosítás utáni további használat a feltételek elfogadását jelenti.</p>

<h2>10. Alkalmazandó jog</h2>
<p>A feltételekre a magyar jog irányadó. Fogyasztói jogvita esetén a lakóhelyed szerinti
békéltető testülethez fordulhatsz.</p>

<h2>11. Kapcsolat</h2>
<p><a href="mailto:mate.teke@gmail.com">mate.teke@gmail.com</a></p>

<footer>
  MealPilot ·
  <a href="privacy.html">Adatkezelési tájékoztató</a> ·
  <a href="terms.html">Felhasználási feltételek</a> ·
  <a href="delete-data.html">Adatok törlése</a> ·
  <a href="support.html">Támogatás</a> ·
  <a href="en/terms.html">English</a>
</footer>

</body>
</html>
`,
  },
}
