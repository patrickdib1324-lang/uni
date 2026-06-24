# 📘 UniPortal — Beginner's Guide (how the whole app works)

This guide explains the prototype step by step, in junior-friendly words.
Read it top to bottom. Once you understand the **Login** flow, every other
feature works the *exact same way*.

---

┌─────────────────────────── BROWSER (index.html) ───────────────────────────┐
│  [ username ][ password ]  ( Sign In ) ──click──► doLogin()                  │
│                                                     │                        │
│                                       fetch('/api/login', POST, body)        │
└─────────────────────────────────────────────────────┼──────────────────────┘
                                                        │  HTTP request
                                                        ▼
┌─────────────────────────── SERVER (Server.java) ───────────────────────────┐
│  createContext("/api/login") ──► handleLogin(ex)                            │
│       readForm(ex)  → username, password                                    │
│       prepareStatement("SELECT ... WHERE username=? AND password=?")        │
│                                   │                                          │
└───────────────────────────────────┼─────────────────────────────────────────┘
                                     │  SQL query (JDBC)
                                     ▼
                        ┌──────────────────────────┐
                        │  DATABASE (users table)  │
                        │  finds row → role, name  │
                        └──────────────┬───────────┘
                                     │  row (or nothing)
                                     ▼
┌─────────────────────────── SERVER ─────────────────────────────────────────┐
│   rs.next()?  yes → send(200, {"ok":true,"role":"student",...})            │
│               no  → send(401, {"ok":false,"error":"Wrong..."})            │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                     │  HTTP response (JSON)
                                     ▼
┌─────────────────────────── BROWSER ────────────────────────────────────────┐
│   out = res.json()                                                          │
│   if(out.ok) → hide login, show app, applyRole(out)                        │
│                 └─► sidebar filled, menu hidden by role, Dashboard opens 🎉 │
└─────────────────────────────────────────────────────────────────────────────┘


## 0) The big picture — 3 parts talking to each other

```
   YOU (browser)            THE SERVER (Java)           THE DATABASE (PostgreSQL)
   index.html        →      Server.java          →      tables: users, students,
   (buttons, screens)  ←    (answers requests)   ←      courses, enrollments...
```

1. **Frontend** = `frontend/index.html` — what you see (buttons, forms, colors).
   Written in HTML (structure) + CSS (looks) + JavaScript (actions).
2. **Backend** = `server/Server.java` — a small program that listens on
   `http://localhost:8080` and answers requests like "check this login".
3. **Database** = PostgreSQL — where all the real data is stored permanently.

The frontend NEVER touches the database directly. It always asks the server,
and the server talks to the database. Like ordering at a restaurant:
**you (frontend) → waiter (server) → kitchen (database).**

---

## ⭐ THE PATTERN (memorize this — everything uses it)

Every feature = these 5 steps:

1. **Button** in HTML → `onclick="someFunction()"`
2. **JavaScript function** runs → grabs what you typed
3. **`fetch(...)`** sends it to the server (an "API call")
4. **Java handler** in `Server.java` runs the SQL on the database
5. **Answer comes back** → JavaScript shows the result on screen

---

## 1) When you open the website

- You go to `http://localhost:8080`.
- The server's `handleStatic` function sends back the file `index.html`.
- The browser draws it. At the start, the **login page is shown** and the
  **app is hidden**:

```html
<div id="loginPage" ...>   ...login screen...   </div>   <!-- visible -->
<div id="app" style="display:none">  ...dashboard...  </div>  <!-- hidden -->
```

Remember: both screens are always in the file. We just hide one and show
the other (no second page, no reload).

---

## 2) The Login — full walk-through of THE PATTERN

### Step 1 — the button (HTML)
```html
<input id="loginUser" placeholder="e.g. sara">
<input id="loginPass" type="password">
<button onclick="doLogin()">Sign In →</button>
```
"When the button is clicked, run `doLogin()`."

### Step 2 + 3 — the JavaScript function + sending to server
```js
async function doLogin(){
  // grab what the user typed
  const username = document.getElementById('loginUser').value;
  const password = document.getElementById('loginPass').value;

  // send it to the server and WAIT for the answer
  const res = await fetch('/api/login', {
    method:'POST',
    headers:{'Content-Type':'application/x-www-form-urlencoded'},
    body:`username=${username}&password=${password}`
  });
  const out = await res.json();   // turn the answer into a usable object

  // decide what to do with the answer
  if(out.ok){
    currentUser = out;                                  // remember who logged in
    document.getElementById('loginPage').style.display = 'none';   // hide login
    document.getElementById('app').style.display = 'block';        // show app
    applyRole(out);                                     // set up their menu
  } else {
    // show the error message in red
  }
}
```

New words:
- **`fetch`** = "send a message to the server."
- **`await`** = "wait here until the answer comes back."
- **`async`** = a function is allowed to use `await`.
- **`out.ok`** = the server's answer included `ok: true` or `ok: false`.

### Step 4 — the Java handler (server) checks the database
```java
static void handleLogin(HttpExchange ex){
   // read username + password the browser sent
   // ask the database: is there a user with this username AND password?
   "SELECT u.role, u.student_id, u.teacher_id, name FROM users u
    WHERE u.username = ? AND u.password = ?"
   // if found  -> send back {"ok":true, "role":"student", "name":"Sara"...}
   // if not    -> send back {"ok":false, "error":"Wrong username or password"}
}
```
The `?` are filled in safely with what the user typed (this prevents hacking).

### Step 5 — the answer comes back
- If `ok:true` → JavaScript hides the login, shows the app. You're in! 🎉
- If `ok:false` → JavaScript shows the red error. Try again.

**That's the entire pattern. Everything below is the SAME 5 steps.**

---

## 3) After login — roles decide what you see

`applyRole()` runs after a successful login. It looks at your `role`
(`student`, `teacher`, or `admin`) and **hides every menu item you're not
allowed to see**:

```js
document.querySelectorAll('[data-roles]').forEach(el => {
  const allowed = el.getAttribute('data-roles').split(',');
  el.style.display = allowed.includes(user.role) ? '' : 'none';
});
```

In the HTML, each menu item says who may see it:
```html
<div class="nav-item" data-roles="teacher,admin">Students</div>
<div class="nav-item" data-roles="admin">Requests</div>
```
So a student simply never sees the "Students" or "Requests" buttons.

---

## 4) The menu — same hide/show trick

Clicking a menu item calls `show('courses', this)`. The `show()` function:
1. hides ALL the view boxes,
2. shows only the one you clicked,
3. loads that page's data from the server if needed.

```js
function show(id, el){
  document.querySelectorAll('.view').forEach(v=>v.classList.remove('active')); // hide all
  document.getElementById(id).classList.add('active');                          // show one
  if(id === 'grades') loadGradesView();   // ask the server for grades
  if(id === 'students') loadStudents();   // ask the server for students
  // ...etc
}
```

---

## 5) Each feature = the same pattern. Here's the map.

| Feature | Button calls | Server endpoint | What the database does |
|---------|--------------|-----------------|------------------------|
| Log in | `doLogin()` | `/api/login` | find matching user |
| Request account | `doRegister()` | `/api/register` | save a "pending" request |
| Admin accepts/rejects sign-up | `decideAccount()` | `/api/account-decision` | create real user (or reject) |
| See courses to enroll | `loadEnrollList()` | `/api/courses` | list all courses |
| Enroll | `doEnroll()` | `/api/enroll` | add rows to `enrollments` |
| My courses | `loadMyCourses()` | `/api/student-courses` | my enrolled courses |
| Teacher sends a grade | `saveGrade()` | `/api/grade` | save a "pending" submission |
| Admin approves grade | `decide()` | `/api/submission-decision` | write the real grade |
| See grades | `loadGradesTable()` | `/api/grades` | list grades (filtered by role) |
| Students list | `loadStudents()` | `/api/students-overview` | list students + averages |

Open any one of these functions in `index.html` and you'll see the **exact
same shape** as `doLogin`: grab input → `fetch` → read answer → update screen.

---

## 6) Two special "approval" features (the smart parts)

**A) New sign-ups need admin approval** (stops fake accounts)
- A new student/teacher fills the form → `doRegister()` → server saves it as
  `pending` in the `account_requests` table. **No login is created yet.**
- The admin sees them under **Requests** (with a red badge) and clicks Accept
  → only THEN is a real `users` row created and they can log in.

**B) Teacher grades need admin approval**
- Teacher enters a grade → it's saved as `pending` in `grade_submissions`.
  The student does NOT see it yet.
- The admin sees it under **Grades → Pending Approvals** (red badge) and clicks
  Approve → only THEN is the real grade written and the student can see it.

Both use the same idea: **save as "pending" → admin decides → then make it real.**

---

## 7) The red notification badge

Every 7 seconds the app quietly asks the server "anything pending?" and shows
a red number on the menu if yes:
```js
setInterval(refreshNotifications, 7000);   // check again every 7 seconds
```
That's why the admin sees the red badge appear without refreshing.

---

## 8) The back button (login ↔ account toggle)
- **← Login** (in the app) = hide app, show login page, BUT keep you signed in.
- **← Back to my account** (on login page) = show the app again, no re-login.
- **🚪 logout** = really sign out (forgets `currentUser`).

---

## 9) How to run it
1. Make sure PostgreSQL is running (it starts with Windows).
2. Double-click **`start.bat`** (or run `java -cp "out;lib/postgresql.jar" Server`).
3. Open `http://localhost:8080`.
4. Log in — all usernames/passwords are in **`SECRET.md`**.

---

## 10) Where to look in the code
- **Screens & buttons** → `frontend/index.html` (top half = HTML, bottom half
  inside `<script>` = JavaScript).
- **Server answers** → `server/Server.java` (each `handleXxx` is one endpoint).
- **Database shape** → `database/*.sql` files.

👉 Tip: pick ONE feature, find its button in the HTML, then follow the 5 steps.
Do that 2-3 times and the whole app will "click". 🚀

---

# 📚 KEY CONCEPTS (the important things to remember)

These are the core ideas. If you understand these, you understand the whole app.

## A) The 3-tier architecture (3 layers, each with ONE job)

```
FRONTEND  ──fetch() / HTTP──►  BACKEND  ──connection + SQL query──►  DATABASE
(index.html)                  (Server.java)                        (PostgreSQL)
   looks          ◄── JSON ──     rules         ◄── rows ──          storage
```

- **Frontend** (`index.html`) = what you SEE and click. Job: show things, send requests.
- **Backend** (`Server.java`) = the trusted BRAIN. Job: enforce rules, talk to the DB.
- **Database** (PostgreSQL) = permanent MEMORY. Job: store data, answer queries.

**Golden rule:** the frontend can NEVER touch the database directly. It must go
through the backend, because the backend is the guard that checks the rules.

## B) The two "languages" (each hop speaks differently)

| Hop | Tool | Language | Answer comes back as |
|-----|------|----------|----------------------|
| Frontend → Backend | `fetch()` | **HTTP** | **JSON** (text) |
| Backend → Database | `db()` connection | **SQL** | **rows** (ResultSet) |

The backend is a **translator**: receives HTTP → speaks SQL → turns rows into JSON → sends HTTP back.

## C) What is JSON?
Data written as **labeled text**, so two programs can exchange it:
```json
{ "ok": true, "role": "student", "name": "Sara", "studentId": 1 }
```
- `{ }` = an object (group of facts), `[ ]` = a list, `"text"`, numbers, `true/false`, `null`.
- The backend BUILDS json text; the frontend reads it with `res.json()` into an object (`out`).
- The names (`ok`, `role`…) are YOUR choice — they just must MATCH on both sides.

## D) The "talk to backend" prototype (copy this shape every time)
**READ data (GET):**
```js
const res = await fetch('/api/SOMETHING');   // ask
const data = await res.json();               // read the answer
```
**SEND data (POST):**
```js
const res = await fetch('/api/SOMETHING', {
  method:'POST',                                                 // I'm sending data
  headers:{'Content-Type':'application/x-www-form-urlencoded'},
  body:`key1=value1&key2=value2`                                 // the data
});
const out = await res.json();
if(out.ok){ /* worked */ } else { /* show out.error */ }
```
Recipe to memorize: **fetch → json → check `out.ok`.**

## E) GET vs POST
- **GET** = "give me data" (just reading). No body. (e.g. `/api/courses`)
- **POST** = "here's data, save/do something" (changing). Has a body. (e.g. `/api/login`)

## F) `out.ok` is a VALUE, not a function
- No `()` = reading a value (`out.ok`, `out.role`).  With `()` = running a function (`doLogin()`).
- `out` is the object made from the server's JSON. `out.ok` = open the "ok" drawer.

## G) How login compares username + password
The comparison happens INSIDE the database, in the SQL `WHERE` clause:
```java
"... WHERE username = ? AND password = ?"   // the matching happens here
ps.setString(1, username);                   // fill the 1st ?  (a blank, filled safely)
ps.setString(2, password);                   // fill the 2nd ?
ResultSet rs = ps.executeQuery();
if (rs.next()) { /* a row was found → CORRECT login */ }
else           { /* no row → wrong username/password */ }
```
- **`=`** means "is equal to" → this is the actual MATCH.
- **`AND`** means BOTH must be true.
- **`?`** is a BLANK (placeholder), filled by `setString`. It is NOT the comparison — it's a safety tool.
- `rs.next()` true = found a match (login), false = no match (error).

## H) Why `?` placeholders matter (security)
Never glue user text straight into SQL. The `?` keeps input as plain DATA, never as
commands — this blocks **SQL injection** (a hacker typing SQL into a form to fool the DB).
> Frontend hiding = clean screen. Backend checks (`if (!role.equals("admin"))`) = the REAL lock.
> The browser can be tampered with, so security rules MUST live on the server.

## I) HTTP status codes used in this app
| Code | Meaning | When |
|------|---------|------|
| 200 | OK / success | it worked |
| 400 | Bad request | rule not met (e.g. under 15 credits) |
| 401 | Unauthorized | wrong username/password |
| 403 | Forbidden | not allowed (wrong role, pending account) |
| 404 | Not found | thing doesn't exist |
| 500 | Server error | something crashed |

## J) Show/hide trick (the whole UI is built on this)
There's ONE page. We hide one part and show another by changing `display`, using a CSS class:
```css
.view        { display:none; }    /* hidden by default */
.view.active { display:block; }   /* shown only if it ALSO has class "active" */
```
```js
el.classList.add('active');     // → matches .view.active → SHOWN
el.classList.remove('active');  // → just .view           → HIDDEN
```
- `.view.active` (no space) = an element with BOTH classes.
- Roles use the same idea: `display = allowed.includes(user.role) ? '' : 'none'`
  (`? :` is a one-line if/else: condition ? valueIfTrue : valueIfFalse).

## K) Authentication vs Authorization
- **Authentication** = "are you really you?" (username + password check).
- **Authorization** = "what are you allowed to do?" (your `role` decides menu + powers).

## L) Where the ROLE comes from (source of truth = database)
```
users.role column (DB) → handleLogin SELECT → JSON "role":"student"
  → out.role → currentUser → applyRole(user) → user.role
```
The frontend never decides the role; it only RECEIVES it.

## M) If you want to CHANGE something — which file?
Ask: *is this about looks, rules, or storage?*
- **Looks / screen / buttons** → `frontend/index.html`
- **Rules / security / logic** → `server/Server.java`
- **Stored data / table shape** → `database/*.sql`
(Adding a role/feature often touches all three.)

## N) Honest "real world" note
Passwords are stored as PLAIN TEXT here (fine for learning, unsafe for production).
The professional fix is **hashing** (store a scrambled version, compare scrambled).
A good engineer knows the shortcut they took and why the real version exists.

