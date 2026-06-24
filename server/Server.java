// ===== IMPORTS: ready-made Java toolboxes we borrow code from =====
import com.sun.net.httpserver.*;        // the tiny built-in web server (handles http://localhost:8080)
import java.io.*;                        // reading/writing data streams (input/output)
import java.net.InetSocketAddress;      // lets us say "listen on port 8080"
import java.nio.charset.StandardCharsets;// text encoding (UTF-8 = supports emojis & accents)
import java.nio.file.*;                  // reading files from disk (like index.html)
import java.sql.*;                       // talking to the database (SQL)
import java.util.*;                      // helpers like Map and HashMap (key→value boxes)

/*
 * Tiny university backend — NO Gradle, NO Spring.
 * Compile:  javac -cp lib/postgresql.jar -d out server/Server.java
 * Run:      java  -cp "out;lib/postgresql.jar" Server
 * Open:     http://localhost:8080
 */
public class Server {                    // our program. "public class Server" must match the file name Server.java

    // ===== SETTINGS: the database login is READ from secret.properties (not written here) =====
    static final Properties cfg = loadConfig();      // load the secret file once at startup
    static final String DB_URL  = conf("DB_URL",  "jdbc:postgresql://localhost:5432/university"); // address of the database
    static final String DB_USER = conf("DB_USER", "postgres");      // database username (from secret file)
    static final String DB_PASS = conf("DB_PASS", "");             // database password (from secret file)
    static final Path FRONTEND  = Paths.get("frontend");   // the folder where index.html lives

    // read secret.properties from disk; if it's missing, just use an empty config (defaults will apply)
    static Properties loadConfig() {
        Properties p = new Properties();                       // an empty key→value holder
        try (InputStream in = new FileInputStream("secret.properties")) { // try to open the secret file
            p.load(in);                                        // read all DB_URL=... lines into p
        } catch (Exception e) {
            System.out.println("⚠ secret.properties not found — using default DB settings");
        }
        return p;                                              // give back whatever we loaded
    }

    // pick a setting: 1) environment variable wins, 2) then the secret file, 3) then the default
    static String conf(String key, String def) {
        String env = System.getenv(key);                       // check the environment first (good for cloud)
        if (env != null && !env.isBlank()) return env;         // use it if present
        return cfg.getProperty(key, def);                      // else use the secret file, else the default
    }

    // ===== main(): the FIRST thing that runs when you start the program =====
    public static void main(String[] args) throws Exception {
        Class.forName("org.postgresql.Driver");   // load the PostgreSQL driver (the "translator" to the DB)
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080")); // cloud hosts tell us the port via PORT; locally use 8080
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0); // create the web server on that port

        // Each line below means: "when a request comes to this web address, run that function."
        server.createContext("/api/courses",         Server::handleCourses);         // list / add courses
        server.createContext("/api/students",        Server::handleStudents);        // list students (simple)
        server.createContext("/api/grades",          Server::handleGrades);          // list grades
        server.createContext("/api/grade",           Server::handleEnterGrade);      // teacher submits / admin saves a grade
        server.createContext("/api/gpa",             Server::handleGpa);             // each student's GPA
        server.createContext("/api/enroll",          Server::handleEnroll);          // student enrolls in courses
        server.createContext("/api/student-courses", Server::handleStudentCourses);  // one student's courses
        server.createContext("/api/teacher-courses", Server::handleTeacherCourses);  // one teacher's own courses
        server.createContext("/api/course-students", Server::handleCourseStudents);  // students inside one course
        server.createContext("/api/submissions",        Server::handleSubmissions);        // grades a teacher sent the admin
        server.createContext("/api/submission-decision",Server::handleSubmissionDecision); // admin approves/rejects a grade
        server.createContext("/api/students-overview",  Server::handleStudentsOverview);   // students + stats per role
        server.createContext("/api/account-requests",   Server::handleAccountRequests);    // pending sign-ups (admin)
        server.createContext("/api/account-decision",   Server::handleAccountDecision);    // admin accepts/rejects a sign-up
        server.createContext("/api/login",           Server::handleLogin);           // check username + password
        server.createContext("/api/register",        Server::handleRegister);        // new student/teacher requests an account
        server.createContext("/",                    Server::handleStatic);          // serve the website files

        server.setExecutor(null);   // use the default way of handling requests (one at a time, simple)
        server.start();             // actually TURN ON the server
        System.out.println("✅ Backend running on port " + port); // print a message to the console
    }

    // ====================================================================
    //  HANDLERS — each one answers ONE web address (endpoint)
    // ====================================================================

    // ---------- GET/POST /api/courses : list all courses, or add a new one ----------
    static void handleCourses(HttpExchange ex) throws IOException {   // ex = the incoming request + the reply we'll send
        try (Connection c = db()) {                                  // open a database connection (auto-closes at the end)
            if (ex.getRequestMethod().equals("POST")) {             // POST = "add something" (here: add a course)
                Map<String,String> f = readForm(ex);                // read the form fields the browser sent
                PreparedStatement ps = c.prepareStatement(          // prepare a safe SQL command with blanks (?)
                    "INSERT INTO courses(code,title,teacher,credits) VALUES (?,?,?,?)");
                ps.setString(1, f.get("code"));                     // fill blank #1 with the course code
                ps.setString(2, f.get("title"));                    // fill blank #2 with the title
                ps.setString(3, f.get("teacher"));                  // fill blank #3 with the teacher name
                ps.setInt(4, Integer.parseInt(f.getOrDefault("credits","3"))); // blank #4 = credits (default 3)
                ps.executeUpdate();                                 // run the INSERT (actually save it)
                send(ex, 200, "{\"ok\":true}");                     // reply "ok" (200 = success)
                return;                                             // stop here (don't run the GET part below)
            }
            // ---- GET = "give me the list" ----
            StringBuilder j = new StringBuilder("[");               // we build a JSON text by hand; start a list with "["
            ResultSet rs = c.createStatement().executeQuery(        // run a SELECT and get the rows back
                "SELECT id,code,title,teacher,credits FROM courses ORDER BY code");
            boolean first = true;                                   // track whether this is the first row (for commas)
            while (rs.next()) {                                     // loop through every row the database returned
                if (!first) j.append(","); first = false;          // add a comma between items (but not before the first)
                j.append("{")                                       // start one course object  {
                 .append("\"id\":").append(rs.getInt("id")).append(",")          // "id": 1,
                 .append("\"code\":\"").append(esc(rs.getString("code"))).append("\",")     // "code":"CS101",
                 .append("\"title\":\"").append(esc(rs.getString("title"))).append("\",")   // "title":"...",
                 .append("\"teacher\":\"").append(esc(rs.getString("teacher"))).append("\",")// "teacher":"...",
                 .append("\"credits\":").append(rs.getInt("credits"))            // "credits":3
                 .append("}");                                      // close the object  }
            }
            j.append("]");                                          // close the list  ]
            sendJson(ex, j.toString());                            // send the finished JSON text back to the browser
        } catch (Exception e) { error(ex, e); }                    // if anything breaks, send an error reply
    }

    // ---------- GET /api/students : simple list of all students ----------
    // (Same shape as handleCourses' GET part: run SELECT → loop rows → build JSON → send.)
    static void handleStudents(HttpExchange ex) throws IOException {
        try (Connection c = db()) {                                 // open DB connection
            StringBuilder j = new StringBuilder("[");               // start JSON list
            ResultSet rs = c.createStatement().executeQuery(        // ask for all students
                "SELECT id,name,email,major FROM students ORDER BY id");
            boolean first = true;                                   // comma helper
            while (rs.next()) {                                     // for each student row...
                if (!first) j.append(","); first = false;          // comma between items
                j.append("{\"id\":").append(rs.getInt("id"))                                  // "id":..
                 .append(",\"name\":\"").append(esc(rs.getString("name"))).append("\"")       // "name":".."
                 .append(",\"email\":\"").append(esc(rs.getString("email"))).append("\"")     // "email":".."
                 .append(",\"major\":\"").append(esc(rs.getString("major"))).append("\"}");   // "major":".."
            }
            j.append("]");                                          // close list
            sendJson(ex, j.toString());                            // send it
        } catch (Exception e) { error(ex, e); }
    }

    // ---------- GET /api/grades : list grades, FILTERED by who is asking ----------
    //   ?role=student&studentId=1  -> only that student's grades
    //   ?role=teacher&teacherId=2  -> only grades for that teacher's courses
    //   ?role=admin                -> every grade
    static void handleGrades(HttpExchange ex) throws IOException {
        try (Connection c = db()) {
            Map<String,String> q = query(ex);                       // read the ?role=...&studentId=... from the web address
            String role = q.getOrDefault("role", "");               // who is asking? (student/teacher/admin)
            String sql =                                            // start building the SQL text
                "SELECT s.name AS student, co.code, co.title, e.score " +   // columns we want
                "FROM enrollments e " +                             // main table: enrollments (who took what)
                "JOIN students s ON s.id=e.student_id " +           // attach the student's name
                "JOIN courses co ON co.id=e.course_id ";            // attach the course info
            if (role.equals("student")) sql += "WHERE e.student_id=? "; // student: only their own rows
            else if (role.equals("teacher")) sql += "WHERE co.teacher_id=? "; // teacher: only their courses
            sql += "ORDER BY s.name, co.code";                      // sort the result nicely

            PreparedStatement ps = c.prepareStatement(sql);         // prepare the (maybe filtered) query
            if (role.equals("student"))      ps.setInt(1, Integer.parseInt(q.getOrDefault("studentId","0"))); // fill the ? with studentId
            else if (role.equals("teacher")) ps.setInt(1, Integer.parseInt(q.getOrDefault("teacherId","0"))); // or with teacherId

            StringBuilder j = new StringBuilder("[");               // build JSON list
            ResultSet rs = ps.executeQuery();                       // run the SELECT
            boolean first = true;                                   // comma helper
            while (rs.next()) {                                     // for each grade row...
                if (!first) j.append(","); first = false;          // comma between items
                Object sc = rs.getObject("score");                 // score may be empty (NULL) if not graded yet
                j.append("{\"student\":\"").append(esc(rs.getString("student"))).append("\"")  // "student":".."
                 .append(",\"code\":\"").append(esc(rs.getString("code"))).append("\"")        // "code":".."
                 .append(",\"title\":\"").append(esc(rs.getString("title"))).append("\"")      // "title":".."
                 .append(",\"score\":").append(sc==null ? "null" : sc.toString())              // "score": number OR null
                 .append("}");
            }
            j.append("]");
            sendJson(ex, j.toString());
        } catch (Exception e) { error(ex, e); }
    }

    // ---------- POST /api/grade : a teacher SUBMITS a grade, or an admin SAVES it ----------
    static void handleEnterGrade(HttpExchange ex) throws IOException {
        try (Connection c = db()) {
            Map<String,String> f = readForm(ex);                    // read what the browser sent
            int studentId = Integer.parseInt(f.get("studentId"));   // which student (turn text "3" into number 3)
            int courseId  = Integer.parseInt(f.get("courseId"));    // which course
            double score  = Double.parseDouble(f.get("score"));     // the grade (allows decimals like 88.5)

            String role = f.getOrDefault("role", "");               // who is sending this? teacher or admin

            // ---- TEACHER: does NOT save the real grade. It is SENT to the admin as "pending". ----
            if (role.equals("teacher")) {
                int teacherId = Integer.parseInt(f.getOrDefault("teacherId", "-1")); // which teacher
                // SECURITY CHECK: is this course really taught by this teacher?
                PreparedStatement own = c.prepareStatement(
                    "SELECT 1 FROM courses WHERE id=? AND teacher_id=?");
                own.setInt(1, courseId); own.setInt(2, teacherId); // fill the two blanks
                if (!own.executeQuery().next()) {                  // .next() is false = no matching row = NOT their course
                    send(ex, 403, "{\"ok\":false,\"error\":\"You can only grade courses you teach\"}"); // 403 = forbidden
                    return;                                        // stop
                }
                // If they already sent a pending grade for this student+course, just UPDATE it...
                PreparedStatement upd = c.prepareStatement(
                    "UPDATE grade_submissions SET score=?, submitted_at=now() " +
                    "WHERE student_id=? AND course_id=? AND teacher_id=? AND status='pending'");
                upd.setDouble(1, score); upd.setInt(2, studentId); // fill blanks
                upd.setInt(3, courseId); upd.setInt(4, teacherId);
                if (upd.executeUpdate() == 0) {                    // 0 = nothing was updated = none existed yet → INSERT a new one
                    PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO grade_submissions(student_id,course_id,teacher_id,score) " +
                        "VALUES (?,?,?,?)");
                    ins.setInt(1, studentId); ins.setInt(2, courseId);
                    ins.setInt(3, teacherId); ins.setDouble(4, score);
                    ins.executeUpdate();                           // save the new pending submission
                }
                send(ex, 200, "{\"ok\":true,\"submitted\":true}"); // tell the browser "sent to admin"
                return;                                            // stop (don't run the admin part)
            }

            // ---- ADMIN: writes the grade STRAIGHT into the official table (enrollments). ----
            PreparedStatement up = c.prepareStatement(             // try to UPDATE the existing enrollment's score
                "UPDATE enrollments SET score=? WHERE student_id=? AND course_id=?");
            up.setDouble(1, score); up.setInt(2, studentId); up.setInt(3, courseId);
            int rows = up.executeUpdate();                         // how many rows were changed?
            if (rows == 0) {                                       // 0 = they weren't enrolled yet → create the enrollment with the grade
                PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO enrollments(student_id,course_id,score) VALUES (?,?,?)");
                ins.setInt(1, studentId); ins.setInt(2, courseId); ins.setDouble(3, score);
                ins.executeUpdate();
            }
            send(ex, 200, "{\"ok\":true}");                        // reply ok
        } catch (Exception e) { error(ex, e); }
    }

    // ---------- GET /api/gpa : each student's GPA (computed by a database VIEW) ----------
    // (Same list shape again: SELECT → loop → build JSON → send.)
    static void handleGpa(HttpExchange ex) throws IOException {
        try (Connection c = db()) {
            StringBuilder j = new StringBuilder("[");
            ResultSet rs = c.createStatement().executeQuery(        // student_gpa is a saved query (a VIEW) in the DB
                "SELECT name, courses_passed, total_credits, gpa FROM student_gpa ORDER BY gpa DESC");
            boolean first = true;
            while (rs.next()) {
                if (!first) j.append(","); first = false;
                Object gpa = rs.getObject("gpa");                   // gpa can be NULL (no graded courses)
                j.append("{\"name\":\"").append(esc(rs.getString("name"))).append("\"")
                 .append(",\"coursesPassed\":").append(rs.getInt("courses_passed"))
                 .append(",\"totalCredits\":").append(rs.getInt("total_credits"))
                 .append(",\"gpa\":").append(gpa==null ? "null" : gpa.toString())
                 .append("}");
            }
            j.append("]");
            sendJson(ex, j.toString());
        } catch (Exception e) { error(ex, e); }
    }

    // ---------- POST /api/enroll : a student picks courses (must total at least 15 credits) ----------
    static void handleEnroll(HttpExchange ex) throws IOException {
        try (Connection c = db()) {
            Map<String,String> f = readForm(ex);                    // read the form
            int studentId = Integer.parseInt(f.get("studentId"));   // who is enrolling
            String[] courseIds = f.getOrDefault("courseIds","").split(","); // "1,3,5" → ["1","3","5"]

            // First, add up the credits of all chosen courses.
            int totalCredits = 0;                                   // running total
            for (String cid : courseIds) {                          // look at each chosen course id
                if (cid.isBlank()) continue;                        // skip empty pieces
                PreparedStatement cs = c.prepareStatement("SELECT credits FROM courses WHERE id=?");
                cs.setInt(1, Integer.parseInt(cid));               // ask the DB for that course's credits
                ResultSet r = cs.executeQuery();
                if (r.next()) totalCredits += r.getInt(1);         // add its credits to the total
            }
            // RULE: refuse if it's under 15 credits.
            if (totalCredits < 15) {
                send(ex, 400, "{\"ok\":false,\"error\":\"Need at least 15 credits, you chose "
                        + totalCredits + "\"}");                    // 400 = bad request (rule not met)
                return;
            }
            // Now actually save each enrollment (score left empty = not graded yet).
            for (String cid : courseIds) {
                if (cid.isBlank()) continue;
                PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO enrollments(student_id,course_id) VALUES (?,?) " +
                    "ON CONFLICT (student_id,course_id) DO NOTHING"); // if already enrolled, skip quietly
                ps.setInt(1, studentId); ps.setInt(2, Integer.parseInt(cid));
                ps.executeUpdate();
            }
            send(ex, 200, "{\"ok\":true,\"credits\":" + totalCredits + "}"); // reply ok + how many credits
        } catch (Exception e) { error(ex, e); }
    }

    // ---------- GET /api/student-courses?studentId=1 : one student's enrolled courses ----------
    // (List shape; the only new idea is reading ?studentId= from the address with query(ex).)
    static void handleStudentCourses(HttpExchange ex) throws IOException {
        try (Connection c = db()) {
            int studentId = Integer.parseInt(query(ex).getOrDefault("studentId","0")); // read ?studentId=
            PreparedStatement ps = c.prepareStatement(
                "SELECT co.id, co.code, co.title, co.credits, e.score " +
                "FROM enrollments e JOIN courses co ON co.id=e.course_id " +
                "WHERE e.student_id=? ORDER BY co.code");
            ps.setInt(1, studentId);                               // fill the ? with the student id
            ResultSet rs = ps.executeQuery();
            StringBuilder j = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) j.append(","); first = false;
                Object sc = rs.getObject("score");                 // score may be NULL
                j.append("{\"id\":").append(rs.getInt("id"))
                 .append(",\"code\":\"").append(esc(rs.getString("code"))).append("\"")
                 .append(",\"title\":\"").append(esc(rs.getString("title"))).append("\"")
                 .append(",\"credits\":").append(rs.getInt("credits"))
                 .append(",\"score\":").append(sc==null ? "null" : sc.toString())
                 .append("}");
            }
            j.append("]");
            sendJson(ex, j.toString());
        } catch (Exception e) { error(ex, e); }
    }

    // ---------- GET /api/teacher-courses?teacherId=2 : the courses one teacher teaches ----------
    // (Same list shape as handleStudentCourses, different SELECT.)
    static void handleTeacherCourses(HttpExchange ex) throws IOException {
        try (Connection c = db()) {
            int teacherId = Integer.parseInt(query(ex).getOrDefault("teacherId","0")); // read ?teacherId=
            PreparedStatement ps = c.prepareStatement(
                "SELECT id, code, title, credits FROM courses WHERE teacher_id=? ORDER BY code");
            ps.setInt(1, teacherId);
            ResultSet rs = ps.executeQuery();
            StringBuilder j = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) j.append(","); first = false;
                j.append("{\"id\":").append(rs.getInt("id"))
                 .append(",\"code\":\"").append(esc(rs.getString("code"))).append("\"")
                 .append(",\"title\":\"").append(esc(rs.getString("title"))).append("\"")
                 .append(",\"credits\":").append(rs.getInt("credits"))
                 .append("}");
            }
            j.append("]");
            sendJson(ex, j.toString());
        } catch (Exception e) { error(ex, e); }
    }

    // ---------- GET /api/course-students?courseId=1 : students inside one course (+ their score) ----------
    // (Same list shape, different SELECT.)
    static void handleCourseStudents(HttpExchange ex) throws IOException {
        try (Connection c = db()) {
            int courseId = Integer.parseInt(query(ex).getOrDefault("courseId","0")); // read ?courseId=
            PreparedStatement ps = c.prepareStatement(
                "SELECT s.id, s.name, e.score " +
                "FROM enrollments e JOIN students s ON s.id=e.student_id " +
                "WHERE e.course_id=? ORDER BY s.name");
            ps.setInt(1, courseId);
            ResultSet rs = ps.executeQuery();
            StringBuilder j = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) j.append(","); first = false;
                Object sc = rs.getObject("score");
                j.append("{\"id\":").append(rs.getInt("id"))
                 .append(",\"name\":\"").append(esc(rs.getString("name"))).append("\"")
                 .append(",\"score\":").append(sc==null ? "null" : sc.toString())
                 .append("}");
            }
            j.append("]");
            sendJson(ex, j.toString());
        } catch (Exception e) { error(ex, e); }
    }

    // ---------- GET /api/students-overview : students + stats (admin = all, teacher = only own) ----------
    static void handleStudentsOverview(HttpExchange ex) throws IOException {
        try (Connection c = db()) {
            Map<String,String> q = query(ex);                       // read the address parameters
            boolean byTeacher = q.containsKey("teacherId");         // did they pass ?teacherId= ? then it's a teacher
            String sql =
                "SELECT s.id, s.name, s.email, " +
                "       COUNT(e.id) AS courses, ROUND(AVG(e.score),0) AS avg " + // COUNT = how many, AVG = average score
                "FROM students s " +
                (byTeacher ? "JOIN" : "LEFT JOIN") + " enrollments e ON e.student_id=s.id "; // teacher: only enrolled; admin: everyone
            if (byTeacher) sql += "JOIN courses co ON co.id=e.course_id WHERE co.teacher_id=? "; // teacher: limit to their courses
            sql += "GROUP BY s.id, s.name, s.email ORDER BY s.name"; // GROUP BY = one row per student (needed for COUNT/AVG)

            PreparedStatement ps = c.prepareStatement(sql);
            if (byTeacher) ps.setInt(1, Integer.parseInt(q.get("teacherId"))); // fill the ? only when filtering by teacher

            ResultSet rs = ps.executeQuery();
            StringBuilder j = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) j.append(","); first = false;
                Object avg = rs.getObject("avg");                  // average can be NULL (no grades)
                j.append("{\"id\":").append(rs.getInt("id"))
                 .append(",\"name\":\"").append(esc(rs.getString("name"))).append("\"")
                 .append(",\"email\":\"").append(esc(rs.getString("email"))).append("\"")
                 .append(",\"courses\":").append(rs.getInt("courses"))
                 .append(",\"avg\":").append(avg==null ? "null" : avg.toString())
                 .append("}");
            }
            j.append("]");
            sendJson(ex, j.toString());
        } catch (Exception e) { error(ex, e); }
    }

    // ---------- GET /api/submissions : grades teachers SENT the admin ----------
    //   ?status=pending  -> admin's inbox (waiting grades)
    //   ?teacherId=2     -> one teacher's own submissions (to see approved/rejected)
    static void handleSubmissions(HttpExchange ex) throws IOException {
        try (Connection c = db()) {
            Map<String,String> q = query(ex);
            String sql =                                            // join 3 tables to show readable names
                "SELECT gs.id, gs.score, gs.status, gs.submitted_at, " +
                "       s.name AS student, co.code, co.title, t.name AS teacher " +
                "FROM grade_submissions gs " +
                "JOIN students s ON s.id=gs.student_id " +
                "JOIN courses  co ON co.id=gs.course_id " +
                "JOIN teachers t ON t.id=gs.teacher_id ";
            boolean byStatus = q.containsKey("status");             // filtering by status? (pending)
            boolean byTeacher = q.containsKey("teacherId");         // or by teacher?
            if (byStatus)       sql += "WHERE gs.status=? ";        // only that status
            else if (byTeacher) sql += "WHERE gs.teacher_id=? ";    // only that teacher
            sql += "ORDER BY gs.submitted_at DESC";                 // newest first

            PreparedStatement ps = c.prepareStatement(sql);
            if (byStatus)       ps.setString(1, q.get("status"));               // fill ? with the status text
            else if (byTeacher) ps.setInt(1, Integer.parseInt(q.get("teacherId"))); // or with teacher id

            ResultSet rs = ps.executeQuery();
            StringBuilder j = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) j.append(","); first = false;
                java.sql.Timestamp ts = rs.getTimestamp("submitted_at"); // the date/time it was sent
                j.append("{\"id\":").append(rs.getInt("id"))
                 .append(",\"student\":\"").append(esc(rs.getString("student"))).append("\"")
                 .append(",\"teacher\":\"").append(esc(rs.getString("teacher"))).append("\"")
                 .append(",\"code\":\"").append(esc(rs.getString("code"))).append("\"")
                 .append(",\"title\":\"").append(esc(rs.getString("title"))).append("\"")
                 .append(",\"score\":").append(rs.getBigDecimal("score").toString())
                 .append(",\"status\":\"").append(esc(rs.getString("status"))).append("\"")
                 .append(",\"submittedAt\":\"").append(ts==null?"":esc(ts.toString())).append("\"")
                 .append("}");
            }
            j.append("]");
            sendJson(ex, j.toString());
        } catch (Exception e) { error(ex, e); }
    }

    // ---------- POST /api/submission-decision : ADMIN approves/rejects a teacher's grade ----------
    static void handleSubmissionDecision(HttpExchange ex) throws IOException {
        try (Connection c = db()) {
            Map<String,String> f = readForm(ex);
            if (!f.getOrDefault("role","").equals("admin")) {       // only the admin is allowed here
                send(ex, 403, "{\"ok\":false,\"error\":\"Only the admin can approve grades\"}");
                return;
            }
            int subId = Integer.parseInt(f.get("submissionId"));    // which submission
            String decision = f.getOrDefault("decision","");        // "approve" or "reject"

            // Look up that pending submission to get its student/course/score.
            PreparedStatement get = c.prepareStatement(
                "SELECT student_id, course_id, score FROM grade_submissions " +
                "WHERE id=? AND status='pending'");
            get.setInt(1, subId);
            ResultSet rs = get.executeQuery();
            if (!rs.next()) {                                       // not found (maybe already decided)
                send(ex, 404, "{\"ok\":false,\"error\":\"No pending submission with that id\"}");
                return;
            }
            int studentId = rs.getInt("student_id");                // read the values out of the found row
            int courseId  = rs.getInt("course_id");
            double score  = rs.getDouble("score");

            if (decision.equals("approve")) {                       // ===== APPROVE =====
                // write the score into the official enrollments table (UPDATE, or INSERT if needed)
                PreparedStatement up = c.prepareStatement(
                    "UPDATE enrollments SET score=? WHERE student_id=? AND course_id=?");
                up.setDouble(1, score); up.setInt(2, studentId); up.setInt(3, courseId);
                if (up.executeUpdate() == 0) {                      // not enrolled yet → create it
                    PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO enrollments(student_id,course_id,score) VALUES (?,?,?)");
                    ins.setInt(1, studentId); ins.setInt(2, courseId); ins.setDouble(3, score);
                    ins.executeUpdate();
                }
                PreparedStatement done = c.prepareStatement(        // mark the submission as approved
                    "UPDATE grade_submissions SET status='approved', decided_at=now() WHERE id=?");
                done.setInt(1, subId); done.executeUpdate();
                send(ex, 200, "{\"ok\":true,\"status\":\"approved\"}");
            } else if (decision.equals("reject")) {                 // ===== REJECT =====
                PreparedStatement done = c.prepareStatement(        // just mark it rejected; grade is NOT written
                    "UPDATE grade_submissions SET status='rejected', decided_at=now() WHERE id=?");
                done.setInt(1, subId); done.executeUpdate();
                send(ex, 200, "{\"ok\":true,\"status\":\"rejected\"}");
            } else {                                                // anything else = bad input
                send(ex, 400, "{\"ok\":false,\"error\":\"decision must be approve or reject\"}");
            }
        } catch (Exception e) { error(ex, e); }
    }

    // ---------- POST /api/login : check username + password, return the user's ROLE ----------
    static void handleLogin(HttpExchange ex) throws IOException {
        try (Connection c = db()) {
            Map<String,String> f = readForm(ex);                    // read username + password the browser sent
            PreparedStatement ps = c.prepareStatement(             // find a user that matches BOTH
                "SELECT u.role, u.student_id, u.teacher_id, " +
                "       COALESCE(s.name, te.name, u.username) AS name " + // COALESCE = first non-empty: student name, else teacher name, else username
                "FROM users u " +
                "LEFT JOIN students s ON s.id = u.student_id " +    // attach student name if this user is a student
                "LEFT JOIN teachers te ON te.id = u.teacher_id " +  // attach teacher name if this user is a teacher
                "WHERE u.username = ? AND u.password = ?");
            String username = f.getOrDefault("username","").trim(); // .trim() removes accidental spaces
            ps.setString(1, username);                              // fill ? #1
            ps.setString(2, f.getOrDefault("password",""));        // fill ? #2
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {                                        // found one row = correct login
                Object sid = rs.getObject("student_id");           // student id (null for teachers/admin)
                Object tid = rs.getObject("teacher_id");           // teacher id (null for students/admin)
                send(ex, 200, "{\"ok\":true,"                      // send back who they are
                    + "\"role\":\"" + esc(rs.getString("role")) + "\","   // role = student/teacher/admin
                    + "\"name\":\"" + esc(rs.getString("name")) + "\","   // display name
                    + "\"studentId\":" + (sid==null ? "null" : sid.toString()) + ","
                    + "\"teacherId\":" + (tid==null ? "null" : tid.toString()) + "}");
            } else {                                                // no match — but WHY? maybe still waiting for approval
                PreparedStatement pr = c.prepareStatement(         // look for a sign-up request with this username
                    "SELECT status FROM account_requests WHERE username=? ORDER BY requested_at DESC LIMIT 1");
                pr.setString(1, username);
                ResultSet prs = pr.executeQuery();
                String reqStatus = prs.next() ? prs.getString("status") : null; // pending / rejected / none
                if ("pending".equals(reqStatus)) {                 // they signed up but admin hasn't decided
                    send(ex, 403, "{\"ok\":false,\"error\":\"Your account is waiting for admin approval\"}");
                } else if ("rejected".equals(reqStatus)) {         // admin said no
                    send(ex, 403, "{\"ok\":false,\"error\":\"Your sign-up request was rejected by the admin\"}");
                } else {                                            // just a wrong username/password
                    send(ex, 401, "{\"ok\":false,\"error\":\"Wrong username or password\"}");
                }
            }
        } catch (Exception e) { error(ex, e); }
    }

    // ---------- POST /api/register : a new student/teacher REQUESTS an account (no login yet) ----------
    static void handleRegister(HttpExchange ex) throws IOException {
        try (Connection c = db()) {
            Map<String,String> f = readForm(ex);                    // read the sign-up form
            String name     = f.getOrDefault("name","").trim();     // their full name
            String email    = f.getOrDefault("email","").trim();    // their email
            String username = f.getOrDefault("username","").trim();  // chosen username
            String password = f.getOrDefault("password","");        // chosen password
            String role     = f.getOrDefault("role","student").trim(); // "student" or "teacher"
            if (!role.equals("student") && !role.equals("teacher")) role = "student"; // safety: only allow these two

            if (name.isEmpty() || username.isEmpty() || password.isEmpty()) { // required fields missing?
                send(ex, 400, "{\"ok\":false,\"error\":\"Name, username and password are required\"}");
                return;
            }
            // Is this username already a REAL account?
            PreparedStatement chk = c.prepareStatement("SELECT 1 FROM users WHERE username=?");
            chk.setString(1, username);
            if (chk.executeQuery().next()) {                       // found = taken
                send(ex, 409, "{\"ok\":false,\"error\":\"Username already taken\"}"); // 409 = conflict
                return;
            }
            // Is there already a PENDING request with this username?
            PreparedStatement chk2 = c.prepareStatement(
                "SELECT 1 FROM account_requests WHERE username=? AND status='pending'");
            chk2.setString(1, username);
            if (chk2.executeQuery().next()) {
                send(ex, 409, "{\"ok\":false,\"error\":\"That username already has a request awaiting approval\"}");
                return;
            }
            // Save the request as "pending" — the admin will accept/reject later.
            PreparedStatement ins = c.prepareStatement(
                "INSERT INTO account_requests(name,email,username,password,role) VALUES (?,?,?,?,?)");
            ins.setString(1, name);
            ins.setString(2, email.isEmpty() ? username + "@uni.edu" : email); // make an email if they left it blank
            ins.setString(3, username);
            ins.setString(4, password);
            ins.setString(5, role);
            ins.executeUpdate();                                   // save the request
            send(ex, 200, "{\"ok\":true,\"pending\":true,\"role\":\"" + esc(role) + "\"}"); // tell browser "request sent"
        } catch (Exception e) { error(ex, e); }
    }

    // ---------- GET /api/account-requests?status=pending : sign-ups waiting for the admin ----------
    // (List shape again: SELECT → loop → JSON → send.)
    static void handleAccountRequests(HttpExchange ex) throws IOException {
        try (Connection c = db()) {
            Map<String,String> q = query(ex);
            String sql = "SELECT id, name, email, username, role, status, requested_at " +
                         "FROM account_requests ";
            boolean byStatus = q.containsKey("status");             // filter by status if given
            if (byStatus) sql += "WHERE status=? ";
            sql += "ORDER BY requested_at DESC";                    // newest first
            PreparedStatement ps = c.prepareStatement(sql);
            if (byStatus) ps.setString(1, q.get("status"));
            ResultSet rs = ps.executeQuery();
            StringBuilder j = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) j.append(","); first = false;
                java.sql.Timestamp ts = rs.getTimestamp("requested_at");
                j.append("{\"id\":").append(rs.getInt("id"))
                 .append(",\"name\":\"").append(esc(rs.getString("name"))).append("\"")
                 .append(",\"email\":\"").append(esc(rs.getString("email"))).append("\"")
                 .append(",\"username\":\"").append(esc(rs.getString("username"))).append("\"")
                 .append(",\"role\":\"").append(esc(rs.getString("role"))).append("\"")
                 .append(",\"status\":\"").append(esc(rs.getString("status"))).append("\"")
                 .append(",\"requestedAt\":\"").append(ts==null?"":esc(ts.toString())).append("\"")
                 .append("}");
            }
            j.append("]");
            sendJson(ex, j.toString());
        } catch (Exception e) { error(ex, e); }
    }

    // ---------- POST /api/account-decision : ADMIN accepts/rejects a sign-up ----------
    static void handleAccountDecision(HttpExchange ex) throws IOException {
        try (Connection c = db()) {
            Map<String,String> f = readForm(ex);
            if (!f.getOrDefault("role","").equals("admin")) {       // only the admin may decide
                send(ex, 403, "{\"ok\":false,\"error\":\"Only the admin can accept accounts\"}");
                return;
            }
            int reqId = Integer.parseInt(f.get("requestId"));       // which request
            String decision = f.getOrDefault("decision","");        // approve / reject

            // Load the pending request's details.
            PreparedStatement get = c.prepareStatement(
                "SELECT name, email, username, password, role FROM account_requests " +
                "WHERE id=? AND status='pending'");
            get.setInt(1, reqId);
            ResultSet rs = get.executeQuery();
            if (!rs.next()) {                                       // not found / already decided
                send(ex, 404, "{\"ok\":false,\"error\":\"No pending request with that id\"}");
                return;
            }
            String name=rs.getString("name"), email=rs.getString("email"),     // copy the values into variables
                   username=rs.getString("username"), password=rs.getString("password"),
                   role=rs.getString("role");

            if (decision.equals("reject")) {                        // ===== REJECT: just mark it, create nothing =====
                PreparedStatement up = c.prepareStatement(
                    "UPDATE account_requests SET status='rejected', decided_at=now() WHERE id=?");
                up.setInt(1, reqId); up.executeUpdate();
                send(ex, 200, "{\"ok\":true,\"status\":\"rejected\"}");
                return;
            }
            if (!decision.equals("approve")) {                      // not approve and not reject = bad input
                send(ex, 400, "{\"ok\":false,\"error\":\"decision must be approve or reject\"}");
                return;
            }

            // ===== APPROVE: make a real account. First re-check the username is still free. =====
            PreparedStatement taken = c.prepareStatement("SELECT 1 FROM users WHERE username=?");
            taken.setString(1, username);
            if (taken.executeQuery().next()) {
                send(ex, 409, "{\"ok\":false,\"error\":\"Username was taken in the meantime\"}");
                return;
            }

            if (role.equals("student")) {                           // ---- approve a STUDENT ----
                PreparedStatement ins = c.prepareStatement(         // 1) create the student record
                    "INSERT INTO students(name,email,major) VALUES (?,?,'Computer Science') RETURNING id"); // RETURNING id = give back the new id
                ins.setString(1, name); ins.setString(2, email);
                ResultSet idrs = ins.executeQuery(); idrs.next();   // read the new student's id
                int studentId = idrs.getInt(1);
                PreparedStatement u = c.prepareStatement(           // 2) create the login linked to that student
                    "INSERT INTO users(username,password,role,student_id) VALUES (?,?,'student',?)");
                u.setString(1, username); u.setString(2, password); u.setInt(3, studentId);
                u.executeUpdate();
            } else {                                                // ---- approve a TEACHER ----
                PreparedStatement ins = c.prepareStatement(         // 1) create (or reuse) the teacher record
                    "INSERT INTO teachers(name,email) VALUES (?,?) " +
                    "ON CONFLICT (name) DO UPDATE SET email=EXCLUDED.email RETURNING id"); // if name exists, just update email
                ins.setString(1, name); ins.setString(2, email);
                ResultSet idrs = ins.executeQuery(); idrs.next();
                int teacherId = idrs.getInt(1);                     // the teacher's id
                PreparedStatement u = c.prepareStatement(           // 2) create the login linked to that teacher
                    "INSERT INTO users(username,password,role,teacher_id) VALUES (?,?,'teacher',?)");
                u.setString(1, username); u.setString(2, password); u.setInt(3, teacherId);
                u.executeUpdate();
            }
            PreparedStatement done = c.prepareStatement(            // finally, mark the request approved
                "UPDATE account_requests SET status='approved', decided_at=now() WHERE id=?");
            done.setInt(1, reqId); done.executeUpdate();
            send(ex, 200, "{\"ok\":true,\"status\":\"approved\",\"role\":\"" + esc(role) + "\"}");
        } catch (Exception e) { error(ex, e); }
    }

    // ---------- serve the website files (index.html, etc.) ----------
    static void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();                // what file did the browser ask for? e.g. "/"
        if (path.equals("/")) path = "/index.html";                // "/" means "the home page" = index.html
        Path file = FRONTEND.resolve(path.substring(1)).normalize();// build the real path inside the frontend folder
        if (!file.startsWith(FRONTEND) || !Files.exists(file)) { send(ex, 404, "Not found"); return; } // safety + missing file = 404
        byte[] body = Files.readAllBytes(file);                    // read the whole file into memory
        String ct = path.endsWith(".html") ? "text/html" :         // pick the right "content type" so the browser understands it
                    path.endsWith(".css")  ? "text/css"  :
                    path.endsWith(".js")   ? "text/javascript" : "text/plain";
        ex.getResponseHeaders().set("Content-Type", ct + "; charset=utf-8"); // tell the browser the type
        ex.sendResponseHeaders(200, body.length);                  // say "200 OK, this many bytes coming"
        try (OutputStream os = ex.getResponseBody()) { os.write(body); } // send the file's bytes
    }

    // ====================================================================
    //  HELPERS — small reusable tools used by the handlers above
    // ====================================================================

    // open a fresh connection to the database
    static Connection db() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS); // log into PostgreSQL and return the connection
    }

    // read POST form data ("a=1&b=2") into a Map (key→value)
    static Map<String,String> readForm(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8); // get the raw text body
        Map<String,String> m = new HashMap<>();                    // empty box of key→value pairs
        for (String pair : body.split("&")) {                      // split "a=1&b=2" into ["a=1","b=2"]
            String[] kv = pair.split("=", 2);                      // split "a=1" into ["a","1"]
            if (kv.length == 2)                                    // only if it really had an "="
                m.put(dec(kv[0]), dec(kv[1]));                     // store decoded key + decoded value
        }
        return m;                                                  // give back the filled map
    }

    // read the "?a=1&b=2" part of the web address into a Map
    static Map<String,String> query(HttpExchange ex) {
        Map<String,String> m = new HashMap<>();
        String q = ex.getRequestURI().getQuery();                  // the text after "?" (may be null)
        if (q != null) for (String pair : q.split("&")) {          // if there is one, split by "&"
            String[] kv = pair.split("=", 2);                      // split each "key=value"
            if (kv.length == 2) m.put(dec(kv[0]), dec(kv[1]));      // store it
        }
        return m;
    }

    // turn web-encoded text (e.g. "Mona%20Saleh") back into normal text ("Mona Saleh")
    static String dec(String s) { return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8); }

    // make a string safe to put inside JSON (escape backslashes and quotes)
    static String esc(String s) { return s==null ? "" : s.replace("\\","\\\\").replace("\"","\\\""); }

    // send a JSON reply (sets the type header, then sends with code 200)
    static void sendJson(HttpExchange ex, String json) throws IOException {
        ex.getResponseHeaders().set("Content-Type","application/json; charset=utf-8"); // say "this is JSON"
        send(ex, 200, json);                                       // send it
    }

    // the core "send a reply" tool: status code + text body
    static void send(HttpExchange ex, int code, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);          // turn text into bytes
        ex.sendResponseHeaders(code, b.length);                    // send the status code + length
        try (OutputStream os = ex.getResponseBody()) { os.write(b); } // send the bytes
    }

    // if something crashes, print it to the console and send a 500 error reply
    static void error(HttpExchange ex, Exception e) throws IOException {
        e.printStackTrace();                                       // show the full error in the server window (for debugging)
        send(ex, 500, "{\"ok\":false,\"error\":\"" + esc(e.getMessage()) + "\"}"); // 500 = server error
    }
}
