# 🚀 Putting UniPortal Online (beginner guide — Render.com)

Goal: turn "works on my laptop" into a public `https://...` website.
We use **Render.com** because it's free, browser-based, gives HTTPS automatically,
and can host both the app and the database.

Your project is already prepared:
- DB login reads from `secret.properties` / environment variables (no secrets in code)
- `PORT` is read from the environment (cloud picks the port)
- `Dockerfile` tells Render how to build & run it
- `.gitignore` keeps `secret.properties` and `SECRET.md` private

---

## STEP 1 — Put your code on GitHub
Render deploys from a GitHub repo.
1. Make a free account at https://github.com
2. Install Git: https://git-scm.com/download/win
3. In your project folder, run:
   ```
   git init
   git add .
   git commit -m "UniPortal"
   ```
   (Thanks to `.gitignore`, `secret.properties` and `SECRET.md` are NOT included. Good.)
4. Create a new empty repo on GitHub, then run the two commands GitHub shows you
   (they look like):
   ```
   git remote add origin https://github.com/YOUR_NAME/university-app.git
   git push -u origin main
   ```

## STEP 2 — Create the database on Render
1. Make a free account at https://render.com
2. Click **New → PostgreSQL**. Give it a name, click **Create Database**.
3. Wait until it's ready, then copy these values from the database page:
   - **Hostname**, **Port** (5432), **Database**, **Username**, **Password**

## STEP 3 — Load your tables into the cloud database
Your cloud database is empty — load your SQL files into it.
- On the Render database page, find the **PSQL Command** (or "External Connection").
- Run your files against it, in this order:
  ```
  psql "THE_EXTERNAL_CONNECTION_STRING" -f database/schema.sql
  psql "THE_EXTERNAL_CONNECTION_STRING" -f database/users.sql
  psql "THE_EXTERNAL_CONNECTION_STRING" -f database/teachers.sql
  psql "THE_EXTERNAL_CONNECTION_STRING" -f database/approvals.sql
  psql "THE_EXTERNAL_CONNECTION_STRING" -f database/accounts.sql
  ```

## STEP 4 — Create the web service (your app)
1. On Render, click **New → Web Service**.
2. Connect your GitHub repo.
3. Render sees the **Dockerfile** and uses it automatically.
4. Pick the free plan, click create.

## STEP 5 — Give the app its secrets (environment variables)
On the web service → **Environment** → add these (from Step 2):
```
DB_URL  = jdbc:postgresql://HOSTNAME:5432/DATABASE
DB_USER = USERNAME
DB_PASS = PASSWORD
```
(You do NOT set PORT — Render sets it for you. The code already reads it.)

## STEP 6 — Deploy 🎉
Render builds and starts it. You get a public URL like:
```
https://university-app.onrender.com
```
It already has HTTPS (the padlock). Open it and log in.

## STEP 7 (optional) — Use your own .com
1. Buy a domain (Namecheap / Cloudflare / Porkbun, ~$10/year).
2. On Render: web service → **Settings → Custom Domain** → add `myuniportal.com`.
3. Render tells you a DNS record to add at your domain registrar. Add it.
4. Wait a few minutes → your site is live at `https://myuniportal.com` (HTTPS auto).

---

## ⚠️ BEFORE real users (security to-do)
This project is a learning demo. Before inviting real people:
1. **Hash passwords** (users' passwords are still plain text in the DB).
2. **Use sessions/tokens** instead of trusting role/teacherId sent by the browser.
3. Keep `secret.properties` and `SECRET.md` private (already gitignored).

For a class demo / portfolio, Steps 1–6 are enough to show it working live.

---

## 💡 Notes
- Free Render services "sleep" after inactivity and take ~30s to wake on the next visit.
- Free PostgreSQL has size/time limits — fine for a demo, not for production.
- Every time you `git push`, Render automatically re-deploys the new version.
